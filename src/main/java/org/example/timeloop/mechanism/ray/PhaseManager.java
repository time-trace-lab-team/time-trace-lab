package org.example.timeloop.mechanism.ray;

public class PhaseManager {

    public enum PhaseState { NORMAL, PHASED, RECOVERING }

    private static final int PHASE_DURATION_TICKS = 30;
    private static final int RECOVERY_DURATION_TICKS = 45;

    private PhaseState state = PhaseState.NORMAL;
    private int remainingTicks = 0;
    private boolean spacePressed = false;

    public PhaseState getState() { return state; }
    public boolean isPhased() { return state == PhaseState.PHASED; }
    public boolean canTrigger() { return state == PhaseState.NORMAL; }

    public void pressSpace() {
        if (state == PhaseState.NORMAL && !spacePressed) {
            state = PhaseState.PHASED;
            remainingTicks = PHASE_DURATION_TICKS;
            spacePressed = true;
        }
    }

    public void releaseSpace() {
        spacePressed = false;
    }

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
}