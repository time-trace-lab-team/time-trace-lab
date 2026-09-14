package org.example.timeloop.mechanism;

import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.example.timeloop.mechanism.event.GameEvent;
import org.example.timeloop.mechanism.event.GameObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L01-GATE-MERGE-DEV3 §2.2 / §2.5-2、§2.5-5：开关变体的<b>本轮内锁存</b>语义。
 *
 * <p>锁存语义四件套：踩上 → ON；离开 → <b>仍 ON</b>；{@code reset()} → OFF；重复踩不改变状态。
 * 另加残影触发、非开关板回归保护、快照往返、以及「{@code Door} 行为不变也能看到 ON」。</p>
 */
class DockingPlateSwitchLatchTest {

    private static final Vector2D SWITCH_POS = new Vector2D(888.0, 408.0);
    private static final Vector2D LEFT_POS = new Vector2D(216.0, 312.0);

    private DockingPlateRegistry registry;
    private EventDispatcher bus;

    @BeforeEach
    void setUp() {
        registry = new DockingPlateRegistry();
        bus = new EventDispatcher();
    }

    @Test
    void steppingOnTheSwitchTurnsItOn() {
        DockingPlate switchPlate = switchPlate();

        assertFalse(switchPlate.isLatched(), "初始必须是 OFF");
        assertTrue(switchPlate.tryEnter("player", 0, 10));

        assertTrue(switchPlate.isLatched(), "踩上即开启");
        assertTrue(switchPlate.isOccupied(), "ON 状态同时满足门的条件");
        assertEquals(DockingPlate.State.OCCUPIED, switchPlate.getState());
    }

    @Test
    void leavingDoesNotTurnItOff() {
        DockingPlate switchPlate = switchPlate();
        switchPlate.tryEnter("player", 0, 10);

        assertTrue(switchPlate.tryExit("player", 0, 40));

        assertTrue(switchPlate.isLatched(), "离开不得清除锁存");
        assertTrue(switchPlate.isOccupied(), "锁存后门条件依然成立");
        assertEquals(DockingPlate.State.UNOCCUPIED, switchPlate.getState(), "真实占用已释放");
        assertEquals(null, switchPlate.getOccupantId(), "占用者已清空");
    }

    /** 轮末 / FULL_RESTART / 场景退出共用 {@code reset()}：三者都必须回到 OFF。 */
    @Test
    void resetTurnsItOff() {
        DockingPlate switchPlate = switchPlate();
        switchPlate.tryEnter("echo_1", 1, 10);
        switchPlate.tryExit("echo_1", 1, 40);

        switchPlate.reset();

        assertFalse(switchPlate.isLatched(), "reset 必须归零锁存（OFF）");
        assertFalse(switchPlate.isOccupied());
        assertEquals(DockingPlate.State.UNOCCUPIED, switchPlate.getState());
    }

    @Test
    void steppingAgainKeepsItOnAndIsIdempotent() {
        DockingPlate switchPlate = switchPlate();
        switchPlate.tryEnter("player", 0, 10);
        switchPlate.tryExit("player", 0, 40);

        assertTrue(switchPlate.tryEnter("player", 0, 100), "锁存不阻止再次踩上");
        assertTrue(switchPlate.isLatched(), "重复踩不改变状态（仍 ON）");
        assertTrue(switchPlate.tryExit("player", 0, 130));
        assertTrue(switchPlate.isLatched(), "再次离开也不得清除");
    }

    /** §2.5-5：残影踩开关同样置 ON（复用 PLATE_ENTERED，不新增事件类型）。 */
    @Test
    void echoCanTriggerTheSwitch() {
        DockingPlate switchPlate = switchPlate();
        List<String> events = new ArrayList<>();
        GameObserver observer = event -> events.add(event.eventType() + ":" + event.sourceRound());
        bus.register(GameEvent.PLATE_ENTERED, observer);

        assertTrue(switchPlate.tryEnter("echo_1", 1, 300));

        assertTrue(switchPlate.isLatched(), "残影也能开开关");
        assertTrue(switchPlate.tryExit("echo_1", 1, 320));
        assertTrue(switchPlate.isLatched(), "残影离开后仍 ON");
        assertEquals(List.of(GameEvent.PLATE_ENTERED + ":1"), events,
                "只允许用既有的 PLATE_ENTERED 事件");
    }

    /** 回归保护：非开关板行为完全不变。 */
    @Test
    void nonSwitchPlateNeverLatches() {
        DockingPlate plain = new DockingPlate("L01_plate_left", LEFT_POS, registry, bus);

        assertTrue(plain.tryEnter("player", 0, 10));
        assertFalse(plain.isLatched(), "普通驻留板不得有锁存行为");
        assertTrue(plain.tryExit("player", 0, 40));
        assertFalse(plain.isOccupied(), "普通驻留板离开即释放");
        assertFalse(plain.isLatched());
    }

