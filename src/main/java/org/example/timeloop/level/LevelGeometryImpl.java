package org.example.timeloop.level;

import org.example.timeloop.level.model.*;

import java.util.*;

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

        this.nodeMap = new HashMap<>();
        for (PathNode node : pathNodes) {
            nodeMap.put(node.getId(), node);
        }

        validateNodes();

        this.validExitsMap = new HashMap<>();
        this.defaultExitMap = new HashMap<>();
        for (PathNode node : pathNodes) {
            validExitsMap.put(node.getId(), Collections.unmodifiableSet(node.getAllowDirs()));
            if (node.getDefaultExit() != null) {
                defaultExitMap.put(node.getId(), node.getDefaultExit());
            }
        }

        this.adjacencyMap = buildAdjacency();
        this.wallPositions = extractWallPositions(levelData);

        this.closedDoors = new HashSet<>();
        for (DoorInfo door : levelData.getDoors()) {
            if (!door.isInitiallyOpen()) {
                closedDoors.add(door.getId());
            }
        }
    }

    private void validateNodes() {
        if (pathNodes == null || pathNodes.isEmpty()) {
            throw new IllegalArgumentException("路径节点列表不能为空");
        }

        for (PathNode node : pathNodes) {
            if (node.getId() == null || node.getId().trim().isEmpty()) {
                throw new IllegalArgumentException("路径节点缺少 ID: " + node.getWorldPos());
            }
        }

        for (int i = 0; i < pathNodes.size(); i++) {
            for (int j = i + 1; j < pathNodes.size(); j++) {
                PathNode a = pathNodes.get(i);
                PathNode b = pathNodes.get(j);
                double dx = a.getWorldPos().x() - b.getWorldPos().x();
                double dy = a.getWorldPos().y() - b.getWorldPos().y();
                if (dx * dx + dy * dy < 0.001) {
                    throw new IllegalArgumentException(
                            "零长度边: 节点 " + a.getId() + " 与 " + b.getId() + " 位置相同"
                    );
                }
            }
        }

        if (pathNodes.size() > 1) {
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
            for (Map.Entry<String, Set<String>> entry : tempAdj.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    throw new IllegalArgumentException(
                            "非法孤点: 节点 " + entry.getKey() + " 没有任何相邻节点"
                    );
                }
            }
        }

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