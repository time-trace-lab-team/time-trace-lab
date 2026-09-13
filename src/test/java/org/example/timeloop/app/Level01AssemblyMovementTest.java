package org.example.timeloop.app;

import org.example.timeloop.core.Direction;
import org.example.timeloop.core.MovementState;
import org.example.timeloop.core.input.InputIntent;
import org.example.timeloop.core.input.LogicalKey;
import org.example.timeloop.render.RenderViews;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 集成层（{@code app/}）对 C-PLAYER-MOVE-02 的适配测试。
 *
 * <p>覆盖：无输入静止 / 按住走 / 松开同刻停 / 同刻相反方向停 / 按住前进键再按垂直方向不冻住 /
 * 单槽方向意图在节点中心提交转向 / 普通路段与节点主动掉头 / 出生点反方向不崩 /
 * 驻留板走到中心后停驻并能离开释放占用。</p>
 */
class Level01AssemblyMovementTest {

    /** PatrolConfig.C2_BASE_SPEED：每逻辑刻推进的世界单位。 */
    private static final double BASE_SPEED = 2.0;

    /** Level01Footsteps.TILE_SIZE：第一关格子边长，同时是节点间距与 autoDock 区域边长。 */
    private static final double TILE_SIZE = 48.0;

    private static final double EPSILON = 1e-9;

    private Level01Assembly assembly;

    @AfterEach
    void cleanupAssembly() {
        if (assembly != null) {
            assembly.cleanup();
            assembly = null;
        }
    }

    @Test
    void noInputKeepsPlayerStill() {
        Level01Assembly a = started();
        a.tick(InputIntent.empty(0));
        RenderViews.Player atSpawn = player(a);
        assertEquals(MovementState.IDLE, atSpawn.movementState());

        for (long tick = 1; tick <= 10; tick++) {
            a.tick(InputIntent.empty(tick));
        }
        RenderViews.Player after = player(a);
        assertEquals(atSpawn.x(), after.x(), EPSILON);
        assertEquals(atSpawn.y(), after.y(), EPSILON);
        assertEquals(MovementState.IDLE, after.movementState());
    }

    @Test
    void holdingMovesAtBaseSpeedAndReleasingStopsOnTheSameTick() {
        Level01Assembly a = started();
        a.tick(InputIntent.empty(0));
        double spawnY = player(a).y();

        a.tick(press(1, LogicalKey.DIR_DOWN));
        assertEquals(spawnY + BASE_SPEED, player(a).y(), EPSILON);
        assertEquals(MovementState.CRUISING, player(a).movementState());

        a.tick(hold(2, LogicalKey.DIR_DOWN));
        assertEquals(spawnY + 2 * BASE_SPEED, player(a).y(), EPSILON);

        a.tick(InputIntent.empty(3));
        RenderViews.Player released = player(a);
        assertEquals(MovementState.IDLE, released.movementState());
        assertEquals(spawnY + 2 * BASE_SPEED, released.y(), EPSILON);

        a.tick(InputIntent.empty(4));
        assertEquals(spawnY + 2 * BASE_SPEED, player(a).y(), EPSILON);
    }

    @Test
    void holdingOppositeDirectionsStopsThePlayer() {
        Level01Assembly a = started();
        a.tick(InputIntent.empty(0));
        double spawnY = player(a).y();

        a.tick(new InputIntent(1, Set.of(), Set.of(),
                Set.of(LogicalKey.DIR_DOWN, LogicalKey.DIR_UP), List.of()));

        RenderViews.Player stopped = player(a);
        assertEquals(MovementState.IDLE, stopped.movementState());
        assertEquals(spawnY, stopped.y(), EPSILON);
    }

