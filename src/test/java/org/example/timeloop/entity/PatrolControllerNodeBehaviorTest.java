package org.example.timeloop.entity;

import org.example.timeloop.core.Direction;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatrolControllerNodeBehaviorTest {

    private static final double EPSILON = PatrolConfig.C2_EPSILON;

    @Test
    void locksOneChoiceBeforeCenterAndPreservesLaterInputForNextNode() {
        PatrolController controller = new PatrolController(
                lockedQueueGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());
        controller.queueDirection(Direction.RIGHT);

        PlayerKinematics enteringLock = controller.advance(0, ExitPassability.allOpen());
        assertEquals(Direction.DOWN, enteringLock.direction(), "turn is not committed before the center");
        assertEquals(Direction.RIGHT, controller.lockedDirection().orElseThrow());
        assertFalse(controller.pendingDirection().isPresent());

        controller.queueDirection(Direction.DOWN);
        assertEquals(Direction.DOWN, controller.pendingDirection().orElseThrow());

        PlayerKinematics atFirstCenter = null;
        for (long tick = 1; tick <= 3; tick++) {
            atFirstCenter = controller.advance(tick, ExitPassability.allOpen());
        }
        assertEquals(0.0, atFirstCenter.x(), EPSILON);
        assertEquals(0.0, atFirstCenter.y(), EPSILON);
        assertEquals(Direction.RIGHT, atFirstCenter.direction());
        assertEquals(Direction.DOWN, controller.pendingDirection().orElseThrow(),
                "input submitted after the lock belongs to the next node");

        PlayerKinematics atSecondCenter = null;
        for (long tick = 4; tick <= 7; tick++) {
            atSecondCenter = controller.advance(tick, ExitPassability.allOpen());
        }
        assertEquals(8.0, atSecondCenter.x(), EPSILON);
        assertEquals(0.0, atSecondCenter.y(), EPSILON);
        assertEquals(Direction.DOWN, atSecondCenter.direction());
        assertFalse(controller.pendingDirection().isPresent());
    }

    @Test
    void commitsTurnAtCenterAndSpendsOvershootOnNewCenterLine() {
        PatrolController controller = new PatrolController(
                shortTurnGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());
        controller.queueDirection(Direction.RIGHT);

        PlayerKinematics end = controller.advance(0, ExitPassability.allOpen());

        assertEquals(Direction.RIGHT, end.direction());
        assertEquals(1.0, end.x(), EPSILON,
                "one world unit remains after reaching the center and must be consumed on the new segment");
        assertEquals(0.0, end.y(), EPSILON,
                "turning at the center snaps the perpendicular axis to the new center line");
    }

    @Test
    void latestUncommittedIntentReplacesTheEarlierOne() {
        PatrolController controller = new PatrolController(
                lockedQueueGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());
        controller.queueDirection(Direction.LEFT);
        controller.queueDirection(Direction.RIGHT);

        PlayerKinematics atCenter = null;
        for (long tick = 0; tick <= 3; tick++) {
            atCenter = controller.advance(tick, ExitPassability.allOpen());
        }

        assertEquals(Direction.RIGHT, atCenter.direction());
        assertFalse(controller.pendingDirection().isPresent());
    }

    @Test
    void excludesAClosedStraightExitAtTheNodeInsteadOfWaitingAtIt() {
        PatrolController controller = new PatrolController(
                shortTurnGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());

        PlayerKinematics end = controller.advance(0, (from, exit, target) ->
                !(from.id().equals("junction") && exit.direction() == Direction.DOWN));

        assertEquals(Direction.RIGHT, end.direction());
        assertEquals(1.0, end.x(), EPSILON);
        assertEquals(0.0, end.y(), EPSILON);
    }

    @Test
    void trueDeadEndAutomaticallyReversesInsteadOfStopping() {
        PatrolController controller = new PatrolController(
                deadEndGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());
        PlayerKinematics end = null;

        for (long tick = 0; tick < 5; tick++) {
            end = controller.advance(tick, ExitPassability.allOpen());
        }

        assertEquals(0.0, end.x(), EPSILON);
        assertEquals(0.0, end.y(), EPSILON);
        assertEquals(Direction.UP, end.direction());
    }

    @Test
    void consumesOneLargeTickBudgetAcrossMultipleShortSegments() {
        PatrolController controller = new PatrolController(
                shortSegmentGraph(), "n0", Direction.RIGHT, PatrolConfig.c2Greybox());

        PlayerKinematics end = controller.advance(0, ExitPassability.allOpen());

        assertEquals(2.0, end.x(), EPSILON);
        assertEquals(0.0, end.y(), EPSILON);
        assertEquals(Direction.RIGHT, end.direction());
    }

    @Test
    void holdsCenterLineWithoutDriftForTenAndOneThousandTurns() {
        assertStableTurns(10);
        assertStableTurns(1_000);
    }

    @Test
    void reproducesIdenticalTickEndStatesForEqualInputsAndPassabilitySequence() {
        PatrolController first = new PatrolController(squareGraph(), "a", Direction.RIGHT, PatrolConfig.c2Greybox());
        PatrolController second = new PatrolController(squareGraph(), "a", Direction.RIGHT, PatrolConfig.c2Greybox());

        for (long tick = 0; tick < 80; tick++) {
            if (tick % 11 == 0) {
                first.queueDirection(Direction.DOWN);
                second.queueDirection(Direction.DOWN);
            } else if (tick % 11 == 5) {
                first.queueDirection(Direction.LEFT);
                second.queueDirection(Direction.LEFT);
            }
            boolean blockLeftForThisTick = tick % 7 == 0;
            ExitPassability firstQuery = (from, exit, target) ->
                    !(blockLeftForThisTick && exit.direction() == Direction.LEFT);
            ExitPassability secondQuery = (from, exit, target) ->
                    !(blockLeftForThisTick && exit.direction() == Direction.LEFT);

            assertEquals(first.advance(tick, firstQuery), second.advance(tick, secondQuery), "tick " + tick);
        }
    }

    private static void assertStableTurns(int requiredTurns) {
        PatrolController controller = new PatrolController(squareGraph(), "a", Direction.RIGHT, PatrolConfig.c2Greybox());
        Direction previousDirection = controller.direction();
        int turns = 0;
        long tick = 0;

        while (turns < requiredTurns) {
            PlayerKinematics end = controller.advance(tick++, ExitPassability.allOpen());
            assertTrue(onSquareCenterLine(end.x(), end.y()),
                    () -> "off center line at tick " + end.tick() + ": (" + end.x() + ", " + end.y() + ")");
            if (end.direction() != previousDirection) {
                turns++;
                previousDirection = end.direction();
            }
            assertTrue(tick < 20_000, "a valid square graph must not miss a node or loop forever");
        }
    }

    private static boolean onSquareCenterLine(double x, double y) {
        return Math.abs(x) <= EPSILON
                || Math.abs(x - 10.0) <= EPSILON
                || Math.abs(y) <= EPSILON
                || Math.abs(y - 10.0) <= EPSILON;
    }

    private static OrthogonalPathGraph lockedQueueGraph() {
        return new OrthogonalPathGraph(List.of(
                node("start", 0.0, -8.0, List.of(new PathExit(Direction.DOWN, "junction"))),
                new PathNode(
                        "junction",
                        new PathPoint(0.0, 0.0),
                        List.of(
                                new PathExit(Direction.UP, "start"),
                                new PathExit(Direction.RIGHT, "east"),
                                new PathExit(Direction.DOWN, "south"),
                                new PathExit(Direction.LEFT, "west")),
                        Optional.of(Direction.RIGHT)),
                node("east", 8.0, 0.0, List.of(
                        new PathExit(Direction.LEFT, "junction"), new PathExit(Direction.DOWN, "eastSouth"))),
                node("eastSouth", 8.0, 8.0, List.of(new PathExit(Direction.UP, "east"))),
                node("south", 0.0, 8.0, List.of(new PathExit(Direction.UP, "junction"))),
                node("west", -8.0, 0.0, List.of(new PathExit(Direction.RIGHT, "junction")))));
    }

    private static OrthogonalPathGraph shortTurnGraph() {
        return new OrthogonalPathGraph(List.of(
                node("start", 0.0, -1.0, List.of(new PathExit(Direction.DOWN, "junction"))),
                new PathNode(
                        "junction",
                        new PathPoint(0.0, 0.0),
                        List.of(
                                new PathExit(Direction.UP, "start"),
                                new PathExit(Direction.RIGHT, "right"),
                                new PathExit(Direction.DOWN, "blockedStraight"),
                                new PathExit(Direction.LEFT, "left")),
                        Optional.of(Direction.RIGHT)),
                node("right", 10.0, 0.0, List.of(new PathExit(Direction.LEFT, "junction"))),
                node("blockedStraight", 0.0, 10.0, List.of(new PathExit(Direction.UP, "junction"))),
                node("left", -10.0, 0.0, List.of(new PathExit(Direction.RIGHT, "junction")))));
    }

    private static OrthogonalPathGraph deadEndGraph() {
        return new OrthogonalPathGraph(List.of(
                node("start", 0.0, -10.0, List.of(new PathExit(Direction.DOWN, "end"))),
                node("end", 0.0, 0.0, List.of(new PathExit(Direction.UP, "start")))));
    }

    private static OrthogonalPathGraph shortSegmentGraph() {
        return new OrthogonalPathGraph(List.of(
                node("n0", 0.0, 0.0, List.of(new PathExit(Direction.RIGHT, "n1"))),
                node("n1", 0.5, 0.0, List.of(new PathExit(Direction.RIGHT, "n2"))),
                node("n2", 1.0, 0.0, List.of(new PathExit(Direction.RIGHT, "n3"))),
                node("n3", 1.5, 0.0, List.of(new PathExit(Direction.RIGHT, "n4"))),
                node("n4", 2.0, 0.0, List.of(new PathExit(Direction.RIGHT, "n5"))),
                node("n5", 2.5, 0.0, List.of(new PathExit(Direction.LEFT, "n4")))));
    }

    private static OrthogonalPathGraph squareGraph() {
        return new OrthogonalPathGraph(List.of(
                node("a", 0.0, 0.0, List.of(new PathExit(Direction.RIGHT, "b"), new PathExit(Direction.DOWN, "d"))),
                node("b", 10.0, 0.0, List.of(new PathExit(Direction.LEFT, "a"), new PathExit(Direction.DOWN, "c"))),
                node("c", 10.0, 10.0, List.of(new PathExit(Direction.UP, "b"), new PathExit(Direction.LEFT, "d"))),
                node("d", 0.0, 10.0, List.of(new PathExit(Direction.RIGHT, "c"), new PathExit(Direction.UP, "a")))));
    }

    private static PathNode node(String id, double x, double y, List<PathExit> exits) {
        return new PathNode(id, new PathPoint(x, y), exits);
    }
}
