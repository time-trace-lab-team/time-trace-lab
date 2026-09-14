package org.example.timeloop.mechanism.ray;

import org.example.timeloop.level.Level02Corridor;
import org.example.timeloop.level.model.EntitySpawnInfo;
import org.example.timeloop.level.model.LevelData;
import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L2-B 射线装配与周期（卡 {@code L02-B-DEV3} §三）。
 *
 * <p>覆盖：周期参数符合 README §三 / 由共享 {@code roundTick} 驱动 / 判定线不覆盖任何压力板驻留点
 * （裁决 §十一.4 约束 5）/ 命中只做几何判定（减速结算与相位豁免属移动层）/
 * 装配全程使用注入总线且**源码里不出现全局单例**。</p>
 */
class L02RayTest {

    private static final double TILE = Level02Corridor.TILE_SIZE;

    @Test
    void levelTwoDeclaresExactlyOneRayWithReadmeCompliantCycle() {
        LevelData level = Level02Corridor.build();
        List<Ray> rays = RayFactory.buildFrom(level, new EventDispatcher());

        assertEquals(1, rays.size(), "L2 只应有一束低风险射线（裁决 §二）");
        Ray ray = rays.get(0);
        assertEquals(Level02Corridor.RAY_CORRIDOR, ray.getId());

        long warning = Level02Corridor.RAY_WARNING_DURATION_TICKS;
        long active = Level02Corridor.RAY_ACTIVE_DURATION_TICKS;

        // README §三：预警 1.0–1.4 s（60–84 刻）、激活 0.8–1.2 s（48–72 刻）
        assertTrue(warning >= 60 && warning <= 84, "预警时长须在 60–84 刻，实测 " + warning);
        assertTrue(active >= 48 && active <= 72, "激活时长须在 48–72 刻，实测 " + active);

        // README §三 公平性：预警 ≥ 反应预算(24–30) + 输入缓冲(6–10)
        assertTrue(warning >= 30 + 10, "预警必须覆盖最坏情况下的反应预算 + 输入缓冲");
        // README §三 相位公平性：激活窗口 ≥ 穿越 1 格(24 刻) + 成功余量(≥12 刻)
        assertTrue(active >= 24 + 12, "激活窗口必须容得下穿越 1 格并留余量");
    }

    @Test
    void cycleIsDrivenBySharedRoundTickOnly() {
        List<Ray> rays = RayFactory.buildFrom(Level02Corridor.build(), new EventDispatcher());
        Ray ray = rays.get(0);

        RayFactory.updateAll(rays, Level02Corridor.RAY_WARNING_START_TICK);
        assertEquals(Ray.State.WARNING, ray.getState(), "周期起点应为 WARNING");

        RayFactory.updateAll(rays, Level02Corridor.RAY_ACTIVE_START_TICK - 1);
        assertEquals(Ray.State.WARNING, ray.getState(), "预警最后一刻仍是 WARNING");

        RayFactory.updateAll(rays, Level02Corridor.RAY_ACTIVE_START_TICK);
        assertEquals(Ray.State.ACTIVE, ray.getState(), "激活起点应为 ACTIVE");

        long cycle = Level02Corridor.RAY_WARNING_START_TICK
                + Level02Corridor.RAY_WARNING_DURATION_TICKS
                + Level02Corridor.RAY_ACTIVE_DURATION_TICKS;
        RayFactory.updateAll(rays, cycle - 1);
        assertEquals(Ray.State.ACTIVE, ray.getState(), "激活最后一刻仍是 ACTIVE");

        RayFactory.updateAll(rays, cycle);
        assertEquals(Ray.State.WARNING, ray.getState(), "下一周期从 WARNING 重新开始（周期 " + cycle + " 刻）");
    }

