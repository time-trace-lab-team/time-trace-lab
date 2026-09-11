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
 *   <li>无输入 → 静止 IDLE；</li>
 *   <li>按住当前朝向 → 以 baseSpeed 沿段推进；</li>
 *   <li>段中间按 90° → 停下；</li>
 *   <li>节点中心：newestEdge 是 90° 且仍按住且合法 → 转向；否则当前朝向合法 → 直行；否则检查死路掉头；否则 IDLE；</li>
 *   <li><b>真死路掉头豁免</b>（严格前置条件）：
 *       前方（当前朝向）不可通行
 *       且反方向确有出口
 *       且无任何可通行 90° 出口
 *       → 允许请求反方向；</li>
 *   <li>同刻按住相反方向 → IDLE；</li>
 *   <li>始终吸附在正交路径中心线上。</li>
 * </ul>
 */
public final class PatrolController {

    private final OrthogonalPathGraph graph;
    private final PatrolConfig config;
    private final SpeedModifierPort speedModifier;

    private String segmentStartNodeId;
    private String segmentEndNodeId;
    private Direction direction;
    private PathPoint position;
    private Direction pendingDirection;

    public PatrolController(
            OrthogonalPathGraph graph,
            String startNodeId,
            Direction initialDirection,
            PatrolConfig config) {
        this(graph, startNodeId, initialDirection, config, SpeedModifierPort.normalSpeed());
    }

    public PatrolController(
            OrthogonalPathGraph graph,
            String startNodeId,
            Direction initialDirection,
            PatrolConfig config,
            SpeedModifierPort speedModifier) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.config = Objects.requireNonNull(config, "config");
        this.speedModifier = Objects.requireNonNull(speedModifier, "speedModifier");
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
     * 推进一帧。
     *
     * @param tick            当前刻号（≥0）
     * @param heldDirections  本刻末仍按住的方向集合（可为空）
     * @param newestEdge      本刻最后新按下的方向（无则 {@link Optional#empty()}）；
     *                        仅在到达节点中心时用于提交转向，段中间忽略
     * @param passability     出口通行性判定
     * @return 本刻的玩家运动学快照
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

        refreshPendingDirection(heldDirections, newestEdge);

        // R1：同刻按住相反方向 → IDLE
        if (heldDirections.contains(direction)
                && heldDirections.contains(DirectionGeometry.opposite(direction))) {
            return idleFrame(tick);
        }

        // R2：无输入 → IDLE
        if (heldDirections.isEmpty()) {
            return idleFrame(tick);
        }

        double multiplier = speedMultiplier();
        double budget = config.baseSpeed() * multiplier;
        while (budget > 0) {
            PathNode center = centerNodeIfAtCenter();
            if (center != null) {
                Direction nextDir = decideDirection(center, heldDirections, passability);
                if (nextDir == null) {
                    return idleFrame(tick);
                }
                if (nextDir == pendingDirection) {
                    pendingDirection = null;
                }
                // P0-E 修复：站在段终点节点中心时，即使 nextDir == direction 也必须滚动 segment
                if (nextDir != direction || center.id().equals(segmentEndNodeId)) {
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
                return movingFrame(tick, multiplier);
            } else {
                position = destination.center();
                budget -= dist;
            }
        }
        return movingFrame(tick, multiplier);
    }
    /** 返回当前吸附位置（始终在路径中心线上）。 */
    public PathPoint position() {
        return position;
    }

    /** 返回当前朝向。 */
    public Direction direction() {
        return direction;
    }

    /** 返回本控制器使用的配置。 */
    public PatrolConfig config() {
        return config;
    }

    // ========== private ==========

    private Direction decideDirection(
            PathNode center,
            Set<Direction> held,
            ExitPassability passability) {
        // 1. 单槽方向意图仍按住且合法 → 转向
        if (pendingDirection != null
                && held.contains(pendingDirection)
                && PathExitSelector.isPassable(graph, center, pendingDirection, passability)) {
            return pendingDirection;
        }

        // 2. held 含当前朝向且直行合法 → 继续
        if (held.contains(direction)
                && PathExitSelector.isPassable(graph, center, direction, passability)) {
            return direction;
        }

        // 3. P0-D 修复：真死路掉头豁免（严格前置条件）
        Direction reverse = DirectionGeometry.opposite(direction);
        if (held.contains(reverse)
                && !PathExitSelector.isPassable(graph, center, direction, passability)  // 前方不可通行
                && PathExitSelector.isPassable(graph, center, reverse, passability)    // 反方向确有出口
                && !hasAny90Exit(center, passability)) {                               // 无任何 90° 出口
            return reverse;
        }

        // 4. 无法移动
        return null;
    }

    private void refreshPendingDirection(Set<Direction> heldDirections, Optional<Direction> newestEdge) {
        if (pendingDirection != null && !heldDirections.contains(pendingDirection)) {
            pendingDirection = null;
        }
        newestEdge.ifPresent(edge -> {
            if (heldDirections.contains(edge)
                    && edge != direction
                    && edge != DirectionGeometry.opposite(direction)) {
                pendingDirection = edge;
            }
        });
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

    private PlayerKinematics movingFrame(long tick, double multiplier) {
        return new PlayerKinematics(tick, position.x(), position.y(), direction,
                multiplier < 1.0 ? MovementState.SLOWED : MovementState.CRUISING,
                ActorPhase.AVAILABLE, 0, false, AnimationState.MOVING);
    }

    private double speedMultiplier() {
        double multiplier = speedModifier.speedMultiplier();
        if (!Double.isFinite(multiplier) || multiplier < 0.0) {
            throw new IllegalStateException(
                    "speed multiplier must be finite and >= 0, actual " + multiplier);
        }
        return multiplier;
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
