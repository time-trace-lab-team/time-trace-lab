package org.example.timeloop.level.model;

import java.util.EnumSet;

/**
 * 路径路口节点：恒速巡行系统使用，存储路口位置、允许通行方向、defaultExit默认出口
 * 对应文档：普通廊道到达路口中心执行转向逻辑
 */
public class PathNode {
    private final String id;
    // 节点世界坐标（tileSize倍数）
    private final Vector2D worldPos;
    // 允许通行方向集合
    private final EnumSet<Dir> allowDirs;
    // 本路口默认出口方向 defaultExit，多条侧路时自动走该方向
    private Dir defaultExit;

    /**
     * 方向枚举，对应W/A/S/D
     */
    public enum Dir {
        UP, DOWN, LEFT, RIGHT
    }

    public PathNode(String id,Vector2D worldPos, EnumSet<Dir> allowDirs) {
        this.id = id;
        this.worldPos = worldPos;
        this.allowDirs = allowDirs;
    }

    // ---------------- getter setter ----------------
    public String getId() { return id; }
    public Vector2D getWorldPos() {
        return worldPos;
    }

    public EnumSet<Dir> getAllowDirs() {
        return allowDirs;
    }

    public Dir getDefaultExit() {
        return defaultExit;
    }

    public void setDefaultExit(Dir defaultExit) {
        this.defaultExit = defaultExit;
    }
}