package org.example.timeloop.render;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
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

/** 闸门合并后的开关、驻留板与闸门终点外观离屏回归。 */
class MechanismLayerGateMergeVisualTest {

    private static final double CANVAS_SIZE = 160.0;
    private static final double TILE_SIZE = 48.0;
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
    void switchOffAndLatchedOnHaveDistinctRasters() throws Exception {
        onFxThread(() -> {
            int[] off = render(RenderViews.MechanismKind.SWITCH, false);
            int[] on = render(RenderViews.MechanismKind.SWITCH, true);

            assertTrue(hasVisiblePixels(off), "开关 OFF 必须有可见的踏板外形");
            assertTrue(hasVisiblePixels(on), "开关锁存 ON 必须有可见的踏板外形");
            assertFalse(Arrays.equals(off, on), "锁存 ON 必须表现为压下并亮起的持续状态，而非站立态");
        });
    }

    @Test
    void switchAndPlateAreStructurallyDistinct() throws Exception {
        onFxThread(() -> {
            int[] switchOn = render(RenderViews.MechanismKind.SWITCH, true);
            int[] plateOn = render(RenderViews.MechanismKind.PLATE, true);

            assertFalse(Arrays.equals(switchOn, plateOn),
                    "开关必须以形状与颜色通道区别于驻留板，不能读成第二块板");
        });
    }

    @Test
    void closedAndOpenGateExitHaveDistinctRasters() throws Exception {
        onFxThread(() -> {
            int[] closed = render(RenderViews.MechanismKind.EXIT, false);
            int[] open = render(RenderViews.MechanismKind.EXIT, true);

            assertTrue(hasVisiblePixels(closed), "关闭闸门必须有可见的实体门栅");
            assertTrue(hasVisiblePixels(open), "打开闸门必须有可见的门框与通行状态");
            assertFalse(Arrays.equals(closed, open), "EXIT.active 的关闭/打开两态必须具有不同结构与颜色通道");
        });
    }

    private static int[] render(RenderViews.MechanismKind kind, boolean active) {
        Canvas canvas = new Canvas(CANVAS_SIZE, CANVAS_SIZE);
        RenderViews.Frame frame = new RenderViews.Frame(
                new RenderViews.Player(0.0, 0.0, Direction.DOWN, MovementState.IDLE, false),
                List.of(new RenderViews.Mechanism("fixture", CENTER, CENTER, kind, active)),
                List.of());
        new MechanismLayer(() -> frame, TILE_SIZE, WorldTransform.identity())
                .render(canvas.getGraphicsContext2D(), CANVAS_SIZE, CANVAS_SIZE, 0.0);

        WritableImage image = canvas.snapshot(null, null);
        int[] pixels = new int[(int) CANVAS_SIZE * (int) CANVAS_SIZE];
        image.getPixelReader().getPixels(0, 0, (int) CANVAS_SIZE, (int) CANVAS_SIZE,
                javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels, 0, (int) CANVAS_SIZE);
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

        assertTrue(done.await(10, TimeUnit.SECONDS), "JavaFX 机关外观离屏渲染超时");
        if (failure[0] != null) {
            throw new AssertionError("机关外观离屏渲染失败", failure[0]);
        }
    }

    @FunctionalInterface
    private interface FxAssertion {
        void run() throws Exception;
    }
}
