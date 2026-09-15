package org.example.timeloop.replay;

/**
 * 关卡会话终局的只读结算结果（TASK-DEV2-L02-RESULT-PROJECTION）。
 *
 * <p>通关提醒界面所需的数据，从时间与状态系统<b>只读投影</b>出来：</p>
 * <ul>
 *   <li>{@code levelName}：关卡名（结算界面显示）；由装配层在构造 {@link RecordingSession}
 *       时注入（来源：{@code LevelFlow.LevelId.title()}）。装配层未接线时可为 {@code null}。</li>
 *   <li>{@code cleared}：是否通关（{@code true} 通关 / {@code false} 失败）。</li>
 *   <li>{@code clearedRound}：达成/失败所在轮次（通关 = 达成轮；失败 = 第 {@code maxRounds} 轮）。</li>
 *   <li>{@code maxRounds}：本关总轮数（界面显示「第 N / 共 M 轮」）。</li>
 *   <li>{@code usedTicks}：从第 1 轮起累计到终局刻的用时（刻），显示时 {@code /60} 得秒。
 *       与 HUD 的唯一共享读秒同源，不引入第二套计时器。</li>
 * </ul>
 *
 * <p>本类型是<b>不可变数据载体</b>，不持有时钟、缓冲或任何可变状态；不含
 * {@link org.example.timeloop.core.GamePhase}（阶段是装配/时钟的权威状态，不复制进结算 DTO，
 * 避免两个真相源——界面用装配的 {@code phase()} 自行区分 {@code RESULT} / {@code FAILED} 文案）。</p>
 *
 * @param levelName    关卡名（装配未接线时可为 {@code null}）
 * @param cleared      是否通关
 * @param clearedRound 达成/失败所在轮次（≥1）
 * @param maxRounds    总轮数（≥1）
 * @param usedTicks    累计用时（刻，≥0）
 */
public record LevelResult(
        String levelName,
        boolean cleared,
        int clearedRound,
        int maxRounds,
        long usedTicks
) {

    public LevelResult {
        if (clearedRound < 1) {
            throw new IllegalArgumentException("clearedRound 必须 >= 1，实际 " + clearedRound);
        }
        if (maxRounds < 1) {
            throw new IllegalArgumentException("maxRounds 必须 >= 1，实际 " + maxRounds);
        }
        if (clearedRound > maxRounds) {
            throw new IllegalArgumentException(
                    "clearedRound 不能超过 maxRounds：clearedRound=" + clearedRound
                            + "，maxRounds=" + maxRounds);
        }
        if (usedTicks < 0) {
            throw new IllegalArgumentException("usedTicks 必须 >= 0，实际 " + usedTicks);
        }
    }

    /** 通关后界面上显示秒数（向下取整），仅供展示，不参与玩法判定。 */
    public long usedSeconds() {
        return usedTicks / 60L;
    }
}
