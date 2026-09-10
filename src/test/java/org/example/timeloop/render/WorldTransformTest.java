package org.example.timeloop.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * C5 坐标变换测试（纯逻辑，不初始化 JavaFX Toolkit）。
 */
class WorldTransformTest {

    private static final double EPS = 1e-9;

    @Test
    void identityMapsWorldToSameCoordinates() {
        WorldTransform t = WorldTransform.identity();
        assertEquals(96.0, t.toCanvasX(96.0), EPS);
        assertEquals(240.0, t.toCanvasY(240.0), EPS);
        assertEquals(0.5, t.scaled(0.5), EPS);
    }

    @Test
    void scaleAndOriginProjectWorldToCanvas() {
        WorldTransform t = new WorldTransform(2.0, 10.0, 20.0);
        assertEquals(10.0 + 96.0 * 2.0, t.toCanvasX(96.0), EPS);
        assertEquals(20.0 + 240.0 * 2.0, t.toCanvasY(240.0), EPS);
    }

    @Test
    void canvasToWorldIsInverseOfWorldToCanvas() {
        WorldTransform t = new WorldTransform(1.5, -12.0, 8.0);
        double worldX = 123.0;
        double worldY = 45.0;
        assertEquals(worldX, t.toWorldX(t.toCanvasX(worldX)), EPS);
        assertEquals(worldY, t.toWorldY(t.toCanvasY(worldY)), EPS);
    }

    @Test
    void scaledConvertsWorldLengthToCanvasLength() {
        WorldTransform t = new WorldTransform(3.0, 0.0, 0.0);
        assertEquals(144.0, t.scaled(48.0), EPS);
    }

    @Test
    void translatedShiftsOriginOnly() {
        WorldTransform t = new WorldTransform(2.0, 10.0, 20.0);
        WorldTransform moved = t.translated(5.0, -5.0);
        assertEquals(15.0, moved.originX(), EPS);
        assertEquals(15.0, moved.originY(), EPS);
        assertEquals(2.0, moved.scale(), EPS);
    }

    @Test
    void nonPositiveOrNonFiniteScaleIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new WorldTransform(0.0, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new WorldTransform(-1.0, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new WorldTransform(Double.NaN, 0.0, 0.0));
    }

    @Test
    void nonFiniteOriginIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorldTransform(1.0, Double.POSITIVE_INFINITY, 0.0));
    }
}
