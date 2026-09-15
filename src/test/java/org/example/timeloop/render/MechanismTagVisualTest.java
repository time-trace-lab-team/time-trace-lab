package org.example.timeloop.render;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.example.timeloop.core.Direction;
import org.example.timeloop.core.MovementState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第二关「闸链」改版的<b>色系分组 + 数字角标</b>离屏回归（设计说明 §四）。
 *
 * <p>{@link MechanismLayer} 现在读两个新增的只读分量：{@code Mechanism.gateGroup}（板取哪一组颜色）
 * 与 {@code Mechanism.tag}（是否画数字角标）。它们是纯显示信息，既不能用「有没有异常」验证，
 * 也不能用「projection 里字段对不对」验证 —— 唯一能证明「画面上真的分了两组、真的画了角标」的，
 * 就是对离屏 {@link Canvas} 快照逐像素比对。本测试因此按三条断言锁住：</p>
 *
 * <ol>
 *   <li>{@code gateGroup=true} 与 {@code false} 的板<b>栅格不同</b>，且板心色分别落在
 *       {@link RenderPalette#INTERACTIVE}（琥珀）与 {@link RenderPalette#PLATE}（蓝）两组上 ——
 *       取色如果被写死成一组，这条先红；</li>
 *   <li>{@code tag=null} 与 {@code tag="1"} 的板<b>栅格不同</b>（真的画了角标），
 *       且差异像素全部落在本格内 —— {@code drawTag} 如果没被调用，这条先红；</li>
 *   <li>门带 {@code tag} 时，角标（与无角标渲染的差异像素）同样<b>不越出本格</b> ——
 *       角标偏移若按格宽而不是按图标 {@code size} 算，这条先红。</li>
 * </ol>
 */
class MechanismTagVisualTest {

    private static final double CANVAS_SIZE = 160.0;
    private static final double TILE_SIZE = 48.0;
    private static final double CENTER = CANVAS_SIZE / 2.0;
    /** 本格半径：图标与角标都必须落在 {@code [CENTER ± TILE_SIZE/2]} 内。 */
    private static final double CELL_HALF = TILE_SIZE / 2.0;

    @BeforeAll
    static void initializeJavafx() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // 同一测试 JVM 中已初始化 Toolkit，直接复用。
        }
    }

    @Test
    void gateGroupPlatesUseASecondColourGroup() throws Exception {
        onFxThread(() -> {
            int[] plainGroup = render(plate(true, null, false));
            int[] gateGroup = render(plate(true, null, true));

            assertTrue(hasVisiblePixels(plainGroup), "开门组的板必须有可见外形");
            assertTrue(hasVisiblePixels(gateGroup), "终点闸组的板必须有可见外形");
            assertFalse(Arrays.equals(plainGroup, gateGroup),
                    "gateGroup=true / false 必须是两套取色：作用于终点闸的板不能与开门板画成同一块");

            Color plainCenter = colorAt(plainGroup, CENTER, CENTER);
            Color gateCenter = colorAt(gateGroup, CENTER, CENTER);
            assertTrue(distance(plainCenter, RenderPalette.PLATE)
                            < distance(plainCenter, RenderPalette.INTERACTIVE),
                    "gateGroup=false 的板心应取 RenderPalette.PLATE 蓝，实际=" + plainCenter);
            assertTrue(distance(gateCenter, RenderPalette.INTERACTIVE)
                            < distance(gateCenter, RenderPalette.PLATE),
                    "gateGroup=true 的板心应取 RenderPalette.INTERACTIVE 琥珀，实际=" + gateCenter);
        });
    }

    @Test
    void tagDrawsAVisibleBadgeInsideThePlateCell() throws Exception {
        onFxThread(() -> {
            int[] untagged = render(plate(true, null, true));
            int[] tagged = render(plate(true, "1", true));

            assertFalse(Arrays.equals(untagged, tagged),
                    "tag=\"1\" 的板必须与 tag=null 的板栅格不同（否则数字角标没画出来）");

            List<int[]> diff = differingPixels(untagged, tagged);
            assertFalse(diff.isEmpty(), "角标必须在栅格上留下差异像素");
            for (int[] pixel : diff) {
                assertWithinCell(pixel, "板的数字角标");
            }
        });
    }

    @Test
    void doorBadgeStaysInsideItsOwnCell() throws Exception {
        onFxThread(() -> {
            int[] untagged = render(door(true, null));
            int[] tagged = render(door(true, "1"));

            assertFalse(Arrays.equals(untagged, tagged),
                    "带 tag 的门必须与不带 tag 的门栅格不同（否则门上的数字角标没画出来）");

            List<int[]> diff = differingPixels(untagged, tagged);
            assertFalse(diff.isEmpty(), "门的角标必须在栅格上留下差异像素");
            for (int[] pixel : diff) {
                assertWithinCell(pixel, "门的数字角标");
            }
        });
    }

    // ---------- 夹具与栅格工具 ----------

    private static RenderViews.Mechanism plate(boolean active, String tag, boolean gateGroup) {
        return new RenderViews.Mechanism("plate_fixture", CENTER, CENTER,
                RenderViews.MechanismKind.PLATE, active, tag, gateGroup);
    }

    private static RenderViews.Mechanism door(boolean active, String tag) {
        return new RenderViews.Mechanism("door_fixture", CENTER, CENTER,
                RenderViews.MechanismKind.DOOR, active, tag, false);
    }

    private static int[] render(RenderViews.Mechanism mechanism) {
        Canvas canvas = new Canvas(CANVAS_SIZE, CANVAS_SIZE);
        RenderViews.Frame frame = new RenderViews.Frame(
                new RenderViews.Player(0.0, 0.0, Direction.DOWN, MovementState.IDLE, false),
                List.of(mechanism),
                List.of());
        new MechanismLayer(() -> frame, TILE_SIZE, WorldTransform.identity())
                .render(canvas.getGraphicsContext2D(), CANVAS_SIZE, CANVAS_SIZE, 0.0);

        WritableImage image = canvas.snapshot(null, null);
        int[] pixels = new int[(int) CANVAS_SIZE * (int) CANVAS_SIZE];
        image.getPixelReader().getPixels(0, 0, (int) CANVAS_SIZE, (int) CANVAS_SIZE,
                PixelFormat.getIntArgbInstance(), pixels, 0, (int) CANVAS_SIZE);
        return pixels;
    }

    /** 两个栅格里所有取值不同的像素坐标（ARGB 全通道比较，含 alpha）。 */
    private static List<int[]> differingPixels(int[] left, int[] right) {
        List<int[]> diff = new ArrayList<>();
        for (int y = 0; y < (int) CANVAS_SIZE; y++) {
            for (int x = 0; x < (int) CANVAS_SIZE; x++) {
                int index = y * (int) CANVAS_SIZE + x;
                if (left[index] != right[index]) {
                    diff.add(new int[] {x, y});
                }
            }
        }
        return diff;
    }

    private static void assertWithinCell(int[] pixel, String what) {
        assertTrue(Math.abs(pixel[0] - CENTER) <= CELL_HALF && Math.abs(pixel[1] - CENTER) <= CELL_HALF,
                what + " 越出了本格（tileSize=" + TILE_SIZE + "）："
                        + Arrays.toString(pixel) + "，本格中心=(" + CENTER + "," + CENTER + ")");
    }

    private static Color colorAt(int[] pixels, double x, double y) {
        return Color.rgb((pixels[(int) y * (int) CANVAS_SIZE + (int) x] >> 16) & 0xFF,
                (pixels[(int) y * (int) CANVAS_SIZE + (int) x] >> 8) & 0xFF,
                pixels[(int) y * (int) CANVAS_SIZE + (int) x] & 0xFF);
    }

    private static double distance(Color left, Color right) {
        double dr = left.getRed() - right.getRed();
        double dg = left.getGreen() - right.getGreen();
        double db = left.getBlue() - right.getBlue();
        return Math.sqrt(dr * dr + dg * dg + db * db);
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

        assertTrue(done.await(10, TimeUnit.SECONDS), "JavaFX 角标离屏渲染超时");
        if (failure[0] != null) {
            throw new AssertionError("角标离屏渲染断言失败", failure[0]);
        }
    }

    @FunctionalInterface
    private interface FxAssertion {
        void run() throws Exception;
    }
}
