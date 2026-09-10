package org.example.timeloop.level;

import java.util.regex.Pattern;

/**
 * 开发三数据使用的稳定 ID 校验器。
 *
 * <p>它只校验格式和命名空间，不生成 ID，也不根据坐标或集合位置推导 ID。</p>
 */
public final class StableIdValidator {

    private static final Pattern MECHANISM_ID = Pattern.compile(
            "L0[1-5]_(?:plate|door|ray|resonance|relay_i|relay_ii|core|exit)_[a-z0-9]+(?:_[a-z0-9]+)*"
    );
    private static final Pattern PATH_NODE_ID = Pattern.compile(
            "L0[1-5]_node_[a-z0-9]+(?:_[a-z0-9]+)*"
    );

    private StableIdValidator() {
    }

    public static String requireMechanismId(String id, String fieldName) {
        requireNonBlank(id, fieldName);
        if (!MECHANISM_ID.matcher(id).matches()) {
            throw new IllegalArgumentException(fieldName + " 不是合法机制 ID: " + id);
        }
        return id;
    }

    public static String requireMechanismId(String id, String expectedKind, String fieldName) {
        requireMechanismId(id, fieldName);
        String expectedPrefix = id.substring(0, 3) + "_" + expectedKind + "_";
        if (!id.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException(
                    fieldName + " 的机制类型应为 " + expectedKind + ": " + id
            );
        }
        return id;
    }

    public static String requirePathNodeId(String id, String fieldName) {
        requireNonBlank(id, fieldName);
        if (!PATH_NODE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException(fieldName + " 不是合法路径节点 ID: " + id);
        }
        return id;
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空白");
        }
    }
}
