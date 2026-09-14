package org.example.timeloop.level;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import org.example.timeloop.core.Direction;
import org.example.timeloop.core.MovementState;
import org.example.timeloop.level.model.LevelData;
import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.DockingPlate;
import org.example.timeloop.mechanism.DockingPlateRegistry;
import org.example.timeloop.mechanism.Door;
import org.example.timeloop.mechanism.ExitTerminal;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.example.timeloop.render.CanvasAdapter;
import org.example.timeloop.render.EchoTrailLayer;
import org.example.timeloop.render.GroundWallLayer;
import org.example.timeloop.render.MechanismLayer;
import org.example.timeloop.render.PathNodeHintLayer;
import org.example.timeloop.render.PlayerLayer;
import org.example.timeloop.render.RenderViews;
import org.example.timeloop.render.SpawnLayer;
import org.example.timeloop.render.WorldTransform;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L2-A 验收场景离屏出图（回应开发一《L02-A-开发1-render交付与PM开发三对接卡》**§4-⑤**）：
 * 提供「**同时看到三块驻留板、房门与终点闸门**」的可复核画面。
 *
 * <p>本用例**不依赖 `app/**`**（L2 装配卡属 PM）：直接按 development 侧契约手构造
 * {@link RenderViews.Frame}（三板 + 两门 + 出口 + 两条残影轨迹），用与
 * {@code TimeTraceLabApplication} 相同的图层顺序离屏渲染，产出：</p>
 * <ul>
 *   <li>{@code target/observations/level02-map-render.png} —— 加载态（六件机关齐全）；</li>
 *   <li>{@code target/observations/level02-acceptance-solved.png} —— 官方解第 3 轮：内板(E2) + 主驻留板(E1)
 *       同刻被占 → 终点闸门打开（EXIT active），房门已回锁。</li>
 * </ul>
 */
class Level02MapRenderSnapshotTest {

    private static final double TILE = Level02Corridor.TILE_SIZE;
    private static final String LOADED_PATH = "target/observations/level02-map-render.png";
    private static final String SOLVED_PATH = "target/observations/level02-acceptance-solved.png";

    /** §4-⑤ 的硬要求：画面必须同时含三块板 + 房门 + 终点闸门（本用例按机制条数断言）。 */
    @Test
    void acceptanceFrameContainsThreePlatesRoomDoorAndExitGate() {
        Fixture fixture = new Fixture();

        List<RenderViews.Mechanism> mechanisms = fixture.mechanisms();

        assertEquals(5, mechanisms.size(), "应为 三板 + 房门 + 终点闸门 = 5 件（门与出口同格，只投影一个 EXIT）");
        assertEquals(3, mechanisms.stream().filter(m -> m.kind() == RenderViews.MechanismKind.PLATE).count(),
                "三块驻留板必须都是普通 PLATE（开发一 §4-③：不得用 SWITCH/锁存）");
        assertEquals(1, mechanisms.stream().filter(m -> m.kind() == RenderViews.MechanismKind.DOOR).count(),
                "房门必须使用 DOOR（开发一 §3-③）");
        assertEquals(1, mechanisms.stream().filter(m -> m.kind() == RenderViews.MechanismKind.EXIT).count(),
                "终点闸门必须使用 EXIT，不得用 DOOR 替代（开发一 §3-④）");
        assertEquals(0, mechanisms.stream().filter(m -> m.kind() == RenderViews.MechanismKind.SWITCH).count(),
                "L2 不得出现 SWITCH（三块板都是普通驻留板）");
    }

    @Test
    void rendersLevel02MapWithoutExceptionAndWritesPng() throws Exception {
        Fixture fixture = new Fixture();
        snapshot(fixture, LOADED_PATH);
        assertTrue(new File(LOADED_PATH).length() > 4096, "应生成非空 PNG");
    }

