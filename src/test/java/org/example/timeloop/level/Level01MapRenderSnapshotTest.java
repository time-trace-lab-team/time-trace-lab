package org.example.timeloop.level;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import org.example.timeloop.app.Level01Assembly;
import org.example.timeloop.core.Direction;
import org.example.timeloop.core.GamePhase;
import org.example.timeloop.core.input.InputIntent;
import org.example.timeloop.core.input.LogicalKey;
import org.example.timeloop.level.model.LevelData;
import org.example.timeloop.render.CanvasAdapter;
import org.example.timeloop.render.EchoTrailLayer;
import org.example.timeloop.render.GroundWallLayer;
import org.example.timeloop.render.MechanismLayer;
import org.example.timeloop.render.PathNodeHintLayer;
import org.example.timeloop.render.PlayerLayer;
import org.example.timeloop.render.SpawnLayer;
import org.example.timeloop.render.WorldTransform;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第一关地图的渲染冒烟测试 + 出图。
 *
 * <p>用途有三个：</p>
 * <ol>
 *   <li><b>冒烟</b>：用真实关卡数据 + 真实渲染图层跑完整帧，任何图层抛异常都会让本测试失败；</li>
 *   <li><b>关卡加载图</b>：{@code target/observations/level01-map-render.png}；</li>
 *   <li><b>通关瞬间图</b>：{@code target/observations/level01-map-victory.png} ——
 *       两块驻留板被占、门解锁、终点上方显示 {@code E} 的那一刻。E 提示是玩家判断
 *       "现在能按 E" 的唯一反馈，必须有自动化证据证明它真的被画出来。</li>
 * </ol>
 *
 * <p>本测试只读渲染，不写回玩法状态，也不启动窗口。</p>
 */
class Level01MapRenderSnapshotTest {

    private static final double TILE = 48.0;
    private static final int SPAWN_COL = 10;
    private static final int SPAWN_ROW = 2;
    private static final String LOADED_PATH = "target/observations/level01-map-render.png";
    private static final String VICTORY_PATH = "target/observations/level01-map-victory.png";

    /** 出生点 → 左驻留板（18 格 = 432 刻）。 */
    private static final String TO_LEFT = "DLL" + "DDDDDDD" + "LLL" + "UU" + "L" + "UU";
    /** 出生点 → 右驻留板（22 格 = 528 刻）。 */
    private static final String TO_RIGHT = "DD" + "RR" + "D" + "RRRRRRRRRR" + "DDD" + "LLLL";

    @Test
    void rendersLevel01MapWithoutExceptionAndWritesPng() throws Exception {
        Level01Assembly assembly = new Level01Assembly();
        try {
            snapshot(assembly, LOADED_PATH);
            assertTrue(new File(LOADED_PATH).length() > 4096, "应生成非空 PNG");
        } finally {
            assembly.cleanup();
        }
    }

    @Test
    void rendersTheWinningMomentWithTheInteractPromptVisible() throws Exception {
        Level01Assembly assembly = new Level01Assembly();
        try {
            assembly.start();

            // 第 1 轮：停在左驻留板上（残影据此录制）；随后让本轮自然结束。
            walk(assembly, TO_LEFT);
            waitRound(assembly, 1);

            // 第 2 轮：残影已压住左板，玩家走上右驻留板 —— 两块板同时被占，门解锁。
            walk(assembly, TO_RIGHT);
            settle(assembly);

            assertEquals(GamePhase.PLAYING, assembly.phase(),
                    "走到右板时仍在第 2 轮内，尚未按 E");
            snapshot(assembly, VICTORY_PATH);

            // 这一刻按 E 必须通关：证明出图的那一帧确实是"可交互"状态。
            int ticks = pressInteract(assembly, 10);
            assertEquals(GamePhase.RESULT, assembly.phase(),
                    "门已解锁 + 站在右板上按 E 必须通关");
            assertTrue(ticks >= 1);
        } finally {
            assembly.cleanup();
        }
    }

    // ---------- 无头驱动 ----------

    private static void walk(Level01Assembly assembly, String route) {
        int col = SPAWN_COL;
        int row = SPAWN_ROW;
        for (char step : route.toCharArray()) {
            Direction direction = switch (step) {
                case 'U' -> Direction.UP;
                case 'D' -> Direction.DOWN;
                case 'L' -> Direction.LEFT;
                default -> Direction.RIGHT;
            };
            col += (direction == Direction.LEFT ? -1 : direction == Direction.RIGHT ? 1 : 0);
            row += (direction == Direction.UP ? -1 : direction == Direction.DOWN ? 1 : 0);
            boolean edge = true;
            int guard = 0;
            while (!atCell(assembly, col, row) && guard++ < 400 && assembly.isPlaying()) {
                assembly.tick(hold(direction, edge));
                edge = false;
            }
        }
    }

    private static void waitRound(Level01Assembly assembly, int fromRound) {
        int guard = 0;
        while (assembly.hudContext().currentRound() == fromRound && guard++ < 1500 && assembly.isPlaying()) {
            assembly.tick(release());
        }
    }

    private static void settle(Level01Assembly assembly) {
        for (int i = 0; i < 4 && assembly.isPlaying(); i++) {
            assembly.tick(release());
        }
    }

