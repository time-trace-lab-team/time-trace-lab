package org.example.timeloop.core;

/**
 * 固定步长时钟（60 Hz）。
 * 纯 Java，不依赖 JavaFX，可在 JUnit 中直接构造。
 *
 * <p>不变量：{@link #getInterpolationAlpha()} 恒在 {@code [0, 1)}。
 * 严重掉帧时宁可让游戏时间变慢，也不累积欠账，避免帧率恢复后快进。</p>
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

        // D-02：时间源回退（休眠唤醒、多显示器切换等）不能让累加器变负，
        // 否则插值 alpha 为负会导致画面实体向后跳一帧。
        if (delta <= 0.0) {
            return 0;
        }

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

        // D-01：补算达到上限后豁免剩余债务。
        // 若把余额留给下一帧，持续掉帧会累积出无法即时清偿的欠账，
        // 帧率恢复后将以 MAX_STEPS_PER_FRAME 倍速快进偿还，
        // 违反指南 3.1「不允许把 roundTick 直接跳到未来」。
        // 本类的不变量由此成立：getInterpolationAlpha() 恒在 [0, 1)。
        if (steps == MAX_STEPS_PER_FRAME && accumulator >= STEP_SECONDS) {
            accumulator = 0.0;
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