package org.example.timeloop.entity;

import org.example.timeloop.core.Direction;
import org.example.timeloop.core.MovementState;
import org.example.timeloop.core.PlayerKinematics;
import org.example.timeloop.core.path.ExitPassability;
import org.example.timeloop.core.path.OrthogonalPathGraph;
import org.example.timeloop.core.path.PathExit;
import org.example.timeloop.core.path.PathNode;
import org.example.timeloop.core.path.PathPoint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** CORE-2 v2：速度倍率在移动、节点转向和轮初复位后的契约测试。 */
class PatrolControllerSpeedModifierTest {

    private static final double EPS = 1e-7;

    @Test
    void halfMultiplierMovesOneUnitPerTickAndReportsSlowed() {
        PatrolController controller = controller(graphWithCorner(), () -> 0.5);

        PlayerKinematics frame = advance(controller, 0, Set.of(Direction.DOWN), Optional.empty());

        assertEquals(0.0, frame.x(), EPS);
        assertEquals(-1.0, frame.y(), EPS);
        assertEquals(MovementState.SLOWED, frame.movementState());
    }

    @Test
    void restoringNormalMultiplierRestoresBaseSpeedAndCruisingState() {
        AtomicReference<Double> multiplier = new AtomicReference<>(0.5);
        PatrolController controller = controller(graphWithCorner(), multiplier::get);

        PlayerKinematics slowed = advance(controller, 0, Set.of(Direction.DOWN), Optional.empty());
        multiplier.set(1.0);
        PlayerKinematics restored = advance(controller, 1, Set.of(Direction.DOWN), Optional.empty());

        assertEquals(-1.0, slowed.y(), EPS);
        assertEquals(1.0, restored.y(), EPS, "恢复后本刻应以 baseSpeed=2 推进");
        assertEquals(MovementState.CRUISING, restored.movementState());
    }

    @Test
    void halfMultiplierAppliesMidSegmentAndAfterTurnAtNodeCenter() {
        PatrolController controller = controller(graphWithCorner(), () -> 0.5);

        PlayerKinematics midSegment = advance(controller, 0, Set.of(Direction.DOWN), Optional.empty());
        PlayerKinematics atCenter = advance(controller, 1,
                Set.of(Direction.DOWN, Direction.RIGHT), Optional.of(Direction.RIGHT));
        PlayerKinematics afterTurn = advance(controller, 2,
                Set.of(Direction.DOWN, Direction.RIGHT), Optional.empty());

        assertEquals(-1.0, midSegment.y(), EPS);
        assertEquals(0.0, atCenter.y(), EPS, "到节点中心仍只消耗 1 world unit");
        assertEquals(Direction.RIGHT, afterTurn.direction());
        assertEquals(1.0, afterTurn.x(), EPS, "转向后的本刻仍使用 0.5 倍速");
        assertEquals(MovementState.SLOWED, afterTurn.movementState());
    }

    @Test
    void resetKeepsTheCurrentSpeedModifier() {
        PatrolController controller = controller(graphWithCorner(), () -> 0.5);
        advance(controller, 0, Set.of(Direction.DOWN), Optional.empty());

        controller.resetTo("start", Direction.DOWN);
        PlayerKinematics afterReset = advance(controller, 1, Set.of(Direction.DOWN), Optional.empty());

        assertEquals(0.0, afterReset.x(), EPS);
        assertEquals(-1.0, afterReset.y(), EPS);
        assertEquals(MovementState.SLOWED, afterReset.movementState());
    }

    @Test
    void noInputRemainsIdleRegardlessOfSpeedModifier() {
        PatrolController controller = controller(graphWithCorner(), () -> 0.5);

        PlayerKinematics frame = advance(controller, 0, Set.of(), Optional.empty());

        assertEquals(0.0, frame.x(), EPS);
        assertEquals(-2.0, frame.y(), EPS);
        assertEquals(MovementState.IDLE, frame.movementState());
    }

    private static PlayerKinematics advance(PatrolController controller,
                                             long tick,
                                             Set<Direction> held,
                                             Optional<Direction> edge) {
        return controller.advance(tick, held, edge, ExitPassability.allOpen());
    }

    private static PatrolController controller(OrthogonalPathGraph graph, SpeedModifierPort modifier) {
        return new PatrolController(graph, "start", Direction.DOWN, PatrolConfig.c2Greybox(), modifier);
    }

    private static OrthogonalPathGraph graphWithCorner() {
        return new OrthogonalPathGraph(List.of(
                node("start", 0.0, -2.0, List.of(new PathExit(Direction.DOWN, "corner"))),
                node("corner", 0.0, 0.0, List.of(
                        new PathExit(Direction.UP, "start"),
                        new PathExit(Direction.RIGHT, "east"),
                        new PathExit(Direction.DOWN, "south"))),
                node("east", 4.0, 0.0, List.of(new PathExit(Direction.LEFT, "corner"))),
                node("south", 0.0, 4.0, List.of(new PathExit(Direction.UP, "corner")))));
    }

    private static PathNode node(String id, double x, double y, List<PathExit> exits) {
        return new PathNode(id, new PathPoint(x, y), exits);
    }
}
