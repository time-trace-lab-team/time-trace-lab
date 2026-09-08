package org.example.timeloop.level.model;

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
    // [待确认] tileSize像素尺寸，需要与渲染层统一
    private static final double TILE_SIZE = 64.0;
    // 60FPS逻辑刻，16秒总tick
    private static final long DURATION_TICKS = 16 * 60L;
    private static final int MAX_ROUNDS = 3;
    private static final int ECHO_LIFE_L = 1;

    /**
     * 构建第一关关卡数据
     * @return LevelData 关卡数据模型，交给关卡工厂生成世界实体
     */
    public static LevelData build() {
        // 1.构建瓦片逻辑网格（示例11×9网格，WALL墙体 FLOOR地面 SPAWN_POINT出生）
        TileType[][] grid = createTileGrid();

        // 2.玩家出生世界坐标（瓦片中心）
        Vector2D spawnWorldPos = new Vector2D(TILE_SIZE * 5.0, TILE_SIZE * 1.0);

        // 3.构建巡行路径路口节点（供恒速巡行转向使用）
        List<PathNode> nodeList = buildPathNodes();

        // 4.构建机关生成列表：2个驻留板autoDock=true；1个出口终端
        List<EntitySpawnInfo> entityList = buildEntities();

        // 组装关卡数据对象
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

    /**
     * 创建瓦片逻辑网格，逻辑网格存在，画面不会绘制完整棋盘格
     */
    private static TileType[][] createTileGrid() {
        int width = 11;
        int height =9;
        TileType[][] grid = new TileType[height][width];
        // 全部填充墙体
        for(int y=0;y<height;y++){
            for(int x=0;x<width;x++){
                grid[y][x]= TileType.WALL;
            }
        }
        // 开辟中间廊道，出生点y=1，向下走到分叉 y=3
        for(int x=4;x<=6;x++) grid[1][x]=TileType.FLOOR;
        for(int x=4;x<=6;x++) grid[2][x]=TileType.FLOOR;
        for(int x=4;x<=6;x++) grid[3][x]=TileType.FLOOR;

        // 左分支：分叉路口向左，通向左侧驻留板位置
        for(int y=3;y<=5;y++) grid[y][2]=TileType.FLOOR;
        grid[5][2]=TileType.FLOOR; //左驻留板瓦片

        // 右分支：分叉路口向右，通向右侧驻留板+出口终端
        for(int y=3;y<=5;y++) grid[y][8]=TileType.FLOOR;
        grid[5][8]=TileType.FLOOR; //右驻留板瓦片

        // 出生标记
        grid[1][5]=TileType.SPAWN_POINT;
        return grid;
    }

    /**
     * 创建巡行路口PathNode节点，角色到达节点中心执行转向逻辑
     */
    private static List<PathNode> buildPathNodes() {
        List<PathNode> nodes = new ArrayList<>();
        // 分叉路口坐标：x=5 tile，y=3 tile
        Vector2D forkPos = new Vector2D(5 * TILE_SIZE,3 * TILE_SIZE);
        PathNode forkNode = new PathNode(forkPos,
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT));
        nodes.add(forkNode);

        // 左分支终点节点
        Vector2D leftEndPos = new Vector2D(2 * TILE_SIZE, 5 * TILE_SIZE);
        PathNode leftNode = new PathNode(leftEndPos, EnumSet.of(PathNode.Dir.UP));
        nodes.add(leftNode);

        // 右分支终点节点
        Vector2D rightEndPos = new Vector2D(8 * TILE_SIZE, 5 * TILE_SIZE);
        PathNode rightNode = new PathNode(rightEndPos, EnumSet.of(PathNode.Dir.UP));
        nodes.add(rightNode);
        return nodes;
    }

    /**
     * 生成机关实体：2块驻留板 autoDock=true，出口终端；无其他机关
     */
    private static List<EntitySpawnInfo> buildEntities() {
        List<EntitySpawnInfo> list = new ArrayList<>();
        // 左侧驻留板 autoDock=true
        EntitySpawnInfo dockLeft = new EntitySpawnInfo("dock_plate",new Vector2D(2*TILE_SIZE,5*TILE_SIZE))
                .putProp("autoDock",true);
        list.add(dockLeft);

        // 右侧驻留板 autoDock=true
        EntitySpawnInfo dockRight = new EntitySpawnInfo("dock_plate",new Vector2D(8*TILE_SIZE,5*TILE_SIZE))
                .putProp("autoDock",true);
        list.add(dockRight);

        // 出口终端，放在右侧驻留板旁边
        EntitySpawnInfo exitTerminal = new EntitySpawnInfo("exit_terminal",new Vector2D(9*TILE_SIZE,5*TILE_SIZE));
        list.add(exitTerminal);
        return list;
    }
}