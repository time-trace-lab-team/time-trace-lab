package org.example.timeloop.mechanism.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 机制事件总线（BUG-002-LIFECYCLE）。
 *
 * <p>实现 {@link GameEventBus} 窄端口。{@link #getInstance()} 是<b>兼容层</b>：
 * Phase 2 将在 app 与测试全部迁移后删除全局单例；新代码应构造独立实例、由关卡装配持有，
 * 这样场景切换与"从第一轮重开"不会残留旧监听器。</p>
 */
public final class EventDispatcher implements GameEventBus {

    private static final EventDispatcher INSTANCE = new EventDispatcher();
    private final Map<String, List<GameObserver>> listeners = new ConcurrentHashMap<>();

    /** 兼容层单例（Phase 2 删除）。新代码请用 {@link #EventDispatcher()}。 */
    public static EventDispatcher getInstance() {
        return INSTANCE;
    }

    /** 每个关卡装配应持有自己的总线实例。 */
    public EventDispatcher() {}

    @Override
    public void register(String eventType, GameObserver observer) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(observer);
    }

    @Override
    public void unregister(String eventType, GameObserver observer) {
        List<GameObserver> list = listeners.get(eventType);
        if (list != null) {
            list.remove(observer);
        }
    }

    @Override
    public void unregisterAll(GameObserver observer) {
        for (List<GameObserver> list : listeners.values()) {
            list.remove(observer);
        }
    }

    @Override
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
