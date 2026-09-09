package org.example.timeloop.level.model;

import org.example.timeloop.level.model.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * 第一关：留下的脚步 Level01Footsteps
 * 需求参考README.md：
 *  参数：16s(960tick) / L=1 / maxRounds=3
 *  谜题：两条分叉各一块驻留板(autoDock=true)，第一轮残影停左板，第二轮玩家停右板，双驻留激活开门，操作出口终端通关
 *  本类只构建LevelData模型对象；不做渲染、游戏逻辑；后续可替换为JSON加载
 */
public class Level01Footsteps {

    // tileSize 统一为 48.0
    private static final double TILE_SIZE = 48.0;
    // 60FPS逻辑刻，16秒总tick
    private static final long DURATION_TICKS = 16 * 60L;
    private static final int MAX_ROUNDS = 3;
    private static final int ECHO_LIFE_L = 1;

    public static LevelData build() {
        TileType[][] grid = createTileGrid();
        Vector2D spawnWorldPos = new Vector2D(TILE_SIZE * 5.0, TILE_SIZE * 1.0);
        List<PathNode> nodeList = buildPathNodes();
        List<EntitySpawnInfo> entityList = buildEntities();

        return new LevelData(
                TILE_SIZE,
                grid,
                nodeList,
                entityList,
                spawnWorldPos,
                DURATION_TICKS,
                MAX_ROUNDS,
                ECHO_LIFE_L
        );
    }

    private static TileType[][] createTileGrid() {
        int width = 11;
        int height = 9;
        TileType[][] grid = new TileType[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                grid[y][x] = TileType.WALL;
            }
        }

        // 中间廊道
        for (int x = 4; x <= 6; x++) grid[1][x] = TileType.FLOOR;
        for (int x = 4; x <= 6; x++) grid[2][x] = TileType.FLOOR;
        for (int x = 4; x <= 6; x++) grid[3][x] = TileType.FLOOR;

        // 左分支
        for (int y = 3; y <= 5; y++) grid[y][2] = TileType.FLOOR;
        grid[5][2] = TileType.FLOOR;

        // 右分支
        for (int y = 3; y <= 5; y++) grid[y][8] = TileType.FLOOR;
        grid[5][8] = TileType.FLOOR;

        grid[1][5] = TileType.SPAWN_POINT;
        return grid;
    }

    private static List<PathNode> buildPathNodes() {
        List<PathNode> nodes = new ArrayList<>();

        // 分叉路口节点
        PathNode forkNode = new PathNode(
                "fork",
                new Vector2D(5 * TILE_SIZE, 3 * TILE_SIZE),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)
        );
        nodes.add(forkNode);

        // 左侧终点节点
        PathNode leftNode = new PathNode(
                "left_end",
                new Vector2D(2 * TILE_SIZE, 5 * TILE_SIZE),
                EnumSet.of(PathNode.Dir.UP)
        );
        nodes.add(leftNode);

        // 右侧终点节点
        PathNode rightNode = new PathNode(
                "right_end",
                new Vector2D(8 * TILE_SIZE, 5 * TILE_SIZE),
                EnumSet.of(PathNode.Dir.UP)
        );
        nodes.add(rightNode);

        return nodes;
    }

    private static List<EntitySpawnInfo> buildEntities() {
        List<EntitySpawnInfo> list = new ArrayList<>();

        EntitySpawnInfo dockLeft = new EntitySpawnInfo("dock_plate", new Vector2D(2 * TILE_SIZE, 5 * TILE_SIZE))
                .putProp("autoDock", true);
        list.add(dockLeft);

        EntitySpawnInfo dockRight = new EntitySpawnInfo("dock_plate", new Vector2D(8 * TILE_SIZE, 5 * TILE_SIZE))
                .putProp("autoDock", true);
        list.add(dockRight);

        EntitySpawnInfo exitTerminal = new EntitySpawnInfo("exit_terminal", new Vector2D(9 * TILE_SIZE, 5 * TILE_SIZE));
        list.add(exitTerminal);

        return list;
    }
}