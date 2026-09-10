package org.example.timeloop.mechanism;

import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StableMechanismIdTest {

    @AfterEach
    void clearGlobalMechanismState() {
        EventDispatcher.getInstance().clear();
        DockingPlateRegistry.getInstance().clear();
    }

    @Test
    void dockingPlateKeepsCanonicalImmutableId() {
        DockingPlate plate = new DockingPlate("L01_plate_left", new Vector2D(96.0, 240.0));

        assertEquals("L01_plate_left", plate.getId());
        assertEquals(plate, DockingPlateRegistry.getInstance().get("L01_plate_left"));
    }

    @Test
    void duplicateDockingPlateIdIsRejectedInsteadOfOverwritten() {
        new DockingPlate("L01_plate_left", new Vector2D(96.0, 240.0));

        assertThrows(IllegalArgumentException.class,
                () -> new DockingPlate("L01_plate_left", new Vector2D(144.0, 240.0)));
        assertEquals(new Vector2D(96.0, 240.0),
                DockingPlateRegistry.getInstance().get("L01_plate_left").getPosition());
    }

    @Test
    void mechanismConstructorsRejectWrongIdKinds() {
        assertThrows(IllegalArgumentException.class,
                () -> new DockingPlate("L01_door_01", new Vector2D(96.0, 240.0)));
        assertThrows(IllegalArgumentException.class,
                () -> new Door("L01_plate_left", new Vector2D(240.0, 240.0), Set.of("L01_plate_left")));
        assertThrows(IllegalArgumentException.class,
                () -> new ExitTerminal("L01_plate_left", new Vector2D(432.0, 240.0), "L01_door_01"));
    }

    @Test
    void doorAndExitUseCanonicalReferences() {
        Door door = new Door("L01_door_01", new Vector2D(240.0, 240.0), Set.of("L01_plate_left"));
        ExitTerminal exit = new ExitTerminal("L01_exit_00", new Vector2D(432.0, 240.0), "L01_door_01");

        assertEquals("L01_door_01", door.getId());
        assertEquals("L01_exit_00", exit.getId());
    }
}
