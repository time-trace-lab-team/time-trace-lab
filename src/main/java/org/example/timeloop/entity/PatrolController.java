package org.example.timeloop.entity;

import org.example.timeloop.core.ActorPhase;
import org.example.timeloop.core.AnimationState;
import org.example.timeloop.core.Direction;
import org.example.timeloop.core.MovementState;
import org.example.timeloop.core.PlayerKinematics;
import org.example.timeloop.core.path.DirectionGeometry;
import org.example.timeloop.core.path.ExitPassability;
import org.example.timeloop.core.path.OrthogonalPathGraph;
import org.example.timeloop.core.path.PathExitDecision;
import org.example.timeloop.core.path.PathExitSelector;
import org.example.timeloop.core.path.PathNode;
import org.example.timeloop.core.path.PathPoint;

import java.util.Objects;
import java.util.Optional;

/**
 * Current-player C2 greybox state for deterministic, constant-speed patrol.
 *
 * <p>This controller owns no clock. Its caller supplies the shared logical
 * tick solely for the returned tick-end {@link PlayerKinematics}; the movement
 * budget is always the frozen {@link PatrolConfig#baseSpeed()} for one call.</p>
 */
public final class PatrolController {

    private final OrthogonalPathGraph graph;
    private final PatrolConfig config;

    private String segmentStartNodeId;
    private String segmentEndNodeId;
    private Direction direction;
    private PathPoint position;
    private Direction pendingDirection;
    private String lockedNodeId;
    private Direction lockedDirection;

    /**
     * Creates a controller at a node center with an explicit first segment.
     *
     * @param graph path graph whose topology was already validated
     * @param startNodeId node at which this patrol begins
     * @param initialDirection explicit initial direction; there is no hidden global default
     * @param config frozen movement and geometry parameters
     */
    public PatrolController(
            OrthogonalPathGraph graph,
            String startNodeId,
            Direction initialDirection,
            PatrolConfig config) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.config = Objects.requireNonNull(config, "config");
        Objects.requireNonNull(initialDirection, "initialDirection");

        PathNode start = graph.node(startNodeId);
        PathNode end = graph.neighbor(start, initialDirection).orElseThrow(() -> new IllegalArgumentException(
                "start node '" + start.id() + "' has no " + initialDirection + " exit"));

