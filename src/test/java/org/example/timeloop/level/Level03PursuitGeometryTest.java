package org.example.timeloop.level;

import org.example.timeloop.level.model.DoorInfo;
import org.example.timeloop.level.model.EntitySpawnInfo;
import org.example.timeloop.level.model.LevelData;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第三关《追赶过去》重排 v3 的几何与刻表冻结测试（开发三）。
 *
 * <p>设计文档（设计图 + 坐标刻表）里的每一个数字都在这里有对应断言：地形 239 格、13 段段长、
 * 咽喉点（门 A / 门 B / 射线 / 门 C）、射线初相、窗口长度、七条公平性不等式、出口闸三条件。
 * 任一数字被改坏，本类会先把版本钉在红上，而不是等到第三关玩不通才发现。</p>
 *
 * <p>所有「格数」都由 {@link Level03Pursuit#isOpen} 现算 BFS，不手抄地图；刻数 = 格数 ×
 * {@link Level03Pursuit#TICKS_PER_TILE}。</p>
 */
class Level03PursuitGeometryTest {

    private static final long TILE = Level03Pursuit.TICKS_PER_TILE;
    private static final int[][] DELTAS = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};

    // ---------- 地形 ----------

    @Test
    void mapIsTwentyEightBySixteenAndEveryRowIsFullWidth() {
        assertEquals(28, Level03Pursuit.GRID_COLS);
        assertEquals(16, Level03Pursuit.GRID_ROWS);
        assertEquals(Level03Pursuit.GRID_ROWS, Level03Pursuit.MAP.length, "行数");
        for (int row = 0; row < Level03Pursuit.GRID_ROWS; row++) {
            assertEquals(Level03Pursuit.GRID_COLS, Level03Pursuit.MAP[row].length(),
                    "第 " + row + " 行宽度");
        }
        assertEquals(48.0, Level03Pursuit.TILE_SIZE);
        assertEquals(24L, TILE, "每格 24 刻（2 单位/刻 × 48）");
        assertEquals(1260L, Level03Pursuit.DURATION_TICKS, "轮长 21s × 60 刻");
        assertEquals(3, Level03Pursuit.MAX_ROUNDS);
        assertEquals(2, Level03Pursuit.ECHO_LIFE_L, "第三关残影寿命 L = 2");
    }

    @Test
    void floorCountMatchesTheFrozenDesign() {
        int floor = 0;
        for (int row = 0; row < Level03Pursuit.GRID_ROWS; row++) {
            for (int col = 0; col < Level03Pursuit.GRID_COLS; col++) {
                if (Level03Pursuit.isOpen(col, row)) {
                    floor++;
                }
            }
        }
        assertEquals(238, floor, "地板 238/448 = 53%（C 板挪到 (12,3) + (6,14) 补墙）");
    }

    @Test
    void markersSitOnTheDocumentedCells() {
        expectMarker('S', Level03Pursuit.SPAWN_CELL);
        expectMarker('A', Level03Pursuit.CELL_PLATE_A);
        expectMarker('C', Level03Pursuit.CELL_PLATE_C);
        expectMarker('K', Level03Pursuit.CELL_PLATE_K);
        expectMarker('B', Level03Pursuit.CELL_PLATE_B);
        expectMarker('2', Level03Pursuit.CELL_SWITCH_S2);
        expectMarker('3', Level03Pursuit.CELL_SWITCH_S3);
        expectMarker('a', Level03Pursuit.CELL_DOOR_A);
        expectMarker('J', Level03Pursuit.CELL_FORK_J);
        expectMarker('b', Level03Pursuit.CELL_DOOR_B);
        expectMarker('c', Level03Pursuit.CELL_DOOR_C);
        expectMarker('x', Level03Pursuit.CELL_EXIT);
        expectMarker('R', Level03Pursuit.CELL_RAY);
    }

    private static void expectMarker(char marker, int[] cell) {
        assertEquals(String.valueOf(marker), String.valueOf(
                        Level03Pursuit.MAP[cell[1]].charAt(cell[0])),
                "标记 '" + marker + "' 应在 (" + cell[0] + "," + cell[1] + ")");
    }

    // ---------- 段长 ----------

    @Test
    void segmentLengthsMatchTheDesignTable() {
        expectTiles("出生点 → A", Level03Pursuit.SPAWN_CELL, Level03Pursuit.CELL_PLATE_A,
                Level03Pursuit.SPAWN_TO_PLATE_A_TICKS);
        expectTiles("A → C", Level03Pursuit.CELL_PLATE_A, Level03Pursuit.CELL_PLATE_C,
                Level03Pursuit.PLATE_A_TO_C_TICKS);
        expectTiles("C → K", Level03Pursuit.CELL_PLATE_C, Level03Pursuit.CELL_PLATE_K,
                Level03Pursuit.PLATE_C_TO_K_TICKS);
        expectTiles("出生点 → 门A", Level03Pursuit.SPAWN_CELL, Level03Pursuit.CELL_DOOR_A,
                Level03Pursuit.SPAWN_TO_DOOR_A_TICKS);
        expectTiles("门A → J", Level03Pursuit.CELL_DOOR_A, Level03Pursuit.CELL_FORK_J,
                Level03Pursuit.DOOR_A_TO_FORK_TICKS);
        expectTiles("J → S₂", Level03Pursuit.CELL_FORK_J, Level03Pursuit.CELL_SWITCH_S2,
                Level03Pursuit.FORK_TO_SWITCH_S2_TICKS);
        expectTiles("S₂ → 射线", Level03Pursuit.CELL_SWITCH_S2, Level03Pursuit.CELL_RAY,
                Level03Pursuit.SWITCH_S2_TO_RAY_TICKS);
        expectTiles("射线 → 门C", Level03Pursuit.CELL_RAY, Level03Pursuit.CELL_DOOR_C,
                Level03Pursuit.RAY_TO_DOOR_C_TICKS);
        expectTiles("门C → B", Level03Pursuit.CELL_DOOR_C, Level03Pursuit.CELL_PLATE_B,
                Level03Pursuit.DOOR_C_TO_PLATE_B_TICKS);
        expectTiles("J → 门B", Level03Pursuit.CELL_FORK_J, Level03Pursuit.CELL_DOOR_B,
                Level03Pursuit.FORK_TO_DOOR_B_TICKS);
        expectTiles("门B → S₃", Level03Pursuit.CELL_DOOR_B, Level03Pursuit.CELL_SWITCH_S3,
                Level03Pursuit.SWITCH_S3_TO_DOOR_B_TICKS);
        expectTiles("门B → 门C", Level03Pursuit.CELL_DOOR_B, Level03Pursuit.CELL_DOOR_C,
                Level03Pursuit.DOOR_B_TO_DOOR_C_TICKS);
        expectTiles("门C → 出口", Level03Pursuit.CELL_DOOR_C, Level03Pursuit.CELL_EXIT,
                Level03Pursuit.DOOR_C_TO_EXIT_TICKS);
    }

    private static void expectTiles(String name, int[] from, int[] to, long ticks) {
        assertEquals(ticks / TILE, bfs(from, to, Set.of()),
                name + "：段长（刻 " + ticks + "）");
    }

    // ---------- 咽喉点 ----------

    @Test
    void doorAIsTheOnlyInnerRegionEntrance() {
        assertTrue(bfs(Level03Pursuit.SPAWN_CELL, Level03Pursuit.CELL_PLATE_B, Set.of()) > 0,
                "夹具前提：不封门 A 时内区可达");
        assertEquals(-1, bfs(Level03Pursuit.SPAWN_CELL, Level03Pursuit.CELL_PLATE_B,
                        Set.of(key(Level03Pursuit.CELL_DOOR_A))),
                "门 A 是内区唯一入口：封掉它，B 板必须不可达");
        for (int row = 0; row < Level03Pursuit.GRID_ROWS; row++) {
            if (row == Level03Pursuit.CELL_DOOR_A[1]) {
                continue;
            }
            assertFalse(Level03Pursuit.isOpen(Level03Pursuit.CELL_DOOR_A[0], row),
                    "第 13 列第 " + row + " 行必须是墙（内区唯一入口）");
        }
    }

    @Test
    void doorBIsTheOnlySwitchRoomEntrance() {
        assertTrue(bfs(Level03Pursuit.SPAWN_CELL, Level03Pursuit.CELL_SWITCH_S3, Set.of()) > 0,
                "夹具前提：不封门 B 时开关室可达");
        assertEquals(-1, bfs(Level03Pursuit.SPAWN_CELL, Level03Pursuit.CELL_SWITCH_S3,
                        Set.of(key(Level03Pursuit.CELL_DOOR_B))),
                "门 B 是东北开关室唯一进口：封掉它，S₃ 必须不可达");
    }

    @Test
    void theRayCellIsTheOnlyWayFromTheForkToDoorCWhileDoorBIsLocked() {
        // 第二轮（门 B 还锁着）时，分岔口 J → 门 C 只能穿射线格（S₂ 已挪到门 C 之后的东南回环上）
        assertEquals(-1, bfs(Level03Pursuit.CELL_FORK_J, Level03Pursuit.CELL_DOOR_C,
                        Set.of(key(Level03Pursuit.CELL_RAY), key(Level03Pursuit.CELL_DOOR_B))),
                "门 B 锁着时，封掉射线格后 J 必须到不了门 C");
        // 对照：只封射线、不锁门 B —— 可以从北边绕行，说明两条咽喉互为前提（设计意图）
        assertTrue(bfs(Level03Pursuit.CELL_FORK_J, Level03Pursuit.CELL_DOOR_C,
                        Set.of(key(Level03Pursuit.CELL_RAY))) > 0,
                "只封射线而不锁门 B 时应存在绕行（否则「门 B 必经」不成立）");
        assertFalse(Level03Pursuit.isOpen(Level03Pursuit.CELL_RAY[0], Level03Pursuit.CELL_RAY[1] - 1),
                "射线格北邻必须是墙");
        assertFalse(Level03Pursuit.isOpen(Level03Pursuit.CELL_RAY[0], Level03Pursuit.CELL_RAY[1] + 1),
                "射线格南邻必须是墙");
    }

    @Test
    void doorCIsTheOnlyWayToPlateBAndToTheExit() {
        assertEquals(-1, bfs(Level03Pursuit.SPAWN_CELL, Level03Pursuit.CELL_PLATE_B,
                        Set.of(key(Level03Pursuit.CELL_DOOR_C))),
                "封掉门 C，B 板必须不可达（先过门 C 才能拿到 B 板）");
        assertEquals(-1, bfs(Level03Pursuit.SPAWN_CELL, Level03Pursuit.CELL_EXIT,
                        Set.of(key(Level03Pursuit.CELL_DOOR_C))),
                "封掉门 C，出口必须不可达");
    }

    // ---------- 射线 ----------

    @Test
    void rayRunsVerticallyAcrossOneCorridorCell() {
        assertEquals((Level03Pursuit.CELL_RAY[0] + 0.5) * 48.0, Level03Pursuit.RAY_X, 1e-9,
                "v3 的射线是竖的：x 固定在本列列中心");
        assertEquals(Level03Pursuit.CELL_RAY[1] * 48.0, Level03Pursuit.RAY_Y0, 1e-9, "上端点");
        assertEquals((Level03Pursuit.CELL_RAY[1] + 1) * 48.0, Level03Pursuit.RAY_Y1, 1e-9, "下端点");
        assertEquals(48.0, Level03Pursuit.RAY_Y1 - Level03Pursuit.RAY_Y0, 1e-9, "竖跨恰好一格");

        double band = Level03Pursuit.RAY_HIT_WIDTH;
        assertEquals(0.20 * 48.0, band, 1e-9, "命中半宽沿用第二关口径");
        for (int col : new int[] {Level03Pursuit.CELL_RAY[0] - 1, Level03Pursuit.CELL_RAY[0] + 1}) {
            double x = (col + 0.5) * 48.0;
            assertTrue(Math.abs(x - Level03Pursuit.RAY_X) > band,
                    "相邻格中心 x=" + x + " 不得落在判定带内");
        }
        for (int[] cell : List.of(Level03Pursuit.CELL_PLATE_B, Level03Pursuit.CELL_SWITCH_S2,
                Level03Pursuit.CELL_SWITCH_S3, Level03Pursuit.CELL_DOOR_C, Level03Pursuit.CELL_PLATE_K)) {
            double x = (cell[0] + 0.5) * 48.0;
            double y = (cell[1] + 0.5) * 48.0;
            assertTrue(Math.abs(x - Level03Pursuit.RAY_X) > band
                            || Math.abs(y - Level03Pursuit.RAY_Y0) > band,
                    "机关 (" + cell[0] + "," + cell[1] + ") 不得落在射线判定带里");
        }
    }

    @Test
    void rayPhaseMakesE2ArrivalFallInsideTheActiveWindow() {
        assertEquals(72L, Level03Pursuit.RAY_WARNING_DURATION_TICKS, "预警 72 刻");
        assertEquals(60L, Level03Pursuit.RAY_ACTIVE_DURATION_TICKS, "激活 60 刻");
        assertEquals(132L, Level03Pursuit.RAY_OFF_DURATION_TICKS, "OFF 132 刻（528→264→132 两次减半）");
        assertEquals(204L, Level03Pursuit.RAY_ACTIVE_START_TICK, "周期内激活起点 = 132 + 72");
        assertEquals(132L, Level03Pursuit.RAY_WARNING_START_TICK, "周期内预警起点 = OFF 段长度");
        assertEquals(264L, Level03Pursuit.RAY_CYCLE_TICKS, "周期 = 132 + 72 + 60");
        assertEquals(Level03Pursuit.RAY_CROSS_TICK, Level03Pursuit.RAY_ACTIVE_START_ABSOLUTE_TICK,
                "E₂ 抵达刻（600）就是绝对激活起点");
        assertEquals(528L, Level03Pursuit.RAY_WARNING_START_ABSOLUTE_TICK,
                "绝对预警起点仍是 528（教学锚点不随周期变化）");
        long phase = Level03Pursuit.RAY_CROSS_PHASE;
        assertTrue(phase >= Level03Pursuit.RAY_ACTIVE_START_TICK
                        && phase < Level03Pursuit.RAY_ACTIVE_START_TICK
                        + Level03Pursuit.RAY_ACTIVE_DURATION_TICKS,
                "抵达射线的相位必须落在 ACTIVE 段内，实际 " + phase);
    }

    // ---------- 刻表与公平性 ----------

    @Test
    void tickTableMatchesTheFrozenDesign() {
        assertEquals(312L, Level03Pursuit.GATE_A_WINDOW_START, "A 窗口起（出生点→A 13 格）");
        assertEquals(360L, Level03Pursuit.GATE_A_WINDOW_END, "A 窗口末（HOLD_A 1 格）");
        assertEquals(528L, Level03Pursuit.PLATE_C_ARRIVAL, "E₁ 抵 C（A→C 7 格）");
        assertEquals(624L, Level03Pursuit.DOOR_C_CROSS_BY_E2_TICK, "E₂ 穿门 C");
        assertEquals(744L, Level03Pursuit.PLATE_B_ARRIVAL, "E₂ 抵 B");
        assertEquals(384L, Level03Pursuit.FORK_ARRIVAL, "J");
        assertEquals(1080L, Level03Pursuit.SWITCH_S2_ARRIVAL, "S₂（第三轮从门 C 走过去 4 格）");
        assertEquals(600L, Level03Pursuit.RAY_CROSS_TICK, "E₂ 抵射线");
        assertEquals(624L, Level03Pursuit.E3_DOOR_B_ARRIVAL, "E₃ 抵门 B（门外等）");
        assertEquals(744L, Level03Pursuit.DOOR_B_CROSS_TICK, "E₃ 穿门 B");
        assertEquals(792L, Level03Pursuit.SWITCH_S3_ARRIVAL, "S₃（门 B→S₃ 2 格）");
        assertEquals(984L, Level03Pursuit.DOOR_C_CROSS_TICK, "E₃ 穿门 C");
        assertEquals(1200L, Level03Pursuit.EXIT_ARRIVAL, "E₃ 抵出口");
        assertEquals(1008L, Level03Pursuit.PLATE_C_WINDOW_END, "C 窗口末");
        assertEquals(1248L, Level03Pursuit.PLATE_K_ARRIVAL, "E₁ 抵 K（C→K 10 格）");
        assertTrue(Level03Pursuit.PLATE_K_ARRIVAL < Level03Pursuit.DURATION_TICKS,
                "K 板驻留必须早于轮末，玩家才来得及按 E");
        assertTrue(Level03Pursuit.EXIT_ARRIVAL < Level03Pursuit.PLATE_K_ARRIVAL,
                "v3 的 K 供能晚于到出口刻：玩家要在出口等 —— 设计意图");
    }

    @Test
    void windowWidthsMatchTheHoldConstants() {
        assertEquals(Level03Pursuit.HOLD_A_TILES * TILE,
                Level03Pursuit.GATE_A_WINDOW_END - Level03Pursuit.GATE_A_WINDOW_START,
                "A 窗口 = HOLD_A_TILES 格");
        assertEquals(2, Level03Pursuit.HOLD_A_TILES, "A 驻留 2 格：E₁ 走 13 格到 A，窗口要盖住玩家 336 穿门刻");
        assertEquals(Level03Pursuit.HOLD_C_TILES * TILE,
                Level03Pursuit.PLATE_C_WINDOW_END - Level03Pursuit.PLATE_C_ARRIVAL,
                "C 窗口 = HOLD_C_TILES 格");
        assertEquals(20, Level03Pursuit.HOLD_C_TILES);
        assertTrue(Level03Pursuit.PLATE_C_ARRIVAL <= Level03Pursuit.DOOR_C_CROSS_BY_E2_TICK,
                "⑤ E₂ 到门 C 时门已经开着（不必空等）");
        assertTrue(Level03Pursuit.DOOR_C_CROSS_BY_E2_TICK < Level03Pursuit.DOOR_C_CROSS_TICK,
                "E₂ 必须比 E₃ 先穿门 C");
    }

    @Test
    void fairnessInequalitiesHold() {
        assertTrue(Level03Pursuit.PLATE_C_WINDOW_END - Level03Pursuit.DOOR_C_CROSS_TICK
                        >= Level03Pursuit.SUCCESS_MARGIN_TICKS,
                "① 正解余量");
        assertTrue(Level03Pursuit.LAGGED_B_ARRIVAL > Level03Pursuit.LATEST_USEFUL_B_ARRIVAL,
                "② B 板失效：受击 " + Level03Pursuit.LAGGED_B_ARRIVAL
                        + " vs 最晚有用 " + Level03Pursuit.LATEST_USEFUL_B_ARRIVAL);
        assertTrue(Level03Pursuit.LATE_DOOR_C_ARRIVAL > Level03Pursuit.PLATE_C_WINDOW_END,
                "② 受击穿门 C 时刻必须晚于窗口末（受击必失败）");
        assertTrue(Level03Pursuit.WAIT_FOR_OFF_B_ARRIVAL > Level03Pursuit.LATEST_USEFUL_B_ARRIVAL,
                "③ B 板失效：等待");
        assertTrue(Level03Pursuit.WAIT_DOOR_C_ARRIVAL > Level03Pursuit.PLATE_C_WINDOW_END,
                "③ 等待路线穿门 C 时刻必须晚于窗口末");
        assertEquals(Level03Pursuit.HIT_DELAY_TICKS,
                Level03Pursuit.SUCCESS_MARGIN_TICKS
                        + (Level03Pursuit.LATE_DOOR_C_ARRIVAL - Level03Pursuit.PLATE_C_WINDOW_END),
                "④ 余量 24 + 超出 6 = 30");
        assertEquals(30L, Level03Pursuit.HIT_DELAY_TICKS);
        assertEquals(24L, Level03Pursuit.SUCCESS_MARGIN_TICKS);
        assertTrue(Level03Pursuit.E3_DOOR_B_ARRIVAL <= Level03Pursuit.DOOR_B_OPEN_TICK,
                "⑥ E₃ 抵门 B 早于开门刻");
        assertTrue(Level03Pursuit.PLATE_K_ARRIVAL < Level03Pursuit.DURATION_TICKS, "⑦ 供能早于轮末");
    }

    // ---------- LevelData ----------

    @Test
    void levelDataBuildsFourPlatesTwoSwitchesThreeDoorsAndTheExitGate() {
        LevelData level = Level03Pursuit.build();
        int plainPlates = 0;
        int switchingPlates = 0;
        for (EntitySpawnInfo entity : level.getEntitySpawnList()) {
            if (!"dock_plate".equals(entity.getEntityType())) {
                continue;
            }
            assertEquals(Boolean.TRUE, entity.getProperties().get("autoDock"),
                    entity.getId() + " 必须是自动驻留板");
            if ("switch".equals(entity.getProperties().get("role"))) {
                switchingPlates++;
                assertTrue(entity.getId().equals(Level03Pursuit.SWITCH_S2)
                                || entity.getId().equals(Level03Pursuit.SWITCH_S3),
                        "只有 S₂ / S₃ 是开关，实际 " + entity.getId());
            } else {
                plainPlates++;
            }
        }
        assertEquals(4, plainPlates, "普通驻留板 A / C / K / B");
        assertEquals(2, switchingPlates, "锁存开关 S₂ / S₃");

        assertEquals(4, level.getDoors().size(), "门 A / B / C + 终点供能闸");
        DoorInfo gate = level.getDoors().stream()
                .filter(d -> Level03Pursuit.DOOR_EXIT.equals(d.getId()))
                .findFirst().orElseThrow();
        assertEquals(Set.of(Level03Pursuit.SWITCH_S2, Level03Pursuit.SWITCH_S3, Level03Pursuit.PLATE_K),
                gate.getRequiredPlateIds(), "出口闸条件 = {S₂, S₃, K}");
        for (String doorId : List.of(Level03Pursuit.DOOR_A, Level03Pursuit.DOOR_B, Level03Pursuit.DOOR_C)) {
            DoorInfo door = level.getDoors().stream()
                    .filter(d -> doorId.equals(d.getId())).findFirst().orElseThrow();
            assertEquals(1, door.getRequiredPlateIds().size(), doorId + " 只需一块板");
        }
        assertEquals(Level03Pursuit.cellCenter(Level03Pursuit.CELL_EXIT[0], Level03Pursuit.CELL_EXIT[1]),
                gate.getPosition(), "终点闸与出口同格");

        for (int[] cell : List.of(Level03Pursuit.CELL_PLATE_A, Level03Pursuit.CELL_PLATE_C,
                Level03Pursuit.CELL_PLATE_K, Level03Pursuit.CELL_PLATE_B, Level03Pursuit.CELL_SWITCH_S2,
                Level03Pursuit.CELL_SWITCH_S3, Level03Pursuit.CELL_DOOR_A, Level03Pursuit.CELL_DOOR_B,
                Level03Pursuit.CELL_DOOR_C, Level03Pursuit.CELL_EXIT, Level03Pursuit.CELL_RAY)) {
            assertTrue(Level03Pursuit.isOpen(cell[0], cell[1]),
                    "机关格 (" + cell[0] + "," + cell[1] + ") 必须是可走地板");
        }
        for (EntitySpawnInfo entity : level.getEntitySpawnList()) {
            if ("dock_plate".equals(entity.getEntityType())) {
                assertNotNull(entity.getPathNodeId(), entity.getId() + " 必须有路径节点");
            }
        }
        assertEquals(Level03Pursuit.cellCenter(Level03Pursuit.SPAWN_CELL[0], Level03Pursuit.SPAWN_CELL[1]),
                level.getSpawnPos(), "出生点");
    }

    // ---------- 工具 ----------

    private static String key(int[] cell) {
        return cell[0] + "," + cell[1];
    }

    /** 四方向 BFS 最短格数；{@code blocked} 中的格不可通行；不可达返回 -1。 */
    private static int bfs(int[] from, int[] to, Set<String> blocked) {
        Map<String, Integer> dist = new HashMap<>();
        Deque<int[]> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>(blocked);
        dist.put(key(from), 0);
        seen.add(key(from));
        queue.add(from);
        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            if (key(current).equals(key(to))) {
                return dist.get(key(current));
            }
            for (int[] delta : DELTAS) {
                int col = current[0] + delta[0];
                int row = current[1] + delta[1];
                if (!Level03Pursuit.isOpen(col, row)) {
                    continue;
                }
                String next = col + "," + row;
                if (!seen.add(next)) {
                    continue;
                }
                dist.put(next, dist.get(key(current)) + 1);
                queue.add(new int[] {col, row});
            }
        }
        return -1;
    }
}
