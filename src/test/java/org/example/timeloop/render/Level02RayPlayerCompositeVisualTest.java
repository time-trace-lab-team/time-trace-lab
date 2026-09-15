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

/** L02-B 射线与玩家状态同屏时的离屏组合视觉回归。 */
class Level02RayPlayerCompositeVisualTest {

    private static final int SIZE = 160;
    private static final double CENTER = SIZE / 2.0;

    @BeforeAll
    static void initializeJavafx() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // 同一测试 JVM 中已初始化 Toolkit。
        }
    }

    @Test
    void phasedAndSlowedPlayerRemainsDistinctAboveActiveRay() throws Exception {
        onFxThread(() -> {
            int[] rayOnly = render(true, false, false);
            int[] combined = render(true, true, false);
            int[] reversed = render(true, true, true);

            assertTrue(hasVisiblePixels(combined), "ACTIVE 射线与玩家组合必须可见");
            assertFalse(Arrays.equals(rayOnly, combined),
                    "PHASED + SLOWED 玩家不能被 ACTIVE 射线吞没");
            assertFalse(Arrays.equals(combined, reversed),
                    "正式的射线后、玩家前图层顺序必须产生可辨的遮挡关系");
            assertTrue(hasPlayerCueOutsideRayBand(combined),
                    "相位环和减速轮廓应在射线带外留下非纯颜色形状提示");
        });
    }

    @Test
    void combinedPhaseAndSlowCuesDifferFromEitherStateAlone() throws Exception {
        onFxThread(() -> {
            int[] phasedOnly = render(false, true, false);
            int[] slowedOnly = render(true, false, false);
            int[] combined = render(true, true, false);

            assertFalse(Arrays.equals(phasedOnly, combined),
                    "组合态必须保留减速轮廓与拖尾");
            assertFalse(Arrays.equals(slowedOnly, combined),
                    "组合态必须保留相位压低、半透明与相位环");
        });
    }

    private static int[] render(boolean slowed, boolean phased, boolean reverseLayerOrder) {
        MovementState movement = slowed ? MovementState.SLOWED : MovementState.CRUISING;
        RenderViews.Player player = new RenderViews.Player(
                CENTER, CENTER, Direction.RIGHT, movement, phased);
        RenderViews.RayBeam beam = new RenderViews.RayBeam(
                "active-ray", 20.0, CENTER, SIZE - 20.0, CENTER,
                RenderViews.RayVisualState.ACTIVE);
        RenderViews.Frame frame = new RenderViews.Frame(player, List.of(), List.of(), List.of(beam));

        Canvas canvas = new Canvas(SIZE, SIZE);
        RayLayer rayLayer = new RayLayer(() -> frame, WorldTransform.identity());
        PlayerLayer playerLayer = new PlayerLayer(() -> frame, 48.0, WorldTransform.identity());
        if (reverseLayerOrder) {
            playerLayer.render(canvas.getGraphicsContext2D(), SIZE, SIZE, 0.0);
            rayLayer.render(canvas.getGraphicsContext2D(), SIZE, SIZE, 0.0);
        } else {
            rayLayer.render(canvas.getGraphicsContext2D(), SIZE, SIZE, 0.0);
            playerLayer.render(canvas.getGraphicsContext2D(), SIZE, SIZE, 0.0);
        }

        WritableImage image = canvas.snapshot(null, null);
        int[] pixels = new int[SIZE * SIZE];
        image.getPixelReader().getPixels(0, 0, SIZE, SIZE,
                PixelFormat.getIntArgbInstance(), pixels, 0, SIZE);
        return pixels;
    }

    private static boolean hasVisiblePixels(int[] pixels) {
        return Arrays.stream(pixels).anyMatch(argb -> (argb >>> 24) != 0);
    }

    private static boolean hasPlayerCueOutsideRayBand(int[] pixels) {
        int rayBandTop = (int) CENTER - 5;
        int rayBandBottom = (int) CENTER + 5;
        int playerAreaLeft = (int) CENTER - 32;
        int playerAreaRight = (int) CENTER + 32;
        int playerAreaTop = (int) CENTER - 32;
        int playerAreaBottom = (int) CENTER + 32;

        for (int y = playerAreaTop; y <= playerAreaBottom; y++) {
            if (y >= rayBandTop && y <= rayBandBottom) {
                continue;
            }
            for (int x = playerAreaLeft; x <= playerAreaRight; x++) {
                if ((pixels[y * SIZE + x] >>> 24) != 0) {
                    return true;
                }
            }
        }
        return false;
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

        assertTrue(done.await(10, TimeUnit.SECONDS), "JavaFX 射线/玩家组合离屏渲染超时");
        if (failure[0] != null) {
            throw new AssertionError("射线/玩家组合视觉验证失败", failure[0]);
        }
    }

    @FunctionalInterface
    private interface FxAssertion {
        void run() throws Exception;
    }
}
