package org.example.timeloop.core;

/**
 * 单次逻辑 tick 推进的权威结果。
 */
public enum TickStepResult {

    /** 当前阶段不推进逻辑。 */
    NO_ADVANCE,
    /** 当前 tick 已处理且共享时钟成功推进。 */
    ADVANCED,
    /** 当前轮最后一个 tick 已处理，等待上层发起轮次事务。 */
    ROUND_END;

    /**
     * 本结果是否允许固定步长循环继续消费当前渲染帧的补算额度。
     *
     * @return 仅 {@link #ADVANCED} 返回 {@code true}
     */
    public boolean shouldContinueFrame() {
        return this == ADVANCED;
    }
}
