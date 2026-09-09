package org.example.timeloop.core;

/**
 * 固定步长循环的逻辑更新端口。
 *
 * <p>每次调用代表推进一个完整的权威逻辑刻。实现方应在这里更新纯逻辑
 * 状态；它不应依赖 JavaFX 的帧回调，也不应根据渲染帧数自行计时。</p>
 */
@FunctionalInterface
public interface TickUpdatePort {

    /**
     * 推进一个逻辑刻。
     */
    void stepOnce();
}
