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
 * L01-GATE-MERGE-DEV2 §二.3：门与终点合并后，锁存开关的快照/恢复一致性。
 *
 * <p>验证两点：① 锁存位随快照往返（{@code capture} 透传 {@code isLatched()}），恢复到
 * 「开关锁存 ON（占用为空）+ 门解锁」后，门<b>不被重算锁回</b>，终点仍可交互；② 轮末
 * reset 路径清锁存、门回 LOCKED。</p>
 */
class MechanismSnapshotSwitchLatchTest {

    private static final Vector2D SWITCH_POS = new Vector2D(888.0, 408.0);
    private static final Vector2D LEFT_POS = new Vector2D(216.0, 312.0);
    private static final Vector2D DOOR_POS = new Vector2D(888.0, 360.0);
    private static final Vector2D EXIT_POS = new Vector2D(888.0, 312.0);

    @Test
    void restoreLatchedSwitchKeepsGateUnlocked() {
        Fixture fixture = fixture();

        // 触发开关锁存：踩上即 ON，离开不释放 → 锁存保持、真实占用清空。
        assertTrue(fixture.switchPlate().tryEnter("player", 0, 10));
        assertTrue(fixture.switchPlate().tryExit("player", 0, 40));
        assertTrue(fixture.switchPlate().isLatched(), "离开后锁存必须保持 ON");
        assertEquals(DockingPlate.State.UNOCCUPIED, fixture.switchPlate().getState());

        // 左板残影占用 → 双板条件满足 → 门解锁。
        assertTrue(fixture.leftPlate().tryEnter("echo_1", 1, 300));
        assertTrue(fixture.door().isUnlocked(), "开关锁存 + 左板占用 → 门解锁");
        assertTrue(fixture.exit().isDoorUnlocked(), "终点同步感知门已解锁");

        // 捕获快照，锁存位必须透传进快照。
        MechanismSnapshot snapshot = MechanismSnapshot.capture(
                fixture.plates(), fixture.doors(), fixture.exits());
        assertTrue(snapshot.getPlateSnapshots().get("L01_plate_right").isLatched(),
                "快照必须携带锁存位");

        // 轮末 / 重开：全部 reset，锁存回 OFF、门回 LOCKED。
        fixture.resetAll();
        assertFalse(fixture.switchPlate().isLatched());
        assertFalse(fixture.door().isUnlocked());

        // 恢复快照：锁存 ON（占用空）+ 左板残影占用 + 门解锁 + 终点未触发。
        snapshot.restore(fixture.plates(), fixture.doors(), fixture.exits());

        assertTrue(fixture.switchPlate().isLatched(), "恢复后锁存仍在");
        assertTrue(fixture.switchPlate().isOccupied(), "锁存 ON 即满足门条件");
        assertEquals(DockingPlate.State.UNOCCUPIED, fixture.switchPlate().getState(),
                "占用已空（玩家已离开开关）");
        assertEquals(null, fixture.switchPlate().getOccupantId());
        assertTrue(fixture.leftPlate().isOccupied(), "左板残影占用恢复");
        assertTrue(fixture.door().isUnlocked(), "门照抄快照 UNLOCKED，不被重算锁回");
        assertTrue(fixture.exit().isDoorUnlocked());
        assertFalse(fixture.exit().isTriggered());

        // 关键：终点可交互（门解锁 + 未触发）。
        assertTrue(fixture.exit().interact(999, 0), "恢复到锁存门解锁后终点应可交互");
    }

    @Test
    void roundEndResetClearsLatchedSwitch() {
        Fixture fixture = fixture();

        // 触发锁存 + 门解锁。
        assertTrue(fixture.switchPlate().tryEnter("player", 0, 10));
        assertTrue(fixture.switchPlate().tryExit("player", 0, 40));
        assertTrue(fixture.leftPlate().tryEnter("echo_1", 1, 300));
        assertTrue(fixture.switchPlate().isLatched());
        assertTrue(fixture.door().isUnlocked());

        // ROUND_END / FULL_RESTART / SCENE_EXIT 共用 reset()：锁存归零、门回 LOCKED。
        fixture.resetAll();

        assertFalse(fixture.switchPlate().isLatched(), "轮末 reset 必须清锁存");
        assertFalse(fixture.switchPlate().isOccupied());
        assertEquals(DockingPlate.State.UNOCCUPIED, fixture.switchPlate().getState());
        assertFalse(fixture.door().isUnlocked(), "门回到 LOCKED");
    }

    private static Fixture fixture() {
        DockingPlateRegistry registry = new DockingPlateRegistry();
        EventDispatcher bus = new EventDispatcher();

        DockingPlate switchPlate = new DockingPlate(
                "L01_plate_right", SWITCH_POS, registry, bus, true);
        DockingPlate leftPlate = new DockingPlate(
                "L01_plate_left", LEFT_POS, registry, bus);
        Door door = new Door(
                "L01_door_01", DOOR_POS, Set.of("L01_plate_left", "L01_plate_right"), registry, bus);
        ExitTerminal exit = new ExitTerminal(
                "L01_exit_00", EXIT_POS, door.getId(), 72.0, bus);

        Map<String, DockingPlate> plates = Map.of(
                switchPlate.getId(), switchPlate,
                leftPlate.getId(), leftPlate);
        Map<String, Door> doors = Map.of(door.getId(), door);
        Map<String, ExitTerminal> exits = Map.of(exit.getId(), exit);

        return new Fixture(switchPlate, leftPlate, door, exit, plates, doors, exits);
    }

    private record Fixture(
            DockingPlate switchPlate,
            DockingPlate leftPlate,
            Door door,
            ExitTerminal exit,
            Map<String, DockingPlate> plates,
            Map<String, Door> doors,
            Map<String, ExitTerminal> exits) {

        void resetAll() {
            switchPlate.reset();
            leftPlate.reset();
            door.reset();
            exit.reset();
        }
    }
}
