package org.example.timeloop.replay;

import org.example.timeloop.core.ActorPhase;
import org.example.timeloop.core.AnimationState;
import org.example.timeloop.core.Direction;
import org.example.timeloop.core.GamePhase;
import org.example.timeloop.core.MovementState;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R3 自动测试：轮末事务与四种边界的记录生命周期。
 *
 * <p>机关快照恢复端口由开发 3 提供，这里用注入的计数 Runnable 验证调用时机；
 * 单残影确定性门禁由本类的 L=1 场景与 {@link EchoStateTest} 共同证明。</p>
 */
class RecordingSessionTest {

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

    private static void recordFullRound(RecordingSession s) {
        for (int i = 0; i < D; i++) {
            s.recordFrame(frame(i));
        }
    }

    @Test
    void normalRoundEnd_sealsCreatesEchoAndAdvancesRound() {
        RoundClock clock = playingClock(D, 3);
        RecordingSession s = new RecordingSession(clock, new EchoQueue(2));
        AtomicInteger restoreCalls = new AtomicInteger();
        s.beginRound();
        recordFullRound(s);

        EchoState e1 = s.completeNormalRound(restoreCalls::incrementAndGet);

        assertEquals(1, e1.sourceRound(), "新残影来源轮次 = 刚结束的第 1 轮");
        assertEquals(2, clock.currentRound(), "轮次 +1");
        assertEquals(0, clock.roundTick(), "刻归零");
        assertEquals(GamePhase.READY, clock.phase());
        assertEquals(1, restoreCalls.get(), "机关快照恢复恰好调用一次");
        assertTrue(s.currentBuffer().isPresent(), "已为下一轮开新缓冲");
        assertEquals(0, s.currentBuffer().get().size(), "新缓冲为空");
    }

    @Test
    void nextRoundEcho_replaysPreviousRoundVerbatim() {
        RoundClock clock = playingClock(D, 3);
        RecordingSession s = new RecordingSession(clock, new EchoQueue(2));
        s.beginRound();
        recordFullRound(s);
        s.completeNormalRound(() -> {});

        // 单残影确定性：第 2 轮 E1 按共享 roundTick 逐 tick 复现第 1 轮。
        var active = s.echoQueue().activeEchoes(2);
        assertEquals(1, active.size());
        for (int t = 0; t < D; t++) {
            PlayerFrame f = active.get(0).frameAt(t);
            assertEquals(t, f.tick());
            assertEquals(t * 10.0, f.x(), 1e-9);
        }
    }

    @Test
    void goalAchieved_discardsIncompleteBufferAndGeneratesNoEcho() {
        RoundClock clock = playingClock(D, 3);
        RecordingSession s = new RecordingSession(clock, new EchoQueue(2));
        s.beginRound();
        s.recordFrame(frame(0));
        s.recordFrame(frame(1));

        s.completeGoal();

        assertEquals(GamePhase.RESULT, clock.phase());
        assertTrue(s.echoQueue().isEmpty(), "通关不生成残影");
        assertTrue(s.currentBuffer().isEmpty(), "未满缓冲被丢弃");
    }

    @Test
    void finalRoundTimeout_entersFailedWithoutNewEcho() {
        RoundClock clock = playingClock(D, 3);
        RecordingSession s = new RecordingSession(clock, new EchoQueue(2));
        s.beginRound();
        recordFullRound(s);
        s.completeNormalRound(() -> {});   // -> READY 第 2 轮
        clock.transition(GamePhase.PLAYING);
        recordFullRound(s);
        s.completeNormalRound(() -> {});   // -> READY 第 3 轮（最终轮）
        clock.transition(GamePhase.PLAYING);
        assertEquals(3, clock.currentRound());
        recordFullRound(s);

        s.failFinalRound();

        assertEquals(GamePhase.FAILED, clock.phase());
        assertEquals(2, s.echoQueue().size(), "最终轮不生成无法使用的新残影");
        assertTrue(s.currentBuffer().isEmpty());
    }

    @Test
    void finalRoundCannotUseNormalRoundEnd() {
        RoundClock clock = playingClock(D, 1);
        RecordingSession s = new RecordingSession(clock, new EchoQueue(2));
        s.beginRound();
        recordFullRound(s);
        assertThrows(IllegalStateException.class, () -> s.completeNormalRound(() -> {}),
                "最终轮读秒归零应走 FAILED，不得走普通轮末事务");
    }

    @Test
    void restartFromPause_clearsSessionAndReturnsToRoundOne() {
        RoundClock clock = playingClock(D, 3);
        RecordingSession s = new RecordingSession(clock, new EchoQueue(2));
        AtomicInteger restoreCalls = new AtomicInteger();
        s.beginRound();
        recordFullRound(s);
        s.completeNormalRound(() -> {});   // -> READY 第 2 轮，队列中有 E1
        clock.transition(GamePhase.PLAYING);
        s.recordFrame(frame(0));
        clock.transition(GamePhase.PAUSED);

        s.restartFromFirstRound(restoreCalls::incrementAndGet);

        assertEquals(1, clock.currentRound(), "回到第 1 轮");
        assertEquals(GamePhase.READY, clock.phase());
        assertTrue(s.echoQueue().isEmpty(), "全部残影被清空");
        assertEquals(1, restoreCalls.get(), "初始快照恢复恰好调用一次");
        assertTrue(s.currentBuffer().isPresent(), "已为第 1 轮开新缓冲");
        assertEquals(0, s.currentBuffer().get().size());
    }

