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

/** L2-B 相位下潜的角色外观离屏回归。 */
class PlayerLayerPhaseVisualTest {

    private static final int CANVAS_SIZE = 160;
    private static final double CENTER = CANVAS_SIZE / 2.0;

    @BeforeAll
    static void initializeJavafx() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // 同一测试 JVM 中已初始化 Toolkit，直接复用。
        }
    }

    @Test
    void phasedPlayerHasDistinctCompressedTranslucentRaster() throws Exception {
        onFxThread(() -> {
            int[] normal = render(false);
            int[] phased = render(true);

            assertTrue(hasVisiblePixels(normal), "普通玩家必须可见");
            assertTrue(hasVisiblePixels(phased), "PHASED 玩家必须可见，不能完全隐身");
            assertFalse(Arrays.equals(normal, phased),
                    "PHASED 必须通过压低身体、半透明和脚下圆环形成非纯颜色差异");
        });
    }

    private static int[] render(boolean phased) {
        Canvas canvas = new Canvas(CANVAS_SIZE, CANVAS_SIZE);
        RenderViews.Frame frame = new RenderViews.Frame(
                new RenderViews.Player(CENTER, CENTER, Direction.RIGHT,
                        MovementState.CRUISING, phased),
                List.of(),
                List.of());
        new PlayerLayer(() -> frame, 48.0, WorldTransform.identity())
                .render(canvas.getGraphicsContext2D(), CANVAS_SIZE, CANVAS_SIZE, 0.0);

        WritableImage image = canvas.snapshot(null, null);
        int[] pixels = new int[CANVAS_SIZE * CANVAS_SIZE];
        image.getPixelReader().getPixels(0, 0, CANVAS_SIZE, CANVAS_SIZE,
                PixelFormat.getIntArgbInstance(), pixels, 0, CANVAS_SIZE);
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

        assertTrue(done.await(10, TimeUnit.SECONDS), "JavaFX PHASED 离屏渲染超时");
        if (failure[0] != null) {
            throw new AssertionError("PHASED 角色外观验证失败", failure[0]);
        }
    }

    @FunctionalInterface
    private interface FxAssertion {
        void run() throws Exception;
    }
}
