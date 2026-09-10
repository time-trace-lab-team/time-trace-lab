package org.example.timeloop.core.path;

/**
 * Read-only per-tick answer to whether a declared path exit may be used.
 *
 * <p>C2 intentionally knows nothing about doors or mechanisms. A later
 * integration layer may adapt its state to this narrow query without creating
 * a core-to-mechanism dependency.</p>
 */
@FunctionalInterface
public interface ExitPassability {

    /** Returns whether the directed exit is passable for the current tick. */
    boolean isPassable(PathNode from, PathExit exit, PathNode target);

    /** A query for greybox tests and paths with no temporary obstruction. */
    static ExitPassability allOpen() {
        return (from, exit, target) -> true;
    }
}
