package org.example.timeloop.app;

import org.example.timeloop.core.input.InputIntent;
import org.example.timeloop.level.Level02Corridor;
import org.example.timeloop.mechanism.ray.Ray;
import org.example.timeloop.render.RenderViews;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L02-B / B3 验收（交接书 §5.1 与 §5.2 的 headless 部分）。
 *
 * <p>覆盖：①权威射线用共享 {@code roundTick} 更新；②三态在**同一 tick 的 Frame.rays()** 中可见；
 * ③端点与 {@code Ray.getStart()/getEnd()} 一致；④两个门互不影响；⑤L1 的 {@code Frame.rays()} 恒为空（P7）。</p>
 */
class Level02AssemblyRayProjectionTest {

    private Level02Assembly assembly;
    private Level01Assembly levelOne;

    @AfterEach
    void cleanup() {
        if (assembly != null) {
            assembly.cleanup();
            assembly = null;
        }
        if (levelOne != null) {
            levelOne.cleanup();
            levelOne = null;
        }
    }

    /** ① 三条射线状态按共享 roundTick 演进：OFF → WARNING → ACTIVE 都能在同一 tick 的帧里看到。 */
    @Test
    void rayStatesFollowTheSharedRoundTickAndReachTheSameTickFrame() {
        assembly = new Level02Assembly();
        assembly.start();

        assertEquals(1, assembly.rays().size(), "L02 本阶段只有一束射线");
        Ray ray = assembly.rays().get(0);

        assertSameStateInFrameAndAuthority(ray, assembly);


        advanceTo(Level02Corridor.RAY_WARNING_START_TICK + 1);
        assertSameStateInFrameAndAuthority(ray, assembly);
        assertEquals(Ray.State.WARNING, ray.getState(), "预警段应处于 WARNING");
        assertEquals(RenderViews.RayVisualState.WARNING, onlyBeam(assembly).state());

        advanceTo(Level02Corridor.RAY_ACTIVE_START_TICK + 1);
        assertSameStateInFrameAndAuthority(ray, assembly);
        assertEquals(Ray.State.ACTIVE, ray.getState(), "激活段应处于 ACTIVE");
        assertEquals(RenderViews.RayVisualState.ACTIVE, onlyBeam(assembly).state());

        // L2 本关的射线周期在轮内只出现 WARNING 与 ACTIVE 两段（周期尾部即回绕），
        // 因此不在本用例里伪造 OFF 刻；RayVisualState.OFF 的 DTO 级映射由 render 侧
        // RenderViewsRayProjectionTest / RayLayerVisualTest 覆盖，本用例负责"同 tick 帧 == 权威状态"。
        assertSameStateInFrameAndAuthority(ray, assembly);    }

    /** ② 帧里的端点必须逐字等于权威 Ray 的端点（世界坐标，不做屏幕偏移）。 */
    @Test
    void beamEndpointsMatchTheAuthoritativeRay() {
        assembly = new Level02Assembly();
        assembly.start();
        advanceTo(Level02Corridor.RAY_ACTIVE_START_TICK + 1);

        Ray ray = assembly.rays().get(0);
        RenderViews.RayBeam beam = onlyBeam(assembly);
        assertEquals(ray.getId(), beam.id());
        assertEquals(ray.getStart().x(), beam.startX(), 1e-9);
        assertEquals(ray.getStart().y(), beam.startY(), 1e-9);
        assertEquals(ray.getEnd().x(), beam.endX(), 1e-9);
        assertEquals(ray.getEnd().y(), beam.endY(), 1e-9);
    }

    /** ③ 两个门互不影响：只占门外板 → 房门开、出口闸门仍锁。 */
    @Test
    void theTwoDoorsUnlockIndependently() {
        assembly = new Level02Assembly();
        assembly.start();

        assertFalse(mechanism(assembly, Level02Corridor.DOOR_ROOM).active(), "初始房门应关闭");
        assertFalse(mechanism(assembly, Level02Corridor.EXIT).active(), "初始闸门应关闭");

        assertTrue(assembly.plate(Level02Corridor.PLATE_DOOR).tryEnter(
                Level02Assembly.PLAYER_ACTOR_ID, Level02Assembly.PLAYER_SOURCE_ROUND, 100));

        assertTrue(mechanism(assembly, Level02Corridor.DOOR_ROOM).active(),
                "占住门外板后房门应打开");
        assertFalse(mechanism(assembly, Level02Corridor.EXIT).active(),
                "房门打开不得顺带解锁出口闸门（两门判定独立）");

        // 多门阻挡：房门已开 ⇒ 房门那一格不再被挡；闸门仍锁 ⇒ 闸门那一格仍被挡。
        assertFalse(assembly.blockedByAnyDoor(
                assembly.door(Level02Corridor.DOOR_ROOM).getPosition().x(),
                assembly.door(Level02Corridor.DOOR_ROOM).getPosition().y()));
        assertTrue(assembly.blockedByAnyDoor(
                assembly.door(Level02Corridor.DOOR_EXIT).getPosition().x(),
                assembly.door(Level02Corridor.DOOR_EXIT).getPosition().y()));
    }

    /** ④ P7：L1 继续用三参兼容构造器 ⇒ 射线列表恒为空。 */
    @Test
    void levelOneFrameCarriesNoRays() {
        levelOne = new Level01Assembly();
        levelOne.start();
        List<RenderViews.RayBeam> rays = levelOne.renderViews().rays();
        assertTrue(rays.isEmpty(), "L1 画面不得出现射线，实际 " + rays);
    }

    // ---------- 工具 ----------

    private void advanceTo(long targetTick) {
        while (assembly.hudContext().roundTick() < targetTick) {
            assembly.tick(InputIntent.empty(assembly.hudContext().roundTick()));
        }
    }

    private static void assertSameStateInFrameAndAuthority(Ray ray, Level02Assembly target) {
        RenderViews.RayVisualState projected = onlyBeam(target).state();
        RenderViews.RayVisualState expected = switch (ray.getState()) {
            case OFF -> RenderViews.RayVisualState.OFF;
            case WARNING -> RenderViews.RayVisualState.WARNING;
            case ACTIVE -> RenderViews.RayVisualState.ACTIVE;
        };
        assertEquals(expected, projected, "同 tick 的投影必须等于权威状态");
    }

    private static RenderViews.RayBeam onlyBeam(Level02Assembly target) {
        List<RenderViews.RayBeam> beams = target.renderViews().rays();
        assertEquals(1, beams.size(), "本阶段应恰好一束射线");
        return beams.get(0);
    }

    private static RenderViews.Mechanism mechanism(Level02Assembly target, String mechanismId) {
        return target.renderViews().mechanisms().stream()
                .filter(candidate -> candidate.id().equals(mechanismId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("帧里缺少机关: " + mechanismId));
    }
}
