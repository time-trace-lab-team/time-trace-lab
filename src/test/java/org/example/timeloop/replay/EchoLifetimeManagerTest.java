package org.example.timeloop.replay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EchoLifetimeManagerTest {

    private EchoLifetimeManager manager;

    @BeforeEach
    void setUp() {
        manager = new EchoLifetimeManager();
        manager.initialize(60, 4, 2);
    }

    @Test
    void testAddEchoAndAdvanceRounds() {
        manager.advanceRound(0);   // current=1
        manager.addEcho(1);        // E1 source=1, current=1

        manager.advanceRound(0);   // current=2, E1 age=1 active
        EchoLifetime e1 = manager.getEcho(1);
        assertNotNull(e1);
        assertTrue(e1.isActive());
        assertEquals(2, e1.getRemainingRounds());

        manager.addEcho(2);        // E2 source=2, current=2
        manager.advanceRound(0);   // current=3

        EchoLifetime e1_3 = manager.getEcho(1);
        EchoLifetime e2_3 = manager.getEcho(2);
        assertTrue(e1_3.isActive());
        assertTrue(e2_3.isActive());
        assertEquals(1, e1_3.getRemainingRounds());
        assertEquals(2, e2_3.getRemainingRounds());
        assertTrue(e1_3.getBodyAlpha() < e2_3.getBodyAlpha());
    }

    @Test
    void testE1DisappearsBeforeE2() {
        manager.advanceRound(0);   // current=1
        manager.addEcho(1);        // E1 source=1, current=1, age=0
        manager.advanceRound(0);   // current=2, E1 age=1, remaining=2
        manager.addEcho(2);        // E2 source=2, current=2, age=0
        manager.advanceRound(0);   // current=3, E1 age=2, E2 age=1
        manager.advanceRound(0);   // current=4, E1 age=3(inactive), E2 age=2(active)

        EchoLifetime e1 = manager.getEcho(1);
        EchoLifetime e2 = manager.getEcho(2);
        assertNotNull(e1);
        assertNotNull(e2);
        assertFalse(e1.isActive());
        assertTrue(e2.isActive());
        assertEquals(0, e1.getRemainingRounds());
        assertEquals(1, e2.getRemainingRounds());
    }

    @Test
    void testGetEchoesToDisappear() {
        manager.advanceRound(0);   // current=1
        manager.addEcho(1);        // E1 source=1
        manager.advanceRound(0);   // current=2, E1 age=1
        manager.addEcho(2);        // E2 source=2, current=2
        manager.advanceRound(0);   // current=3, E1 age=2, E2 age=1

        var toDisappear = manager.getEchoesToDisappear();
        assertTrue(toDisappear.contains(1));
        assertFalse(toDisappear.contains(2));
    }

    @Test
    void testLifetimeAlphaChanges() {
        manager.advanceRound(0);   // current=1
        manager.addEcho(1);        // E1 source=1, current=1
        manager.advanceRound(0);   // current=2, E1 age=1, roundTick=0

        EchoLifetime e1 = manager.getEcho(1);
        assertNotNull(e1);
        assertTrue(e1.isActive());
        assertEquals(0.0, e1.getLifeProgress(), 0.01);
        assertEquals(0.82, e1.getBodyAlpha(), 0.01);

        manager.updateRoundTick(30);
        e1 = manager.getEcho(1);
        assertEquals(0.25, e1.getLifeProgress(), 0.01);
        assertEquals(0.74, e1.getBodyAlpha(), 0.01);
    }
}