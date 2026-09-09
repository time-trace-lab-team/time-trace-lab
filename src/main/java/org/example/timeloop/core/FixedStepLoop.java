package org.example.timeloop.core;

import java.util.Objects;

/**
 * 将固定步长时钟接入纯逻辑更新端口。
 *
 * <p>该类不拥有游戏状态，也不维护第二套计时器。每次动画帧到达时，
 * 由 {@link FixedStepClock} 决定需要补算的逻辑刻数量，再逐刻调用
 * {@link TickUpdatePort#stepOnce()}。</p>
 */
public final class FixedStepLoop {

    private final FixedStepClock clock;
    private final TickUpdatePort updatePort;

    /**
     * 创建一个使用新固定步长时钟的逻辑循环。
     *
     * @param updatePort 每个逻辑刻执行一次的纯逻辑更新端口
     */
    public FixedStepLoop(TickUpdatePort updatePort) {
        this.clock = new FixedStepClock();
        this.updatePort = Objects.requireNonNull(updatePort, "updatePort");
    }

    /**
     * 处理一帧 JavaFX 动画时间。
     *
     * <p>一个渲染帧可能对应零个、一个或多个逻辑刻。逻辑更新只按完整刻
     * 执行；当本帧没有完整逻辑刻时，不调用更新端口。</p>
     *
     * @param nanoTime JavaFX {@code AnimationTimer} 提供的纳秒时间戳
     * @param phase 当前游戏阶段
     */
    public void onAnimationFrame(long nanoTime, GamePhase phase) {
        int steps = clock.advance(nanoTime, phase);
        for (int i = 0; i < steps; i++) {
            updatePort.stepOnce();
        }
    }

    /**
     * 获取当前逻辑状态到下一逻辑刻之间的渲染插值比例。
     *
     * @return 恒在 {@code [0, 1)} 的插值比例
     */
    public double interpolationAlpha() {
        return clock.getInterpolationAlpha();
    }
}
