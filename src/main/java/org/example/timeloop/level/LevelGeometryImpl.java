package org.example.timeloop.level;

import org.example.timeloop.level.model.LevelData;
import org.example.timeloop.level.model.PathNode;
import org.example.timeloop.level.model.TileType;
import org.example.timeloop.level.model.Vector2D;

import java.util.*;

/**
 * LevelGeometry 的不可变实现。
 * 从 LevelData 构建，所有数据在构造时冻结。
 * 包含地图数据合法性验证（非法孤点、零长度边等配置错误）。
 */
public class LevelGeometryImpl implements LevelGeometry {

    private final double tileSize;
    private final Vector2D spawnPosition;
    private final List<PathNode> pathNodes;
    private final Map<String, PathNode> nodeMap;
    private final Map<String, Set<PathNode.Dir>> validExitsMap;
    private final Map<String, PathNode.Dir> defaultExitMap;
    private final Map<String, Set<String>> adjacencyMap;
    private final Set<Vector2D> wallPositions;
    private final Set<String> closedDoors;

    public LevelGeometryImpl(LevelData levelData) {
        this.tileSize = levelData.getTileSize();
        this.spawnPosition = levelData.getSpawnPos();
        this.pathNodes = Collections.unmodifiableList(new ArrayList<>(levelData.getPathNodes()));

        // 构建节点索引
        this.nodeMap = new HashMap<>();
        for (PathNode node : pathNodes) {
            nodeMap.put(node.getId(), node);
        }

        // 验证地图数据合法性
        validateNodes();

        // 构建合法出口映射
        this.validExitsMap = new HashMap<>();
        this.defaultExitMap = new HashMap<>();
        for (PathNode node : pathNodes) {
            validExitsMap.put(node.getId(), Collections.unmodifiableSet(node.getAllowDirs()));
            if (node.getDefaultExit() != null) {
                defaultExitMap.put(node.getId(), node.getDefaultExit());
            }
        }

        // 构建邻接关系（根据路径节点位置判断）
        this.adjacencyMap = buildAdjacency();

        // 墙体位置（从 LevelData 的 tileGrid 提取）
        this.wallPositions = extractWallPositions(levelData);

        // 关闭的门（初始状态，门是否关闭需要从mechanism查询）
        this.closedDoors = new HashSet<>();
    }

    /**
     * 验证路径节点配置是否合法。
     * 检查项：
     * 1. 所有节点必须有 ID
     * 2. 零长度边（两个节点位置相同）
     * 3. 非法孤点（节点没有任何相邻节点）
     *
     * @throws IllegalArgumentException 如果配置不合法
     */
    private void validateNodes() {
        if (pathNodes == null || pathNodes.isEmpty()) {
            throw new IllegalArgumentException("路径节点列表不能为空");
        }

        // 1. 检查是否所有节点都有 ID
        for (PathNode node : pathNodes) {
            if (node.getId() == null || node.getId().trim().isEmpty()) {
                throw new IllegalArgumentException("路径节点缺少 ID: " + node.getWorldPos());
            }
        }

        // 2. 检查零长度边（两个节点位置相同）
        for (int i = 0; i < pathNodes.size(); i++) {
            for (int j = i + 1; j < pathNodes.size(); j++) {
                PathNode nodeA = pathNodes.get(i);
                PathNode nodeB = pathNodes.get(j);
                double dx = nodeA.getWorldPos().x() - nodeB.getWorldPos().x();
                double dy = nodeA.getWorldPos().y() - nodeB.getWorldPos().y();
                if (dx * dx + dy * dy < 0.001) {
                    throw new IllegalArgumentException(
                            "零长度边: 节点 " + nodeA.getId() + " 与 " + nodeB.getId() + " 位置相同"
                    );
                }
            }
        }

        // 3. 检查是否存在非法孤点
        // 先计算每个节点的邻居
        Map<String, Set<String>> tempAdj = new HashMap<>();
        for (PathNode node : pathNodes) {
            Set<String> neighbors = new HashSet<>();
            for (PathNode.Dir dir : node.getAllowDirs()) {
                Vector2D neighborPos = getNeighborPosition(node.getWorldPos(), dir);
                String neighborId = findNodeAt(neighborPos);
                if (neighborId != null && !neighborId.equals(node.getId())) {
                    neighbors.add(neighborId);
                }
            }
            tempAdj.put(node.getId(), neighbors);
        }

        // 如果节点没有邻居（且不是唯一节点），判定为非法孤点
        if (pathNodes.size() > 1) {
            for (Map.Entry<String, Set<String>> entry : tempAdj.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    throw new IllegalArgumentException(
                            "非法孤点: 节点 " + entry.getKey() + " 没有任何相邻节点"
                    );
                }
            }
        }

