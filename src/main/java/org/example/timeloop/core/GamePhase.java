package org.example.timeloop.core;

/**
 * 全项目唯一的正式阶段枚举。
 *
 * <p>共十个阶段，与项目状态流程一致。{@link #advancesLogic()} 冻结“是否推进
 * 逻辑刻”的语义：仅 {@link #PLAYING} 返回 {@code true}；其他阶段一律冻结，
 * 不推进 {@code roundTick}、不写记录帧。</p>
 *
 * <p>新增阶段时必须在枚举常量中显式声明该值，避免默认推进逻辑刻。</p>
 */
public enum GamePhase {

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

    private final boolean advancesLogic;

    GamePhase(boolean advancesLogic) {
        this.advancesLogic = advancesLogic;
    }

    /**
     * 当前阶段是否推进一个逻辑刻。
     *
     * @return 仅 {@link #PLAYING} 为 {@code true}
     */
    public boolean advancesLogic() {
        return advancesLogic;
    }
}
