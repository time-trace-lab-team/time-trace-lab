package org.example.timeloop.level.model;

import java.util.EnumSet;

public class PathNode {

    public enum Dir {
        UP, DOWN, LEFT, RIGHT
    }

    private final String id;
    private final Vector2D worldPos;
    private final EnumSet<Dir> allowDirs;
    private Dir defaultExit;

    public PathNode(String id, Vector2D worldPos, EnumSet<Dir> allowDirs) {
        this.id = id;
        this.worldPos = worldPos;
        this.allowDirs = allowDirs;
    }

    // 兼容旧构造器（不推荐使用，建议使用带 id 的版本）
    public PathNode(Vector2D worldPos, EnumSet<Dir> allowDirs) {
        this(null, worldPos, allowDirs);
    }

    public String getId() {
        return id;
    }

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