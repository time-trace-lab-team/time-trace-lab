package org.example.timeloop.entity;

/**
 * Immutable C2 movement parameters frozen for one patrol controller session.
 *
 * <p>The greybox factory is the approved C2 configuration. The constructor is
 * intentionally explicit so a future level adapter cannot mutate speed or
 * geometry constants midway through a recorded round.</p>
 */
public record PatrolConfig(
        double tileSize,
        double baseSpeed,
        double turnLockDistance,
        double epsilon
) {

    public static final double C2_TILE_SIZE = 48.0;
    public static final double C2_BASE_SPEED = 2.0;
    public static final double C2_TURN_LOCK_DISTANCE = 7.2;
    public static final double C2_EPSILON = 0.000048;

    public PatrolConfig {
        requirePositiveFinite("tileSize", tileSize);
        requirePositiveFinite("baseSpeed", baseSpeed);
        requirePositiveFinite("turnLockDistance", turnLockDistance);
        requirePositiveFinite("epsilon", epsilon);
        if (turnLockDistance >= tileSize) {
            throw new IllegalArgumentException(
                    "turnLockDistance must be smaller than tileSize: " + turnLockDistance + " >= " + tileSize);
        }
    }

    /** Returns C2's approved 48-unit grid and 2-world-unit-per-tick speed. */
    public static PatrolConfig c2Greybox() {
        return new PatrolConfig(C2_TILE_SIZE, C2_BASE_SPEED, C2_TURN_LOCK_DISTANCE, C2_EPSILON);
    }

    private static void requirePositiveFinite(String name, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and > 0, actual " + value);
        }
    }
}
