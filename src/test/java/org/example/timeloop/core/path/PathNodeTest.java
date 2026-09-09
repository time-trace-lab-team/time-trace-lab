package org.example.timeloop.core.path;

import org.example.timeloop.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathNodeTest {

    @Test
    void preservesImmutableExitDataAndDeclaredDefault() {
        PathNode node = new PathNode(
                "junction",
                new PathPoint(48.0, 48.0),
                List.of(new PathExit(Direction.RIGHT, "east"), new PathExit(Direction.DOWN, "south")),
                Optional.of(Direction.DOWN));

        assertEquals("south", node.exitFor(Direction.DOWN).orElseThrow().targetNodeId());
        assertEquals(Direction.DOWN, node.defaultExit().orElseThrow());
        assertThrows(UnsupportedOperationException.class,
                () -> node.exits().add(new PathExit(Direction.LEFT, "west")));
    }

    @Test
    void rejectsDuplicateDirectionInsteadOfSilentlyOverwritingIt() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new PathNode(
                "junction",
                new PathPoint(0.0, 0.0),
                List.of(new PathExit(Direction.RIGHT, "east-a"), new PathExit(Direction.RIGHT, "east-b"))));

        assertTrue(error.getMessage().contains("duplicate direction"));
        assertTrue(error.getMessage().contains("junction"));
    }

    @Test
    void rejectsDefaultExitThatWasNotDeclared() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new PathNode(
                "junction",
                new PathPoint(0.0, 0.0),
                List.of(new PathExit(Direction.RIGHT, "east")),
                Optional.of(Direction.LEFT)));

        assertTrue(error.getMessage().contains("defaultExit"));
    }
}
