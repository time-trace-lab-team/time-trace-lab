package org.example.timeloop.level;

import org.example.timeloop.level.model.LevelData;
import org.example.timeloop.level.model.PathNode;
import org.example.timeloop.level.model.Vector2D;

import java.util.*;

/**
 * LevelGeometry 的不可变实现。
 * 从 LevelData 构建，所有数据在构造时冻结。
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

        // 构建合法出口映射
        this.validExitsMap = new HashMap<>();
        this.defaultExitMap = new HashMap<>();
        for (PathNode node : pathNodes) {
            validExitsMap.put(node.getId(), Collections.unmodifiableSet(node.getAllowDirs()));
            if (node.getDefaultExit() != null) {
                defaultExitMap.put(node.getId(), node.getDefaultExit());
            }
        }

        // 构建邻接关系（简化：根据路径节点位置判断）
        this.adjacencyMap = buildAdjacency();

        // 墙体位置（从 LevelData 的 tileGrid 提取）
        this.wallPositions = extractWallPositions(levelData);

        // 关闭的门（初始状态，门是否关闭需要从mechanism查询）
        this.closedDoors = new HashSet<>();
    }

    private Map<String, Set<String>> buildAdjacency() {
        Map<String, Set<String>> adj = new HashMap<>();
        // 简化实现：根据 PathNode 的位置和合法出口构建邻接
        // 实际实现需要根据关卡数据中的路径连接关系
        for (PathNode node : pathNodes) {
            Set<String> neighbors = new HashSet<>();
            // 通过合法出口方向查找相邻节点
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