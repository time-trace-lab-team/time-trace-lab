package org.example.timeloop.replay;

import org.example.timeloop.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TimelineEventTest {

    @Test
    void constructor_validArgs_succeeds() {
        TimelineEvent e = new TimelineEvent(10L, "player", 0, "plate_left",
                TimelineEvent.EventType.DOCK_ENTERED, Direction.UP, "enter");
        assertEquals(10L, e.tick());
        assertEquals("player", e.actorId());
        assertEquals("plate_left", e.mechanismId());
    }

    @Test
    void constructor_nullActorId_throws() {
        assertThrows(NullPointerException.class, () ->
                new TimelineEvent(0L, null, 0, "m", TimelineEvent.EventType.DOCK_ENTERED, null, null));
    }

    @Test
    void constructor_nullMechanismId_throws() {
        assertThrows(NullPointerException.class, () ->
                new TimelineEvent(0L, "a", 0, null, TimelineEvent.EventType.DOCK_ENTERED, null, null));
    }

    @Test
    void constructor_nullEventType_throws() {
        assertThrows(NullPointerException.class, () ->
                new TimelineEvent(0L, "a", 0, "m", null, null, null));
    }

    @Test
    void constructor_negativeTick_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new TimelineEvent(-1L, "a", 0, "m", TimelineEvent.EventType.DOCK_ENTERED, null, null));
    }

    @Test
    void constructor_emptyActorId_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new TimelineEvent(0L, "", 0, "m", TimelineEvent.EventType.DOCK_ENTERED, null, null));
    }

    @Test
    void stableOrder_sortsByTickFirst() {
        TimelineEvent e10 = ev(10, "a", 0, "m1", TimelineEvent.EventType.DOCK_ENTERED);
        TimelineEvent e5 = ev(5, "a", 0, "m1", TimelineEvent.EventType.DOCK_ENTERED);
        List<TimelineEvent> list = new ArrayList<>(List.of(e10, e5));
        list.sort(TimelineEvent.STABLE_ORDER);
        assertEquals(5L, list.get(0).tick());
        assertEquals(10L, list.get(1).tick());
    }

    @Test
    void stableOrder_sameTick_sortsByEventTypePriority() {
        TimelineEvent left = ev(5, "a", 0, "m1", TimelineEvent.EventType.DOCK_LEFT);
        TimelineEvent entered = ev(5, "a", 0, "m1", TimelineEvent.EventType.DOCK_ENTERED);
        TimelineEvent released = ev(5, "a", 0, "m1", TimelineEvent.EventType.OCCUPANCY_RELEASED);
        List<TimelineEvent> list = new ArrayList<>(List.of(left, released, entered));
        list.sort(TimelineEvent.STABLE_ORDER);
        assertEquals(TimelineEvent.EventType.DOCK_ENTERED, list.get(0).eventType());
        assertEquals(TimelineEvent.EventType.DOCK_LEFT, list.get(1).eventType());
        assertEquals(TimelineEvent.EventType.OCCUPANCY_RELEASED, list.get(2).eventType());
    }

    @Test
    void stableOrder_sameTickSameType_sortsByMechanismId() {
        TimelineEvent m2 = ev(5, "a", 0, "plate_right", TimelineEvent.EventType.DOCK_ENTERED);
        TimelineEvent m1 = ev(5, "a", 0, "plate_left", TimelineEvent.EventType.DOCK_ENTERED);
        List<TimelineEvent> list = new ArrayList<>(List.of(m2, m1));
        list.sort(TimelineEvent.STABLE_ORDER);
        assertEquals("plate_left", list.get(0).mechanismId());
        assertEquals("plate_right", list.get(1).mechanismId());
    }

    @Test
    void stableOrder_sameTickSameTypeSameMechanism_sortsByActorId() {
        TimelineEvent b = ev(5, "b", 0, "plate_left", TimelineEvent.EventType.DOCK_ENTERED);
        TimelineEvent a = ev(5, "a", 0, "plate_left", TimelineEvent.EventType.DOCK_ENTERED);
        List<TimelineEvent> list = new ArrayList<>(List.of(b, a));
        list.sort(TimelineEvent.STABLE_ORDER);
        assertEquals("a", list.get(0).actorId());
        assertEquals("b", list.get(1).actorId());
    }

    @Test
    void stableOrder_deterministic_sameInputSameOrder() {
        List<TimelineEvent> input = List.of(
                ev(3, "b", 1, "m2", TimelineEvent.EventType.DOCK_LEFT),
                ev(1, "a", 0, "m1", TimelineEvent.EventType.DOCK_ENTERED),
                ev(3, "a", 1, "m2", TimelineEvent.EventType.DOCK_ENTERED),
                ev(1, "b", 0, "m1", TimelineEvent.EventType.DOCK_LEFT),
                ev(2, "a", 0, "m1", TimelineEvent.EventType.OCCUPANCY_RELEASED));

        List<TimelineEvent> sorted1 = new ArrayList<>(input);
        sorted1.sort(TimelineEvent.STABLE_ORDER);

        List<TimelineEvent> reversed = new ArrayList<>(input);
        Collections.reverse(reversed);
        reversed.sort(TimelineEvent.STABLE_ORDER);

        assertEquals(sorted1, reversed);
    }

    private static TimelineEvent ev(long tick, String actor, int sr, String mech, TimelineEvent.EventType type) {
        return new TimelineEvent(tick, actor, sr, mech, type, null, null);
    }
}