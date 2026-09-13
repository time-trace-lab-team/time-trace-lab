package org.example.timeloop.render;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.example.timeloop.core.Direction;
import org.example.timeloop.core.MovementState;
import org.example.timeloop.level.model.Vector2D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 残影轨迹与路径节点提示对动态世界变换的离屏像素验证。 */
class OverlayLayerTransformTest {

    private static final WorldTransform INITIAL = WorldTransform.identity();
    private static final WorldTransform UPDATED = new WorldTransform(0.5, 100.0, 20.0);

    @BeforeAll
    static void initializeJavafx() {
        ensureFxStarted();
    }

    @Test
    void echoTrailReprojectsWorldPointsWhileKeepingTwoPixelOffset() throws Exception {
        onFxThread((canvas, graphics) -> {
            AtomicReference<WorldTransform> transform = new AtomicReference<>(INITIAL);
            EchoTrailLayer layer = new EchoTrailLayer(echoFrame(), transform::get);

            layer.render(graphics, 240.0, 160.0, 0.0);
            WritableImage first = canvas.snapshot(null, null);
            Color lineColor = first.getPixelReader().getColor(70, 50);
            assertTrue(lineColor.getGreen() > 0.4, "初始残影轨迹应绘制在世界点加 2px 偏移处");

            transform.set(UPDATED);
            Canvas updatedCanvas = new Canvas(240.0, 160.0);
            layer.render(updatedCanvas.getGraphicsContext2D(), 240.0, 160.0, 0.0);
            WritableImage updated = updatedCanvas.snapshot(null, null);

            assertColorClose(lineColor, updated.getPixelReader().getColor(136, 46));
            assertFalse(hasColor(lineColor, updated.getPixelReader().getColor(70, 50)),
                    "更新变换后旧轨迹位置不得保留");
            assertFalse(hasColor(lineColor, updated.getPixelReader().getColor(136, 44)),
                    "残影的 2px 平行偏移必须保持屏幕像素语义");
        });
    }

    @Test
    void pathNodeHintReprojectsCenterWhileKeepingEightPixelDiamond() throws Exception {
        onFxThread((canvas, graphics) -> {
            AtomicReference<WorldTransform> transform = new AtomicReference<>(INITIAL);
            PathNodeHintLayer layer = new PathNodeHintLayer(nodeFrame(),
                    List.of(new RenderViews.PathNodeMarker("node", 48.0, 48.0)), 48.0, transform::get);

            layer.render(graphics, 240.0, 160.0, 0.0);
            WritableImage first = canvas.snapshot(null, null);
            Color nodeColor = first.getPixelReader().getColor(48, 44);
            assertTrue(nodeColor.getRed() > 0.5, "初始节点菱形顶部应被绘制");

            transform.set(UPDATED);
            Canvas updatedCanvas = new Canvas(240.0, 160.0);
            layer.render(updatedCanvas.getGraphicsContext2D(), 240.0, 160.0, 0.0);
            WritableImage updated = updatedCanvas.snapshot(null, null);

            assertColorClose(nodeColor, updated.getPixelReader().getColor(124, 40));
            assertFalse(hasColor(nodeColor, updated.getPixelReader().getColor(48, 44)),
                    "更新变换后旧节点位置不得保留");
            assertFalse(hasColor(nodeColor, updated.getPixelReader().getColor(124, 36)),
                    "节点菱形应保持 8px，而非随 scale 缩小");
        });
    }

    private static Supplier<RenderViews.Frame> echoFrame() {
        return () -> new RenderViews.Frame(
                new RenderViews.Player(48.0, 48.0, Direction.RIGHT, MovementState.IDLE, false),
                List.of(),
                List.of(new RenderViews.EchoTrail(1,
                        List.of(new Vector2D(48.0, 48.0), new Vector2D(96.0, 48.0)), true)));
    }

    private static Supplier<RenderViews.Frame> nodeFrame() {
        return () -> new RenderViews.Frame(
                new RenderViews.Player(48.0, 48.0, Direction.RIGHT, MovementState.IDLE, false),
                List.of(),
                List.of());
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

        assertTrue(done.await(10, TimeUnit.SECONDS), "JavaFX overlay 图层离屏渲染超时");
        if (failure[0] != null) {
            throw new AssertionError("overlay 图层动态变换验证失败", failure[0]);
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
