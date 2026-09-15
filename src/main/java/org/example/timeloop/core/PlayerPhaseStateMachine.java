package org.example.timeloop.core;

import java.util.Objects;

/** 当前玩家的纯逻辑相位状态机；不改变移动、方向或墙门碰撞。 */
public final class PlayerPhaseStateMachine {

    public static final int DEFAULT_PHASE_DURATION_TICKS = 30;
    public static final int DEFAULT_RECOVERY_DURATION_TICKS = 45;

    private final int phaseDurationTicks;
    private final int recoveryDurationTicks;

    private ActorPhase phase = ActorPhase.AVAILABLE;
    private long stateUntilExclusive;
    private long lastTick = -1;
    private boolean pressedPreviously;

    public PlayerPhaseStateMachine() {
        this(DEFAULT_PHASE_DURATION_TICKS, DEFAULT_RECOVERY_DURATION_TICKS);
    }

    public PlayerPhaseStateMachine(int phaseDurationTicks, int recoveryDurationTicks) {
        if (phaseDurationTicks <= 0) {
            throw new IllegalArgumentException("phaseDurationTicks 必须 > 0");
        }
        if (recoveryDurationTicks <= 0) {
            throw new IllegalArgumentException("recoveryDurationTicks 必须 > 0");
        }
        this.phaseDurationTicks = phaseDurationTicks;
        this.recoveryDurationTicks = recoveryDurationTicks;
    }

    /**
     * 推进到一个 PLAYING 逻辑刻。
     *
     * @param pressed 当前 tick 结束时 Space 是否处于按下状态；内部自行检测新边沿
     * @param roundTick 当前关卡共享 roundTick
     * @return 本 tick 应写入规范玩家帧的相位快照
     */
    public Snapshot advance(boolean pressed, long roundTick) {
        requireMonotonicTick(roundTick);
        boolean pressedEdge = pressed && !pressedPreviously;
        pressedPreviously = pressed;

        advanceExpiredState(roundTick);
        if (phase == ActorPhase.AVAILABLE && pressedEdge) {
            phase = ActorPhase.PHASED;
            stateUntilExclusive = Math.addExact(roundTick, phaseDurationTicks);
        }
        lastTick = roundTick;
        return snapshotAt(roundTick);
    }

    public Snapshot snapshot() {
        long tick = Math.max(lastTick, 0);
        return snapshotAt(tick);
    }

    public void reset(PlayerEffectResetReason reason) {
        Objects.requireNonNull(reason, "reason");
        phase = ActorPhase.AVAILABLE;
        stateUntilExclusive = 0;
        lastTick = -1;
        pressedPreviously = false;
    }

    private void advanceExpiredState(long roundTick) {
        if (phase == ActorPhase.PHASED && roundTick >= stateUntilExclusive) {
            phase = ActorPhase.RECOVERING;
            stateUntilExclusive = Math.addExact(roundTick, recoveryDurationTicks);
        } else if (phase == ActorPhase.RECOVERING && roundTick >= stateUntilExclusive) {
            phase = ActorPhase.AVAILABLE;
            stateUntilExclusive = 0;
        }
    }

    private Snapshot snapshotAt(long roundTick) {
        int remaining = phase == ActorPhase.AVAILABLE
                ? 0
                : Math.toIntExact(stateUntilExclusive - roundTick);
        return new Snapshot(phase, remaining);
    }

    private void requireMonotonicTick(long roundTick) {
        if (roundTick < 0) {
            throw new IllegalArgumentException("roundTick 必须 >= 0");
        }
        if (lastTick >= 0 && roundTick <= lastTick) {
            throw new IllegalArgumentException("roundTick 必须严格递增；轮次切换前须 reset");
        }
    }

    public record Snapshot(ActorPhase phase, int ticksRemaining) {
        public Snapshot {
            Objects.requireNonNull(phase, "phase");
            if (ticksRemaining < 0 || (phase == ActorPhase.AVAILABLE && ticksRemaining != 0)) {
                throw new IllegalArgumentException("相位与剩余 tick 不一致");
            }
        }
    }
}
