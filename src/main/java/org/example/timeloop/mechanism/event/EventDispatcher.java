package org.example.timeloop.mechanism.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class EventDispatcher {

    private static final EventDispatcher INSTANCE = new EventDispatcher();
    private final Map<String, List<GameObserver>> listeners = new ConcurrentHashMap<>();

    private EventDispatcher() {}

    public static EventDispatcher getInstance() {
        return INSTANCE;
    }

    public void register(String eventType, GameObserver observer) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(observer);
    }

    public void unregister(String eventType, GameObserver observer) {
        List<GameObserver> list = listeners.get(eventType);
        if (list != null) {
            list.remove(observer);
        }
    }

    public void unregisterAll(GameObserver observer) {
        for (List<GameObserver> list : listeners.values()) {
            list.remove(observer);
        }
    }

    public void dispatch(GameEvent event) {
        List<GameObserver> list = listeners.get(event.eventType());
        if (list != null) {
            for (GameObserver observer : list) {
                observer.onEvent(event);
            }
        }
    }

    public void dispatchAll(List<GameEvent> events) {
        for (GameEvent event : events) {
            dispatch(event);
        }
    }

    public void clear() {
        listeners.clear();
    }
}