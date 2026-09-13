package org.example.timeloop.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** R-2 档位与静态节点视图校验；不初始化 JavaFX Toolkit。 */
class PathNodeHintLayerTest {

    /**
     * 28×16 地图每格都是路径节点（265 个），档位必须收紧到「自己这格 + 上下左右紧邻格」，
     * 否则玩家周围会同时出现 13–21 个菱形，退化成局部棋盘格。
     * 48 单位 = 1 格；√2×48 ≈ 67.88 单位 = 斜角，必须隐藏。
     */
    @Test
    void levelForKeepsOnlyThePlayerCellAndItsOrthogonalNeighbours() {
        double tileSize = 48.0;

        // 玩家所在格：亮（边界用 0.6 × tileSize 表达，与 levelFor 的 BRIGHT_RADIUS_TILES 同一算式）
        assertEquals(PathNodeHintLayer.HintLevel.BRIGHT, PathNodeHintLayer.levelFor(0.0, tileSize));
        assertEquals(PathNodeHintLayer.HintLevel.BRIGHT, PathNodeHintLayer.levelFor(0.6 * tileSize, tileSize));
        // 上下左右紧邻格（恰好 1 格）：暗，但必须可见
        assertEquals(PathNodeHintLayer.HintLevel.DIM, PathNodeHintLayer.levelFor(28.8001, tileSize));
        assertEquals(PathNodeHintLayer.HintLevel.DIM, PathNodeHintLayer.levelFor(48.0, tileSize));
        assertEquals(PathNodeHintLayer.HintLevel.DIM, PathNodeHintLayer.levelFor(1.2 * tileSize, tileSize));
        // 斜角（√2 格）与更远：隐藏
        assertEquals(PathNodeHintLayer.HintLevel.HIDDEN,
                PathNodeHintLayer.levelFor(Math.sqrt(2.0) * tileSize, tileSize));
        assertEquals(PathNodeHintLayer.HintLevel.HIDDEN, PathNodeHintLayer.levelFor(57.6001, tileSize));
        assertEquals(PathNodeHintLayer.HintLevel.HIDDEN, PathNodeHintLayer.levelFor(96.0, tileSize));
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
