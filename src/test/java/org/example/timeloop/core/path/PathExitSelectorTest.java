package org.example.timeloop.core.path;

import org.example.timeloop.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PathExitSelectorTest {

    @Test
    void honorsAllFiveExitPriorityLevels() {
        OrthogonalPathGraph graph = junctionGraph();
        PathNode junction = graph.node("junction");

        PathExitDecision queued = select(graph, junction, Direction.DOWN, Direction.RIGHT, Set.of());
        assertEquals(Direction.RIGHT, queued.direction());
        assertEquals(true, queued.usedQueuedDirection());

        PathExitDecision straight = select(graph, junction, Direction.DOWN, null, Set.of());
        assertEquals(Direction.DOWN, straight.direction());

        PathExitDecision onlySide = select(graph, junction, Direction.DOWN, null, Set.of(Direction.DOWN, Direction.RIGHT));
        assertEquals(Direction.LEFT, onlySide.direction());

        PathExitDecision defaultExit = select(
                graph, junction, Direction.DOWN, null, Set.of(Direction.DOWN));
        assertEquals(Direction.RIGHT, defaultExit.direction());

        PathExitDecision reverse = select(
                graph, junction, Direction.DOWN, null, Set.of(Direction.DOWN, Direction.LEFT, Direction.RIGHT));
        assertEquals(Direction.UP, reverse.direction());
    }

    @Test
    void dropsAReverseOrClosedQueuedDirectionAndContinuesWithPolicy() {
        OrthogonalPathGraph graph = junctionGraph();
        PathNode junction = graph.node("junction");

        PathExitDecision reverseInput = select(graph, junction, Direction.DOWN, Direction.UP, Set.of());
        assertEquals(Direction.DOWN, reverseInput.direction());
        assertFalse(reverseInput.usedQueuedDirection());

        PathExitDecision closedInput = select(
                graph, junction, Direction.DOWN, Direction.RIGHT, Set.of(Direction.RIGHT));
        assertEquals(Direction.DOWN, closedInput.direction());
        assertFalse(closedInput.usedQueuedDirection());
    }

    private static PathExitDecision select(
            OrthogonalPathGraph graph,
            PathNode node,
            Direction arrivalDirection,
            Direction queuedDirection,
            Set<Direction> blockedDirections) {
        return PathExitSelector.select(
                graph,
                node,
                arrivalDirection,
                queuedDirection,
                (from, exit, target) -> !blockedDirections.contains(exit.direction()));
    }

    private static OrthogonalPathGraph junctionGraph() {
        return new OrthogonalPathGraph(List.of(
                node("incoming", 0.0, -48.0, List.of(new PathExit(Direction.DOWN, "junction"))),
                new PathNode(
                        "junction",
                        new PathPoint(0.0, 0.0),
                        List.of(
                                new PathExit(Direction.UP, "incoming"),
                                new PathExit(Direction.RIGHT, "right"),
                                new PathExit(Direction.DOWN, "straight"),
                                new PathExit(Direction.LEFT, "left")),
                        Optional.of(Direction.RIGHT)),
                node("right", 48.0, 0.0, List.of(new PathExit(Direction.LEFT, "junction"))),
                node("straight", 0.0, 48.0, List.of(new PathExit(Direction.UP, "junction"))),
                node("left", -48.0, 0.0, List.of(new PathExit(Direction.RIGHT, "junction")))));
    }

    private static PathNode node(String id, double x, double y, List<PathExit> exits) {
        return new PathNode(id, new PathPoint(x, y), exits);
    }
}
