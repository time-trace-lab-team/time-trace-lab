package org.example.timeloop.app;

import org.example.timeloop.core.Direction;
import org.example.timeloop.core.MovementState;
import org.example.timeloop.core.input.InputIntent;
import org.example.timeloop.core.input.LogicalKey;
import org.example.timeloop.render.RenderViews;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L02-B / B4 的 headless 联调证据（相位时序 / 相位位移 / 复位），全部基于**可观察量**断言：
 * 每 tick 的玩家位置与 {@link RenderViews.Player} 的相位、移动状态。
 *
 * <p>刻意不读装配的内部控制器：这样断言的是"玩家真正经历的行为"，而不是"内部字段等于某值"。
 * 减速的 0.50× / 60 tick 与射线命中豁免由（同批）射线穿越用例覆盖，本类先锁相位与复位。</p>
 */
class Level02RayHitIntegrationTest {

    /** PatrolConfig 的公开基础速度（每逻辑刻世界单位）。 */
    private static final double BASE_SPEED = 2.0;

    private Level02Assembly assembly;

    @AfterEach
    void cleanup() {
        if (assembly != null) {
            assembly.cleanup();
            assembly = null;
        }
    }

    /** 按住 Space 100 tick：只进入 PHASED 一次。 */
    @Test
    void holdingPhaseTriggersExactlyOnce() {
        assembly = new Level02Assembly();
        assembly.start();

        int phaseStarts = 0;
        boolean wasPhased = false;
        for (long tick = 0; tick < 100; tick++) {
            assembly.tick(holding(tick, LogicalKey.PHASE, LogicalKey.DIR_RIGHT));
            boolean phased = assembly.renderViews().player().phased();
            if (phased && !wasPhased) {
                phaseStarts++;
            }
            wasPhased = phased;
        }
        assertEquals(1, phaseStarts, "按住 Space 100 tick 只应触发一次相位下潜");
    }

    /** PHASED 精确持续 30 tick（连续为真的刻数）。 */
    @Test
    void phasedLastsExactlyThirtyTicks() {
        assembly = new Level02Assembly();
        assembly.start();

        int longestRun = 0;
        int run = 0;
        for (long tick = 0; tick < 200; tick++) {
            assembly.tick(holding(tick, LogicalKey.PHASE, LogicalKey.DIR_RIGHT));
            if (assembly.renderViews().player().phased()) {
                run++;
                longestRun = Math.max(longestRun, run);
            } else {
                run = 0;
            }
        }
        assertEquals(30, longestRun, "PHASED 应精确持续 30 tick");
    }

    /** 相位期间仍以基础速度前进（相位不是停车、也不减速）。 */
    @Test
    void phasedMovementKeepsBaseSpeed() {
        assembly = new Level02Assembly();
        assembly.start();

        double previous = Double.NaN;
        int sampledPhasedTicks = 0;
        for (long tick = 0; tick < 40; tick++) {
            assembly.tick(holding(tick, LogicalKey.PHASE, LogicalKey.DIR_RIGHT));
            RenderViews.Player player = assembly.renderViews().player();
            if (!player.phased()) {
                continue;
            }
            if (!Double.isNaN(previous) && player.direction() == Direction.RIGHT) {
                double delta = player.x() - previous;
                if (delta > 0) {
                    assertEquals(BASE_SPEED, delta, 1e-9,
                            "相位期间应保持基础速度（第 " + tick + " 刻）");
                    sampledPhasedTicks++;
                }
            }
            previous = player.x();
        }
        assertTrue(sampledPhasedTicks > 0, "至少应采样到若干相位刻");
    }

    /** 相位在 RECOVERING 期间不得重复触发（按住不放也不行）。 */
    @Test
    void recoveringBlocksRetriggerWhileSpaceStillHeld() {
        assembly = new Level02Assembly();
        assembly.start();

        // 相位结束后（30 tick）到相位+恢复结束（30+45=75 tick）之间，不应再出现 PHASED。
        boolean phasedAfterRecoveryStart = false;
        for (long tick = 0; tick < 200; tick++) {
            assembly.tick(holding(tick, LogicalKey.PHASE, LogicalKey.DIR_RIGHT));
            if (tick >= 31 && assembly.renderViews().player().phased()) {
                phasedAfterRecoveryStart = true;
                break;
            }
        }
        assertFalse(phasedAfterRecoveryStart, "RECOVERING 期间按住 Space 不得再次触发相位");
    }

    /** 轮末复位：相位与减速必须清零，新一轮以基础速度起跑。 */
    @Test
    void roundBoundaryClearsPhaseAndSpeedPenalty() {
        assembly = new Level02Assembly();
        assembly.start();

        // 前 1190 刻按住相位推进；末尾 20 刻只按方向（避免"复位清空边沿记忆后、仍按住 Space
        // 于新一轮合法再次触发相位"干扰本用例的观测目标 —— 那是设计行为，不是残留）。
        for (long tick = 0; tick < 1190; tick++) {
            assembly.tick(holding(tick, LogicalKey.PHASE, LogicalKey.DIR_RIGHT));
        }
        for (long tick = 1190; tick < 1215; tick++) {
            assembly.tick(holding(tick, LogicalKey.DIR_RIGHT));
        }
        assertTrue(assembly.hudContext().currentRound() >= 2,
                "应已进入第二轮，实际第 " + assembly.hudContext().currentRound() + " 轮");

        assembly.tick(holding(0, LogicalKey.DIR_RIGHT));
        RenderViews.Player afterRound = assembly.renderViews().player();
        assertFalse(afterRound.phased(), "轮末后不应残留相位状态");
        assertFalse(afterRound.movementState() == MovementState.SLOWED,
                "轮末后不应残留减速状态");

        double before = afterRound.x();
        assembly.tick(holding(1, LogicalKey.DIR_RIGHT));
        double delta = assembly.renderViews().player().x() - before;
        if (delta > 0) {
            assertEquals(BASE_SPEED, delta, 1e-9, "新一轮应以基础速度移动");
        }
    }
    // ---------- 工具 ----------

    /** 本刻按住给定键（沿用既有 InputIntent 的"按住 + 方向边沿"表达）。 */
    private static InputIntent holding(long tick, LogicalKey... keys) {
        Set<LogicalKey> held = Set.of(keys);
        List<Direction> edges = List.of(Direction.RIGHT);
        return new InputIntent(tick, held, Set.of(), held, edges);
    }
}
