package org.example.timeloop.render;

import java.util.Objects;

/**
 * 残影寿命的不可变只读渲染投影。
 *
 * <p>最后有效轮、消散阶段和进度均由 replay/app 上游提供。render 不得从来源轮次、
 * 当前轮次或本地计时器推算这些值。</p>
 */
public record EchoLifecycleVisual(int sourceRound,
                                  boolean lastEffectiveRound,
                                  EchoVisualPhase phase,
                                  double effectProgress) {

    public EchoLifecycleVisual {
        if (sourceRound < 1) {
            throw new IllegalArgumentException("sourceRound 必须 >= 1");
        }
        Objects.requireNonNull(phase, "phase");
        if (!Double.isFinite(effectProgress) || effectProgress < 0.0 || effectProgress > 1.0) {
            throw new IllegalArgumentException("effectProgress 必须位于 [0, 1]");
        }
        if (phase == EchoVisualPhase.LAST_EFFECTIVE_ROUND && !lastEffectiveRound) {
            throw new IllegalArgumentException("LAST_EFFECTIVE_ROUND 必须标记 lastEffectiveRound");
        }
    }

    /** 本轮是否应以“最后有效轮”语义强调该残影。 */
    public boolean isLastEffectiveRound() {
        return lastEffectiveRound || phase == EchoVisualPhase.LAST_EFFECTIVE_ROUND;
    }
}
