package org.example.timeloop.entity;

import org.example.timeloop.core.Direction;
import org.example.timeloop.replay.TimelineEvent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * C3 停放/输入策略在单个逻辑刻给出的决策。
 *
 * <p>策略不拥有位置：调用方按 {@link Status} 决定是否推进运动，并把 {@code CRUISE} 时的
 * {@link #departureDirection()} 作为本刻必须采用的离开方向。</p>
 *
 * @param status              本刻运动决策
 * @param departureDirection  仅“离开停驻”时非空：本刻必须采用的离开方向
 * @param events              本刻产生的稳定事件（已按 tick 归属）
 * @param interactBuffered    E 交互缓冲当前是否有效
 */
public record C3DockDecision(
        Status status,
        Optional<Direction> departureDirection,
        List<TimelineEvent> events,
        boolean interactBuffered
) {

    public enum Status {
        /** 驻留/离开中且尚未出界：调用方不得推进运动。 */
        FREEZE,
        /** 正常巡行或离开移动：调用方推进运动。 */
        CRUISE
    }

    public C3DockDecision {
        Objects.requireNonNull(status, "status");
        departureDirection = Objects.requireNonNull(departureDirection, "departureDirection");
        events = List.copyOf(Objects.requireNonNull(events, "events"));
    }

    public boolean isFreeze() {
        return status == Status.FREEZE;
    }
}
