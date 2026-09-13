package org.example.timeloop.replay;

import org.example.timeloop.core.ActorPhase;
import org.example.timeloop.core.AnimationState;
import org.example.timeloop.core.Direction;
import org.example.timeloop.core.GamePhase;
import org.example.timeloop.core.MovementState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * X-MOVE-COLLAPSE-01-DEV2 · R-3 轮边界契约。
 *
 * <p>把「轮末事务结束于 READY、由调用方负责 transition(PLAYING)」写成可执行的测试契约。
 * 这是任务卡指出的「第二轮开始就动不了」的根因：{@code completeNormalRound} 收尾停在 READY，
 * 若调用方（app）只在开局 transition(PLAYING) 一次，则第 1 轮结束后玩家再也不能移动。</p>
 *
 * <p>三条契约：</p>
 * <ol>
 *   <li>{@code completeNormalRound} 后 phase()==READY、roundTick()==0、currentRound()+1；</li>
 *   <li>READY 下 recordFrame / recordEvent 抛 IllegalStateException；</li>
 *   <li>调用方 transition(PLAYING) 后新缓冲可用、帧索引从 0 重新开始、durationTicks 不变。</li>
 * </ol>
 */
class RoundBoundaryContractTest {

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
    void completeNormalRound_endsAtReadyAndCallerMustResumePlaying() {
        RoundClock clock = playingClock(D, 3);
        RecordingSession s = new RecordingSession(clock, new EchoQueue(2));
        s.beginRound();
        recordFullRound(s);
        s.completeNormalRound(() -> {});

        // 契约 ①：轮末事务结束后停在 READY，轮次 +1、刻归零
        assertEquals(GamePhase.READY, clock.phase(), "轮末事务必须停在 READY");
        assertFalse(clock.isPlaying(), "READY 不是 PLAYING，调用方负责启动下一轮");
        assertEquals(0, clock.roundTick(), "刻归零");
        assertEquals(2, clock.currentRound(), "轮次 +1");
        // 新缓冲已开好但为空，等待下一轮写入
        assertTrue(s.currentBuffer().isPresent());
        assertEquals(0, s.currentBuffer().get().size());
    }

    @Test
    void recordFrameAndEvent_rejectedInReady() {
        // 直接停在 READY（模拟轮末事务后的状态）
        RoundClock clock = new RoundClock(D, 3);
        clock.transition(GamePhase.MENU);
        clock.transition(GamePhase.LEVEL_SELECT);
        clock.transition(GamePhase.READY);
        RecordingSession s = new RecordingSession(clock, new EchoQueue(2));
        s.beginRound();

        // 契约 ②：READY 下写帧、写事件都必须抛异常
        assertThrows(IllegalStateException.class, () -> s.recordFrame(frame(0)),
                "READY 阶段不得写帧");
        assertThrows(IllegalStateException.class, () -> s.recordEvent(new TimelineEvent(
                        0, "player", 1, "L01_plate_left",
                        TimelineEvent.EventType.DOCK_ENTERED, null, null)),
                "READY 阶段不得写事件");
    }

    @Test
    void resumePlayingAfterReady_newBufferReusableAndTickRestartsFromZero() {
        RoundClock clock = playingClock(D, 3);
        RecordingSession s = new RecordingSession(clock, new EchoQueue(2));
        s.beginRound();
        recordFullRound(s);
        s.completeNormalRound(() -> {});   // -> READY 第 2 轮

        // 调用方负责启动下一轮
        clock.transition(GamePhase.PLAYING);

        // 契约 ③：新缓冲可复用、轮长冻结、帧索引从 0 重新开始
        assertTrue(s.currentBuffer().isPresent(), "transition(PLAYING) 后新缓冲已开好");
        TimelineRecording buf = s.currentBuffer().get();
        assertEquals(D, buf.durationTicks(), "轮长 durationTicks 必须冻结不变");
        assertEquals(0, buf.size(), "新缓冲从空开始");

        s.recordFrame(frame(0));
        assertEquals(0, s.currentBuffer().get().frameAt(0).tick(), "帧索引从 0 重新开始");
        s.recordFrame(frame(1));
        assertEquals(1, s.currentBuffer().get().frameAt(1).tick(), "帧索引连续递增");
    }
}
