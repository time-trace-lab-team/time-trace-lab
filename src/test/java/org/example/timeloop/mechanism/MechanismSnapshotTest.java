package org.example.timeloop.mechanism;

import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.example.timeloop.mechanism.event.GameEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechanismSnapshotTest {

    @AfterEach
    void clearGlobalMechanismState() {
        EventDispatcher.getInstance().clear();
        DockingPlateRegistry.getInstance().clear();
    }

    @Test
    void snapshotsAreImmutableAndRestoreWithoutGameplayEvents() {
        DockingPlate plate = new DockingPlate(
                "L01_plate_left", new Vector2D(96.0, 240.0));
        Door door = new Door(
                "L01_door_01", new Vector2D(240.0, 240.0), Set.of("L01_plate_left"));
        ExitTerminal exit = new ExitTerminal(
                "L01_exit_00", new Vector2D(432.0, 240.0), "L01_door_01");

        assertTrue(plate.tryEnter("echo_1", 1, 5));
        assertTrue(exit.interact(6, 1));

        DockingPlate.Snapshot plateSnapshot = plate.createSnapshot();
        Door.Snapshot doorSnapshot = door.createSnapshot();
        ExitTerminal.Snapshot exitSnapshot = exit.createSnapshot();

        assertEquals("L01_plate_left", plateSnapshot.getMechanismId());
        assertEquals("L01_door_01", doorSnapshot.getMechanismId());
        assertEquals("L01_exit_00", exitSnapshot.getMechanismId());

        plate.reset();
        door.reset();
        exit.reset();
        assertEquals(DockingPlate.State.OCCUPIED, plateSnapshot.getState());
        assertEquals("echo_1", plateSnapshot.getOccupantId());
        assertEquals(1, plateSnapshot.getOccupantSourceRound());
        assertEquals(Door.State.UNLOCKED, doorSnapshot.getState());
        assertTrue(exitSnapshot.isDoorUnlocked());
        assertTrue(exitSnapshot.isTriggered());

        AtomicInteger restoredEvents = new AtomicInteger();
        EventDispatcher.getInstance().register(
                GameEvent.DOOR_UNLOCKED, event -> restoredEvents.incrementAndGet());

        plate.restore(plateSnapshot);
        door.restore(doorSnapshot);
        exit.restore(exitSnapshot);
        plate.restore(plateSnapshot);
        door.restore(doorSnapshot);
        exit.restore(exitSnapshot);

        assertTrue(plate.isOccupied());
        assertEquals("echo_1", plate.getOccupantId());
        assertEquals(1, plate.getOccupantSourceRound());
        assertEquals(Door.State.UNLOCKED, door.getState());
        assertTrue(exit.isDoorUnlocked());
        assertTrue(exit.isTriggered());
        assertEquals(0, restoredEvents.get(), "恢复不得重复派发 gameplay 事件");
    }

    @Test
    void restoreRejectsSnapshotsBelongingToAnotherMechanism() {
        DockingPlate firstPlate = new DockingPlate(
                "L01_plate_left", new Vector2D(96.0, 240.0));
        DockingPlate secondPlate = new DockingPlate(
                "L01_plate_right", new Vector2D(336.0, 240.0));
        Door firstDoor = new Door(
                "L01_door_left", new Vector2D(192.0, 240.0), Set.of("L01_plate_left"));
        Door secondDoor = new Door(
                "L01_door_right", new Vector2D(288.0, 240.0), Set.of("L01_plate_right"));
        ExitTerminal firstExit = new ExitTerminal(
                "L01_exit_left", new Vector2D(432.0, 240.0), "L01_door_left");
        ExitTerminal secondExit = new ExitTerminal(
                "L01_exit_right", new Vector2D(480.0, 240.0), "L01_door_right");

        assertThrows(IllegalArgumentException.class,
                () -> secondPlate.restore(firstPlate.createSnapshot()));
        assertThrows(IllegalArgumentException.class,
                () -> secondDoor.restore(firstDoor.createSnapshot()));
        assertThrows(IllegalArgumentException.class,
                () -> secondExit.restore(firstExit.createSnapshot()));
    }

    @Test
    void inconsistentPlateSnapshotIsRejectedBeforeStateChanges() {
        DockingPlate plate = new DockingPlate(
                "L01_plate_left", new Vector2D(96.0, 240.0));
        DockingPlate.Snapshot validSnapshot = plate.createSnapshot();

        assertThrows(IllegalArgumentException.class,
                () -> plate.restore(new DockingPlate.StateSnapshot(
                        "L01_plate_left",
                        DockingPlate.State.UNOCCUPIED,
                        "echo_1",
                        1)));
        assertThrows(IllegalArgumentException.class,
                () -> new ExitTerminal.StateSnapshot("L01_exit_00", false, true));

        plate.restore(validSnapshot);
        assertFalse(plate.isOccupied());
    }
}
