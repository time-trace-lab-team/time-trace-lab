package org.example.timeloop.replay;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R0 纸面演算样例的可执行形式（durationTicks = 5）。
 * 只验证 TickContext 的契约不变量，不实现记录/回放/寿命/快照逻辑。
 */
class TickContextTest {

    @Test
    void validFrameIndices_areZeroThroughDurationMinusOne() {
        for (int tick = 0; tick < 5; tick++) {
            TickContext ctx = new TickContext(tick, 5, 1, 3);
            assertEquals(tick, ctx.roundTick());
            assertEquals(5, ctx.durationTicks());
            assertEquals(1, ctx.currentRound());
            assertEquals(3, ctx.maxRounds());
        }
    }

    @Test
    void lastFrame_isDurationMinusOne() {
        assertTrue(new TickContext(4, 5, 1, 3).isLastTick());
        assertFalse(new TickContext(0, 5, 1, 3).isLastTick());
        assertFalse(new TickContext(3, 5, 1, 3).isLastTick());
    }

    @Test
    void frameIndexEqualToDuration_isRejected() {
        // 不存在索引 5 的帧
        assertThrows(IllegalArgumentException.class, () -> new TickContext(5, 5, 1, 3));
    }

    @Test
    void negativeFrameIndex_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> new TickContext(-1, 5, 1, 3));
    }

    @Test
    void durationTicksMustBeAtLeastOne() {
        assertThrows(IllegalArgumentException.class, () -> new TickContext(0, 0, 1, 3));
    }

    @Test
    void maxRoundsMustBeAtLeastOne() {
        assertThrows(IllegalArgumentException.class, () -> new TickContext(0, 5, 1, 0));
    }

    @Test
    void currentRoundMustBeWithinMaxRounds() {
        assertThrows(IllegalArgumentException.class, () -> new TickContext(0, 5, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new TickContext(0, 5, 4, 3));
    }

    @Test
    void resettingBoundary_isCurrentRoundPlusOneAndTickZero() {
        // 第 1 轮最后一帧（D-1 = 4）
        TickContext endOfRound1 = new TickContext(4, 5, 1, 3);
        assertTrue(endOfRound1.isLastTick());

        // 普通轮次切换后：currentRound + 1、roundTick = 0（纸面边界，非实现逻辑）
        TickContext startOfRound2 = new TickContext(0, 5, 2, 3);
        assertEquals(0, startOfRound2.roundTick());
        assertEquals(2, startOfRound2.currentRound());
        assertFalse(startOfRound2.isLastTick());
    }

    @Test
    void recordHasNoWritableEntryPoints() {
        Field[] fields = TickContext.class.getDeclaredFields();
        assertTrue(fields.length >= 4, "record 应包含 4 个字段");
        for (Field f : fields) {
            assertTrue(Modifier.isFinal(f.getModifiers()), "字段应为 final: " + f.getName());
        }
    }
}
