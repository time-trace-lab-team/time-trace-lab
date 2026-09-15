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
 * 第二关射线装配、周期与判定（桌面工具 {@code L02_ray_01} + README §三）。
 *
 * <p>参数逐字来自新地图数据：{@code x = 288}（第 5/6 格之间）、{@code y 600 → 696}、
 * 预警起点 60 / 预警 72 刻 / 激活起点 132 / 激活 60 刻 → <b>周期 192 刻</b>。</p>
 *
 * <p>覆盖：周期参数符合 README §三 / 由共享 {@code roundTick} 驱动 / 判定线不覆盖任何驻留点 /
 * 命中只做几何判定 / 装配全程使用注入总线且<b>源码里不出现全局单例</b>。</p>
 */
class L02RayTest {

    private static final double TILE = Level02Corridor.TILE_SIZE;

    @Test
    void levelTwoDeclaresExactlyOneRayWithTheDesignedSegment() {
        LevelData level = Level02Corridor.build();
        List<Ray> rays = RayFactory.buildFrom(level, new EventDispatcher());

        assertEquals(1, rays.size(), "L2 只应有一束射线");
        Ray ray = rays.get(0);
        assertEquals(Level02Corridor.RAY_CORRIDOR, ray.getId());

        // 端点逐字来自关卡数据（x = 6.0 × 48 = 288；y = 12.5 × 48 = 600 → 14.5 × 48 = 696）。
        assertEquals(288.0, Level02Corridor.RAY_X, 1e-9);
        assertEquals(600.0, Level02Corridor.RAY_Y0, 1e-9);
        assertEquals(696.0, Level02Corridor.RAY_Y1, 1e-9);
        assertEquals(Level02Corridor.RAY_X, ray.getStart().x(), 1e-9);
        assertEquals(Level02Corridor.RAY_Y0, ray.getStart().y(), 1e-9);
        assertEquals(Level02Corridor.RAY_X, ray.getEnd().x(), 1e-9);
        assertEquals(Level02Corridor.RAY_Y1, ray.getEnd().y(), 1e-9);

        // 这条线横穿南厅第 13 行的东西向通路（出生点就在这一行）。
        assertTrue(Level02Corridor.isOpen(5, 13) && Level02Corridor.isOpen(6, 13),
                "射线所在位置两侧必须是地板（玩家沿 r13 往返必然穿过）");
        assertTrue(13.5 * TILE >= ray.getStart().y() && 13.5 * TILE <= ray.getEnd().y(),
                "第 13 行中心必须落在判定线段内");

        EntitySpawnInfo entity = level.getEntitySpawnList().stream()
                .filter(e -> Level02Corridor.RAY_CORRIDOR.equals(e.getId()))
                .findFirst().orElseThrow();
        assertEquals(Level02Corridor.RAY_X, (double) entity.getProperties().get("endX"), 1e-9);
        assertEquals(Level02Corridor.RAY_Y1, (double) entity.getProperties().get("endY"), 1e-9);
        assertEquals(Level02Corridor.RAY_WARNING_START_TICK,
                ((Number) entity.getProperties().get("warningStartTick")).longValue());
        assertEquals(Level02Corridor.RAY_WARNING_DURATION_TICKS,
                ((Number) entity.getProperties().get("warningDurationTicks")).longValue());
        assertEquals(Level02Corridor.RAY_ACTIVE_START_TICK,
                ((Number) entity.getProperties().get("activeStartTick")).longValue());
        assertEquals(Level02Corridor.RAY_ACTIVE_DURATION_TICKS,
                ((Number) entity.getProperties().get("activeDurationTicks")).longValue());
    }

