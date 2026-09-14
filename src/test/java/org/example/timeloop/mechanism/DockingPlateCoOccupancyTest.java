package org.example.timeloop.mechanism;

import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.example.timeloop.mechanism.event.GameEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 驻留板多占用（L03-DEV3 工作令：四条加严 + 8 条测试）。
 *
 * <p><b>历史缺陷</b>：板只有一个占用槽位，第二个踩上来的人被 {@code tryEnter} 返回 {@code false}
 * 静默丢弃；此后先占槽位者一离开（或残影一消散），板就整体释放、门当刻回锁 ——
 * 画面上海有人站在板上，门却关了。三关同构，都中招。</p>
 *
 * <p><b>四条加严</b>：① 第二人不再被丢弃；② 退出只移除自己，板上还有人时保持占用；
 * ③ 残影消散只释放该残影自己；④ {@code isOccupied()} 由「占用者集合非空 或 锁存位」决定。</p>
 *
 * <p>门状态一律用真实 {@link Door} 读占用注册表来验 —— 缺陷的可见后果就是门被错误回锁。</p>
 */
class DockingPlateCoOccupancyTest {

    private static final String PLATE_ID = "L02_plate_door";
    private static final String DOOR_ID = "L02_door_gate";

    private DockingPlateRegistry registry;
    private EventDispatcher bus;
    private DockingPlate plate;
    private Door door;

    @BeforeEach
    void setUp() {
        registry = new DockingPlateRegistry();
        bus = new EventDispatcher();
        plate = new DockingPlate(PLATE_ID, new Vector2D(100.0, 100.0), registry, bus);
        door = new Door(DOOR_ID, new Vector2D(148.0, 100.0), Set.of(PLATE_ID), registry, bus);
    }

    // ---------- ① 第二人不再被丢弃 ----------

    @Test
    void secondActorIsRegisteredInsteadOfBeingSilentlyDropped() {
        assertTrue(plate.tryEnter("player", 0, 100L), "第一人进入");
        assertFalse(plate.isOccupied() == false, "板必须已占用");

        assertTrue(plate.tryEnter("echo_1", 1, 120L),
                "第二人必须被登记 —— 旧模型在这里返回 false 并被静默丢弃");
        assertEquals(List.of("player", "echo_1"), plate.getOccupantIds(), "按进入顺序");
        assertEquals(2, plate.getOccupantCount());
        assertEquals("player", plate.getOccupantId(), "主占用者 = 最先进入且仍在板上的人");
        assertEquals(0, plate.getOccupantSourceRound(), "主占用者的来源轮");
        assertTrue(plate.isOccupiedBy("player"));
        assertTrue(plate.isOccupiedBy("echo_1"));
        assertTrue(plate.isOccupied());
        assertTrue(door.isUnlocked(), "有人压着板，门必须开着");
    }

    // ---------- ② 退出只移除自己 ----------

    @Test
    void primaryOccupantLeavingKeepsThePlateOccupiedForTheRemainingActor() {
        plate.tryEnter("player", 0, 100L);
        plate.tryEnter("echo_1", 1, 120L);

        assertTrue(plate.tryExit("player", 0, 200L), "玩家离开");
        assertTrue(plate.isOccupied(),
                "残影还站在板上 → 板必须仍为占用（旧模型在这里整体释放）");
        assertTrue(door.isUnlocked(), "门绝不能在还有人压板时回锁");
        assertEquals(DockingPlate.State.OCCUPIED, plate.getState(), "此刻确实有人站着");
        assertEquals("echo_1", plate.getOccupantId(), "主占用者交给剩下最早进入的 echo_1");
        assertEquals(1, plate.getOccupantSourceRound(), "换主后来源轮要跟着换");
        assertEquals(List.of("echo_1"), plate.getOccupantIds());
    }

    // ---------- ③ 残影消散只释放自己 ----------

