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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * C-PLAYER-MOVE-01：四方向受约束移动 — 节点/转向/障碍行为。
 */
class PatrolControllerNodeBehaviorTest {

    private static final double EPS = PatrolConfig.C2_EPSILON;
    private static final Optional<Direction> NONE = Optional.empty();

    // ========== 1. 无输入到达路口不自动转向 ==========

    @Test
    void noInputAtJunction_doesNotAutoTurn() {
        PatrolController c = new PatrolController(
                lockedGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());

        // 按住 DOWN，走到 junction 中心后直行不可通行（jct 无 DOWN 出口）→ 停在中心
        PlayerKinematics last = null;
        for (long tick = 0; tick < 20; tick++) {
            last = c.advance(tick, Optional.of(Direction.DOWN), ExitPassability.allOpen());
        }
        assertEquals(0.0, last.x(), EPS);
        assertEquals(0.0, last.y(), EPS);
        assertEquals(MovementState.IDLE, last.movementState(),
                "直行不可通行时应在节点中心停下");
    }

    // ========== 2. 段中间按 90° → 停下 ==========

    @Test
    void perpendicularMidSegment_stopsIdle() {
        PatrolController c = new PatrolController(
                lockedGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());

        // 先走几 tick，脱离段起点但没到 junction
        for (long tick = 0; tick < 2; tick++) {
            c.advance(tick, Optional.of(Direction.DOWN), ExitPassability.allOpen());
        }
        // 此时 y ≈ -4（段 start(-8) → junction(0)），未到中心
        PlayerKinematics result = c.advance(2, Optional.of(Direction.RIGHT), ExitPassability.allOpen());
        assertEquals(MovementState.IDLE, result.movementState(),
                "段中间按 90° 应停在原地");
        assertEquals(0.0, result.x(), EPS);
    }

    // ========== 3. 节点中心按 90° → 提交转向 ==========

    @Test
    void perpendicularAtCenter_commitsTurn() {
        PatrolController c = new PatrolController(
                lockedGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());

        // 走到 junction 中心（start(-8) → junction(0)：距离 8，需要 4 tick）
        for (long tick = 0; tick < 4; tick++) {
            c.advance(tick, Optional.of(Direction.DOWN), ExitPassability.allOpen());
        }
        // 此时在 junction 中心；按 RIGHT
        PlayerKinematics turned = c.advance(4, Optional.of(Direction.RIGHT), ExitPassability.allOpen());
        assertEquals(Direction.RIGHT, turned.direction());
        assertEquals(MovementState.CRUISING, turned.movementState());
        assertEquals(2.0, turned.x(), EPS, "转向后沿新段推进 2 单位");
        assertEquals(0.0, turned.y(), EPS);
    }

    // ========== 4. 前方关门 → 停在节点中心 ==========

    @Test
    void closedDoorAhead_stopsAtNodeCenter() {
        PatrolController c = new PatrolController(
                lockedGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());

        // 关门：junction 处 DOWN 出口不可通行
        ExitPassability closedDown = (from, exit, target) ->
                !(from.id().equals("junction") && exit.direction() == Direction.DOWN);

        PlayerKinematics last = null;
        for (long tick = 0; tick < 6; tick++) {
            last = c.advance(tick, Optional.of(Direction.DOWN), closedDown);
        }
        assertEquals(0.0, last.x(), EPS);
        assertEquals(0.0, last.y(), EPS);
        assertEquals(MovementState.IDLE, last.movementState());
    }

    // ========== 5. 按反方向 → 停下 ==========

    @Test
    void oppositeDirection_stopsIdle() {
        PatrolController c = new PatrolController(
                lockedGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());

        for (long tick = 0; tick < 2; tick++) {
            c.advance(tick, Optional.of(Direction.DOWN), ExitPassability.allOpen());
        }
        PlayerKinematics result = c.advance(2, Optional.of(Direction.UP), ExitPassability.allOpen());
        assertEquals(MovementState.IDLE, result.movementState());
        assertEquals(Direction.DOWN, result.direction(), "朝向不因反方向输入改变");
    }

    // ========== 6. 残影式确定性：同输入 → 同结果 ==========

    @Test
    void sameInputs_produceIdenticalStates() {
        PatrolController a = new PatrolController(
                lockedGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());
        PatrolController b = new PatrolController(
                lockedGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());

        for (long tick = 0; tick < 10; tick++) {
            Optional<Direction> dir = (tick < 4) ? Optional.of(Direction.DOWN) : Optional.empty();
            PlayerKinematics ra = a.advance(tick, dir, ExitPassability.allOpen());
            PlayerKinematics rb = b.advance(tick, dir, ExitPassability.allOpen());
            assertEquals(ra, rb, "tick " + tick);
        }
    }

    // ========== 7. IDLE 段仍逐 tick 写入帧 ==========

    @Test
    void idleWritesFrameEveryTick() {
        PatrolController c = new PatrolController(
                lockedGraph(), "start", Direction.DOWN, PatrolConfig.c2Greybox());

        for (long tick = 0; tick < 5; tick++) {
            PlayerKinematics k = c.advance(tick, NONE, ExitPassability.allOpen());
            assertEquals(tick, k.tick(), "IDLE 帧必须逐 tick 写入");
            assertEquals(MovementState.IDLE, k.movementState());
        }
    }

    // ========== 图形 ==========

    /**
     * start(0,-8) --DOWN--> junction(0,0) --RIGHT--> east(8,0)
     */
    private static OrthogonalPathGraph lockedGraph() {
        return new OrthogonalPathGraph(List.of(
                node("start", 0.0, -8.0, List.of(new PathExit(Direction.DOWN, "junction"))),
                node("junction", 0.0, 0.0, List.of(
                        new PathExit(Direction.UP, "start"),
                        new PathExit(Direction.RIGHT, "east"))),
                node("east", 8.0, 0.0, List.of(new PathExit(Direction.LEFT, "junction")))));
    }

    private static PathNode node(String id, double x, double y, List<PathExit> exits) {
        return new PathNode(id, new PathPoint(x, y), exits);
    }
}