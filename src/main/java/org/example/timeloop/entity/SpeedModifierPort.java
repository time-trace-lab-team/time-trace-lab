package org.example.timeloop.entity;

/**
 * Provides the movement-speed multiplier prepared for the current logical tick.
 *
 * <p>The entity layer only consumes this value. Mechanism implementations own
 * the decision of when a modifier begins or ends.</p>
 */
@FunctionalInterface
public interface SpeedModifierPort {

    /** Returns the non-negative, finite multiplier for the current tick. */
    double speedMultiplier();

    /** The default modifier for movement unaffected by a mechanism. */
    static SpeedModifierPort normalSpeed() {
        return () -> 1.0;
    }
}
