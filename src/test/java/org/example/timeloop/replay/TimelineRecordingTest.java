package org.example.timeloop.replay;

import org.example.timeloop.core.ActorPhase;
import org.example.timeloop.core.AnimationState;
import org.example.timeloop.core.Direction;
import org.example.timeloop.core.MovementState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R2 自动测试：定长录制缓冲的索引、满长、封装与丢弃边界。
 */
class TimelineRecordingTest {

    private static PlayerFrame frame(long tick) {
        return new PlayerFrame(tick, tick * 2.0, 0.0, Direction.RIGHT, false,
                MovementState.CRUISING, ActorPhase.AVAILABLE, 0, AnimationState.MOVING);
    }

    private static TimelineRecording filled(int durationTicks) {
        TimelineRecording r = new TimelineRecording(durationTicks, 1);
        for (int i = 0; i < durationTicks; i++) {
            r.record(frame(i));
        }
        return r;
    }

    @Test
    void emptyBuffer_isIncompleteAndUnsealed() {
        TimelineRecording r = new TimelineRecording(5, 1);
        assertEquals(0, r.size());
        assertFalse(r.isComplete());
        assertFalse(r.isSealed());
    }

    @Test
    void frameTick_mustEqualListIndex() {
        TimelineRecording r = filled(5);
        assertTrue(r.isComplete());
        for (int i = 0; i < 5; i++) {
            assertEquals(i, r.frameAt(i).tick(), "帧 tick 必须等于其列表索引");
        }
    }

    @Test
    void exactlyDurationFrames_thenComplete() {
        TimelineRecording r = new TimelineRecording(5, 2);
        for (int i = 0; i < 4; i++) {
            r.record(frame(i));
            assertFalse(r.isComplete(), "第 " + i + " 帧后不应满长");
        }
        r.record(frame(4));
        assertTrue(r.isComplete());
        assertEquals(5, r.size());
    }

    @Test
    void missingTick_rejected() {
        TimelineRecording r = new TimelineRecording(5, 1);
        r.record(frame(0));
        assertThrows(IllegalArgumentException.class, () -> r.record(frame(2)),
                "跳号写入必须失败，不能静默错位");
        assertEquals(1, r.size());
    }

    @Test
    void duplicateTick_rejected() {
        TimelineRecording r = new TimelineRecording(5, 1);
        r.record(frame(0));
        assertThrows(IllegalArgumentException.class, () -> r.record(frame(0)),
                "同一 tick 重复写入必须失败");
    }

    @Test
    void overfill_rejected() {
        TimelineRecording r = filled(3);
        assertThrows(IllegalStateException.class, () -> r.record(frame(3)),
                "超过 durationTicks 的写入必须失败，不存在索引 D");
    }

    @Test
    void seal_beforeComplete_rejected() {
        TimelineRecording r = new TimelineRecording(5, 1);
        r.record(frame(0));
        assertThrows(IllegalStateException.class, r::seal,
                "未满长缓冲不能封装，应直接丢弃");
    }

    @Test
    void seal_onceOnly() {
        TimelineRecording r = filled(5);
        r.seal();
        assertTrue(r.isSealed());
        assertThrows(IllegalStateException.class, r::seal, "满长记录只能封装一次");
    }

    @Test
    void record_afterSeal_rejected() {
        TimelineRecording r = filled(5);
        r.seal();
        assertThrows(IllegalStateException.class, () -> r.record(frame(4)),
                "封装后必须拒绝后续写入");
    }

    @Test
    void dockedSegment_stillWritesFramePerTick() {
        TimelineRecording r = new TimelineRecording(4, 1);
        // 驻留期间位置不变，但仍必须逐刻写帧，残影才能按记录复现停驻长度。
        for (int i = 0; i < 4; i++) {
            r.record(new PlayerFrame(i, 100.0, 200.0, Direction.UP, false,
                    MovementState.DOCKED, ActorPhase.AVAILABLE, 0, AnimationState.DOCKED));
        }
        assertTrue(r.isComplete());
        assertEquals(100.0, r.frameAt(0).x());
        assertEquals(100.0, r.frameAt(3).x(), "驻留段位置不变但帧数照常累积");
        assertEquals(MovementState.DOCKED, r.frameAt(3).movementState());
    }

    @Test
    void frames_returnsUnmodifiableSnapshot() {
        TimelineRecording r = filled(3);
        List<PlayerFrame> frames = r.frames();
        assertEquals(3, frames.size());
        assertThrows(UnsupportedOperationException.class, () -> frames.add(frame(3)));
    }

    @Test
    void singleTickRound_hasExactlyOneFrame() {
        TimelineRecording r = new TimelineRecording(1, 1);
        r.record(frame(0));
        assertTrue(r.isComplete());
        r.seal();
        assertEquals(1, r.frames().size());
        assertThrows(IllegalStateException.class, () -> r.record(frame(1)));
    }

    @Test
    void invalidConstructorArguments_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new TimelineRecording(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new TimelineRecording(5, 0));
    }

    @Test
    void frameAt_outOfRange_rejected() {
        TimelineRecording r = filled(3);
        assertThrows(IndexOutOfBoundsException.class, () -> r.frameAt(3));
        assertThrows(IndexOutOfBoundsException.class, () -> r.frameAt(-1));
    }
}
