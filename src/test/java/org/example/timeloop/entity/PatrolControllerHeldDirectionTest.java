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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P0-B：多键按住的处理。
 *
 * <p>按住当前朝向 + 90° 方向 → 继续沿当前朝向行进；到节点中心后转向 90°。</p>
 */
class PatrolControllerHeldDirectionTest {

    private static final double EPS = PatrolConfig.C2_EPSILON;

    @Test
    void holdingDirectionAndSide_continuesMoving() {
        // 图：start(0,-4) --DOWN--> jct(0,0) --RIGHT--> east(8,0)
        PatrolController c = new PatrolController(junctionGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());

        // 第一帧：只按 DOWN
        PlayerKinematics first = c.advance(0, Set.of(Direction.DOWN), Optional.empty(), ExitPassability.allOpen());
        assertEquals(MovementState.CRUISING, first.movementState());

        // 第二帧：按住 DOWN + RIGHT（模拟玩家按住前进键再改按垂直方向）
        PlayerKinematics second = c.advance(1, Set.of(Direction.DOWN, Direction.RIGHT), Optional.empty(), ExitPassability.allOpen());
        assertEquals(MovementState.CRUISING, second.movementState(), "按住前进+垂直方向不能冻住");
        assertEquals(Direction.DOWN, second.direction(), "段中间仍沿原方向");
    }

    @Test
    void holdingDirectionAndSide_thenEdgeCommittedAtNode() {
        PatrolController c = new PatrolController(junctionGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());

        // 走到 jct 中心（start(-4) → jct(0)：距离 4，2 tick）
        c.advance(0, Set.of(Direction.DOWN), Optional.empty(), ExitPassability.allOpen());
        c.advance(1, Set.of(Direction.DOWN, Direction.RIGHT), Optional.of(Direction.RIGHT), ExitPassability.allOpen());

        // 在 jct 中心，newestEdge=RIGHT 且仍按住 → 转向
        PlayerKinematics atJct = c.advance(2, Set.of(Direction.DOWN, Direction.RIGHT), Optional.of(Direction.RIGHT), ExitPassability.allOpen());
        assertEquals(Direction.RIGHT, atJct.direction(), "到节点中心提交 90° 转向");
    }

    @Test
    void holdingOppositeDirections_staysIdle() {
        PatrolController c = new PatrolController(junctionGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());
        c.advance(0, Set.of(Direction.DOWN), Optional.empty(), ExitPassability.allOpen());
        // 同刻按 DOWN + UP（相反）
        PlayerKinematics result = c.advance(1, Set.of(Direction.DOWN, Direction.UP), Optional.empty(), ExitPassability.allOpen());
        assertEquals(MovementState.IDLE, result.movementState(), "同刻相反方向应 IDLE");
    }

    private static OrthogonalPathGraph junctionGraph() {
        return new OrthogonalPathGraph(List.of(
                node("start", 0.0, -4.0, List.of(new PathExit(Direction.DOWN, "jct"))),
                node("jct", 0.0, 0.0, List.of(
                        new PathExit(Direction.UP, "start"),
                        new PathExit(Direction.RIGHT, "east"))),
                node("east", 8.0, 0.0, List.of(new PathExit(Direction.LEFT, "jct")))));
    }

    private static PathNode node(String id, double x, double y, List<PathExit> exits) {
        return new PathNode(id, new PathPoint(x, y), exits);
    }
}