        // 4. 额外检查：合法出口方向是否都有对应的邻居节点
        for (PathNode node : pathNodes) {
            for (PathNode.Dir dir : node.getAllowDirs()) {
                Vector2D neighborPos = getNeighborPosition(node.getWorldPos(), dir);
                String neighborId = findNodeAt(neighborPos);
                if (neighborId == null) {
                    throw new IllegalArgumentException(
                            "节点 " + node.getId() + " 的合法出口方向 " + dir +
                                    " 没有对应的相邻节点（位置: " + neighborPos + "）"
                    );
                }
            }
        }

        // 5. 检查 defaultExit 是否在合法出口中
        for (PathNode node : pathNodes) {
            if (node.getDefaultExit() != null && !node.getAllowDirs().contains(node.getDefaultExit())) {
                throw new IllegalArgumentException(
                        "节点 " + node.getId() + " 的 defaultExit " + node.getDefaultExit() +
                                " 不在合法出口集合中: " + node.getAllowDirs()
                );
            }
        }
    }

    private Map<String, Set<String>> buildAdjacency() {
        Map<String, Set<String>> adj = new HashMap<>();
        for (PathNode node : pathNodes) {
            Set<String> neighbors = new HashSet<>();
            for (PathNode.Dir dir : node.getAllowDirs()) {
                Vector2D neighborPos = getNeighborPosition(node.getWorldPos(), dir);
                String neighborId = findNodeAt(neighborPos);
                if (neighborId != null && !neighborId.equals(node.getId())) {
                    neighbors.add(neighborId);
                }
            }
            adj.put(node.getId(), Collections.unmodifiableSet(neighbors));
        }
        return adj;
    }

    private Vector2D getNeighborPosition(Vector2D pos, PathNode.Dir dir) {
        double offset = tileSize;
        switch (dir) {
            case UP:    return new Vector2D(pos.x(), pos.y() - offset);
            case DOWN:  return new Vector2D(pos.x(), pos.y() + offset);
            case LEFT:  return new Vector2D(pos.x() - offset, pos.y());
            case RIGHT: return new Vector2D(pos.x() + offset, pos.y());
            default:    return pos;
        }
    }

    private String findNodeAt(Vector2D pos) {
        double threshold = tileSize * 0.1;
        for (PathNode node : pathNodes) {
            double dx = node.getWorldPos().x() - pos.x();
            double dy = node.getWorldPos().y() - pos.y();
            if (dx * dx + dy * dy < threshold * threshold) {
                return node.getId();
            }
        }
        return null;
    }

    private Set<Vector2D> extractWallPositions(LevelData levelData) {
        Set<Vector2D> walls = new HashSet<>();
        TileType[][] grid = levelData.getTileGrid();
        if (grid == null) return Collections.unmodifiableSet(walls);

        for (int row = 0; row < grid.length; row++) {
            for (int col = 0; col < grid[row].length; col++) {
                if (grid[row][col] == TileType.WALL) {
                    walls.add(new Vector2D(col * tileSize, row * tileSize));
                }
            }
        }
        return Collections.unmodifiableSet(walls);
    }

    @Override
    public double getTileSize() { return tileSize; }

    @Override
    public Vector2D getSpawnPosition() { return spawnPosition; }

    @Override
    public List<PathNode> getPathNodes() { return pathNodes; }

    @Override
    public Set<PathNode.Dir> getValidExits(String nodeId) {
        return validExitsMap.getOrDefault(nodeId, Collections.emptySet());
    }

    @Override
    public PathNode.Dir getDefaultExit(String nodeId) {
        return defaultExitMap.get(nodeId);
    }

    @Override
    public boolean isConnected(String nodeIdA, String nodeIdB) {
        Set<String> neighbors = adjacencyMap.get(nodeIdA);
        return neighbors != null && neighbors.contains(nodeIdB);
    }

    @Override
    public boolean isWall(Vector2D position) {
        return wallPositions.contains(position);
    }

    @Override
    public boolean isDoorClosed(String doorId) {
        return closedDoors.contains(doorId);
    }

    @Override
    public boolean hasNode(String nodeId) {
        return nodeMap.containsKey(nodeId);
    }

    @Override
    public Set<String> getNeighbors(String nodeId) {
        return adjacencyMap.getOrDefault(nodeId, Collections.emptySet());
    }
}