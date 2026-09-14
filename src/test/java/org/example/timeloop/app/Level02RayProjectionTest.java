package org.example.timeloop.app;

import org.example.timeloop.core.Direction;
import org.example.timeloop.core.input.InputIntent;
import org.example.timeloop.core.input.LogicalKey;
import org.example.timeloop.level.Level02Corridor;
import org.example.timeloop.mechanism.ray.Ray;
import org.example.timeloop.render.RenderViews;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L02-B / B3（app 投影与图层接线）的 headless 验收。
 *
 * <p>锁三件事：</p>
 * <ol>
 *   <li><b>同一刻一致性（P5/P6）</b>：权威 {@link Ray} 的状态由共享 {@code roundTick} 决定，
 *       投影进 {@link RenderViews.Frame#rays()} 的状态必须与<b>同一刻</b>的权威状态逐刻相等
 *       —— 画面不得比碰撞早或晚一 tick；</li>
 *   <li><b>端点无偏移（P8）</b>：帧里的端点就是 {@code Ray.getStart()/getEnd()} 的世界坐标，
 *       app 侧不得再叠任何屏幕偏移；</li>
 *   <li><b>L1 隔离（P7）</b>：第一关的帧恒为空射线，第一关画面不可能出现射线。</li>
 * </ol>
 */
class Level02RayProjectionTest {

    private Level02Assembly assembly;
    private Level01Assembly levelOne;

    @AfterEach
    void cleanupAssemblies() {
        if (assembly != null) {
            assembly.cleanup();
            assembly = null;
        }
        if (levelOne != null) {
            levelOne.cleanup();
            levelOne = null;
        }
    }

    @Test
    void frameRayStateEqualsAuthoritativeRayStateOnTheSameTick() {
        assembly = started();
        long tick = 0L;
        assembly.tick(press(tick++, LogicalKey.DIR_UP));

        Set<RenderViews.RayVisualState> observed = EnumSet.noneOf(RenderViews.RayVisualState.class);
        for (int step = 0; step < 400; step++) {
            // 本刻将被 tick() 使用的共享 roundTick：射线状态完全由它决定（无独立计时器）。
            long processingTick = assembly.hudContext().roundTick();
            assembly.tick(InputIntent.empty(tick++));

            List<Ray> authoritative = assembly.rays();
            assertEquals(1, authoritative.size(), "第二关应恰好装配 1 束射线");
            Ray ray = authoritative.get(0);

            assertEquals(expectedState(processingTick), ray.getState(),
                    "刻 " + processingTick + " 的权威射线状态与周期公式不符");

            List<RenderViews.RayBeam> beams = assembly.renderViews().rays();
            assertEquals(1, beams.size(), "第二关帧必须携带该射线（否则画面上看不见它）");
            RenderViews.RayBeam beam = beams.get(0);
            assertEquals(ray.getState().name(), beam.state().name(),
                    "刻 " + processingTick + " 的帧射线状态与权威状态不同刻（画面早于或晚于碰撞）");
            observed.add(beam.state());
        }

        assertEquals(EnumSet.allOf(RenderViews.RayVisualState.class), observed,
                "一个周期内 OFF → WARNING → ACTIVE 三态都必须真的进入过画面帧");
    }

    @Test
    void frameRayEndpointsAreTheAuthoritativeWorldCoordinates() {
        assembly = started();
        long tick = 0L;
        assembly.tick(press(tick++, LogicalKey.DIR_UP));

        RenderViews.RayBeam beam = assembly.renderViews().rays().get(0);
        Ray ray = assembly.rays().get(0);

        assertEquals(Level02Corridor.RAY_CORRIDOR, beam.id(), "射线投影必须沿用关卡数据里的实体 ID");
        assertEquals(ray.getId(), beam.id());
        assertEquals(ray.getStart().x(), beam.startX(), 0.0);
        assertEquals(ray.getStart().y(), beam.startY(), 0.0);
        assertEquals(ray.getEnd().x(), beam.endX(), 0.0);
        assertEquals(ray.getEnd().y(), beam.endY(), 0.0);
        // 关卡数据里的几何：x 固定，y 从 RAY_Y0 到 RAY_Y1（南北向短廊）。
        assertEquals(Level02Corridor.RAY_X, beam.startX(), 0.0);
        assertEquals(Level02Corridor.RAY_Y0, beam.startY(), 0.0);
        assertEquals(Level02Corridor.RAY_Y1, beam.endY(), 0.0);
        assertTrue(beam.endY() > beam.startY(), "夹具前提：射线自北向南");
    }

    @Test
    void firstLevelFramesNeverCarryRays() {
        levelOne = new Level01Assembly();
        levelOne.start();

        assertTrue(levelOne.renderViews().rays().isEmpty(), "第一关初始帧不得有射线");

        long tick = 0L;
        levelOne.tick(press(tick++, LogicalKey.DIR_DOWN));
        for (int i = 0; i < 24; i++) {
            levelOne.tick(InputIntent.empty(tick++));
            assertTrue(levelOne.renderViews().rays().isEmpty(),
                    "第一关推进中也不得出现射线（L1 画面零变化）");
        }
        assertTrue(levelOne.phase().advancesLogic(), "夹具前提：第一关仍在进行中");
    }

    /** 一个周期内的期望状态：{@code [0,60) OFF → [60,132) WARNING → [132,192) ACTIVE}，之后按周期重复。 */
    private static Ray.State expectedState(long roundTick) {
        long cycleTick = roundTick % Level02Corridor.RAY_CYCLE_TICKS;
        if (cycleTick < Level02Corridor.RAY_WARNING_START_TICK) {
            return Ray.State.OFF;
        }
        if (cycleTick < Level02Corridor.RAY_ACTIVE_START_TICK) {
            return Ray.State.WARNING;
        }
        return Ray.State.ACTIVE;
    }

    private static Level02Assembly started() {
        Level02Assembly started = new Level02Assembly();
        started.start();
        assertTrue(started.isPlaying());
        return started;
    }

    private static InputIntent press(long tick, LogicalKey key) {
        return new InputIntent(tick, Set.of(key), Set.of(), Set.of(key), List.of(directionOf(key)));
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
