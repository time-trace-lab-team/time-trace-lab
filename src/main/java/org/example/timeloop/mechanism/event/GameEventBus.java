package org.example.timeloop.mechanism.event;

/**
 * 机制事件总线的窄端口（BUG-002-LIFECYCLE Phase 1）。
 *
 * <p>机关只依赖本端口，不依赖 {@link EventDispatcher} 具体类，也不依赖全局单例。
 * 关卡装配应持有一个总线实例并注入本端口；测试可为每个用例创建独立实例，互不污染。</p>
 */
public interface GameEventBus {

    void register(String eventType, GameObserver observer);

    void unregister(String eventType, GameObserver observer);

    void unregisterAll(GameObserver observer);

    void dispatch(GameEvent event);
}
