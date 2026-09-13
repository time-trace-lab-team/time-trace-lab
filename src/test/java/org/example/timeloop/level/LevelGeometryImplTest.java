package org.example.timeloop.level;

import org.example.timeloop.level.model.DoorInfo;
import org.example.timeloop.level.model.EntitySpawnInfo;
import org.example.timeloop.level.model.LevelData;
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
        assertEquals(new Vector2D(504.0, 120.0), geometry.getSpawnPosition());
        assertTrue(geometry.hasNode("L01_node_spawn"));
        // 出生点 (10,2) 是三度路口：向左到 (9,2)、向下到 (10,3) 两条不同分支都必须连通。
        assertTrue(geometry.isConnected("L01_node_spawn", "L01_node_c9_r2"));
        assertTrue(geometry.isConnected("L01_node_spawn", "L01_node_c10_r3"));
        // 右板前一个节点 → 右驻留板 → 出口终端，构成通关链路的最后一段。
        assertTrue(geometry.isConnected("L01_node_c17_r8", "L01_node_plate_right"));
        assertTrue(geometry.isConnected("L01_node_plate_right", "L01_node_exit_terminal"));
        assertEquals(java.util.Set.of(PathNode.Dir.RIGHT),
                geometry.getValidExits("L01_node_c3_r4"));
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
                () -> geometry.getValidExits("L01_node_spawn").clear());
        assertThrows(UnsupportedOperationException.class,
                () -> geometry.getNeighbors("L01_node_spawn").clear());
    }

    @Test
    void pathNodeViewsAreDeeplyReadOnly() {
        LevelGeometry geometry = new LevelGeometryImpl(Level01Footsteps.build());
        PathNode spawn = geometry.getPathNodes().stream()
                .filter(node -> "L01_node_spawn".equals(node.getId()))
                .findFirst()
                .orElseThrow();

        assertThrows(UnsupportedOperationException.class,
                () -> spawn.getAllowDirs().clear());
        assertEquals(java.util.Set.of(
                        PathNode.Dir.UP,
                        PathNode.Dir.DOWN,
                        PathNode.Dir.LEFT),
                geometry.getValidExits("L01_node_spawn"));
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
