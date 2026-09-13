package org.example.timeloop.mechanism.autodock;

import org.example.timeloop.level.model.PathNode;
import org.example.timeloop.level.model.Vector2D;

public interface AutoDockOccupancyPort {

    AutoDockResult tryEnter(String mechanismId,
                             String actorId,
                             int sourceRound,
                             long tick,
                             Vector2D worldPosition);

    /**
     * 占用者在同一逻辑刻按下合法出口方向即离开并释放占用，<b>不要求位置已出区域</b>
     * （README §三；PM 2026-09-10 批准）。
     *
     * <p>判定顺序：未知 dock（{@code UNKNOWN_DOCK}）→ 非占用者（{@code NOT_OCCUPANT}）
     * → 非法方向（{@code INVALID_EXIT_DIRECTION}）→ 释放并返回 {@code LEFT}。
     * 释放后同一 tick 的重入由 {@code SAME_TICK_REENTRY_BLOCKED} 拦截。
     * {@code worldPosition} 只用于参数校验与诊断，不参与释放判定。</p>
     */
    AutoDockResult tryLeave(String mechanismId,
                             String actorId,
                             int sourceRound,
                             long tick,
                             PathNode.Dir exitDirection,
                             Vector2D worldPosition);

    /** 释放指定残影在所有 dock 上的占用，返回实际释放数量。 */
    int releaseActor(String actorId, int sourceRound, long tick);

    /** 轮末、整局重开或场景退出的幂等清理。 */
    void reset(AutoDockResetReason reason, long tick);
}