    @Test
    void echoDisappearingKeepsThePlateOccupiedWhileThePlayerStandsOnIt() {
        // 残影先占、玩家后到，然后残影寿命用尽（ECHO_DISAPPEARED）。
        plate.tryEnter("echo_1", 1, 100L);
        plate.tryEnter("player", 0, 140L);

        bus.dispatch(GameEvent.echoDisappeared("echo_1", 300L, 1));

        assertFalse(plate.isOccupiedBy("echo_1"), "消散的残影必须被移除");
        assertTrue(plate.isOccupiedBy("player"), "玩家仍在板上");
        assertTrue(plate.isOccupied(), "板必须仍为占用（旧模型在这里整体释放）");
        assertTrue(door.isUnlocked(), "玩家脚下的门绝不能因为残影消散而回锁");
        assertEquals("player", plate.getOccupantId(), "主占用者交给玩家");
        assertEquals(0, plate.getOccupantSourceRound());
    }

    // ---------- ④ 最后一人离开才释放 ----------

    @Test
    void plateReleasesAndDoorRelocksOnlyWhenTheLastActorLeaves() {
        plate.tryEnter("player", 0, 100L);
        plate.tryEnter("echo_1", 1, 120L);
        assertTrue(door.isUnlocked());

        plate.tryExit("echo_1", 1, 200L);
        assertTrue(plate.isOccupied(), "玩家还在 → 仍占用");
        assertTrue(door.isUnlocked(), "门仍开");

        assertTrue(plate.tryExit("player", 0, 220L), "最后一人离开");
        assertFalse(plate.isOccupied(), "板上无人且非锁存 → 释放");
        assertEquals(DockingPlate.State.UNOCCUPIED, plate.getState());
        assertEquals(List.of(), plate.getOccupantIds());
        assertEquals(null, plate.getOccupantId());
        assertEquals(0, plate.getOccupantSourceRound());
        assertFalse(door.isUnlocked(), "最后一人离开后门才回锁");
    }

    // ---------- 幂等与拒绝 ----------

    @Test
    void sameActorReenteringIsIdempotent() {
        assertTrue(plate.tryEnter("player", 0, 100L));
        assertFalse(plate.tryEnter("player", 0, 120L), "同一个人重复进入是 no-op");
        assertEquals(1, plate.getOccupantCount(), "不得重复计数");
        assertEquals(List.of("player"), plate.getOccupantIds());

        // 板上还有别人时，重复进入同样只是 no-op，不影响别人。
        plate.tryEnter("echo_1", 1, 140L);
        assertFalse(plate.tryEnter("player", 0, 160L));
        assertEquals(List.of("player", "echo_1"), plate.getOccupantIds());
    }

    @Test
    void nonOccupantExitIsRejectedAndLeavesOthersUntouched() {
        plate.tryEnter("player", 0, 100L);
        plate.tryEnter("echo_1", 1, 120L);

        assertFalse(plate.tryExit("echo_2", 2, 200L), "不在板上的人退出必须被拒");
        assertEquals(List.of("player", "echo_1"), plate.getOccupantIds(), "拒绝不得改动任何人");
        assertTrue(door.isUnlocked());

        assertThrows(IllegalArgumentException.class, () -> plate.tryEnter("  ", 0, 300L));
        assertThrows(IllegalArgumentException.class, () -> plate.tryExit(null, 0, 300L));
    }

    // ---------- 快照（加严到集合维度）----------

