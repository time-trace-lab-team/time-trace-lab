package org.example.timeloop.level;

import org.example.timeloop.level.model.DoorInfo;
import org.example.timeloop.level.model.EntitySpawnInfo;
import org.example.timeloop.level.model.LevelData;
import org.example.timeloop.level.model.PathNode;
import org.example.timeloop.level.model.Vector2D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelGeometryImplTest {

    @Test
    void level01BuildsConnectedGeometry() {
        LevelGeometry geometry = new LevelGeometryImpl(Level01Footsteps.build());

        assertEquals(48.0, geometry.getTileSize());
        assertEquals(new Vector2D(264.0, 72.0), geometry.getSpawnPosition());
        assertTrue(geometry.hasNode("L01_node_fork"));
        assertTrue(geometry.isConnected("L01_node_fork", "L01_node_left_01"));
        assertTrue(geometry.isConnected("L01_node_fork", "L01_node_right_01"));
        assertTrue(geometry.isConnected("L01_node_right_approach", "L01_node_exit_approach"));
        assertTrue(geometry.isConnected("L01_node_exit_approach", "L01_node_exit_terminal"));
        assertNull(geometry.getDefaultExit("L01_node_fork"),
                "L-4a：第一关不再声明默认出口");
        assertEquals(java.util.Set.of(PathNode.Dir.UP),
                geometry.getValidExits("L01_node_left_end"));
    }

    @Test
    void level01PlacesWorldObjectsAtGridCellCenters() {
        LevelData level = Level01Footsteps.build();

        assertCellCenter(level.getSpawnPos(), level.getTileSize(), "spawn");
        for (PathNode node : level.getPathNodes()) {
            assertCellCenter(node.getWorldPos(), level.getTileSize(), node.getId());
        }
        for (EntitySpawnInfo entity : level.getEntitySpawnList()) {
            assertCellCenter(entity.getPos(), level.getTileSize(), entity.getId());
        }
        for (DoorInfo door : level.getDoors()) {
            assertCellCenter(door.getPosition(), level.getTileSize(), door.getId());
        }
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
        assertNull(fork.getDefaultExit(),
                "L-4a：路径节点只表达可通行性与中心线");
        assertEquals(java.util.Set.of(
                        PathNode.Dir.UP,
                        PathNode.Dir.DOWN,
                        PathNode.Dir.LEFT,
                        PathNode.Dir.RIGHT),
                geometry.getValidExits("L01_node_fork"));
    }

    private static void assertCellCenter(Vector2D position, double tileSize, String label) {
        double col = Math.floor(position.x() / tileSize);
        double row = Math.floor(position.y() / tileSize);
        assertEquals((col + 0.5) * tileSize, position.x(), 1.0e-9,
                label + " x 必须位于格子中心");
        assertEquals((row + 0.5) * tileSize, position.y(), 1.0e-9,
                label + " y 必须位于格子中心");
    }
}
