package org.example.timeloop.core.path;

import org.example.timeloop.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathNodeTest {

    @Test
    void preservesImmutableExitData() {
        PathNode node = new PathNode(
                "junction",
                new PathPoint(48.0, 48.0),
                List.of(new PathExit(Direction.RIGHT, "east"), new PathExit(Direction.DOWN, "south")));

        assertEquals("south", node.exitFor(Direction.DOWN).orElseThrow().targetNodeId());
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

}
