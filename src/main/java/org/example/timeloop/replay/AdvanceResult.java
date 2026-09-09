package org.example.timeloop.replay;

/**
 * 单次逻辑刻推进的结果。
 */
public enum AdvanceResult {

    /** 当前阶段冻结，未推进（TUTORIAL / READY / PAUSED / RESETTING / RESULT / FAILED 等零推进）。 */
    NO_ADVANCE,

    /** 推进到下一逻辑刻。 */
    ADVANCED,

    /** 已到达本轮最后一帧（{@code durationTicks - 1}），不再存在下一帧，发出轮末信号。 */
    ROUND_END
}
