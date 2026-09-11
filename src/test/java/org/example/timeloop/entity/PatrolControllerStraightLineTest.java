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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * C-PLAYER-MOVE-01：四方向受约束移动 — 直线段行为。
 */
class PatrolControllerStraightLineTest {

    @Test
    void noInput_staysIdleAtStartCenter() {
        PatrolController c = controllerFor(Direction.DOWN);
        PlayerKinematics last = null;
        for (long tick = 0; tick < 60; tick++) {
            last = c.advance(tick, Optional.empty(), ExitPassability.allOpen());
        }
        assertEquals(0.0, last.x(), 1e-7);
        assertEquals(0.0, last.y(), 1e-7);
        assertEquals(59L, last.tick());
        assertEquals(MovementState.IDLE, last.movementState());
        assertEquals(ActorPhase.AVAILABLE, last.actorPhase());
        assertEquals(AnimationState.MOVING, last.animationState());
    }

    @Test
    void holdingDirection_movesAtBaseSpeedForSixtyTicks() {
        PatrolController c = controllerFor(Direction.DOWN);
        PlayerKinematics last = null;
        for (long tick = 0; tick < 60; tick++) {
            last = c.advance(tick, Optional.of(Direction.DOWN), ExitPassability.allOpen());
        }
        assertEquals(0.0, last.x(), 1e-7);
        assertEquals(120.0, last.y(), 1e-7);
        assertEquals(MovementState.CRUISING, last.movementState());
    }

    @Test
    void releaseDirection_stopsImmediatelyAtCurrentPosition() {
        PatrolController c = controllerFor(Direction.DOWN);
        for (long tick = 0; tick < 10; tick++) {
            c.advance(tick, Optional.of(Direction.DOWN), ExitPassability.allOpen());
        }
        PlayerKinematics stopped = c.advance(10, Optional.empty(), ExitPassability.allOpen());
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
        PlayerKinematics k = controllerFor(direction)
                .advance(0, Optional.of(direction), ExitPassability.allOpen());
        assertEquals(expectedX, k.x(), 1e-7, direction + " x");
        assertEquals(expectedY, k.y(), 1e-7, direction + " y");
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