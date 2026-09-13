package org.example.timeloop.replay;

/**
 * 开发 2 主责的共享时间上下文（候选契约 v1，待团队确认后冻结）。
 *
 * <p>已批准语义：</p>
 * <ul>
 *   <li>逻辑刻 tick，固定 60 Hz；时间单位一律为整数 tick，不混用秒。</li>
 *   <li>{@code roundTick} 的有效帧索引为 {@code 0 .. durationTicks - 1}；
 *       一条成功封装的记录恰好包含 {@code durationTicks} 帧。</li>
 *   <li>{@code currentRound} / {@code maxRounds} 为整数轮，进入关卡时冻结，本局中途不得改变。</li>
 *   <li>不可变：record 字段均为 final，无 setter、无可变集合写入口。</li>
 * </ul>
 *
 * <p>纸面演算样例（{@code durationTicks = 5}）：</p>
 * <pre>
 * 帧索引：0, 1, 2, 3, 4        恰好 5 帧，索引 5 不存在
 * 最后一帧是索引 4（D-1）；其完成后进入 RESETTING 轮次切换，
 * 切换结果为：currentRound + 1、roundTick = 0。
 * </pre>
 *
 * <p>事件顺序规则（R2 落地具体类型）：同 tick 事件先批量收集，
 * 再按“类别优先级 + 稳定 ID”排序；持续状态不逐 tick 重复发“进入”事件。</p>
 *
 * <p>状态所有权三类（R5 落地快照）：轮内状态每轮恢复初始快照；
 * 关卡会话状态保留并推进；存档状态不因轮次切换或重开而清空。</p>
 *
 * @param roundTick     当前轮内的逻辑刻，范围 [0, durationTicks)
 * @param durationTicks 每轮固定时长（tick），范围 [1, ∞)
 * @param currentRound  当前轮次，范围 [1, maxRounds]
 * @param maxRounds     本关最大轮数，范围 [1, ∞)
 */
public record TickContext(
        long roundTick,
        int durationTicks,
        int currentRound,
        int maxRounds
) {

    public TickContext {
        if (durationTicks < 1) {
            throw new IllegalArgumentException("durationTicks 必须 >= 1，实际 " + durationTicks);
        }
        if (maxRounds < 1) {
            throw new IllegalArgumentException("maxRounds 必须 >= 1，实际 " + maxRounds);
        }
        if (currentRound < 1 || currentRound > maxRounds) {
            throw new IllegalArgumentException(
                    "currentRound 必须在 [1, " + maxRounds + "]，实际 " + currentRound);
        }
        if (roundTick < 0 || roundTick >= durationTicks) {
            throw new IllegalArgumentException(
                    "roundTick 必须在 [0, " + (durationTicks - 1) + "]，实际 " + roundTick);
        }
    }

    /**
     * 是否为本轮最后一帧（D-1）。
     * 纯派生访问器，不推进任何逻辑。
     */
    public boolean isLastTick() {
        return roundTick == durationTicks - 1L;
    }
}
