package org.example.timeloop.replay;

import org.example.timeloop.core.Direction;

import java.util.Comparator;
import java.util.Objects;

public record TimelineEvent(
        long tick,
        String actorId,
        int sourceRound,
        String mechanismId,
        EventType eventType,
        Direction leaveDirection,
        String reason
) {

    public enum EventType {
        DOCK_ENTERED,
        DOCK_LEFT,
        OCCUPANCY_RELEASED
    }

    public static final Comparator<TimelineEvent> STABLE_ORDER =
            Comparator.comparingLong(TimelineEvent::tick)
                    .thenComparingInt(e -> e.eventType().ordinal())
                    .thenComparing(TimelineEvent::mechanismId)
                    .thenComparing(TimelineEvent::actorId)
                    .thenComparingInt(TimelineEvent::sourceRound);

    public TimelineEvent {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(mechanismId, "mechanismId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        if (tick < 0) throw new IllegalArgumentException("tick must be >= 0");
        if (sourceRound < 0) throw new IllegalArgumentException("sourceRound must be >= 0");
        if (actorId.isEmpty()) throw new IllegalArgumentException("actorId must not be empty");
        if (mechanismId.isEmpty()) throw new IllegalArgumentException("mechanismId must not be empty");
    }
}