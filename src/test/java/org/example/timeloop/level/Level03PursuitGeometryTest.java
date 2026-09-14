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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第三关《追赶过去》几何、刻表与公平性（设定书 §3.2 / §6.4 / §11.2）。
 *
 * <p>本测试只用仓库真实类自身做校验：地形由 {@link LevelGeometryImpl} 建图，通路与最短格数用 BFS
 * 在同一张图上算，刻表由「格数 × {@link Level03Pursuit#TICKS_PER_TILE}」推出。所有数字都必须与
 * {@code Level03Pursuit} 的常量一致，<b>不写字面量</b>。</p>
 */
class Level03PursuitGeometryTest {

    private static final double TILE = Level03Pursuit.TILE_SIZE;
    private static final long TICKS_PER_TILE = Level03Pursuit.TICKS_PER_TILE;

    private final LevelData level = Level03Pursuit.build();
    private final LevelGeometry geometry = new LevelGeometryImpl(level);

    // ---------- 地形 ----------

    @Test
    void mapShapeAndWalkableCells() {
        assertEquals(Level03Pursuit.GRID_ROWS, Level03Pursuit.MAP.length, "行数");
        for (int row = 0; row < Level03Pursuit.MAP.length; row++) {
            assertEquals(Level03Pursuit.GRID_COLS, Level03Pursuit.MAP[row].length(), "第 " + row + " 行宽度");
        }
        TileType[][] grid = level.getTileGrid();
        assertEquals(Level03Pursuit.GRID_ROWS, grid.length);
        assertEquals(Level03Pursuit.GRID_COLS, grid[0].length);

        int walkable = 0;
        for (TileType[] tiles : grid) {
            for (TileType tile : tiles) {
                if (tile != TileType.WALL) {
                    walkable++;
                }
            }
        }
        assertEquals(walkable, level.getPathNodes().size(), "可走格数必须等于路径节点数");
        assertTrue(walkable >= 80, "地图太小，路线会挤在一起: " + walkable);
    }

    @Test
    void spawnIsOnTheSpawnCellAndAllMechanismCellsAreWalkable() {
        assertEquals(Level03Pursuit.cellCenter(Level03Pursuit.SPAWN_CELL[0], Level03Pursuit.SPAWN_CELL[1]),
                level.getSpawnPos());
        assertEquals(TileType.SPAWN_POINT, tileAt(Level03Pursuit.SPAWN_CELL));

        for (int[] cell : List.of(Level03Pursuit.CELL_PLATE_A, Level03Pursuit.CELL_PLATE_B,
                Level03Pursuit.CELL_PLATE_C, Level03Pursuit.CELL_PLATE_D,
                Level03Pursuit.CELL_DOOR_A, Level03Pursuit.CELL_FORK_J,
                Level03Pursuit.CELL_DOOR_B, Level03Pursuit.CELL_DOOR_C,
                Level03Pursuit.CELL_EXIT, Level03Pursuit.CELL_RAY)) {
            assertTrue(Level03Pursuit.isOpen(cell[0], cell[1]),
                    "机关所在格必须是可走格: (" + cell[0] + "," + cell[1] + ")");
        }
    }

    // ---------- 刻表：格数 × 24 ----------

    @Test
    void segmentLengthsMatchTheTickTable() {
        assertEquals(Level03Pursuit.SPAWN_TO_PLATE_A_TICKS,
                tiles(Level03Pursuit.NODE_SPAWN, Level03Pursuit.NODE_PLATE_A) * TICKS_PER_TILE,
                "出生点 → A");
        assertEquals(Level03Pursuit.PLATE_A_TO_C_TICKS,
                tiles(Level03Pursuit.NODE_PLATE_A, Level03Pursuit.NODE_PLATE_C) * TICKS_PER_TILE,
                "A → C");
        assertEquals(Level03Pursuit.PLATE_C_TO_D_TICKS,
                tiles(Level03Pursuit.NODE_PLATE_C, Level03Pursuit.NODE_PLATE_D) * TICKS_PER_TILE,
                "C → D");

        assertEquals(Level03Pursuit.SPAWN_TO_DOOR_A_TICKS,
                tiles(Level03Pursuit.NODE_SPAWN, Level03Pursuit.NODE_DOOR_A) * TICKS_PER_TILE,
                "出生点 → 门 A");
        assertEquals(Level03Pursuit.DOOR_A_TO_FORK_TICKS,
                tiles(Level03Pursuit.NODE_DOOR_A, Level03Pursuit.NODE_J) * TICKS_PER_TILE,
                "门 A → J");
        assertEquals(Level03Pursuit.FORK_TO_RAY_TICKS,
                tiles(Level03Pursuit.NODE_J, Level03Pursuit.nodeIdOf(Level03Pursuit.CELL_RAY)) * TICKS_PER_TILE,
                "J → 射线");
        assertEquals(Level03Pursuit.RAY_TO_PLATE_B_TICKS,
                tiles(Level03Pursuit.nodeIdOf(Level03Pursuit.CELL_RAY), Level03Pursuit.NODE_PLATE_B) * TICKS_PER_TILE,
                "射线 → B");
        assertEquals(Level03Pursuit.FORK_TO_DOOR_B_TICKS,
                tiles(Level03Pursuit.NODE_J, Level03Pursuit.NODE_DOOR_B) * TICKS_PER_TILE,
                "J → 门 B");
        assertEquals(Level03Pursuit.DOOR_B_TO_DOOR_C_TICKS,
                tiles(Level03Pursuit.NODE_DOOR_B, Level03Pursuit.NODE_DOOR_C) * TICKS_PER_TILE,
                "门 B → 门 C");
        assertEquals(Level03Pursuit.DOOR_C_TO_EXIT_TICKS,
                tiles(Level03Pursuit.NODE_DOOR_C, Level03Pursuit.NODE_EXIT) * TICKS_PER_TILE,
                "门 C → 出口");
    }

    @Test
    void routeTicksAreConsistentWithTheSegmentLengths() {
        assertEquals(Level03Pursuit.SPAWN_TO_PLATE_A_TICKS, Level03Pursuit.GATE_A_WINDOW_START);
        assertEquals(Level03Pursuit.GATE_A_WINDOW_START, Level03Pursuit.DOOR_A_CROSS_TICK,
                "第二轮/第三轮玩家在门 A 窗口一开始就穿门");
        assertEquals(Level03Pursuit.DOOR_A_CROSS_TICK + Level03Pursuit.DOOR_A_TO_FORK_TICKS,
                Level03Pursuit.FORK_ARRIVAL);
        assertEquals(Level03Pursuit.FORK_ARRIVAL + Level03Pursuit.FORK_TO_RAY_TICKS,
                Level03Pursuit.RAY_CROSS_TICK);
        assertEquals(Level03Pursuit.RAY_CROSS_TICK + Level03Pursuit.RAY_TO_PLATE_B_TICKS,
                Level03Pursuit.PLATE_B_ARRIVAL);
        assertEquals(Level03Pursuit.PLATE_B_ARRIVAL, Level03Pursuit.DOOR_B_CROSS_TICK,
                "门 B 由 E₂ 在 B 就位的同一刻开启");
        assertEquals(Level03Pursuit.PLATE_B_ARRIVAL + Level03Pursuit.DOOR_B_TO_DOOR_C_TICKS,
                Level03Pursuit.DOOR_C_CROSS_TICK);
        assertEquals(Level03Pursuit.DOOR_C_CROSS_TICK + Level03Pursuit.DOOR_C_TO_EXIT_TICKS,
                Level03Pursuit.EXIT_ARRIVAL);
        assertEquals(Level03Pursuit.PLATE_D_ARRIVAL, Level03Pursuit.EXIT_ARRIVAL,
                "E₁ 抵达 D 供能的刻正好是第三轮玩家走到出口的刻");
        assertTrue(Level03Pursuit.EXIT_ARRIVAL < Level03Pursuit.DURATION_TICKS,
                "通关必须能在轮长内完成");
    }

    @Test
    void firstRoundLeavesAEnoughToReachD() {
        assertEquals(Level03Pursuit.GATE_A_WINDOW_END + Level03Pursuit.PLATE_A_TO_C_TICKS,
                Level03Pursuit.PLATE_C_ARRIVAL);
        assertEquals(Level03Pursuit.PLATE_C_WINDOW_END + Level03Pursuit.PLATE_C_TO_D_TICKS,
                Level03Pursuit.PLATE_D_ARRIVAL);
        assertTrue(Level03Pursuit.PLATE_C_ARRIVAL < Level03Pursuit.PLATE_C_WINDOW_END,
                "C 板窗口必须非空");
        assertTrue(Level03Pursuit.PLATE_D_ARRIVAL < Level03Pursuit.DURATION_TICKS,
                "第一轮必须来得及在轮末前抵达 D 并驻留");
        assertTrue(Level03Pursuit.GATE_A_WINDOW_END - Level03Pursuit.GATE_A_WINDOW_START
                        >= 2 * TICKS_PER_TILE,
                "门 A 窗口不得要求单帧通过（≥2 格宽度）");
        assertTrue(Level03Pursuit.PLATE_C_WINDOW_END - Level03Pursuit.PLATE_C_ARRIVAL
                        >= 2 * TICKS_PER_TILE,
                "门 C 窗口不得要求单帧通过（≥2 格宽度）");
    }

    // ---------- 设定书 §3.2：空间关系与「无旁路」----------

    @Test
    void outerControlRouteIsReachableWithoutPassingDoorA() {
        Set<String> blocked = Set.of(Level03Pursuit.NODE_DOOR_A);
        for (String plate : List.of(Level03Pursuit.NODE_PLATE_A, Level03Pursuit.NODE_PLATE_C,
                Level03Pursuit.NODE_PLATE_D)) {
            assertTrue(reachable(Level03Pursuit.NODE_SPAWN, plate, blocked),
                    "A/C/D 必须在门 A 之外，无需穿门即可走到: " + plate);
        }
    }

    @Test
    void doorAIsTheOnlyEntranceToTheInnerRegion() {
        Set<String> blocked = Set.of(Level03Pursuit.NODE_DOOR_A);
        for (String inner : List.of(Level03Pursuit.NODE_J, Level03Pursuit.NODE_PLATE_B,
                Level03Pursuit.NODE_DOOR_B, Level03Pursuit.NODE_EXIT)) {
            assertFalse(reachable(Level03Pursuit.NODE_SPAWN, inner, blocked),
                    "删掉门 A 后内区必须不可达（门 A 是唯一入口）: " + inner);
        }
    }

    @Test
    void doorBAndDoorCAreBothMandatoryOnTheMainChannel() {
        assertFalse(reachable(Level03Pursuit.NODE_SPAWN, Level03Pursuit.NODE_EXIT,
                        Set.of(Level03Pursuit.NODE_DOOR_B)),
                "删掉门 B 后出口必须不可达");
        assertFalse(reachable(Level03Pursuit.NODE_SPAWN, Level03Pursuit.NODE_EXIT,
                        Set.of(Level03Pursuit.NODE_DOOR_C)),
                "删掉门 C 后出口必须不可达");
    }

    @Test
    void bBranchAndMainChannelOnlyMeetAtTheFork() {
        // 删掉分岔口 J：B 支路与主通道都必须不可达 —— 两者之间不得有第二条缝。
        Set<String> blockedFork = Set.of(Level03Pursuit.NODE_J);
        assertFalse(reachable(Level03Pursuit.NODE_SPAWN, Level03Pursuit.NODE_PLATE_B, blockedFork),
                "删掉 J 后 B 板必须不可达");
        assertFalse(reachable(Level03Pursuit.NODE_SPAWN, Level03Pursuit.NODE_EXIT, blockedFork),
                "删掉 J 后出口必须不可达");

        // 从 B 板出发、把 J 封死：主通道（第 13 行第 10..21 列）一格都不可达 —— 禁止从射线后方横切。
        Set<String> reachableFromB = reachableSet(Level03Pursuit.NODE_PLATE_B, blockedFork);
        for (int col = 10; col <= 21; col++) {
            assertFalse(reachableFromB.contains(Level03Pursuit.nodeId(col, 13)),
                    "B 支路不得横切到主通道: 第 13 行第 " + col + " 列");
        }
    }

    @Test
    void raySpansOnlyTheBBranchAndNeverTouchesTheMainChannel() {
        Vector2D start = new Vector2D(Level03Pursuit.RAY_X0, Level03Pursuit.RAY_Y);
        Vector2D end = new Vector2D(Level03Pursuit.RAY_X1, Level03Pursuit.RAY_Y);
        assertEquals(Level03Pursuit.cellCenter(Level03Pursuit.CELL_RAY[0], Level03Pursuit.CELL_RAY[1]).y(),
                Level03Pursuit.RAY_Y, "射线必须横穿 B 支路所在行的行中心");
        assertEquals(TILE, Level03Pursuit.RAY_X1 - Level03Pursuit.RAY_X0,
                "射线只跨一格（横跨 B 支路竖廊）");
        assertTrue(start.y() == end.y(), "射线是水平线段");

        // 射线的命中带 = 到线段距离 ≤ RAY_HIT_WIDTH 的那些格子；带内只允许出现 B 支路的格子。
        double width = Level03Pursuit.RAY_HIT_WIDTH;
        boolean sawTheRayCell = false;
        for (PathNode node : level.getPathNodes()) {
            double distance = distanceToRaySegment(node.getWorldPos());
            if (distance > width) {
                continue;
            }
            assertEquals(Level03Pursuit.nodeIdOf(Level03Pursuit.CELL_RAY), node.getId(),
                    "射线命中带内只允许 B 支路的格子（距离 " + distance + "）");
            sawTheRayCell = true;
        }
        assertTrue(sawTheRayCell, "射线必须真的横跨 B 支路（命中带内至少要有 B 支路那一格）");
        // 主通道（第 13 行）离射线至少 3 格，第三轮玩家不会进入判定范围。
        for (int col = 10; col <= 21; col++) {
            Vector2D cell = Level03Pursuit.cellCenter(col, 13);
            assertTrue(Math.abs(cell.y() - Level03Pursuit.RAY_Y) > width * 10,
                    "主通道必须远离射线判定带: 第 " + col + " 列");
        }
        // 分岔口 J 到射线的直线路径不经过主通道。
        assertEquals(Level03Pursuit.FORK_TO_RAY_TICKS,
                tiles(Level03Pursuit.NODE_J, Level03Pursuit.nodeIdOf(Level03Pursuit.CELL_RAY)) * TICKS_PER_TILE);
    }

    // ---------- 设定书 §6.4：五个公平性不等式 ----------

    @Test
    void fairnessInequalitiesHold() {
        long phaseArrivalB = Level03Pursuit.PLATE_B_ARRIVAL;
        long latestUsefulB = Level03Pursuit.LATEST_USEFUL_B_ARRIVAL;
        long laggedB = Level03Pursuit.LAGGED_B_ARRIVAL;
        long waitB = Level03Pursuit.WAIT_FOR_OFF_B_ARRIVAL;
        long normalDoorC = Level03Pursuit.DOOR_C_CROSS_TICK;
        long lateDoorC = Level03Pursuit.LATE_DOOR_C_ARRIVAL;
        long doorCClose = Level03Pursuit.PLATE_C_WINDOW_END;
        long margin = Level03Pursuit.SUCCESS_MARGIN_TICKS;

        // ① 正确相位：能赶上
        assertTrue(phaseArrivalB <= latestUsefulB,
                "phaseArrivalB(" + phaseArrivalB + ") 必须 ≤ latestUsefulB(" + latestUsefulB + ")");
        // ② 被命中：迟到 30 刻，必然错过
        assertTrue(laggedB > latestUsefulB,
                "laggedArrivalB(" + laggedB + ") 必须 > latestUsefulB(" + latestUsefulB + ")");
        // ③ 等待射线关闭：迟到 60 刻，必然错过
        assertTrue(waitB > latestUsefulB,
                "waitForOffArrivalB(" + waitB + ") 必须 > latestUsefulB(" + latestUsefulB + ")");
        // ④ 正解到门 C 后仍有 successMargin 刻余量
        assertTrue(normalDoorC + margin <= doorCClose,
                "normalDoorC(" + normalDoorC + ") + margin(" + margin + ") 必须 ≤ doorCClose(" + doorCClose + ")");
        // ⑤ 迟到路线到门 C 时门已关
        assertTrue(lateDoorC > doorCClose,
                "lateDoorC(" + lateDoorC + ") 必须 > doorCClose(" + doorCClose + ")");

        // latestUsefulB 的定义必须由「门 C 关闭刻 − 门B→门C 路程」推出，不能拍脑袋。
        assertEquals(doorCClose - Level03Pursuit.DOOR_B_TO_DOOR_C_TICKS, latestUsefulB);
        // 受击迟到量必须取满（射线→B 路程 ≥ 60 刻），否则 ② 的严格不等式会被吃掉。
        assertTrue(Level03Pursuit.RAY_TO_PLATE_B_TICKS * 2 >= Level03Pursuit.WAIT_FOR_OFF_DELAY_TICKS,
                "射线→B 必须 ≥ 60 刻，受击迟到量才等于 30 刻");
    }

    /**
     * 余量上限：受击只迟 30 刻，而「正解留余量」与「受击必失败」共用同一段余量，
     * 因此 {@code margin + (lateDoorC - doorCClose) == HIT_DELAY_TICKS}，且 {@code margin <= 29}。
     */
    @Test
    void marginIsCappedByTheGlobalSlowdownAndTheSpecRangeMustYield() {
        long margin = Level03Pursuit.SUCCESS_MARGIN_TICKS;
        long lateOvershoot = Level03Pursuit.LATE_DOOR_C_ARRIVAL - Level03Pursuit.PLATE_C_WINDOW_END;
        assertEquals(Level03Pursuit.HIT_DELAY_TICKS, margin + lateOvershoot,
                "正解余量 + 迟到超出量 必须恰好等于受击迟到量（同一段余量被两条要求共享）");
        assertTrue(margin < Level03Pursuit.HIT_DELAY_TICKS,
                "余量必须严格小于受击迟到量，否则受击路线不会失败");
        assertTrue(lateOvershoot > 0, "受击路线必须真的晚于门 C 关闭刻");
        // 设定书 §11.3 要求「≥30 刻余量」——与 §6.4「受击必失败」在既有全局减速下互斥，
        // 本关取 <30；这条断言把该结论钉住，避免后人误以为可以同时满足。
        assertTrue(margin < 30,
                "既有全局减速（0.5x / 60 刻 ⇒ 迟 30 刻）下，余量不可能 ≥30");
    }

    // ---------- 设定书 §6.4：射线初相 ----------

    @Test
    void rayIsActiveWhenTheSecondRoundPlayerReachesIt() {
        assertTrue(Level03Pursuit.RAY_CROSS_PHASE >= Level03Pursuit.RAY_ACTIVE_START_TICK,
                "抵达射线时必须在 ACTIVE 内，否则「必须下潜」不成立");
        assertTrue(Level03Pursuit.RAY_CROSS_PHASE
                        < Level03Pursuit.RAY_ACTIVE_START_TICK + Level03Pursuit.RAY_ACTIVE_DURATION_TICKS,
                "抵达射线时必须在 ACTIVE 内");
        long warningLead = Level03Pursuit.RAY_ACTIVE_START_TICK - Level03Pursuit.RAY_WARNING_START_TICK;
        assertEquals(Level03Pursuit.RAY_WARNING_DURATION_TICKS, warningLead);
        assertTrue(warningLead >= 30, "预警必须给 ≥24–30 刻反应预算");
        assertTrue(Level03Pursuit.RAY_ACTIVE_DURATION_TICKS >= 30,
                "ACTIVE 时长必须容得下整个穿越过程（不许单帧穿线）");
        assertEquals(Level03Pursuit.RAY_WARNING_START_TICK + Level03Pursuit.RAY_WARNING_DURATION_TICKS
                        + Level03Pursuit.RAY_ACTIVE_DURATION_TICKS,
                Level03Pursuit.RAY_CYCLE_TICKS);
        // 第二轮的「等待射线关闭」路线：等到 ACTIVE 结束即迟 60 刻。
        assertEquals(Level03Pursuit.RAY_ACTIVE_DURATION_TICKS, Level03Pursuit.WAIT_FOR_OFF_DELAY_TICKS);
        assertTrue(Level03Pursuit.WAIT_FOR_OFF_DELAY_TICKS > Level03Pursuit.SUCCESS_MARGIN_TICKS,
                "等待必须比正解余量更贵，否则等待也能过门 C");
    }

    // ---------- 机关接线 ----------

    @Test
    void levelWiresFourPlatesThreeRouteDoorsOneExitGateOneRayAndOneExit() {
        List<EntitySpawnInfo> plates = level.getEntitySpawnList().stream()
                .filter(e -> "dock_plate".equals(e.getEntityType())).toList();
        assertEquals(4, plates.size(), "四块驻留板");
        Set<String> plateIds = new HashSet<>();
        for (EntitySpawnInfo plate : plates) {
            plateIds.add(plate.getId());
            assertTrue(Boolean.TRUE.equals(plate.getProperties().get("autoDock")), "驻留板必须 autoDock");
            assertFalse(plate.getProperties().containsKey("role"),
                    "第三关四块板都是普通板，不得有 role=switch: " + plate.getId());
        }
        assertEquals(Set.of(Level03Pursuit.PLATE_A, Level03Pursuit.PLATE_B,
                Level03Pursuit.PLATE_C, Level03Pursuit.PLATE_D), plateIds);

        List<EntitySpawnInfo> rays = level.getEntitySpawnList().stream()
                .filter(e -> "ray".equals(e.getEntityType())).toList();
        assertEquals(1, rays.size(), "一束射线");
        assertEquals(Level03Pursuit.RAY_B, rays.get(0).getId());

        List<EntitySpawnInfo> exits = level.getEntitySpawnList().stream()
                .filter(e -> "exit_terminal".equals(e.getEntityType())).toList();
        assertEquals(1, exits.size(), "一个出口");
        assertEquals(Level03Pursuit.EXIT, exits.get(0).getId());

        assertEquals(4, level.getDoors().size(), "3 扇路线门 + 1 扇终点供能闸");
        assertEquals(Set.of(Level03Pursuit.PLATE_A), requiredPlates(Level03Pursuit.DOOR_A));
        assertEquals(Set.of(Level03Pursuit.PLATE_B), requiredPlates(Level03Pursuit.DOOR_B));
        assertEquals(Set.of(Level03Pursuit.PLATE_C), requiredPlates(Level03Pursuit.DOOR_C));
        // D 板只「供能」：它开的是与出口同格的终点供能闸，不直接通关。
        assertEquals(Set.of(Level03Pursuit.PLATE_D), requiredPlates(Level03Pursuit.DOOR_EXIT));
        assertEquals(Level03Pursuit.cellCenter(Level03Pursuit.CELL_EXIT[0], Level03Pursuit.CELL_EXIT[1]),
                doorPosition(Level03Pursuit.DOOR_EXIT), "终点供能闸必须与出口同格");
        assertThrows(IllegalArgumentException.class,
                () -> StableIdValidator.requireMechanismId("L03_plate_A", "plate", "夹具前提"),
                "夹具前提：稳定 ID 必须全小写");
    }

    @Test
    void levelParametersMatchTheSettingBook() {
        assertEquals(1200L, Level03Pursuit.DURATION_TICKS);
        assertEquals(3, Level03Pursuit.MAX_ROUNDS, "maxRounds = 3（设定书 §8.3）");
        assertEquals(2, Level03Pursuit.ECHO_LIFE_L, "L = 2（设定书 §7）");
        assertEquals(1200L, level.getDurationTicks());
        assertEquals(3, level.getMaxRounds());
        assertEquals(2, level.getEchoLifeL());
    }

    // ---------- 辅助 ----------

    private TileType tileAt(int[] cell) {
        return level.getTileGrid()[cell[1]][cell[0]];
    }

    /** 点到射线线段（水平线段 {@code [RAY_X0, RAY_X1] × RAY_Y}）的欧氏距离。 */
    private static double distanceToRaySegment(Vector2D point) {
        double clampedX = Math.max(Level03Pursuit.RAY_X0,
                Math.min(Level03Pursuit.RAY_X1, point.x()));
        double dx = point.x() - clampedX;
        double dy = point.y() - Level03Pursuit.RAY_Y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /** 两点之间的最短格数（在关卡通路上，门视为可通行）。 */
    private long tiles(String fromNodeId, String toNodeId) {
        List<String> path = shortestPath(fromNodeId, toNodeId, Set.of());
        assertFalse(path.isEmpty(), "缺少通路: " + fromNodeId + " → " + toNodeId);
        return path.size() - 1;
    }

    private boolean reachable(String fromNodeId, String toNodeId, Set<String> blocked) {
        return !shortestPath(fromNodeId, toNodeId, blocked).isEmpty();
    }

    private Set<String> reachableSet(String fromNodeId, Set<String> blocked) {
        Set<String> seen = new HashSet<>();
        if (blocked.contains(fromNodeId)) {
            return seen;
        }
        Deque<String> queue = new ArrayDeque<>();
        queue.add(fromNodeId);
        seen.add(fromNodeId);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String next : geometry.getNeighbors(current)) {
                if (!blocked.contains(next) && seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return seen;
    }

    /** BFS 最短路；{@code blocked} 里的节点一律不可经过（用于「删掉某扇门/某个岔口」的负向断言）。 */
    private List<String> shortestPath(String fromNodeId, String toNodeId, Set<String> blocked) {
        if (blocked.contains(fromNodeId) || blocked.contains(toNodeId)) {
            return List.of();
        }
        Deque<String> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        Map<String, String> parent = new HashMap<>();
        queue.add(fromNodeId);
        seen.add(fromNodeId);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equals(toNodeId)) {
                List<String> path = new ArrayList<>();
                for (String node = toNodeId; node != null; node = parent.get(node)) {
                    path.add(0, node);
                }
                return path;
            }
            for (String next : geometry.getNeighbors(current)) {
                if (!blocked.contains(next) && seen.add(next)) {
                    parent.put(next, current);
                    queue.add(next);
                }
            }
        }
        return List.of();
    }

    private Set<String> requiredPlates(String doorId) {
        return level.getDoors().stream()
                .filter(d -> doorId.equals(d.getId()))
                .map(DoorInfo::getRequiredPlateIds)
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少门: " + doorId));
    }

    private Vector2D doorPosition(String doorId) {
        return level.getDoors().stream()
                .filter(d -> doorId.equals(d.getId()))
                .map(DoorInfo::getPosition)
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少门: " + doorId));
    }
}