    /** P0-B：按住前进键再按住垂直方向不能冻住。 */
    @Test
    void holdingForwardAndSideKeepsMovingForward() {
        Level01Assembly a = started();
        a.tick(InputIntent.empty(0));

        long tick = 1;
        tick = drive(a, tick, LogicalKey.DIR_DOWN, 30);           // 段中间（出生点与分叉之间）
        double beforeY = player(a).y();

        a.tick(holdBoth(tick, LogicalKey.DIR_DOWN, LogicalKey.DIR_RIGHT));
        RenderViews.Player moving = player(a);
        assertEquals(MovementState.CRUISING, moving.movementState(), "按住前进+垂直方向不能冻住");
        assertEquals(Direction.DOWN, moving.direction(), "段中间仍沿原方向");
        assertEquals(beforeY + BASE_SPEED, moving.y(), EPSILON);
    }

    /** P0-C：垂直方向的单槽意图在到达分叉节点时提交转向。 */
    @Test
    void heldSideIntentTurnsAtTheNextJunction() {
        Level01Assembly a = started();
        a.tick(InputIntent.empty(0));
        double spawnY = player(a).y();

        long tick = 1;
        tick = drive(a, tick, LogicalKey.DIR_DOWN, 30);           // 出生点 → y = 出生点 + 60
        a.tick(pressWhileHolding(tick++, LogicalKey.DIR_DOWN, LogicalKey.DIR_RIGHT));  // 分叉前按下 RIGHT
        for (int i = 0; i < 40; i++) {                            // 一路按住 DOWN+RIGHT 走过分叉
            a.tick(holdBoth(tick++, LogicalKey.DIR_DOWN, LogicalKey.DIR_RIGHT));
        }

        RenderViews.Player turned = player(a);
        assertEquals(Direction.RIGHT, turned.direction(), "在分叉节点中心提交 90° 转向");
        assertTrue(turned.x() > 264.0, "转向后应离开中轴向右行进，实际 x=" + turned.x());
        assertEquals(spawnY + 2 * TILE_SIZE, turned.y(), EPSILON, "转向发生在分叉所在行");
    }

    @Test
    void countdownWaitsForTheFirstMovementKey() {
        Level01Assembly a = started();

        for (long tick = 0; tick < 240; tick++) {
            a.tick(InputIntent.empty(tick));
        }
        assertEquals(0L, a.hudContext().roundTick(), "等待玩家时不得消耗第一轮时间");
        assertEquals(0, a.currentRecording().orElseThrow().size(), "等待阶段不得写空闲帧");

        a.tick(press(0, LogicalKey.DIR_DOWN));
        assertEquals(1L, a.hudContext().roundTick(), "首个移动输入应从 tick 0 启动计时");
        assertEquals(1, a.currentRecording().orElseThrow().size());
        assertEquals(MovementState.CRUISING, player(a).movementState());
    }

    @Test
    void reversingMidSegmentTakesEffectOnTheSameTick() {
        Level01Assembly a = started();
        long tick = 0;
        tick = drive(a, tick, LogicalKey.DIR_DOWN, 12);
        double before = player(a).y();

        a.tick(press(tick, LogicalKey.DIR_UP));
        RenderViews.Player reversed = player(a);
        assertEquals(Direction.UP, reversed.direction());
        assertEquals(before - BASE_SPEED, reversed.y(), EPSILON);
    }

    /** BUG-001：松开前进键后只按住侧向键，也能预判下一路口并吸附转向。 */
    @Test
    void releasingForwardThenHoldingSideTurnsAtTheNextJunction() {
        Level01Assembly a = started();
        a.tick(InputIntent.empty(0));

        long tick = 1;
        tick = drive(a, tick, LogicalKey.DIR_DOWN, 30);           // (10,3) → (10,4) 之间
        a.tick(press(tick++, LogicalKey.DIR_LEFT));
        for (int i = 0; i < 40; i++) {
            a.tick(hold(tick++, LogicalKey.DIR_LEFT));
        }

        RenderViews.Player turned = player(a);
        assertEquals(Direction.LEFT, turned.direction());
        assertTrue(turned.x() < 10.5 * TILE_SIZE,
                "应在 (10,4) 路口吸附后向左移动，实际 x=" + turned.x());
        assertEquals(4.5 * TILE_SIZE, turned.y(), EPSILON, "转向必须发生在 (10,4) 节点中心行");
    }