    /** 官方解第 3 轮画面：内板 + 主驻留板同刻被占 → 闸门开；房门回锁。 */
    @Test
    void rendersTheSolvedMomentWithThreePlatesAndOpenGate() throws Exception {
        Fixture fixture = new Fixture();

        assertTrue(fixture.outerPlate.tryEnter("echo_1", 1, Level02Corridor.WINDOW_START));
        assertTrue(fixture.outerPlate.tryExit("echo_1", 1, Level02Corridor.WINDOW_END));
        assertTrue(fixture.innerPlate.tryEnter("echo_2", 2, Level02Corridor.R2_INNER_ARRIVAL));
        assertTrue(fixture.mainPlate.tryEnter("echo_1", 1, Level02Corridor.R1_MAIN_ARRIVAL));

        assertTrue(fixture.exitGate.isUnlocked(), "内板 + 主驻留板同刻被占 → 终点闸门解锁");
        assertTrue(fixture.exit.isDoorUnlocked(), "出口应被武装（E 提示可见）");
        assertTrue(!fixture.roomDoor.isUnlocked(), "E1 已离开门外板 → 房门回锁");

        snapshot(fixture, SOLVED_PATH);
        assertTrue(new File(SOLVED_PATH).length() > 4096, "应生成非空 PNG");
    }

    // ---------- 场景装配（不依赖 app/**）----------

    private static final class Fixture {

        final DockingPlateRegistry registry = new DockingPlateRegistry();
        final EventDispatcher bus = new EventDispatcher();
        final DockingPlate outerPlate;
        final DockingPlate innerPlate;
        final DockingPlate mainPlate;
        final Door roomDoor;
        final Door exitGate;
        final ExitTerminal exit;
        final LevelData level = Level02Corridor.build();

        Fixture() {
            outerPlate = plate(Level02Corridor.PLATE_DOOR);
            innerPlate = plate(Level02Corridor.PLATE_INNER);
            mainPlate = plate(Level02Corridor.PLATE_MAIN);
            roomDoor = door(Level02Corridor.DOOR_ROOM);
            exitGate = door(Level02Corridor.DOOR_EXIT);
            exit = new ExitTerminal(Level02Corridor.EXIT, position(Level02Corridor.EXIT),
                    exitGate.getId(), ExitTerminal.interactRadiusForTileSize(TILE), bus);
        }

        RenderViews.Frame frame() {
            return new RenderViews.Frame(
                    new RenderViews.Player(spawnX(), spawnY(), Direction.DOWN, MovementState.DOCKED, false),
                    mechanisms(),
                    List.of(
                            new RenderViews.EchoTrail(1, List.of(
                                    new Vector2D(2.5 * TILE, 11.5 * TILE),
                                    new Vector2D(6.5 * TILE, 11.5 * TILE),
                                    new Vector2D(6.5 * TILE, 6.5 * TILE)), false),
                            new RenderViews.EchoTrail(2, List.of(
                                    new Vector2D(2.5 * TILE, 11.5 * TILE),
                                    new Vector2D(5.5 * TILE, 11.5 * TILE),
                                    new Vector2D(5.5 * TILE, 5.5 * TILE)), true)));
        }

        List<RenderViews.Mechanism> mechanisms() {
            List<RenderViews.Mechanism> list = new ArrayList<>();
            list.add(mechanism(outerPlate.getId(), outerPlate.getPosition(), RenderViews.MechanismKind.PLATE,
                    outerPlate.isOccupied()));
            list.add(mechanism(innerPlate.getId(), innerPlate.getPosition(), RenderViews.MechanismKind.PLATE,
                    innerPlate.isOccupied()));
            list.add(mechanism(mainPlate.getId(), mainPlate.getPosition(), RenderViews.MechanismKind.PLATE,
                    mainPlate.isOccupied()));
            list.add(mechanism(roomDoor.getId(), roomDoor.getPosition(), RenderViews.MechanismKind.DOOR,
                    roomDoor.isUnlocked()));
            // 终点闸门与出口终端同格：只投影一个 EXIT（与 L1 的做法一致，避免同格出现两个图标）
            list.add(mechanism(exitGate.getId(), exitGate.getPosition(), RenderViews.MechanismKind.EXIT,
                    exitGate.isUnlocked()));
            return list;
        }

