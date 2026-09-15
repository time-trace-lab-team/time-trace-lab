package org.example.timeloop.render;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.example.timeloop.core.Direction;
import org.example.timeloop.core.MovementState;
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

/** 机关和当前玩家图层对动态世界变换的离屏像素验证。 */
class ActorLayerTransformTest {

    private static final WorldTransform INITIAL = WorldTransform.identity();
    private static final WorldTransform UPDATED = new WorldTransform(0.5, 100.0, 20.0);

    @BeforeAll
    static void initializeJavafx() {
        ensureFxStarted();
    }

    @Test
    void mechanismMarkerUsesUpdatedCenterAndWorldScaledSize() throws Exception {
        onFxThread((canvas, graphics) -> {
            AtomicReference<WorldTransform> transform = new AtomicReference<>(INITIAL);
            MechanismLayer layer = new MechanismLayer(mechanismFrame(), 48.0, transform::get);

            layer.render(graphics, 240.0, 160.0, 0.0);
            WritableImage first = canvas.snapshot(null, null);
            assertColorClose(RenderPalette.PLATE, first.getPixelReader().getColor(48, 60));

            transform.set(UPDATED);
            Canvas updatedCanvas = new Canvas(240.0, 160.0);
            layer.render(updatedCanvas.getGraphicsContext2D(), 240.0, 160.0, 0.0);
            WritableImage updated = updatedCanvas.snapshot(null, null);

            assertColorClose(RenderPalette.PLATE, updated.getPixelReader().getColor(124, 44));
            assertFalse(hasColor(RenderPalette.PLATE, updated.getPixelReader().getColor(48, 60)),
                    "更新变换后旧机关中心不得保留");
            assertFalse(hasColor(RenderPalette.PLATE, updated.getPixelReader().getColor(124, 56)),
                    "机关主体必须随 scale 从原尺寸缩小");
        });
    }

    @Test
    void playerBodyUsesUpdatedCenterAndWorldScaledSize() throws Exception {
        onFxThread((canvas, graphics) -> {
            AtomicReference<WorldTransform> transform = new AtomicReference<>(INITIAL);
            PlayerLayer layer = new PlayerLayer(playerFrame(), 48.0, transform::get);

            layer.render(graphics, 240.0, 160.0, 0.0);
            WritableImage first = canvas.snapshot(null, null);
            assertColorClose(RenderPalette.PLAYER, first.getPixelReader().getColor(48, 58));

            transform.set(UPDATED);
            Canvas updatedCanvas = new Canvas(240.0, 160.0);
            layer.render(updatedCanvas.getGraphicsContext2D(), 240.0, 160.0, 0.0);
            WritableImage updated = updatedCanvas.snapshot(null, null);

            assertColorClose(RenderPalette.PLAYER, updated.getPixelReader().getColor(124, 49));
            assertFalse(hasColor(RenderPalette.PLAYER, updated.getPixelReader().getColor(48, 58)),
                    "更新变换后旧玩家位置不得保留");
            assertFalse(hasColor(RenderPalette.PLAYER, updated.getPixelReader().getColor(124, 54)),
                    "玩家身体半径必须随 scale 缩小");
        });
    }

    private static Supplier<RenderViews.Frame> mechanismFrame() {
        return () -> new RenderViews.Frame(
                new RenderViews.Player(48.0, 48.0, Direction.RIGHT, MovementState.IDLE, false),
                List.of(new RenderViews.Mechanism("plate", 48.0, 48.0, RenderViews.MechanismKind.PLATE, true)),
                List.of());
    }

    private static Supplier<RenderViews.Frame> playerFrame() {
        return () -> new RenderViews.Frame(
                new RenderViews.Player(48.0, 48.0, Direction.RIGHT, MovementState.CRUISING, false),
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

        assertTrue(done.await(10, TimeUnit.SECONDS), "JavaFX actor 图层离屏渲染超时");
        if (failure[0] != null) {
            throw new AssertionError("actor 图层动态变换验证失败", failure[0]);
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
