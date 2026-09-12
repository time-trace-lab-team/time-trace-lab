package org.example.timeloop.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** R-2 档位与静态节点视图校验；不初始化 JavaFX Toolkit。 */
class PathNodeHintLayerTest {

    @Test
    void levelForUsesInclusiveOneAndTwoTileThresholds() {
        double tileSize = 48.0;

        assertEquals(PathNodeHintLayer.HintLevel.BRIGHT, PathNodeHintLayer.levelFor(0.0, tileSize));
        assertEquals(PathNodeHintLayer.HintLevel.BRIGHT, PathNodeHintLayer.levelFor(48.0, tileSize));
        assertEquals(PathNodeHintLayer.HintLevel.DIM, PathNodeHintLayer.levelFor(48.0001, tileSize));
        assertEquals(PathNodeHintLayer.HintLevel.DIM, PathNodeHintLayer.levelFor(96.0, tileSize));
        assertEquals(PathNodeHintLayer.HintLevel.HIDDEN, PathNodeHintLayer.levelFor(96.0001, tileSize));
    }

    @Test
    void levelForRejectsInvalidDistanceOrTileSize() {
        assertThrows(IllegalArgumentException.class, () -> PathNodeHintLayer.levelFor(-1.0, 48.0));
        assertThrows(IllegalArgumentException.class, () -> PathNodeHintLayer.levelFor(Double.NaN, 48.0));
        assertThrows(IllegalArgumentException.class, () -> PathNodeHintLayer.levelFor(1.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> PathNodeHintLayer.levelFor(1.0, Double.POSITIVE_INFINITY));
    }

    @Test
    void markerRequiresStableIdAndFiniteWorldCoordinates() {
        RenderViews.PathNodeMarker marker = new RenderViews.PathNodeMarker("fork", 240.0, 144.0);

        assertEquals("fork", marker.id());
        assertThrows(IllegalArgumentException.class, () -> new RenderViews.PathNodeMarker(" ", 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new RenderViews.PathNodeMarker("fork", Double.NaN, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new RenderViews.PathNodeMarker("fork", 0.0, Double.NEGATIVE_INFINITY));
    }
}