    /** 快照必须携带锁存位，否则「已触发但人已离开」的状态会在恢复后丢失。 */
    @Test
    void snapshotCarriesTheLatchThroughRestore() {
        DockingPlate switchPlate = switchPlate();
        switchPlate.tryEnter("player", 0, 10);
        switchPlate.tryExit("player", 0, 40);
        assertTrue(switchPlate.isLatched());

        DockingPlate.Snapshot snapshot = switchPlate.createSnapshot();
        assertEquals(true, snapshot.isLatched(), "快照必须带锁存位");

        // 恢复目标必须是同一 ID 的另一个实例：用独立注册表，避免与源实例的 ID 冲突。
        DockingPlate restored = new DockingPlate(
                "L01_plate_right", SWITCH_POS, new DockingPlateRegistry(), new EventDispatcher(), true);
        restored.restore(snapshot);

        assertTrue(restored.isLatched(), "恢复后锁存仍在");
        assertTrue(restored.isOccupied());
        assertEquals(DockingPlate.State.UNOCCUPIED, restored.getState());
    }

    /** 旧的四参快照（无锁存位）仍可用，语义等价于 OFF —— 保证 snapshot/** 不被本卡打断。 */
    @Test
    void legacySnapshotWithoutLatchStillRestores() {
        DockingPlate switchPlate = switchPlate();
        DockingPlate.Snapshot legacy = new DockingPlate.StateSnapshot(
                "L01_plate_right", DockingPlate.State.UNOCCUPIED, null, 0);

        switchPlate.restore(legacy);

        assertFalse(switchPlate.isLatched());
        assertFalse(switchPlate.isOccupied());
    }

    /**
     * PM 追加确认项（2026-09-14）：残影淘汰只释放占用，<b>绝不清锁存</b>。
     *
     * <p>若这里被清成 OFF，第 2 轮里「E₁ 踩开的开关」会在 E₁ 消散时凭空关掉，
     * 门随之重新上锁 → 关卡在轮末前就变成无解。</p>
     */
    @Test
    void echoDisappearanceReleasesOccupancyButKeepsTheLatch() {
        DockingPlate switchPlate = switchPlate();
        List<String> exited = new ArrayList<>();
        bus.register(GameEvent.PLATE_EXITED, event -> exited.add(event.eventType() + ":" + event.sourceRound()));

        assertTrue(switchPlate.tryEnter("echo_1", 1, 300));
        assertTrue(switchPlate.isLatched());

        bus.dispatch(GameEvent.echoDisappeared("L01_plate_right", 400, 1));

        assertEquals(DockingPlate.State.UNOCCUPIED, switchPlate.getState(), "占用必须被释放");
        assertEquals(null, switchPlate.getOccupantId(), "占用者必须清空");
        assertTrue(switchPlate.isLatched(), "ECHO_DISAPPEARED 不得清除锁存");
        assertTrue(switchPlate.isOccupied(), "锁存仍在 → 门条件依然成立");
        assertEquals(List.of(GameEvent.PLATE_EXITED + ":1"), exited);
    }

    /** Door 行为不变（卡 §三 禁止修改）：它只问端口 isOccupied，锁存后即视为满足。 */
    @Test
    void doorSeesTheLatchedSwitchWithoutAnyDoorChange() {
        DockingPlate switchPlate = switchPlate();
        DockingPlate leftPlate = new DockingPlate("L01_plate_left", LEFT_POS, registry, bus);
        Door door = new Door("L01_door_01", new Vector2D(888.0, 360.0),
                Set.of("L01_plate_left", "L01_plate_right"), registry, bus);

        switchPlate.tryEnter("player", 0, 10);
        switchPlate.tryExit("player", 0, 40);
        assertFalse(door.isUnlocked(), "只有开关 ON 时门仍关闭");

        assertTrue(leftPlate.tryEnter("echo_1", 1, 300));

        assertTrue(door.isUnlocked(), "开关 ON + 左板被占 → 门解锁");
        assertTrue(leftPlate.tryExit("echo_1", 1, 320));
        assertFalse(door.isUnlocked(), "左板释放后门重新上锁（Door 是实时门）");
        assertTrue(switchPlate.isLatched(), "但开关仍保持 ON");
    }

    private DockingPlate switchPlate() {
        return new DockingPlate("L01_plate_right", SWITCH_POS, registry, bus, true);
    }
}