        this.segmentStartNodeId = start.id();
        this.segmentEndNodeId = end.id();
        this.direction = initialDirection;
        this.position = start.center();
    }

    /**
     * Advances exactly one C2 logical tick and returns its canonical end state.
     *
     * <p>When the destination enters the frozen lock distance, the five-level
     * exit decision is made exactly once. The turn is committed only at the
     * destination center; all remaining movement budget is then spent on the
     * newly selected segment.</p>
     *
     * @param tick shared nonnegative logic tick to put in the snapshot
     * @param passability read-only current-tick exit query
     * @return immutable tick-end position and C2's CRUISING state
     */
    public PlayerKinematics advance(long tick, ExitPassability passability) {
        if (tick < 0) {
            throw new IllegalArgumentException("tick must be >= 0, actual " + tick);
        }
        Objects.requireNonNull(passability, "passability");

        double remainingDistance = config.baseSpeed();
        int arrivals = 0;
        int maximumArrivals = maximumArrivalsFor(remainingDistance);

        while (true) {
            PathNode destination = graph.node(segmentEndNodeId);
            double distanceToDestination = distanceToDestination(destination);

            if (!isLockedFor(destination)) {
                if (distanceToDestination <= config.turnLockDistance() + config.epsilon()) {
                    lockExitFor(destination, passability);
                    continue;
                }
                if (remainingDistance <= 0.0) {
                    break;
                }

                double distanceToLockBoundary = distanceToDestination - config.turnLockDistance();
                if (remainingDistance < distanceToLockBoundary) {
                    position = DirectionGeometry.move(position, direction, remainingDistance);
                    remainingDistance = 0.0;
                    continue;
                }
                position = DirectionGeometry.move(position, direction, distanceToLockBoundary);
                remainingDistance -= distanceToLockBoundary;
                continue;
            }

            if (distanceToDestination <= config.epsilon()) {
                position = destination.center();
                commitLockedExit(destination);
                arrivals = incrementArrivalCount(arrivals, maximumArrivals, tick);
                continue;
            }

            if (remainingDistance <= 0.0) {
                break;
            }

            if (remainingDistance < distanceToDestination) {
                position = DirectionGeometry.move(position, direction, remainingDistance);
                remainingDistance = 0.0;
                continue;
            }

            position = DirectionGeometry.move(position, direction, distanceToDestination);
            remainingDistance -= distanceToDestination;
        }

        return new PlayerKinematics(
                tick,
                position.x(),
                position.y(),
                direction,
                MovementState.CRUISING,
                ActorPhase.AVAILABLE,
                0,
                false,
                AnimationState.MOVING);
    }

    /** Current tick-end world-space center, useful for pure-logic assertions. */
    public PathPoint position() {
        return position;
    }

    /** Current segment direction after the most recent committed node arrival. */
    public Direction direction() {
        return direction;
    }

    /** Returns the frozen session configuration. */
    public PatrolConfig config() {
        return config;
    }

    /** Replaces the not-yet-committed single-slot direction intent. */
    public void queueDirection(Direction requestedDirection) {
        pendingDirection = Objects.requireNonNull(requestedDirection, "requestedDirection");
    }

    /** Returns the input intent still reserved for a future, unlocked node. */
    public Optional<Direction> pendingDirection() {
        return Optional.ofNullable(pendingDirection);
    }

    /** Returns the direction already locked for the current destination, if any. */
    public Optional<Direction> lockedDirection() {
        return Optional.ofNullable(lockedDirection);
    }

    private double distanceToDestination(PathNode destination) {
        double crossAxisError;
        double forwardDistance;
        switch (direction) {
            case UP -> {
                crossAxisError = Math.abs(position.x() - destination.center().x());
                forwardDistance = position.y() - destination.center().y();
            }
            case RIGHT -> {
                crossAxisError = Math.abs(position.y() - destination.center().y());
                forwardDistance = destination.center().x() - position.x();
            }
            case DOWN -> {
                crossAxisError = Math.abs(position.x() - destination.center().x());
                forwardDistance = destination.center().y() - position.y();
            }
            case LEFT -> {
                crossAxisError = Math.abs(position.y() - destination.center().y());
                forwardDistance = position.x() - destination.center().x();
            }
            default -> throw new IllegalStateException("unsupported direction " + direction);
        }
        if (crossAxisError > config.epsilon() || forwardDistance < -config.epsilon()) {
            throw new IllegalStateException(
                    "patrol left its segment " + segmentStartNodeId + " -> " + segmentEndNodeId
                            + " while facing " + direction + ", position=" + position);
        }
        return Math.max(0.0, forwardDistance);
    }

    private boolean isLockedFor(PathNode destination) {
        return lockedNodeId != null && lockedNodeId.equals(destination.id());
    }

    private void lockExitFor(PathNode destination, ExitPassability passability) {
        if (lockedNodeId != null) {
            if (!lockedNodeId.equals(destination.id())) {
                throw new IllegalStateException(
                        "locked node '" + lockedNodeId + "' does not match current destination '" + destination.id() + "'");
            }
            return;
        }

        PathExitDecision decision = PathExitSelector.select(
                graph,
                destination,
                direction,
                pendingDirection,
                passability);
        // The old slot belongs to this node whether it won or was invalid.
        // Inputs submitted after this point populate a new slot for the next node.
        pendingDirection = null;
        lockedNodeId = destination.id();
        lockedDirection = decision.direction();
    }

    private void commitLockedExit(PathNode destination) {
        if (!isLockedFor(destination) || lockedDirection == null) {
            throw new IllegalStateException("destination '" + destination.id() + "' was reached without a locked exit");
        }
        Direction committedDirection = lockedDirection;
        PathNode next = graph.neighbor(destination, committedDirection).orElseThrow(() -> new IllegalStateException(
                "locked direction " + committedDirection + " is missing at path node '" + destination.id() + "'"));
        segmentStartNodeId = destination.id();
        segmentEndNodeId = next.id();
        direction = committedDirection;
        lockedNodeId = null;
        lockedDirection = null;
    }

    private int maximumArrivalsFor(double movementBudget) {
        double rawBound = Math.ceil(movementBudget / graph.shortestSegmentLength()) + 1.0;
        if (rawBound > Integer.MAX_VALUE) {
            throw new IllegalStateException("movement budget creates an unsafe node-arrival bound: " + rawBound);
        }
        return (int) rawBound;
    }

    private int incrementArrivalCount(int arrivals, int maximumArrivals, long tick) {
        int next = arrivals + 1;
        if (next > maximumArrivals) {
            throw new IllegalStateException(
                    "patrol exceeded proven node-arrival bound at tick " + tick
                            + ": " + next + " > " + maximumArrivals);
        }
        return next;
    }
}
