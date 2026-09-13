package org.example.timeloop.level;

import org.example.timeloop.level.model.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 第一关「留下的脚步」关卡数据。
 *
 * <p>地图为 <b>28 列 × 16 行</b>，每格 48.0 世界单位。玩家出生在顶部通道口
 * (9, 2)/(10, 2) 一带的 SPAWN_POINT 格；两块驻留板分置左右两个房间；
 * 终点终端紧挨右驻留板；门放在底部中偏右。</p>
 *
 * <p><b>通关链路</b>（MAX_ROUNDS=3、ECHO_LIFE_L=1，每轮只有上一轮的残影在场）：</p>
 * <ol>
 *   <li>第 1 轮走到左驻留板并停住，路线写入本轮记录；</li>
 *   <li>第 2 轮残影复现该路线压住左板，玩家同时压住右板 —— 两板同时被占，
 *       门派发 DOOR_UNLOCKED，终点 {@code doorUnlocked} 锁存为真；</li>
 *   <li>玩家站在右板上（距终点 48 &lt;= 交互半径 72）原地按 E 通关。</li>
 * </ol>
 *
 * <p><b>不要</b>把终点挪到离右驻留板超过 72 世界单位的位置：那样玩家必须下板才能交互，
 * 而下板即释放占用、门立刻回锁，关卡将无解。</p>
 */
public class Level01Footsteps {

    private static final double TILE_SIZE = 48.0;
    private static final long DURATION_TICKS = 16 * 60L;
    private static final int MAX_ROUNDS = 3;
    private static final int ECHO_LIFE_L = 1;

    public static LevelData build() {
        TileType[][] grid = createTileGrid();
        Vector2D spawnWorldPos = cellCenter(10, 2);
        List<PathNode> nodeList = buildPathNodes();
        List<EntitySpawnInfo> entityList = buildEntities();
        List<DoorInfo> doors = buildDoors();

        LevelData levelData = new LevelData(
                TILE_SIZE,
                grid,
                nodeList,
                entityList,
                doors,
                spawnWorldPos,
                DURATION_TICKS,
                MAX_ROUNDS,
                ECHO_LIFE_L
        );
        LevelDataValidator.validateFirstLevel(levelData);
        return levelData;
    }

    /**
     * 16 行 × 28 列瓦片网格，索引一律为 {@code grid[row][col]}。
     *
     * <p>可走格共 265 个，与 {@link #buildPathNodes()} 的节点数严格相等 ——
     * 每个可走格都必须有对应路径节点，否则玩家会走进一个无法离开的格子。</p>
     */
    private static TileType[][] createTileGrid() {
        return new TileType[][]{
                {TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL},
                {TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.WALL, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL},
                {TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.FLOOR, TileType.SPAWN_POINT, TileType.WALL, TileType.WALL, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.WALL, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL},
                {TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL},
                {TileType.WALL, TileType.WALL, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.WALL},
                {TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.WALL},
                {TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.WALL},
                {TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.WALL},
                {TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.WALL},
                {TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.WALL},
                {TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL},
                {TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL},
                {TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.WALL},
                {TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.WALL},
                {TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR, TileType.WALL},
                {TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL, TileType.WALL},
        };
    }

