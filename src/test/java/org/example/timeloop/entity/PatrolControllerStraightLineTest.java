package org.example.timeloop.entity;

import org.example.timeloop.core.ActorPhase;
import org.example.timeloop.core.AnimationState;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

class PatrolControllerStraightLineTest {

    private static PlayerKinematics tick(PatrolController c, long t, Direction... held) {
        return c.advance(t, Set.of(held), Optional.empty(), ExitPassability.allOpen());
    }

    private static PlayerKinematics tickEdge(PatrolController c, long t, Direction edge, Direction... held) {
        return c.advance(t, Set.of(held), Optional.of(edge), ExitPassability.allOpen());
    }

    @Test
    void noInput_staysIdleAtStartCenter() {
        PatrolController c = controllerFor(Direction.DOWN);
        PlayerKinematics last = null;
        for (long t = 0; t < 60; t++) {
            last = c.advance(t, Set.of(), Optional.empty(), ExitPassability.allOpen());
        }
        assertEquals(0.0, last.x(), 1e-7);
        assertEquals(0.0, last.y(), 1e-7);
        assertEquals(MovementState.IDLE, last.movementState());
        assertEquals(ActorPhase.AVAILABLE, last.actorPhase());
        assertEquals(AnimationState.MOVING, last.animationState());
    }

    @Test
    void holdingDirection_movesAtBaseSpeedForSixtyTicks() {
        PatrolController c = controllerFor(Direction.DOWN);
        PlayerKinematics last = null;
        for (long t = 0; t < 60; t++) {
            last = tick(c, t, Direction.DOWN);
        }
        assertEquals(0.0, last.x(), 1e-7);
        assertEquals(120.0, last.y(), 1e-7);
        assertEquals(MovementState.CRUISING, last.movementState());
    }

    @Test
    void speedModifierChangesDistanceAndMovementStateWithoutChangingAdvanceSignature() {
        PatrolController slowed = new PatrolController(
                graphFor(Direction.DOWN), "start", Direction.DOWN, PatrolConfig.c2Greybox(), () -> 0.5);
        PlayerKinematics slowFrame = tick(slowed, 0, Direction.DOWN);

        assertEquals(1.0, slowFrame.y(), 1e-7);
        assertEquals(MovementState.SLOWED, slowFrame.movementState());

        PatrolController normal = new PatrolController(
                graphFor(Direction.DOWN), "start", Direction.DOWN, PatrolConfig.c2Greybox(), () -> 1.0);
        PlayerKinematics normalFrame = tick(normal, 0, Direction.DOWN);

        assertEquals(2.0, normalFrame.y(), 1e-7);
        assertEquals(MovementState.CRUISING, normalFrame.movementState());
    }

    @Test
    void releaseDirection_stopsImmediately() {
        PatrolController c = controllerFor(Direction.DOWN);
        for (long t = 0; t < 10; t++) {
            tick(c, t, Direction.DOWN);
        }
        PlayerKinematics stopped = c.advance(10, Set.of(), Optional.empty(), ExitPassability.allOpen());
        assertEquals(0.0, stopped.x(), 1e-7);
        assertEquals(20.0, stopped.y(), 1e-7);
        assertEquals(MovementState.IDLE, stopped.movementState());
    }

    @Test
    void advancesAllFourDirectionsInWorldCoordinates() {
        assertOneTick(Direction.UP, 0.0, -2.0);
        assertOneTick(Direction.RIGHT, 2.0, 0.0);
        assertOneTick(Direction.DOWN, 0.0, 2.0);
        assertOneTick(Direction.LEFT, -2.0, 0.0);
    }

    @Test
    void requiresAnExplicitDeclaredInitialExit() {
        OrthogonalPathGraph graph = graphFor(Direction.RIGHT);
        assertThrows(IllegalArgumentException.class,
                () -> new PatrolController(graph, "start", Direction.UP, PatrolConfig.c2Greybox()));
    }

    private static void assertOneTick(Direction direction, double expectedX, double expectedY) {
        PlayerKinematics k = tickEdge(controllerFor(direction), 0, direction, direction);
        assertEquals(expectedX, k.x(), 1e-7);
        assertEquals(expectedY, k.y(), 1e-7);
        assertEquals(direction, k.direction());
    }

    private static PatrolController controllerFor(Direction direction) {
        return new PatrolController(graphFor(direction), "start", direction, PatrolConfig.c2Greybox());
    }

    private static OrthogonalPathGraph graphFor(Direction direction) {
        PathPoint end = switch (direction) {
            case UP -> new PathPoint(0.0, -200.0);
            case RIGHT -> new PathPoint(200.0, 0.0);
            case DOWN -> new PathPoint(0.0, 200.0);
            case LEFT -> new PathPoint(-200.0, 0.0);
        };
        return new OrthogonalPathGraph(List.of(
                new PathNode("start", new PathPoint(0.0, 0.0), List.of(new PathExit(direction, "end"))),
                new PathNode("end", end, List.of(new PathExit(opposite(direction), "start")))));
    }

    private static Direction opposite(Direction direction) {
        return switch (direction) {
            case UP -> Direction.DOWN;
            case RIGHT -> Direction.LEFT;
            case DOWN -> Direction.UP;
            case LEFT -> Direction.RIGHT;
        };
    }
}
