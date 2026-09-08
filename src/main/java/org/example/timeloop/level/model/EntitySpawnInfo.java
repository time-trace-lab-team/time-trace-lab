package org.example.timeloop.level.model;

/**
 * 机关实体生成描述
 * entityType：实体类型字符串，运行时由工厂解析生成对应机关实例
 * pos：世界坐标
 * properties：可变参数map，存放机关配置，例如autoDock=true
 */
import java.util.HashMap;
import java.util.Map;

public class EntitySpawnInfo {
    private final String entityType;
    private final Vector2D pos;
    private final Map<String, Object> properties;

    public EntitySpawnInfo(String entityType, Vector2D pos) {
        this.entityType = entityType;
        this.pos = pos;
        this.properties = new HashMap<>();
    }

    /**
     * 添加属性，例如 put("autoDock",true)
     */
    public EntitySpawnInfo putProp(String key, Object value) {
        properties.put(key, value);
        return this;
    }

    // -------- getter --------
    public String getEntityType() {
        return entityType;
    }

    public Vector2D getPos() {
        return pos;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }
}