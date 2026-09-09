package org.example.timeloop.replay;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EchoLifetimeTest {

    private static final int L = 2;
    private static final long D = 60;

    @Test
    void testAgeCalculation() {
        // E1 来源轮次 s=1，当前轮次 r=3
        EchoLifetime echo = new EchoLifetime(1, L, D, 3, 0);
        assertEquals(2, echo.getAge());
        assertTrue(echo.isActive());
        assertEquals(1, echo.getRemainingRounds());
    }

    @Test
    void testInactiveBeforeBirth() {
        // 当前轮次 r=1，E1 还没出生
        EchoLifetime echo = new EchoLifetime(1, L, D, 1, 0);
        assertEquals(0, echo.getAge());
        assertFalse(echo.isActive());
        assertEquals(0, echo.getRemainingRounds());
    }

    @Test
    void testInactiveAfterDeath() {
        // 当前轮次 r=4，E1 已淘汰
        EchoLifetime echo = new EchoLifetime(1, L, D, 4, 0);
        assertEquals(3, echo.getAge());
        assertFalse(echo.isActive());
        assertEquals(0, echo.getRemainingRounds());
    }

    @Test
    void testLifeProgressAtBirth() {
        // E1 出生时刻：第 2 轮第 0 tick
        EchoLifetime echo = new EchoLifetime(1, L, D, 2, 0);
        assertEquals(0.0, echo.getLifeProgress(), 0.01);
        assertEquals(0.82, echo.getBodyAlpha(), 0.01);
    }

    @Test
    void testLifeProgressAtLastTick() {
        // E1 最后一个有效轮（第 3 轮）的最后一 tick
        EchoLifetime echo = new EchoLifetime(1, L, D, 3, D - 1);
        assertEquals(1.0, echo.getLifeProgress(), 0.01);
        assertEquals(0.50, echo.getBodyAlpha(), 0.01);
    }

    @Test
    void testLastEffectiveRound() {
        EchoLifetime echo = new EchoLifetime(1, L, D, 3, 0);
        assertTrue(echo.isLastEffectiveRound());
    }

    @Test
    void testShouldDisappearAtRoundEnd() {
        EchoLifetime echo = new EchoLifetime(1, L, D, 3, D - 1);
        assertTrue(echo.shouldDisappearAtRoundEnd());
    }

    @Test
    void testThirdRoundE1AndE2() {
        // 第 3 轮：E1 和 E2 同时存在
        EchoLifetime e1 = new EchoLifetime(1, L, D, 3, 30);
        EchoLifetime e2 = new EchoLifetime(2, L, D, 3, 30);

        assertTrue(e1.isActive());
        assertTrue(e2.isActive());
        assertEquals(1, e1.getRemainingRounds());
        assertEquals(2, e2.getRemainingRounds());
        assertTrue(e1.getBodyAlpha() < e2.getBodyAlpha());
    }

    @Test
    void testE1DisappearsBeforeE2() {
        // 第 4 轮：E1 淘汰，E2 保留
        EchoLifetime e1 = new EchoLifetime(1, L, D, 4, 0);
        EchoLifetime e2 = new EchoLifetime(2, L, D, 4, 0);

        assertFalse(e1.isActive());
        assertTrue(e2.isActive());
        assertEquals(0, e1.getRemainingRounds());
        assertEquals(1, e2.getRemainingRounds());
    }
}