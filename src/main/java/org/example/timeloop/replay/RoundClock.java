package org.example.timeloop.replay;

import org.example.timeloop.core.GamePhase;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 开发 2 的唯一 {@code roundTick} 权威值与合法阶段转移。
 *
 * <p>本类不直接产生真实时间：纳秒累加、帧间隔、补帧上限是开发 1 的
 * {@code FixedStepClock} 职责。本类只接收“推进一个逻辑刻”的显式请求，
 * 管理在本轮内的 {@code roundTick} 与阶段转移。</p>
 *
 * <p>阶段约束：</p>
 * <ul>
 *   <li>{@link GamePhase#TUTORIAL} / {@link GamePhase#READY} / {@link GamePhase#PAUSED}：
 *       不推进 {@code roundTick}（{@link AdvanceResult#NO_ADVANCE}）。</li>
 *   <li>{@link GamePhase#PLAYING}：{@code roundTick} 严格 {@code 0 .. durationTicks - 1}，
 *       不存在索引 {@code durationTicks} 的帧；到达 {@code D-1} 后再推进返回
 *       {@link AdvanceResult#ROUND_END}，越界一律上报而不会静默跳到下一轮。</li>
 *   <li>{@link GamePhase#RESETTING}：冻结玩法更新。</li>
 *   <li>{@link GamePhase#RESULT}：目标达成后立即结束（未满缓冲由调用方丢弃）。</li>
 *   <li>{@link GamePhase#FAILED}：仅在第 {@code maxRounds} 轮读秒归零仍未通关时进入。</li>
 * </ul>
 *
 * <p>非法阶段转移会抛出 {@link IllegalStateException}。轮次切换事务中的
 * 记录封装、残影队列、快照恢复属于 R3/R5，不在本类实现；本类只负责时钟状态自身的
 * 阶段、{@code roundTick} 与 {@code currentRound} 推进。</p>
 */
public final class RoundClock {

    private static final Map<GamePhase, Set<GamePhase>> LEGAL = buildLegal();

    /** 每轮固定时长（tick），进入关卡时冻结，本局不可改。 */
    private final int durationTicks;
    /** 本关最大轮数，进入关卡时冻结，本局不可改。 */
    private final int maxRounds;

    private GamePhase phase;
    private long roundTick;
    private int currentRound;

    /**
     * @param durationTicks 每轮固定时长（tick），范围 [1, ∞)
     * @param maxRounds     本关最大轮数，范围 [1, ∞)
     */
    public RoundClock(int durationTicks, int maxRounds) {
        if (durationTicks < 1) {
            throw new IllegalArgumentException("durationTicks 必须 >= 1，实际 " + durationTicks);
        }
        if (maxRounds < 1) {
            throw new IllegalArgumentException("maxRounds 必须 >= 1，实际 " + maxRounds);
        }
        this.durationTicks = durationTicks;
        this.maxRounds = maxRounds;
        // 初始阶段：README 状态机自 BOOT 起；开发 2 时钟以 READY 作为回合初值。
        this.phase = GamePhase.BOOT;
        this.roundTick = 0;
        this.currentRound = 1;
    }

    /**
     * 请求一次合法的阶段转移；非法转移抛出 {@link IllegalStateException}。
     *
     * @param to 目标阶段
     */
    public void transition(GamePhase to) {
        Objects.requireNonNull(to, "to");
        if (!LEGAL.getOrDefault(phase, Set.of()).contains(to)) {
            throw new IllegalStateException("非法阶段转移: " + phase + " -> " + to);
        }
        apply(to);
        phase = to;
    }

    private void apply(GamePhase to) {
        switch (to) {
            case READY -> {
                if (phase == GamePhase.RESETTING) {
                    // RESETTING 只用于非最终轮，续轮并推进 currentRound。
                    if (currentRound >= maxRounds) {
                        throw new IllegalStateException("最后一轮读秒归零应进入 FAILED，而非 READY 续轮");
                    }
                    currentRound++;
                } else {
                    // 首次进入 / 从 PAUSED、FAILED 整局重开：回到第 1 轮。
                    currentRound = 1;
                }
                roundTick = 0;
            }
            case PLAYING -> {
                if (phase == GamePhase.READY) {
                    // 新一轮开始：roundTick 从 0 起。
                    roundTick = 0;
                }
                // 从 PAUSED 恢复：保留 roundTick（续玩）。
            }
            case RESETTING -> {
                if (currentRound >= maxRounds) {
                    throw new IllegalStateException("最后一轮读秒归零应进入 FAILED，而非 RESETTING");
                }
                // 保留 roundTick（D-1 末刻），先冻结再进行事务。
            }
            case FAILED -> {
                if (currentRound != maxRounds) {
                    throw new IllegalStateException("仅第 maxRounds 轮读秒归零未通关才进入 FAILED");
                }
                // 保留 roundTick。
            }
            case BOOT, MENU, LEVEL_SELECT, TUTORIAL -> {
                // 重新进入应用/教学阶段：重置为一个全新会话。
                roundTick = 0;
                currentRound = 1;
            }
            case PAUSED, RESULT -> {
                // 冻结 / 结束：保留 roundTick。
            }
        }
    }

    /**
     * 推进一个逻辑刻。仅在 {@link GamePhase#PLAYING} 真正推进；
     * 冻结阶段返回 {@link AdvanceResult#NO_ADVANCE}；
     * 到达本轮最后一帧（{@code D-1}）后再推进返回 {@link AdvanceResult#ROUND_END}，
     * 不会推进到索引 {@code D}，也不会静默跳到下一轮。
     */
    public AdvanceResult advance() {
        if (!phase.advancesLogic()) {
            return AdvanceResult.NO_ADVANCE;
        }
        if (roundTick >= durationTicks - 1L) {
            return AdvanceResult.ROUND_END;
        }
        roundTick++;
        return AdvanceResult.ADVANCED;
    }

    public GamePhase phase() {
        return phase;
    }

    public long roundTick() {
        return roundTick;
    }

    public int currentRound() {
        return currentRound;
    }

    public int durationTicks() {
        return durationTicks;
    }

    public int maxRounds() {
        return maxRounds;
    }

    public boolean isPlaying() {
        return phase == GamePhase.PLAYING;
    }

    /**
     * 供所有 actor 只读的统一时间上下文（不可变快照，来自 R0 的 {@link TickContext}）。
     */
    public TickContext toContext() {
        return new TickContext(roundTick, durationTicks, currentRound, maxRounds);
    }

    private static Map<GamePhase, Set<GamePhase>> buildLegal() {
        Map<GamePhase, Set<GamePhase>> m = new EnumMap<>(GamePhase.class);
        m.put(GamePhase.BOOT, EnumSet.of(GamePhase.MENU));
        m.put(GamePhase.MENU, EnumSet.of(GamePhase.LEVEL_SELECT));
        m.put(GamePhase.LEVEL_SELECT, EnumSet.of(GamePhase.TUTORIAL, GamePhase.READY));
        m.put(GamePhase.TUTORIAL, EnumSet.of(GamePhase.READY));
        m.put(GamePhase.READY,
                EnumSet.of(GamePhase.PLAYING, GamePhase.PAUSED, GamePhase.TUTORIAL, GamePhase.LEVEL_SELECT));
        m.put(GamePhase.PLAYING,
                EnumSet.of(GamePhase.PAUSED, GamePhase.RESULT, GamePhase.RESETTING, GamePhase.FAILED));
        m.put(GamePhase.PAUSED, EnumSet.of(GamePhase.PLAYING, GamePhase.READY, GamePhase.LEVEL_SELECT));
        m.put(GamePhase.RESETTING, EnumSet.of(GamePhase.READY));
        m.put(GamePhase.RESULT, EnumSet.of(GamePhase.MENU, GamePhase.LEVEL_SELECT));
        m.put(GamePhase.FAILED, EnumSet.of(GamePhase.READY, GamePhase.LEVEL_SELECT));
        return m;
    }
}
