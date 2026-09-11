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

class PatrolControllerNodeBehaviorTest {

    private static final double EPS = PatrolConfig.C2_EPSILON;

    private static PlayerKinematics tick(PatrolController c, long t, Direction... held) {
        return c.advance(t, Set.of(held), Optional.empty(), ExitPassability.allOpen());
    }

    private static PlayerKinematics tickEdge(PatrolController c, long t, Direction edge, Direction... held) {
        return c.advance(t, Set.of(held), Optional.of(edge), ExitPassability.allOpen());
    }

    @Test
    void noInputAtJunction_staysIdle() {
        PatrolController c = new PatrolController(lockedGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());
        PlayerKinematics last = null;
        for (long t = 0; t < 20; t++) {
            last = tick(c, t, Direction.DOWN);
        }
        assertEquals(0.0, last.x(), EPS);
        assertEquals(0.0, last.y(), EPS);
        assertEquals(MovementState.IDLE, last.movementState(), "直行不可通行时应在节点中心停下");
    }

    @Test
    void perpendicularMidSegment_stopsIdle() {
        PatrolController c = new PatrolController(lockedGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());
        tick(c, 0, Direction.DOWN);
        tick(c, 1, Direction.DOWN);
        PlayerKinematics result = tickEdge(c, 2, Direction.RIGHT, Direction.RIGHT);
        assertEquals(MovementState.IDLE, result.movementState());
    }

    @Test
    void perpendicularAtCenter_commitsTurn() {
        PatrolController c = new PatrolController(lockedGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());
        for (long t = 0; t < 4; t++) {
            tick(c, t, Direction.DOWN);
        }
        PlayerKinematics turned = tickEdge(c, 4, Direction.RIGHT, Direction.RIGHT);
        assertEquals(Direction.RIGHT, turned.direction());
        assertEquals(MovementState.CRUISING, turned.movementState());
    }

    @Test
    void closedDoorAhead_stopsAtNodeCenter() {
        PatrolController c = new PatrolController(lockedGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());
        ExitPassability closedDown = (from, exit, target) ->
                !(from.id().equals("junction") && exit.direction() == Direction.DOWN);
        PlayerKinematics last = null;
        for (long t = 0; t < 6; t++) {
            last = c.advance(t, Set.of(Direction.DOWN), Optional.empty(), closedDown);
        }
        assertEquals(0.0, last.x(), EPS);
        assertEquals(0.0, last.y(), EPS);
        assertEquals(MovementState.IDLE, last.movementState());
    }

    @Test
    void oppositeDirection_openSegment_staysIdle() {
        PatrolController c = new PatrolController(lockedGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());
        tick(c, 0, Direction.DOWN);
        tick(c, 1, Direction.DOWN);
        // 段中间按反方向：held 不含当前朝向 → IDLE
        PlayerKinematics result = tick(c, 2, Direction.UP);
        assertEquals(MovementState.IDLE, result.movementState());
    }

    @Test
    void sameInputs_produceIdenticalStates() {
        PatrolController a = new PatrolController(lockedGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());
        PatrolController b = new PatrolController(lockedGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());
        for (long t = 0; t < 10; t++) {
            Optional<Direction> edge = (t < 4) ? Optional.of(Direction.DOWN) : Optional.empty();
            PlayerKinematics ra = a.advance(t, Set.of(Direction.DOWN), edge, ExitPassability.allOpen());
            PlayerKinematics rb = b.advance(t, Set.of(Direction.DOWN), edge, ExitPassability.allOpen());
            assertEquals(ra, rb, "tick " + t);
        }
    }

    @Test
    void idleWritesFrameEveryTick() {
        PatrolController c = new PatrolController(lockedGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());
        for (long t = 0; t < 5; t++) {
            PlayerKinematics k = c.advance(t, Set.of(), Optional.empty(), ExitPassability.allOpen());
            assertEquals(t, k.tick());
            assertEquals(MovementState.IDLE, k.movementState());
        }
    }

    private static OrthogonalPathGraph lockedGraph() {
        return new OrthogonalPathGraph(List.of(
                node("start", 0.0, -8.0, List.of(new PathExit(Direction.DOWN, "junction"))),
                node("junction", 0.0, 0.0, List.of(
                        new PathExit(Direction.UP, "start"),
                        new PathExit(Direction.RIGHT, "east"))),
                node("east", 8.0, 0.0, List.of(new PathExit(Direction.LEFT, "junction")))));
    }

    private static PathNode node(String id, double x, double y, List<PathExit> exits) {
        return new PathNode(id, new PathPoint(x, y), exits);
    }
}