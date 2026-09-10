package org.example.timeloop.mechanism.event;

@FunctionalInterface
public interface GameObserver {
    void onEvent(GameEvent event);
}