    @Test
    void cycleParametersSatisfyReadmeSectionThree() {
        long warning = Level02Corridor.RAY_WARNING_DURATION_TICKS;
        long active = Level02Corridor.RAY_ACTIVE_DURATION_TICKS;

        // README §三：预警 1.0–1.4 s（60–84 刻）、激活 0.8–1.2 s（48–72 刻）
        assertTrue(warning >= 60 && warning <= 84, "预警时长须在 60–84 刻，实测 " + warning);
        assertTrue(active >= 48 && active <= 72, "激活时长须在 48–72 刻，实测 " + active);

        // README §三 公平性：预警 ≥ 反应预算(24–30) + 输入缓冲(6–10)
        assertTrue(warning >= 30 + 10, "预警必须覆盖最坏情况下的反应预算 + 输入缓冲");
        // README §三 相位公平性：激活窗口 ≥ 穿越 1 格(24 刻) + 成功余量(≥12 刻)
        assertTrue(active >= 24 + 12, "激活窗口必须容得下穿越 1 格并留余量");

        // 周期 = 预警起点 + 预警时长 + 激活时长 = 60 + 72 + 60 = 192
        assertEquals(192L, Level02Corridor.RAY_CYCLE_TICKS);
        assertEquals(Level02Corridor.RAY_WARNING_START_TICK
                        + Level02Corridor.RAY_WARNING_DURATION_TICKS
                        + Level02Corridor.RAY_ACTIVE_DURATION_TICKS,
                Level02Corridor.RAY_CYCLE_TICKS);
    }

    /**
     * 由共享 {@code roundTick} 驱动，周期 192 刻；相位边界在 60 / 132 / 192。
     *
     * <p>注意这与 L2-A 的旧数据不同：旧数据预警起点是 0，新地图的预警起点是 60，
     * 因此一轮的相位是 {@code [0,60) OFF → [60,132) WARNING → [132,192) ACTIVE}，
     * 刻 192 回到与刻 0 相同的相位（OFF），下一个周期的预警从刻 192 + 60 = 252 开始。</p>
     */
    @Test
    void cycleIsDrivenBySharedRoundTickOnly() {
        List<Ray> rays = RayFactory.buildFrom(Level02Corridor.build(), new EventDispatcher());
        Ray ray = rays.get(0);
        long cycle = Level02Corridor.RAY_CYCLE_TICKS;

        RayFactory.updateAll(rays, 0L);
        assertEquals(Ray.State.OFF, ray.getState(), "周期起点（刻 0）不是预警");

        RayFactory.updateAll(rays, Level02Corridor.RAY_WARNING_START_TICK - 1);
        assertEquals(Ray.State.OFF, ray.getState(), "预警起点前一刻仍是 OFF");

        RayFactory.updateAll(rays, Level02Corridor.RAY_WARNING_START_TICK);
        assertEquals(Ray.State.WARNING, ray.getState(), "刻 60 = 预警第一刻");

        RayFactory.updateAll(rays, Level02Corridor.RAY_ACTIVE_START_TICK - 1);
        assertEquals(Ray.State.WARNING, ray.getState(), "刻 131 = 预警最后一刻");

        RayFactory.updateAll(rays, Level02Corridor.RAY_ACTIVE_START_TICK);
        assertEquals(Ray.State.ACTIVE, ray.getState(), "刻 132 = 激活第一刻");

        RayFactory.updateAll(rays, cycle - 1);
        assertEquals(Ray.State.ACTIVE, ray.getState(), "刻 191 = 激活最后一刻");

        RayFactory.updateAll(rays, cycle);
        assertEquals(Ray.State.OFF, ray.getState(), "刻 192 回到周期起点相位（OFF）");

        RayFactory.updateAll(rays, cycle + Level02Corridor.RAY_WARNING_START_TICK);
        assertEquals(Ray.State.WARNING, ray.getState(), "下一周期刻 " + (cycle + 60) + " 重新预警");

        RayFactory.updateAll(rays, cycle + Level02Corridor.RAY_ACTIVE_START_TICK);
        assertEquals(Ray.State.ACTIVE, ray.getState(), "下一周期刻 " + (cycle + 132) + " 重新激活");
    }

