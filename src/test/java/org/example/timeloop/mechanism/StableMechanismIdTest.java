package org.example.timeloop.mechanism;

import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 稳定机制 ID 与构造校验。
 *
 * <p>BUG-002-LIFECYCLE Phase 1 起，每个用例使用**独立的注册表与事件总线**，
 * 不再依赖也不再手工清理全局单例。</p>
 */
class StableMechanismIdTest {

    private DockingPlateRegistry registry;
    private EventDispatcher bus;

    @BeforeEach
    void freshMechanismScope() {
        registry = new DockingPlateRegistry();
        bus = new EventDispatcher();
    }

    @Test
    void dockingPlateKeepsCanonicalImmutableId() {
        DockingPlate plate = new DockingPlate("L01_plate_left", new Vector2D(96.0, 240.0), registry, bus);

        assertEquals("L01_plate_left", plate.getId());
        assertEquals(plate, registry.get("L01_plate_left"));
    }

    @Test
    void duplicateDockingPlateIdIsRejectedInsteadOfOverwritten() {
        new DockingPlate("L01_plate_left", new Vector2D(96.0, 240.0), registry, bus);

        assertThrows(IllegalArgumentException.class,
                () -> new DockingPlate("L01_plate_left", new Vector2D(144.0, 240.0), registry, bus));
        assertEquals(new Vector2D(96.0, 240.0),
                registry.get("L01_plate_left").getPosition());
    }

    @Test
    void mechanismConstructorsRejectWrongIdKinds() {
        assertThrows(IllegalArgumentException.class,
                () -> new DockingPlate("L01_door_01", new Vector2D(96.0, 240.0), registry, bus));
        assertThrows(IllegalArgumentException.class,
                () -> new Door("L01_plate_left", new Vector2D(240.0, 240.0),
                        Set.of("L01_plate_left"), registry, bus));
        assertThrows(IllegalArgumentException.class,
                () -> new ExitTerminal("L01_plate_left", new Vector2D(432.0, 240.0),
                        "L01_door_01", ExitTerminal.DEFAULT_INTERACT_RADIUS, bus));
    }

    @Test
    void doorAndExitUseCanonicalReferences() {
        Door door = new Door("L01_door_01", new Vector2D(240.0, 240.0),
                Set.of("L01_plate_left"), registry, bus);
        ExitTerminal exit = new ExitTerminal("L01_exit_00", new Vector2D(432.0, 240.0),
                "L01_door_01", ExitTerminal.DEFAULT_INTERACT_RADIUS, bus);

        assertEquals("L01_door_01", door.getId());
        assertEquals("L01_exit_00", exit.getId());
    }
}
