package org.example.timeloop.core;

/**
 * 相位下潜管理器。
 * 由 Space 键触发，持续 30 tick，恢复 45 tick。
 * 只忽略射线，不改变方向或速度。
 */
public class PhaseManager {

    public enum PhaseState { NORMAL, PHASED, RECOVERING }

    private static final int PHASE_DURATION_TICKS = 30;
    private static final int RECOVERY_DURATION_TICKS = 45;

    private PhaseState state = PhaseState.NORMAL;
    private int remainingTicks = 0;
    private boolean spacePressed = false;

    public PhaseState getState() { return state; }
    public boolean isPhased() { return state == PhaseState.PHASED; }
    public boolean isRecovering() { return state == PhaseState.RECOVERING; }
    public boolean canTrigger() { return state == PhaseState.NORMAL; }

    /**
     * 处理 Space 键按下（新边沿触发）。
     */
    public void pressSpace() {
        if (state == PhaseState.NORMAL && !spacePressed) {
            state = PhaseState.PHASED;
            remainingTicks = PHASE_DURATION_TICKS;
            spacePressed = true;
        }
    }

    /**
     * 处理 Space 键释放（允许下次按下重新触发）。
     */
    public void releaseSpace() {
        spacePressed = false;
    }

    /**
     * 每 tick 更新相位状态。
     */
    public void update() {
        switch (state) {
            case PHASED:
                remainingTicks--;
                if (remainingTicks <= 0) {
                    state = PhaseState.RECOVERING;
                    remainingTicks = RECOVERY_DURATION_TICKS;
                }
                break;
            case RECOVERING:
                remainingTicks--;
                if (remainingTicks <= 0) {
                    state = PhaseState.NORMAL;
                }
                break;
            default:
                break;
        }
    }

    public void reset() {
        state = PhaseState.NORMAL;
        remainingTicks = 0;
        spacePressed = false;
    }

    public int getRemainingTicks() {
        return remainingTicks;
    }

    public double getProgress() {
        // 用于 UI 显示恢复进度（0-1）
        if (state == PhaseState.PHASED) {
            return (double) remainingTicks / PHASE_DURATION_TICKS;
        } else if (state == PhaseState.RECOVERING) {
            return 1.0 - (double) remainingTicks / RECOVERY_DURATION_TICKS;
        }
        return 1.0;
    }
}