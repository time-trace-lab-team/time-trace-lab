package org.example.timeloop.level.model;

/**
 * 地图瓦片逻辑类型
 * WALL：墙体（不可通行）
 * FLOOR：普通地面（可走廊道）
 * SPAWN_POINT：玩家出生瓦片
 */
public enum TileType {
    WALL,
    FLOOR,
    SPAWN_POINT
}