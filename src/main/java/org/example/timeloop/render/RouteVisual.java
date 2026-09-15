package org.example.timeloop.render;

import org.example.timeloop.level.model.Vector2D;

import java.util.List;
import java.util.Objects;

/**
 * 静态路线的不可变只读渲染投影。
 *
 * <p>路线几何来自关卡权威描述，经 app 映射后在装配时注入。本类型不解析关卡数据，
 * 也不携带任何动态玩法状态。</p>
 *
 * @param branchNodeId 关联分岔节点 ID；不经过分岔的路线使用空字符串
 */
public record RouteVisual(String id,
                          RouteKind kind,
                          List<Vector2D> worldPoints,
                          String branchNodeId) {

    public RouteVisual {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id 不能为空白");
        }
        Objects.requireNonNull(kind, "kind");
        worldPoints = List.copyOf(Objects.requireNonNull(worldPoints, "worldPoints"));
        if (worldPoints.size() < 2) {
            throw new IllegalArgumentException("worldPoints 至少需要两个点");
        }
        for (Vector2D point : worldPoints) {
            Objects.requireNonNull(point, "worldPoints 不能包含 null");
            if (!Double.isFinite(point.x()) || !Double.isFinite(point.y())) {
                throw new IllegalArgumentException("worldPoints 坐标必须为有限数");
            }
        }
        branchNodeId = branchNodeId == null ? "" : branchNodeId.strip();
    }
}
