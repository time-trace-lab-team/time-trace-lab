package org.example.timeloop.level;

import org.example.timeloop.level.model.DoorInfo;
import org.example.timeloop.level.model.EntitySpawnInfo;
import org.example.timeloop.level.model.LevelData;
import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.DockingPlate;
import org.example.timeloop.mechanism.DockingPlateRegistry;
import org.example.timeloop.mechanism.Door;
import org.example.timeloop.mechanism.ExitTerminal;
import org.example.timeloop.mechanism.autodock.AutoDockResult;
import org.example.timeloop.mechanism.autodock.AutoDockService;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.example.timeloop.mechanism.event.GameEvent;
import org.example.timeloop.mechanism.event.GameObserver;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第一关最小 headless 因果链（L01-GATE-MERGE 后）：<b>锁存开关 + 左板 + 闸门终点</b>。
 *
 * <p>新解法：第 1 轮玩家踩开关（锁存 ON）并停在左板 → 第 2 轮 {@code E₁} 复现这两段，
 * 门解锁，当前玩家到闸门按 E。闸门与终点同格 (18,7)，开关在 (18,8) 正下方一格。</p>
 */
class Level01CausalChainTest {

    private static final double TILE = 48.0;
    private static final double INTERACT_RADIUS = 72.0;

    /** §2.5-3 两轮通关：E1 开开关 + 压左板 → 当前玩家到闸门按 E 结算。 */
    @Test
    void level01IsSolvableInTwoRoundsThroughLatchedSwitchAndLeftPlate() {
        LevelData level = Level01Footsteps.build();
        assertEquals(16 * 60L, level.getDurationTicks());
        assertEquals(3, level.getMaxRounds());
        assertEquals(1, level.getEchoLifeL());

        DockingPlateRegistry registry = new DockingPlateRegistry();
        EventDispatcher dispatcher = new EventDispatcher();
        List<String> eventTrace = registerEventTrace(dispatcher);

        DockingPlate leftPlate = new DockingPlate(
                "L01_plate_left", entity(level, "L01_plate_left").getPos(), registry, dispatcher);
        DockingPlate switchPlate = new DockingPlate(
                "L01_plate_right", entity(level, "L01_plate_right").getPos(), registry, dispatcher, true);
        Door door = new Door("L01_door_01", doorPosition(level),
                java.util.Set.of("L01_plate_left", "L01_plate_right"), registry, dispatcher);
        ExitTerminal exit = new ExitTerminal("L01_exit_00", exitPosition(level), door.getId(),
                ExitTerminal.interactRadiusForTileSize(level.getTileSize()), dispatcher);
        AutoDockService autoDock = new AutoDockService(level);

        // ---- 第 1 轮：玩家踩开关（锁存 ON），再去压左板 ----
        assertTrue(switchPlate.tryEnter("player", 0, 100));
        assertTrue(switchPlate.isLatched(), "踩上即开启");
        assertTrue(switchPlate.tryExit("player", 0, 140), "离开开关");
        assertTrue(switchPlate.isLatched(), "离开后开关仍 ON");
        assertTrue(leftPlate.tryEnter("player", 0, 600));
        assertTrue(door.isUnlocked(), "开关 ON + 左板被占 → 门解锁");
        assertFalse(exit.isInInteractRange(leftPlate.getPosition()),
                "第 1 轮人在左板：闸门不在交互半径内，无法结算（app 侧按此判定）");

        // ---- 轮末：机关复位（开关回到 OFF），第 2 轮开始 ----
        switchPlate.reset();
        leftPlate.reset();
        assertFalse(switchPlate.isLatched(), "轮末必须回到 OFF");

        // ---- 第 2 轮：E1 复现第 1 轮（先踩开关，再压左板）----
        AutoDockResult echoDocked = autoDock.tryEnter(
                "L01_plate_right", "echo_1", 1, 200,
                entity(level, "L01_plate_right").getPos());
        assertEquals(AutoDockResult.Status.ENTERED, echoDocked.status());
        assertTrue(switchPlate.tryEnter("echo_1", 1, 200), "残影也能开开关");
        assertTrue(switchPlate.isLatched());
        assertTrue(switchPlate.tryExit("echo_1", 1, 230));

        assertFalse(door.isUnlocked(), "只有开关 ON 时门仍应关闭");
        assertTrue(leftPlate.tryEnter("echo_1", 1, 600), "E1 复现压左板");

        assertTrue(door.isUnlocked(), "开关 ON + 左板被 E1 占用 → 门解锁");
        assertTrue(exit.isDoorUnlocked());
        assertTrue(exit.isInInteractRange(switchPlate.getPosition()),
                "站在开关上应正好在闸门交互半径内（相距 1 格）");
        assertTrue(exit.interact(700, 0), "当前玩家到闸门后应能触发出口");
        assertFalse(exit.interact(701, 0), "出口触发后不得重复触发");

        assertEquals(List.of(
                        GameEvent.PLATE_ENTERED + ":L01_plate_right",
                        GameEvent.PLATE_EXITED + ":L01_plate_right",
                        GameEvent.PLATE_ENTERED + ":L01_plate_left",
                        GameEvent.DOOR_UNLOCKED + ":L01_door_01",
                        GameEvent.PLATE_ENTERED + ":L01_plate_right",
                        GameEvent.PLATE_EXITED + ":L01_plate_right",
                        GameEvent.PLATE_ENTERED + ":L01_plate_left",
                        GameEvent.DOOR_UNLOCKED + ":L01_door_01",
                        GameEvent.EXIT_TRIGGERED + ":L01_exit_00"),
                eventTrace);
    }

