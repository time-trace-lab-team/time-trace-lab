package org.example.timeloop.mechanism.event;

public record GameEvent(
        String eventType,
        String sourceId,
        long tick,
        int sourceRound,
        Object payload
) {

    public static final String PLATE_ENTERED = "PLATE_ENTERED";
    public static final String PLATE_EXITED = "PLATE_EXITED";
    public static final String DOOR_UNLOCKED = "DOOR_UNLOCKED";
    public static final String EXIT_TRIGGERED = "EXIT_TRIGGERED";
    public static final String ECHO_DISAPPEARED = "ECHO_DISAPPEARED";
    public static final String TICK_ADVANCED = "TICK_ADVANCED";

    public static GameEvent plateEntered(String sourceId, long tick, int sourceRound) {
        return new GameEvent(PLATE_ENTERED, sourceId, tick, sourceRound, null);
    }

    public static GameEvent plateExited(String sourceId, long tick, int sourceRound) {
        return new GameEvent(PLATE_EXITED, sourceId, tick, sourceRound, null);
    }

    public static GameEvent doorUnlocked(String sourceId, long tick) {
        return new GameEvent(DOOR_UNLOCKED, sourceId, tick, 0, null);
    }

    public static GameEvent exitTriggered(String sourceId, long tick, int sourceRound) {
        return new GameEvent(EXIT_TRIGGERED, sourceId, tick, sourceRound, null);
    }

    public static GameEvent echoDisappeared(String sourceId, long tick, int sourceRound) {
        return new GameEvent(ECHO_DISAPPEARED, sourceId, tick, sourceRound, null);
    }

    public static GameEvent tickAdvanced(long tick) {
        return new GameEvent(TICK_ADVANCED, "system", tick, 0, null);
    }

    public boolean isType(String type) {
        return eventType.equals(type);
    }
}