        private RenderViews.Mechanism mechanism(String id, Vector2D pos,
                                                RenderViews.MechanismKind kind, boolean active) {
            return new RenderViews.Mechanism(id, pos.x(), pos.y(), kind, active);
        }

        private double spawnX() {
            return level.getSpawnPos() == null ? 2.5 * TILE : level.getSpawnPos().x();
        }

        private double spawnY() {
            return level.getSpawnPos() == null ? 11.5 * TILE : level.getSpawnPos().y();
        }

        private DockingPlate plate(String id) {
            return new DockingPlate(id, position(id), registry, bus);
        }

        private Door door(String id) {
            Set<String> required = level.getDoors().stream()
                    .filter(d -> id.equals(d.getId()))
                    .map(d -> d.getRequiredPlateIds())
                    .findFirst()
                    .orElseThrow();
            return new Door(id, position(id), required, registry, bus);
        }

        private Vector2D position(String mechanismId) {
            return level.getEntitySpawnList().stream()
                    .filter(e -> mechanismId.equals(e.getId()))
                    .map(e -> e.getPos())
                    .findFirst()
                    .orElseGet(() -> level.getDoors().stream()
                            .filter(d -> mechanismId.equals(d.getId()))
                            .map(d -> d.getPosition())
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("缺少机制: " + mechanismId)));
        }
    }

    // ---------- 离屏渲染（与 L1 出图同一套路，批量像素拷贝）----------

    private static void snapshot(Fixture fixture, String outPath) throws Exception {
        LevelData level = fixture.level;
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

                adapter.addLayer(new GroundWallLayer(level.getTileGrid(), TILE, transform));
                adapter.addLayer(new SpawnLayer(level.getSpawnPos().x(), level.getSpawnPos().y(), TILE, transform));
                adapter.addLayer(new PathNodeHintLayer(
                        fixture::frame, pathNodeMarkers(level), TILE, transform));
                adapter.addLayer(new MechanismLayer(fixture::frame, TILE, transform));
                adapter.addLayer(new PlayerLayer(fixture::frame, TILE, transform));
                adapter.addLayer(new EchoTrailLayer(fixture::frame, transform));

                adapter.renderFrame(worldW, worldH, 0.0);

                WritableImage image = canvas.snapshot(null, null);
                int width = (int) worldW;
                int height = (int) worldH;
                BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                int[] pixels = new int[width * height];
                PixelReader reader = image.getPixelReader();
                reader.getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
                buffered.setRGB(0, 0, width, height, pixels, 0, width);

                File out = new File(outPath);
                File parent = out.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                ImageIO.write(buffered, "png", out);

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
            throw new AssertionError("L2 地图渲染抛出异常: " + outPath, failure[0]);
        }
        assertTrue(new File(outPath).isFile(), "应生成 PNG: " + outPath);
    }

    private static List<RenderViews.PathNodeMarker> pathNodeMarkers(LevelData level) {
        List<RenderViews.PathNodeMarker> markers = new ArrayList<>();
        for (var node : level.getPathNodes()) {
            markers.add(new RenderViews.PathNodeMarker(node.getId(),
                    node.getWorldPos().x(), node.getWorldPos().y()));
        }
        return markers;
    }

    private static int distinctSampledColors(int[] pixels, int width, int height, int step) {
        java.util.HashSet<Integer> colors = new java.util.HashSet<>();
        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                colors.add(pixels[y * width + x]);
            }
        }
        return colors.size();
    }

    private static void ensureFxStarted() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // 同一 JVM 内第二次出图：Toolkit 已启动，直接复用。
        }
    }
}
