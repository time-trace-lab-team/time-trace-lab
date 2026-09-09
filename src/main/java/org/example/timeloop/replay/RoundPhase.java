package org.example.timeloop.replay;

/**
 * 开发 2 自身的阶段表示（R1 过渡版，不依赖 {@code core/GamePhase}）。
 *
 * <p>完整阶段集与 README「游戏状态建议」一致，最终归属仍需项目经理裁决后与
 * {@code core/GamePhase} 合并；在此先把阶段“是否推进逻辑刻”的语义冻结。</p>
 *
 * <p>{@link #advances()} 仅 {@link #PLAYING} 为 {@code true}；
 * 其余阶段为冻结阶段，调用 {@link RoundClock#advance()} 返回
 * {@link AdvanceResult#NO_ADVANCE}（零推进）。</p>
 */
public enum RoundPhase {

    BOOT(false),
    MENU(false),
    LEVEL_SELECT(false),
    TUTORIAL(false),
    READY(false),
    PLAYING(true),
    PAUSED(false),
    RESETTING(false),
    RESULT(false),
    FAILED(false);

    private final boolean advances;

    RoundPhase(boolean advances) {
        this.advances = advances;
    }

    /** 该阶段是否推进 {@code roundTick}（仅 PLAYING 为 true）。 */
    public boolean advances() {
        return advances;
    }
}
