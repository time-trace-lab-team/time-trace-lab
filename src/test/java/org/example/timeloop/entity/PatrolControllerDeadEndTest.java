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
 * P0-A：真死路掉头豁免。
 */
class PatrolControllerDeadEndTest {

    private static final double EPS = PatrolConfig.C2_EPSILON;

    private static PlayerKinematics tick(PatrolController c, long t, Direction... held) {
        return c.advance(t, Set.of(held), Optional.empty(), ExitPassability.allOpen());
    }

    @Test
    void deadEndRequestReversal_isAllowed() {
        // 图：start(0,-4) --DOWN--> deadEnd(0,0)，deadEnd 只有 UP 出口
        PatrolController c = new PatrolController(deadEndGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());
        // 走到 deadEnd 中心
        for (long t = 0; t < 3; t++) {
            tick(c, t, Direction.DOWN);
        }
        // 在 deadEnd 按 UP
        PlayerKinematics result = tick(c, 3, Direction.UP);
        assertEquals(Direction.UP, result.direction(), "真死路应允许掉头");
        assertEquals(MovementState.CRUISING, result.movementState());
    }

    @Test
    void openSegmentRequestReversal_isRejected() {
        // 图：start(0,0) --DOWN--> (0,10) --DOWN--> (0,20)，全部开放
        PatrolController c = new PatrolController(straightGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());
        // 走到中间段（未到节点中心）
        tick(c, 0, Direction.DOWN);
        // 段中间按反方向：held 不含当前朝向 → IDLE
        PlayerKinematics result = tick(c, 1, Direction.UP);
        assertEquals(MovementState.IDLE, result.movementState(), "开放路段中间不允许反方向");
    }

    @Test
    void closedDoorBlockingDeadEnd_requestReversal_isAllowed() {
        // 图：start(0,-4) --DOWN--> deadEnd(0,0) --DOWN--> (0,4)
        // 玩家按 DOWN 到 deadEnd 中心，但 DOWN 出口被关门挡住
        PatrolController c = new PatrolController(threeNodeGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());
        ExitPassability closedDown = (from, exit, target) ->
                !(from.id().equals("deadEnd") && exit.direction() == Direction.DOWN);

        for (long t = 0; t < 3; t++) {
            c.advance(t, Set.of(Direction.DOWN), Optional.empty(), closedDown);
        }
        // 在 deadEnd 按 UP（反方向）
        PlayerKinematics result = c.advance(3, Set.of(Direction.UP), Optional.of(Direction.UP), closedDown);
        assertEquals(Direction.UP, result.direction(), "被关门挡住的死路应允许掉头");
        assertEquals(MovementState.CRUISING, result.movementState());
    }

    @Test
    void junctionWithSideExit_stillRejectsReversal() {
        // 图：start(0,-4) --DOWN--> jct(0,0)，jct 有 UP / DOWN / RIGHT
        PatrolController c = new PatrolController(junctionGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());
        for (long t = 0; t < 3; t++) {
            tick(c, t, Direction.DOWN);
        }
        // jct 有 RIGHT 出口，不是死路，请求反方向应被拒绝
        PlayerKinematics result = tick(c, 3, Direction.UP);
        assertEquals(MovementState.IDLE, result.movementState(), "有 90° 出口的节点不允许掉头");
    }

    private static OrthogonalPathGraph deadEndGraph() {
        return new OrthogonalPathGraph(List.of(
                node("start", 0.0, -4.0, List.of(new PathExit(Direction.DOWN, "deadEnd"))),
                node("deadEnd", 0.0, 0.0, List.of(new PathExit(Direction.UP, "start")))));
    }

    private static OrthogonalPathGraph straightGraph() {
        return new OrthogonalPathGraph(List.of(
                node("start", 0.0, 0.0, List.of(new PathExit(Direction.DOWN, "mid"))),
                node("mid", 0.0, 10.0, List.of(new PathExit(Direction.DOWN, "end"))),
                node("end", 0.0, 20.0, List.of(new PathExit(Direction.UP, "mid")))));
    }

    private static OrthogonalPathGraph threeNodeGraph() {
        return new OrthogonalPathGraph(List.of(
                node("start", 0.0, -4.0, List.of(new PathExit(Direction.DOWN, "deadEnd"))),
                node("deadEnd", 0.0, 0.0, List.of(
                        new PathExit(Direction.UP, "start"),
                        new PathExit(Direction.DOWN, "south"))),
                node("south", 0.0, 4.0, List.of(new PathExit(Direction.UP, "deadEnd")))));
    }

    private static OrthogonalPathGraph junctionGraph() {
        return new OrthogonalPathGraph(List.of(
                node("start", 0.0, -4.0, List.of(new PathExit(Direction.DOWN, "jct"))),
                node("jct", 0.0, 0.0, List.of(
                        new PathExit(Direction.UP, "start"),
                        new PathExit(Direction.DOWN, "south"),
                        new PathExit(Direction.RIGHT, "east"))),
                node("south", 0.0, 4.0, List.of(new PathExit(Direction.UP, "jct"))),
                node("east", 4.0, 0.0, List.of(new PathExit(Direction.LEFT, "jct")))));
    }

    private static PathNode node(String id, double x, double y, List<PathExit> exits) {
        return new PathNode(id, new PathPoint(x, y), exits);
    }
}