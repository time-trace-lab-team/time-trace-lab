package org.example.timeloop.level;

import org.example.timeloop.level.model.DoorInfo;
import org.example.timeloop.level.model.EntitySpawnInfo;
import org.example.timeloop.level.model.LevelData;
import org.example.timeloop.level.model.PathNode;
import org.example.timeloop.level.model.Vector2D;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L2-A 几何与约束测试（卡 §二.2 + 裁决 §十一.4 / §十三）。
 *
 * <p>约束以裁决 <b>§十一.4</b> 为准：1′（锁门只挡房间）／2（闸门与内板 &gt; 72）／
 * 3′（窗口时长 ≥ 房门内侧邻点→内板 走行刻 + 余量）／7（门外板与主驻留板 ≥ 3 格）；
 * 5 属 L2-B；6 由机制语义保证（见 {@code Level02CorridorChainTest}）。</p>
 */
class Level02CorridorGeometryTest {

    private static final double TILE = 48.0;
    private static final double TICKS_PER_TILE = 24.0;
    private static final double GATE_INTERACT = 72.0;

    private final LevelData level = Level02Corridor.build();
    private final LevelGeometry geometry = new LevelGeometryImpl(level);

    @Test
    void parametersMatchTheRuling() {
        assertEquals(1200L, level.getDurationTicks());
        assertEquals(4, level.getMaxRounds());
        assertEquals(2, level.getEchoLifeL());
    }

    /** 约束 2：终点闸门（与出口同格）到内板必须 > 1.5 × tileSize。 */
    @Test
    void exitGateIsOutOfReachFromTheInnerPlate() {
        double distance = distance(centerOf(Level02Corridor.NODE_EXIT),
                centerOf(Level02Corridor.NODE_PLATE_INNER));
        // (16,11) 与 (5,5)：dx=11、dy=6 → √157 × 48 = 601.438
        assertEquals(601.44, distance, 0.01);
        assertTrue(distance > GATE_INTERACT, "闸门到内板必须 > " + GATE_INTERACT);
    }

    /** 约束 7：门外板与主驻留板不得相邻（建议 ≥ 3 格）。 */
    @Test
    void outerPlateAndMainPlateAreFarApart() {
        double distance = distance(centerOf(Level02Corridor.NODE_PLATE_DOOR),
                centerOf(Level02Corridor.NODE_PLATE_MAIN));
        assertEquals(8.602, distance / TILE, 0.01);
        assertTrue(distance / TILE >= 3.0, "两板间距必须 ≥ 3 格");
    }

    /** 约束 1′：删掉房门节点后，「出生点 → 门外板 / 主驻留板 / 闸门」三条路线仍连通。 */
    @Test
    void roomDoorIsNotACutPointOfTheOuterRoutes() {
        assertTrue(reachableWithout(Level02Corridor.NODE_SPAWN, Level02Corridor.NODE_PLATE_DOOR,
                Level02Corridor.NODE_DOOR_ROOM), "删房门后仍须能走到门外板");
        assertTrue(reachableWithout(Level02Corridor.NODE_SPAWN, Level02Corridor.NODE_PLATE_MAIN,
                Level02Corridor.NODE_DOOR_ROOM), "删房门后仍须能走到主驻留板");
        assertTrue(reachableWithout(Level02Corridor.NODE_SPAWN, Level02Corridor.NODE_EXIT,
                Level02Corridor.NODE_DOOR_ROOM), "删房门后仍须能走到闸门");
    }

    /** 房间不设旁路：删掉房门节点后，内板不可达（房门是唯一入口）。 */
    @Test
    void innerPlateIsUnreachableWithoutTheRoomDoor() {
        assertFalse(reachableWithout(Level02Corridor.NODE_SPAWN, Level02Corridor.NODE_PLATE_INNER,
                Level02Corridor.NODE_DOOR_ROOM), "房门必须是房间唯一入口（不设旁路）");
        assertTrue(reachableWithout(Level02Corridor.NODE_SPAWN, Level02Corridor.NODE_PLATE_INNER,
                ""), "房门存在时内板必须可达");
    }

    /** 约束 3′：窗口时长 ≥ 房门内侧邻点 → 内板 的走行刻 + 余量（≥ 30 刻）。 */
    @Test
    void windowIsLongEnoughForTheDashToTheInnerPlate() {
        long walkTicks = (long) (distance(centerOf(Level02Corridor.NODE_DOOR_ROOM),
                centerOf(Level02Corridor.NODE_PLATE_INNER)) / TILE * TICKS_PER_TILE);
        assertEquals(24L, walkTicks, "房门内侧邻点→内板 = 1 格 = 24 刻");

        long window = Level02Corridor.WINDOW_END - Level02Corridor.WINDOW_START;
        assertEquals(180L, window);
        assertTrue(window >= walkTicks + 30, "窗口必须 ≥ 走行刻 + 30 刻余量");
    }

