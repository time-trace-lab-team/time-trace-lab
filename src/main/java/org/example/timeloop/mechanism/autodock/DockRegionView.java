package org.example.timeloop.mechanism.autodock;

import org.example.timeloop.level.model.Vector2D;

import java.util.Objects;

/** 不可变的 autoDock 轴对齐矩形。 */
public record DockRegionView(double minX,
                             double minY,
                             double maxX,
                             double maxY,
                             double epsilon) {

    public DockRegionView {
        requireFinite(minX, "minX");
        requireFinite(minY, "minY");
        requireFinite(maxX, "maxX");
        requireFinite(maxY, "maxY");
        requireFinite(epsilon, "epsilon");
        if (minX > maxX || minY > maxY) {
            throw new IllegalArgumentException("autoDock 区域边界无效");
        }
        if (epsilon < 0.0) {
            throw new IllegalArgumentException("autoDock epsilon 不能为负数");
        }
    }

    public static DockRegionView around(Vector2D center, double tileSize) {
        Objects.requireNonNull(center, "center");
        requireFinite(tileSize, "tileSize");
        if (tileSize <= 0.0) {
            throw new IllegalArgumentException("tileSize 必须为正数");
        }
        double half = tileSize / 2.0;
        return new DockRegionView(
                center.x() - half,
                center.y() - half,
                center.x() + half,
                center.y() + half,
                1e-6 * tileSize
        );
    }

    public boolean contains(Vector2D position) {
        Objects.requireNonNull(position, "position");
        requireFinite(position.x(), "position.x");
        requireFinite(position.y(), "position.y");
        return minX - epsilon <= position.x() && position.x() <= maxX + epsilon
                && minY - epsilon <= position.y() && position.y() <= maxY + epsilon;
    }

    public double distanceSquared(Vector2D position) {
        Objects.requireNonNull(position, "position");
        requireFinite(position.x(), "position.x");
        requireFinite(position.y(), "position.y");

        double dx = position.x() < minX ? minX - position.x()
                : position.x() > maxX ? position.x() - maxX : 0.0;
        double dy = position.y() < minY ? minY - position.y()
                : position.y() > maxY ? position.y() - maxY : 0.0;
        return dx * dx + dy * dy;
    }

    public boolean overlaps(DockRegionView other) {
        Objects.requireNonNull(other, "other");
        double width = Math.min(maxX, other.maxX) - Math.max(minX, other.minX);
        double height = Math.min(maxY, other.maxY) - Math.max(minY, other.minY);
        return width > 0.0 && height > 0.0;
    }

    private static void requireFinite(double value, String fieldName) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " 必须为有限数");
        }
    }
}
