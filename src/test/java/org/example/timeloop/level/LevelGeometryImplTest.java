package org.example.timeloop.level;

import org.example.timeloop.level.model.PathNode;
import org.example.timeloop.level.model.Vector2D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelGeometryImplTest {

    @Test
    void level01BuildsConnectedGeometry() {
        LevelGeometry geometry = new LevelGeometryImpl(Level01Footsteps.build());

        assertEquals(48.0, geometry.getTileSize());
        assertEquals(new Vector2D(240.0, 48.0), geometry.getSpawnPosition());
        assertTrue(geometry.hasNode("L01_node_fork"));
        assertTrue(geometry.isConnected("L01_node_fork", "L01_node_left_01"));
        assertTrue(geometry.isConnected("L01_node_fork", "L01_node_right_01"));
        assertTrue(geometry.isConnected("L01_node_right_approach", "L01_node_exit_approach"));
        assertTrue(geometry.isConnected("L01_node_exit_approach", "L01_node_exit_terminal"));
        assertEquals(PathNode.Dir.UP, geometry.getDefaultExit("L01_node_fork"));
        assertEquals(java.util.Set.of(PathNode.Dir.UP),
                geometry.getValidExits("L01_node_left_end"));
    }

    @Test
    void level01GeometryKeepsWallsAndClosedDoorData() {
        LevelGeometry geometry = new LevelGeometryImpl(Level01Footsteps.build());

        assertTrue(geometry.isWall(new Vector2D(0.0, 0.0)));
        assertFalse(geometry.isWall(new Vector2D(96.0, 240.0)));
        assertTrue(geometry.isDoorClosed("L01_door_01"));
        assertFalse(geometry.isDoorClosed("L01_door_missing"));
    }

    @Test
    void validExitAndNeighborViewsAreReadOnly() {
        LevelGeometry geometry = new LevelGeometryImpl(Level01Footsteps.build());

        assertThrows(UnsupportedOperationException.class,
                () -> geometry.getValidExits("L01_node_fork").clear());
        assertThrows(UnsupportedOperationException.class,
                () -> geometry.getNeighbors("L01_node_fork").clear());
    }

    @Test
    void pathNodeViewsAreDeeplyReadOnly() {
        LevelGeometry geometry = new LevelGeometryImpl(Level01Footsteps.build());
        PathNode fork = geometry.getPathNodes().stream()
                .filter(node -> "L01_node_fork".equals(node.getId()))
                .findFirst()
                .orElseThrow();

        assertThrows(UnsupportedOperationException.class,
                () -> fork.getAllowDirs().clear());
        assertEquals(PathNode.Dir.UP, fork.getDefaultExit());
        assertEquals(java.util.Set.of(
                        PathNode.Dir.UP,
                        PathNode.Dir.DOWN,
                        PathNode.Dir.LEFT,
                        PathNode.Dir.RIGHT),
                geometry.getValidExits("L01_node_fork"));
    }
}
