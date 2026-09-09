package org.example.timeloop.replay;

public class EchoLifetime {

    private final int sourceRound;
    private final int lifetimeRounds;
    private final long durationTicks;
    private final int currentRound;
    private final long roundTick;

    public EchoLifetime(int sourceRound, int lifetimeRounds, long durationTicks,
                        int currentRound, long roundTick) {
        this.sourceRound = sourceRound;
        this.lifetimeRounds = lifetimeRounds;
        this.durationTicks = durationTicks;
        this.currentRound = currentRound;
        this.roundTick = roundTick;
    }

    public int getAge() {
        return currentRound - sourceRound;
    }

    public boolean isActive() {
        int age = getAge();
        return age >= 1 && age <= lifetimeRounds;
    }

    public int getRemainingRounds() {
        int age = getAge();
        if (!isActive()) {
            return 0;
        }
        return lifetimeRounds - age + 1;
    }

    public double getLifeProgress() {
        int age = getAge();
        if (!isActive()) {
            return 1.0;
        }
        double numerator = (age - 1) * durationTicks + roundTick;
        double denominator = lifetimeRounds * durationTicks;
        double progress = numerator / denominator;
        return Math.max(0, Math.min(1, progress));
    }

    public double getBodyAlpha() {
        return 0.82 - 0.32 * getLifeProgress();
    }

    public boolean isLastEffectiveRound() {
        return getRemainingRounds() == 1;
    }

    public boolean shouldDisappearAtRoundEnd() {
        return isActive() && getRemainingRounds() == 1;
    }

    public int getSourceRound() {
        return sourceRound;
    }

    public int getLifetimeRounds() {
        return lifetimeRounds;
    }

    public long getDurationTicks() {
        return durationTicks;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public long getRoundTick() {
        return roundTick;
    }

    @Override
    public String toString() {
        return String.format("EchoLifetime{source=%d, age=%d, remaining=%d, alpha=%.2f}",
                sourceRound, getAge(), getRemainingRounds(), getBodyAlpha());
    }
}