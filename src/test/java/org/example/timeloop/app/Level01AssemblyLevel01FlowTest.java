package org.example.timeloop.app;

import org.example.timeloop.core.Direction;
import org.example.timeloop.core.GamePhase;
import org.example.timeloop.core.MovementState;
import org.example.timeloop.core.input.InputIntent;
import org.example.timeloop.core.input.LogicalKey;
import org.example.timeloop.mechanism.DockingPlate;
import org.example.timeloop.mechanism.DockingPlateRegistry;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.example.timeloop.render.RenderViews;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第一关端到端（PM 侧 app/）：
 *
 * <ul>
 *   <li><b>ECHO-ACTOR E-1</b>：残影回放写入机关的 actor 必须是 {@code echo_<sourceRound>}，
 *       而不是记录里的活玩家 {@code player}（否则残影占用与活玩家无法区分、残影消失时驻留板不会释放）；</li>
 *   <li><b>APP-2</b>：出口终端必须在宽容半径内才结算 —— 右驻留板中心可按 {@code E} 通关，
 *       而「门已解锁但玩家在左驻留板（离出口约 15 格）」时按 {@code E} 不得结算。</li>
 * </ul>
 *
 * <p>两轮/三轮脚本同时充当「第一关在自由移动下可通关」的集成证据。</p>
 */
class Level01AssemblyLevel01FlowTest {

    private static final double TILE_SIZE = 48.0;
    /** 新地图左驻留板 (4,6) 中心 y = 312；到出口终端 (19,8) = (936,408) 相距约 726，远超 1.5 格半径 72。 */
    private static final double LEFT_PLATE_Y = 6.5 * TILE_SIZE;
    /** 新地图右驻留板 (18,8) 中心；与出口终端 (19,8) 恰好相距 1 格。 */
    private static final double RIGHT_PLATE_X = 18.5 * TILE_SIZE;
    private static final double RIGHT_PLATE_Y = 8.5 * TILE_SIZE;

    private Level01Assembly assembly;

    @AfterEach
    void clearGlobalsAndCleanup() {
        if (assembly != null) {
            assembly.cleanup();
            assembly = null;
        }
        EventDispatcher.getInstance().clear();
        DockingPlateRegistry.getInstance().clear();
    }

    @Test
    void echoOccupancyIsAttributedToEchoActorInSecondRound() {
        Level01Assembly a = started();
        long tick = runFirstRoundToEnd(a);
        assertEquals(2, a.hudContext().currentRound());

        // 第 2 轮：残影在 roundTick ≈ 420 复现第 1 轮记录里的左驻留板 DOCK_ENTERED
        while (a.hudContext().roundTick() < 500) {
            a.tick(InputIntent.empty(tick++));
        }
        DockingPlate left = DockingPlateRegistry.getInstance().get("L01_plate_left");
        assertNotNull(left);
        assertTrue(left.isOccupied(), "第 2 轮该由上一轮残影占住左驻留板");
        assertEquals("echo_1", left.getOccupantId(),
                "残影占用必须归属于 echo_<sourceRound>，而不是录制时的 player");
        assertEquals(1, left.getOccupantSourceRound());
    }

    @Test
    void pressingInteractInRangeOnRightPlateWinsTheLevel() {
        Level01Assembly a = started();
        long tick = runFirstRoundToEnd(a);

        // 第 2 轮：走到右驻留板 (18,8) → 与残影占住的左板共同解锁门
        tick = driveRightPlateRoute(a, tick);
        for (int i = 0; i < 3; i++) {
            a.tick(InputIntent.empty(tick++));               // 走到机关中心并停驻
        }

        RenderViews.Player docked = player(a);
        assertEquals(MovementState.DOCKED, docked.movementState(), "应在右驻留板上停驻");
        assertEquals(RIGHT_PLATE_X, docked.x(), 1e-9);
        assertEquals(RIGHT_PLATE_Y, docked.y(), 1e-9);
        assertTrue(DockingPlateRegistry.getInstance().isOccupied("L01_plate_right"));
        assertTrue(DockingPlateRegistry.getInstance().isOccupied("L01_plate_left"),
                "左板应仍由残影占住");

        // 在右驻留板中心（距出口终端恰好 1 格）按 E → 宽容半径内结算
        a.tick(pressKey(tick, LogicalKey.INTERACT));
        assertEquals(GamePhase.RESULT, a.phase(), "半径内按 E 应结算通关");
    }

