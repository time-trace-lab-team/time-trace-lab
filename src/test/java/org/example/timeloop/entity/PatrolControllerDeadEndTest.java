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
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * BUG-001：反向输入、节点吸附与直行穿节点。
 */
class PatrolControllerDeadEndTest {

    private static final double EPS = PatrolConfig.C2_EPSILON;

    private static PlayerKinematics tick(PatrolController c, long t, Direction... held) {
        return c.advance(t, Set.of(held), Optional.empty(), ExitPassability.allOpen());
    }

    private static PlayerKinematics tickEdge(PatrolController c, long t, Direction edge, Direction... held) {
        return c.advance(t, Set.of(held), Optional.of(edge), ExitPassability.allOpen());
    }

    // ========== P0-A：真死路豁免生效 ==========

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    void deadEndRequestReversal_isAllowed() {
        PatrolController c = new PatrolController(deadEndGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());
        for (long t = 0; t < 3; t++) {
            tick(c, t, Direction.DOWN);
        }
        PlayerKinematics result = tick(c, 3, Direction.UP);
        assertEquals(Direction.UP, result.direction(), "真死路应允许掉头");
        assertEquals(MovementState.CRUISING, result.movementState());
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    void openSegmentRequestReversal_isImmediate() {
        PatrolController c = new PatrolController(corridorGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());
        tick(c, 0, Direction.DOWN);
        PlayerKinematics reversed = tickEdge(c, 1, Direction.UP, Direction.UP);
        assertEquals(MovementState.CRUISING, reversed.movementState());
        assertEquals(Direction.UP, reversed.direction(), "段中间反方向应同 tick 生效");
        assertEquals(0.0, reversed.y(), EPS);
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    void closedDoorBlockingDeadEnd_requestReversal_isAllowed() {
        PatrolController c = new PatrolController(threeNodeGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());
        ExitPassability closedDown = (from, exit, target) ->
                !(from.id().equals("deadEnd") && exit.direction() == Direction.DOWN);

        for (long t = 0; t < 3; t++) {
            c.advance(t, Set.of(Direction.DOWN), Optional.empty(), closedDown);
        }
        PlayerKinematics result = c.advance(3, Set.of(Direction.UP), Optional.of(Direction.UP), closedDown);
        assertEquals(Direction.UP, result.direction(), "被关门挡住的死路应允许掉头");
        assertEquals(MovementState.CRUISING, result.movementState());
    }

    // ========== P0-D：真死路豁免严格前置条件 ==========

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    void junctionWithSideExit_allowsRequestedReversal() {
        PatrolController c = new PatrolController(junctionGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());
        for (long t = 0; t < 2; t++) {
            tick(c, t, Direction.DOWN);
        }
        PlayerKinematics result = tickEdge(c, 2, Direction.UP, Direction.UP);
        assertEquals(Direction.UP, result.direction(), "普通路口也允许玩家主动反向");
        assertEquals(MovementState.CRUISING, result.movementState());
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    void reversalAtSpawnWithoutExit_doesNotThrow() {
        // 出生点只有 DOWN 出口，按 UP 不应抛 NoSuchElementException
        PatrolController c = new PatrolController(deadEndGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());
        // 不移动，直接按 UP
        PlayerKinematics result = tick(c, 0, Direction.UP);
        assertEquals(MovementState.IDLE, result.movementState(), "无出口不应崩、应静止");
        assertEquals(Direction.DOWN, result.direction(), "朝向不变");
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    void reversalAtPlainCorridorNode_isAllowed() {
        // (5,2) 类普通直廊节点：UP/DOWN 都开放，按 UP 主动掉头。
        PatrolController c = new PatrolController(corridorGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());
        // start(0,-4) --DOWN--> mid(0,0) --DOWN--> end(0,4)
        // 走到 mid 中心
        for (long t = 0; t < 2; t++) {
            tick(c, t, Direction.DOWN);
        }
        PlayerKinematics result = tickEdge(c, 2, Direction.UP, Direction.UP);
        assertEquals(Direction.UP, result.direction());
        assertEquals(MovementState.CRUISING, result.movementState());
    }

    // ========== P0-E：直行穿节点不挂死 ==========

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    void straightThroughNode_continuesMoving() {
        // start(0,0) --DOWN--> mid(0,10) --DOWN--> end(0,20)
        PatrolController c = new PatrolController(straightGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());
        PlayerKinematics last = null;
        for (long t = 0; t < 10; t++) {
            last = tick(c, t, Direction.DOWN);
        }
        assertEquals(0.0, last.x(), EPS);
        assertEquals(20.0, last.y(), EPS);
        assertEquals(Direction.DOWN, last.direction());
        assertEquals(MovementState.CRUISING, last.movementState());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void straightThroughManyNodes_doesNotHang() {
        // 多节点直廊：start(0,0) --DOWN--> n1(0,5) --DOWN--> n2(0,10) --DOWN--> n3(0,15) --DOWN--> end(0,20)
        PatrolController c = new PatrolController(multiNodeGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());
        for (long t = 0; t < 30; t++) {
            tick(c, t, Direction.DOWN);
        }
    }

    // ========== 图形 ==========

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

    private static OrthogonalPathGraph corridorGraph() {
        return new OrthogonalPathGraph(List.of(
                node("start", 0.0, 0.0, List.of(new PathExit(Direction.DOWN, "mid"))),
                node("mid", 0.0, 5.0, List.of(
                        new PathExit(Direction.UP, "start"),
                        new PathExit(Direction.DOWN, "end"))),
                node("end", 0.0, 10.0, List.of(new PathExit(Direction.UP, "mid")))));
    }

    private static OrthogonalPathGraph multiNodeGraph() {
        return new OrthogonalPathGraph(List.of(
                node("start", 0.0, 0.0, List.of(new PathExit(Direction.DOWN, "n1"))),
                node("n1", 0.0, 5.0, List.of(
                        new PathExit(Direction.UP, "start"),
                        new PathExit(Direction.DOWN, "n2"))),
                node("n2", 0.0, 10.0, List.of(
                        new PathExit(Direction.UP, "n1"),
                        new PathExit(Direction.DOWN, "n3"))),
                node("n3", 0.0, 15.0, List.of(
                        new PathExit(Direction.UP, "n2"),
                        new PathExit(Direction.DOWN, "end"))),
                node("end", 0.0, 20.0, List.of(new PathExit(Direction.UP, "n3")))));
    }

    private static PathNode node(String id, double x, double y, List<PathExit> exits) {
        return new PathNode(id, new PathPoint(x, y), exits);
    }
}
