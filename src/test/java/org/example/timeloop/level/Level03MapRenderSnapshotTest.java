package org.example.timeloop.level;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import org.example.timeloop.core.Direction;
import org.example.timeloop.core.MovementState;
import org.example.timeloop.level.model.LevelData;
import org.example.timeloop.level.model.PathNode;
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
import org.example.timeloop.render.RayLayer;
import org.example.timeloop.render.RenderViews;
import org.example.timeloop.render.SpawnLayer;
import org.example.timeloop.render.WorldTransform;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第三关（重排 v3 · C 板 (12,3) / K 板 (7,14)）的离屏出图。
 *
 * <p>与 {@code Level02MapRenderSnapshotTest} 同一套路：<b>不依赖 {@code app/**}</b>，
 * 直接按开发侧契约手构造 {@link RenderViews.Frame}，用与 {@code TimeTraceLabApplication} 相同的
 * 图层顺序离屏渲染，产出：</p>
 * <ul>
 *   <li>{@code target/observations/level03-map-render.png} —— 加载态（4 板 + 2 开关 + 3 门 + 1 出口，
 *       门与驻留板带 1/2/3 同号角标，终点组琥珀、不计号，射线画成一条竖线）；</li>
 *   <li>{@code target/observations/level03-solved.png} —— 官方解第三轮通关瞬间：
 *       出口闸三条件（S₂ 锁存 / S₃ 锁存 / K 被 E₁ 压住）同时成立 → EXIT 激活，玩家站在出口格上，
 *       两条残影轨迹（控制线 S→A→C→K / E₂ 支路 S→门A→J→S₂→射线→门C→B）可见。</li>
 * </ul>
 */
class Level03MapRenderSnapshotTest {

    private static final double TILE = Level03Pursuit.TILE_SIZE;
    private static final String LOADED_PATH = "target/observations/level03-map-render.png";
    private static final String SOLVED_PATH = "target/observations/level03-solved.png";

    /** 机关投影：4 板 + 2 开关 + 3 门 + 1 出口；标识与设计图 v3 一致。 */
    @Test
    void mechanismProjectionMatchesTheV3DesignAndKeepsEveryNumber() {
        Fixture fixture = new Fixture();
        List<RenderViews.Mechanism> mechanisms = fixture.mechanisms();

        assertEquals(10, mechanisms.size(),
                "4 板 + 2 开关 + 3 门 + 1 出口 = 10 件（终点闸与出口同格只投影一个 EXIT）");
        assertEquals(5, mechanisms.stream()
                .filter(m -> m.kind() == RenderViews.MechanismKind.PLATE).count(), "五块驻留板（含 S₂）");
        assertEquals(1, mechanisms.stream()
                .filter(m -> m.kind() == RenderViews.MechanismKind.SWITCH).count(), "只剩 S₃ 是胶囊");
        assertEquals(3, mechanisms.stream()
                .filter(m -> m.kind() == RenderViews.MechanismKind.DOOR).count(), "三扇门");
        assertEquals(1, mechanisms.stream()
                .filter(m -> m.kind() == RenderViews.MechanismKind.EXIT).count(), "一个出口");

        Set<String> switchIds = mechanisms.stream()
                .filter(m -> m.kind() == RenderViews.MechanismKind.SWITCH)
                .map(RenderViews.Mechanism::id).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(Level03Pursuit.SWITCH_S3), switchIds,
                "SWITCH 只剩 S₃（S₂ 按项目方要求改画成驻留板）");

        // 标识：开门组 A/B/C = 1/2/3（板心 + 门右下角同号）；终点组琥珀、一律不带数字。
        assertEquals("1", tagOf(mechanisms, Level03Pursuit.PLATE_A));
        assertEquals("2", tagOf(mechanisms, Level03Pursuit.PLATE_B));
        assertEquals("3", tagOf(mechanisms, Level03Pursuit.PLATE_C));
        assertEquals("1", tagOf(mechanisms, Level03Pursuit.DOOR_A));
        assertEquals("2", tagOf(mechanisms, Level03Pursuit.DOOR_B));
        assertEquals("3", tagOf(mechanisms, Level03Pursuit.DOOR_C));
        assertNull(tagOf(mechanisms, Level03Pursuit.PLATE_K));
        assertNull(tagOf(mechanisms, Level03Pursuit.SWITCH_S2));
        assertNull(tagOf(mechanisms, Level03Pursuit.SWITCH_S3));
        assertNull(tagOf(mechanisms, Level03Pursuit.EXIT));

        for (String gateGroup : List.of(Level03Pursuit.PLATE_K, Level03Pursuit.SWITCH_S2,
                Level03Pursuit.SWITCH_S3, Level03Pursuit.EXIT)) {
            assertTrue(gateGroupOf(mechanisms, gateGroup), gateGroup + " 属终点组（琥珀）");
        }
        for (String opening : List.of(Level03Pursuit.PLATE_A, Level03Pursuit.PLATE_B,
                Level03Pursuit.PLATE_C, Level03Pursuit.DOOR_A, Level03Pursuit.DOOR_B,
                Level03Pursuit.DOOR_C)) {
            assertFalse(gateGroupOf(mechanisms, opening), opening + " 属开门组（蓝）");
        }
    }

    /** 出两张图：加载态 + 通关瞬间。 */
    @Test
    void rendersTheLoadedMapAndTheSolvedMoment() throws Exception {
        Fixture fixture = new Fixture();

        snapshot(fixture, fixture.frameLoaded(), LOADED_PATH);
        assertTrue(new File(LOADED_PATH).length() > 4096, "加载态应生成非空 PNG");

        snapshot(fixture, fixture.frameSolved(), SOLVED_PATH);
        assertTrue(new File(SOLVED_PATH).length() > 4096, "通关瞬间应生成非空 PNG");
    }

    /** 通关瞬间的世界状态：三条件齐、三道门开、玩家在出口格。 */
    @Test
    void solvedFrameIsActuallyTheClearedState() {
        Fixture fixture = new Fixture();

        // E₁ 压住 A 板（门 A 窗口）与 C 板（门 C 窗口），最后驻留 K 板到轮末。
        assertTrue(fixture.plateA.tryEnter("echo_1", 1, Level03Pursuit.GATE_A_WINDOW_START));
        assertTrue(fixture.plateC.tryEnter("echo_1", 1, Level03Pursuit.PLATE_C_ARRIVAL));
        assertTrue(fixture.plateK.tryEnter("echo_1", 1, Level03Pursuit.PLATE_K_ARRIVAL));
        // E₂ 在第二轮踩下 S₂（锁存）并在刻 744 抵 B 板（门 B 由此开）。
        assertTrue(fixture.switchS2.tryEnter("echo_2", 2, Level03Pursuit.SWITCH_S2_ARRIVAL));
        assertTrue(fixture.switchS2.tryExit("echo_2", 2,
                Level03Pursuit.SWITCH_S2_ARRIVAL + Level03Pursuit.TICKS_PER_TILE));
        assertTrue(fixture.switchS2.isLatched(), "S₂ 必须已锁存（人走了条件仍成立）");
        assertTrue(fixture.plateB.tryEnter("echo_2", 2, Level03Pursuit.PLATE_B_ARRIVAL));
        // 第三轮玩家在开关室踩下 S₃（锁存）后离开，最后站在出口格上。
        assertTrue(fixture.switchS3.tryEnter("player", 0, Level03Pursuit.SWITCH_S3_ARRIVAL));
        assertTrue(fixture.switchS3.tryExit("player", 0,
                Level03Pursuit.SWITCH_S3_ARRIVAL + Level03Pursuit.TICKS_PER_TILE));

        assertTrue(fixture.exitGate.isUnlocked(), "S₂ + S₃ + K → 出口闸解锁");
        assertTrue(fixture.exit.isDoorUnlocked(), "出口终端被武装（E 提示可见）");
        assertTrue(fixture.doorA.isUnlocked(), "E₁ 压着 A 板 → 门 A 开");
        assertTrue(fixture.doorB.isUnlocked(), "E₂ 压着 B 板 → 门 B 开");
        assertTrue(fixture.doorC.isUnlocked(), "E₁ 压着 C 板 → 门 C 开");
        assertTrue(fixture.exit.interact(Level03Pursuit.PLATE_K_ARRIVAL, 0), "站在出口格按 E 通关");
    }

    // ---------- 场景装配（不依赖 app/**）----------

    private static final class Fixture {

        final DockingPlateRegistry registry = new DockingPlateRegistry();
        final EventDispatcher bus = new EventDispatcher();
        final LevelData level = Level03Pursuit.build();
        final LevelGeometry geometry = new LevelGeometryImpl(level);

        final DockingPlate plateA;
        final DockingPlate plateB;
        final DockingPlate plateC;
        final DockingPlate plateK;
        final DockingPlate switchS2;
        final DockingPlate switchS3;
        final Door doorA;
        final Door doorB;
        final Door doorC;
        final Door exitGate;
        final ExitTerminal exit;

        Fixture() {
            plateA = plate(Level03Pursuit.PLATE_A);
            plateB = plate(Level03Pursuit.PLATE_B);
            plateC = plate(Level03Pursuit.PLATE_C);
            plateK = plate(Level03Pursuit.PLATE_K);
            switchS2 = plate(Level03Pursuit.SWITCH_S2);
            switchS3 = plate(Level03Pursuit.SWITCH_S3);
            doorA = door(Level03Pursuit.DOOR_A);
            doorB = door(Level03Pursuit.DOOR_B);
            doorC = door(Level03Pursuit.DOOR_C);
            exitGate = door(Level03Pursuit.DOOR_EXIT);
            exit = new ExitTerminal(Level03Pursuit.EXIT, position(Level03Pursuit.EXIT),
                    exitGate.getId(), ExitTerminal.interactRadiusForTileSize(TILE), bus);
        }

        /** 加载态：出生点站立，10 件机关齐全且全部未激活，射线画成竖线，无残影。 */
        RenderViews.Frame frameLoaded() {
            return new RenderViews.Frame(
                    new RenderViews.Player(level.getSpawnPos().x(), level.getSpawnPos().y(),
                            Direction.UP, MovementState.IDLE, false),
                    mechanisms(),
                    List.of(),
                    rays());
        }

        /** 通关瞬间：玩家站在出口格上，两条残影分别压着控制线与 E₂ 支路的板。 */
        RenderViews.Frame frameSolved() {
            Vector2D exitCell = position(Level03Pursuit.EXIT);
            return new RenderViews.Frame(
                    new RenderViews.Player(exitCell.x(), exitCell.y(), Direction.RIGHT,
                            MovementState.IDLE, false),
                    mechanisms(),
                    List.of(
                            new RenderViews.EchoTrail(1, trace(Level03Pursuit.NODE_SPAWN,
                                    Level03Pursuit.NODE_PLATE_A, Level03Pursuit.NODE_PLATE_C,
                                    Level03Pursuit.NODE_PLATE_K), false),
                            new RenderViews.EchoTrail(2, trace(Level03Pursuit.NODE_SPAWN,
                                    Level03Pursuit.NODE_DOOR_A, Level03Pursuit.NODE_J,
                                    Level03Pursuit.NODE_SWITCH_S2,
                                    Level03Pursuit.nodeId(Level03Pursuit.CELL_RAY[0],
                                            Level03Pursuit.CELL_RAY[1]),
                                    Level03Pursuit.NODE_DOOR_C,
                                    Level03Pursuit.NODE_PLATE_B), true)),
                    rays());
        }

        /** 与 {@code Level03Assembly.renderViews} 同构的机关投影（本用例不依赖 app/**）。 */
        List<RenderViews.Mechanism> mechanisms() {
            List<RenderViews.Mechanism> list = new ArrayList<>();
            list.add(mechanism(plateA.getId(), plateA.getPosition(),
                    RenderViews.MechanismKind.PLATE, plateA.isOccupied(), "1", false));
            list.add(mechanism(plateB.getId(), plateB.getPosition(),
                    RenderViews.MechanismKind.PLATE, plateB.isOccupied(), "2", false));
            list.add(mechanism(plateC.getId(), plateC.getPosition(),
                    RenderViews.MechanismKind.PLATE, plateC.isOccupied(), "3", false));
            list.add(mechanism(plateK.getId(), plateK.getPosition(),
                    RenderViews.MechanismKind.PLATE, plateK.isOccupied(), null, true));
            list.add(mechanism(switchS2.getId(), switchS2.getPosition(),
                    RenderViews.MechanismKind.PLATE, switchS2.isOccupied(), null, true));
            list.add(mechanism(switchS3.getId(), switchS3.getPosition(),
                    RenderViews.MechanismKind.SWITCH, switchS3.isOccupied(), null, true));
            list.add(mechanism(doorA.getId(), doorA.getPosition(),
                    RenderViews.MechanismKind.DOOR, doorA.isUnlocked(), "1", false));
            list.add(mechanism(doorB.getId(), doorB.getPosition(),
                    RenderViews.MechanismKind.DOOR, doorB.isUnlocked(), "2", false));
            list.add(mechanism(doorC.getId(), doorC.getPosition(),
                    RenderViews.MechanismKind.DOOR, doorC.isUnlocked(), "3", false));
            // 终点闸与出口同格：只投影一个 EXIT（ID 用出口终端自己的，与 app 投影一致），不带角标。
            list.add(mechanism(exit.getId(), exit.getPosition(),
                    RenderViews.MechanismKind.EXIT, exitGate.isUnlocked(), null, true));
            return list;
        }

        /** 射线：v3 是竖线（x 固定、y 跨一格）。 */
        List<RenderViews.RayBeam> rays() {
            return List.of(new RenderViews.RayBeam(Level03Pursuit.RAY_B,
                    Level03Pursuit.RAY_X, Level03Pursuit.RAY_Y0,
                    Level03Pursuit.RAY_X, Level03Pursuit.RAY_Y1,
                    RenderViews.RayVisualState.ACTIVE));
        }

        List<Vector2D> trace(String... stops) {
            List<Vector2D> out = new ArrayList<>();
            for (int i = 0; i + 1 < stops.length; i++) {
                List<String> segment = shortestPath(stops[i], stops[i + 1]);
                if (segment.isEmpty()) {
                    throw new AssertionError("残影轨迹缺少路径: " + stops[i] + " → " + stops[i + 1]);
                }
                for (int k = (i == 0 ? 0 : 1); k < segment.size(); k++) {
                    out.add(position(segment.get(k)));
                }
            }
            return out;
        }

        private List<String> shortestPath(String from, String to) {
            Deque<String> queue = new ArrayDeque<>();
            Set<String> seen = new HashSet<>();
            Map<String, String> parent = new HashMap<>();
            queue.add(from);
            seen.add(from);
            while (!queue.isEmpty()) {
                String current = queue.poll();
                if (current.equals(to)) {
                    List<String> path = new ArrayList<>();
                    for (String node = to; node != null; node = parent.get(node)) {
                        path.add(0, node);
                    }
                    return path;
                }
                for (String next : geometry.getNeighbors(current)) {
                    if (seen.add(next)) {
                        parent.put(next, current);
                        queue.add(next);
                    }
                }
            }
            return List.of();
        }

        private RenderViews.Mechanism mechanism(String id, Vector2D pos,
                                                RenderViews.MechanismKind kind, boolean active,
                                                String tag, boolean gateGroup) {
            return new RenderViews.Mechanism(id, pos.x(), pos.y(), kind, active, tag, gateGroup);
        }

        /** 按关卡数据的 {@code role} 装配：{@code role=switch} → 锁存开关变体。 */
        private DockingPlate plate(String id) {
            boolean latching = level.getEntitySpawnList().stream()
                    .filter(e -> id.equals(e.getId()))
                    .findFirst()
                    .map(e -> "switch".equals(e.getProperties().get("role")))
                    .orElse(false);
            return new DockingPlate(id, position(id), registry, bus, latching);
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
                    .orElseGet(() -> level.getPathNodes().stream()
                            .filter(n -> mechanismId.equals(n.getId()))
                            .map(PathNode::getWorldPos)
                            .findFirst()
                            .orElseGet(() -> level.getDoors().stream()
                                    .filter(d -> mechanismId.equals(d.getId()))
                                    .map(d -> d.getPosition())
                                    .findFirst()
                                    .orElseThrow(() -> new AssertionError("缺少机制: " + mechanismId))));
        }
    }

    // ---------- 离屏渲染（与 L1 / L2 出图同一套路）----------

    private static void snapshot(Fixture fixture, RenderViews.Frame frame, String outPath) throws Exception {
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
                adapter.addLayer(new PathNodeHintLayer(() -> frame, pathNodeMarkers(level), TILE, transform));
                adapter.addLayer(new RayLayer(() -> frame, transform));
                adapter.addLayer(new MechanismLayer(() -> frame, TILE, transform));
                adapter.addLayer(new PlayerLayer(() -> frame, TILE, transform));
                adapter.addLayer(new EchoTrailLayer(() -> frame, transform));

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
            throw new AssertionError("L3 地图渲染抛出异常: " + outPath, failure[0]);
        }
        assertTrue(new File(outPath).isFile(), "应生成 PNG: " + outPath);
    }

    private static List<RenderViews.PathNodeMarker> pathNodeMarkers(LevelData level) {
        List<RenderViews.PathNodeMarker> markers = new ArrayList<>();
        for (PathNode node : level.getPathNodes()) {
            markers.add(new RenderViews.PathNodeMarker(node.getId(),
                    node.getWorldPos().x(), node.getWorldPos().y()));
        }
        return markers;
    }

    private static int distinctSampledColors(int[] pixels, int width, int height, int step) {
        Set<Integer> colors = new HashSet<>();
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

    private static String tagOf(List<RenderViews.Mechanism> mechanisms, String id) {
        return mechanismOf(mechanisms, id).tag();
    }

    private static boolean gateGroupOf(List<RenderViews.Mechanism> mechanisms, String id) {
        return mechanismOf(mechanisms, id).gateGroup();
    }

    private static RenderViews.Mechanism mechanismOf(List<RenderViews.Mechanism> mechanisms, String id) {
        return mechanisms.stream()
                .filter(m -> m.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("机关投影缺少: " + id));
    }
}