    /** P0-A：前方被关闭门挡住时，允许原路返回。 */
    @Test
    void closedDoorDeadEndAllowsReversal() {
        Level01Assembly a = started();
        a.tick(InputIntent.empty(0));

        // 新地图：门在 (18,13)。从下方走廊 (9,14) 绕到 (16,13) 再向右贴上门口节点 (17,13)。
        long tick = 1;
        tick = drive(a, tick, LogicalKey.DIR_DOWN, 24);           // (10,2) 出生点 → (10,3)
        tick = drive(a, tick, LogicalKey.DIR_LEFT, 24);           // (10,3) → (9,3)
        tick = drive(a, tick, LogicalKey.DIR_DOWN, 264);          // (9,3) → (9,14) 竖廊
        tick = drive(a, tick, LogicalKey.DIR_RIGHT, 168);         // (9,14) → (16,14)
        tick = drive(a, tick, LogicalKey.DIR_UP, 24);             // (16,14) → (16,13)
        tick = drive(a, tick, LogicalKey.DIR_RIGHT, 44);          // (16,13) → 停在门前 (17,13)
        double lockedX = 17.5 * TILE_SIZE;
        assertEquals(lockedX, player(a).x(), EPSILON, "关闭的门必须把玩家挡在 (17,13) 节点中心");
        assertEquals(Direction.RIGHT, player(a).direction());

        a.tick(press(tick, LogicalKey.DIR_LEFT));                 // 松 RIGHT、按 LEFT → 被门堵住时掉头
        RenderViews.Player reversed = player(a);
        assertEquals(Direction.LEFT, reversed.direction());
        assertEquals(lockedX - BASE_SPEED, reversed.x(), EPSILON);
    }

    /** P0-A 的连锁修复：驻留板从区域边界走到机关中心再停驻，离开时能在节点中心提交转向并释放占用。 */
    @Test
    void dockingSnapsToMechanismCenterAndLeavingReleasesOccupancy() {
        Level01Assembly a = started();
        a.tick(InputIntent.empty(0));

        // 新地图左驻留板在 (4,6) = (216,312)：出生点 ↓(10,3) ←(8,3) ↓(8,10) ←(5,10) ↑(5,8) ←(4,8) ↑(4,6)
        long tick = 1;
        tick = drive(a, tick, LogicalKey.DIR_DOWN, 24);           // (10,2) → (10,3)
        tick = drive(a, tick, LogicalKey.DIR_LEFT, 48);           // (10,3) → (8,3)
        tick = drive(a, tick, LogicalKey.DIR_DOWN, 168);          // (8,3) → (8,10)
        tick = drive(a, tick, LogicalKey.DIR_LEFT, 72);           // (8,10) → (5,10)
        tick = drive(a, tick, LogicalKey.DIR_UP, 48);             // (5,10) → (5,8)
        tick = drive(a, tick, LogicalKey.DIR_LEFT, 24);           // (5,8) → (4,8)
        tick = drive(a, tick, LogicalKey.DIR_UP, 48);             // (4,8) → 驻留板区域 → 机关中心
        a.tick(InputIntent.empty(tick++));                        // 无输入 → 停驻

        RenderViews.Player docked = player(a);
        assertEquals(MovementState.DOCKED, docked.movementState());
        assertEquals(4.5 * TILE_SIZE, docked.x(), EPSILON, "停驻位置应为机关中心 (4,6) = (216,312)");
        assertEquals(6.5 * TILE_SIZE, docked.y(), EPSILON, "停驻位置应为机关中心 (4,6) = (216,312)");
        assertTrue(a.isPlateOccupied("L01_plate_left"));

        a.tick(press(tick++, LogicalKey.DIR_DOWN));               // 合法离开方向（新左板的出口是 DOWN / RIGHT）
        for (int i = 0; i < 20; i++) {
            a.tick(hold(tick++, LogicalKey.DIR_DOWN));
        }

        RenderViews.Player left = player(a);
        assertEquals(Direction.DOWN, left.direction());
        assertTrue(left.y() > 7 * TILE_SIZE, "应沿中心线走出 autoDock 区域，实际 y=" + left.y());
        assertTrue(!a.isPlateOccupied("L01_plate_left"),
                "离开区域后占用应被释放");
    }

