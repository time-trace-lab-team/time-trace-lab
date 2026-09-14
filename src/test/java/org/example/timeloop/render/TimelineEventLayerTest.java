package org.example.timeloop.render;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.WritableImage;
import org.example.timeloop.level.model.Vector2D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** R3 时间刻度与驻留/离开节点的离屏 Canvas 回归。 */
class TimelineEventLayerTest {

    private static final int WIDTH = 240;
    private static final int HEIGHT = 160;

    @BeforeAll
    static void initializeJavafx() {
        ensureFxStarted();
    }

    @Test
    void readsOneImmutableEventSnapshotPerFrame() throws Exception {
        onFxThread(() -> {
            AtomicInteger reads = new AtomicInteger();
            TimelineEventLayer layer = new TimelineEventLayer(() -> {
                reads.incrementAndGet();
                return List.of(event(TimelineEventKind.TICK_MARK, 48.0, 48.0));
            }, WorldTransform.identity());

            Canvas canvas = new Canvas(WIDTH, HEIGHT);
            layer.render(canvas.getGraphicsContext2D(), WIDTH, HEIGHT, 0.0);

            assertEquals(1, reads.get(), "一个 render 帧只能读取一次事件 Supplier");
        });
    }

    @Test
    void tickEnterAndLeaveRemainReadableWithoutColorOnlyEncoding() throws Exception {
        onFxThread(() -> {
            Canvas canvas = new Canvas(WIDTH, HEIGHT);
            TimelineEventLayer layer = new TimelineEventLayer(() -> List.of(
                    event(TimelineEventKind.TICK_MARK, 40.0, 50.0),
                    event(TimelineEventKind.DOCK_ENTER, 80.0, 50.0),
                    event(TimelineEventKind.DOCK_LEAVE, 120.0, 50.0)), WorldTransform.identity());

            layer.render(canvas.getGraphicsContext2D(), WIDTH, HEIGHT, 0.0);
            WritableImage image = canvas.snapshot(null, null);
            int background = image.getPixelReader().getArgb(0, 0);

            assertTrue(paintedPixels(image, 36, 44, 44, 56, background) > 0,
                    "TICK_MARK 应绘制短刻痕");
            assertEquals(9, paintedPixels(image, 79, 49, 81, 51, background),
                    "进入节点的中心 3x3 区域应完整填充");
            assertTrue(paintedPixels(image, 119, 49, 121, 51, background) < 9,
                    "离开节点中心只应由斜线穿过，而非实心填充");
            assertTrue(image.getPixelReader().getArgb(80, 50) != background,
                    "DOCK_ENTER 中心应填充");
            assertTrue(image.getPixelReader().getArgb(120, 50) != background,
                    "DOCK_LEAVE 中心应由斜线标记");
        });
    }

    @Test
    void reprojectsWorldCentersWhileKeepingGlyphsInScreenPixels() throws Exception {
        onFxThread(() -> {
            AtomicReference<WorldTransform> transform = new AtomicReference<>(WorldTransform.identity());
            TimelineEventLayer layer = new TimelineEventLayer(
                    () -> List.of(event(TimelineEventKind.DOCK_ENTER, 48.0, 48.0)), transform::get);

            Canvas firstCanvas = new Canvas(WIDTH, HEIGHT);
            layer.render(firstCanvas.getGraphicsContext2D(), WIDTH, HEIGHT, 0.0);
            WritableImage first = firstCanvas.snapshot(null, null);
            int firstBackground = first.getPixelReader().getArgb(0, 0);
            assertTrue(first.getPixelReader().getArgb(48, 43) != firstBackground,
                    "初始世界坐标应投影到 (48,48)，并保留 5px 菱形半径");

            transform.set(new WorldTransform(0.5, 100.0, 20.0));
            Canvas updatedCanvas = new Canvas(WIDTH, HEIGHT);
            layer.render(updatedCanvas.getGraphicsContext2D(), WIDTH, HEIGHT, 0.0);
            WritableImage updated = updatedCanvas.snapshot(null, null);
            int updatedBackground = updated.getPixelReader().getArgb(0, 0);

            assertTrue(updated.getPixelReader().getArgb(124, 39) != updatedBackground,
                    "缩放后中心应投影到 (124,44)，图形仍保持 5px 屏幕半径");
            assertFalse(updated.getPixelReader().getArgb(48, 43) != updatedBackground,
                    "动态变换后不得继续绘制在旧位置");
            assertEquals(updatedBackground, updated.getPixelReader().getArgb(124, 38),
                    "菱形尺寸不得随世界 scale 改为 10px 或其他值");
        });
    }

