package org.example.timeloop.mechanism.resonance;

import java.util.Objects;

/** 单个共享逻辑刻处理结束后的只读共振结果。 */
public record ResonanceTickResult(
        ResonanceState state,
        boolean currentPlayerPreviewed,
        boolean armedThisTick,
        boolean latchedThisTick,
        boolean timedOutThisTick
) {

    public ResonanceTickResult {
        state = Objects.requireNonNull(state, "resonance.result.state");
        if (latchedThisTick && state != ResonanceState.LATCHED) {
            throw new IllegalArgumentException("本 tick 锁存后状态必须为 LATCHED");
        }
    }
}
