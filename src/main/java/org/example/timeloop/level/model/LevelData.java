package org.example.timeloop.level.model;

import java.util.List;

/**
 * 完整关卡数据POJO
 * tileSize：瓦片像素尺寸 [待确认] 需要和渲染层统一常量
 * tileGrid：瓦片网格，逻辑网格，画面不绘制完整网格
 * pathNodes：全部巡行路口节点
 * entitySpawnList：机关实体生成列表
 * spawnPos：玩家出生世界坐标
 * durationTicks：本轮总逻辑tick；第一关16s @60fps =960 tick
 * maxRounds：最大轮次
 * echoLifeL：残影存活轮数L，第一关L=1
 */
public class LevelData {
    private final double tileSize;
    private final TileType[][] tileGrid;
    private final List<PathNode> pathNodes;
    private final List<EntitySpawnInfo> entitySpawnList;
    private final Vector2D spawnPos;

    // 关卡会话参数（进入关卡冻结，运行时不可修改）
    private final long durationTicks;
    private final int maxRounds;
    private final int echoLifeL;

    public LevelData(double tileSize,
                     TileType[][] tileGrid,
                     List<PathNode> pathNodes,
                     List<EntitySpawnInfo> entitySpawnList,
                     Vector2D spawnPos,
                     long durationTicks,
                     int maxRounds,
                     int echoLifeL) {
        this.tileSize = tileSize;
        this.tileGrid = tileGrid;
        this.pathNodes = pathNodes;
        this.entitySpawnList = entitySpawnList;
        this.spawnPos = spawnPos;
        this.durationTicks = durationTicks;
        this.maxRounds = maxRounds;
        this.echoLifeL = echoLifeL;
    }

    // -------- getters --------
    public double getTileSize() { return tileSize; }
    public TileType[][] getTileGrid() { return tileGrid; }
    public List<PathNode> getPathNodes() { return pathNodes; }
    public List<EntitySpawnInfo> getEntitySpawnList() { return entitySpawnList; }
    public Vector2D getSpawnPos() { return spawnPos; }
    public long getDurationTicks() { return durationTicks; }
    public int getMaxRounds() { return maxRounds; }
    public int getEchoLifeL() { return echoLifeL; }
}