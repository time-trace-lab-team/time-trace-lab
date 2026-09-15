package org.example.timeloop.mechanism.resonance;

import org.example.timeloop.replay.EchoState;
import org.example.timeloop.replay.TickContext;

import java.util.Collection;
import java.util.Objects;
import java.util.TreeSet;

/**
 * 固定区域共振的纯 Java 轮内状态机。
 *
 * <p>调用方在每个共享逻辑 tick 收集“位于固定区域内”的当前玩家和活跃残影，并调用
 * {@link #observe(TickContext, boolean, Collection)} 一次。本类不做几何相交、移动、回放、
 * 中继、开门、核心或通关；开发 1 提供区域相交结果，开发 2 的 {@code EchoQueue} 负责只提供
 * 活跃残影。本类只读取开发 2 已批准的 {@link TickContext} 与 {@link EchoState}。</p>
 *
 * <p>窗口以第一个有效残影进入时的 {@link TickContext#roundTick()} 为基准。第二个不同
 * {@code sourceRound} 的历史残影在 {@code elapsed <= windowTicks} 时锁存，因此首刻和
 * 末刻都有效，超时后一刻先复位再处理该 tick 的新进入。状态机从不推进自己的 tick；
 * {@code armedAtRoundTick} 只是共享时钟的一个历史标记。</p>
 */
public final class ResonanceStateMachine implements ResonanceSnapshotPort {

    private final long windowTicks;
    private final TreeSet<Integer> insideEchoSourceRounds = new TreeSet<>();

    private ResonanceState state = ResonanceState.DORMANT;
    private long armedAtRoundTick = ResonanceStateSnapshot.NO_ARMED_TICK;
    private int armedSourceRound = ResonanceStateSnapshot.NO_SOURCE_ROUND;
    private boolean currentPlayerInside;

    /**
     * @param windowTicks 接受两个不同历史残影进入的最大 tick 差，必须大于零
     */
    public ResonanceStateMachine(long windowTicks) {
        if (windowTicks < 1) {
            throw new IllegalArgumentException("resonanceWindowTicks 必须 >= 1，实际 " + windowTicks);
        }
        this.windowTicks = windowTicks;
    }

    /** 共振窗口的冻结 tick 数。 */
    public long windowTicks() {
        return windowTicks;
    }

    /** 当前纯玩法状态。 */
    public synchronized ResonanceState state() {
        return state;
    }

    /** 是否已在本轮锁存。锁存只表示能源网络可继续处理，不表示门或出口已打开。 */
    public synchronized boolean isLatched() {
        return state == ResonanceState.LATCHED;
    }

    /**
     * 处理一个共享逻辑刻的固定区域存在结果。
     *
     * <p>{@code echoesInside} 只能包含调用方从 {@code EchoQueue.activeEchoes(...)} 取得的
     * 活跃历史残影。每个来源在本次输入中只能出现一次，且必须早于当前轮；当前玩家使用
     * 独立的 {@code currentPlayerInside} 参数，永远不能充当历史来源。</p>
     *
     * <p>本类保存上一次的“区域内”集合，从而自行把持续停留折叠成一次进入边沿。残影离开
     * 后再次进入会形成新边沿，但在同一个 ARMED 窗口中，和首个残影相同的来源既不会刷新
     * 窗口，也不能锁存。</p>
     *
     * @param context             开发 2 提供的权威共享 tick/轮次上下文
     * @param currentPlayerInside 当前玩家本 tick 是否在固定区域内；只产生预览结果
     * @param echoesInside        本 tick 在固定区域内的活跃历史残影
     * @return 本 tick 的只读状态变化结果
     */
    public synchronized ResonanceTickResult observe(TickContext context,
                                                     boolean currentPlayerInside,
                                                     Collection<EchoState> echoesInside) {
        Objects.requireNonNull(context, "resonance.context");
        TreeSet<Integer> nextInsideEchoSourceRounds = validateInsideEchoes(context, echoesInside);

        boolean currentPlayerPreviewed = currentPlayerInside && !this.currentPlayerInside;
        boolean timedOut = expireBeforeEntries(context.roundTick());

        TreeSet<Integer> enteringSourceRounds = new TreeSet<>(nextInsideEchoSourceRounds);
        enteringSourceRounds.removeAll(insideEchoSourceRounds);

        boolean armedThisTick = false;
        boolean latchedThisTick = false;
        for (int sourceRound : enteringSourceRounds) {
            if (state == ResonanceState.DORMANT) {
                arm(context.roundTick(), sourceRound);
                armedThisTick = true;
            } else if (state == ResonanceState.ARMED && sourceRound != armedSourceRound) {
                latch();
                latchedThisTick = true;
            }
        }

        this.currentPlayerInside = currentPlayerInside;
        insideEchoSourceRounds.clear();
        insideEchoSourceRounds.addAll(nextInsideEchoSourceRounds);
        return new ResonanceTickResult(
                state, currentPlayerPreviewed, armedThisTick, latchedThisTick, timedOut);
    }

