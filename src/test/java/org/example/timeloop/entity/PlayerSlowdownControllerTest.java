package org.example.timeloop.entity;

import org.example.timeloop.core.PlayerEffectResetReason;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerSlowdownControllerTest {

    @Test
    void acceptedHitMarksCurrentFrameAndSlowsTheNext60MovementTicks() {
        PlayerSlowdownController slowdown = new PlayerSlowdownController();
        slowdown.beginTick(40);

        assertEquals(1.0, slowdown.speedMultiplier(), 0.0001,
                "移动前查询仍是满速");
        assertTrue(slowdown.noteHit("ray-a", 0));
        assertEquals(0.50, slowdown.speedMultiplier(), 0.0001,
                "移动后命中的本 tick 帧应已携带 SLOWED");

        for (long tick = 41; tick < 100; tick++) {
            slowdown.beginTick(tick);
            assertEquals(0.50, slowdown.speedMultiplier(), 0.0001, "slow tick=" + tick);
        }
        slowdown.beginTick(100);
        assertEquals(1.0, slowdown.speedMultiplier(), 0.0001);
    }

    @Test
    void sameRayCycleDeduplicatesWhileNewHitRefreshesWithoutStacking() {
        PlayerSlowdownController slowdown = new PlayerSlowdownController(0.5, 4);
        slowdown.beginTick(10);
        assertTrue(slowdown.noteHit("ray-a", 3));

        slowdown.beginTick(11);
        assertFalse(slowdown.noteHit("ray-a", 3), "同一射线激活周期只能结算一次");
        assertTrue(slowdown.noteHit("ray-b", 8), "不同射线/周期命中应刷新窗口");
        assertEquals(0.5, slowdown.speedMultiplier(), 0.0001, "倍率不得叠加");

        slowdown.beginTick(14);
        assertEquals(0.5, slowdown.speedMultiplier(), 0.0001, "窗口应刷新到第二次命中之后");
        slowdown.beginTick(15);
        assertEquals(1.0, slowdown.speedMultiplier(), 0.0001);
    }

    @Test
    void queryDoesNotConsumeTimeAndEveryResetReasonClearsDeduplication() {
        for (PlayerEffectResetReason reason : PlayerEffectResetReason.values()) {
            PlayerSlowdownController slowdown = new PlayerSlowdownController(0.5, 2);
            slowdown.beginTick(0);
            slowdown.noteHit("ray", 0);
            assertEquals(slowdown.speedMultiplier(), slowdown.speedMultiplier());

            slowdown.reset(reason);
            assertEquals(1.0, slowdown.speedMultiplier(), 0.0001);
            slowdown.beginTick(0);
            assertTrue(slowdown.noteHit("ray", 0), reason.toString());
        }
    }

    @Test
    void validatesConfigurationCallOrderAndMonotonicTick() {
        assertThrows(IllegalArgumentException.class, () -> new PlayerSlowdownController(-0.1, 60));
        assertThrows(IllegalArgumentException.class, () -> new PlayerSlowdownController(1.1, 60));
        assertThrows(IllegalArgumentException.class, () -> new PlayerSlowdownController(0.5, 0));

        PlayerSlowdownController slowdown = new PlayerSlowdownController();
        assertThrows(IllegalStateException.class, () -> slowdown.noteHit("ray", 0));
        assertThrows(IllegalArgumentException.class, () -> slowdown.beginTick(-1));
        slowdown.beginTick(0);
        assertThrows(IllegalArgumentException.class, () -> slowdown.noteHit(" ", 0));
        assertThrows(IllegalArgumentException.class, () -> slowdown.noteHit("ray", -1));
        assertThrows(IllegalArgumentException.class, () -> slowdown.beginTick(0));
        assertThrows(NullPointerException.class, () -> slowdown.reset(null));
    }
}
