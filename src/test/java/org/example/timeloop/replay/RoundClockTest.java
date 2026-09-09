package org.example.timeloop.replay;

import org.example.timeloop.core.GamePhase;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R1 自动测试：共享时钟与游戏阶段。
 * 只验证 roundTick 权威值、合法阶段转移与冻结参数，不实现记录/回放/寿命/快照逻辑。
 */
class RoundClockTest {

    /** 走到 READY（BOOT -> MENU -> LEVEL_SELECT -> TUTORIAL -> READY）。 */
    private static RoundClock toReady(int durationTicks, int maxRounds) {
        RoundClock c = new RoundClock(durationTicks, maxRounds);
        c.transition(GamePhase.MENU);
        c.transition(GamePhase.LEVEL_SELECT);
        c.transition(GamePhase.TUTORIAL);
        c.transition(GamePhase.READY);
        return c;
    }

    private static void toPlaying(RoundClock c) {
        c.transition(GamePhase.PLAYING);
    }

    @Test
    void readyDoesNotAdvance() {
        RoundClock c = toReady(5, 3);
        assertEquals(AdvanceResult.NO_ADVANCE, c.advance());
        assertEquals(0, c.roundTick());
    }

    @Test
    void tutorialDoesNotAdvance() {
        RoundClock c = new RoundClock(5, 3);
        c.transition(GamePhase.MENU);
        c.transition(GamePhase.LEVEL_SELECT);
        c.transition(GamePhase.TUTORIAL);
        assertEquals(AdvanceResult.NO_ADVANCE, c.advance());
        assertEquals(0, c.roundTick());
    }

    @Test
    void pausedDoesNotAdvance() {
        RoundClock c = toReady(5, 3);
        toPlaying(c);
        c.advance(); // roundTick 0 -> 1
        c.transition(GamePhase.PAUSED);
        assertEquals(AdvanceResult.NO_ADVANCE, c.advance());
        assertEquals(1, c.roundTick(), "暂停不应推进 roundTick");
    }

    @Test
    void playingAdvancesStrictlyThroughDurationMinusOne() {
        RoundClock c = toReady(5, 3);
        toPlaying(c);
        List<Long> ticks = new ArrayList<>();
        long safety = 0;
        while (true) {
            ticks.add(c.roundTick());
            AdvanceResult r = c.advance();
            if (r == AdvanceResult.ROUND_END) {
                break;
            }
            assertTrue(++safety < 100, "一轮应能按时结束");
        }
        assertEquals(List.of(0L, 1L, 2L, 3L, 4L), ticks);
        assertEquals(4L, c.roundTick());
        assertFalse(ticks.contains(5L), "不应存在索引 5（== durationTicks）的帧");
    }

    @Test
    void singleTickRound_hasOnlyIndexZero() {
        RoundClock c = toReady(1, 3);
        toPlaying(c);
        assertEquals(0, c.roundTick());
        assertEquals(AdvanceResult.ROUND_END, c.advance(), "D=1 时 tick0 即最后一帧");
        assertEquals(0, c.roundTick());
    }

    @Test
    void resettingThenReadyAdvancesRoundAndResetsTick() {
        RoundClock c = toReady(5, 3);
        toPlaying(c);
        while (c.advance() != AdvanceResult.ROUND_END) {
            // spin to round end
        }
        assertEquals(1, c.currentRound());
        c.transition(GamePhase.RESETTING);
        c.transition(GamePhase.READY);
        assertEquals(2, c.currentRound());
        assertEquals(0, c.roundTick());
    }

    @Test
    void failedOnlyOnMaxRound() {
        RoundClock c = toReady(5, 1);
        toPlaying(c);
        while (c.advance() != AdvanceResult.ROUND_END) {
            // spin
        }
        assertEquals(1, c.currentRound());
        c.transition(GamePhase.FAILED);
        assertEquals(GamePhase.FAILED, c.phase());
    }

    @Test
    void resettingRejectedOnMaxRound() {
        RoundClock c = toReady(5, 1);
        toPlaying(c);
        while (c.advance() != AdvanceResult.ROUND_END) {
            // spin
        }
        assertThrows(IllegalStateException.class, () -> c.transition(GamePhase.RESETTING),
                "最后一轮读秒归零应进入 FAILED，而非 RESETTING");
    }

    @Test
    void resumePreservesRoundTick() {
        RoundClock c = toReady(5, 3);
        toPlaying(c);
        c.advance(); // ->1
        c.advance(); // ->2
        c.transition(GamePhase.PAUSED);
        assertEquals(2, c.roundTick());
        c.transition(GamePhase.PLAYING);
        assertEquals(2, c.roundTick(), "恢复后应保留 roundTick");
        assertEquals(AdvanceResult.ADVANCED, c.advance());
        assertEquals(3, c.roundTick());
    }

    @Test
    void frozenParamsAreFinalAndStable() throws NoSuchFieldException {
        RoundClock c = toReady(5, 3);
        toPlaying(c);
        c.advance();
        assertEquals(5, c.durationTicks());
        assertEquals(3, c.maxRounds());
        Field duration = RoundClock.class.getDeclaredField("durationTicks");
        Field max = RoundClock.class.getDeclaredField("maxRounds");
        assertTrue(Modifier.isFinal(duration.getModifiers()), "durationTicks 应为 final");
        assertTrue(Modifier.isFinal(max.getModifiers()), "maxRounds 应为 final");

        // toContext 是不可变共享快照，字段全 final。
        TickContext ctx = c.toContext();
        assertEquals(c.roundTick(), ctx.roundTick());
        assertEquals(5, ctx.durationTicks());
        for (Field f : TickContext.class.getDeclaredFields()) {
            assertTrue(Modifier.isFinal(f.getModifiers()), "TickContext 字段应为 final: " + f.getName());
        }
    }

    @Test
    void illegalTransitionFails() {
        RoundClock c = toReady(5, 3);
        assertThrows(IllegalStateException.class, () -> c.transition(GamePhase.RESULT),
                "READY -> RESULT 非法");
        assertThrows(IllegalStateException.class, () -> c.transition(GamePhase.RESETTING),
                "READY -> RESETTING 非法");
        toPlaying(c);
        assertThrows(IllegalStateException.class, () -> c.transition(GamePhase.READY),
                "PLAYING -> READY 非法（应经 PAUSED/RESULT/RESETTING/FAILED）");
    }

    @Test
    void resultAndFailedDoNotAdvance() {
        RoundClock c = toReady(5, 3);
        toPlaying(c);
        c.advance(); // ->1
        c.transition(GamePhase.RESULT);
        assertEquals(AdvanceResult.NO_ADVANCE, c.advance());
        assertEquals(1, c.roundTick());
    }

    @Test
    void onlyPlayingPhase_advancesLogic() {
        // 遍历而非硬编码：项目经理以后往 GamePhase 里新增阶段时会被自动检查，
        // 强制其显式决定是否推进逻辑刻（对齐开发 1 的 D-03 做法）。
        for (GamePhase phase : GamePhase.values()) {
            assertEquals(phase == GamePhase.PLAYING, phase.advancesLogic(),
                    phase + " 的 advancesLogic() 取值不符合 C1 §1：仅 PLAYING 推进逻辑刻");
        }
    }
}
