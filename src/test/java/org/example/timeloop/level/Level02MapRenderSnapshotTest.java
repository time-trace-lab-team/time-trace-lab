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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第二关「闸链」新地图的离屏出图（沿用仓库既有六个渲染图层，渲染代码零改动）。
 *
 * <p>本用例<b>不依赖 {@code app/**}</b>：直接按开发侧契约手构造 {@link RenderViews.Frame}
 * （5 板 + 2 门 + 1 出口 + 2 条残影轨迹），用与 {@code TimeTraceLabApplication} 相同的图层顺序
 * 离屏渲染，产出：</p>
 * <ul>
 *   <li>{@code target/observations/level02-map-render.png} —— 加载态（8 件机关齐全，无残影）；</li>
 *   <li>{@code target/observations/level02-acceptance-solved.png} —— 官方解第 3 轮通关瞬间：
 *       内板(E2) + 主板(E1) + 终结板(玩家) 同刻被占 → 终点闸 EXIT active，D2 已回锁。</li>
 * </ul>
 */
class Level02MapRenderSnapshotTest {

    private static final double TILE = Level02Corridor.TILE_SIZE;
    private static final String LOADED_PATH = "target/observations/level02-map-render.png";
    private static final String SOLVED_PATH = "target/observations/level02-acceptance-solved.png";

    /** 机关投影硬要求：5 块普通板 + 2 扇门 + 1 个出口，且不得出现 SWITCH。 */
    @Test
    void mechanismProjectionIsFivePlatesTwoDoorsAndOneExit() {
        Fixture fixture = new Fixture();

        List<RenderViews.Mechanism> mechanisms = fixture.mechanisms();

        assertEquals(8, mechanisms.size(),
                "应为 5 板 + 2 门 + 1 出口 = 8 件（终点闸与出口同格，只投影一个 EXIT）");
        assertEquals(5, mechanisms.stream().filter(m -> m.kind() == RenderViews.MechanismKind.PLATE).count(),
                "五块驻留板必须都是普通 PLATE（P2 已从 role=switch 改回普通板）");
        assertEquals(2, mechanisms.stream().filter(m -> m.kind() == RenderViews.MechanismKind.DOOR).count(),
                "两扇门必须使用 DOOR（外闸 D1 / 内室门 D2）");
        assertEquals(1, mechanisms.stream().filter(m -> m.kind() == RenderViews.MechanismKind.EXIT).count(),
                "终点闸 + 出口必须使用 EXIT，不得用 DOOR 替代");
        assertEquals(0, mechanisms.stream().filter(m -> m.kind() == RenderViews.MechanismKind.SWITCH).count(),
                "L2 不得出现 SWITCH");

        Set<String> ids = new HashSet<>();
        for (RenderViews.Mechanism mechanism : mechanisms) {
            assertTrue(ids.add(mechanism.id()), "机关 ID 不得重复: " + mechanism.id());
        }
        assertTrue(ids.containsAll(Set.of(Level02Corridor.PLATE_GATE, Level02Corridor.PLATE_RELAY,
                Level02Corridor.PLATE_INNER, Level02Corridor.PLATE_MAIN, Level02Corridor.PLATE_CORE,
                Level02Corridor.DOOR_GATE, Level02Corridor.DOOR_RELAY, Level02Corridor.DOOR_EXIT)),
                "机关投影必须覆盖设计说明 §三 的全部机关: " + ids);
    }

    @Test
    void rendersTheLoadedMapAndWritesPng() throws Exception {
        Fixture fixture = new Fixture();
        snapshot(fixture, fixture.frameLoaded(), LOADED_PATH);
        assertTrue(new File(LOADED_PATH).length() > 4096, "应生成非空 PNG");
    }

    /** 官方解第 3 轮画面：内板 + 主板 + 终结板同刻被占 → 终点闸解锁；D1/D2 均已回锁。 */
    @Test
    void rendersTheSolvedMomentWithThreePlatesAndOpenGate() throws Exception {
        Fixture fixture = new Fixture();

        // R1 的残影 E1：闸板窗口 [240,384) + 主板自 624 起驻留。
        assertTrue(fixture.gatePlate.tryEnter("echo_1", 1, Level02Corridor.GATE_WINDOW_START));
        assertTrue(fixture.gatePlate.tryExit("echo_1", 1, Level02Corridor.GATE_WINDOW_END));
        assertTrue(fixture.mainPlate.tryEnter("echo_1", 1, Level02Corridor.MAIN_ARRIVAL));
        // R2 的残影 E2：中继窗口 [456,744) + 内板自 960 起驻留。
        assertTrue(fixture.relayPlate.tryEnter("echo_2", 2, Level02Corridor.RELAY_WINDOW_START));
        assertTrue(fixture.relayPlate.tryExit("echo_2", 2, Level02Corridor.RELAY_WINDOW_END));
        assertTrue(fixture.innerPlate.tryEnter("echo_2", 2, Level02Corridor.INNER_ARRIVAL));
        // 当前玩家：跨 D2 后踩住终结板。
        assertTrue(fixture.corePlate.tryEnter("player", 0, Level02Corridor.CORE_ARRIVAL));

        assertTrue(fixture.exitGate.isUnlocked(), "内板 + 主板 + 终结板同刻被占 → 终点闸解锁");
        assertTrue(fixture.exit.isDoorUnlocked(), "出口应被武装（E 提示可见）");
        assertFalse(fixture.gateDoor.isUnlocked(), "E1 已离开外闸板 → D1 回锁");
        assertFalse(fixture.relayDoor.isUnlocked(), "E2 已离开中继板 → D2 回锁");
        assertTrue(fixture.exit.interact(Level02Corridor.EXIT_UNLOCK_TICK, 0), "站在 P5 上按 E 通关");

        snapshot(fixture, fixture.frameSolved(), SOLVED_PATH);
        assertTrue(new File(SOLVED_PATH).length() > 4096, "应生成非空 PNG");
    }

    // ---------- 场景装配（不依赖 app/**）----------

    private static final class Fixture {

        final DockingPlateRegistry registry = new DockingPlateRegistry();
        final EventDispatcher bus = new EventDispatcher();
        final LevelData level = Level02Corridor.build();
        final LevelGeometry geometry = new LevelGeometryImpl(level);

        final DockingPlate gatePlate;
        final DockingPlate relayPlate;
        final DockingPlate innerPlate;
        final DockingPlate mainPlate;
        final DockingPlate corePlate;
        final Door gateDoor;
        final Door relayDoor;
        final Door exitGate;
        final ExitTerminal exit;

        Fixture() {
            gatePlate = plate(Level02Corridor.PLATE_GATE);
            relayPlate = plate(Level02Corridor.PLATE_RELAY);
            innerPlate = plate(Level02Corridor.PLATE_INNER);
            mainPlate = plate(Level02Corridor.PLATE_MAIN);
            corePlate = plate(Level02Corridor.PLATE_CORE);
            gateDoor = door(Level02Corridor.DOOR_GATE);
            relayDoor = door(Level02Corridor.DOOR_RELAY);
            exitGate = door(Level02Corridor.DOOR_EXIT);
            exit = new ExitTerminal(Level02Corridor.EXIT, position(Level02Corridor.EXIT),
                    exitGate.getId(), ExitTerminal.interactRadiusForTileSize(TILE), bus);
        }

        /** 加载态：出生点站立，8 件机关齐全且全部未激活，无残影。 */
        RenderViews.Frame frameLoaded() {
            return new RenderViews.Frame(
                    new RenderViews.Player(level.getSpawnPos().x(), level.getSpawnPos().y(),
                            Direction.RIGHT, MovementState.IDLE, false),
                    mechanisms(),
                    List.of());
        }

        /** 通关瞬间：玩家站在终结板上（DOCKED），两条残影仍在压板路线上。 */
        RenderViews.Frame frameSolved() {
            Vector2D core = position(Level02Corridor.PLATE_CORE);
            return new RenderViews.Frame(
                    new RenderViews.Player(core.x(), core.y(), Direction.RIGHT, MovementState.DOCKED, false),
                    mechanisms(),
                    List.of(
                            new RenderViews.EchoTrail(1, trace(Level02Corridor.NODE_SPAWN,
                                    Level02Corridor.NODE_PLATE_GATE, Level02Corridor.NODE_PLATE_MAIN), false),
                            new RenderViews.EchoTrail(2, trace(Level02Corridor.NODE_SPAWN,
                                    Level02Corridor.NODE_DOOR_GATE, Level02Corridor.NODE_PLATE_RELAY,
                                    Level02Corridor.NODE_PLATE_INNER), true)));
        }

        List<RenderViews.Mechanism> mechanisms() {
            List<RenderViews.Mechanism> list = new ArrayList<>();
            list.add(mechanism(gatePlate.getId(), gatePlate.getPosition(),
                    RenderViews.MechanismKind.PLATE, gatePlate.isOccupied()));
            list.add(mechanism(relayPlate.getId(), relayPlate.getPosition(),
                    RenderViews.MechanismKind.PLATE, relayPlate.isOccupied()));
            list.add(mechanism(innerPlate.getId(), innerPlate.getPosition(),
                    RenderViews.MechanismKind.PLATE, innerPlate.isOccupied()));
            list.add(mechanism(mainPlate.getId(), mainPlate.getPosition(),
                    RenderViews.MechanismKind.PLATE, mainPlate.isOccupied()));
            list.add(mechanism(corePlate.getId(), corePlate.getPosition(),
                    RenderViews.MechanismKind.PLATE, corePlate.isOccupied()));
            list.add(mechanism(gateDoor.getId(), gateDoor.getPosition(),
                    RenderViews.MechanismKind.DOOR, gateDoor.isUnlocked()));
            list.add(mechanism(relayDoor.getId(), relayDoor.getPosition(),
                    RenderViews.MechanismKind.DOOR, relayDoor.isUnlocked()));
            // 终点闸与出口终端同格：只投影一个 EXIT（与 L1 的做法一致，避免同格出现两个图标）。
            list.add(mechanism(exitGate.getId(), exitGate.getPosition(),
                    RenderViews.MechanismKind.EXIT, exitGate.isUnlocked()));
            return list;
        }

        /** 用最短路串起若干停靠点的残影轨迹（节点中心序列）。 */
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
                                                RenderViews.MechanismKind kind, boolean active) {
            return new RenderViews.Mechanism(id, pos.x(), pos.y(), kind, active);
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

    // ---------- 离屏渲染（与 L1 出图同一套路，批量像素拷贝）----------

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
                adapter.addLayer(new PathNodeHintLayer(
                        () -> frame, pathNodeMarkers(level), TILE, transform));
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
            throw new AssertionError("L2 地图渲染抛出异常: " + outPath, failure[0]);
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
}
