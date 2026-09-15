package org.example.timeloop.render;

import org.example.timeloop.core.ActorPhase;
import org.example.timeloop.core.AnimationState;
import org.example.timeloop.core.Direction;

import java.util.Objects;

/**
 * 单个残影在当前显示刻的只读精灵投影。
 *
 * <p>此类型刻意不进入 {@link RenderViews.Frame}：app 将 replay 的权威帧并行投影并按帧注入，
 * render 不从 {@code EchoTrail}、来源轮次或本地时间推算位置、姿态或相位。</p>
 */
public record EchoActor(int sourceRound,
                        double x,
                        double y,
                        Direction direction,
                        AnimationState animation,
                        ActorPhase actorPhase) {

    public EchoActor {
        if (sourceRound < 1) {
            throw new IllegalArgumentException("sourceRound 必须 >= 1");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("EchoActor 世界坐标必须为有限数");
        }
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(animation, "animation");
        Objects.requireNonNull(actorPhase, "actorPhase");
    }
}
