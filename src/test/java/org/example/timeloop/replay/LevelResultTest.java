package org.example.timeloop.replay;

import org.example.timeloop.core.ActorPhase;
import org.example.timeloop.core.AnimationState;
import org.example.timeloop.core.Direction;
import org.example.timeloop.core.GamePhase;
import org.example.timeloop.core.MovementState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-DEV2-L02-RESULT-PROJECTION：通关/失败结果的只读投影与固化时机。
 *
 * <p>锁定两点：① {@code LevelResult} 在终局边界刻固化（达成/失败那一刻的 roundTick），
 * 用时口径 {@code usedTicks = (clearedRound - 1) × durationTicks + roundTickAtEnd} 与
 * HUD 的唯一共享读秒同源；② 未终局为空、重开清空、终局后 roundTick 冻结。</p>
 */
class LevelResultTest {

    private static final int D = 5;

    private static RoundClock playingClock(int durationTicks, int maxRounds) {
        RoundClock c = new RoundClock(durationTicks, maxRounds);
        c.transition(GamePhase.MENU);
        c.transition(GamePhase.LEVEL_SELECT);
        c.transition(GamePhase.READY);
        c.transition(GamePhase.PLAYING);
        return c;
    }

    private static PlayerFrame frame(long tick) {
        return new PlayerFrame(tick, tick * 10.0, 0, Direction.RIGHT, false,
                MovementState.CRUISING, ActorPhase.AVAILABLE, 0, AnimationState.MOVING);
    }

    /** 模拟真实 app 节奏：每 tick 先 recordFrame 再 advance。 */
    private static void advance(RecordingSession s, RoundClock clock, int ticks) {
        for (int t = 0; t < ticks; t++) {
            s.recordFrame(frame(t));
            clock.advance();
        }
    }

    @Test
    void completeGoal_freezesClearedResult() {
        RoundClock clock = playingClock(D, 4);
        RecordingSession s = new RecordingSession(clock, new EchoQueue(2), "第二关：闸链");
        s.beginRound();
        // 第 1、2 轮完整推进。
        advance(s, clock, D);
        s.completeNormalRound(() -> {});
        clock.transition(GamePhase.PLAYING);
        advance(s, clock, D);
        s.completeNormalRound(() -> {});
        clock.transition(GamePhase.PLAYING);
        // 第 3 轮推进 3 tick 后达成。
        advance(s, clock, 3);

        s.completeGoal();

        assertEquals(GamePhase.RESULT, clock.phase());
        LevelResult r = s.result().orElseThrow();
        assertTrue(r.cleared(), "通关应 cleared=true");
        assertEquals(3, r.clearedRound(), "达成轮 = 第 3 轮");
        assertEquals(4, r.maxRounds());
        assertEquals("第二关：闸链", r.levelName(), "关卡名随构造注入并透传到结果");
        assertEquals((3 - 1) * (long) D + 3, r.usedTicks(), "用时 = (3-1)*5 + 3 = 13");
        assertEquals(2, s.echoQueue().size(), "通关不新增残影（保留既有的 E1、E2）");
        assertTrue(s.currentBuffer().isEmpty(), "未满缓冲被丢弃");
    }

    @Test
    void failFinalRound_freezesFailedResult() {
        RoundClock clock = playingClock(D, 4);
        RecordingSession s = new RecordingSession(clock, new EchoQueue(2), "第二关：闸链");
        s.beginRound();
        for (int round = 1; round <= 3; round++) {
            advance(s, clock, D);
            s.completeNormalRound(() -> {});
            clock.transition(GamePhase.PLAYING);
        }
        // 最终轮录满 D 帧，roundTick 停在 D-1。
        advance(s, clock, D);

        s.failFinalRound();

        assertEquals(GamePhase.FAILED, clock.phase());
        LevelResult r = s.result().orElseThrow();
        assertFalse(r.cleared(), "失败应 cleared=false");
        assertEquals(4, r.clearedRound(), "失败所在轮 = 第 maxRounds 轮");
        assertEquals(4, r.maxRounds());
        assertEquals((4 - 1) * (long) D + (D - 1), r.usedTicks(), "用时 = 3*5 + 4 = 19");
    }

    @Test
    void result_emptyBeforeTerminalPhase() {
        RoundClock clock = playingClock(D, 4);
        RecordingSession s = new RecordingSession(clock, new EchoQueue(2), "测试关卡");
        s.beginRound();
        advance(s, clock, 3);

        assertTrue(s.result().isEmpty(), "未进入终局前结果为空");
    }

    @Test
    void restartFromFirstRound_clearsResult() {
        RoundClock clock = playingClock(D, 2);
        RecordingSession s = new RecordingSession(clock, new EchoQueue(2), "测试关卡");
        s.beginRound();
        advance(s, clock, D);
        s.completeNormalRound(() -> {});
        clock.transition(GamePhase.PLAYING);
        advance(s, clock, D);
        s.failFinalRound();
        assertTrue(s.result().isPresent(), "失败后结果已固化");
        assertEquals(GamePhase.FAILED, clock.phase());

        s.restartFromFirstRound(() -> {});

        assertTrue(s.result().isEmpty(), "从第一轮重开后结果必须清空");
        assertEquals(1, clock.currentRound());
        assertEquals(GamePhase.READY, clock.phase());
    }

    @Test
    void terminalPhase_freezesRoundTick() {
        RoundClock clock = playingClock(D, 4);
        RecordingSession s = new RecordingSession(clock, new EchoQueue(2), "测试关卡");
        s.beginRound();
        advance(s, clock, 3);
        long tickAtGoal = clock.roundTick();

        s.completeGoal();

        assertEquals(tickAtGoal, clock.roundTick(), "RESULT 阶段 roundTick 冻结");
        assertEquals(GamePhase.RESULT, clock.phase());
        assertEquals(tickAtGoal, s.result().orElseThrow().usedTicks(), "用时含达成刻");
    }

    @Test
    void usedSeconds_dividesTicksBy60() {
        LevelResult r = new LevelResult("测试关卡", true, 1, 4, 121);
        assertEquals(2, r.usedSeconds(), "121 刻 / 60 = 2 秒（向下取整）");
    }
}
