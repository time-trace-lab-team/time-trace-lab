package org.example.timeloop.mechanism.autodock;

import org.example.timeloop.level.Level01Footsteps;
import org.example.timeloop.level.model.PathNode;
import org.example.timeloop.level.model.Vector2D;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoDockSnapshotTest {

    @Test
    void snapshotRestoresOccupancyAsAnImmutableValue() {
        AutoDockService service = new AutoDockService(Level01Footsteps.build());
        AutoDockSnapshotPort snapshotPort = service;
        Vector2D center = new Vector2D(96.0, 240.0);

        assertEquals(AutoDockResult.Status.ENTERED,
                service.tryEnter("L01_plate_left", "echo_1", 1, 10, center).status());
        AutoDockStateSnapshot snapshot = snapshotPort.createSnapshot();

        assertThrows(UnsupportedOperationException.class, () -> snapshot.docks().clear());

        service.reset(AutoDockResetReason.ROUND_END, 11);
        assertFalse(service.findById("L01_plate_left").orElseThrow().occupancy().occupied());

        snapshotPort.restore(snapshot);
        snapshotPort.restore(snapshot);
        AutoDockView restored = service.findById("L01_plate_left").orElseThrow();
        assertTrue(restored.occupancy().occupied());
        assertEquals("echo_1", restored.occupancy().occupantId());
        assertEquals(1, restored.occupancy().occupantSourceRound());
        assertEquals(10, restored.occupancy().occupiedAtTick());
    }

    @Test
    void snapshotRestoresReentryBoundaryAndResetClearsEveryDynamicField() {
        AutoDockService service = new AutoDockService(Level01Footsteps.build());
        Vector2D center = new Vector2D(96.0, 240.0);

        service.tryEnter("L01_plate_left", "player", 1, 10, center);
        assertEquals(AutoDockResult.Status.LEFT, service.tryLeave(
                "L01_plate_left", "player", 1, 11, PathNode.Dir.UP,
                new Vector2D(96.0, 215.0)).status());
        AutoDockStateSnapshot afterLeave = service.createSnapshot();

        service.reset(AutoDockResetReason.ROUND_END, 12);
        service.restore(afterLeave);
        AutoDockView restored = service.findById("L01_plate_left").orElseThrow();
        assertFalse(restored.occupancy().occupied());
        assertEquals(11, restored.reentryBlockedAtTick());
        assertEquals(AutoDockResult.Status.SAME_TICK_REENTRY_BLOCKED,
                service.tryEnter("L01_plate_left", "echo_1", 1, 11, center).status());

        assertEquals(AutoDockResult.Status.ENTERED,
                service.tryEnter("L01_plate_left", "echo_1", 1, 12, center).status());
        service.reset(AutoDockResetReason.FULL_RESTART, 13);
        service.reset(AutoDockResetReason.FULL_RESTART, 13);
        service.reset(AutoDockResetReason.SCENE_EXIT, 14);

        AutoDockView cleared = service.findById("L01_plate_left").orElseThrow();
        assertFalse(cleared.occupancy().occupied());
        assertEquals(-1, cleared.reentryBlockedAtTick());
    }

    @Test
    void mismatchedSnapshotIsRejectedWithoutPartialRestore() {
        AutoDockService service = new AutoDockService(Level01Footsteps.build());
        Vector2D center = new Vector2D(96.0, 240.0);
        service.tryEnter("L01_plate_left", "echo_1", 1, 10, center);

        AutoDockStateSnapshot invalid = new AutoDockStateSnapshot(List.of(
                new AutoDockStateSnapshot.DockSnapshot(
                        "L01_plate_missing", DockOccupancyView.empty(), -1)));

        assertThrows(IllegalArgumentException.class, () -> service.restore(invalid));
        AutoDockView left = service.findById("L01_plate_left").orElseThrow();
        assertTrue(left.occupancy().occupied());
        assertEquals("echo_1", left.occupancy().occupantId());
    }

    @Test
    void completeSnapshotWithAdditionalUnknownIdIsRejected() {
        AutoDockService service = new AutoDockService(Level01Footsteps.build());
        Vector2D center = new Vector2D(96.0, 240.0);
        service.tryEnter("L01_plate_left", "echo_1", 1, 10, center);
        AutoDockStateSnapshot complete = service.createSnapshot();

        List<AutoDockStateSnapshot.DockSnapshot> withUnknownId =
                new ArrayList<>(complete.docks());
        withUnknownId.add(new AutoDockStateSnapshot.DockSnapshot(
                "L01_plate_unknown", DockOccupancyView.empty(), -1));
        AutoDockStateSnapshot invalid = new AutoDockStateSnapshot(withUnknownId);

        assertThrows(IllegalArgumentException.class, () -> service.restore(invalid));
        AutoDockView left = service.findById("L01_plate_left").orElseThrow();
        assertTrue(left.occupancy().occupied());
        assertEquals("echo_1", left.occupancy().occupantId());
    }
}
