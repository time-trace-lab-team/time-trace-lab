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

import static org.junit.jupiter.api.Assertions.assertTrue;

/** L02-A 双残影在同刻完全重合时的非纯颜色可读性回归。 */
class EchoTrailLayerReadabilityTest {

    private static final int CANVAS_WIDTH = 180;
    private static final int CANVAS_HEIGHT = 120;
    private static final int TRAIL_Y = 60;

    @BeforeAll
    static void initializeJavafx() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // 同一测试 JVM 中已初始化 Toolkit，直接复用。
        }
    }

    @Test
    void overlappingGenerationsRemainDistinctByOffsetAndLinePattern() throws Exception {
        onFxThread(() -> {
            List<Vector2D> samePath = List.of(
                    new Vector2D(30.0, TRAIL_Y),
                    new Vector2D(150.0, TRAIL_Y));
            RenderViews.Frame frame = new RenderViews.Frame(
                    new RenderViews.Player(0.0, 0.0, Direction.RIGHT, MovementState.IDLE, false),
                    List.of(),
                    List.of(
                            new RenderViews.EchoTrail(2, samePath, true),
                            new RenderViews.EchoTrail(1, samePath, false)));

            Canvas canvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
            new EchoTrailLayer(() -> frame, WorldTransform.identity())
                    .render(canvas.getGraphicsContext2D(), CANVAS_WIDTH, CANVAS_HEIGHT, 0.0);
            WritableImage image = canvas.snapshot(null, null);
            int background = image.getPixelReader().getArgb(0, 0);

            RowPattern newer = rowPattern(image, TRAIL_Y + 2, background);
            RowPattern older = rowPattern(image, TRAIL_Y - 2, background);
            RowPattern center = rowPattern(image, TRAIL_Y, background);

            assertTrue(newer.paintedPixels() >= 110,
                    "较新残影应为连续实线，不能只有零散像素");
            assertTrue(older.paintedPixels() > 0 && older.paintedPixels() < newer.paintedPixels(),
                    "较旧残影应有可见虚线段，且空隙使其少于新残影实线");
            assertTrue(older.backgroundGaps() >= 3,
                    "较旧残影必须存在多个虚线空隙，不能只靠颜色区分");
            assertTrue(center.paintedPixels() == 0,
                    "两代轨迹应分居原路径两侧，中线不应被任一条覆盖");
        });
    }

    private static RowPattern rowPattern(WritableImage image, int y, int background) {
        int painted = 0;
        int gaps = 0;
        boolean inPaint = false;
        for (int x = 24; x <= 156; x++) {
            boolean currentPaint = image.getPixelReader().getArgb(x, y) != background;
            if (currentPaint) {
                painted++;
            } else if (inPaint) {
                gaps++;
            }
            inPaint = currentPaint;
        }
        return new RowPattern(painted, gaps);
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

        assertTrue(done.await(10, TimeUnit.SECONDS), "JavaFX 双残影离屏渲染超时");
        if (failure[0] != null) {
            throw new AssertionError("双残影重合可读性验证失败", failure[0]);
        }
    }

    private record RowPattern(int paintedPixels, int backgroundGaps) {
    }

    @FunctionalInterface
    private interface FxAssertion {
        void run() throws Exception;
    }
}
