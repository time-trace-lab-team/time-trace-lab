package org.example.timeloop.replay;

import org.example.timeloop.core.ActorPhase;
import org.example.timeloop.core.AnimationState;
import org.example.timeloop.core.Direction;
import org.example.timeloop.core.MovementState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R3 自动测试（第一步）：单残影按共享 roundTick 确定性播放。
 */
class EchoStateTest {

    private static PlayerFrame frame(long tick, MovementState state) {
        return new PlayerFrame(tick, 100.0 + tick, 200.0, Direction.RIGHT, false,
                state, ActorPhase.AVAILABLE, 0,
                state == MovementState.DOCKED ? AnimationState.DOCKED : AnimationState.MOVING);
    }

    private static TimelineRecording sealedRecording(int durationTicks) {
        TimelineRecording r = new TimelineRecording(durationTicks, 1);
        for (int i = 0; i < durationTicks; i++) {
            // 模拟"巡行 → 驻留 → 再巡行"：中段写入 DOCKED 静止帧
            MovementState state = (i >= 2 && i <= 3) ? MovementState.DOCKED : MovementState.CRUISING;
            r.record(frame(i, state));
        }
        r.seal();
        return r;
    }

    @Test
    void of_rejectsUnsealedRecording() {
        TimelineRecording r = new TimelineRecording(5, 1);
        assertThrows(IllegalStateException.class, () -> EchoState.of(r),
                "未封装记录不能成为残影");

        TimelineRecording partial = new TimelineRecording(5, 1);
        partial.record(frame(0, MovementState.CRUISING));
        assertThrows(IllegalStateException.class, () -> EchoState.of(partial),
                "未满长记录不能成为残影，不得用最后一帧补齐");
    }

    @Test
    void playbackMatchesRecordingTickByTick() {
        TimelineRecording r = sealedRecording(6);
        EchoState echo = EchoState.of(r);
        for (int t = 0; t < 6; t++) {
            PlayerFrame expected = r.frameAt(t);
            PlayerFrame actual = echo.frameAt(t);
            assertEquals(expected.tick(), actual.tick());
            assertEquals(expected.x(), actual.x(), 1e-9);
            assertEquals(expected.y(), actual.y(), 1e-9);
            assertEquals(expected.movementState(), actual.movementState());
        }
    }

    @Test
    void dockedSegment_isReplayedVerbatim() {
        EchoState echo = EchoState.of(sealedRecording(6));
        assertEquals(MovementState.DOCKED, echo.frameAt(2).movementState());
        assertEquals(MovementState.DOCKED, echo.frameAt(3).movementState());
        assertEquals(MovementState.CRUISING, echo.frameAt(4).movementState(),
                "残影必须按记录时刻离开驻留，不能永久停住或提前滑出");
    }

    @Test
    void noIndependentClock_sameTickAlwaysSameFrame() {
        EchoState echo = EchoState.of(sealedRecording(6));
        PlayerFrame first = echo.frameAt(3);
        PlayerFrame second = echo.frameAt(3);
        PlayerFrame third = echo.frameAt(3);
        assertSame(first, second, "同一 roundTick 必须返回同一不可变帧");
        assertSame(second, third);
    }

    @Test
    void outOfRangeTick_rejected() {
        EchoState echo = EchoState.of(sealedRecording(6));
        assertThrows(IndexOutOfBoundsException.class, () -> echo.frameAt(6),
                "不存在索引 D 的帧");
        assertThrows(IndexOutOfBoundsException.class, () -> echo.frameAt(-1));
    }

    @Test
    void exposesSourceRoundAndDuration() {
        EchoState echo = EchoState.of(sealedRecording(6));
        assertEquals(1, echo.sourceRound());
        assertEquals(6, echo.durationTicks());
    }
}
