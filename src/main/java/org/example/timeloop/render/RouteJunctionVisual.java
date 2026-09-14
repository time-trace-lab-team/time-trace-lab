package org.example.timeloop.render;

import org.example.timeloop.level.model.Vector2D;

import java.util.Objects;

/**
 * 第三关路线分岔的静态世界坐标投影。
 *
 * <p>本关已冻结语义：向上为 B 支路，向右为最终主通道。方向标识仅表达该关的既定路线职责，
 * 不参与寻路、碰撞或门状态计算。</p>
 */
public record RouteJunctionVisual(String id, Vector2D worldPosition) {

    public RouteJunctionVisual {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id 不能为空白");
        }
        Objects.requireNonNull(worldPosition, "worldPosition");
        if (!Double.isFinite(worldPosition.x()) || !Double.isFinite(worldPosition.y())) {
            throw new IllegalArgumentException("worldPosition 坐标必须为有限数");
        }
    }
}
