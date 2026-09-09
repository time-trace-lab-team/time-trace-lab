package org.example.timeloop.level.model;

import java.util.List;

/**
 * 完整关卡数据（不可变）。
 * 所有字段在构造时设置，运行时不可修改。
 */
public final class LevelData {

    private final double tileSize;
    private final TileType[][] tileGrid;
    private final List<PathNode> pathNodes;
    private final List<EntitySpawnInfo> entitySpawnList;
    private final Vector2D spawnPos;
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
        this.tileGrid = copyGrid(tileGrid);
        this.pathNodes = List.copyOf(pathNodes);
        this.entitySpawnList = List.copyOf(entitySpawnList);
        this.spawnPos = spawnPos;
        this.durationTicks = durationTicks;
        this.maxRounds = maxRounds;
        this.echoLifeL = echoLifeL;
    }

    private static TileType[][] copyGrid(TileType[][] original) {
        if (original == null) return new TileType[0][0];
        TileType[][] copy = new TileType[original.length][];
        for (int i = 0; i < original.length; i++) {
            copy[i] = original[i].clone();
        }
        return copy;
    }

    public double getTileSize() { return tileSize; }

    public TileType[][] getTileGrid() {
        return copyGrid(tileGrid);
    }

    public List<PathNode> getPathNodes() {
        return pathNodes;
    }

    public List<EntitySpawnInfo> getEntitySpawnList() {
        return entitySpawnList;
    }

    public Vector2D getSpawnPos() {
        return spawnPos;
    }

    public long getDurationTicks() {
        return durationTicks;
    }

    public int getMaxRounds() {
        return maxRounds;
    }

    public int getEchoLifeL() {
        return echoLifeL;
    }
}