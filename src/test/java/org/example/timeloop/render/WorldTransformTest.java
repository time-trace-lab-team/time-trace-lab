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

    @Test
    void fitKeepsOneToOneWorldAtViewportOrigin() {
        WorldTransform transform = WorldTransform.fit(100.0, 100.0, 100.0, 100.0);

        assertTransform(transform, 1.0, 0.0, 0.0);
    }

    @Test
    void fitScalesMatchingAspectRatioWithoutLetterboxing() {
        WorldTransform transform = WorldTransform.fit(100.0, 50.0, 400.0, 200.0);

        assertTransform(transform, 4.0, 0.0, 0.0);
    }

    @Test
    void fitCentersWorldHorizontallyInWiderViewport() {
        WorldTransform transform = WorldTransform.fit(100.0, 100.0, 300.0, 100.0);

        assertTransform(transform, 1.0, 100.0, 0.0);
        assertEquals(100.0, transform.originX(), EPS);
        assertEquals(100.0, 300.0 - transform.toCanvasX(100.0), EPS);
    }

    @Test
    void fitCentersWorldVerticallyInTallerViewport() {
        WorldTransform transform = WorldTransform.fit(100.0, 100.0, 100.0, 300.0);

        assertTransform(transform, 1.0, 0.0, 100.0);
        assertEquals(100.0, transform.originY(), EPS);
        assertEquals(100.0, 300.0 - transform.toCanvasY(100.0), EPS);
    }

    @Test
    void fitContainsLevelOneWorldInsideStandardViewport() {
        WorldTransform transform = WorldTransform.fit(1_344.0, 768.0, 960.0, 576.0);

        assertTransform(transform, 960.0 / 1_344.0, 0.0,
                (576.0 - 768.0 * (960.0 / 1_344.0)) / 2.0);
        assertEquals(0.0, transform.toCanvasX(0.0), EPS);
        assertEquals(960.0, transform.toCanvasX(1_344.0), EPS);
        assertEquals(transform.toCanvasY(0.0), 576.0 - transform.toCanvasY(768.0), EPS);
    }

    @Test
    void fitRoundTripsWorldCoordinates() {
        WorldTransform transform = WorldTransform.fit(1_344.0, 768.0, 1_200.0, 500.0);

        assertEquals(381.25, transform.toWorldX(transform.toCanvasX(381.25)), EPS);
        assertEquals(617.5, transform.toWorldY(transform.toCanvasY(617.5)), EPS);
    }

    @Test
    void fitContainsAnExtremeAspectRatioWithSymmetricLetterboxing() {
        WorldTransform transform = WorldTransform.fit(1.0, 10_000.0, 10_000.0, 1.0);

        assertEquals(0.0001, transform.scale(), EPS);
        assertEquals(transform.originX(), 10_000.0 - transform.toCanvasX(1.0), EPS);
        assertEquals(0.0, transform.originY(), EPS);
        assertEquals(1.0, transform.toCanvasY(10_000.0), EPS);
    }

    @Test
    void fitRejectsEveryNonPositiveOrNonFiniteDimension() {
        double[] invalidDimensions = {
                0.0,
                -1.0,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        };

        for (double invalid : invalidDimensions) {
            assertThrows(IllegalArgumentException.class, () -> WorldTransform.fit(invalid, 100.0, 100.0, 100.0));
            assertThrows(IllegalArgumentException.class, () -> WorldTransform.fit(100.0, invalid, 100.0, 100.0));
            assertThrows(IllegalArgumentException.class, () -> WorldTransform.fit(100.0, 100.0, invalid, 100.0));
            assertThrows(IllegalArgumentException.class, () -> WorldTransform.fit(100.0, 100.0, 100.0, invalid));
        }
    }

    private static void assertTransform(WorldTransform transform, double scale, double originX, double originY) {
        assertEquals(scale, transform.scale(), EPS);
        assertEquals(originX, transform.originX(), EPS);
        assertEquals(originY, transform.originY(), EPS);
    }
}