    @Test
    void recordFrame_rejectedOutsidePlaying() {
        RoundClock clock = playingClock(D, 3);
        RecordingSession s = new RecordingSession(clock, new EchoQueue(2));
        clock.transition(GamePhase.PAUSED);
        assertThrows(IllegalStateException.class, () -> s.recordFrame(frame(0)),
                "暂停不推进逻辑刻，也不写帧");
    }

    @Test
    void normalRoundEnd_rejectsIncompleteBuffer() {
        RoundClock clock = playingClock(D, 3);
        RecordingSession s = new RecordingSession(clock, new EchoQueue(2));
        s.beginRound();
        s.recordFrame(frame(0));
        assertThrows(IllegalStateException.class, () -> s.completeNormalRound(() -> {}),
                "未满长缓冲不能封装（对应 D-1 帧不能封装的边界）");
    }

    @Test
    void slidingWindowAcrossThreeRounds() {
        // 连续三轮事务：第 3 轮为 E1+E2，第 4 轮（若继续）淘汰 E1。
        RoundClock clock = playingClock(D, 4);
        RecordingSession s = new RecordingSession(clock, new EchoQueue(2));
        s.beginRound();
        recordFullRound(s);
        s.completeNormalRound(() -> {});   // E1 -> READY 第 2 轮
        clock.transition(GamePhase.PLAYING);
        recordFullRound(s);
        s.completeNormalRound(() -> {});   // E2 -> READY 第 3 轮
        assertEquals(2, s.echoQueue().activeEchoes(3).size(), "第 3 轮为 E1+E2");
        clock.transition(GamePhase.PLAYING);
        recordFullRound(s);
        s.completeNormalRound(() -> {});   // E3 -> READY 第 4 轮，E1 淘汰
        var active = s.echoQueue().activeEchoes(4);
        assertEquals(2, active.size());
        assertEquals(2, active.get(0).sourceRound(), "第 4 轮为 E2+E3");
        assertEquals(3, active.get(1).sourceRound());
    }
    // ========== R3 新增：recordEvent 转发 =========

    @Test
    void recordEvent_beforePlaying_rejected() {
        // 未进入 PLAYING 的时钟：READY 阶段
        RoundClock clock = new RoundClock(D, 3);
        RecordingSession s = new RecordingSession(clock, new EchoQueue(2));
        clock.transition(GamePhase.MENU);
        clock.transition(GamePhase.LEVEL_SELECT);
        clock.transition(GamePhase.READY);
        s.beginRound();
        TimelineEvent e = event(0, "player", "L01_plate_left",
                TimelineEvent.EventType.DOCK_ENTERED);
        assertThrows(IllegalStateException.class, () -> s.recordEvent(e),
                "READY 阶段不写事件");
    }

    @Test
    void recordEvent_afterPlaying_recordsToBuffer() {
        RoundClock clock = playingClock(D, 3);
        RecordingSession s = new RecordingSession(clock, new EchoQueue(2));
        s.beginRound();
        TimelineEvent e = event(0, "player", "L01_plate_left",
                TimelineEvent.EventType.DOCK_ENTERED);
        s.recordEvent(e);
        assertTrue(s.currentBuffer().isPresent());
        assertEquals(1, s.currentBuffer().get().events().size());
        assertEquals(TimelineEvent.EventType.DOCK_ENTERED,
                s.currentBuffer().get().events().get(0).eventType());
    }

    @Test
    void recordEvent_nullEvent_throws() {
        RoundClock clock = playingClock(D, 3);
        RecordingSession s = new RecordingSession(clock, new EchoQueue(2));
        s.beginRound();
        assertThrows(NullPointerException.class, () -> s.recordEvent(null));
    }

    @Test
    void recordEvent_fullRound_eventsSealedAndSorted() {
        RoundClock clock = playingClock(D, 3);
        RecordingSession s = new RecordingSession(clock, new EchoQueue(2));
        s.beginRound();
        // 乱序收集两条同 tick 事件
        s.recordEvent(event(0, "player", "L01_plate_right",
                TimelineEvent.EventType.DOCK_ENTERED));
        s.recordEvent(event(0, "player", "L01_plate_left",
                TimelineEvent.EventType.DOCK_ENTERED));
        recordFullRound(s);
        s.completeNormalRound(() -> {});

        // 封装后事件按 STABLE_ORDER 排序：plate_left 在 plate_right 前
        var active = s.echoQueue().activeEchoes(2);
        assertEquals(1, active.size());
        var events = active.get(0).allEvents();
        assertEquals(2, events.size());
        assertEquals("L01_plate_left", events.get(0).mechanismId());
        assertEquals("L01_plate_right", events.get(1).mechanismId());
    }

    private static TimelineEvent event(long tick, String actor, String mechanism,
                                       TimelineEvent.EventType type) {
        return new TimelineEvent(tick, actor, 1, mechanism, type, null, null);
    }
}