    @Test
    void snapshotRoundTripsEveryOccupantAndKeepsTheLatchRule() {
        DockingPlate switchPlate = new DockingPlate(
                "L01_plate_right", new Vector2D(200.0, 100.0), registry, bus, true);
        switchPlate.tryEnter("player", 0, 100L);
        switchPlate.tryEnter("echo_1", 1, 120L);
        switchPlate.tryExit("player", 0, 200L);

        DockingPlate.Snapshot snapshot = switchPlate.createSnapshot();
        assertEquals(List.of("echo_1"), snapshot.getOccupantIds(), "快照必须带上全部占用者");
        assertEquals("echo_1", snapshot.getOccupantId());
        assertTrue(snapshot.isLatched(), "锁存位随快照");

        DockingPlate restored = new DockingPlate(
                "L01_plate_right", new Vector2D(200.0, 100.0), new DockingPlateRegistry(), bus, true);
        restored.restore(snapshot);
        assertEquals(List.of("echo_1"), restored.getOccupantIds(), "恢复后占用集合一致");
        assertEquals("echo_1", restored.getOccupantId());
        assertTrue(restored.isLatched());
        assertTrue(restored.isOccupied());

        // 旧形态（单占用者、无锁存位）的快照仍可恢复：默认方法把单占用者包成单元素集合。
        DockingPlate plain = new DockingPlate(
                PLATE_ID, new Vector2D(100.0, 100.0), new DockingPlateRegistry(), bus);
        plain.restore(new DockingPlate.StateSnapshot(PLATE_ID,
                DockingPlate.State.OCCUPIED, "player", 0));
        assertEquals(List.of("player"), plain.getOccupantIds());

        // 非开关板不得恢复锁存位（既有校验保留）。
        assertThrows(IllegalArgumentException.class, () -> plain.restore(
                new DockingPlate.StateSnapshot(PLATE_ID, DockingPlate.State.OCCUPIED, "player", 0, true)));

        // 加严：集合必须自洽 —— 空集合不能是 OCCUPIED、主占用者必须在集合里、元素不得空白/重复。
        assertThrows(IllegalArgumentException.class, () -> new DockingPlate.StateSnapshot(
                PLATE_ID, DockingPlate.State.OCCUPIED, "player", 0, false, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new DockingPlate.StateSnapshot(
                PLATE_ID, DockingPlate.State.OCCUPIED, "player", 0, false, List.of("echo_1")));
        assertThrows(IllegalArgumentException.class, () -> new DockingPlate.StateSnapshot(
                PLATE_ID, DockingPlate.State.OCCUPIED, "player", 0, false, List.of("player", "player")));
        assertThrows(IllegalArgumentException.class, () -> new DockingPlate.StateSnapshot(
                PLATE_ID, DockingPlate.State.OCCUPIED, "player", 0, false, List.of("player", "  ")));
        assertThrows(IllegalArgumentException.class, () -> new DockingPlate.StateSnapshot(
                PLATE_ID, DockingPlate.State.UNOCCUPIED, null, 0, false, List.of("player")));
    }

    // ---------- 锁存开关在多人占用下语义不变 ----------

    @Test
    void latchingSwitchSemanticsUnchangedUnderCoOccupancy() {
        DockingPlate switchPlate = new DockingPlate(
                "L01_plate_right", new Vector2D(200.0, 100.0), registry, bus, true);

        assertTrue(switchPlate.tryEnter("player", 0, 100L));
        assertTrue(switchPlate.tryEnter("echo_1", 1, 120L), "两人可同时踩开关");
        assertTrue(switchPlate.isLatched());

        switchPlate.tryExit("player", 0, 200L);
        switchPlate.tryExit("echo_1", 1, 240L);
        assertFalse(switchPlate.isOccupiedBy("player"));
        assertFalse(switchPlate.isOccupiedBy("echo_1"));
        assertEquals(DockingPlate.State.UNOCCUPIED, switchPlate.getState(), "此刻真没人站着");
        assertTrue(switchPlate.isLatched(), "锁存保持到轮末");
        assertTrue(switchPlate.isOccupied(), "锁存位让门条件继续成立");

        // 残影消散只放占用、不清锁存（既有语义不变）。
        switchPlate.tryEnter("echo_2", 2, 300L);
        bus.dispatch(GameEvent.echoDisappeared("echo_2", 400L, 2));
        assertFalse(switchPlate.isOccupiedBy("echo_2"));
        assertTrue(switchPlate.isLatched(), "残影消散不得回滚已兑现的锁存");

        // 轮末 reset 才归零。
        switchPlate.reset();
        assertFalse(switchPlate.isLatched());
        assertFalse(switchPlate.isOccupied());
        assertEquals(List.of(), switchPlate.getOccupantIds());
    }
}