    private static int pressInteract(Level01Assembly assembly, int max) {
        int used = 0;
        while (assembly.isPlaying() && used < max) {
            assembly.tick(interact());
            used++;
            if (assembly.phase() != GamePhase.PLAYING) {
                break;
            }
        }
        return used;
    }

    private static boolean atCell(Level01Assembly assembly, int col, int row) {
        var player = assembly.renderViews().player();
        return Math.abs(player.x() - (col + 0.5) * TILE) < 0.5
                && Math.abs(player.y() - (row + 0.5) * TILE) < 0.5;
    }

    private static InputIntent hold(Direction direction, boolean edge) {
        LogicalKey key = key(direction);
        return new InputIntent(0L, edge ? Set.of(key) : Set.of(), Set.of(), Set.of(key),
                edge ? List.of(direction) : List.of());
    }

    private static InputIntent release() {
        return new InputIntent(0L, Set.of(), Set.of(), Set.of(), List.of());
    }

    private static InputIntent interact() {
        return new InputIntent(0L, Set.of(LogicalKey.INTERACT), Set.of(),
                Set.of(LogicalKey.INTERACT), List.of());
    }

    private static LogicalKey key(Direction direction) {
        return switch (direction) {
            case UP -> LogicalKey.DIR_UP;
            case DOWN -> LogicalKey.DIR_DOWN;
            case LEFT -> LogicalKey.DIR_LEFT;
            case RIGHT -> LogicalKey.DIR_RIGHT;
        };
    }

    // ---------- 渲染出图 ----------

    private static void snapshot(Level01Assembly assembly, String outPath) throws Exception {
        LevelData level = Level01Footsteps.build();
        int rows = level.getTileGrid().length;
        int cols = level.getTileGrid()[0].length;
        double worldW = cols * TILE;
        double worldH = rows * TILE;

        CountDownLatch done = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];

        ensureFxStarted();
        Platform.runLater(() -> {
            try {
                Canvas canvas = new Canvas(worldW, worldH);
                CanvasAdapter adapter = new CanvasAdapter(canvas);
                WorldTransform transform = WorldTransform.identity();

                // 与 app/TimeTraceLabApplication 的图层顺序一致
                adapter.addLayer(new GroundWallLayer(level.getTileGrid(), TILE, transform));
                adapter.addLayer(new SpawnLayer(
                        level.getSpawnPos().x(), level.getSpawnPos().y(), TILE, transform));
                adapter.addLayer(new PathNodeHintLayer(
                        assembly::renderViews, assembly.pathNodeMarkers(), TILE, transform));
                adapter.addLayer(new MechanismLayer(assembly::renderViews, TILE, transform));
                adapter.addLayer(new PlayerLayer(assembly::renderViews, TILE, transform));
                adapter.addLayer(new EchoTrailLayer(assembly::renderViews, transform));

                adapter.renderFrame(worldW, worldH, 0.0);

                WritableImage image = canvas.snapshot(null, null);
                PixelReader reader = image.getPixelReader();
                int width = (int) worldW;
                int height = (int) worldH;
                BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

                // TECH-DEBT-SNAPSHOT-RUNTIME-v2：原实现逐像素 getArgb + setRGB
                //（1344×768 ≈ 103 万次调用）是本用例的主要耗时；改为一次性批量像素拷贝，
                // 产出的图像与逐像素版本逐位相同，断言强度不变。
                int[] pixels = new int[width * height];
                reader.getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
                buffered.setRGB(0, 0, width, height, pixels, 0, width);

                File out = new File(outPath);
                File parent = out.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                ImageIO.write(buffered, "png", out);

                // 结构断言（替代原先仅"文件字节数 > 4096"的弱断言）：
                // 每隔 8 像素抽样，要求画面确实存在足量不同颜色 —— 单一色块（漏画图层/整屏同色）会被立刻发现。
                assertTrue(distinctSampledColors(pixels, width, height, 8) >= 8,
                        "画面抽样颜色数不足，疑似漏画图层或整屏单色: " + outPath);
            } catch (Throwable throwable) {
                failure[0] = throwable;
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(90, TimeUnit.SECONDS), "JavaFX 渲染超时: " + outPath);
        if (failure[0] != null) {
            throw new AssertionError("第一关地图渲染抛出异常: " + outPath, failure[0]);
        }
        File out = new File(outPath);
        assertTrue(out.isFile() && out.length() > 4096,
                "应生成非空 PNG: " + out.getAbsolutePath() + " size=" + out.length());
    }

    private static void ensureFxStarted() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // 同一个 JVM 里第二次出图：Toolkit 已启动，直接复用。
        }
    }

    /**
     * 抽样统计不同颜色数（每隔 {@code step} 像素取一个样本）。
     *
     * <p>抽样而非全量扫描：本断言要抓的是「整屏单色 / 漏画图层」这类粗粒度回归，
     * 每 8 像素取样仍有约 1.6 万个样本，足以覆盖全部 28×16 格与所有图层；
     * 而全量去重需要 ~103 万次哈希操作，正是本卡要收口的耗时来源。</p>
     */
    private static int distinctSampledColors(int[] pixels, int width, int height, int step) {
        java.util.HashSet<Integer> colors = new java.util.HashSet<>();
        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                colors.add(pixels[y * width + x]);
            }
        }
        return colors.size();
    }
}
