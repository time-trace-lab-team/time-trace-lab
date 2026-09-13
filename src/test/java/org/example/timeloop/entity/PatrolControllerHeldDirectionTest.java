package org.example.timeloop.entity;

import org.example.timeloop.core.Direction;
import org.example.timeloop.core.MovementState;
import org.example.timeloop.core.PlayerKinematics;
import org.example.timeloop.core.path.ExitPassability;
import org.example.timeloop.core.path.OrthogonalPathGraph;
import org.example.timeloop.core.path.PathExit;
import org.example.timeloop.core.path.PathNode;
import org.example.timeloop.core.path.PathPoint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P0-B：多键按住的处理。
 *
 * <p>按住当前朝向 + 90° 方向 → 继续沿当前朝向行进；到节点中心后转向 90°。</p>
 */
class PatrolControllerHeldDirectionTest {

    private static final double EPS = PatrolConfig.C2_EPSILON;

    @Test
    void holdingDirectionAndSide_continuesMoving() {
        // 图：start(0,-4) --DOWN--> jct(0,0) --RIGHT--> east(8,0)
        PatrolController c = new PatrolController(junctionGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());

        // 第一帧：只按 DOWN
        PlayerKinematics first = c.advance(0, Set.of(Direction.DOWN), Optional.empty(), ExitPassability.allOpen());
        assertEquals(MovementState.CRUISING, first.movementState());

        // 第二帧：按住 DOWN + RIGHT（模拟玩家按住前进键再改按垂直方向）
        PlayerKinematics second = c.advance(1, Set.of(Direction.DOWN, Direction.RIGHT), Optional.empty(), ExitPassability.allOpen());
        assertEquals(MovementState.CRUISING, second.movementState(), "按住前进+垂直方向不能冻住");
        assertEquals(Direction.DOWN, second.direction(), "段中间仍沿原方向");
    }

    @Test
    void earlySideEdgeIsRetainedUntilTheNodeCenter() {
        PatrolController c = new PatrolController(junctionGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());

        // 在段中间按 RIGHT；下一刻不再有边沿，但仍持续按住。
        c.advance(0, Set.of(Direction.DOWN), Optional.empty(), ExitPassability.allOpen());
        c.advance(1, Set.of(Direction.DOWN, Direction.RIGHT), Optional.of(Direction.RIGHT), ExitPassability.allOpen());

        PlayerKinematics atJct = c.advance(2, Set.of(Direction.DOWN, Direction.RIGHT), Optional.empty(), ExitPassability.allOpen());
        assertEquals(Direction.RIGHT, atJct.direction(), "到节点中心提交 90° 转向");
    }

    @Test
    void releasingForwardWhileHoldingLegalSideContinuesToNodeAndUsesRemainingBudgetAfterTurning() {
        // 起点距路口 3 单位；每 tick 预算 2 单位，第二 tick 必须先沿 DOWN 走 1 单位到中心，
        // 再在同 tick 沿 RIGHT 消耗余下 1 单位。
        PatrolController c = new PatrolController(shortJunctionGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());

        PlayerKinematics midSegment = c.advance(0, Set.of(Direction.DOWN), Optional.of(Direction.DOWN),
                ExitPassability.allOpen());
        assertEquals(0.0, midSegment.x(), EPS);
        assertEquals(-1.0, midSegment.y(), EPS);
        assertEquals(Direction.DOWN, midSegment.direction());

        PlayerKinematics turned = c.advance(1, Set.of(Direction.RIGHT), Optional.of(Direction.RIGHT),
                ExitPassability.allOpen());

        assertEquals(MovementState.CRUISING, turned.movementState());
        assertEquals(Direction.RIGHT, turned.direction(), "松开前进键后，合法侧向键必须在节点中心提交");
        assertEquals(1.0, turned.x(), EPS, "跨节点后剩余预算必须沿新方向消费");
        assertEquals(0.0, turned.y(), EPS, "转向前必须先吸附到节点中心线");
    }

    @Test
    void releasedSideIntentIsDiscardedBeforeTheNodeCenter() {
        PatrolController c = new PatrolController(junctionGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());

        c.advance(0, Set.of(Direction.DOWN), Optional.empty(), ExitPassability.allOpen());
        c.advance(1, Set.of(Direction.DOWN, Direction.RIGHT), Optional.of(Direction.RIGHT), ExitPassability.allOpen());

        PlayerKinematics atJct = c.advance(2, Set.of(Direction.DOWN), Optional.empty(), ExitPassability.allOpen());
        assertEquals(Direction.DOWN, atJct.direction(), "松开侧向键后不得在节点转向");
    }

