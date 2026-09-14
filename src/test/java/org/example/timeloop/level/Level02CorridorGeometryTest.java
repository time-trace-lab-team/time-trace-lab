package org.example.timeloop.level;

import org.example.timeloop.level.model.DoorInfo;
import org.example.timeloop.level.model.EntitySpawnInfo;
import org.example.timeloop.level.model.LevelData;
import org.example.timeloop.level.model.PathNode;
import org.example.timeloop.level.model.TileType;
import org.example.timeloop.level.model.Vector2D;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第二关「闸链」新地图（28×16）的几何与刻表测试。
 *
 * <p>全部数字都由 {@link Level02Corridor#build()} 的数据 <b>现算</b>（BFS 格数 / 欧氏距离 /
 * 栅格扫描），下面每个断言都写明它在桌面工具与设计说明里的出处，
 * 便于「数字改了但测试没改」时立刻暴露。</p>
 */
class Level02CorridorGeometryTest {

    private static final double TILE = Level02Corridor.TILE_SIZE;
    private static final long TICKS_PER_TILE = Level02Corridor.TICKS_PER_TILE;
    private static final double GATE_INTERACT = 72.0;

    private final LevelData level = Level02Corridor.build();
    private final LevelGeometry geometry = new LevelGeometryImpl(level);

    // ================= 网格与节点 =================

    @Test
    void parametersMatchTheDesignDocument() {
        assertEquals(48.0, level.getTileSize());
        assertEquals(1200L, level.getDurationTicks());
        assertEquals(4, level.getMaxRounds());
        assertEquals(2, level.getEchoLifeL());

        TileType[][] grid = level.getTileGrid();
        assertEquals(Level02Corridor.GRID_ROWS, grid.length, "网格行数");
        for (TileType[] row : grid) {
            assertEquals(Level02Corridor.GRID_COLS, row.length, "网格列数");
        }
        assertEquals(28, Level02Corridor.GRID_COLS);
        assertEquals(16, Level02Corridor.GRID_ROWS);
    }

    /** 设计说明 §一/§五：可走格 300，且 {@code LevelGeometryImpl} 校验通过（无孤点、无零长度边）。 */
    @Test
    void walkableNodeCountIsThreeHundredAndGeometryValidates() {
        assertEquals(300, level.getPathNodes().size(), "28×16 图上可走格应为 300");

        int floorTiles = 0;
        TileType[][] grid = level.getTileGrid();
        for (int row = 0; row < grid.length; row++) {
            for (int col = 0; col < grid[row].length; col++) {
                if (grid[row][col] != TileType.WALL) {
                    floorTiles++;
                }
            }
        }
        assertEquals(floorTiles, level.getPathNodes().size(), "每个地板格都必须有路径节点");

        // 构造即校验（LevelGeometryImpl 会把孤点/悬空出口/重复位置抛出来）。
        LevelGeometry built = new LevelGeometryImpl(level);
        assertEquals(300, built.getPathNodes().size());
        for (PathNode node : built.getPathNodes()) {
            assertFalse(built.getNeighbors(node.getId()).isEmpty(),
                    "节点不得是孤点: " + node.getId());
        }
    }

    /** 节点 ID 必须是桌面工具的规则：{@code L02_node_c<col>_r<row>}，且方向与 {@code open(...)} 完全一致。 */
    @Test
    void nodeIdsAndAllowDirsFollowTheDesktopToolRule() {
        Map<String, PathNode> byId = new HashMap<>();
        for (PathNode node : level.getPathNodes()) {
            assertTrue(byId.put(node.getId(), node) == null, "节点 ID 必须唯一: " + node.getId());
        }

        for (int row = 0; row < Level02Corridor.GRID_ROWS; row++) {
            for (int col = 0; col < Level02Corridor.GRID_COLS; col++) {
                boolean open = Level02Corridor.isOpen(col, row);
                PathNode node = byId.get(Level02Corridor.nodeId(col, row));
                if (!open) {
                    assertTrue(node == null, "墙格不得有节点: (" + col + "," + row + ")");
                    continue;
                }
                assertTrue(node != null, "地板格必须有节点: (" + col + "," + row + ")");
                assertEquals(Level02Corridor.cellCenter(col, row), node.getWorldPos(),
                        "节点世界坐标必须是格中心: " + node.getId());

                Set<PathNode.Dir> expected = new HashSet<>();
                if (Level02Corridor.isOpen(col, row - 1)) expected.add(PathNode.Dir.UP);
                if (Level02Corridor.isOpen(col, row + 1)) expected.add(PathNode.Dir.DOWN);
                if (Level02Corridor.isOpen(col - 1, row)) expected.add(PathNode.Dir.LEFT);
                if (Level02Corridor.isOpen(col + 1, row)) expected.add(PathNode.Dir.RIGHT);
                assertEquals(expected, node.getAllowDirs(), "allowDirs 必须等于 open(...) 的结果: " + node.getId());
            }
        }
    }

    /** 设计说明 §五「关于黑块」：不贴任何地板的墙格 = 7（全在边框上，消不掉）。 */
    @Test
    void blackWallCellsCountMatchesTheDesignDocument() {
        List<String> black = new ArrayList<>();
        for (int row = 0; row < Level02Corridor.GRID_ROWS; row++) {
            for (int col = 0; col < Level02Corridor.GRID_COLS; col++) {
                if (Level02Corridor.isOpen(col, row)) {
                    continue;
                }
                if (Level02Corridor.isOpen(col, row - 1) || Level02Corridor.isOpen(col, row + 1)
                        || Level02Corridor.isOpen(col - 1, row) || Level02Corridor.isOpen(col + 1, row)) {
                    continue;
                }
                black.add("(" + col + "," + row + ")");
            }
        }
        assertEquals(7, black.size(), "黑洞格应为 7，实测: " + black);
        // 全部落在边框上：任一坐标取 0 或最大。
        for (String cell : black) {
            String[] parts = cell.substring(1, cell.length() - 1).split(",");
            int col = Integer.parseInt(parts[0]);
            int row = Integer.parseInt(parts[1]);
            assertTrue(col == 0 || row == 0
                            || col == Level02Corridor.GRID_COLS - 1 || row == Level02Corridor.GRID_ROWS - 1,
                    "黑洞格只应出现在边框: " + cell);
        }
    }

    /** 设计说明 §一：全图只有一处「笔直 1 格宽」通道，长度 3（第 5 列 r6~r8 = 通道 A）。 */
    @Test
    void longestStraightOneTileCorridorIsThree() {
        assertEquals(3, longestStraightCorridorRun(), "最长笔直单通道应为 3 格");

        for (int row = 6; row <= 8; row++) {
            assertTrue(Level02Corridor.isOpen(5, row), "通道 A 的地板: (5," + row + ")");
            assertFalse(Level02Corridor.isOpen(4, row), "通道 A 西侧必须是墙: (4," + row + ")");
            assertFalse(Level02Corridor.isOpen(6, row), "通道 A 东侧必须是墙: (6," + row + ")");
        }
        // 通道 A 就是那 3 格：北接 (5,5)、南接 (5,9)。
        assertTrue(Level02Corridor.isOpen(5, 5));
        assertTrue(Level02Corridor.isOpen(5, 9));
    }

    // ================= 距离约束 =================

    /** 设计说明 §五「距离」：出口↔内板 P3 = 528.00 > 72（不能原地按 E）。 */
    @Test
    void exitIsOutOfReachFromTheInnerPlate() {
        double distance = distance(centerOf(Level02Corridor.NODE_EXIT),
                centerOf(Level02Corridor.NODE_PLATE_INNER));
        // (25,2) 与 (25,13)：dy = 11 格 × 48 = 528
        assertEquals(528.00, distance, 0.01);
        assertTrue(distance > GATE_INTERACT, "出口到内板必须 > " + GATE_INTERACT);
    }

    /** 设计说明 §三：终结板 P5 距出口 48.00 ≤ 72 → 站在 P5 上可原地按 E。 */
    @Test
    void exitIsReachableFromTheCorePlate() {
        double distance = distance(centerOf(Level02Corridor.NODE_EXIT),
                centerOf(Level02Corridor.NODE_PLATE_CORE));
        // (25,2) 与 (24,2)：dx = 1 格 × 48 = 48
        assertEquals(48.00, distance, 0.01);
        assertTrue(distance <= GATE_INTERACT, "出口到终结板必须 ≤ " + GATE_INTERACT);
    }

    /** 设计说明 §五「距离」：外闸板 P1 ↔ 主板 P4 ≈ 7.21 格 ≥ 3（两块板不得挨着）。 */
    @Test
    void gatePlateAndMainPlateAreFarApart() {
        double tiles = distance(centerOf(Level02Corridor.NODE_PLATE_GATE),
                centerOf(Level02Corridor.NODE_PLATE_MAIN)) / TILE;
        // (3,5) 与 (9,1)：dx=6、dy=4 → √52 = 7.2111
        assertEquals(7.21, tiles, 0.01);
        assertTrue(tiles >= 3.0, "两板间距必须 ≥ 3 格");
    }

    // ================= 封闭性（割点） =================

    /** 删掉外闸格 D1 → 东翼全不可达；主区（外闸板 / 主板）仍可达。 */
    @Test
    void deletingTheGateDoorMakesTheEastWingUnreachable() {
        String gateCell = Level02Corridor.NODE_DOOR_GATE;

        assertTrue(reachableWithout(gateCell, Level02Corridor.NODE_PLATE_GATE), "主区仍应可达外闸板");
        assertTrue(reachableWithout(gateCell, Level02Corridor.NODE_PLATE_MAIN), "主区仍应可达主板");

        for (String eastWing : List.of(Level02Corridor.NODE_PLATE_RELAY,
                Level02Corridor.NODE_DOOR_RELAY,
                Level02Corridor.NODE_PLATE_INNER,
                Level02Corridor.NODE_PLATE_CORE,
                Level02Corridor.NODE_EXIT)) {
            assertFalse(reachableWithout(gateCell, eastWing),
                    "删 D1 后东翼必须不可达: " + eastWing);
        }
        // D1 存在时东翼必须可达（门开了就能进）。
        for (String eastWing : List.of(Level02Corridor.NODE_PLATE_RELAY,
                Level02Corridor.NODE_PLATE_INNER,
                Level02Corridor.NODE_PLATE_CORE,
                Level02Corridor.NODE_EXIT)) {
            assertTrue(reachableWithout("", eastWing), "D1 存在时东翼必须可达: " + eastWing);
        }
    }

    /** 删掉内室门格 D2 → 终结板 / 出口不可达；中继板与内板仍在东翼内可达。 */
    @Test
    void deletingTheRelayDoorSealsOnlyTheInnerRoom() {
        String relayDoorCell = Level02Corridor.NODE_DOOR_RELAY;

        assertFalse(reachableWithout(relayDoorCell, Level02Corridor.NODE_PLATE_CORE),
                "删 D2 后终结板 P5 必须不可达");
        assertFalse(reachableWithout(relayDoorCell, Level02Corridor.NODE_EXIT),
                "删 D2 后出口必须不可达");

        assertTrue(reachableWithout(relayDoorCell, Level02Corridor.NODE_PLATE_RELAY),
                "删 D2 后中继板 P2 仍可达");
        assertTrue(reachableWithout(relayDoorCell, Level02Corridor.NODE_PLATE_INNER),
                "删 D2 后内板 P3 仍可达");
        assertTrue(reachableWithout(relayDoorCell, Level02Corridor.NODE_DOOR_GATE),
                "删 D2 后外闸格 D1 仍可达");
    }

    // ================= 刻表常量（逐条由几何现算） =================

    /**
     * 设计说明 §四「三轮刻表」：每个常量都必须等于「按几何 BFS 出来的格数 × 24 刻」。
     *
     * <p>任何一格地图改动都会让这里失败 —— 这正是它的意义。</p>
     */
    @Test
    void timelineTickConstantsAreDerivedFromTheGeometry() {
        // R1：出生点 → P1 = 10 格 = 240 刻
        assertEquals(Level02Corridor.GATE_WINDOW_START,
                pathTiles(Level02Corridor.NODE_SPAWN, Level02Corridor.NODE_PLATE_GATE) * TICKS_PER_TILE);
        // R1：P1 驻留 144 刻 → 松开刻，再走 10 格到 P4 = 624 刻
        assertEquals(144L, Level02Corridor.GATE_WINDOW_END - Level02Corridor.GATE_WINDOW_START);
        assertEquals(Level02Corridor.GATE_WINDOW_END
                        + pathTiles(Level02Corridor.NODE_PLATE_GATE, Level02Corridor.NODE_PLATE_MAIN) * TICKS_PER_TILE,
                Level02Corridor.MAIN_ARRIVAL);
        // R2/R3：出生点 → D1 = 13 格 = 312 刻（落 D1 窗口内）
        assertEquals(Level02Corridor.GATE_DOOR_CROSS,
                pathTiles(Level02Corridor.NODE_SPAWN, Level02Corridor.NODE_DOOR_GATE) * TICKS_PER_TILE);
        // R2：出生点 → P2 = 19 格 = 456 刻；驻留 288 刻造 D2 窗口
        assertEquals(Level02Corridor.RELAY_WINDOW_START,
                pathTiles(Level02Corridor.NODE_SPAWN, Level02Corridor.NODE_PLATE_RELAY) * TICKS_PER_TILE);
        assertEquals(288L, Level02Corridor.RELAY_WINDOW_END - Level02Corridor.RELAY_WINDOW_START);
        // R2：P2 → P3 = 9 格 = 216 刻 → 960 刻踩上内板
        assertEquals(Level02Corridor.RELAY_WINDOW_END
                        + pathTiles(Level02Corridor.NODE_PLATE_RELAY, Level02Corridor.NODE_PLATE_INNER) * TICKS_PER_TILE,
                Level02Corridor.INNER_ARRIVAL);
        // R3：出生点 → D2 = 28 格 = 672 刻；跨门 696；D2 → P5 = 6 格 = 144 刻 → 840 刻
        assertEquals(Level02Corridor.RELAY_DOOR_REACH,
                pathTiles(Level02Corridor.NODE_SPAWN, Level02Corridor.NODE_DOOR_RELAY) * TICKS_PER_TILE);
        assertEquals(Level02Corridor.RELAY_DOOR_CROSS,
                Level02Corridor.RELAY_DOOR_REACH + TICKS_PER_TILE);
        assertEquals(Level02Corridor.RELAY_DOOR_CROSS
                        + pathTiles(Level02Corridor.NODE_DOOR_RELAY, Level02Corridor.NODE_PLATE_CORE) * TICKS_PER_TILE,
                Level02Corridor.CORE_ARRIVAL);
        // 解锁刻 = E2 踩上内板的刻，轮末前仍有 240 刻余量。
        assertEquals(Level02Corridor.INNER_ARRIVAL, Level02Corridor.EXIT_UNLOCK_TICK);
        assertTrue(Level02Corridor.DURATION_TICKS - Level02Corridor.EXIT_UNLOCK_TICK >= 240,
                "解锁后必须留 ≥ 240 刻（10 格）余量");
    }

    /** 设计说明 §六「时间余量」：两个窗口两端都留 ≥ 72 刻。 */
    @Test
    void bothWindowsKeepAtLeastThreeTilesOfMargin() {
        // D1 窗口 [240, 384)，跨门 312：两端各 72 刻。
        assertTrue(Level02Corridor.GATE_DOOR_CROSS > Level02Corridor.GATE_WINDOW_START);
        assertTrue(Level02Corridor.GATE_DOOR_CROSS < Level02Corridor.GATE_WINDOW_END);
        assertEquals(72L, Level02Corridor.GATE_DOOR_CROSS - Level02Corridor.GATE_WINDOW_START);
        assertEquals(72L, Level02Corridor.GATE_WINDOW_END - Level02Corridor.GATE_DOOR_CROSS);

        // D2 窗口 [456, 744)，跨门 696：起点余 240、终点余 48。
        assertTrue(Level02Corridor.RELAY_DOOR_CROSS > Level02Corridor.RELAY_WINDOW_START);
        assertTrue(Level02Corridor.RELAY_DOOR_CROSS < Level02Corridor.RELAY_WINDOW_END);
        assertEquals(240L, Level02Corridor.RELAY_DOOR_CROSS - Level02Corridor.RELAY_WINDOW_START);
        assertEquals(48L, Level02Corridor.RELAY_WINDOW_END - Level02Corridor.RELAY_DOOR_CROSS);
    }

    /** 设计说明 §二「争抢」：R2 进东翼的路线不经过外闸板 P1 / 主板 P4 / 终结板 P5。 */
    @Test
    void r2RouteToTheInnerPlateNeverTouchesGateMainOrCorePlates() {
        List<String> route = shortestPath(Level02Corridor.NODE_PLATE_RELAY, Level02Corridor.NODE_PLATE_INNER);
        assertFalse(route.isEmpty(), "P2 → P3 必须连通（R2 第 5 步）");
        assertEquals(9, route.size() - 1, "P2 → P3 应为 9 格，实测: " + route);
        for (String plate : List.of(Level02Corridor.NODE_PLATE_GATE,
                Level02Corridor.NODE_PLATE_MAIN, Level02Corridor.NODE_PLATE_CORE)) {
            assertFalse(route.contains(plate), "R2 进路不得经过 " + plate + "，实测: " + route);
        }
    }

    // ================= 数据接线 =================

    /** 5 块普通板 + 3 扇门 + 出口 + 1 束射线，且板全部 {@code dock_plate} + {@code autoDock=true}、无 {@code role}。 */
    @Test
    void levelDataWiresFivePlainPlatesThreeDoorsExitAndOneRay() {
        assertEquals(Set.of(Level02Corridor.DOOR_GATE, Level02Corridor.DOOR_RELAY, Level02Corridor.DOOR_EXIT),
                level.getDoors().stream().map(DoorInfo::getId).collect(Collectors.toSet()));

        assertEquals(Set.of(Level02Corridor.PLATE_GATE), door(Level02Corridor.DOOR_GATE).getRequiredPlateIds());
        assertEquals(centerOf(Level02Corridor.NODE_DOOR_GATE), door(Level02Corridor.DOOR_GATE).getPosition());

        assertEquals(Set.of(Level02Corridor.PLATE_RELAY), door(Level02Corridor.DOOR_RELAY).getRequiredPlateIds());
        assertEquals(centerOf(Level02Corridor.NODE_DOOR_RELAY), door(Level02Corridor.DOOR_RELAY).getPosition());

        assertEquals(Set.of(Level02Corridor.PLATE_INNER, Level02Corridor.PLATE_MAIN, Level02Corridor.PLATE_CORE),
                door(Level02Corridor.DOOR_EXIT).getRequiredPlateIds());
        assertEquals(centerOf(Level02Corridor.NODE_EXIT), door(Level02Corridor.DOOR_EXIT).getPosition());

        for (String plateId : List.of(Level02Corridor.PLATE_GATE, Level02Corridor.PLATE_RELAY,
                Level02Corridor.PLATE_INNER, Level02Corridor.PLATE_MAIN, Level02Corridor.PLATE_CORE)) {
            EntitySpawnInfo plate = entity(plateId);
            assertEquals("dock_plate", plate.getEntityType(), plateId + " 必须是驻留板");
            assertEquals(Boolean.TRUE, plate.getProperties().get("autoDock"), plateId + " 必须 autoDock=true");
            assertFalse(plate.getProperties().containsKey("role"),
                    plateId + " 不得带 role（P2 已从 switch 改回普通板）");
        }

        assertEquals(centerOf(Level02Corridor.NODE_EXIT), entity(Level02Corridor.EXIT).getPos());
        assertEquals(centerOf(Level02Corridor.NODE_PLATE_CORE), entity(Level02Corridor.PLATE_CORE).getPos());
        assertEquals(centerOf(Level02Corridor.NODE_PLATE_INNER), entity(Level02Corridor.PLATE_INNER).getPos());

        List<EntitySpawnInfo> rays = level.getEntitySpawnList().stream()
                .filter(e -> "ray".equals(e.getEntityType())).toList();
        assertEquals(1, rays.size(), "本关只应有一束射线");
        assertEquals(Level02Corridor.RAY_CORRIDOR, rays.get(0).getId());
    }

    // ---------- 工具 ----------

    private DoorInfo door(String id) {
        return level.getDoors().stream().filter(d -> id.equals(d.getId())).findFirst().orElseThrow();
    }

    private EntitySpawnInfo entity(String id) {
        return level.getEntitySpawnList().stream()
                .filter(e -> id.equals(e.getId())).findFirst().orElseThrow();
    }

    private Vector2D centerOf(String nodeId) {
        return level.getPathNodes().stream()
                .filter(n -> nodeId.equals(n.getId()))
                .map(PathNode::getWorldPos)
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少节点: " + nodeId));
    }

    private static double distance(Vector2D a, Vector2D b) {
        return Math.hypot(a.x() - b.x(), a.y() - b.y());
    }

    /** 走行格数 = 最短路的边数（与欧氏距离无关）。 */
    private long pathTiles(String from, String to) {
        List<String> path = shortestPath(from, to);
        assertFalse(path.isEmpty(), "应存在路径: " + from + " → " + to);
        return path.size() - 1L;
    }

    private boolean reachableWithout(String ignore, String to) {
        return !shortestPath(Level02Corridor.NODE_SPAWN, to, ignore).isEmpty();
    }

    private List<String> shortestPath(String from, String to) {
        return shortestPath(from, to, "");
    }

    /** BFS 最短路（跳过 {@code ignore} 节点；空串表示不跳过任何节点）。 */
    private List<String> shortestPath(String from, String to, String ignore) {
        if (ignore.equals(from) || ignore.equals(to)) {
            return List.of();
        }
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
                if (!ignore.equals(next) && seen.add(next)) {
                    parent.put(next, current);
                    queue.add(next);
                }
            }
        }
        return List.of();
    }

    /**
     * 最长「笔直 1 格宽」通道：某格地板，且左右（或上下）都不是地板（桌面工具 {@code longestRun()} 的定义）。
     */
    private static int longestStraightCorridorRun() {
        int best = 0;
        for (int col = 0; col < Level02Corridor.GRID_COLS; col++) {
            int run = 0;
            for (int row = 0; row < Level02Corridor.GRID_ROWS; row++) {
                boolean corridor = Level02Corridor.isOpen(col, row)
                        && !Level02Corridor.isOpen(col - 1, row)
                        && !Level02Corridor.isOpen(col + 1, row);
                run = corridor ? run + 1 : 0;
                best = Math.max(best, run);
            }
        }
        for (int row = 0; row < Level02Corridor.GRID_ROWS; row++) {
            int run = 0;
            for (int col = 0; col < Level02Corridor.GRID_COLS; col++) {
                boolean corridor = Level02Corridor.isOpen(col, row)
                        && !Level02Corridor.isOpen(col, row - 1)
                        && !Level02Corridor.isOpen(col, row + 1);
                run = corridor ? run + 1 : 0;
                best = Math.max(best, run);
            }
        }
        return best;
    }
}
