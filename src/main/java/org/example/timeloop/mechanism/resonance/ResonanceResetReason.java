package org.example.timeloop.mechanism.resonance;

/**
 * 共振轮内状态支持的三种恢复边界。
 *
 * <p>取值集合与 {@code AutoDockResetReason} <b>对齐</b>（R5-B 冻结，PM 2026-09-13 裁决 §四）：
 * 场景退出不再被降级映射成 {@link #FULL_RESTART}，语义区分留在调用方，
 * 聚合器因此不需要 `mapToFullRestart` 之类的原因降级代码。</p>
 *
 * <p>三个原因**都**从 {@link ResonanceState#DORMANT} 重新开始（{@code ResonanceStateMachine.reset}
 * 不按原因分支）。本枚举**按名字序列化、无 ordinal 依赖**：全仓仅本文件、{@code reset} 的
 * {@code requireNonNull} 与测试引用它，没有 {@code ordinal()} / {@code values()} 用法，
 * 因此新增常量是源码兼容变更。</p>
 */
public enum ResonanceResetReason {
    /** 正常轮次结束后，下一轮从 {@link ResonanceState#DORMANT} 开始。 */
    ROUND_END,
    /** 整局从第一轮重新开始时，清除全部共振轮内状态。 */
    FULL_RESTART,
    /** 退出关卡场景时，清除全部共振轮内状态（放弃本会话，动作语义上与整局重开区分开）。 */
    SCENE_EXIT
}
