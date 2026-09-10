package org.example.timeloop.replay;

import java.util.Objects;

/**
 * 残影整轮寿命的权威计算（R4 基础，开发 2 主责）。
 *
 * <p>本类是 README「统一生命周期公式」的唯一实现。公式（来源轮次 {@code s}、
 * 当前轮次 {@code r}、整轮寿命 {@code L}、轮长 {@code D}）：</p>
 *
 * <pre>
 * age             = r - s
 * active          = 1 &lt;= age &lt;= L
 * remainingRounds = L - age + 1
 * lifeProgress    = clamp(((age - 1) * D + roundTick) / (L * D), 0, 1)
 * bodyAlpha       = 0.82 - 0.32 * lifeProgress
 * </pre>
 *
 * <p>约束（README 与开发 2 技术指南 §3.6）：</p>
 * <ul>
 *   <li>{@code active} 与 {@code remainingRounds} 决定玩法状态，只随轮次变化；
 *       残影只能在轮次边界生成或淘汰，不得因透明度降低在轮中移除；</li>
 *   <li>{@code lifeProgress} 只用于透明度、轨迹与粒子表现，由共享 {@code roundTick}
 *       推导，<b>不是新的游戏计时器</b>；</li>
 *   <li>{@code bodyAlpha} 从约 0.82 平滑降至 0.50；最后一轮结束后的 0.50 → 0
 *       淡出属于 RESETTING 视觉过渡，不由本公式表达；</li>
 *   <li>本类不持有任何计数器：轮次与进度由调用方通过
 *       {@link #of(int, int, TickContext)} 从共享 {@link TickContext} 派生，
 *       禁止自建第二套轮次/秒数。</li>
 * </ul>
 */
public final class EchoLifetime {

    private final int sourceRound;
    private final int lifetimeRounds;
    private final long durationTicks;
    private final int currentRound;
    private final long roundTick;

    /**
     * 直接构造。优先使用 {@link #of(int, int, TickContext)}，
     * 以保证轮次与刻来自共享时钟而非调用方自报。
     *
     * @param sourceRound    来源轮次，范围 [1, ∞)
     * @param lifetimeRounds 整轮寿命 L，范围 [1, ∞)
     * @param durationTicks  轮长 D，范围 [1, ∞)
     * @param currentRound   当前轮次，范围 [1, ∞)
     * @param roundTick      当前刻，范围 [0, durationTicks)
     */
    public EchoLifetime(int sourceRound, int lifetimeRounds, long durationTicks,
                        int currentRound, long roundTick) {
        if (sourceRound < 1) {
            throw new IllegalArgumentException("sourceRound 必须 >= 1，实际 " + sourceRound);
        }
        if (lifetimeRounds < 1) {
            throw new IllegalArgumentException("lifetimeRounds 必须 >= 1，实际 " + lifetimeRounds);
        }
        if (durationTicks < 1) {
            throw new IllegalArgumentException("durationTicks 必须 >= 1，实际 " + durationTicks);
        }
        if (currentRound < 1) {
            throw new IllegalArgumentException("currentRound 必须 >= 1，实际 " + currentRound);
        }
        if (roundTick < 0 || roundTick >= durationTicks) {
            throw new IllegalArgumentException(
                    "roundTick 必须在 [0, " + (durationTicks - 1) + "]，实际 " + roundTick);
        }
        this.sourceRound = sourceRound;
        this.lifetimeRounds = lifetimeRounds;
        this.durationTicks = durationTicks;
        this.currentRound = currentRound;
        this.roundTick = roundTick;
    }

    /**
     * 由共享 {@link TickContext} 派生寿命视图 —— 这是推荐的构造方式：
     * 轮次与刻直接取自唯一权威 {@link RoundClock#toContext()}，
     * 调用方不需要、也不允许维护自己的轮次计数。
     *
     * @param sourceRound    残影来源轮次
     * @param lifetimeRounds 整轮寿命 L
     * @param context        共享时间上下文（来自 {@link RoundClock#toContext()}）
     * @return 该残影在当前轮次/刻的寿命视图
     */
    public static EchoLifetime of(int sourceRound, int lifetimeRounds, TickContext context) {
        Objects.requireNonNull(context, "context");
        return new EchoLifetime(sourceRound, lifetimeRounds, context.durationTicks(),
                context.currentRound(), context.roundTick());
    }

    /** 残影年龄：当前轮次减来源轮次；1 表示它创建后的第一个完整轮。 */
    public int getAge() {
        return currentRound - sourceRound;
    }

    /** 是否参与本轮行动：{@code 1 <= age <= L}；淘汰只发生在轮次边界。 */
    public boolean isActive() {
        int age = getAge();
        return age >= 1 && age <= lifetimeRounds;
    }

    /** 剩余存活轮数；{@code 1} 表示本Tick是它最后一个有效轮。 */
    public int getRemainingRounds() {
        if (!isActive()) {
            return 0;
        }
        return lifetimeRounds - getAge() + 1;
    }

    /**
     * 寿命进度 [0, 1]，仅用于透明度/轨迹/粒子表现，由共享 {@code roundTick} 推导。
     * 未生效（{@code age < 1}）时为 0，过期（{@code age > L}）时为 1。
     */
    public double getLifeProgress() {
        double numerator = (getAge() - 1) * (double) durationTicks + roundTick;
        double denominator = lifetimeRounds * (double) durationTicks;
        return Math.max(0.0, Math.min(1.0, numerator / denominator));
    }

    /** 身体透明度：{@code 0.82 - 0.32 * lifeProgress}，从约 0.82 平滑降至 0.50。 */
    public double getBodyAlpha() {
        return 0.82 - 0.32 * getLifeProgress();
    }

    /** 本Tick是否为该残影的最后一个有效轮（HUD「剩余 1 轮」/断续外环的依据）。 */
    public boolean isLastEffectiveRound() {
        return getRemainingRounds() == 1;
    }

    /** 是否应在本轮结束时消散（最后有效轮走完后于 RESETTING 过渡中淡出）。 */
    public boolean shouldDisappearAtRoundEnd() {
        return isActive() && getRemainingRounds() == 1;
    }

    /** 来源轮次。 */
    public int getSourceRound() {
        return sourceRound;
    }

    /** 整轮寿命 L。 */
    public int getLifetimeRounds() {
        return lifetimeRounds;
    }
}
