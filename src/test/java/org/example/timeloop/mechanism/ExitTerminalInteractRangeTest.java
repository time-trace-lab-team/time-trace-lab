package org.example.timeloop.mechanism;

import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * X-MOVE-COLLAPSE-01 L-3 · 出口终端宽容交互半径（PM 2026-09-10 批准方案 A、半径 1.5 格）。
 *
 * <p>新第一关地图坐标：右驻留板中心 (18,8) = (888,408)、出口终端 (19,8) = (936,408)，
 * 两者仍恰好相距 1 格；出生点 (10,2) = (504,120) 是三度路口，距终端约 519。
 * 硬下限是 1 格（48）——必须覆盖「右驻留板 → 出口终端」这 1 格距离，否则玩家须离开右板才能按 E，
 * 而离开即释放占用、`Door` 不闩锁会重新上锁 → 第一关无解。</p>
 *
 * <p>BUG-002-LIFECYCLE Phase 1 起使用独立事件总线，不再手工清理全局单例。</p>
 */
class ExitTerminalInteractRangeTest {

    private static final double TILE = 48.0;
    private static final Vector2D TERMINAL_CENTER = new Vector2D(936.0, 408.0);
    private static final Vector2D RIGHT_PLATE_CENTER = new Vector2D(888.0, 408.0);
    /** 新地图的出生点路口 (10,2)：三度节点，距出口终端约 519 世界单位。 */
    private static final Vector2D FORK = new Vector2D(504.0, 120.0);

    private EventDispatcher bus;

    @BeforeEach
    void freshMechanismScope() {
        bus = new EventDispatcher();
    }

    @Test
    void recommendedRadiusIsOneAndAHalfTiles() {
        assertEquals(72.0, ExitTerminal.interactRadiusForTileSize(TILE), 1e-9);
        assertEquals(1.5, ExitTerminal.INTERACT_RADIUS_TILES, 1e-9);
        assertThrows(IllegalArgumentException.class, () -> ExitTerminal.interactRadiusForTileSize(0.0));
    }

    @Test
    void adjacentPlateCenterIsInRange() {
        ExitTerminal exit = exitWithRecommendedRadius();

        assertTrue(exit.isInInteractRange(RIGHT_PLATE_CENTER),
                "从右驻留板中心 (888,408) 必须能按 E");
        assertTrue(exit.isInInteractRange(TERMINAL_CENTER),
                "终端自身中心必须在范围内");
    }

    @Test
    void forkIsOutOfRange() {
        ExitTerminal exit = exitWithRecommendedRadius();

        assertFalse(exit.isInInteractRange(FORK), "出生点路口 (504,120) 距终端约 519，必须判定为超距");
    }

    @Test
    void defaultRadiusIsAtLeastOneTile() {
        ExitTerminal exit = new ExitTerminal("L01_exit_00", TERMINAL_CENTER, "L01_door_01",
                ExitTerminal.DEFAULT_INTERACT_RADIUS, bus);

        assertEquals(48.0, exit.getInteractRadius(), 1e-9);
        assertTrue(ExitTerminal.DEFAULT_INTERACT_RADIUS >= TILE,
                "兼容默认半径不得小于 1 格，否则第一关无解");
        assertTrue(exit.isInInteractRange(RIGHT_PLATE_CENTER),
                "默认半径也必须恰好覆盖相邻的右驻留板中心");
    }

    @Test
    void rangeCheckIsIndependentOfDoorState() {
        ExitTerminal exit = exitWithRecommendedRadius();

        assertTrue(exit.isInInteractRange(RIGHT_PLATE_CENTER));
        assertFalse(exit.interact(1, 0), "门未解锁时不得结算");
        assertTrue(exit.isInInteractRange(RIGHT_PLATE_CENTER),
                "半径判定是纯几何，必须与门权限状态无关");
    }

    @Test
    void invalidRadiusIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExitTerminal("L01_exit_00", TERMINAL_CENTER, "L01_door_01", 0.0, bus));
        assertThrows(IllegalArgumentException.class,
                () -> new ExitTerminal("L01_exit_00", TERMINAL_CENTER, "L01_door_01", Double.NaN, bus));
    }

    private ExitTerminal exitWithRecommendedRadius() {
        return new ExitTerminal("L01_exit_00", TERMINAL_CENTER, "L01_door_01",
                ExitTerminal.interactRadiusForTileSize(TILE), bus);
    }
}