    @Test
    void drawsRecordedRayDelayAsHourglassAndPreservesLaterDockStyle() throws Exception {
        onFxThread(() -> {
            Canvas canvas = new Canvas(WIDTH, HEIGHT);
            TimelineEventLayer layer = new TimelineEventLayer(() -> List.of(
                    new TimelineVisualEvent(1, 60, new Vector2D(80.0, 60.0), TimelineEventKind.RAY_DELAY, 30),
                    event(TimelineEventKind.DOCK_ENTER, 120.0, 60.0)), WorldTransform.identity());

            layer.render(canvas.getGraphicsContext2D(), WIDTH, HEIGHT, 0.0);
            WritableImage image = canvas.snapshot(null, null);
            int background = image.getPixelReader().getArgb(0, 0);

            assertTrue(paintedPixels(image, 73, 53, 87, 67, background) > 0,
                    "RAY_DELAY 应绘制非颜色专属的交叉沙漏标记");
            assertTrue(image.getPixelReader().getArgb(120, 60) != background,
                    "射线事件后续的 DOCK_ENTER 仍必须按原来的实心菱形绘制");
            assertEquals(RenderPalette.INTERACTIVE.toString(),
                    image.getPixelReader().getColor(120, 60).toString(),
                    "RAY_DELAY 不能把射线颜色泄漏到后续驻留节点");
        });
    }

    @Test
    void rayDelayReprojectsItsWorldPositionWhenViewportTransformChanges() throws Exception {
        onFxThread(() -> {
            AtomicReference<WorldTransform> transform = new AtomicReference<>(WorldTransform.identity());
            TimelineEventLayer layer = new TimelineEventLayer(
                    () -> List.of(new TimelineVisualEvent(1, 60,
                            new Vector2D(48.0, 48.0), TimelineEventKind.RAY_DELAY, 0)), transform::get);

            Canvas firstCanvas = new Canvas(WIDTH, HEIGHT);
            layer.render(firstCanvas.getGraphicsContext2D(), WIDTH, HEIGHT, 0.0);
            WritableImage first = firstCanvas.snapshot(null, null);
            int firstBackground = first.getPixelReader().getArgb(0, 0);
            assertTrue(paintedPixels(first, 40, 40, 56, 56, firstBackground) > 0,
                    "初始 RAY_DELAY 应绘制在世界坐标 (48,48) 附近");

            transform.set(new WorldTransform(0.5, 100.0, 20.0));
            Canvas updatedCanvas = new Canvas(WIDTH, HEIGHT);
            layer.render(updatedCanvas.getGraphicsContext2D(), WIDTH, HEIGHT, 0.0);
            WritableImage updated = updatedCanvas.snapshot(null, null);
            int updatedBackground = updated.getPixelReader().getArgb(0, 0);

            assertTrue(paintedPixels(updated, 116, 36, 132, 52, updatedBackground) > 0,
                    "缩放后 RAY_DELAY 应投影到世界点 (124,44) 附近");
            assertEquals(0, paintedPixels(updated, 40, 40, 56, 56, updatedBackground),
                    "动态变换后不得遗留旧位置的 RAY_DELAY");
        });
    }

    private static TimelineVisualEvent event(TimelineEventKind kind, double x, double y) {
        return new TimelineVisualEvent(1, 60, new Vector2D(x, y), kind, 0);
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

        assertTrue(done.await(10, TimeUnit.SECONDS), "JavaFX 时间线事件离屏渲染超时");
        if (failure[0] != null) {
            throw new AssertionError("时间线事件图层验证失败", failure[0]);
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
