package org.example.timeloop.mechanism.resonance;

/** 固定区域共振的唯一玩法状态。 */
public enum ResonanceState {
    /** 尚未有有效历史残影进入本区域。 */
    DORMANT,
    /** 已有一个历史残影进入，正在等待不同来源的第二个残影。 */
    ARMED,
    /** 两个不同来源的历史残影已在窗口内进入；保持到轮次边界。 */
    LATCHED
}
