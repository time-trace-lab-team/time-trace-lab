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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PatrolControllerStraightLineTest {

    @Test
    void cruisesForSixtyTicksWithoutAnyQueuedDirection() {
        PatrolController controller = controllerFor(Direction.DOWN);
        PlayerKinematics last = null;

        for (long tick = 0; tick < 60; tick++) {
            last = controller.advance(tick, ExitPassability.allOpen());
        }

        assertEquals(0.0, last.x(), 0.0000001);
        assertEquals(120.0, last.y(), 0.0000001);
        assertEquals(59L, last.tick());
        assertEquals(Direction.DOWN, last.direction());
        assertEquals(MovementState.CRUISING, last.movementState());
        assertEquals(ActorPhase.AVAILABLE, last.actorPhase());
        assertEquals(0, last.actorPhaseTicksRemaining());
        assertEquals(AnimationState.MOVING, last.animationState());
    }

    @Test
    void advancesAllFourDirectionsInWorldCoordinates() {
        assertAfterOneTick(Direction.UP, 0.0, -2.0);
        assertAfterOneTick(Direction.RIGHT, 2.0, 0.0);
        assertAfterOneTick(Direction.DOWN, 0.0, 2.0);
        assertAfterOneTick(Direction.LEFT, -2.0, 0.0);
    }

    @Test
    void requiresAnExplicitDeclaredInitialExit() {
        OrthogonalPathGraph graph = graphFor(Direction.RIGHT);

        assertThrows(IllegalArgumentException.class,
                () -> new PatrolController(graph, "start", Direction.UP, PatrolConfig.c2Greybox()));
    }

    private static void assertAfterOneTick(Direction direction, double expectedX, double expectedY) {
        PlayerKinematics kinematics = controllerFor(direction).advance(0, ExitPassability.allOpen());
        assertEquals(expectedX, kinematics.x(), 0.0000001, direction + " x");
        assertEquals(expectedY, kinematics.y(), 0.0000001, direction + " y");
        assertEquals(direction, kinematics.direction());
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