    /** 刻表余量（§十三.2 第 2/3 条）：到南邻点早于窗口、跨门刻两侧各 ≥ 30 刻。 */
    @Test
    void timelineKeepsTheRequiredMargins() {
        assertTrue(Level02Corridor.R2_SOUTH_ARRIVAL < Level02Corridor.WINDOW_START,
                "R2 到房门南邻点必须早于窗口开始（可等待、不追赶）");
        assertTrue(Level02Corridor.WINDOW_START - Level02Corridor.R2_SOUTH_ARRIVAL >= 30);
        assertTrue(Level02Corridor.R2_DOOR_CROSS - Level02Corridor.WINDOW_START >= 30,
                "跨门刻相对窗口起点须 ≥ 30 刻余量");
        assertTrue(Level02Corridor.WINDOW_END - Level02Corridor.R2_DOOR_CROSS >= 30,
                "跨门刻相对窗口终点须 ≥ 30 刻余量");
        assertTrue(Level02Corridor.R2_INNER_ARRIVAL > Level02Corridor.R2_DOOR_CROSS);
        assertTrue(Level02Corridor.R1_MAIN_ARRIVAL > Level02Corridor.WINDOW_END);
    }

    /** 刻表几何依据：各段<b>走行格数</b>（路径边数，不是欧氏距离）。 */
    @Test
    void timelineTickConstantsMatchTheGeometry() {
        assertEquals(216L, pathTiles(Level02Corridor.NODE_SPAWN, Level02Corridor.NODE_PLATE_DOOR) * 24L);
        assertEquals(Level02Corridor.R2_SOUTH_ARRIVAL,
                pathTiles(Level02Corridor.NODE_SPAWN, Level02Corridor.NODE_DOOR_SOUTH) * 24L);
        assertEquals(264L, pathTiles(Level02Corridor.NODE_SPAWN, Level02Corridor.NODE_PLATE_MAIN) * 24L);
        assertEquals(336L, pathTiles(Level02Corridor.NODE_SPAWN, Level02Corridor.NODE_EXIT) * 24L);
        assertEquals(288L, pathTiles(Level02Corridor.NODE_PLATE_DOOR, Level02Corridor.NODE_PLATE_MAIN) * 24L);
        assertEquals(R2_INNER_TICKS, pathTiles(Level02Corridor.NODE_DOOR_ROOM,
                Level02Corridor.NODE_PLATE_INNER) * 24L);
    }

    private static final long R2_INNER_TICKS = 24L;

    /**
     * §十三.3 第 1 条：R2 的进房路线与门外板节点<b>不相交</b>。
     *
     * <p>取「出生点 → 内板」的最短路（房门开启时的唯一可行路线），断言其中不含门外板节点。</p>
     */
    @Test
    void r2EntryRouteDoesNotIntersectOuterPlate() {
        List<String> route = shortestPath(Level02Corridor.NODE_SPAWN, Level02Corridor.NODE_PLATE_INNER);

        assertFalse(route.isEmpty(), "应存在进房路线");
        assertFalse(route.contains(Level02Corridor.NODE_PLATE_DOOR),
                "R2 进房路线不得经过门外板（§十三.2），实测路线: " + route);
        assertTrue(route.contains(Level02Corridor.NODE_DOOR_SOUTH), "R2 必须经过房门南邻点");
        assertTrue(route.contains(Level02Corridor.NODE_DOOR_ROOM), "R2 必须穿过房门格");
        assertEquals(9, route.size() - 1, "R2 进房路线应为 9 格（216 刻），实测: " + route);
    }

    /** 数据一致性：两个门、三块板、出口挂在 door_exit 上。 */
    @Test
    void levelDataWiresTwoDoorsThreePlatesAndTheExit() {
        assertEquals(Set.of(Level02Corridor.DOOR_ROOM, Level02Corridor.DOOR_EXIT),
                level.getDoors().stream().map(DoorInfo::getId).collect(java.util.stream.Collectors.toSet()));

        DoorInfo roomDoor = door(Level02Corridor.DOOR_ROOM);
        assertEquals(Set.of(Level02Corridor.PLATE_DOOR), roomDoor.getRequiredPlateIds());
        assertEquals(centerOf(Level02Corridor.NODE_DOOR_ROOM), roomDoor.getPosition());

        DoorInfo exitDoor = door(Level02Corridor.DOOR_EXIT);
        assertEquals(Set.of(Level02Corridor.PLATE_INNER, Level02Corridor.PLATE_MAIN),
                exitDoor.getRequiredPlateIds());
        assertEquals(centerOf(Level02Corridor.NODE_EXIT), exitDoor.getPosition());

        for (String plateId : List.of(Level02Corridor.PLATE_DOOR, Level02Corridor.PLATE_INNER,
                Level02Corridor.PLATE_MAIN)) {
            EntitySpawnInfo plate = entity(plateId);
            assertEquals("dock_plate", plate.getEntityType());
            assertEquals(Boolean.TRUE, plate.getProperties().get("autoDock"));
        }
        assertEquals(centerOf(Level02Corridor.NODE_EXIT), entity(Level02Corridor.EXIT).getPos());
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

    private double tilesFromSpawn(String nodeId) {
        return distance(centerOf(Level02Corridor.NODE_SPAWN), centerOf(nodeId)) / TILE;
    }

    /** 走行格数 = 最短路的边数（与欧氏距离无关）。 */
    private long pathTiles(String from, String to) {
        List<String> path = shortestPath(from, to);
        assertFalse(path.isEmpty(), "应存在路径: " + from + " → " + to);
        return path.size() - 1L;
    }

    private static double distance(Vector2D a, Vector2D b) {
        return Math.hypot(a.x() - b.x(), a.y() - b.y());
    }

    private boolean reachableWithout(String from, String to, String ignore) {
        return !shortestPath(from, to, ignore).isEmpty();
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
        java.util.Map<String, String> parent = new java.util.HashMap<>();
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
}
