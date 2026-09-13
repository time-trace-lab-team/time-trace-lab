package org.example.timeloop.mechanism;

import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.example.timeloop.mechanism.event.GameEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 机关快照的不可变性与恢复语义。
 *
 * <p>BUG-002-LIFECYCLE Phase 1 起，每个用例使用**独立的注册表与事件总线**，
 * 不再依赖也不再手工清理全局单例。</p>
 */
class MechanismSnapshotTest {

    private DockingPlateRegistry registry;
    private EventDispatcher bus;

    @BeforeEach
    void freshMechanismScope() {
        registry = new DockingPlateRegistry();
        bus = new EventDispatcher();
    }

    private DockingPlate plate(String id, double x, double y) {
        return new DockingPlate(id, new Vector2D(x, y), registry, bus);
    }

    private Door door(String id, double x, double y, Set<String> plates) {
        return new Door(id, new Vector2D(x, y), plates, registry, bus);
    }

    private ExitTerminal exit(String id, double x, double y, String doorId) {
        return new ExitTerminal(id, new Vector2D(x, y), doorId,
                ExitTerminal.DEFAULT_INTERACT_RADIUS, bus);
    }

    @Test
    void snapshotsAreImmutableAndRestoreWithoutGameplayEvents() {
        DockingPlate plate = plate("L01_plate_left", 96.0, 240.0);
        Door door = door("L01_door_01", 240.0, 240.0, Set.of("L01_plate_left"));
        ExitTerminal exit = exit("L01_exit_00", 432.0, 240.0, "L01_door_01");

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
        bus.register(GameEvent.DOOR_UNLOCKED, event -> restoredEvents.incrementAndGet());

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
        DockingPlate firstPlate = plate("L01_plate_left", 96.0, 240.0);
        DockingPlate secondPlate = plate("L01_plate_right", 336.0, 240.0);
        Door firstDoor = door("L01_door_left", 192.0, 240.0, Set.of("L01_plate_left"));
        Door secondDoor = door("L01_door_right", 288.0, 240.0, Set.of("L01_plate_right"));
        ExitTerminal firstExit = exit("L01_exit_left", 432.0, 240.0, "L01_door_left");
        ExitTerminal secondExit = exit("L01_exit_right", 480.0, 240.0, "L01_door_right");

        assertThrows(IllegalArgumentException.class,
                () -> secondPlate.restore(firstPlate.createSnapshot()));
        assertThrows(IllegalArgumentException.class,
                () -> secondDoor.restore(firstDoor.createSnapshot()));
        assertThrows(IllegalArgumentException.class,
                () -> secondExit.restore(firstExit.createSnapshot()));
    }

    @Test
    void inconsistentPlateSnapshotIsRejectedBeforeStateChanges() {
        DockingPlate plate = plate("L01_plate_left", 96.0, 240.0);
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
