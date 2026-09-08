package org.example.timeloop.core;

/**
 * 固定步长时钟（60 Hz）。
 * 纯 Java，不依赖 JavaFX，可在 JUnit 中直接构造。
 */
public final class FixedStepClock {

    public static final double STEP_SECONDS = 1.0 / 60.0;
    public static final double MAX_FRAME_SECONDS = 0.25;
    public static final int MAX_STEPS_PER_FRAME = 5;

    private double accumulator = 0.0;
    private long lastNanoTime = -1;
    private long totalTicks = 0;

    public int advance(long nanoTime, GamePhase phase) {
        if (phase == GamePhase.BOOT) {
            lastNanoTime = nanoTime;
            return 0;
        }

        if (phase == GamePhase.READY || phase == GamePhase.PAUSED) {
            lastNanoTime = nanoTime;
            return 0;
        }

        if (lastNanoTime < 0) {
            lastNanoTime = nanoTime;
            return 0;
        }

        double delta = (nanoTime - lastNanoTime) / 1_000_000_000.0;
        lastNanoTime = nanoTime;

        if (delta > MAX_FRAME_SECONDS) {
            delta = MAX_FRAME_SECONDS;
        }

        accumulator += delta;
        int steps = 0;

        while (accumulator >= STEP_SECONDS && steps < MAX_STEPS_PER_FRAME) {
            accumulator -= STEP_SECONDS;
            steps++;
            totalTicks++;
        }

        return steps;
    }

    public double getInterpolationAlpha() {
        return accumulator / STEP_SECONDS;
    }

    public long getTotalTicks() {
        return totalTicks;
    }

    public void reset() {
        accumulator = 0.0;
        lastNanoTime = -1;
        totalTicks = 0;
    }
}