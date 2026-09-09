package org.example.timeloop.core.path;

/**
 * Immutable world-space point used by the C2 orthogonal path graph.
 *
 * <p>Coordinates use the shared world convention: origin at the upper-left,
 * positive x to the right and positive y downward. They are never screen or
 * Canvas coordinates.</p>
 */
public record PathPoint(double x, double y) {

    public PathPoint {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("path point coordinates must be finite: (" + x + ", " + y + ")");
        }
    }

    /** Returns the Euclidean distance to another world-space point. */
    public double distanceTo(PathPoint other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        return Math.hypot(other.x - x, other.y - y);
    }
}
