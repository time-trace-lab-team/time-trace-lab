package org.example.timeloop.snapshot;

import org.example.timeloop.core.GamePhase;
import org.example.timeloop.replay.EchoFrameView;
import org.example.timeloop.replay.PlayerFrame;

import java.util.List;
import java.util.Objects;

/**
 * 第一关 MVP 最小只读渲染视图（W4，开发 2 主责）。
 *
 * <p>聚合渲染层需要的最小字段：当前玩家帧、活跃残影帧视图、阶段、轮次、当前刻。
 * 所有字段不可变，列表为不可修改副本；开发 1 消费后不得反向修改时间系统
 * （渲染零回写）。</p>
 *
 * <p>完整 {@code RenderSnapshot}（含射线/共振/中继/核心）属 R6（第 5 天），
 * 本类只交付第一关 MVP 所需最小字段。</p>
 *
 * @param phase        当前游戏阶段
 * @param roundTick    当前逻辑刻
 * @param currentRound 当前轮次
 * @param maxRounds    最大轮次
 * @param currentPlayer 当前玩家帧；尚无缓冲时为 {@code null}
 * @param activeEchoes 活跃残影帧视图（按来源轮次升序）
 */
public record MvpRenderSnapshot(
        GamePhase phase,
        long roundTick,
        int currentRound,
        int maxRounds,
        PlayerFrame currentPlayer,
        List<EchoFrameView> activeEchoes
) {

    public MvpRenderSnapshot {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(activeEchoes, "activeEchoes");
        if (roundTick < 0) {
            throw new IllegalArgumentException("roundTick 必须 >= 0，实际 " + roundTick);
        }
        if (currentRound < 1) {
            throw new IllegalArgumentException("currentRound 必须 >= 1，实际 " + currentRound);
        }
        if (maxRounds < 1) {
            throw new IllegalArgumentException("maxRounds 必须 >= 1，实际 " + maxRounds);
        }
        if (currentRound > maxRounds) {
            throw new IllegalArgumentException(
                    "currentRound 不能超过 maxRounds：current=" + currentRound
                            + ", max=" + maxRounds);
        }
        activeEchoes = List.copyOf(activeEchoes);
    }
}