    /**
     * PM 裁决 §八.3 的官方验收链路：第二轮 <b>残影压左板 + 当前玩家踩开关</b> → 开门 → 到闸门按 E。
     *
     * <p>本用例额外锁住锁存的核心价值：<b>玩家离开开关后门不得重新上锁</b>。</p>
     */
    @Test
    void secondRoundPlayerPressesTheSwitchWhileEchoHoldsTheLeftPlate() {
        LevelData level = Level01Footsteps.build();
        DockingPlateRegistry registry = new DockingPlateRegistry();
        EventDispatcher dispatcher = new EventDispatcher();

        DockingPlate leftPlate = new DockingPlate(
                "L01_plate_left", entity(level, "L01_plate_left").getPos(), registry, dispatcher);
        DockingPlate switchPlate = new DockingPlate(
                "L01_plate_right", entity(level, "L01_plate_right").getPos(), registry, dispatcher, true);
        Door door = new Door("L01_door_01", doorPosition(level),
                java.util.Set.of("L01_plate_left", "L01_plate_right"), registry, dispatcher);
        ExitTerminal exit = new ExitTerminal("L01_exit_00", exitPosition(level), door.getId(),
                ExitTerminal.interactRadiusForTileSize(level.getTileSize()), dispatcher);

        // 第二轮开局：开关是 OFF 的（轮末 reset 过），残影已占左板
        assertFalse(switchPlate.isLatched());
        assertTrue(leftPlate.tryEnter("echo_1", 1, 300));
        assertFalse(door.isUnlocked(), "只有残影压着左板时门仍关闭");

        // 当前玩家踩开关：踩上即开启
        assertTrue(switchPlate.tryEnter("player", 0, 500));
        assertTrue(switchPlate.isLatched());
        assertTrue(door.isUnlocked(), "开关 ON + 左板被残影压住 → 门解锁");

        // 离开开关：锁存使门保持解锁 —— 这正是「不必站在开关上按 E」的依据
        assertTrue(switchPlate.tryExit("player", 0, 540));
        assertEquals(DockingPlate.State.UNOCCUPIED, switchPlate.getState());
        assertTrue(switchPlate.isLatched());
        assertTrue(door.isUnlocked(), "离开开关后门不得重新上锁");

        // 玩家走到闸门（与开关相距 1 格）按 E 结算
        assertTrue(exit.isInInteractRange(switchPlate.getPosition()));
        assertTrue(exit.interact(560, 0), "门解锁后当前玩家应能结算");
        assertFalse(exit.interact(561, 0), "出口不得重复触发");
    }