    /** 判定线不得覆盖任何驻留板的驻留点（整个激活期逐刻判定）。 */
    @Test
    void rayDoesNotCoverAnyPlateDwellPoint() {
        LevelData level = Level02Corridor.build();
        Ray ray = RayFactory.buildFrom(level, new EventDispatcher()).get(0);

        List<EntitySpawnInfo> plates = level.getEntitySpawnList().stream()
                .filter(e -> "dock_plate".equals(e.getEntityType()))
                .toList();
        assertEquals(5, plates.size(), "L2 应有五块驻留板");

        double nearest = Double.MAX_VALUE;
        for (long tick = Level02Corridor.RAY_ACTIVE_START_TICK;
             tick < Level02Corridor.RAY_ACTIVE_START_TICK + Level02Corridor.RAY_ACTIVE_DURATION_TICKS;
             tick++) {
            RayFactory.updateAll(List.of(ray), tick);
            assertEquals(Ray.State.ACTIVE, ray.getState(), "刻 " + tick + " 应在激活期");
            for (EntitySpawnInfo plate : plates) {
                double distance = distanceToSegment(plate.getPos(), ray.getStart(), ray.getEnd());
                nearest = Math.min(nearest, distance);
                assertTrue(distance > TILE,
                        "板 " + plate.getId() + " 到射线距离必须 > 1 格，实测 " + distance);
                assertFalse(ray.containsPoint(plate.getPos(), Level02Corridor.RAY_HIT_WIDTH),
                        "射线判定不得覆盖驻留点: " + plate.getId());
            }
        }
        // 最近的驻留点是外闸板 P1 (3,5)：到线段上端点 (288,600) 的距离 = √(120² + 336²) = 356.79
        assertEquals(356.79, nearest, 0.01, "最近驻留点距离（设计说明 §五：356.79 ≫ 判定宽 9.6）");
        assertTrue(nearest > 10 * Level02Corridor.RAY_HIT_WIDTH, "余量必须远大于判定宽度");
    }

    @Test
    void hitIsPureGeometryInsideSegmentWithinWidth() {
        Ray ray = RayFactory.buildFrom(Level02Corridor.build(), new EventDispatcher()).get(0);
        RayFactory.updateAll(List.of(ray), Level02Corridor.RAY_ACTIVE_START_TICK);
        assertEquals(Ray.State.ACTIVE, ray.getState());

        double x = Level02Corridor.RAY_X;
        double width = Level02Corridor.RAY_HIT_WIDTH;
        assertEquals(0.20 * TILE, width, 1e-9);

        // 线段内、宽度内 → 命中（第 13 行中心 y = 648 是出生点所在行）。
        assertTrue(ray.containsPoint(new Vector2D(x, 13.5 * TILE), width), "南厅通路中心应命中");
        assertTrue(ray.containsPoint(new Vector2D(x + 0.5 * width, 13.5 * TILE), width),
                "半宽内应命中");
        assertTrue(RayFactory.hits(List.of(ray), new Vector2D(x, 13.5 * TILE), width),
                "激活期 RayFactory.hits 应命中");
        // 宽度外 → 不命中
        assertFalse(ray.containsPoint(new Vector2D(x + 2 * width, 13.5 * TILE), width),
                "超出判定宽度不得命中");
        // 线段端点之外 → 不命中
        assertFalse(ray.containsPoint(new Vector2D(x, ray.getEnd().y() + 2 * width), width),
                "线段之外不得命中");
        assertFalse(ray.containsPoint(new Vector2D(x, ray.getStart().y() - 2 * width), width),
                "线段起点之外不得命中");

        // 非激活态不做命中（预警期穿过不受影响）
        RayFactory.updateAll(List.of(ray), Level02Corridor.RAY_ACTIVE_START_TICK - 1);
        assertEquals(Ray.State.WARNING, ray.getState());
        assertFalse(RayFactory.hits(List.of(ray), new Vector2D(x, 13.5 * TILE), width),
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
        RayFactory.updateAll(first, Level02Corridor.RAY_ACTIVE_START_TICK);
        assertEquals(Ray.State.ACTIVE, first.get(0).getState());
        assertEquals(Ray.State.OFF, second.get(0).getState(), "第二条装配不受第一条驱动影响");
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
