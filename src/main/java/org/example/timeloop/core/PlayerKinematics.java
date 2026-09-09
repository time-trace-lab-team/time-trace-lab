package org.example.timeloop.core;

import java.util.Objects;

/**
 * 玩家在一个完整逻辑 tick 结束时的不可变规范状态。
 *
 * <p>坐标使用以左上为原点、向右为 x 正向、向下为 y 正向的世界逻辑坐标；
 * {@code x/y} 是玩家逻辑碰撞体中心，不能由 Canvas 缩放或高 DPI 回写。</p>
 */
public record PlayerKinematics(
        long tick,
        double x,
        double y,
        Direction direction,
        MovementState movementState,
        ActorPhase actorPhase,
        int actorPhaseTicksRemaining,
        boolean interactionTriggered,
        AnimationState animationState
) {

    /**
     * 校验冻结字段之间可由本模块独立判定的不变量。
     */
    public PlayerKinematics {
        if (tick < 0) {
            throw new IllegalArgumentException("tick 必须 >= 0，实际 " + tick);
        }
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(movementState, "movementState");
        Objects.requireNonNull(actorPhase, "actorPhase");
        Objects.requireNonNull(animationState, "animationState");
        if (actorPhaseTicksRemaining < 0) {
            throw new IllegalArgumentException(
                    "actorPhaseTicksRemaining 必须 >= 0，实际 " + actorPhaseTicksRemaining);
        }
        if (actorPhase == ActorPhase.AVAILABLE && actorPhaseTicksRemaining != 0) {
            throw new IllegalArgumentException("AVAILABLE 时 actorPhaseTicksRemaining 必须为 0");
        }

        AnimationState expectedAnimationState = interactionTriggered
                ? AnimationState.INTERACTING
                : movementState == MovementState.DOCKED
                        ? AnimationState.DOCKED
                        : AnimationState.MOVING;
        if (animationState != expectedAnimationState) {
            throw new IllegalArgumentException(
                    "animationState 必须由 interactionTriggered 和 movementState 派生，期望 "
                            + expectedAnimationState + "，实际 " + animationState);
        }
    }
}
