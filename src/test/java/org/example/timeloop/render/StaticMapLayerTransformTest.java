package org.example.timeloop.render;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.example.timeloop.level.model.TileType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 静态地图/出生点图层对动态世界变换的离屏像素验证。 */
class StaticMapLayerTransformTest {

    @BeforeAll
    static void initializeJavafx() {
        ensureFxStarted();
    }

    @Test
    void groundTileUsesUpdatedOriginAndScaledTileSize() throws Exception {
        onFxThread((canvas, graphics) -> {
            AtomicReference<WorldTransform> transform = new AtomicReference<>(WorldTransform.identity());
            GroundWallLayer layer = new GroundWallLayer(new TileType[][]{{TileType.FLOOR}}, 48.0, transform::get);

            layer.render(graphics, 200.0, 100.0, 0.0);
            WritableImage first = canvas.snapshot(null, null);
            assertColorClose(RenderPalette.FLOOR_SHADES[0], first.getPixelReader().getColor(40, 40));

            transform.set(new WorldTransform(0.5, 100.0, 20.0));
            Canvas updatedCanvas = new Canvas(240.0, 160.0);
            layer.render(updatedCanvas.getGraphicsContext2D(), 200.0, 100.0, 0.0);
            WritableImage updated = updatedCanvas.snapshot(null, null);

            assertColorClose(RenderPalette.FLOOR_SHADES[0], updated.getPixelReader().getColor(110, 30));
            assertFalse(hasColor(RenderPalette.FLOOR_SHADES[0], updated.getPixelReader().getColor(40, 40)),
                    "更新后旧 origin 位置不得仍是地格");
            assertFalse(hasColor(RenderPalette.FLOOR_SHADES[0], updated.getPixelReader().getColor(140, 30)),
                    "更新后地格宽度必须随 scale 从 48 缩为 24 像素");
        });
    }

    @Test
    void spawnMarkerUsesUpdatedOriginAndScale() throws Exception {
        onFxThread((canvas, graphics) -> {
            AtomicReference<WorldTransform> transform = new AtomicReference<>(WorldTransform.identity());
            SpawnLayer layer = new SpawnLayer(48.0, 48.0, 48.0, transform::get);

            layer.render(graphics, 240.0, 160.0, 0.0);
            WritableImage first = canvas.snapshot(null, null);
            assertColorClose(RenderPalette.SPAWN_CORE, first.getPixelReader().getColor(48, 48));

            transform.set(new WorldTransform(0.5, 100.0, 20.0));
            Canvas updatedCanvas = new Canvas(240.0, 160.0);
            layer.render(updatedCanvas.getGraphicsContext2D(), 240.0, 160.0, 0.0);
            WritableImage updated = updatedCanvas.snapshot(null, null);

            assertFalse(hasColor(RenderPalette.SPAWN_CORE, updated.getPixelReader().getColor(48, 48)),
                    "旧出生点中心不应在更新变换后保留");
            assertColorClose(RenderPalette.SPAWN_CORE, updated.getPixelReader().getColor(124, 44));
        });
    }

    private static boolean hasColor(Color expected, Color actual) {
        double tolerance = 1.0 / 255.0;
        return Math.abs(expected.getRed() - actual.getRed()) <= tolerance
                && Math.abs(expected.getGreen() - actual.getGreen()) <= tolerance
                && Math.abs(expected.getBlue() - actual.getBlue()) <= tolerance
                && Math.abs(expected.getOpacity() - actual.getOpacity()) <= tolerance;
    }

    private static void assertColorClose(Color expected, Color actual) {
        double tolerance = 1.0 / 255.0;
        assertEquals(expected.getRed(), actual.getRed(), tolerance, "red");
        assertEquals(expected.getGreen(), actual.getGreen(), tolerance, "green");
        assertEquals(expected.getBlue(), actual.getBlue(), tolerance, "blue");
        assertEquals(expected.getOpacity(), actual.getOpacity(), tolerance, "opacity");
    }

    private static void onFxThread(FxAssertion assertion) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        Platform.runLater(() -> {
            try {
                Canvas canvas = new Canvas(240.0, 160.0);
                assertion.run(canvas, canvas.getGraphicsContext2D());
            } catch (Throwable throwable) {
                failure[0] = throwable;
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(10, TimeUnit.SECONDS), "JavaFX 静态图层离屏渲染超时");
        if (failure[0] != null) {
            throw new AssertionError("静态图层动态变换验证失败", failure[0]);
        }
    }

    private static void ensureFxStarted() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // 同一测试 JVM 中已初始化 Toolkit，直接复用。
        }
    }

    @FunctionalInterface
    private interface FxAssertion {
        void run(Canvas canvas, GraphicsContext graphics) throws Exception;
    }
}
