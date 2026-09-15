package org.example.timeloop.render;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.WritableImage;
import org.example.timeloop.core.Direction;
import org.example.timeloop.core.MovementState;
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

/** R2 代际标识与 R5 消散投影的离屏 Canvas 回归。 */
class EchoLifecycleLayerTest {

    private static final int WIDTH = 240;
    private static final int HEIGHT = 160;

    @BeforeAll
    static void initializeJavafx() {
        ensureFxStarted();
    }

    @Test
    void readsEachDynamicSourceOnceAndSkipsGoneEchoes() throws Exception {
        onFxThread(() -> {
            AtomicInteger frameReads = new AtomicInteger();
            AtomicInteger lifecycleReads = new AtomicInteger();
            EchoLifecycleLayer layer = new EchoLifecycleLayer(() -> {
                frameReads.incrementAndGet();
                return frame(echo(1, false, 48.0, 48.0), echo(2, true, 96.0, 48.0));
            }, () -> {
                lifecycleReads.incrementAndGet();
                return List.of(
                        new EchoLifecycleVisual(1, true, EchoVisualPhase.LAST_EFFECTIVE_ROUND, 0.0),
                        new EchoLifecycleVisual(2, false, EchoVisualPhase.GONE, 1.0));
            }, WorldTransform.identity());

            Canvas canvas = new Canvas(WIDTH, HEIGHT);
            layer.render(canvas.getGraphicsContext2D(), WIDTH, HEIGHT, 0.0);
            WritableImage image = canvas.snapshot(null, null);
            int background = image.getPixelReader().getArgb(0, 0);

            assertEquals(1, frameReads.get(), "一帧只能读取一次 Frame Supplier");
            assertEquals(1, lifecycleReads.get(), "一帧只能读取一次生命周期 Supplier");
            assertTrue(image.getPixelReader().getArgb(46, 32) != background,
                    "E1 应显示代际标签和最后有效轮外框");
            assertFalse(image.getPixelReader().getArgb(98, 32) != background,
                    "GONE 残影不得继续绘制代际标签");
        });
    }

    @Test
    void keepsBadgeSizeInScreenPixelsWhileReprojectingTrailAnchor() throws Exception {
        onFxThread(() -> {
            AtomicReference<WorldTransform> transform = new AtomicReference<>(WorldTransform.identity());
            EchoLifecycleLayer layer = new EchoLifecycleLayer(
                    () -> frame(echo(1, false, 48.0, 48.0)),
                    () -> List.of(new EchoLifecycleVisual(1, false, EchoVisualPhase.ACTIVE, 0.0)), transform::get);

            Canvas firstCanvas = new Canvas(WIDTH, HEIGHT);
            layer.render(firstCanvas.getGraphicsContext2D(), WIDTH, HEIGHT, 0.0);
            WritableImage first = firstCanvas.snapshot(null, null);
            int background = first.getPixelReader().getArgb(0, 0);
            assertTrue(first.getPixelReader().getArgb(48, 32) != background,
                    "初始 E1 标签应锚定在世界点上方 16px");

            transform.set(new WorldTransform(0.5, 100.0, 20.0));
            Canvas updatedCanvas = new Canvas(WIDTH, HEIGHT);
            layer.render(updatedCanvas.getGraphicsContext2D(), WIDTH, HEIGHT, 0.0);
            WritableImage updated = updatedCanvas.snapshot(null, null);
            int updatedBackground = updated.getPixelReader().getArgb(0, 0);

            assertTrue(updated.getPixelReader().getArgb(122, 28) != updatedBackground,
                    "缩放后标签应跟随锚点到 (122,28) 附近");
            assertFalse(updated.getPixelReader().getArgb(48, 32) != updatedBackground,
                    "动态变换后不得保留旧标签位置");
        });
    }

