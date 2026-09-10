package org.example.timeloop.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PatrolConfigTest {

    @Test
    void exposesApprovedC2Constants() {
        PatrolConfig config = PatrolConfig.c2Greybox();

        assertEquals(48.0, config.tileSize());
        assertEquals(2.0, config.baseSpeed());
        assertEquals(7.2, config.turnLockDistance());
        assertEquals(0.000048, config.epsilon());
    }

    @Test
    void rejectsNonFiniteOrNonPositiveFrozenParameters() {
        assertThrows(IllegalArgumentException.class, () -> new PatrolConfig(48.0, 0.0, 7.2, 0.000048));
        assertThrows(IllegalArgumentException.class,
                () -> new PatrolConfig(Double.NaN, 2.0, 7.2, 0.000048));
        assertThrows(IllegalArgumentException.class, () -> new PatrolConfig(48.0, 2.0, 48.0, 0.000048));
    }
}