    @Test
    void newestOppositeEdgeReversesTheCurrentSegmentImmediately() {
        PatrolController c = new PatrolController(junctionGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());

        c.advance(0, Set.of(Direction.DOWN), Optional.of(Direction.DOWN), ExitPassability.allOpen());
        PlayerKinematics reversed = c.advance(1, Set.of(Direction.DOWN, Direction.UP), Optional.of(Direction.UP), ExitPassability.allOpen());
        assertEquals(MovementState.CRUISING, reversed.movementState());
        assertEquals(Direction.UP, reversed.direction(), "最后按下的反方向应同 tick 生效");
        assertEquals(-4.0, reversed.y());
    }

    @Test
    void resetRestoresStartStateClearsPendingTurnAndCanMoveImmediately() {
        PatrolController c = new PatrolController(junctionGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());

        c.advance(0, Set.of(Direction.DOWN), Optional.empty(), ExitPassability.allOpen());
        c.advance(1, Set.of(Direction.DOWN, Direction.RIGHT), Optional.of(Direction.RIGHT), ExitPassability.allOpen());
        c.resetTo("start", Direction.DOWN);

        assertEquals(new PathPoint(0.0, -4.0), c.position());
        assertEquals(Direction.DOWN, c.direction());

        PlayerKinematics afterReset = c.advance(2, Set.of(Direction.DOWN), Optional.empty(), ExitPassability.allOpen());
        assertEquals(0.0, afterReset.x());
        assertEquals(-2.0, afterReset.y());
        assertEquals(Direction.DOWN, afterReset.direction());
    }

    @Test
    void holdingOppositeDirectionsWithoutANewEdge_staysIdle() {
        PatrolController c = new PatrolController(junctionGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());
        c.advance(0, Set.of(Direction.DOWN), Optional.empty(), ExitPassability.allOpen());
        // 没有新边沿时无法判断用户最后选择，保持静止。
        PlayerKinematics result = c.advance(1, Set.of(Direction.DOWN, Direction.UP), Optional.empty(), ExitPassability.allOpen());
        assertEquals(MovementState.IDLE, result.movementState());
        assertEquals(Direction.DOWN, result.direction());
    }

    @Test
    void idleNearJunction_newSideEdgeSnapsAndTurnsInTheSameTick() {
        PatrolController c = new PatrolController(longJunctionGraph(), "start", Direction.DOWN,
                PatrolConfig.c2Greybox());
        movePastJunctionToY2(c);
        c.advance(6, Set.of(), Optional.empty(), ExitPassability.allOpen());

        PlayerKinematics turned = c.advance(7, Set.of(Direction.RIGHT), Optional.of(Direction.RIGHT),
                ExitPassability.allOpen());

        assertEquals(MovementState.CRUISING, turned.movementState());
        assertEquals(Direction.RIGHT, turned.direction());
        assertEquals(2.0, turned.x(), EPS);
        assertEquals(0.0, turned.y(), EPS);
    }

    @Test
    void idleBeyondSnapDistance_newSideEdgeDoesNotPullBackToJunction() {
        PatrolController c = new PatrolController(longJunctionGraph(), "start", Direction.DOWN,
                PatrolConfig.c2Greybox());
        movePastJunctionToY8(c);
        c.advance(9, Set.of(), Optional.empty(), ExitPassability.allOpen());

        PlayerKinematics result = c.advance(10, Set.of(Direction.RIGHT), Optional.of(Direction.RIGHT),
                ExitPassability.allOpen());

        assertEquals(MovementState.IDLE, result.movementState());
        assertEquals(Direction.DOWN, result.direction());
        assertEquals(0.0, result.x(), EPS);
        assertEquals(8.0, result.y(), EPS);
    }

    @Test
    void idleExactlyAtSnapDistance_isInsideTheClosedBoundary() {
        PatrolController c = new PatrolController(boundaryJunctionGraph(), "start", Direction.DOWN,
                new PatrolConfig(48.0, 2.4, EPS));
        for (long tick = 0; tick < 8; tick++) {
            c.advance(tick, Set.of(Direction.DOWN), Optional.empty(), ExitPassability.allOpen());
        }
        c.advance(8, Set.of(), Optional.empty(), ExitPassability.allOpen());

        PlayerKinematics turned = c.advance(9, Set.of(Direction.RIGHT), Optional.of(Direction.RIGHT),
                ExitPassability.allOpen());

        assertEquals(Direction.RIGHT, turned.direction());
        assertEquals(2.4, turned.x(), EPS);
        assertEquals(0.0, turned.y(), EPS);
    }