    /**
     * 路径节点图：每个可走格一个节点，坐标取格心。
     *
     * <p>{@code allowDirs} 必须与该格上下左右是否可走完全一致 ——
     * {@code LevelGeometryImpl.validateNodes()} 会逐条核对，多写少写都会抛异常。</p>
     */
    private static List<PathNode> buildPathNodes() {
        List<PathNode> nodes = new ArrayList<>();
        // ---- 第 1 行 ----
        nodes.add(new PathNode(
                "L01_node_c9_r1",
                cellCenter(9, 1),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c10_r1",
                cellCenter(10, 1),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c14_r1",
                cellCenter(14, 1),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c15_r1",
                cellCenter(15, 1),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c16_r1",
                cellCenter(16, 1),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c17_r1",
                cellCenter(17, 1),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c18_r1",
                cellCenter(18, 1),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c19_r1",
                cellCenter(19, 1),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c20_r1",
                cellCenter(20, 1),
                EnumSet.of(PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c21_r1",
                cellCenter(21, 1),
                EnumSet.of(PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c22_r1",
                cellCenter(22, 1),
                EnumSet.of(PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c23_r1",
                cellCenter(23, 1),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c24_r1",
                cellCenter(24, 1),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c25_r1",
                cellCenter(25, 1),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c26_r1",
                cellCenter(26, 1),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        // ---- 第 2 行 ----
        nodes.add(new PathNode(
                "L01_node_c9_r2",
                cellCenter(9, 2),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_spawn",
                cellCenter(10, 2),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c14_r2",
                cellCenter(14, 2),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c15_r2",
                cellCenter(15, 2),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c16_r2",
                cellCenter(16, 2),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c17_r2",
                cellCenter(17, 2),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c18_r2",
                cellCenter(18, 2),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c19_r2",
                cellCenter(19, 2),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c23_r2",
                cellCenter(23, 2),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c24_r2",
                cellCenter(24, 2),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c25_r2",
                cellCenter(25, 2),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c26_r2",
                cellCenter(26, 2),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        // ---- 第 3 行 ----
        nodes.add(new PathNode(
                "L01_node_c7_r3",
                cellCenter(7, 3),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c8_r3",
                cellCenter(8, 3),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c9_r3",
                cellCenter(9, 3),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c10_r3",
                cellCenter(10, 3),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c11_r3",
                cellCenter(11, 3),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c12_r3",
                cellCenter(12, 3),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c13_r3",
                cellCenter(13, 3),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c14_r3",
                cellCenter(14, 3),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c15_r3",
                cellCenter(15, 3),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c16_r3",
                cellCenter(16, 3),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c17_r3",
                cellCenter(17, 3),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c18_r3",
                cellCenter(18, 3),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c19_r3",
                cellCenter(19, 3),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c20_r3",
                cellCenter(20, 3),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c21_r3",
                cellCenter(21, 3),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c22_r3",
                cellCenter(22, 3),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c23_r3",
                cellCenter(23, 3),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c24_r3",
                cellCenter(24, 3),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c25_r3",
                cellCenter(25, 3),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c26_r3",
                cellCenter(26, 3),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        // ---- 第 4 行 ----
        nodes.add(new PathNode(
                "L01_node_c3_r4",
                cellCenter(3, 4),
                EnumSet.of(PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c4_r4",
                cellCenter(4, 4),
                EnumSet.of(PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c5_r4",
                cellCenter(5, 4),
                EnumSet.of(PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c6_r4",
                cellCenter(6, 4),
                EnumSet.of(PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c7_r4",
                cellCenter(7, 4),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c8_r4",
                cellCenter(8, 4),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c9_r4",
                cellCenter(9, 4),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c10_r4",
                cellCenter(10, 4),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c11_r4",
                cellCenter(11, 4),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c12_r4",
                cellCenter(12, 4),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c13_r4",
                cellCenter(13, 4),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c14_r4",
                cellCenter(14, 4),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c15_r4",
                cellCenter(15, 4),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c16_r4",
                cellCenter(16, 4),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c17_r4",
                cellCenter(17, 4),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c18_r4",
                cellCenter(18, 4),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c19_r4",
                cellCenter(19, 4),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c20_r4",
                cellCenter(20, 4),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c21_r4",
                cellCenter(21, 4),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c22_r4",
                cellCenter(22, 4),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c23_r4",
                cellCenter(23, 4),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c25_r4",
                cellCenter(25, 4),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c26_r4",
                cellCenter(26, 4),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        // ---- 第 5 行 ----
        nodes.add(new PathNode(
                "L01_node_c1_r5",
                cellCenter(1, 5),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c2_r5",
                cellCenter(2, 5),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c8_r5",
                cellCenter(8, 5),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c9_r5",
                cellCenter(9, 5),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c10_r5",
                cellCenter(10, 5),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c12_r5",
                cellCenter(12, 5),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c13_r5",
                cellCenter(13, 5),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c14_r5",
                cellCenter(14, 5),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c15_r5",
                cellCenter(15, 5),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c16_r5",
                cellCenter(16, 5),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c17_r5",
                cellCenter(17, 5),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c18_r5",
                cellCenter(18, 5),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c19_r5",
                cellCenter(19, 5),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c20_r5",
                cellCenter(20, 5),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c21_r5",
                cellCenter(21, 5),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c22_r5",
                cellCenter(22, 5),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c23_r5",
                cellCenter(23, 5),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c25_r5",
                cellCenter(25, 5),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c26_r5",
                cellCenter(26, 5),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        // ---- 第 6 行 ----
        nodes.add(new PathNode(
                "L01_node_c1_r6",
                cellCenter(1, 6),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c2_r6",
                cellCenter(2, 6),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_plate_left",
                cellCenter(4, 6),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c5_r6",
                cellCenter(5, 6),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c6_r6",
                cellCenter(6, 6),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c8_r6",
                cellCenter(8, 6),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c9_r6",
                cellCenter(9, 6),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c10_r6",
                cellCenter(10, 6),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c12_r6",
                cellCenter(12, 6),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c13_r6",
                cellCenter(13, 6),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c14_r6",
                cellCenter(14, 6),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c22_r6",
                cellCenter(22, 6),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c23_r6",
                cellCenter(23, 6),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c25_r6",
                cellCenter(25, 6),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c26_r6",
                cellCenter(26, 6),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        // ---- 第 7 行 ----
        nodes.add(new PathNode(
                "L01_node_c1_r7",
                cellCenter(1, 7),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c2_r7",
                cellCenter(2, 7),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c4_r7",
                cellCenter(4, 7),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c5_r7",
                cellCenter(5, 7),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c6_r7",
                cellCenter(6, 7),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c8_r7",
                cellCenter(8, 7),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c9_r7",
                cellCenter(9, 7),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c10_r7",
                cellCenter(10, 7),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c12_r7",
                cellCenter(12, 7),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c13_r7",
                cellCenter(13, 7),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c14_r7",
                cellCenter(14, 7),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c16_r7",
                cellCenter(16, 7),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c17_r7",
                cellCenter(17, 7),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c18_r7",
                cellCenter(18, 7),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c19_r7",
                cellCenter(19, 7),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c20_r7",
                cellCenter(20, 7),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c22_r7",
                cellCenter(22, 7),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c23_r7",
                cellCenter(23, 7),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c25_r7",
                cellCenter(25, 7),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c26_r7",
                cellCenter(26, 7),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        // ---- 第 8 行 ----
        nodes.add(new PathNode(
                "L01_node_c1_r8",
                cellCenter(1, 8),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c2_r8",
                cellCenter(2, 8),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c4_r8",
                cellCenter(4, 8),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c5_r8",
                cellCenter(5, 8),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c6_r8",
                cellCenter(6, 8),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c8_r8",
                cellCenter(8, 8),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c9_r8",
                cellCenter(9, 8),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c10_r8",
                cellCenter(10, 8),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c12_r8",
                cellCenter(12, 8),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c13_r8",
                cellCenter(13, 8),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c14_r8",
                cellCenter(14, 8),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c16_r8",
                cellCenter(16, 8),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c17_r8",
                cellCenter(17, 8),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_plate_right",
                cellCenter(18, 8),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_exit_terminal",
                cellCenter(19, 8),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c20_r8",
                cellCenter(20, 8),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c21_r8",
                cellCenter(21, 8),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c22_r8",
                cellCenter(22, 8),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c23_r8",
                cellCenter(23, 8),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c25_r8",
                cellCenter(25, 8),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c26_r8",
                cellCenter(26, 8),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        // ---- 第 9 行 ----
        nodes.add(new PathNode(
                "L01_node_c1_r9",
                cellCenter(1, 9),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c2_r9",
                cellCenter(2, 9),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c5_r9",
                cellCenter(5, 9),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c6_r9",
                cellCenter(6, 9),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c8_r9",
                cellCenter(8, 9),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c9_r9",
                cellCenter(9, 9),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c10_r9",
                cellCenter(10, 9),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c12_r9",
                cellCenter(12, 9),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c13_r9",
                cellCenter(13, 9),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c14_r9",
                cellCenter(14, 9),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c16_r9",
                cellCenter(16, 9),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c17_r9",
                cellCenter(17, 9),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c18_r9",
                cellCenter(18, 9),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c19_r9",
                cellCenter(19, 9),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c20_r9",
                cellCenter(20, 9),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c21_r9",
                cellCenter(21, 9),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c22_r9",
                cellCenter(22, 9),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c23_r9",
                cellCenter(23, 9),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c25_r9",
                cellCenter(25, 9),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c26_r9",
                cellCenter(26, 9),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        // ---- 第 10 行 ----
        nodes.add(new PathNode(
                "L01_node_c1_r10",
                cellCenter(1, 10),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c2_r10",
                cellCenter(2, 10),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c3_r10",
                cellCenter(3, 10),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c4_r10",
                cellCenter(4, 10),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c5_r10",
                cellCenter(5, 10),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c6_r10",
                cellCenter(6, 10),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c7_r10",
                cellCenter(7, 10),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c8_r10",
                cellCenter(8, 10),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c9_r10",
                cellCenter(9, 10),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c10_r10",
                cellCenter(10, 10),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c12_r10",
                cellCenter(12, 10),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c13_r10",
                cellCenter(13, 10),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c14_r10",
                cellCenter(14, 10),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c16_r10",
                cellCenter(16, 10),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c17_r10",
                cellCenter(17, 10),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c18_r10",
                cellCenter(18, 10),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c19_r10",
                cellCenter(19, 10),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c20_r10",
                cellCenter(20, 10),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c22_r10",
                cellCenter(22, 10),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c23_r10",
                cellCenter(23, 10),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c24_r10",
                cellCenter(24, 10),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c25_r10",
                cellCenter(25, 10),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c26_r10",
                cellCenter(26, 10),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        // ---- 第 11 行 ----
        nodes.add(new PathNode(
                "L01_node_c1_r11",
                cellCenter(1, 11),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c2_r11",
                cellCenter(2, 11),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c3_r11",
                cellCenter(3, 11),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c4_r11",
                cellCenter(4, 11),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c6_r11",
                cellCenter(6, 11),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c7_r11",
                cellCenter(7, 11),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c9_r11",
                cellCenter(9, 11),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c10_r11",
                cellCenter(10, 11),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c12_r11",
                cellCenter(12, 11),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c13_r11",
                cellCenter(13, 11),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c14_r11",
                cellCenter(14, 11),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c22_r11",
                cellCenter(22, 11),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c23_r11",
                cellCenter(23, 11),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c24_r11",
                cellCenter(24, 11),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c25_r11",
                cellCenter(25, 11),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c26_r11",
                cellCenter(26, 11),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        // ---- 第 12 行 ----
        nodes.add(new PathNode(
                "L01_node_c1_r12",
                cellCenter(1, 12),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c2_r12",
                cellCenter(2, 12),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c3_r12",
                cellCenter(3, 12),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c4_r12",
                cellCenter(4, 12),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c6_r12",
                cellCenter(6, 12),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c7_r12",
                cellCenter(7, 12),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c9_r12",
                cellCenter(9, 12),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c10_r12",
                cellCenter(10, 12),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c12_r12",
                cellCenter(12, 12),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c13_r12",
                cellCenter(13, 12),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c14_r12",
                cellCenter(14, 12),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c16_r12",
                cellCenter(16, 12),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c17_r12",
                cellCenter(17, 12),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c18_r12",
                cellCenter(18, 12),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c19_r12",
                cellCenter(19, 12),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c20_r12",
                cellCenter(20, 12),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c22_r12",
                cellCenter(22, 12),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c23_r12",
                cellCenter(23, 12),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c25_r12",
                cellCenter(25, 12),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c26_r12",
                cellCenter(26, 12),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        // ---- 第 13 行 ----
        nodes.add(new PathNode(
                "L01_node_c1_r13",
                cellCenter(1, 13),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c2_r13",
                cellCenter(2, 13),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c3_r13",
                cellCenter(3, 13),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c4_r13",
                cellCenter(4, 13),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c6_r13",
                cellCenter(6, 13),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c7_r13",
                cellCenter(7, 13),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c9_r13",
                cellCenter(9, 13),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c10_r13",
                cellCenter(10, 13),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c16_r13",
                cellCenter(16, 13),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c17_r13",
                cellCenter(17, 13),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_door",
                cellCenter(18, 13),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c19_r13",
                cellCenter(19, 13),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c20_r13",
                cellCenter(20, 13),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c21_r13",
                cellCenter(21, 13),
                EnumSet.of(PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c22_r13",
                cellCenter(22, 13),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c23_r13",
                cellCenter(23, 13),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c25_r13",
                cellCenter(25, 13),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c26_r13",
                cellCenter(26, 13),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT)));
        // ---- 第 14 行 ----
        nodes.add(new PathNode(
                "L01_node_c1_r14",
                cellCenter(1, 14),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c2_r14",
                cellCenter(2, 14),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c3_r14",
                cellCenter(3, 14),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c4_r14",
                cellCenter(4, 14),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c6_r14",
                cellCenter(6, 14),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c7_r14",
                cellCenter(7, 14),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c9_r14",
                cellCenter(9, 14),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c10_r14",
                cellCenter(10, 14),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c11_r14",
                cellCenter(11, 14),
                EnumSet.of(PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c12_r14",
                cellCenter(12, 14),
                EnumSet.of(PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c13_r14",
                cellCenter(13, 14),
                EnumSet.of(PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c14_r14",
                cellCenter(14, 14),
                EnumSet.of(PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c15_r14",
                cellCenter(15, 14),
                EnumSet.of(PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c16_r14",
                cellCenter(16, 14),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c17_r14",
                cellCenter(17, 14),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c18_r14",
                cellCenter(18, 14),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c19_r14",
                cellCenter(19, 14),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c20_r14",
                cellCenter(20, 14),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c21_r14",
                cellCenter(21, 14),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c22_r14",
                cellCenter(22, 14),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c23_r14",
                cellCenter(23, 14),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT)));
        nodes.add(new PathNode(
                "L01_node_c25_r14",
                cellCenter(25, 14),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_c26_r14",
                cellCenter(26, 14),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.LEFT)));
        return nodes;
    }

    /** 两块驻留板 + 终点终端。{@code pos} 必须与所引用节点的 worldPos 完全相同。 */
    private static List<EntitySpawnInfo> buildEntities() {
        List<EntitySpawnInfo> list = new ArrayList<>();

        list.add(new EntitySpawnInfo(
                        "L01_plate_left",
                        "dock_plate",
                        cellCenter(4, 6),
                        "L01_node_plate_left")
                .putProp("autoDock", true));

        list.add(new EntitySpawnInfo(
                        "L01_plate_right",
                        "dock_plate",
                        cellCenter(18, 8),
                        "L01_node_plate_right")
                .putProp("autoDock", true));

        list.add(new EntitySpawnInfo(
                "L01_exit_00",
                "exit_terminal",
                cellCenter(19, 8),
                "L01_node_exit_terminal"));

        return list;
    }

    /**
     * 门：两块驻留板必须<b>同时</b>被占才会解锁。
     *
     * <p>门放在 (18, 13)，不在任何必经路上 —— 锁着时只挡自己那一格，
     * 三个目标点的最短路不受影响。它真正的职责是派发 DOOR_UNLOCKED 给终点，
     * 外加作为「两块板是否已同时被占」的唯一视觉反馈（HUD 不显示板的状态）。</p>
     */
    private static List<DoorInfo> buildDoors() {
        List<DoorInfo> doors = new ArrayList<>();
        doors.add(new DoorInfo(
                "L01_door_01",
                cellCenter(18, 13),
                false,
                Set.of("L01_plate_left", "L01_plate_right")));
        return doors;
    }

    /** 第一关世界坐标约定：所有路径节点和可见物件位于对应格子的几何中心。 */
    private static Vector2D cellCenter(int col, int row) {
        return new Vector2D(
                (col + 0.5) * TILE_SIZE,
                (row + 0.5) * TILE_SIZE);
    }
}
