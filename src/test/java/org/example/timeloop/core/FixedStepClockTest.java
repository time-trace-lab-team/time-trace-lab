package org.example.timeloop.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FixedStepClockTest {

    private static final long NS_PER_S = 1_000_000_000L;
    private static final long NS_PER_60FPS = Math.round(NS_PER_S / 60.0);

    @Test
    void firstFrame_recordsBaselineButAdvancesZero() {
        FixedStepClock clock = new FixedStepClock();
        int steps = clock.advance(NS_PER_S, GamePhase.BOOT);
        assertEquals(0, steps);
        assertEquals(0, clock.getTotalTicks());
    }

    @Test
    void at60fps_producesExactlyOneStepPerFrame() {
        FixedStepClock clock = new FixedStepClock();
        clock.advance(0, GamePhase.BOOT);

        long t = 0;
        for (int i = 0; i < 60; i++) {
            t += NS_PER_60FPS;
            int steps = clock.advance(t, GamePhase.PLAYING);
            assertEquals(1, steps, "Frame " + i + " should yield exactly 1 step");
        }
        assertEquals(60, clock.getTotalTicks());
    }

    @Test
    void at120fps_producesSameTotalTicksAs60fps() {
        FixedStepClock clockA = new FixedStepClock();
        FixedStepClock clockB = new FixedStepClock();

        clockA.advance(0, GamePhase.BOOT);
        clockB.advance(0, GamePhase.BOOT);

        long t60 = 0;
        for (int i = 0; i < 60; i++) {
            t60 += NS_PER_60FPS;
            clockA.advance(t60, GamePhase.PLAYING);
        }

        long step120 = Math.round(NS_PER_60FPS / 2.0);
        long t120 = 0;
        for (int i = 0; i < 120; i++) {
            t120 += step120;
            clockB.advance(t120, GamePhase.PLAYING);
        }

        assertEquals(clockA.getTotalTicks(), clockB.getTotalTicks());
    }

    @Test
    void pausedInterval_doesNotAdvanceTicks() {
        FixedStepClock clock = new FixedStepClock();
        clock.advance(0, GamePhase.BOOT);

        long t = NS_PER_60FPS;
        clock.advance(t, GamePhase.PLAYING);

        t += 2 * NS_PER_S;
        int steps = clock.advance(t, GamePhase.PAUSED);
        assertEquals(0, steps);
        assertEquals(1, clock.getTotalTicks());

        t += NS_PER_60FPS;
        steps = clock.advance(t, GamePhase.PLAYING);
        assertEquals(1, steps);
        assertEquals(2, clock.getTotalTicks());
    }

    @Test
    void massiveDelta_isCappedAndDoesNotExplode() {
        FixedStepClock clock = new FixedStepClock();
        clock.advance(0, GamePhase.BOOT);

        long t = NS_PER_S;
        int steps = clock.advance(t, GamePhase.PLAYING);
        assertEquals(FixedStepClock.MAX_STEPS_PER_FRAME, steps);
        assertTrue(clock.getTotalTicks() <= FixedStepClock.MAX_STEPS_PER_FRAME);
    }

    @Test
    void interpolationAlpha_isBetweenZeroAndOne() {
        FixedStepClock clock = new FixedStepClock();
        clock.advance(0, GamePhase.BOOT);

        long halfStep = NS_PER_60FPS / 2;
        clock.advance(halfStep, GamePhase.PLAYING);
        double alpha = clock.getInterpolationAlpha();
        assertTrue(alpha >= 0.0 && alpha < 1.0, "Alpha should be in [0,1)");
    }

    @Test
    void sustainedLag_doesNotAccumulateRepayableDebt() {
        FixedStepClock clock = new FixedStepClock();
        clock.advance(0, GamePhase.BOOT);

        long t = 0;
        for (int i = 0; i < 60; i++) {
            t += 200_000_000L;
            clock.advance(t, GamePhase.PLAYING);
        }

        // 掉帧期间 alpha 必须仍在 [0,1)，而不是累积成几百
        assertTrue(clock.getInterpolationAlpha() < 1.0,
                "掉帧期间 alpha 应仍在 [0,1)，实际 " + clock.getInterpolationAlpha());

        // 帧率恢复后的第一帧必须立刻回到每帧 1 步，不能继续跑满上限
        t += NS_PER_60FPS;
        int steps = clock.advance(t, GamePhase.PLAYING);
        assertEquals(1, steps, "帧率恢复后不得继续以 MAX_STEPS_PER_FRAME 快进还债");
        assertTrue(clock.getInterpolationAlpha() < 1.0);
    }

    @Test
    void interpolationAlpha_isAlwaysWithinUnitIntervalUnderAnyFramePattern() {
        FixedStepClock clock = new FixedStepClock();
        clock.advance(0, GamePhase.BOOT);

        // 混合帧间隔：正常 / 轻微掉帧 / 严重掉帧 / 长卡顿，循环 20 轮
        long[] pattern = {NS_PER_60FPS, 33_000_000L, 200_000_000L, 1_500_000_000L};
        long t = 0;
        for (int round = 0; round < 20; round++) {
            for (long gap : pattern) {
                t += gap;
                clock.advance(t, GamePhase.PLAYING);
                double alpha = clock.getInterpolationAlpha();
                assertTrue(alpha >= 0.0 && alpha < 1.0,
                        "第 " + round + " 轮 gap=" + gap + " 后 alpha 越界: " + alpha);
            }
        }
    }

    @Test
    void nonMonotonicNanoTime_isIgnoredAndKeepsAccumulatorNonNegative() {
        FixedStepClock clock = new FixedStepClock();
        clock.advance(1_000_000_000L, GamePhase.BOOT);

        int steps = clock.advance(0L, GamePhase.PLAYING);

        assertEquals(0, steps, "时间源回退不得推进逻辑刻");
        assertTrue(clock.getInterpolationAlpha() >= 0.0,
                "时间源回退不得产生负 alpha，实际 " + clock.getInterpolationAlpha());
        assertEquals(0, clock.getTotalTicks());
    }

    @Test
    void reset_clearsAllState() {
        FixedStepClock clock = new FixedStepClock();
        clock.advance(0, GamePhase.BOOT);
        clock.advance(NS_PER_S, GamePhase.PLAYING);

        clock.reset();
        assertEquals(0, clock.getTotalTicks());
        assertEquals(0.0, clock.getInterpolationAlpha(), 1e-9);
    }
}