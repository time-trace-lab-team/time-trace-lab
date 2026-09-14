package org.example.timeloop.replay;

import java.util.Objects;
import java.util.Optional;
import org.example.timeloop.core.GamePhase;

/**
 * 当前轮录制与轮末事务（R3，开发 2 主责）。
 *
 * <p>聚合 {@link RoundClock}（权威时钟）、{@link TimelineRecording}（本轮缓冲）与
 * {@link EchoQueue}（滑动窗口），实现开发 2 技术指南 §3.4/§3.5 的边界语义：</p>
 *
 * <ul>
 *   <li><b>普通轮到时</b>（{@link #completeNormalRound}）：冻结末刻 → 封装满长记录 →
 *       生成 {@code sourceRound = currentRound} 的新残影 → 按整轮寿命淘汰 →
 *       恢复轮内机关（注入回调）→ {@code RESETTING → READY}（轮次 +1、刻归零）→ 开新缓冲；</li>
 *   <li><b>最终轮到时</b>（{@link #failFinalRound}）：丢弃缓冲，不生成残影，进入 FAILED；</li>
 *   <li><b>目标达成</b>（{@link #completeGoal}）：丢弃未满缓冲，不生成残影，进入 RESULT；</li>
 *   <li><b>从第一轮重开</b>（{@link #restartFromFirstRound}）：丢弃缓冲、清空全部残影，
 *       恢复初始机关（注入回调）→ READY（第 1 轮）。</li>
 * </ul>
 *
 * <p><b>轮末停靠契约</b>：{@link #completeNormalRound} 与 {@link #restartFromFirstRound}
 * 结束后，时钟停在 {@link GamePhase#READY}、本轮新缓冲已开好；<b>由调用方负责
 * {@code clock.transition(PLAYING)}</b> 启动下一轮。本类只管理时钟与缓冲，
 * 不自行把阶段推进到 PLAYING。</p>
 *
 * <p>机关快照恢复端口尚未由开发 3 冻结，因此以注入的 {@link Runnable} 表达
 * （与 core 包 OrderedTickUpdatePort 的回调注入模式一致）；射线命中不调用任何边界方法，
 * 本类也没有生命值/死亡语义。轮内玩家位置、排队方向等由开发 1 各自重置，不在本类职责内。</p>
 */
public final class RecordingSession {

    private final RoundClock clock;
    private final EchoQueue echoQueue;
    private final String levelName;
    private TimelineRecording currentBuffer;
    private LevelResult result;

    /**
     * 兼容构造器：{@code levelName} 取 {@code null}（装配层尚未接线关卡名）。
     * 新代码请用三参构造器注入关卡名。
     *
     * @param clock     权威共享时钟
     * @param echoQueue 滑动窗口容器（其 L 为进关冻结值）
     */
    public RecordingSession(RoundClock clock, EchoQueue echoQueue) {
        this(clock, echoQueue, null);
    }

