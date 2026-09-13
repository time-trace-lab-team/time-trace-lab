package org.example.timeloop.replay;

import org.example.timeloop.core.ActorPhase;
import org.example.timeloop.core.AnimationState;
import org.example.timeloop.core.Direction;
import org.example.timeloop.core.MovementState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R3 自动测试：单残影按共享 roundTick 确定性播放，残影允许事件的读取与过滤。
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
            MovementState state = (i >= 2 && i <= 3) ? MovementState.DOCKED : MovementState.CRUISING;
            r.record(frame(i, state));
        }
        r.seal();
        return r;
    }

    private static TimelineEvent event(long tick, String actor, String mechanism,
                                       TimelineEvent.EventType type) {
        return new TimelineEvent(tick, actor, 1, mechanism, type, null, null);
    }

    // ========== 确定性播放 ==========

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

    // ========== 事件访问 ==========

    @Test
    void eventsAt_emptyWhenNoEventsCollected() {
        EchoState echo = EchoState.of(sealedRecording(6));
        assertTrue(echo.eventsAt(0L).isEmpty());
        assertTrue(echo.eventsAt(3L).isEmpty());
    }

    @Test
    void eventsAt_returnsOnlyMatchingTick() {
        TimelineRecording r = new TimelineRecording(6, 1);
        for (int i = 0; i < 6; i++) {
            r.record(frame(i, MovementState.CRUISING));
        }
        r.recordEvent(event(2, "player", "L01_plate_left", TimelineEvent.EventType.DOCK_ENTERED));
        r.recordEvent(event(4, "player", "L01_plate_left", TimelineEvent.EventType.DOCK_LEFT));
        r.seal();

        EchoState echo = EchoState.of(r);
        List<TimelineEvent> at2 = echo.eventsAt(2L);
        assertEquals(1, at2.size());
        assertEquals(TimelineEvent.EventType.DOCK_ENTERED, at2.get(0).eventType());

        List<TimelineEvent> at4 = echo.eventsAt(4L);
        assertEquals(1, at4.size());
        assertEquals(TimelineEvent.EventType.DOCK_LEFT, at4.get(0).eventType());

        assertTrue(echo.eventsAt(0L).isEmpty());
    }

    @Test
    void eventsAt_returnsStableOrder() {
        TimelineRecording r = new TimelineRecording(6, 1);
        for (int i = 0; i < 6; i++) {
            r.record(frame(i, MovementState.CRUISING));
        }
        r.recordEvent(event(2, "b", "L01_plate_right", TimelineEvent.EventType.DOCK_ENTERED));
        r.recordEvent(event(2, "a", "L01_plate_left", TimelineEvent.EventType.DOCK_ENTERED));
        r.seal();

        EchoState echo = EchoState.of(r);
        List<TimelineEvent> at2 = echo.eventsAt(2L);
        assertEquals(2, at2.size());
        assertEquals("L01_plate_left", at2.get(0).mechanismId());
        assertEquals("L01_plate_right", at2.get(1).mechanismId());
    }

    @Test
    void allEvents_returnsAllCollected() {
        TimelineRecording r = new TimelineRecording(6, 1);
        for (int i = 0; i < 6; i++) {
            r.record(frame(i, MovementState.CRUISING));
        }
        r.recordEvent(event(1, "player", "L01_plate_left", TimelineEvent.EventType.DOCK_ENTERED));
        r.recordEvent(event(3, "player", "L01_plate_left", TimelineEvent.EventType.DOCK_LEFT));
        r.recordEvent(event(5, "player", "L01_plate_left", TimelineEvent.EventType.OCCUPANCY_RELEASED));
        r.seal();

        EchoState echo = EchoState.of(r);
        List<TimelineEvent> all = echo.allEvents();
        assertEquals(3, all.size());
        assertEquals(1L, all.get(0).tick());
        assertEquals(3L, all.get(1).tick());
        assertEquals(5L, all.get(2).tick());
    }

    @Test
    void eventsAt_unmodifiable() {
        TimelineRecording r = new TimelineRecording(6, 1);
        for (int i = 0; i < 6; i++) {
            r.record(frame(i, MovementState.CRUISING));
        }
        r.recordEvent(event(2, "player", "L01_plate_left", TimelineEvent.EventType.DOCK_ENTERED));
        r.seal();

        EchoState echo = EchoState.of(r);
        assertThrows(UnsupportedOperationException.class, () ->
                echo.eventsAt(2L).add(event(2, "x", "L01_door_01", TimelineEvent.EventType.DOCK_ENTERED)));
    }

    @Test
    void allEvents_unmodifiable() {
        EchoState echo = EchoState.of(sealedRecording(6));
        assertThrows(UnsupportedOperationException.class, () ->
                echo.allEvents().add(event(0, "x", "L01_door_01", TimelineEvent.EventType.DOCK_ENTERED)));
    }

    // ========== R3 新增：EXIT_REQUESTED 是玩家专属，残影不能触发 ==========

    @Test
    void eventsAt_filtersExitRequested() {
        TimelineRecording r = new TimelineRecording(6, 1);
        for (int i = 0; i < 6; i++) {
            r.record(frame(i, MovementState.CRUISING));
        }
        // 同 tick 2：残影允许的 DOCK_ENTERED + 玩家专属的 EXIT_REQUESTED
        r.recordEvent(event(2, "player", "L01_plate_left", TimelineEvent.EventType.DOCK_ENTERED));
        r.recordEvent(event(2, "player", "L01_exit_00", TimelineEvent.EventType.EXIT_REQUESTED));
        r.seal();

        EchoState echo = EchoState.of(r);
        List<TimelineEvent> at2 = echo.eventsAt(2L);
        assertEquals(1, at2.size(), "EXIT_REQUESTED 必须被残影过滤掉");
        assertEquals(TimelineEvent.EventType.DOCK_ENTERED, at2.get(0).eventType());
    }

    @Test
    void allEvents_filtersExitRequested() {
        TimelineRecording r = new TimelineRecording(6, 1);
        for (int i = 0; i < 6; i++) {
            r.record(frame(i, MovementState.CRUISING));
        }
        r.recordEvent(event(1, "player", "L01_plate_left", TimelineEvent.EventType.DOCK_ENTERED));
        r.recordEvent(event(2, "player", "L01_exit_00", TimelineEvent.EventType.EXIT_REQUESTED));
        r.recordEvent(event(3, "player", "L01_plate_left", TimelineEvent.EventType.DOCK_LEFT));
        r.seal();

        EchoState echo = EchoState.of(r);
        List<TimelineEvent> all = echo.allEvents();
        assertEquals(2, all.size(), "EXIT_REQUESTED 必须被残影过滤掉");
        assertEquals(TimelineEvent.EventType.DOCK_ENTERED, all.get(0).eventType());
        assertEquals(TimelineEvent.EventType.DOCK_LEFT, all.get(1).eventType());
    }

    @Test
    void eventsAt_keepsMechanismStateChanged() {
        TimelineRecording r = new TimelineRecording(6, 1);
        for (int i = 0; i < 6; i++) {
            r.record(frame(i, MovementState.CRUISING));
        }
        r.recordEvent(event(2, "player", "L01_door_01",
                TimelineEvent.EventType.MECHANISM_STATE_CHANGED));
        r.seal();

        EchoState echo = EchoState.of(r);
        List<TimelineEvent> at2 = echo.eventsAt(2L);
        assertEquals(1, at2.size(), "MECHANISM_STATE_CHANGED 是残影允许事件");
        assertEquals(TimelineEvent.EventType.MECHANISM_STATE_CHANGED, at2.get(0).eventType());
    }
}