package org.example.timeloop.level;

import org.example.timeloop.level.model.DoorInfo;
import org.example.timeloop.level.model.EntitySpawnInfo;
import org.example.timeloop.level.model.LevelData;
import org.example.timeloop.level.model.PathNode;
import org.example.timeloop.level.model.TileType;
import org.example.timeloop.level.model.Vector2D;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelDataValidatorTest {

    @Test
    void level01UsesCanonicalIdsAndExplicitReferences() {
        LevelData data = Level01Footsteps.build();

        assertEquals(Set.of("L01_plate_left", "L01_plate_right", "L01_exit_00"),
                data.getEntitySpawnList().stream().map(EntitySpawnInfo::getId).collect(java.util.stream.Collectors.toSet()));
        Set<String> pathNodeIds = data.getPathNodes().stream()
                .map(PathNode::getId)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(pathNodeIds.containsAll(Set.of(
                "L01_node_fork", "L01_node_left_end", "L01_node_right_end",
                "L01_node_exit_terminal")));
        assertEquals(17, pathNodeIds.size());
        assertEquals(Set.of("L01_plate_left", "L01_plate_right"),
                data.getDoors().get(0).getRequiredPlateIds());
        assertEquals("L01_node_left_end", data.getEntitySpawnList().get(0).getPathNodeId());
    }

    @Test
    void propertiesAreReadOnlySnapshots() {
        EntitySpawnInfo info = new EntitySpawnInfo(
                "L01_plate_left",
                "dock_plate",
                new Vector2D(48.0, 48.0),
                "L01_node_plate")
                .putProp("autoDock", true);

        Map<String, Object> snapshot = info.getProperties();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("other", false));

        info.putProp("afterBuild", true);
        assertFalse(snapshot.containsKey("afterBuild"), "getter 应返回不可变快照");
        assertTrue(info.getProperties().containsKey("afterBuild"));
    }

    @Test
    void rejectsBlankAndIllegalMechanismIds() {
        assertThrows(IllegalArgumentException.class, () -> new EntitySpawnInfo(
                " ", "dock_plate", new Vector2D(48.0, 48.0), "L01_node_plate"));
        assertThrows(IllegalArgumentException.class, () -> new EntitySpawnInfo(
                "plate_left", "dock_plate", new Vector2D(48.0, 48.0), "L01_node_plate"));
        assertThrows(IllegalArgumentException.class, () -> new DoorInfo(
                "door_1", new Vector2D(120.0, 48.0), false, Set.of("L01_plate_left")));
    }

    @Test
    void rejectsDuplicateMechanismIds() {
        EntitySpawnInfo first = plate("L01_plate_left", "L01_node_plate_left", 48.0, 48.0);
        EntitySpawnInfo duplicate = plate("L01_plate_left", "L01_node_plate_right", 96.0, 48.0);
        LevelData data = fixture(List.of(first, duplicate, exit()), Set.of("L01_plate_left"));

        assertThrows(IllegalArgumentException.class, () -> LevelDataValidator.validateFirstLevel(data));
    }

    @Test
    void rejectsDanglingPathNodeReference() {
        EntitySpawnInfo dangling = plate("L01_plate_left", "L01_node_missing", 48.0, 48.0);
        LevelData data = fixture(List.of(dangling, exit()), Set.of("L01_plate_left"));

        assertThrows(IllegalArgumentException.class, () -> LevelDataValidator.validateFirstLevel(data));
    }

    @Test
    void rejectsDanglingDoorPlateReference() {
        LevelData data = fixture(
                List.of(plate("L01_plate_left", "L01_node_plate_left", 48.0, 48.0), exit()),
                Set.of("L01_plate_missing"));

        assertThrows(IllegalArgumentException.class, () -> LevelDataValidator.validateFirstLevel(data));
    }

    @Test
    void rejectsMechanismTypeMismatch() {
        EntitySpawnInfo wrongType = new EntitySpawnInfo(
                "L01_plate_left",
                "exit_terminal",
                new Vector2D(48.0, 48.0),
                "L01_node_plate_left");
        LevelData data = fixture(List.of(wrongType, exit()), Set.of("L01_plate_left"));

        assertThrows(IllegalArgumentException.class, () -> LevelDataValidator.validateFirstLevel(data));
    }

    private static EntitySpawnInfo plate(String id, String nodeId, double x, double y) {
        return new EntitySpawnInfo(id, "dock_plate", new Vector2D(x, y), nodeId)
                .putProp("autoDock", true);
    }

    private static EntitySpawnInfo exit() {
        return new EntitySpawnInfo(
                "L01_exit_00",
                "exit_terminal",
                new Vector2D(144.0, 48.0),
                "L01_node_exit");
    }

    private static LevelData fixture(List<EntitySpawnInfo> entities, Set<String> requiredPlateIds) {
        List<PathNode> nodes = List.of(
                new PathNode("L01_node_plate_left", new Vector2D(48.0, 48.0), EnumSet.of(PathNode.Dir.UP)),
                new PathNode("L01_node_plate_right", new Vector2D(96.0, 48.0), EnumSet.of(PathNode.Dir.UP)),
                new PathNode("L01_node_exit", new Vector2D(144.0, 48.0), EnumSet.of(PathNode.Dir.UP))
        );
        return new LevelData(
                48.0,
                new TileType[0][0],
                nodes,
                entities,
                List.of(new DoorInfo("L01_door_01", new Vector2D(120.0, 48.0), false, requiredPlateIds)),
                new Vector2D(48.0, 48.0),
                960,
                3,
                1
        );
    }
}