    @Test
    void movingNearJunction_doesNotUseStationaryPullBack() {
        PatrolController c = new PatrolController(longJunctionGraph(), "start", Direction.DOWN,
                PatrolConfig.c2Greybox());
        movePastJunctionToY2(c);

        PlayerKinematics result = c.advance(6, Set.of(Direction.RIGHT), Optional.of(Direction.RIGHT),
                ExitPassability.allOpen());

        assertEquals(MovementState.IDLE, result.movementState());
        assertEquals(Direction.DOWN, result.direction());
        assertEquals(0.0, result.x(), EPS);
        assertEquals(2.0, result.y(), EPS);
    }

    @Test
    void idleNearJunction_blockedSideExitDoesNotSnapThroughObstacle() {
        PatrolController c = new PatrolController(longJunctionGraph(), "start", Direction.DOWN,
                PatrolConfig.c2Greybox());
        movePastJunctionToY2(c);
        c.advance(6, Set.of(), Optional.empty(), ExitPassability.allOpen());
        ExitPassability rightBlocked = (from, exit, target) -> exit.direction() != Direction.RIGHT;

        PlayerKinematics result = c.advance(7, Set.of(Direction.RIGHT), Optional.of(Direction.RIGHT),
                rightBlocked);

        assertEquals(MovementState.IDLE, result.movementState());
        assertEquals(Direction.DOWN, result.direction());
        assertEquals(0.0, result.x(), EPS);
        assertEquals(2.0, result.y(), EPS);
    }

    private static void movePastJunctionToY2(PatrolController c) {
        for (long tick = 0; tick < 6; tick++) {
            c.advance(tick, Set.of(Direction.DOWN), Optional.empty(), ExitPassability.allOpen());
        }
    }

    private static void movePastJunctionToY8(PatrolController c) {
        for (long tick = 0; tick < 9; tick++) {
            c.advance(tick, Set.of(Direction.DOWN), Optional.empty(), ExitPassability.allOpen());
        }
    }

    private static OrthogonalPathGraph junctionGraph() {
        return new OrthogonalPathGraph(List.of(
                node("start", 0.0, -4.0, List.of(new PathExit(Direction.DOWN, "jct"))),
                node("jct", 0.0, 0.0, List.of(
                        new PathExit(Direction.UP, "start"),
                        new PathExit(Direction.RIGHT, "east"))),
                node("east", 8.0, 0.0, List.of(new PathExit(Direction.LEFT, "jct")))));
    }

    private static OrthogonalPathGraph longJunctionGraph() {
        return new OrthogonalPathGraph(List.of(
                node("start", 0.0, -10.0, List.of(new PathExit(Direction.DOWN, "jct"))),
                node("jct", 0.0, 0.0, List.of(
                        new PathExit(Direction.UP, "start"),
                        new PathExit(Direction.DOWN, "south"),
                        new PathExit(Direction.RIGHT, "east"))),
                node("south", 0.0, 20.0, List.of(new PathExit(Direction.UP, "jct"))),
                node("east", 20.0, 0.0, List.of(new PathExit(Direction.LEFT, "jct")))));
    }

    private static OrthogonalPathGraph shortJunctionGraph() {
        return new OrthogonalPathGraph(List.of(
                node("start", 0.0, -3.0, List.of(new PathExit(Direction.DOWN, "jct"))),
                node("jct", 0.0, 0.0, List.of(
                        new PathExit(Direction.UP, "start"),
                        new PathExit(Direction.RIGHT, "east"))),
                node("east", 8.0, 0.0, List.of(new PathExit(Direction.LEFT, "jct")))));
    }

    private static OrthogonalPathGraph boundaryJunctionGraph() {
        return new OrthogonalPathGraph(List.of(
                node("start", 0.0, -12.0, List.of(new PathExit(Direction.DOWN, "jct"))),
                node("jct", 0.0, 0.0, List.of(
                        new PathExit(Direction.UP, "start"),
                        new PathExit(Direction.DOWN, "south"),
                        new PathExit(Direction.RIGHT, "east"))),
                node("south", 0.0, 24.0, List.of(new PathExit(Direction.UP, "jct"))),
                node("east", 24.0, 0.0, List.of(new PathExit(Direction.LEFT, "jct")))));
    }

    private static PathNode node(String id, double x, double y, List<PathExit> exits) {
        return new PathNode(id, new PathPoint(x, y), exits);
    }
}