    /** 裁决 §十一.4 约束 5：射线不得覆盖任何压力板驻留点。 */
    @Test
    void rayDoesNotCoverAnyPlateDwellPoint() {
        LevelData level = Level02Corridor.build();
        Ray ray = RayFactory.buildFrom(level, new EventDispatcher()).get(0);

        List<EntitySpawnInfo> plates = level.getEntitySpawnList().stream()
                .filter(e -> "dock_plate".equals(e.getEntityType()))
                .toList();
        assertEquals(3, plates.size(), "L2 应有三块压力板");

        // 用「整个激活期」都判定：任一刻都不得覆盖驻留点
        for (long tick = Level02Corridor.RAY_ACTIVE_START_TICK;
             tick < Level02Corridor.RAY_ACTIVE_START_TICK + Level02Corridor.RAY_ACTIVE_DURATION_TICKS;
             tick++) {
            RayFactory.updateAll(List.of(ray), tick);
            assertEquals(Ray.State.ACTIVE, ray.getState());
            for (EntitySpawnInfo plate : plates) {
                double distance = distanceToSegment(plate.getPos(), ray.getStart(), ray.getEnd());
                assertTrue(distance > TILE,
                        "板 " + plate.getId() + " 到射线距离必须 > 1 格，实测 " + distance);
                assertFalse(ray.containsPoint(plate.getPos(), TILE),
                        "射线判定不得覆盖驻留点: " + plate.getId());
            }
        }
    }

    @Test
    void hitIsPureGeometryInsideSegmentWithinWidth() {
        Ray ray = RayFactory.buildFrom(Level02Corridor.build(), new EventDispatcher()).get(0);
        RayFactory.updateAll(List.of(ray), Level02Corridor.RAY_ACTIVE_START_TICK);
        assertEquals(Ray.State.ACTIVE, ray.getState());

        double x = Level02Corridor.RAY_X;
        double width = Level02Corridor.RAY_HIT_WIDTH;

        // 线段内、宽度内 → 命中
        assertTrue(ray.containsPoint(new Vector2D(x, 11.5 * TILE), width), "走廊中心应命中");
        assertTrue(ray.containsPoint(new Vector2D(x + 0.5 * width, 11.5 * TILE), width),
                "半宽内应命中");
        // 宽度外 → 不命中
        assertFalse(ray.containsPoint(new Vector2D(x + 2 * width, 11.5 * TILE), width),
                "超出判定宽度不得命中");
        // 线段端点之外 → 不命中
        assertFalse(ray.containsPoint(new Vector2D(x, ray.getEnd().y() + 2 * width), width),
                "线段之外不得命中");

        // 非激活态不做命中（预警期穿过不受影响）
        RayFactory.updateAll(List.of(ray), Level02Corridor.RAY_ACTIVE_START_TICK - 1);
        assertEquals(Ray.State.WARNING, ray.getState());
        assertFalse(RayFactory.hits(List.of(ray), new Vector2D(x, 11.5 * TILE), width),
                "WARNING 期不得结算命中");
    }

    /** 注入优先：装配只依赖调用方传入的总线；源码里不得出现任何全局单例。 */
    @Test
    void assemblyUsesInjectedBusAndRaySourcesNeverTouchGlobalSingletons() throws IOException {
        EventDispatcher firstBus = new EventDispatcher();
        EventDispatcher secondBus = new EventDispatcher();

        List<Ray> first = RayFactory.buildFrom(Level02Corridor.build(), firstBus);
        List<Ray> second = RayFactory.buildFrom(Level02Corridor.build(), secondBus);

        assertEquals(1, first.size());
        assertEquals(1, second.size(), "同一 JVM 内可重复装配（无全局单例冲突）");
        RayFactory.disposeAll(first);

        Path rayDir = Path.of("src", "main", "java", "org", "example", "timeloop", "mechanism", "ray");
        assertTrue(Files.isDirectory(rayDir), "找不到射线源码目录: " + rayDir.toAbsolutePath());
        try (var files = Files.walk(rayDir)) {
            List<Path> javaFiles = files.filter(p -> p.toString().endsWith(".java")).toList();
            assertFalse(javaFiles.isEmpty(), "射线目录应含源码");
            for (Path file : javaFiles) {
                String source = Files.readString(file);
                assertFalse(source.contains("getInstance"),
                        "射线源码不得引用全局单例（BUG-002 Phase 2）: " + file);
            }
        }
    }

    private static double distanceToSegment(Vector2D point, Vector2D start, Vector2D end) {
        double dx = end.x() - start.x();
        double dy = end.y() - start.y();
        double len2 = dx * dx + dy * dy;
        if (len2 == 0) {
            return Math.hypot(point.x() - start.x(), point.y() - start.y());
        }
        double t = ((point.x() - start.x()) * dx + (point.y() - start.y()) * dy) / len2;
        double clamped = Math.max(0, Math.min(1, t));
        double px = start.x() + clamped * dx;
        double py = start.y() + clamped * dy;
        return Math.hypot(point.x() - px, point.y() - py);
    }
}