    /** §2.5-4 反例：只占左板时门不开；且站在左板上按 E 也无解（闸门不在交互半径内）。 */
    @Test
    void oneOccupiedPlateAndNoSwitchCannotBypassFirstLevelDoor() {
        LevelData level = Level01Footsteps.build();
        DockingPlateRegistry registry = new DockingPlateRegistry();
        EventDispatcher dispatcher = new EventDispatcher();

        DockingPlate leftPlate = new DockingPlate(
                "L01_plate_left", entity(level, "L01_plate_left").getPos(), registry, dispatcher);
        DockingPlate switchPlate = new DockingPlate(
                "L01_plate_right", entity(level, "L01_plate_right").getPos(), registry, dispatcher, true);
        Door door = new Door("L01_door_01", doorPosition(level),
                java.util.Set.of("L01_plate_left", "L01_plate_right"), registry, dispatcher);
        ExitTerminal exit = new ExitTerminal("L01_exit_00", exitPosition(level), door.getId(),
                ExitTerminal.interactRadiusForTileSize(level.getTileSize()), dispatcher);

        assertTrue(leftPlate.tryEnter("player", 0, 600));

        assertFalse(switchPlate.isLatched(), "开关未被踩，仍是 OFF");
        assertFalse(door.isUnlocked(), "缺开关时门必须保持关闭");
        assertFalse(exit.isDoorUnlocked());
        assertFalse(exit.isInInteractRange(leftPlate.getPosition()),
                "闸门不在左板交互半径内 → 单轮通关不成立");

        double leftToGate = Math.hypot(
                exit.getPosition().x() - leftPlate.getPosition().x(),
                exit.getPosition().y() - leftPlate.getPosition().y());
        assertTrue(leftToGate > INTERACT_RADIUS,
                "第 1 轮站在左板上必须够不到闸门终点（实测 " + leftToGate + "）");
    }

    /** 开关单独也不能开门（回归：门条件仍是两块板）。 */
    @Test
    void switchAloneCannotUnlockTheDoor() {
        LevelData level = Level01Footsteps.build();
        DockingPlateRegistry registry = new DockingPlateRegistry();
        EventDispatcher dispatcher = new EventDispatcher();

        DockingPlate switchPlate = new DockingPlate(
                "L01_plate_right", entity(level, "L01_plate_right").getPos(), registry, dispatcher, true);
        new DockingPlate("L01_plate_left", entity(level, "L01_plate_left").getPos(), registry, dispatcher);
        Door door = new Door("L01_door_01", doorPosition(level),
                java.util.Set.of("L01_plate_left", "L01_plate_right"), registry, dispatcher);

        assertTrue(switchPlate.tryEnter("player", 0, 100));

        assertTrue(switchPlate.isLatched());
        assertFalse(door.isUnlocked(), "只有开关 ON 时门必须保持关闭");
    }

    /** 闸门节点与开关必须在 1 格内（可读性 + 站在开关上也能按 E）。 */
    @Test
    void gateTerminalStaysWithinReachOfTheSwitch() {
        LevelData level = Level01Footsteps.build();
        Vector2D gate = exitPosition(level);
        Vector2D switchPos = entity(level, "L01_plate_right").getPos();

        double distance = Math.hypot(gate.x() - switchPos.x(), gate.y() - switchPos.y());
        assertEquals(TILE, distance, 1e-9);
        assertTrue(distance <= INTERACT_RADIUS);
    }

    private static Vector2D doorPosition(LevelData level) {
        return level.getDoors().stream()
                .filter(d -> "L01_door_01".equals(d.getId()))
                .map(DoorInfo::getPosition)
                .findFirst()
                .orElseThrow();
    }

    private static Vector2D exitPosition(LevelData level) {
        return entity(level, "L01_exit_00").getPos();
    }

    private static EntitySpawnInfo entity(LevelData level, String id) {
        return level.getEntitySpawnList().stream()
                .filter(entity -> id.equals(entity.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少第一关实体: " + id));
    }

    private static List<String> registerEventTrace(EventDispatcher dispatcher) {
        List<String> trace = new ArrayList<>();
        GameObserver observer = event -> trace.add(event.eventType() + ":" + event.sourceId());
        dispatcher.register(GameEvent.PLATE_ENTERED, observer);
        dispatcher.register(GameEvent.PLATE_EXITED, observer);
        dispatcher.register(GameEvent.DOOR_UNLOCKED, observer);
        dispatcher.register(GameEvent.EXIT_TRIGGERED, observer);
        return trace;
    }
}
