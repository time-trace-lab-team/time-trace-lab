package org.example.timeloop.mechanism.ray;

import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RayTest {

    private Ray ray;

    @BeforeEach
    void setUp() {
        EventDispatcher.getInstance().clear();
        RayManager.getInstance().clear();

        Vector2D start = new Vector2D(100, 100);
        Vector2D end = new Vector2D(300, 100);
        ray = new Ray("ray_1", start, end, 10, 20, 30, 15);
        RayManager.getInstance().register(ray);
    }

    @Test
    void testInitialState() {
        assertEquals(Ray.State.OFF, ray.getState());
    }

    @Test
    void testUpdateToWarning() {
        ray.update(10);  // tick = 10，进入 WARNING
        assertEquals(Ray.State.WARNING, ray.getState());
    }

    @Test
    void testUpdateToActive() {
        ray.update(30);  // tick = 30，进入 ACTIVE
        assertEquals(Ray.State.ACTIVE, ray.getState());
    }

    @Test
    void testUpdateToOff() {
        ray.update(45);  // tick = 45，回到 OFF
        assertEquals(Ray.State.OFF, ray.getState());
    }

    @Test
    void testContainsPointOnRay() {
        Vector2D point = new Vector2D(200, 100);
        assertTrue(ray.containsPoint(point, 5));
    }

    @Test
    void testContainsPointOffRay() {
        Vector2D point = new Vector2D(200, 120);
        assertFalse(ray.containsPoint(point, 5));
    }

    @Test
    void testReset() {
        ray.update(30);
        assertEquals(Ray.State.ACTIVE, ray.getState());
        ray.reset();
        assertEquals(Ray.State.OFF, ray.getState());
    }

    @Test
    void testRayManagerUpdateAll() {
        RayManager.getInstance().updateAll(30);
        assertEquals(Ray.State.ACTIVE, ray.getState());
    }

    @Test
    void testGetActiveRaysAt() {
        RayManager.getInstance().updateAll(30);
        Vector2D point = new Vector2D(200, 100);
        var active = RayManager.getInstance().getActiveRaysAt(point, 5);
        assertEquals(1, active.size());
        assertEquals("ray_1", active.get(0).getId());
    }
}