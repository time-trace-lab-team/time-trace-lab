package org.example.timeloop.ui;

import org.example.timeloop.replay.EchoLifetime;

public class LifetimeUI {

    public static String getEchoStatusText(EchoLifetime lifetime) {
        if (lifetime == null || !lifetime.isActive()) {
            return "已消散";
        }
        int remaining = lifetime.getRemainingRounds();
        double alpha = lifetime.getBodyAlpha();

        if (remaining == 1) {
            return String.format("E%d 最后一轮 (%.0f%%)",
                    lifetime.getSourceRound(), alpha * 100);
        }
        return String.format("E%d 剩余 %d 轮 (%.0f%%)",
                lifetime.getSourceRound(), remaining, alpha * 100);
    }

    public static boolean shouldUseDashedPath(EchoLifetime lifetime) {
        if (lifetime == null || !lifetime.isActive()) {
            return true;
        }
        return lifetime.isLastEffectiveRound();
    }

    public static boolean shouldShowOuterRing(EchoLifetime lifetime) {
        if (lifetime == null || !lifetime.isActive()) {
            return false;
        }
        return lifetime.isLastEffectiveRound();
    }
}