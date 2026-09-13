package org.example.timeloop.level.model;

/**
 * 轻量二维浮点坐标，用于地图、实体位置
 * [待确认]：项目util包是否已有通用Vector，如有应当迁移至util，本类删除
 */
public record Vector2D(double x, double y) {

    /**
     * 向量加法
     */
    public Vector2D add(double dx, double dy) {
        return new Vector2D(x + dx, y + dy);
    }

    /**
     * 向量缩放
     */
    public Vector2D scale(double factor) {
        return new Vector2D(x * factor, y * factor);
    }

    @Override
    public String toString() {
        return String.format("(%.2f,%.2f)", x, y);
    }
}