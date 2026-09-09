package org.example.timeloop.core.path;

import org.example.timeloop.core.Direction;

import java.util.List;
import java.util.Objects;

/** Direction operations expressed explicitly by names, never enum ordinal. */
public final class DirectionGeometry {

    private DirectionGeometry() {
    }

    /** Returns the unique 180-degree reverse direction. */
    public static Direction opposite(Direction direction) {
        Objects.requireNonNull(direction, "direction");
        return switch (direction) {
            case UP -> Direction.DOWN;
            case RIGHT -> Direction.LEFT;
            case DOWN -> Direction.UP;
            case LEFT -> Direction.RIGHT;
        };
    }

    /** Returns the two 90-degree directions in a fixed semantic order. */
    public static List<Direction> sideDirections(Direction direction) {
        Objects.requireNonNull(direction, "direction");
        return switch (direction) {
            case UP, DOWN -> List.of(Direction.LEFT, Direction.RIGHT);
            case LEFT, RIGHT -> List.of(Direction.UP, Direction.DOWN);
        };
    }

    /** Whether a requested direction is straight ahead or a 90-degree turn. */
    public static boolean isStraightOrRightAngle(Direction current, Direction requested) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(requested, "requested");
        return requested != opposite(current);
    }

    /** Moves a point along one orthogonal direction by a finite nonnegative distance. */
    public static PathPoint move(PathPoint point, Direction direction, double distance) {
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(direction, "direction");
        if (!Double.isFinite(distance) || distance < 0.0) {
            throw new IllegalArgumentException("distance must be finite and >= 0, actual " + distance);
        }
        return switch (direction) {
            case UP -> new PathPoint(point.x(), point.y() - distance);
            case RIGHT -> new PathPoint(point.x() + distance, point.y());
            case DOWN -> new PathPoint(point.x(), point.y() + distance);
            case LEFT -> new PathPoint(point.x() - distance, point.y());
        };
    }

    /**
     * Validates that {@code target} is on one cardinal ray from {@code source}
     * and returns that cardinal direction.
     */
    public static Direction directionFromTo(PathPoint source, PathPoint target, double epsilon) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        if (!Double.isFinite(epsilon) || epsilon < 0.0) {
            throw new IllegalArgumentException("epsilon must be finite and >= 0, actual " + epsilon);
        }

        double dx = target.x() - source.x();
        double dy = target.y() - source.y();
        boolean sameX = Math.abs(dx) <= epsilon;
        boolean sameY = Math.abs(dy) <= epsilon;
        if (sameX && sameY) {
            throw new IllegalArgumentException("zero-length path segment from " + source + " to " + target);
        }
        if (!sameX && !sameY) {
            throw new IllegalArgumentException("non-orthogonal path segment from " + source + " to " + target);
        }
        if (sameX) {
            return dy > 0.0 ? Direction.DOWN : Direction.UP;
        }
        return dx > 0.0 ? Direction.RIGHT : Direction.LEFT;
    }
}
