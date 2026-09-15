package org.example.timeloop.render;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.WritableImage;
import org.example.timeloop.level.model.Vector2D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** R1 三路线及 J 分岔的离屏可读性和动态投影回归。 */
class RouteReadabilityLayerTest {

    private static final int WIDTH = 240;
    private static final int HEIGHT = 160;

    @BeforeAll
    static void initializeJavafx() {
        ensureFxStarted();
    }

    @Test
    void keepsAConstructionTimeRouteSnapshotAndUsesDistinctLineGeometries() throws Exception {
        onFxThread(() -> {
            List<RouteVisual> source = new ArrayList<>(List.of(
                    route("control", RouteKind.CONTROL_E1, 20.0),
                    route("branch", RouteKind.BRANCH_E2, 60.0),
                    route("final", RouteKind.FINAL_PLAYER, 100.0)));
            RouteReadabilityLayer layer = new RouteReadabilityLayer(source, List.of(), WorldTransform.identity());
            source.clear();

            Canvas canvas = new Canvas(WIDTH, HEIGHT);
            layer.render(canvas.getGraphicsContext2D(), WIDTH, HEIGHT, 0.0);
            WritableImage image = canvas.snapshot(null, null);
            int background = image.getPixelReader().getArgb(0, 0);

            assertTrue(paintedPixels(image, 18, 15, 102, 25, background) > 0,
                    "构造后清空调用方列表也不得移除控制路线");
            assertTrue(paintedPixels(image, 18, 54, 24, 66, background) > 0,
                    "B 支路端点应带方形刻痕");
            assertTrue(paintedPixels(image, 45, 97, 95, 98, background) > 0
                            && paintedPixels(image, 45, 102, 95, 103, background) > 0,
                    "最终主通道应绘制上下两条轨道，而非单色单线");
        });
    }

    @Test
    void reprojectsRouteAndJunctionWorldCoordinatesWithoutScalingGlyphs() throws Exception {
        onFxThread(() -> {
            AtomicReference<WorldTransform> transform = new AtomicReference<>(WorldTransform.identity());
            RouteReadabilityLayer layer = new RouteReadabilityLayer(
                    List.of(new RouteVisual("branch", RouteKind.BRANCH_E2,
                            List.of(new Vector2D(48.0, 48.0), new Vector2D(96.0, 48.0)), "J")),
                    List.of(new RouteJunctionVisual("J", new Vector2D(48.0, 48.0))), transform::get);

            Canvas firstCanvas = new Canvas(WIDTH, HEIGHT);
            layer.render(firstCanvas.getGraphicsContext2D(), WIDTH, HEIGHT, 0.0);
            WritableImage first = firstCanvas.snapshot(null, null);
            int firstBackground = first.getPixelReader().getArgb(0, 0);
            assertTrue(first.getPixelReader().getArgb(48, 43) != firstBackground,
                    "初始 J 应在世界坐标 (48,48) 处显示固定像素大小的菱形");

            transform.set(new WorldTransform(0.5, 100.0, 20.0));
            Canvas updatedCanvas = new Canvas(WIDTH, HEIGHT);
            layer.render(updatedCanvas.getGraphicsContext2D(), WIDTH, HEIGHT, 0.0);
            WritableImage updated = updatedCanvas.snapshot(null, null);
            int updatedBackground = updated.getPixelReader().getArgb(0, 0);

            assertTrue(updated.getPixelReader().getArgb(124, 39) != updatedBackground,
                    "缩放后 J 中心应投影到 (124,44)，菱形保持 5px 半径");
            assertFalse(updated.getPixelReader().getArgb(48, 43) != updatedBackground,
                    "动态变换后不得继续绘制旧 J 位置");
        });
    }

    private static RouteVisual route(String id, RouteKind kind, double y) {
        return new RouteVisual(id, kind,
                List.of(new Vector2D(20.0, y), new Vector2D(100.0, y)), kind == RouteKind.CONTROL_E1 ? "" : "J");
    }

    private static int paintedPixels(WritableImage image, int minX, int minY, int maxX, int maxY, int background) {
        int count = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (image.getPixelReader().getArgb(x, y) != background) {
                    count++;
                }
            }
        }
        return count;
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

        assertTrue(done.await(10, TimeUnit.SECONDS), "JavaFX 路线离屏渲染超时");
        if (failure[0] != null) {
            throw new AssertionError("路线可读性图层验证失败", failure[0]);
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
        void run() throws Exception;
    }
}
