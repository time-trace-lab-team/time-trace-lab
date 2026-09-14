package org.example.timeloop.snapshot;

import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.DockingPlate;
import org.example.timeloop.mechanism.DockingPlateRegistry;
import org.example.timeloop.mechanism.Door;
import org.example.timeloop.mechanism.ExitTerminal;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L02-A-DEV2 §二.3：快照/恢复覆盖第二关形状（3 块板 + 2 个门 + 1 个出口）。
 *
 * <p>多门是第二关首次出现的形状。本类锁定两点：</p>
 * <ol>
 *   <li>{@link MechanismSnapshot} 能捕获/恢复 3 板 + 2 门 + 1 出口（多门不互相干扰）；</li>
 *   <li>恢复到「房门解锁 + 闸门锁定」的中间态后，行为与恢复前一致；门与门相互独立。</li>
 * </ol>
 *
 * <p>第二关里门外板不锁存，锁存字段在 L2 无使用者；但恢复路径必须不破坏它
 * （L1 仍依赖）——本类顺带断言门外板恢复后锁存恒 false。</p>
 */
class MechanismSnapshotLevelTwoTest {

    private static final Vector2D DOOR_PLATE_POS = new Vector2D(100.0, 100.0);
    private static final Vector2D INNER_PLATE_POS = new Vector2D(200.0, 200.0);
    private static final Vector2D MAIN_PLATE_POS = new Vector2D(300.0, 300.0);
    private static final Vector2D ROOM_DOOR_POS = new Vector2D(150.0, 150.0);
    private static final Vector2D EXIT_DOOR_POS = new Vector2D(250.0, 250.0);
    private static final Vector2D EXIT_POS = new Vector2D(350.0, 350.0);

    @Test
    void restoringLevelTwoMidStateKeepsRoomsDoorUnlockedAndGateLocked() {
        DockingPlateRegistry registry = new DockingPlateRegistry();
        EventDispatcher bus = new EventDispatcher();

        DockingPlate doorPlate = new DockingPlate("L02_plate_door", DOOR_PLATE_POS, registry, bus);
        DockingPlate innerPlate = new DockingPlate("L02_plate_inner", INNER_PLATE_POS, registry, bus);
        DockingPlate mainPlate = new DockingPlate("L02_plate_main", MAIN_PLATE_POS, registry, bus);

        Door roomDoor = new Door("L02_door_room", ROOM_DOOR_POS,
                Set.of("L02_plate_door"), registry, bus);
        Door exitDoor = new Door("L02_door_exit", EXIT_DOOR_POS,
                Set.of("L02_plate_inner", "L02_plate_main"), registry, bus);
        ExitTerminal exit = new ExitTerminal(
                "L02_exit_00", EXIT_POS, exitDoor.getId(), 72.0, bus);

        // 中间态：门外板被残影占用 → 房门解锁；内板/主驻留板未同时占 → 闸门锁定。
        assertTrue(doorPlate.tryEnter("echo_1", 1, 40));
        assertTrue(roomDoor.isUnlocked(), "门外板占用 → 房门解锁");
        assertFalse(exitDoor.isUnlocked(), "内板/主驻留板未同时占 → 闸门锁定");
        assertFalse(exit.isDoorUnlocked(), "出口关联闸门，闸门锁定 → 出口未解锁");

        Map<String, DockingPlate> plates = Map.of(
                doorPlate.getId(), doorPlate,
                innerPlate.getId(), innerPlate,
                mainPlate.getId(), mainPlate);
        Map<String, Door> doors = Map.of(
                roomDoor.getId(), roomDoor,
                exitDoor.getId(), exitDoor);
        Map<String, ExitTerminal> exits = Map.of(exit.getId(), exit);
        MechanismSnapshot snapshot = MechanismSnapshot.capture(plates, doors, exits);

        // 捕获形状核对：3 板 + 2 门 + 1 出口。
        assertEquals(3, snapshot.getPlateSnapshots().size());
        assertEquals(2, snapshot.getDoorSnapshots().size());
        assertEquals(1, snapshot.getExitSnapshots().size());

        // 复位所有机关 → 恢复。
        doorPlate.reset();
        innerPlate.reset();
        mainPlate.reset();
        roomDoor.reset();
        exitDoor.reset();
        exit.reset();
        assertFalse(roomDoor.isUnlocked());
        assertFalse(exitDoor.isUnlocked());

        snapshot.restore(plates, doors, exits);

        // 恢复到中间态：房门解锁、闸门锁定、出口未解锁，与恢复前一致。
        assertTrue(roomDoor.isUnlocked(), "房门恢复为解锁");
        assertFalse(exitDoor.isUnlocked(), "闸门恢复为锁定");
        assertFalse(exit.isDoorUnlocked(), "出口保持未解锁");
        assertFalse(exit.isTriggered());
        assertTrue(doorPlate.isOccupied(), "门外板占用恢复");
        assertEquals("echo_1", doorPlate.getOccupantId());
        assertFalse(doorPlate.isLatched(), "L2 门外板不锁存，恢复后锁存恒 false");

        // 门相互独立：释放门外板 → 房门回锁，闸门不受影响（仍锁定）。
        assertTrue(doorPlate.tryExit("echo_1", 1, 60));
        assertFalse(roomDoor.isUnlocked(), "门外板释放 → 房门回锁");
        assertFalse(exitDoor.isUnlocked(), "闸门不受房门影响，仍锁定");
    }
}
