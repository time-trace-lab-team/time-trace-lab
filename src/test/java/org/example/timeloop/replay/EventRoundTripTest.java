package org.example.timeloop.replay;

import org.example.timeloop.core.ActorPhase;
import org.example.timeloop.core.AnimationState;
import org.example.timeloop.core.Direction;
import org.example.timeloop.core.GamePhase;
import org.example.timeloop.core.MovementState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * X-MOVE-COLLAPSE-01-DEV2 · R-2 事件录制闭环。
 *
 * <p>锁定契约：{@code RecordingSession.recordEvent} → {@code TimelineRecording} →
 * {@code EchoState.eventsAt(tick)} 逐字段 round-trip 相等；同 tick 多事件按
 * {@link TimelineEvent#STABLE_ORDER} 稳定排序；一条 DOCK_ENTERED / DOCK_LEFT 序列
 * 录制后，下一轮能重放出「占用 / 释放」语义。</p>
 *
 * <p>本测试不修改 app/**，只证明 replay/** 的录制与回放闭环保真；
 * app 如何接线（调用 recordEvent）由交付文档的「调用点契约清单」给出。</p>
 */
class EventRoundTripTest {

    private static final int D = 240;
    private static final int ENTER_TICK = 40;
    private static final int LEAVE_TICK = 210;
    private static final String PLATE = "L01_plate_left";
    private static final String ACTOR = "player";

    private static RoundClock playingClock(int durationTicks, int maxRounds) {
        RoundClock c = new RoundClock(durationTicks, maxRounds);
        c.transition(GamePhase.MENU);
        c.transition(GamePhase.LEVEL_SELECT);
        c.transition(GamePhase.READY);
        c.transition(GamePhase.PLAYING);
        return c;
    }

    private static PlayerFrame frame(long tick) {
        return new PlayerFrame(tick, tick * 1.0, 0, Direction.RIGHT, false,
                MovementState.CRUISING, ActorPhase.AVAILABLE, 0, AnimationState.MOVING);
    }

    private static TimelineEvent entered(long tick) {
        return new TimelineEvent(tick, ACTOR, 1, PLATE,
                TimelineEvent.EventType.DOCK_ENTERED, null, null);
    }

    private static TimelineEvent left(long tick, Direction dir) {
        return new TimelineEvent(tick, ACTOR, 1, PLATE,
                TimelineEvent.EventType.DOCK_LEFT, dir, "NEW_DIRECTION");
    }

    /** 第 1 轮录制满长帧 + 若干事件并封装，返回第 2 轮可回放的残影。 */
    private static EchoState recordRoundWithEvents(List<TimelineEvent> events) {
        RoundClock clock = playingClock(D, 3);
        RecordingSession session = new RecordingSession(clock, new EchoQueue(2));
        session.beginRound();
        for (int i = 0; i < D; i++) {
            session.recordFrame(frame(i));
        }
        for (TimelineEvent e : events) {
            session.recordEvent(e);
        }
        session.completeNormalRound(() -> {});
        return session.echoQueue().activeEchoes(2).get(0);
    }

    private static void assertEventEquals(TimelineEvent expected, TimelineEvent actual) {
        assertEquals(expected.tick(), actual.tick(), "tick 不一致");
        assertEquals(expected.actorId(), actual.actorId(), "actorId 不一致");
        assertEquals(expected.sourceRound(), actual.sourceRound(), "sourceRound 不一致");
        assertEquals(expected.mechanismId(), actual.mechanismId(), "mechanismId 不一致");
        assertEquals(expected.eventType(), actual.eventType(), "eventType 不一致");
        assertEquals(expected.leaveDirection(), actual.leaveDirection(), "leaveDirection 不一致");
        assertEquals(expected.reason(), actual.reason(), "reason 不一致");
    }

    @Test
    void recordEvent_roundTripsFieldByField() {
        TimelineEvent enter = entered(ENTER_TICK);
        TimelineEvent leave = left(LEAVE_TICK, Direction.RIGHT);
        TimelineEvent released = new TimelineEvent(LEAVE_TICK + 1, ACTOR, 1, PLATE,
                TimelineEvent.EventType.OCCUPANCY_RELEASED, null, "ROUND_END");

        EchoState echo = recordRoundWithEvents(List.of(enter, leave, released));

        // 三个不同事件类型逐字段 round-trip：写入什么，回放就得到什么
        assertEquals(1, echo.eventsAt(ENTER_TICK).size());
        assertEventEquals(enter, echo.eventsAt(ENTER_TICK).get(0));

        assertEquals(1, echo.eventsAt(LEAVE_TICK).size());
        assertEventEquals(leave, echo.eventsAt(LEAVE_TICK).get(0));

        assertEquals(1, echo.eventsAt(LEAVE_TICK + 1).size());
        assertEventEquals(released, echo.eventsAt(LEAVE_TICK + 1).get(0));
    }

    @Test
    void dockEnteredThenLeft_replaysOccupancyAndReleaseSemantics() {
        EchoState echo = recordRoundWithEvents(
                List.of(entered(ENTER_TICK), left(LEAVE_TICK, Direction.RIGHT)));

        // t=40：进入驻留 → 占用开始
        List<TimelineEvent> atEnter = echo.eventsAt(ENTER_TICK);
        assertEquals(1, atEnter.size());
        assertEquals(TimelineEvent.EventType.DOCK_ENTERED, atEnter.get(0).eventType());

        // t=41..209：无任何事件 → 占用持续（边沿语义，中间不重复发进入事件）
        for (int t = ENTER_TICK + 1; t < LEAVE_TICK; t++) {
            assertTrue(echo.eventsAt(t).isEmpty(),
                    "tick " + t + " 不应有事件（占用持续期）");
        }

        // t=210：离开驻留 → 释放，且带离开方向与原因
        List<TimelineEvent> atLeave = echo.eventsAt(LEAVE_TICK);
        assertEquals(1, atLeave.size());
        assertEquals(TimelineEvent.EventType.DOCK_LEFT, atLeave.get(0).eventType());
        assertEquals(Direction.RIGHT, atLeave.get(0).leaveDirection());
        assertEquals("NEW_DIRECTION", atLeave.get(0).reason());
    }

    @Test
    void sameTickEvents_sortedByStableOrder() {
        // 同 tick=40 乱序写入两条不同优先级事件：
        // DOCK_LEFT(priority 10) 必须排在 DOCK_ENTERED(priority 30) 之前，与写入顺序无关。
        TimelineEvent entered = entered(ENTER_TICK);
        TimelineEvent left = left(ENTER_TICK, Direction.UP);
        EchoState echo = recordRoundWithEvents(List.of(entered, left));

        List<TimelineEvent> at = echo.eventsAt(ENTER_TICK);
        assertEquals(2, at.size());
        assertEquals(TimelineEvent.EventType.DOCK_LEFT, at.get(0).eventType(),
                "priority 小的 DOCK_LEFT 必须先出（稳定排序）");
        assertEquals(TimelineEvent.EventType.DOCK_ENTERED, at.get(1).eventType());
    }

    @Test
    void recordEvent_rejectedOutsidePlaying() {
        TimelineEvent e = entered(0);

        // RESETTING 阶段（轮末事务冻结期）：不得写事件
        RoundClock resetting = playingClock(D, 3);
        RecordingSession s1 = new RecordingSession(resetting, new EchoQueue(2));
        s1.beginRound();
        resetting.transition(GamePhase.RESETTING);
        assertThrows(IllegalStateException.class, () -> s1.recordEvent(e),
                "RESETTING 阶段不得写事件");

        // RESULT 阶段（目标达成结算）：不得写事件
        RoundClock result = playingClock(D, 3);
        RecordingSession s2 = new RecordingSession(result, new EchoQueue(2));
        s2.beginRound();
        result.transition(GamePhase.RESULT);
        assertThrows(IllegalStateException.class, () -> s2.recordEvent(e),
                "RESULT 阶段不得写事件");
    }
}