    /**
     * P0-D 修复后的回归：出生点 (10,2) 的出口是 UP/DOWN/LEFT，(11,2) 是墙 ——
     * 按下一个该节点没有出口的方向时豁免不成立 → 既不崩也不掉头，保持静止。
     */
    @Test
    void reverseAtSpawnStaysIdleWithoutCrash() {
        Level01Assembly a = started();
        a.tick(InputIntent.empty(0));
        double spawnY = player(a).y();

        a.tick(press(1, LogicalKey.DIR_RIGHT));
        a.tick(hold(2, LogicalKey.DIR_RIGHT));

        RenderViews.Player idle = player(a);
        assertEquals(MovementState.IDLE, idle.movementState());
        assertEquals(Direction.DOWN, idle.direction());
        assertEquals(spawnY, idle.y(), EPSILON);
    }

    /** PM 对 BUG-001 的裁决：普通直廊节点也允许玩家主动按反方向掉头。 */
    @Test
    void reversalAllowedAtPlainCorridorNode() {
        Level01Assembly a = started();
        a.tick(InputIntent.empty(0));
        double spawnY = player(a).y();

        long tick = 1;
        tick = drive(a, tick, LogicalKey.DIR_DOWN, 24);           // 正好停在 (5,2) 节点中心
        assertEquals(spawnY + TILE_SIZE, player(a).y(), EPSILON);

        a.tick(press(tick, LogicalKey.DIR_UP));
        RenderViews.Player reversed = player(a);
        assertEquals(MovementState.CRUISING, reversed.movementState());
        assertEquals(Direction.UP, reversed.direction());
        assertEquals(spawnY + TILE_SIZE - BASE_SPEED, reversed.y(), EPSILON);
    }

    // ---------- 工具 ----------

    private Level01Assembly started() {
        assembly = new Level01Assembly();
        assembly.start();
        assertTrue(assembly.isPlaying());
        return assembly;
    }

    private static RenderViews.Player player(Level01Assembly a) {
        return a.renderViews().player();
    }

    /** 按住同一方向 {@code ticks} 个逻辑刻；首刻带按下边沿，之后为持续按住。 */
    private static long drive(Level01Assembly a, long tick, LogicalKey key, int ticks) {
        a.tick(press(tick++, key));
        for (int i = 1; i < ticks; i++) {
            a.tick(hold(tick++, key));
        }
        return tick;
    }

    private static InputIntent press(long tick, LogicalKey key) {
        return new InputIntent(tick, Set.of(key), Set.of(), Set.of(key), List.of(directionOf(key)));
    }

    private static InputIntent hold(long tick, LogicalKey key) {
        return new InputIntent(tick, Set.of(), Set.of(), Set.of(key), List.of());
    }

    private static InputIntent holdBoth(long tick, LogicalKey first, LogicalKey second) {
        return new InputIntent(tick, Set.of(), Set.of(), Set.of(first, second), List.of());
    }

    /** 已按住 {@code heldKey} 时新按下 {@code newKey}（带方向边沿，模拟改按另一方向）。 */
    private static InputIntent pressWhileHolding(long tick, LogicalKey heldKey, LogicalKey newKey) {
        return new InputIntent(tick, Set.of(newKey), Set.of(), Set.of(heldKey, newKey),
                List.of(directionOf(newKey)));
    }

    private static Direction directionOf(LogicalKey key) {
        return switch (key) {
            case DIR_UP -> Direction.UP;
            case DIR_DOWN -> Direction.DOWN;
            case DIR_LEFT -> Direction.LEFT;
            case DIR_RIGHT -> Direction.RIGHT;
            default -> throw new IllegalArgumentException("不是方向键: " + key);
        };
    }
}
