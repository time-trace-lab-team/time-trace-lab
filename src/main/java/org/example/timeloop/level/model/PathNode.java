package org.example.timeloop.level.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class PathNode {

    public enum Dir {
        UP, DOWN, LEFT, RIGHT
    }

    private final String id;
    private final Vector2D worldPos;
    private final Set<Dir> allowDirs;
    private final Dir defaultExit;

    public PathNode(String id, Vector2D worldPos, EnumSet<Dir> allowDirs) {
        this(id, worldPos, allowDirs, null);
    }

    public PathNode(String id,
                    Vector2D worldPos,
                    EnumSet<Dir> allowDirs,
                    Dir defaultExit) {
        this.id = id;
        this.worldPos = Objects.requireNonNull(worldPos, "worldPos");
        Objects.requireNonNull(allowDirs, "allowDirs");
        EnumSet<Dir> allowDirsCopy = allowDirs.clone();
        if (defaultExit != null && !allowDirsCopy.contains(defaultExit)) {
            throw new IllegalArgumentException(
                    "defaultExit 必须属于 allowDirs: " + defaultExit);
        }
        this.allowDirs = Collections.unmodifiableSet(allowDirsCopy);
        this.defaultExit = defaultExit;
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

    public Set<Dir> getAllowDirs() {
        return allowDirs;
    }

    public Dir getDefaultExit() {
        return defaultExit;
    }

}
