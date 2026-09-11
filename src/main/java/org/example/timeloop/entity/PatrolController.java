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

/**
 * 玩家四方向受约束移动（C-PLAYER-MOVE-01，开发 1）。
 *
 * <p>行为规则：</p>
 * <ul>
 *   <li>无输入 → 静止（{@link MovementState#IDLE}），位置不变；</li>
 *   <li>按住当前朝向 → 以 baseSpeed 沿段推进；</li>
 *   <li>到达节点中心 → 若直行合法则继续；否则停下等下一步输入（不自动寻路）；</li>
 *   <li>段中间按 90° 方向 → 停下（只能在节点中心转向）；</li>
 *   <li>按相反方向 → 停下（禁止主动掉头）；</li>
 *   <li>前方是墙/关闭门 → 停在节点中心（IDLE）；</li>
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
     * @param tick               共享逻辑刻
     * @param requestedDirection 本刻有效方向（空 = 无输入）
     * @param passability        本刻可通行查询
     */
    public PlayerKinematics advance(
            long tick,
            Optional<Direction> requestedDirection,
            ExitPassability passability) {
        if (tick < 0) {
            throw new IllegalArgumentException("tick must be >= 0, actual " + tick);
        }
        Objects.requireNonNull(requestedDirection, "requestedDirection");
        Objects.requireNonNull(passability, "passability");

        if (requestedDirection.isEmpty()) {
            return idleFrame(tick);
        }

        Direction requested = requestedDirection.get();

        // 反方向：禁止主动掉头
        if (requested == DirectionGeometry.opposite(direction)) {
            return idleFrame(tick);
        }

        // 90° 转向：只在节点中心提交
        if (requested != direction) {
            PathNode centerNode = centerNodeIfAtCenter();
            if (centerNode == null) {
                return idleFrame(tick);
            }
            if (!PathExitSelector.isPassable(graph, centerNode, requested, passability)) {
                return idleFrame(tick);
            }
            direction = requested;
            segmentStartNodeId = centerNode.id();
            segmentEndNodeId = graph.neighbor(centerNode, requested).get().id();
        }

        return advanceAlongCurrentSegment(tick, passability);
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

    private PlayerKinematics idleFrame(long tick) {
        return new PlayerKinematics(tick, position.x(), position.y(), direction,
                MovementState.IDLE, ActorPhase.AVAILABLE, 0, false, AnimationState.MOVING);
    }

    private PlayerKinematics cruisingFrame(long tick) {
        return new PlayerKinematics(tick, position.x(), position.y(), direction,
                MovementState.CRUISING, ActorPhase.AVAILABLE, 0, false, AnimationState.MOVING);
    }

    private PlayerKinematics advanceAlongCurrentSegment(long tick, ExitPassability passability) {
        double budget = config.baseSpeed();

        while (budget > 0) {
            PathNode destination = graph.node(segmentEndNodeId);
            double dist = distanceToDestination(destination);

            if (dist <= config.epsilon()) {
                position = destination.center();

                // 到达节点：若直行不合法 → 停下
                if (!PathExitSelector.isPassable(graph, destination, direction, passability)) {
                    return idleFrame(tick);
                }

                PathNode next = graph.neighbor(destination, direction).get();
                segmentStartNodeId = destination.id();
                segmentEndNodeId = next.id();
                continue;
            }

            if (budget < dist) {
                position = DirectionGeometry.move(position, direction, budget);
                budget = 0;
            } else {
                position = destination.center();
                budget -= dist;
            }
        }

        return cruisingFrame(tick);
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