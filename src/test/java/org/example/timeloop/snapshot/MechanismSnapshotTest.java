package org.example.timeloop.snapshot;

import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.DockingPlate;
import org.example.timeloop.mechanism.DockingPlateRegistry;
import org.example.timeloop.mechanism.Door;
import org.example.timeloop.mechanism.ExitTerminal;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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
    void captureDefensivelyCopiesUnmodifiableCollections() {
        Fixture fixture = fixture();
        Map<String, DockingPlate> plates = new LinkedHashMap<>(plateMap(fixture));
        Map<String, Door> doors = new LinkedHashMap<>(doorMap(fixture));
        Map<String, ExitTerminal> exits = new LinkedHashMap<>(exitMap(fixture));

        MechanismSnapshot snapshot = MechanismSnapshot.capture(plates, doors, exits);
        plates.clear();
        doors.clear();
        exits.clear();

        assertEquals(Set.of("L01_plate_left"), snapshot.getPlateSnapshots().keySet());
        assertEquals(Set.of("L01_door_01"), snapshot.getDoorSnapshots().keySet());
        assertEquals(Set.of("L01_exit_00"), snapshot.getExitSnapshots().keySet());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.getPlateSnapshots().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.getDoorSnapshots().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.getExitSnapshots().clear());
    }

    @Test
    void captureAndRestoreAcceptEmptyCollections() {
        MechanismSnapshot snapshot = MechanismSnapshot.capture(Map.of(), Map.of(), Map.of());

        snapshot.restore(Map.of(), Map.of(), Map.of());

        assertTrue(snapshot.getPlateSnapshots().isEmpty());
        assertTrue(snapshot.getDoorSnapshots().isEmpty());
        assertTrue(snapshot.getExitSnapshots().isEmpty());
    }

    @Test
    void captureRejectsNullMapsAndMechanisms() {
        assertThrows(NullPointerException.class,
                () -> MechanismSnapshot.capture(null, Map.of(), Map.of()));

        Map<String, DockingPlate> plates = new LinkedHashMap<>();
        plates.put("L01_plate_left", null);
        assertThrows(NullPointerException.class,
                () -> MechanismSnapshot.capture(plates, Map.of(), Map.of()));
    }

    @Test
    void captureRejectsMapKeysThatDoNotMatchMechanismIds() {
        Fixture fixture = fixture();

        assertThrows(IllegalArgumentException.class,
                () -> MechanismSnapshot.capture(
                        Map.of("L01_plate_other", fixture.plate()), Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> MechanismSnapshot.capture(
                        Map.of(), Map.of("L01_door_other", fixture.door()), Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> MechanismSnapshot.capture(
                        Map.of(), Map.of(), Map.of("L01_exit_other", fixture.exit())));
    }

    @Test
    void restoreRejectsNullInputBeforeChangingAnyMechanism() {
        Fixture fixture = fixture();
        MechanismSnapshot snapshot = activatedSnapshot(fixture);
        reset(fixture);

        assertThrows(NullPointerException.class,
                () -> snapshot.restore(plateMap(fixture), null, exitMap(fixture)));

        assertReset(fixture);
    }

    @Test
    void restoreRejectsMismatchedMapKeyAndMechanismIdBeforeChangingState() {
        Fixture fixture = fixture();
        MechanismSnapshot snapshot = activatedSnapshot(fixture);
        reset(fixture);

        assertThrows(IllegalArgumentException.class,
                () -> snapshot.restore(
                        Map.of("L01_plate_other", fixture.plate()),
                        doorMap(fixture),
                        exitMap(fixture)));

        assertReset(fixture);
    }

    @Test
    void restoreRejectsMissingMechanismsBeforeChangingAnyState() {
        Fixture fixture = fixture();
        MechanismSnapshot snapshot = activatedSnapshot(fixture);
        reset(fixture);

        assertThrows(IllegalArgumentException.class,
                () -> snapshot.restore(plateMap(fixture), Map.of(), exitMap(fixture)));

        assertReset(fixture);
    }

    @Test
    void restoreRejectsAdditionalMechanismsBeforeChangingAnyState() {
        Fixture fixture = fixture();
        MechanismSnapshot snapshot = activatedSnapshot(fixture);
        reset(fixture);
        Door additionalDoor = new Door(
                "L01_door_other", new Vector2D(336.0, 240.0), Set.of("L01_plate_left"));
        Map<String, Door> doors = new LinkedHashMap<>(doorMap(fixture));
        doors.put(additionalDoor.getId(), additionalDoor);

        assertThrows(IllegalArgumentException.class,
                () -> snapshot.restore(plateMap(fixture), doors, exitMap(fixture)));

        assertReset(fixture);
    }

    @Test
    void restoreRestoresAllKnownMechanismStatesWhenIdsMatch() {
        Fixture fixture = fixture();
        MechanismSnapshot snapshot = activatedSnapshot(fixture);
        reset(fixture);

        snapshot.restore(plateMap(fixture), doorMap(fixture), exitMap(fixture));

        assertTrue(fixture.plate().isOccupied());
        assertEquals("echo_1", fixture.plate().getOccupantId());
        assertEquals(1, fixture.plate().getOccupantSourceRound());
        assertEquals(Door.State.UNLOCKED, fixture.door().getState());
        assertTrue(fixture.exit().isDoorUnlocked());
        assertTrue(fixture.exit().isTriggered());
    }

    private static MechanismSnapshot activatedSnapshot(Fixture fixture) {
        assertTrue(fixture.plate().tryEnter("echo_1", 1, 5));
        assertTrue(fixture.exit().interact(6, 1));
        return MechanismSnapshot.capture(plateMap(fixture), doorMap(fixture), exitMap(fixture));
    }

    private static void reset(Fixture fixture) {
        fixture.plate().reset();
        fixture.door().reset();
        fixture.exit().reset();
    }

    private static void assertReset(Fixture fixture) {
        assertFalse(fixture.plate().isOccupied());
        assertEquals(Door.State.LOCKED, fixture.door().getState());
        assertFalse(fixture.exit().isDoorUnlocked());
        assertFalse(fixture.exit().isTriggered());
    }

    private static Map<String, DockingPlate> plateMap(Fixture fixture) {
        return Map.of(fixture.plate().getId(), fixture.plate());
    }

    private static Map<String, Door> doorMap(Fixture fixture) {
        return Map.of(fixture.door().getId(), fixture.door());
    }

    private static Map<String, ExitTerminal> exitMap(Fixture fixture) {
        return Map.of(fixture.exit().getId(), fixture.exit());
    }

    private static Fixture fixture() {
        DockingPlate plate = new DockingPlate(
                "L01_plate_left", new Vector2D(96.0, 240.0));
        Door door = new Door(
                "L01_door_01", new Vector2D(240.0, 240.0), Set.of(plate.getId()));
        ExitTerminal exit = new ExitTerminal(
                "L01_exit_00", new Vector2D(432.0, 240.0), door.getId());
        return new Fixture(plate, door, exit);
    }

    private record Fixture(DockingPlate plate, Door door, ExitTerminal exit) {
    }
}
