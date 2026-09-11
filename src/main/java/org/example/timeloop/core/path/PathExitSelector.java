package org.example.timeloop.core.path;

import org.example.timeloop.core.Direction;

import java.util.Objects;

/** Pure passability query for explicitly requested path exits. */
public final class PathExitSelector {

    private PathExitSelector() {
    }

    /**
     * 纯判定：从给定节点沿指定方向是否可通行。
     * 用于四方向受约束移动——玩家请求的方向是否合法。
     *
     * @param graph       路径图
     * @param node        当前节点
     * @param direction   请求方向
     * @param passability 本刻可通行查询
     * @return 该方向存在出口且可通行返回 {@code true}
     */
    public static boolean isPassable(
            OrthogonalPathGraph graph,
            PathNode node,
            Direction direction,
            ExitPassability passability) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(passability, "passability");
        return isUsable(graph, node, direction, passability);
    }

    private static boolean isUsable(
            OrthogonalPathGraph graph,
            PathNode node,
            Direction direction,
            ExitPassability passability) {
        return node.exitFor(direction)
                .map(exit -> passability.isPassable(node, exit, graph.node(exit.targetNodeId())))
                .orElse(false);
    }
}
