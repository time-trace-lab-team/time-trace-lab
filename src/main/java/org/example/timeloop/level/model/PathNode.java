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

    public PathNode(String id, Vector2D worldPos, EnumSet<Dir> allowDirs) {
        this.id = id;
        this.worldPos = Objects.requireNonNull(worldPos, "worldPos");
        Objects.requireNonNull(allowDirs, "allowDirs");
        this.allowDirs = Collections.unmodifiableSet(allowDirs.clone());
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

}
