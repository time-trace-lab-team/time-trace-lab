package org.example.timeloop.replay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R4 基础自动测试：README 统一生命周期公式原样实现。
 *
 * <p>固定测试数据：L=2、D=1200（第四/五关配置），
 * 核对第 2/3 轮 E1 与 E2 在 tick 0 与 tick D-1 的 age、剩余轮数、进度与透明度。</p>
 */
class EchoLifetimeTest {

    private static final long D = 1200;

    private static EchoLifetime e1At(int currentRound, long roundTick) {
        return new EchoLifetime(1, 2, D, currentRound, roundTick);
    }

    private static EchoLifetime e2At(int currentRound, long roundTick) {
        return new EchoLifetime(2, 2, D, currentRound, roundTick);
    }

    @Test
    void round2_e1_firstActiveRound() {
        EchoLifetime e1 = e1At(2, 0);
        assertEquals(1, e1.getAge());
        assertTrue(e1.isActive());
        assertEquals(2, e1.getRemainingRounds());
        assertFalse(e1.isLastEffectiveRound());
        assertEquals(0.0, e1.getLifeProgress(), 1e-9, "第 2 轮 tick 0：进度为 0");
        assertEquals(0.82, e1.getBodyAlpha(), 1e-9, "第 2 轮 tick 0：透明度 0.82");
    }

    @Test
    void round2_e1_endOfRound() {
        EchoLifetime e1 = e1At(2, D - 1);
        assertEquals(0.4996, e1.getLifeProgress(), 1e-3);
        assertEquals(0.6601, e1.getBodyAlpha(), 1e-3, "第 2 轮末刻：透明度约 0.66");
    }

    @Test
    void round3_e1_isLastEffectiveRound() {
        EchoLifetime e1 = e1At(3, 0);
        assertEquals(2, e1.getAge());
        assertTrue(e1.isActive());
        assertEquals(1, e1.getRemainingRounds(), "第 3 轮 E1 应为最后有效轮");
        assertTrue(e1.isLastEffectiveRound());
        assertTrue(e1.shouldDisappearAtRoundEnd(), "E1 应在第 3 轮结束时先消散");
        assertEquals(0.5, e1.getLifeProgress(), 1e-9);
        assertEquals(0.66, e1.getBodyAlpha(), 1e-9);
    }

    @Test
    void round3_e1_endOfRound_reachesMinimumAlpha() {
        EchoLifetime e1 = e1At(3, D - 1);
        assertEquals(0.5001, e1.getBodyAlpha(), 1e-3,
                "E1 末刻透明度约 0.50，之后由 RESETTING 淡出到 0");
    }

    @Test
    void round3_e2_isNewerAndMoreOpaque() {
        EchoLifetime e1 = e1At(3, 0);
        EchoLifetime e2 = e2At(3, 0);
        assertEquals(1, e2.getAge());
        assertEquals(2, e2.getRemainingRounds());
        assertFalse(e2.isLastEffectiveRound());
        assertTrue(e2.getBodyAlpha() > e1.getBodyAlpha(),
                "第 3 轮 E2(0.82) 必须比 E1(0.66) 更不透明");
    }

    @Test
    void slidingWindow_e1InactiveInRound4() {
        EchoLifetime e1 = e1At(4, 0);
        assertEquals(3, e1.getAge());
        assertFalse(e1.isActive(), "第 4 轮 E1 已淘汰（第 4 轮为 E2+E3）");
        assertEquals(0, e1.getRemainingRounds());
        assertEquals(1.0, e1.getLifeProgress(), 1e-9, "过期残影进度为 1");
    }

    @Test
    void ageBeforeCreation_isNotActive() {
        EchoLifetime e1 = e1At(1, 0);
        assertEquals(0, e1.getAge(), "来源轮本Tick残影尚未生成");
        assertFalse(e1.isActive());
        assertEquals(0.0, e1.getLifeProgress(), 1e-9);
    }

    @Test
    void lifetimeOne_firstActiveRoundIsAlsoLast() {
        // 第一/二关 L=1：第 2 轮既是第一有效轮也是最后有效轮。
        EchoLifetime e1 = new EchoLifetime(1, 1, 960, 2, 0);
        assertEquals(1, e1.getAge());
        assertTrue(e1.isActive());
        assertEquals(1, e1.getRemainingRounds());
        assertTrue(e1.isLastEffectiveRound());
        assertTrue(e1.shouldDisappearAtRoundEnd());
    }

    @Test
    void lifetimeOne_progressEqualsTickRatio() {
        EchoLifetime e1 = new EchoLifetime(1, 1, 960, 2, 480);
        assertEquals(0.5, e1.getLifeProgress(), 1e-9,
                "L=1 时 lifeProgress 退化为 roundTick/D");
    }

    @Test
    void activityDoesNotChangeWithinRound() {
        // 淘汰只在轮次边界：同一轮内 tick 0 与 tick D-1 的 active 必须一致。
        EchoLifetime start = e1At(3, 0);
        EchoLifetime end = e1At(3, D - 1);
        assertEquals(start.isActive(), end.isActive());
        assertEquals(start.getRemainingRounds(), end.getRemainingRounds());
    }

    @Test
    void of_derivesFromSharedTickContext() {
        // 共享时钟派生：轮次/刻/轮长全部来自 TickContext，调用方无需自报。
        TickContext ctx = new TickContext(600, 1200, 3, 4);
        EchoLifetime e1 = EchoLifetime.of(1, 2, ctx);
        assertEquals(2, e1.getAge());
        assertTrue(e1.isLastEffectiveRound());
        assertEquals(0.75, e1.getLifeProgress(), 1e-9,
                "(age-1)*D + roundTick = 1200 + 600，除以 L*D = 2400");
    }

    @Test
    void invalidArguments_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new EchoLifetime(0, 2, D, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> new EchoLifetime(1, 0, D, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> new EchoLifetime(1, 2, 0, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> new EchoLifetime(1, 2, D, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new EchoLifetime(1, 2, D, 2, -1));
        assertThrows(IllegalArgumentException.class, () -> new EchoLifetime(1, 2, D, 2, D),
                "roundTick 不存在索引 D");
    }
}
