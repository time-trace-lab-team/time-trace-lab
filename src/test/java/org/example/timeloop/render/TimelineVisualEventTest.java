package org.example.timeloop.render;

import org.example.timeloop.level.model.Vector2D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimelineVisualEventTest {

    private static final Vector2D POSITION = new Vector2D(48.0, 96.0);

    @Test
    void acceptsFrozenR3AndFutureEventShapes() {
        for (TimelineEventKind kind : TimelineEventKind.values()) {
            int delayTicks = kind == TimelineEventKind.RAY_DELAY ? 30 : 0;
            assertDoesNotThrow(() -> new TimelineVisualEvent(1, 60, POSITION, kind, delayTicks));
        }
    }

    @Test
    void rejectsInvalidAuthorityProjectionValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new TimelineVisualEvent(0, 0, POSITION, TimelineEventKind.TICK_MARK, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new TimelineVisualEvent(1, -1, POSITION, TimelineEventKind.TICK_MARK, 0));
        assertThrows(NullPointerException.class,
                () -> new TimelineVisualEvent(1, 0, null, TimelineEventKind.TICK_MARK, 0));
        assertThrows(NullPointerException.class,
                () -> new TimelineVisualEvent(1, 0, POSITION, null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new TimelineVisualEvent(1, 0, POSITION, TimelineEventKind.RAY_DELAY, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new TimelineVisualEvent(1, 0, POSITION, TimelineEventKind.DOCK_ENTER, 1));
    }
}
