package org.example.timeloop.level.model;

import org.example.timeloop.level.StableIdValidator;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class EntitySpawnInfo {
    private final String id;
    private final String entityType;
    private final Vector2D pos;
    private final String pathNodeId;
    private final Map<String, Object> properties = new LinkedHashMap<>();

    /**
     * 兼容尚未迁移的旧关卡数据。第一关必须使用带稳定 ID 和路径节点引用的构造器。
     */
    public EntitySpawnInfo(String entityType, Vector2D pos) {
        this(null, entityType, pos, null);
    }

    public EntitySpawnInfo(String id, String entityType, Vector2D pos) {
        this(id, entityType, pos, null);
    }

    public EntitySpawnInfo(String id, String entityType, Vector2D pos, String pathNodeId) {
        this.id = id == null ? null : StableIdValidator.requireMechanismId(id, "entitySpawn.id");
        this.entityType = entityType;
        this.pos = pos;
        this.pathNodeId = pathNodeId == null
                ? null
                : StableIdValidator.requirePathNodeId(pathNodeId, "entitySpawn.pathNodeId");
    }

    /**
     * 添加属性，例如 put("autoDock",true)
     */
    public EntitySpawnInfo putProp(String key, Object value) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("entitySpawn property key 不能为空白");
        }
        properties.put(key, value);
        return this;
    }

    // -------- getter --------
    public String getId() {
        return id;
    }

    public String getEntityType() {
        return entityType;
    }

    public Vector2D getPos() {
        return pos;
    }

    public String getPathNodeId() {
        return pathNodeId;
    }

    public Map<String, Object> getProperties() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }
}
