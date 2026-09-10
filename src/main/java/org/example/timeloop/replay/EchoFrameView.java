package org.example.timeloop.replay;

import java.util.Objects;

/**
 * 单个活跃残影在某刻的只读渲染视图（R3，开发 2 主责）。
 *
 * <p>聚合渲染层需要的字段：残影本刻位置、来源轮次、剩余存活轮数、寿命进度。
 * 数值由共享 {@link TickContext} 派生（{@link EchoLifetime#of(int, int, TickContext)}），
 * 不维护任何独立进度，也不暴露 {@link EchoState} 内部对象。</p>
 *
 * @param sourceRound     残影来源轮次
 * @param frame           本刻的玩家帧（不重新执行输入/路径/碰撞/射线）
 * @param remainingRounds 剩余存活轮数（1 表示本轮是最后有效轮）
 * @param lifeProgress    寿命进度 [0, 1]，仅表现用
 * @param bodyAlpha       身体透明度，0.82 平滑降至 0.50
 */
public record EchoFrameView(
        int sourceRound,
        PlayerFrame frame,
        int remainingRounds,
        double lifeProgress,
        double bodyAlpha
) {

    public EchoFrameView {
        if (sourceRound < 1) {
            throw new IllegalArgumentException("sourceRound 必须 >= 1，实际 " + sourceRound);
        }
        Objects.requireNonNull(frame, "frame");
        if (remainingRounds < 0) {
            throw new IllegalArgumentException(
                    "remainingRounds 必须 >= 0，实际 " + remainingRounds);
        }
        if (lifeProgress < 0.0 || lifeProgress > 1.0) {
            throw new IllegalArgumentException(
                    "lifeProgress 必须在 [0, 1]，实际 " + lifeProgress);
        }
    }
}