    @Test
    void pressingInteractOutsideRangeWithUnlockedDoorDoesNotComplete() {
        Level01Assembly a = started();
        long tick = runFirstRoundToEnd(a);

        // 第 2 轮：占住右驻留板并停驻到轮末 → 第 2 轮残影将在第 3 轮占右板
        tick = driveRightPlateRoute(a, tick);
        for (int i = 0; i < 3; i++) {
            a.tick(InputIntent.empty(tick++));
        }
        while (a.hudContext().currentRound() < 3) {
            a.tick(InputIntent.empty(tick++));
        }
        assertEquals(3, a.hudContext().currentRound());

        // 第 3 轮：残影在 roundTick ≈ 516 压住右板；玩家先去占左板（432 刻），
        // 之后等残影到位 → 两板同时被占、门解锁，但玩家离出口约 726 世界单位。
        tick = driveLeftPlateRoute(a, tick);
        for (int i = 0; i < 3; i++) {
            a.tick(InputIntent.empty(tick++));
        }
        while (a.hudContext().roundTick() < 600) {
            a.tick(InputIntent.empty(tick++));
        }

        assertTrue(DockingPlateRegistry.getInstance().isOccupied("L01_plate_right"),
                "右板应由第 2 轮残影占住");
        assertTrue(DockingPlateRegistry.getInstance().isOccupied("L01_plate_left"),
                "左板应由当前玩家占住");
        RenderViews.Player onLeftPlate = player(a);
        assertEquals(MovementState.DOCKED, onLeftPlate.movementState());
        assertEquals(LEFT_PLATE_Y, onLeftPlate.y(), 1e-9);

        for (int i = 0; i < 5; i++) {
            a.tick(pressKey(tick++, LogicalKey.INTERACT));
        }
        assertEquals(GamePhase.PLAYING, a.phase(),
                "门虽已解锁（两板皆占用），但玩家在左板 (4,6) 离出口 (19,8) 约 726 → 不得结算");
        assertEquals(3, a.hudContext().currentRound());
    }

    // ---------- 第一轮脚本：走到左驻留板并停驻，直到轮末 ----------

    /** 新地图左驻留板路线（18 格 = 432 刻）：出生点 ↓(10,3) ←(8,3) ↓(8,10) ←(5,10) ↑(5,8) ←(4,8) ↑(4,6)。 */
    private static long driveLeftPlateRoute(Level01Assembly a, long tick) {
        tick = drive(a, tick, LogicalKey.DIR_DOWN, 24);      // (10,2) → (10,3)
        tick = drive(a, tick, LogicalKey.DIR_LEFT, 48);      // (10,3) → (8,3)
        tick = drive(a, tick, LogicalKey.DIR_DOWN, 168);     // (8,3) → (8,10)
        tick = drive(a, tick, LogicalKey.DIR_LEFT, 72);      // (8,10) → (5,10)
        tick = drive(a, tick, LogicalKey.DIR_UP, 48);        // (5,10) → (5,8)
        tick = drive(a, tick, LogicalKey.DIR_LEFT, 24);      // (5,8) → (4,8)
        tick = drive(a, tick, LogicalKey.DIR_UP, 48);        // (4,8) → 左驻留板 (4,6)
        return tick;
    }

    /** 新地图右驻留板路线（22 格 = 528 刻）：出生点 ↓(10,4) →(12,4) ↓(12,5) →(22,5) ↓(22,8) ←(18,8)。 */
    private static long driveRightPlateRoute(Level01Assembly a, long tick) {
        tick = drive(a, tick, LogicalKey.DIR_DOWN, 48);      // (10,2) → (10,4)
        tick = drive(a, tick, LogicalKey.DIR_RIGHT, 48);     // (10,4) → (12,4)
        tick = drive(a, tick, LogicalKey.DIR_DOWN, 24);      // (12,4) → (12,5)
        tick = drive(a, tick, LogicalKey.DIR_RIGHT, 240);    // (12,5) → (22,5)
        tick = drive(a, tick, LogicalKey.DIR_DOWN, 72);      // (22,5) → (22,8)
        tick = drive(a, tick, LogicalKey.DIR_LEFT, 96);      // (22,8) → 右驻留板 (18,8)
        return tick;
    }

    private static long runFirstRoundToEnd(Level01Assembly a) {
        long tick = 0;
        a.tick(InputIntent.empty(tick++));
        tick = driveLeftPlateRoute(a, tick);
        a.tick(InputIntent.empty(tick++));                   // 停驻
        while (a.hudContext().currentRound() == 1) {
            a.tick(InputIntent.empty(tick++));
        }
        return tick;
    }

    private Level01Assembly started() {
        assembly = new Level01Assembly();
        assembly.start();
        assertTrue(assembly.isPlaying());
        return assembly;
    }

    private static RenderViews.Player player(Level01Assembly a) {
        return a.renderViews().player();
    }

    private static long drive(Level01Assembly a, long tick, LogicalKey key, int ticks) {
        a.tick(press(tick++, key));
        for (int i = 1; i < ticks; i++) {
            a.tick(hold(tick++, key));
        }
        return tick;
    }

    private static InputIntent press(long tick, LogicalKey key) {
        return new InputIntent(tick, Set.of(key), Set.of(), Set.of(key), List.of(directionOf(key)));
    }

    private static InputIntent hold(long tick, LogicalKey key) {
        return new InputIntent(tick, Set.of(), Set.of(), Set.of(key), List.of());
    }

    /** 非方向键（如 E）的新按下：无方向边沿。 */
    private static InputIntent pressKey(long tick, LogicalKey key) {
        return new InputIntent(tick, Set.of(key), Set.of(), Set.of(key), List.of());
    }

    private static Direction directionOf(LogicalKey key) {
        return switch (key) {
            case DIR_UP -> Direction.UP;
            case DIR_DOWN -> Direction.DOWN;
            case DIR_LEFT -> Direction.LEFT;
            case DIR_RIGHT -> Direction.RIGHT;
            default -> throw new IllegalArgumentException("不是方向键: " + key);
        };
    }
}
