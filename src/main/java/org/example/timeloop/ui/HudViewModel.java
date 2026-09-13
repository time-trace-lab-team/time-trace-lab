package org.example.timeloop.ui;

import org.example.timeloop.core.GamePhase;
import org.example.timeloop.replay.TickContext;

import java.util.Objects;

/**
 * 共享 HUD 的不可变文字投影。
 *
 * <p>它只从开发 2 提供的 {@link TickContext} 推导显示值，不持有计时器、时钟或可变
 * 游戏状态。固定步长为 60 tick/s；倒计时使用向上取整，使最后一个有效 gameplay
 * tick 仍显示为 1 秒而不是提前显示 0。</p>
 */
public record HudViewModel(int remainingSeconds,
                           int currentRound,
                           int maxRounds) {

    public static final long TICKS_PER_SECOND = 60L;

    public HudViewModel {
        if (remainingSeconds < 1) {
            throw new IllegalArgumentException("remainingSeconds 必须 >= 1");
        }
        if (maxRounds < 1) {
            throw new IllegalArgumentException("maxRounds 必须 >= 1");
        }
        if (currentRound < 1 || currentRound > maxRounds) {
            throw new IllegalArgumentException(
                    "currentRound 必须在 [1, " + maxRounds + "]");
        }
    }

    /** 从唯一共享时间上下文生成 HUD 投影。 */
    public static HudViewModel from(TickContext context) {
        Objects.requireNonNull(context, "context");
        long remainingTicks = context.durationTicks() - context.roundTick();
        int remainingSeconds = Math.toIntExact(
                (remainingTicks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND);
        return new HudViewModel(
                remainingSeconds,
                context.currentRound(),
                context.maxRounds());
    }

    public String countdownText() {
        return "剩余 " + remainingSeconds + " 秒";
    }

    public String roundText() {
        return "第 " + currentRound + " / " + maxRounds + " 轮";
    }

    /**
     * Returns the terminal phase feedback shown beside the shared timer.
     *
     * <p>终局阶段（通关 / 失败）必须同时给出<b>下一步怎么办</b>：这两个阶段不再接受 gameplay 输入，
     * 若只显示结果文字，玩家会停在"角色不能动、也没有下一步"的死画面里。重开键见
     * {@code TimeTraceLabApplication#isRestartKey}。</p>
     */
    public static String phaseText(GamePhase phase) {
        Objects.requireNonNull(phase, "phase");
        return switch (phase) {
            case RESULT -> "通关完成 · 按 R 再玩一次";
            case FAILED -> "挑战失败 · 按 R 重开";
            default -> "";
        };
    }
}
