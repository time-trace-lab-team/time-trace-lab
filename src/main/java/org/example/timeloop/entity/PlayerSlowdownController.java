package org.example.timeloop.entity;

import org.example.timeloop.core.PlayerEffectResetReason;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** 当前玩家的射线减速、激活周期去重和只读速度倍率查询。 */
public final class PlayerSlowdownController implements SpeedModifierPort {

    public static final double DEFAULT_SLOW_MULTIPLIER = 0.50;
    public static final int DEFAULT_SLOW_DURATION_TICKS = 60;

    private final double slowMultiplier;
    private final int slowDurationTicks;
    private final Set<HitKey> notedHits = new HashSet<>();

    private long currentTick = -1;
    private long slowedUntilExclusive;

    public PlayerSlowdownController() {
        this(DEFAULT_SLOW_MULTIPLIER, DEFAULT_SLOW_DURATION_TICKS);
    }

    public PlayerSlowdownController(double slowMultiplier, int slowDurationTicks) {
        if (!Double.isFinite(slowMultiplier) || slowMultiplier < 0.0 || slowMultiplier > 1.0) {
            throw new IllegalArgumentException("slowMultiplier 必须在 [0, 1] 内");
        }
        if (slowDurationTicks <= 0) {
            throw new IllegalArgumentException("slowDurationTicks 必须 > 0");
        }
        this.slowMultiplier = slowMultiplier;
        this.slowDurationTicks = slowDurationTicks;
    }

    /** 提交共享 roundTick；查询本身不推进时间。 */
    public void beginTick(long roundTick) {
        if (roundTick < 0) {
            throw new IllegalArgumentException("roundTick 必须 >= 0");
        }
        if (currentTick >= 0 && roundTick <= currentTick) {
            throw new IllegalArgumentException("roundTick 必须严格递增；轮次切换前须 reset");
        }
        currentTick = roundTick;
    }

    /**
     * 记录移动后发生的一次非 PHASED 权威命中。
     *
     * <p>同一 {@code (rayId, activeCycle)} 只接收一次；新的命中会把减速窗口刷新为
     * {@code [currentTick, currentTick + duration)}，但倍率不叠加。</p>
     *
     * @return 本命中是否首次被接受
     */
    public boolean noteHit(String rayId, long activeCycle) {
        if (rayId == null || rayId.isBlank()) {
            throw new IllegalArgumentException("rayId 不能为空白");
        }
        if (activeCycle < 0) {
            throw new IllegalArgumentException("activeCycle 必须 >= 0");
        }
        if (currentTick < 0) {
            throw new IllegalStateException("noteHit 前必须先调用 beginTick");
        }

        if (!notedHits.add(new HitKey(rayId, activeCycle))) {
            return false;
        }
        slowedUntilExclusive = Math.addExact(currentTick, slowDurationTicks);
        return true;
    }

    @Override
    public double speedMultiplier() {
        return isSlowed() ? slowMultiplier : 1.0;
    }

    public boolean isSlowed() {
        return currentTick >= 0 && currentTick < slowedUntilExclusive;
    }

    public void reset(PlayerEffectResetReason reason) {
        Objects.requireNonNull(reason, "reason");
        currentTick = -1;
        slowedUntilExclusive = 0;
        notedHits.clear();
    }

    private record HitKey(String rayId, long activeCycle) {
        private HitKey {
            Objects.requireNonNull(rayId, "rayId");
        }
    }
}
