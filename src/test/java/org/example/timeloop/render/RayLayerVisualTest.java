package org.example.timeloop.render;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import org.example.timeloop.core.Direction;
import org.example.timeloop.core.MovementState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** OFF/WARNING/ACTIVE 的离屏结构可辨回归，不绑定具体 RGB。 */
class RayLayerVisualTest {

    private static final int SIZE = 120;

    @BeforeAll
    static void initializeJavafx() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // 同一测试 JVM 中已初始化 Toolkit。
        }
    }

    @Test
    void allThreeStatesRemainVisibleAndStructurallyDistinct() throws Exception {
        onFxThread(() -> {
            int[] off = render(RenderViews.RayVisualState.OFF);
            int[] warning = render(RenderViews.RayVisualState.WARNING);
            int[] active = render(RenderViews.RayVisualState.ACTIVE);

            assertTrue(hasVisiblePixels(off), "OFF 应保留低亮度导轨");
            assertTrue(hasVisiblePixels(warning), "WARNING 应显示预警虚线");
            assertTrue(hasVisiblePixels(active), "ACTIVE 应显示实线光束");
            assertFalse(Arrays.equals(off, warning), "OFF 与 WARNING 必须可区分");
            assertFalse(Arrays.equals(warning, active), "WARNING 与 ACTIVE 必须可区分");
            assertFalse(Arrays.equals(off, active), "OFF 与 ACTIVE 必须可区分");
        });
    }

    private static int[] render(RenderViews.RayVisualState state) {
        RenderViews.Player player = new RenderViews.Player(
                0.0, 0.0, Direction.DOWN, MovementState.IDLE, false);
        RenderViews.RayBeam beam = new RenderViews.RayBeam("ray", 20.0, 60.0, 100.0, 60.0, state);
        RenderViews.Frame frame = new RenderViews.Frame(player, List.of(), List.of(), List.of(beam));
        Canvas canvas = new Canvas(SIZE, SIZE);
        new RayLayer(() -> frame, WorldTransform.identity())
                .render(canvas.getGraphicsContext2D(), SIZE, SIZE, 0.0);

        WritableImage image = canvas.snapshot(null, null);
        int[] pixels = new int[SIZE * SIZE];
        image.getPixelReader().getPixels(0, 0, SIZE, SIZE,
                PixelFormat.getIntArgbInstance(), pixels, 0, SIZE);
        return pixels;
    }

    private static boolean hasVisiblePixels(int[] pixels) {
        return Arrays.stream(pixels).anyMatch(argb -> (argb >>> 24) != 0);
    }

    private static void onFxThread(FxAssertion assertion) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        Platform.runLater(() -> {
            try {
                assertion.run();
            } catch (Throwable throwable) {
                failure[0] = throwable;
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(10, TimeUnit.SECONDS), "JavaFX 射线离屏渲染超时");
        if (failure[0] != null) {
            throw new AssertionError("射线三态视觉验证失败", failure[0]);
        }
    }

    @FunctionalInterface
    private interface FxAssertion {
        void run() throws Exception;
    }
}
