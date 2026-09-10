package org.example.timeloop.replay;

import java.util.Objects;
import org.example.timeloop.core.ActorPhase;
import org.example.timeloop.core.AnimationState;
import org.example.timeloop.core.Direction;
import org.example.timeloop.core.MovementState;

/**
 * 一个逻辑刻结束时当前玩家的规范状态（R2，开发 2 主责）。
 *
 * <p>本类型是开发 2 自己的不可变帧，<b>不持有开发 1 的可变玩家对象引用</b>。
 * 数据来自开发 1 的 tick 末 {@code PlayerKinematics}，由开发 2 复制成本帧后
 * 进入录制缓冲；因此开发 1 后续修改其对象不会影响已记录的帧。</p>
 *
 * <p>字段依据 README 第九节的 {@code PlayerFrame}，并按
 * {@code docs/decisions/R2-PlayerFrame契约裁决.md} 落地：</p>
 * <ul>
 *   <li>{@code tick} 取 {@code long}，与 {@link TickContext#roundTick()} 一致；
 *       在 {@link TimelineRecording} 中必须等于其列表索引。</li>
 *   <li>README 的 {@code boolean phaseDodging} 由 {@link #actorPhase} 表达，
 *       两者等价关系为 {@code phaseDodging == (actorPhase == ActorPhase.PHASED)}，
 *       通过 {@link #isPhaseDodging()} 派生；额外保留 {@code RECOVERING}
 *       冷却信息，避免回放时丢失"本刻能否再次相位下潜"。</li>
 *   <li>{@code interacting} 对应 {@code PlayerKinematics.interactionTriggered}，
 *       语义为<b>本刻是否发生交互触发（边沿）</b>，不是"按键持续按住"。</li>
 * </ul>
 *
 * <p>本类型不重新执行转向、碰撞、驻留、射线或机关逻辑，也不参与随机数。</p>
 *
 * @param tick                     本帧所属逻辑刻，必须等于其在记录中的索引，范围 [0, durationTicks)
 * @param x                        世界逻辑坐标 x（玩家碰撞体中心），不能由 Canvas 缩放回写
 * @param y                        世界逻辑坐标 y（玩家碰撞体中心）
 * @param direction                本刻结束时的朝向
 * @param interacting              本刻是否发生交互触发（边沿语义）
 * @param movementState            本刻结束时的移动状态
 * @param actorPhase               本刻结束时的相位能力状态
 * @param actorPhaseTicksRemaining 当前相位状态剩余 tick；{@code AVAILABLE} 时为 0
 * @param animationState           本刻结束时的姿态，仅供渲染，不参与玩法判定
 */
public record PlayerFrame(
        long tick,
        double x,
        double y,
        Direction direction,
        boolean interacting,
        MovementState movementState,
        ActorPhase actorPhase,
        int actorPhaseTicksRemaining,
        AnimationState animationState
) {

    public PlayerFrame {
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
    }

    /**
     * 本刻是否处于相位下潜中，即 README 第九节的 {@code phaseDodging}。
     *
     * @return 仅当 {@link ActorPhase#PHASED} 时为 {@code true}
     */
    public boolean isPhaseDodging() {
        return actorPhase == ActorPhase.PHASED;
    }
}