    /**
     * 轮次边界、整局重开或场景退出时清空所有轮内状态与边沿记忆。
     *
     * <p>三个原因**都**从 {@link ResonanceState#DORMANT} 重新开始（本方法不按原因分支）；
     * 语义由调用方区分：{@code ROUND_END} 用于普通轮末，{@code FULL_RESTART} 用于整局重开，
     * {@code SCENE_EXIT} 用于退出关卡场景。轮次推进仍完全由开发 2 的共享时钟负责，
     * 本类不保存轮次计数。</p>
     */
    public synchronized void reset(ResonanceResetReason reason) {
        Objects.requireNonNull(reason, "resonance.resetReason");
        clearAllState();
    }

    @Override
    public synchronized ResonanceStateSnapshot createSnapshot() {
        return new ResonanceStateSnapshot(
                state,
                armedAtRoundTick,
                armedSourceRound,
                currentPlayerInside,
                java.util.List.copyOf(insideEchoSourceRounds)
        );
    }

    /**
     * 恢复一个同一固定区域、同一轮内语义下创建的快照。
     *
     * <p>调用方必须在后续同一轮的共享 {@link TickContext} 上继续观察，或先执行
     * {@link #reset(ResonanceResetReason)} 再进入下一轮；若缺少边界复位而让共享 tick
     * 回退，本类会明确失败，而不会悄悄保留上一轮的锁存。</p>
     */
    @Override
    public synchronized void restore(ResonanceStateSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "resonance.snapshot");
        TreeSet<Integer> restoredInsideEchoSourceRounds = new TreeSet<>();
        for (Integer sourceRound : snapshot.insideEchoSourceRounds()) {
            if (sourceRound == null || sourceRound < 1 || !restoredInsideEchoSourceRounds.add(sourceRound)) {
                throw new IllegalArgumentException("非法的 resonance 快照区域内来源集合");
            }
        }

        state = snapshot.state();
        armedAtRoundTick = snapshot.armedAtRoundTick();
        armedSourceRound = snapshot.armedSourceRound();
        currentPlayerInside = snapshot.currentPlayerInside();
        insideEchoSourceRounds.clear();
        insideEchoSourceRounds.addAll(restoredInsideEchoSourceRounds);
    }

    private TreeSet<Integer> validateInsideEchoes(TickContext context,
                                                   Collection<EchoState> echoesInside) {
        Objects.requireNonNull(echoesInside, "resonance.echoesInside");
        TreeSet<Integer> sourceRounds = new TreeSet<>();
        for (EchoState echo : echoesInside) {
            Objects.requireNonNull(echo, "resonance.echoesInside 中不能包含 null");
            int sourceRound = echo.sourceRound();
            if (sourceRound >= context.currentRound()) {
                throw new IllegalArgumentException(
                        "共振只接受早于当前轮的历史残影：sourceRound=" + sourceRound
                                + "，currentRound=" + context.currentRound());
            }
            if (echo.durationTicks() != context.durationTicks()) {
                throw new IllegalArgumentException(
                        "共振残影记录长度必须与共享 TickContext 一致：echo=" + echo.durationTicks()
                                + "，context=" + context.durationTicks());
            }
            if (!sourceRounds.add(sourceRound)) {
                throw new IllegalArgumentException("同一 tick 的共振输入包含重复 sourceRound: " + sourceRound);
            }
        }
        return sourceRounds;
    }

    private boolean expireBeforeEntries(long roundTick) {
        if (state != ResonanceState.ARMED) {
            return false;
        }
        if (roundTick < armedAtRoundTick) {
            throw new IllegalStateException(
                    "共享 roundTick 回退；轮次边界必须先调用 resonance.reset(ROUND_END)");
        }
        if (roundTick - armedAtRoundTick > windowTicks) {
            state = ResonanceState.DORMANT;
            clearArmingMarker();
            return true;
        }
        return false;
    }

    private void arm(long roundTick, int sourceRound) {
        state = ResonanceState.ARMED;
        armedAtRoundTick = roundTick;
        armedSourceRound = sourceRound;
    }

    private void latch() {
        state = ResonanceState.LATCHED;
        clearArmingMarker();
    }

    private void clearAllState() {
        state = ResonanceState.DORMANT;
        clearArmingMarker();
        currentPlayerInside = false;
        insideEchoSourceRounds.clear();
    }

    private void clearArmingMarker() {
        armedAtRoundTick = ResonanceStateSnapshot.NO_ARMED_TICK;
        armedSourceRound = ResonanceStateSnapshot.NO_SOURCE_ROUND;
    }
}
