package org.example.timeloop.mechanism.resonance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * 固定区域共振的不可变轮内状态快照。
 *
 * <p>快照不复制 {@code TickContext}，也不保存或推进独立时钟。{@code armedAtRoundTick}
 * 只是首次有效残影进入时从共享 {@code TickContext} 读取的刻标记，恢复后仍由下一次
 * 传入的共享上下文计算窗口是否超时。{@code insideEchoSourceRounds} 只记录当前观察到的
 * 历史残影来源，用于恢复 {@code outside -> inside} 边沿；它不是第二个残影队列。</p>
 */
public record ResonanceStateSnapshot(
        ResonanceState state,
        long armedAtRoundTick,
        int armedSourceRound,
        boolean currentPlayerInside,
        List<Integer> insideEchoSourceRounds
) {

    /** 没有正在等待的首个残影时使用的 tick 标记。 */
    public static final long NO_ARMED_TICK = -1L;
    /** 没有正在等待的首个残影时使用的来源标记。 */
    public static final int NO_SOURCE_ROUND = 0;

    public ResonanceStateSnapshot {
        state = Objects.requireNonNull(state, "resonance.snapshot.state");
        if (state == ResonanceState.ARMED) {
            if (armedAtRoundTick < 0) {
                throw new IllegalArgumentException("ARMED 快照必须包含非负 armedAtRoundTick");
            }
            if (armedSourceRound < 1) {
                throw new IllegalArgumentException("ARMED 快照必须包含有效 armedSourceRound");
            }
        } else if (armedAtRoundTick != NO_ARMED_TICK || armedSourceRound != NO_SOURCE_ROUND) {
            throw new IllegalArgumentException(
                    state + " 快照不能保留等待中的来源或 tick 标记");
        }

        Objects.requireNonNull(insideEchoSourceRounds, "resonance.snapshot.insideEchoSourceRounds");
        TreeSet<Integer> sortedRounds = new TreeSet<>();
        for (Integer sourceRound : insideEchoSourceRounds) {
            if (sourceRound == null || sourceRound < 1) {
                throw new IllegalArgumentException("快照中的残影 sourceRound 必须 >= 1");
            }
            if (!sortedRounds.add(sourceRound)) {
                throw new IllegalArgumentException("快照包含重复的残影 sourceRound: " + sourceRound);
            }
        }
        insideEchoSourceRounds = List.copyOf(new ArrayList<>(sortedRounds));
    }
}
