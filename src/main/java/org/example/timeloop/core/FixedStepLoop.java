package org.example.timeloop.core;

import java.util.Objects;

/**
 * 将固定步长时钟接入纯逻辑更新端口。
 *
 * <p>该类不拥有游戏状态，也不维护第二套计时器。每次动画帧到达时，
 * 由 {@link FixedStepClock} 决定需要补算的逻辑刻数量，再逐刻调用
 * {@link TickUpdatePort#stepOnce()}。</p>
 *
 * <p>一旦某次更新的结果不是 {@link TickStepResult#ADVANCED}，本帧剩余的补算额度
 * 立即丢弃，不会再次执行同一末刻，也不会把额度带入下一轮。</p>
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
     * 兼容目前仍为空的 C1 启动壳。
     *
     * <p>该构造器只适用于尚未接入共享轮次结果的空更新端口；真实玩法接入时
     * 必须使用 {@link TickUpdatePort}，不能通过此入口吞掉
     * {@link TickStepResult#ROUND_END}。</p>
     *
     * @param update 每个逻辑刻执行的空壳更新动作
     */
    public FixedStepLoop(Runnable update) {
        this(toUnboundedPort(update));
    }

    /**
     * 为尚未接入共享时钟结果的启动壳创建循环。
     *
     * <p>启动壳当前没有玩法逻辑，也没有轮末结果可报告，因此每次回调都视为
     * 可继续。真正接入共享轮次时，更新端口必须直接返回
     * {@link TickStepResult}。</p>
     *
     * @param update 每个逻辑刻执行的更新动作
     * @return 不携带结果的固定步长循环
     */
    public static FixedStepLoop forUnboundedUpdates(Runnable update) {
        return new FixedStepLoop(update);
    }

    private static TickUpdatePort toUnboundedPort(Runnable update) {
        Objects.requireNonNull(update, "update");
        return () -> {
            update.run();
            return TickStepResult.ADVANCED;
        };
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
            TickStepResult result = Objects.requireNonNull(updatePort.stepOnce(), "stepOnce result");
            if (!result.shouldContinueFrame()) {
                // NO_ADVANCE / ROUND_END 都终止本帧补算；剩余额度故意丢弃。
                break;
            }
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