    @Test
    void dissipatingEchoUsesInjectedProgressForFadeAndFragments() throws Exception {
        onFxThread(() -> {
            Canvas halfProgressCanvas = new Canvas(WIDTH, HEIGHT);
            new EchoLifecycleLayer(
                    () -> frame(echo(1, true, 80.0, 60.0)),
                    () -> List.of(new EchoLifecycleVisual(1, false, EchoVisualPhase.DISSIPATING, 0.5)),
                    WorldTransform.identity())
                    .render(halfProgressCanvas.getGraphicsContext2D(), WIDTH, HEIGHT, 0.0);
            WritableImage halfProgress = halfProgressCanvas.snapshot(null, null);
            int background = halfProgress.getPixelReader().getArgb(0, 0);

            assertTrue(halfProgress.getPixelReader().getArgb(80, 44) != background,
                    "DISSIPATING 的中途进度仍应保留淡出的代际标签");
            assertTrue(halfProgress.getPixelReader().getArgb(93, 44) != background,
                    "effectProgress=0.5 应在标签中心外绘制扩散碎片");

            Canvas completedCanvas = new Canvas(WIDTH, HEIGHT);
            new EchoLifecycleLayer(
                    () -> frame(echo(1, true, 80.0, 60.0)),
                    () -> List.of(new EchoLifecycleVisual(1, false, EchoVisualPhase.DISSIPATING, 1.0)),
                    WorldTransform.identity())
                    .render(completedCanvas.getGraphicsContext2D(), WIDTH, HEIGHT, 0.0);
            WritableImage completed = completedCanvas.snapshot(null, null);
            int completedBackground = completed.getPixelReader().getArgb(0, 0);
            assertFalse(completed.getPixelReader().getArgb(80, 44) != completedBackground,
                    "进度由上游给到 1 时，render 不得自行保留标签或创建额外计时");
        });
    }

    @Test
    void dissipationFragmentsReprojectTheirWorldAnchorWithoutScalingScreenEffect() throws Exception {
        onFxThread(() -> {
            AtomicReference<WorldTransform> transform = new AtomicReference<>(WorldTransform.identity());
            EchoLifecycleLayer layer = new EchoLifecycleLayer(
                    () -> frame(echo(1, true, 48.0, 48.0)),
                    () -> List.of(new EchoLifecycleVisual(1, false, EchoVisualPhase.DISSIPATING, 0.5)), transform::get);

            Canvas firstCanvas = new Canvas(WIDTH, HEIGHT);
            layer.render(firstCanvas.getGraphicsContext2D(), WIDTH, HEIGHT, 0.0);
            WritableImage first = firstCanvas.snapshot(null, null);
            int firstBackground = first.getPixelReader().getArgb(0, 0);
            assertTrue(first.getPixelReader().getArgb(61, 32) != firstBackground,
                    "初始碎片应锚定于世界点 (48,48) 上方的标签中心");

            transform.set(new WorldTransform(0.5, 100.0, 20.0));
            Canvas updatedCanvas = new Canvas(WIDTH, HEIGHT);
            layer.render(updatedCanvas.getGraphicsContext2D(), WIDTH, HEIGHT, 0.0);
            WritableImage updated = updatedCanvas.snapshot(null, null);
            int updatedBackground = updated.getPixelReader().getArgb(0, 0);

            assertTrue(updated.getPixelReader().getArgb(137, 28) != updatedBackground,
                    "缩放后碎片应随标签中心投影到 (124,28) 附近，扩散距离仍为屏幕像素");
            assertFalse(updated.getPixelReader().getArgb(61, 32) != updatedBackground,
                    "动态变换后不得遗留旧世界投影位置的消散碎片");
        });
    }

    private static RenderViews.Frame frame(RenderViews.EchoTrail... echoes) {
        return new RenderViews.Frame(
                new RenderViews.Player(0.0, 0.0, Direction.DOWN, MovementState.IDLE, false),
                List.of(), List.of(echoes));
    }

    private static RenderViews.EchoTrail echo(int sourceRound, boolean newer, double x, double y) {
        return new RenderViews.EchoTrail(sourceRound,
                List.of(new Vector2D(x, y), new Vector2D(x + 20.0, y)), newer);
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

        assertTrue(done.await(10, TimeUnit.SECONDS), "JavaFX 残影生命周期离屏渲染超时");
        if (failure[0] != null) {
            throw new AssertionError("残影生命周期图层验证失败", failure[0]);
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
