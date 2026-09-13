package org.example.timeloop.mechanism.resonance;

/** 共振轮内状态的两种受支持恢复边界。 */
public enum ResonanceResetReason {
    /** 正常轮次结束后，下一轮从 {@link ResonanceState#DORMANT} 开始。 */
    ROUND_END,
    /** 整局从第一轮重新开始时，清除全部共振轮内状态。 */
    FULL_RESTART
}
