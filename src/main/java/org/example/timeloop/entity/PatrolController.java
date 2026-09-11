package org.example.timeloop.entity;

import org.example.timeloop.core.ActorPhase;
import org.example.timeloop.core.AnimationState;
import org.example.timeloop.core.Direction;
import org.example.timeloop.core.MovementState;
import org.example.timeloop.core.PlayerKinematics;
import org.example.timeloop.core.path.DirectionGeometry;
import org.example.timeloop.core.path.ExitPassability;
import org.example.timeloop.core.path.OrthogonalPathGraph;
import org.example.timeloop.core.path.PathExitSelector;
import org.example.timeloop.core.path.PathNode;
import org.example.timeloop.core.path.PathPoint;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 玩家四方向受约束移动（C-PLAYER-MOVE-02，开发 1）。
 *
 * <p>行为规则：</p>
 * <ul>
 *   <li>无输入 → 静止（{@link MovementState#IDLE}）；</li>
 *   <li>按住当前朝向 → 以 baseSpeed 沿段推进；</li>
 *   <li>段中间按 90° → 停下（只能在节点中心转向）；</li>
 *   <li>节点中心：newestEdge 是 90° 且仍按住且合法 → 转向；
 *       否则当前朝向合法 → 直行；否则检查死路掉头；否则 IDLE；</li>
 *   <li><b>真死路掉头豁免</b>：{@code isPassable(当前节点, 当前朝向)==false}
 *       且该节点无任何可通行 90° 方向 → 允许请求反方向；</li>
 *   <li>同刻按住相反方向 → IDLE；</li>
 *   <li>始终吸附在正交路径中心线上。</li>
 * </ul>
 */
public final class PatrolController {

    private final OrthogonalPathGraph graph;
    private final PatrolConfig config;

    private String segmentStartNodeId;
    private String segmentEndNodeId;
    private Direction direction;
    private PathPoint position;

    public PatrolController(
            OrthogonalPathGraph graph,
            String startNodeId,
            Direction initialDirection,
            PatrolConfig config) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.config = Objects.requireNonNull(config, "config");
        Objects.requireNonNull(initialDirection, "initialDirection");

        PathNode start = graph.node(startNodeId);
        PathNode end = graph.neighbor(start, initialDirection).orElseThrow(() -> new IllegalArgumentException(
                "start node '" + start.id() + "' has no " + initialDirection + " exit"));

        this.segmentStartNodeId = start.id();
        this.segmentEndNodeId = end.id();
        this.direction = initialDirection;
        this.position = start.center();
    }

    /**
     * 推进一个逻辑刻。
     *
     * @param tick            共享逻辑刻
     * @param heldDirections  本刻结束仍按住的方向（可多键）
     * @param newestEdge      本刻最后新按下的方向（仅对首次到达的节点有效）
     * @param passability     本刻可通行查询
     */
    public PlayerKinematics advance(
            long tick,
            Set<Direction> heldDirections,
            Optional<Direction> newestEdge,
            ExitPassability passability) {
        if (tick < 0) {
            throw new IllegalArgumentException("tick must be >= 0, actual " + tick);
        }
        Objects.requireNonNull(heldDirections, "heldDirections");
        Objects.requireNonNull(newestEdge, "newestEdge");
        Objects.requireNonNull(passability, "passability");

        // R1：同刻按住相反方向 → IDLE
        if (heldDirections.contains(direction)
                && heldDirections.contains(DirectionGeometry.opposite(direction))) {
            return idleFrame(tick);
        }

        // R2：无输入 → IDLE
        if (heldDirections.isEmpty()) {
            return idleFrame(tick);
        }

        double budget = config.baseSpeed();
        Optional<Direction> pendingEdge = newestEdge;

        while (budget > 0) {
            PathNode center = centerNodeIfAtCenter();
            if (center != null) {
                Direction nextDir = decideDirection(center, heldDirections, pendingEdge, passability);
                if (nextDir == null) {
                    return idleFrame(tick);
                }
                if (nextDir != direction) {
                    direction = nextDir;
                    segmentStartNodeId = center.id();
                    segmentEndNodeId = graph.neighbor(center, nextDir).get().id();
                }
            } else {
                // 段中间：只按当前朝向才继续
                if (!heldDirections.contains(direction)) {
                    return idleFrame(tick);
                }
            }

            PathNode destination = graph.node(segmentEndNodeId);
            double dist = distanceToDestination(destination);
            if (dist > budget) {
                position = DirectionGeometry.move(position, direction, budget);
                return cruisingFrame(tick);
            } else {
                position = destination.center();
                budget -= dist;
                pendingEdge = Optional.empty();
            }
        }
        return cruisingFrame(tick);
    }

    /** 当前 tick-end world-space center。 */
    public PathPoint position() {
        return position;
    }

    /** 当前段方向。 */
    public Direction direction() {
        return direction;
    }

    /** 冻结的会话配置。 */
    public PatrolConfig config() {
        return config;
    }

    // ========== private ==========

    /**
     * 在节点中心决定下一方向。
     *
     * @return 决定的方向；返回 {@code null} 表示无法移动（IDLE）
     */
    private Direction decideDirection(
            PathNode center,
            Set<Direction> held,
            Optional<Direction> newestEdge,
            ExitPassability passability) {
        // 1. newestEdge 是 90° 且仍按住且合法 → 转向
        if (newestEdge.isPresent()) {
            Direction edge = newestEdge.get();
            if (edge != direction
                    && edge != DirectionGeometry.opposite(direction)
                    && held.contains(edge)
                    && PathExitSelector.isPassable(graph, center, edge, passability)) {
                return edge;
            }
        }

        // 2. held 含当前朝向且直行合法 → 继续
        if (held.contains(direction)
                && PathExitSelector.isPassable(graph, center, direction, passability)) {
            return direction;
        }

        // 3. 真死路掉头豁免
        if (held.contains(DirectionGeometry.opposite(direction))
                && !hasAny90Exit(center, passability)) {
            return DirectionGeometry.opposite(direction);
        }

        // 4. 无法移动
        return null;
    }

    private boolean hasAny90Exit(PathNode node, ExitPassability passability) {
        for (Direction side : DirectionGeometry.sideDirections(direction)) {
            if (PathExitSelector.isPassable(graph, node, side, passability)) {
                return true;
            }
        }
        return false;
    }

    private PlayerKinematics idleFrame(long tick) {
        return new PlayerKinematics(tick, position.x(), position.y(), direction,
                MovementState.IDLE, ActorPhase.AVAILABLE, 0, false, AnimationState.MOVING);
    }

    private PlayerKinematics cruisingFrame(long tick) {
        return new PlayerKinematics(tick, position.x(), position.y(), direction,
                MovementState.CRUISING, ActorPhase.AVAILABLE, 0, false, AnimationState.MOVING);
    }

    private PathNode centerNodeIfAtCenter() {
        PathNode start = graph.node(segmentStartNodeId);
        if (isAtCenterOf(start)) {
            return start;
        }
        PathNode end = graph.node(segmentEndNodeId);
        if (isAtCenterOf(end)) {
            return end;
        }
        return null;
    }

    private boolean isAtCenterOf(PathNode node) {
        double dx = position.x() - node.center().x();
        double dy = position.y() - node.center().y();
        return Math.abs(dx) <= config.epsilon() && Math.abs(dy) <= config.epsilon();
    }

    private double distanceToDestination(PathNode destination) {
        double crossAxisError;
        double forwardDistance;
        switch (direction) {
            case UP -> {
                crossAxisError = Math.abs(position.x() - destination.center().x());
                forwardDistance = position.y() - destination.center().y();
            }
            case RIGHT -> {
                crossAxisError = Math.abs(position.y() - destination.center().y());
                forwardDistance = destination.center().x() - position.x();
            }
            case DOWN -> {
                crossAxisError = Math.abs(position.x() - destination.center().x());
                forwardDistance = destination.center().y() - position.y();
            }
            case LEFT -> {
                crossAxisError = Math.abs(position.y() - destination.center().y());
                forwardDistance = position.x() - destination.center().x();
            }
            default -> throw new IllegalStateException("unsupported direction " + direction);
        }
        if (crossAxisError > config.epsilon() || forwardDistance < -config.epsilon()) {
            throw new IllegalStateException(
                    "patrol left its segment " + segmentStartNodeId + " -> " + segmentEndNodeId
                            + " while facing " + direction + ", position=" + position);
        }
        return Math.max(0.0, forwardDistance);
    }
}