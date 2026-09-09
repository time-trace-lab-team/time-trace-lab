package org.example.timeloop.core;

/**
 * 固定步长循环的逻辑更新端口。
 *
 * <p>只有 {@link TickStepResult#ADVANCED} 时，固定步长循环才会继续执行本帧
 * 剩余的补算；{@link TickStepResult#NO_ADVANCE} 与
 * {@link TickStepResult#ROUND_END} 都必须停止本帧补算。</p>
 */
@FunctionalInterface
public interface TickUpdatePort {

    /**
     * 执行一个逻辑刻，并返回调用方定义的推进结果。
     *
     * <p>实现方应在这里完成一个完整 tick 的纯逻辑更新；它不应依赖
     * JavaFX 的帧回调，也不应根据渲染帧数自行计时。</p>
     *
     * @return 本次逻辑刻的权威推进结果
     */
    TickStepResult stepOnce();
}
