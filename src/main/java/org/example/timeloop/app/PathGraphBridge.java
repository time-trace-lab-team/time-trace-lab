package org.example.timeloop.app;

import org.example.timeloop.core.Direction;
import org.example.timeloop.core.path.OrthogonalPathGraph;
import org.example.timeloop.core.path.PathExit;
import org.example.timeloop.core.path.PathNode;
import org.example.timeloop.core.path.PathPoint;
import org.example.timeloop.level.model.Vector2D;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 关卡数据（{@code level.model.PathNode}）→ 运动图（{@code core.path}）的桥接（集成层）。
 *
 * <p>两套 {@code PathNode} 类型分属不同板块，集成层在此做唯一换算：按
 * {@code tileSize} 与坐标找出 1-tile 相邻节点，生成 {@link PathExit}。</p>
 */
final class PathGraphBridge {

    private final List<org.example.timeloop.level.model.PathNode> levelNodes;
    private final double tileSize;

    PathGraphBridge(List<org.example.timeloop.level.model.PathNode> levelNodes, double tileSize) {
        this.levelNodes = List.copyOf(levelNodes);
        this.tileSize = tileSize;
    }

    OrthogonalPathGraph toGraph() {
        List<PathNode> nodes = new ArrayList<>(levelNodes.size());
        for (org.example.timeloop.level.model.PathNode levelNode : levelNodes) {
            List<PathExit> exits = new ArrayList<>();
            for (org.example.timeloop.level.model.PathNode.Dir dir : levelNode.getAllowDirs()) {
                String neighborId = findNeighborId(levelNode, dir);
                if (neighborId != null) {
                    exits.add(new PathExit(toCore(dir), neighborId));
                }
            }
            if (exits.isEmpty()) {
                throw new IllegalStateException(
                        "关卡节点没有 1-tile 相邻出口，无法构建运动图：" + levelNode.getId());
            }
            Optional<Direction> defaultExit = levelNode.getDefaultExit() == null
                    ? Optional.empty()
                    : Optional.of(toCore(levelNode.getDefaultExit()));
            nodes.add(new PathNode(
                    levelNode.getId(),
                    new PathPoint(levelNode.getWorldPos().x(), levelNode.getWorldPos().y()),
                    exits,
                    defaultExit));
        }
        return new OrthogonalPathGraph(nodes);
    }

    private String findNeighborId(org.example.timeloop.level.model.PathNode node,
                                  org.example.timeloop.level.model.PathNode.Dir dir) {
        Vector2D target = offset(node.getWorldPos(), dir);
        double threshold = tileSize * 0.1;
        for (org.example.timeloop.level.model.PathNode candidate : levelNodes) {
            if (candidate.getId().equals(node.getId())) {
                continue;
            }
            double dx = candidate.getWorldPos().x() - target.x();
            double dy = candidate.getWorldPos().y() - target.y();
            if (dx * dx + dy * dy < threshold * threshold) {
                return candidate.getId();
            }
        }
        return null;
    }

    private Vector2D offset(Vector2D position, org.example.timeloop.level.model.PathNode.Dir dir) {
        return switch (dir) {
            case UP -> new Vector2D(position.x(), position.y() - tileSize);
            case DOWN -> new Vector2D(position.x(), position.y() + tileSize);
            case LEFT -> new Vector2D(position.x() - tileSize, position.y());
            case RIGHT -> new Vector2D(position.x() + tileSize, position.y());
        };
    }

    private static Direction toCore(org.example.timeloop.level.model.PathNode.Dir dir) {
        return Direction.valueOf(dir.name());
    }
}
