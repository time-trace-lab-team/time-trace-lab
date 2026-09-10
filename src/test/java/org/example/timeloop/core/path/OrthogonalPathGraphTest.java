package org.example.timeloop.core.path;

import org.example.timeloop.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrthogonalPathGraphTest {

    @Test
    void resolvesDeclaredNeighborsWithoutDependingOnCollectionOrder() {
        OrthogonalPathGraph graph = new OrthogonalPathGraph(List.of(
                node("west", 0.0, 0.0, List.of(new PathExit(Direction.RIGHT, "center"))),
                new PathNode(
                        "center",
                        new PathPoint(48.0, 0.0),
                        List.of(new PathExit(Direction.LEFT, "west"), new PathExit(Direction.DOWN, "south")),
                        Optional.of(Direction.DOWN)),
                node("south", 48.0, 48.0, List.of(new PathExit(Direction.UP, "center")))));

        PathNode center = graph.node("center");
        assertEquals("south", graph.neighbor(center, Direction.DOWN).orElseThrow().id());
        assertEquals(48.0, graph.segmentLength(center, Direction.DOWN));
        assertEquals(48.0, graph.shortestSegmentLength());
    }

    @Test
    void rejectsMissingNodeReferencesWithSourceContext() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new OrthogonalPathGraph(List.of(
                node("origin", 0.0, 0.0, List.of(new PathExit(Direction.RIGHT, "missing"))))));

        assertTrue(error.getMessage().contains("origin"));
        assertTrue(error.getMessage().contains("missing"));
    }

    @Test
    void rejectsZeroLengthSegments() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new OrthogonalPathGraph(List.of(
                node("origin", 0.0, 0.0, List.of(new PathExit(Direction.RIGHT, "same"))),
                node("same", 0.0, 0.0, List.of(new PathExit(Direction.LEFT, "origin"))))));

        assertTrue(error.getMessage().contains("zero-length"));
    }

    @Test
    void rejectsNonOrthogonalSegments() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new OrthogonalPathGraph(List.of(
                node("origin", 0.0, 0.0, List.of(new PathExit(Direction.RIGHT, "diagonal"))),
                node("diagonal", 48.0, 48.0, List.of(new PathExit(Direction.LEFT, "origin"))))));

        assertTrue(error.getMessage().contains("non-orthogonal"));
    }

    @Test
    void rejectsExitDirectionThatDoesNotMatchGeometry() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new OrthogonalPathGraph(List.of(
                node("origin", 0.0, 0.0, List.of(new PathExit(Direction.DOWN, "east"))),
                node("east", 48.0, 0.0, List.of(new PathExit(Direction.LEFT, "origin"))))));

        assertTrue(error.getMessage().contains("does not match"));
    }

    private static PathNode node(String id, double x, double y, List<PathExit> exits) {
        return new PathNode(id, new PathPoint(x, y), exits);
    }
}