    /**
     * @param clock     权威共享时钟
     * @param echoQueue 滑动窗口容器（其 L 为进关冻结值）
     * @param levelName 关卡名（结算界面显示；来源 {@code LevelFlow.LevelId.title()}，可为 {@code null}）
     */
    public RecordingSession(RoundClock clock, EchoQueue echoQueue, String levelName) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.echoQueue = Objects.requireNonNull(echoQueue, "echoQueue");
        this.levelName = levelName;
    }

    /** 滑动窗口容器（只读持有，供查询活跃残影与寿命视图）。 */
    public EchoQueue echoQueue() {
        return echoQueue;
    }

    /** 当前轮缓冲（若已开始本轮）。 */
    public Optional<TimelineRecording> currentBuffer() {
        return Optional.ofNullable(currentBuffer);
    }

    /**
     * 终局结算结果（通关 {@link #completeGoal} / 失败 {@link #failFinalRound} 固化）。
     * 未进入终局、或已从第一轮重开（{@link #restartFromFirstRound} 清空）时为空。
     *
     * @return 只读结果，或空
     */
    public Optional<LevelResult> result() {
        return Optional.ofNullable(result);
    }

    /**
     * 进入新一轮（READY 之后、PLAYING 之前）调用：为本轮开一个新缓冲。
     * 缓冲来源轮次 = 时钟当前轮次，长度 = 冻结轮长。
     *
     * @throws IllegalStateException 已有未丢弃的缓冲（上一轮边界未正确收尾）
     */
    public void beginRound() {
        if (currentBuffer != null) {
            throw new IllegalStateException(
                    "上一轮缓冲未收尾：sourceRound=" + currentBuffer.sourceRound()
                            + "，应先调用轮末/重开边界方法");
        }
        currentBuffer = new TimelineRecording(clock.durationTicks(), clock.currentRound());
    }

    /**
     * PLAYING 中每完成一次规范逻辑更新记录一帧。
     * 帧校验（tick 必须等于索引）由 {@link TimelineRecording#record} 保证。
     *
     * @throws IllegalStateException 非 PLAYING 阶段或无当前缓冲
     */
    public void recordFrame(PlayerFrame frame) {
        if (!clock.isPlaying()) {
            throw new IllegalStateException(
                    "只有 PLAYING 阶段才录制：当前阶段 " + clock.phase()
                            + "（TUTORIAL/READY/PAUSED 等不推进也不写帧）");
        }
        if (currentBuffer == null) {
            throw new IllegalStateException("尚未 beginRound()，无当前缓冲");
        }
        currentBuffer.record(frame);
    }

    /**
     * 普通轮末事务（非最终轮读秒归零时调用，整个操作在同一逻辑边界完成）。
     *
     * <p>结束后时钟停在 {@link GamePhase#READY}（轮次 +1、刻归零）、新缓冲已开好；
     * 由调用方负责 {@code clock.transition(PLAYING)} 启动下一轮。</p>
     *
     * @param mechanismRestorer 恢复轮内机关状态的注入端口（开发 3 快照恢复；未接入前可传空操作）
     * @return 本轮生成的新残影
     * @throws IllegalStateException 缓冲未满长、处于最终轮，或阶段非法
     */
    public EchoState completeNormalRound(Runnable mechanismRestorer) {
        Objects.requireNonNull(mechanismRestorer, "mechanismRestorer");
        if (currentBuffer == null) {
            throw new IllegalStateException("无当前缓冲，不能执行轮末事务");
        }
        if (clock.currentRound() >= clock.maxRounds()) {
            throw new IllegalStateException(
                    "最终轮读秒归零应走 failFinalRound()，不得生成无法使用的残影");
        }
        // 1. 校验并封装恰好 durationTicks 帧（未满会抛异常，等同于"必须满长"）。
        currentBuffer.seal();
        // 2. 生成 sourceRound = currentRound 的新残影。
        EchoState echo = EchoState.of(currentBuffer);
        // 3. 更新滑动窗口并按整轮寿命在边界淘汰。
        int nextRound = clock.currentRound() + 1;
        echoQueue.addOnRoundEnd(echo, nextRound);
        // 4. 恢复轮内机关状态（注入端口；失败应向集成层暴露，属 R5 事务加固范围）。
        mechanismRestorer.run();
        // 5. RESETTING -> READY：currentRound + 1、roundTick 归零。
        clock.transition(GamePhase.RESETTING);
        clock.transition(GamePhase.READY);
        // 6. 本轮缓冲消费完毕，为下一轮开新缓冲。
        currentBuffer = null;
        beginRound();
        return echo;
    }

    /** 最终轮读秒归零未通关：固化失败结果、丢弃缓冲、不生成残影、进入 FAILED。 */
    public void failFinalRound() {
        if (clock.currentRound() != clock.maxRounds()) {
            throw new IllegalStateException(
                    "仅最终轮可进入 FAILED：当前第 " + clock.currentRound() + " 轮");
        }
        this.result = buildResult(false);
        currentBuffer = null;
        clock.transition(GamePhase.FAILED);
    }

    /** 目标达成：固化通关结果、丢弃未满缓冲、不生成残影、进入 RESULT。 */
    public void completeGoal() {
        this.result = buildResult(true);
        currentBuffer = null;
        clock.transition(GamePhase.RESULT);
    }

    /**
     * 在终局边界刻固化只读结果。用时口径与 HUD 的唯一共享读秒同源：
     * {@code usedTicks = (clearedRound - 1) × durationTicks + roundTickAtEnd}，
     * 其中 {@code roundTickAtEnd} 取固化那一刻的 {@code clock.roundTick()}（{@code RESULT} /
     * {@code FAILED} 阶段均保留 roundTick，故在 {@code transition} 前取值即达成/失败刻）。
     */
    private LevelResult buildResult(boolean cleared) {
        long usedTicks = (clock.currentRound() - 1L) * clock.durationTicks() + clock.roundTick();
        return new LevelResult(
                levelName, cleared, clock.currentRound(), clock.maxRounds(), usedTicks);
    }

    /**
     * 从暂停或失败页面"从第一轮重开"：放弃整个关卡会话 ——
     * 丢弃缓冲、清空全部残影、恢复初始机关，回到第 1 轮 READY。
     * 不产生残影，也不经过普通轮末事务。
     *
     * @param initialSnapshotRestorer 恢复关卡初始快照的注入端口（开发 3）
     */
    public void restartFromFirstRound(Runnable initialSnapshotRestorer) {
        Objects.requireNonNull(initialSnapshotRestorer, "initialSnapshotRestorer");
        currentBuffer = null;
        echoQueue.clear();
        this.result = null;
        initialSnapshotRestorer.run();
        clock.transition(GamePhase.READY);
        beginRound();
    }

    /**
     * PLAYING 中每发生一次状态边沿，记录一条离散事件。
     *
     * <p>事件只在边沿写入：进入 autoDock、离开 autoDock、机关状态变化等。
     * 持续 DOCKED 不重复发进入事件。事件按 {@link TimelineEvent#STABLE_ORDER}
     * 在封装时排序（{@link TimelineRecording#seal()}）。</p>
     *
     * @param event 本刻发生的离散事件
     * @throws IllegalStateException 非 PLAYING 阶段或无当前缓冲
     */
    public void recordEvent(TimelineEvent event) {
        Objects.requireNonNull(event, "event");
        if (!clock.isPlaying()) {
            throw new IllegalStateException(
                    "只有 PLAYING 阶段才录制事件：当前阶段 " + clock.phase()
                            + "（TUTORIAL/READY/PAUSED 等不写事件）");
        }
        if (currentBuffer == null) {
            throw new IllegalStateException("尚未 beginRound()，无当前缓冲");
        }
        currentBuffer.recordEvent(event);
    }
}
