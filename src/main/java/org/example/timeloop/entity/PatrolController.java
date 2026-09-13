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
 *   <li>段中间按住在下一节点合法的新方向 → 沿当前段继续并在节点中心吸附、转向；</li>
 *   <li>上一刻静止且靠近节点时，新按合法 90° 方向可在有限距离内吸附转向；</li>
 *   <li>节点中心：最新且仍按住的合法方向优先；否则当前朝向合法时直行；</li>
 *   <li>反方向与 90° 方向遵守相同的“节点中心提交”规则，不在段中间瞬移或斜切；</li>
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
    private boolean wasIdle;

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
        resetTo(startNodeId, initialDirection);
    }

    /**
     * 推进一帧。
     *
     * @param tick            当前刻号（≥0）
     * @param heldDirections  本刻末仍按住的方向集合（可为空）
     * @param newestEdge      本刻最后新按下的方向（无则 {@link Optional#empty()}）；
     *                        段中间用于预判下一节点，到达节点中心时提交
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
        snapStationaryTurnToNearbyNode(newestEdge, heldDirections, passability);
        reverseCurrentSegmentImmediately(newestEdge, heldDirections, passability);

        // 同时按住相反方向且本刻没有新的裁决边沿时，不猜测集合顺序。
        if (newestEdge.isEmpty()
                && pendingDirection == null
                && heldDirections.contains(direction)
                && heldDirections.contains(DirectionGeometry.opposite(direction))) {
            return idleFrame(tick);
        }

        // 无输入 → IDLE
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
                // 段中间不直接改变方向。新方向若能在当前段终点提交，则继续靠近并由
                // 下方的距离预算精确吸附到节点中心，避免要求玩家卡中单一逻辑刻。
                if (!heldDirections.contains(direction)
                        && !hasUpcomingTurn(heldDirections, passability)) {
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

    /**
     * Restores this controller to a round's start node and initial direction.
     * This method only replaces internal movement state: it writes no frame,
     * publishes no event, and does not affect replay-owned state.
     */
    public void resetTo(String startNodeId, Direction initialDirection) {
        Objects.requireNonNull(initialDirection, "initialDirection");

        PathNode start = graph.node(startNodeId);
        PathNode end = graph.neighbor(start, initialDirection).orElseThrow(() -> new IllegalArgumentException(
                "start node '" + start.id() + "' has no " + initialDirection + " exit"));

        segmentStartNodeId = start.id();
        segmentEndNodeId = end.id();
        direction = initialDirection;
        position = start.center();
        pendingDirection = null;
        wasIdle = true;
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

        // 3. 反方向与 90° 方向统一：只要玩家仍按住且出口合法，就在节点中心提交。
        Direction reverse = DirectionGeometry.opposite(direction);
        if (held.contains(reverse)
                && PathExitSelector.isPassable(graph, center, reverse, passability)) {
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
                    && edge != direction) {
                pendingDirection = edge;
            }
        });
    }

    /**
     * 段中间主动按反方向时，同 tick 沿原路径掉头。
     *
     * <p>交换当前有向段的起终点即可保持当前位置和中心线不变；只有反向边确实可通行时才提交，
     * 因此不会绕过门或墙。90° 转向仍使用下一节点预判与中心吸附。</p>
     */
    private void reverseCurrentSegmentImmediately(
            Optional<Direction> newestEdge,
            Set<Direction> held,
            ExitPassability passability) {
        if (centerNodeIfAtCenter() != null || newestEdge.isEmpty()) {
            return;
        }
        Direction requested = newestEdge.get();
        Direction reverse = DirectionGeometry.opposite(direction);
        if (requested != reverse || !held.contains(requested)) {
            return;
        }

        PathNode reverseFrom = graph.node(segmentEndNodeId);
        if (!PathExitSelector.isPassable(graph, reverseFrom, reverse, passability)) {
            return;
        }

        String oldStart = segmentStartNodeId;
        segmentStartNodeId = segmentEndNodeId;
        segmentEndNodeId = oldStart;
        direction = reverse;
        pendingDirection = null;
    }

    /**
     * Lets an idle player turn from a point just past or just before a junction
     * without requiring pixel-perfect alignment with its center.
     *
     * <p>The correction is deliberately bounded: only a newly pressed 90-degree
     * direction may select the nearest endpoint of the current segment, that
     * endpoint must be within {@link PatrolConfig#turnSnapDistance()}, and its
     * requested exit must be passable this tick. The method never chooses a
     * direction on the player's behalf and never bypasses a closed door.</p>
     */
    private void snapStationaryTurnToNearbyNode(
            Optional<Direction> newestEdge,
            Set<Direction> held,
            ExitPassability passability) {
        if (!wasIdle || newestEdge.isEmpty()) {
            return;
        }
        Direction requested = newestEdge.get();
        if (!held.contains(requested)
                || requested == direction
                || requested == DirectionGeometry.opposite(direction)) {
            return;
        }

        PathNode start = graph.node(segmentStartNodeId);
        PathNode end = graph.node(segmentEndNodeId);
        PathNode candidate = nearerPassableNodeWithinTurnSnapDistance(
                start, end, requested, passability);
        if (candidate == null) {
            return;
        }

        position = candidate.center();
        direction = requested;
        segmentStartNodeId = candidate.id();
        segmentEndNodeId = graph.neighbor(candidate, requested).orElseThrow().id();
        pendingDirection = null;
    }

    private PathNode nearerPassableNodeWithinTurnSnapDistance(
            PathNode first,
            PathNode second,
            Direction requested,
            ExitPassability passability) {
        double firstDistance = distanceTo(first.center());
        double secondDistance = distanceTo(second.center());
        double limit = config.turnSnapDistance() + config.epsilon();
        boolean firstEligible = firstDistance <= limit
                && PathExitSelector.isPassable(graph, first, requested, passability);
        boolean secondEligible = secondDistance <= limit
                && PathExitSelector.isPassable(graph, second, requested, passability);
        if (!firstEligible && !secondEligible) {
            return null;
        }
        if (!firstEligible) {
            return second;
        }
        if (!secondEligible) {
            return first;
        }
        // Equal distances use segmentStart for deterministic replay.
        return firstDistance <= secondDistance ? first : second;
    }

    private double distanceTo(PathPoint point) {
        return Math.hypot(position.x() - point.x(), position.y() - point.y());
    }

    /**
     * 判断当前段终点是否能提交已缓存的新方向。
     *
     * <p>这里只决定是否继续靠近节点，不在段中间改变朝向。真正的转向仍由
     * {@link #decideDirection(PathNode, Set, ExitPassability)} 在节点中心完成。</p>
     */
    private boolean hasUpcomingTurn(Set<Direction> held, ExitPassability passability) {
        if (pendingDirection == null || !held.contains(pendingDirection)) {
            return false;
        }
        PathNode upcoming = graph.node(segmentEndNodeId);
        return PathExitSelector.isPassable(graph, upcoming, pendingDirection, passability);
    }

    private PlayerKinematics idleFrame(long tick) {
        wasIdle = true;
        return new PlayerKinematics(tick, position.x(), position.y(), direction,
                MovementState.IDLE, ActorPhase.AVAILABLE, 0, false, AnimationState.MOVING);
    }

    private PlayerKinematics movingFrame(long tick, double multiplier) {
        wasIdle = false;
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
