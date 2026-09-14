package org.example.timeloop.mechanism.ray;

import org.example.timeloop.level.model.EntitySpawnInfo;
import org.example.timeloop.level.model.LevelData;
import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.event.GameEventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 射线装配与驱动（L2-B，卡 `L02-B-DEV3` §三）。
 *
 * <p>把关卡数据里 {@code entityType = "ray"} 的实体接成 {@link Ray}：</p>
 * <ul>
 *   <li><b>注入优先</b>：构造与注册都使用调用方传入的 {@link GameEventBus}，
 *       <b>不得</b>使用任何兼容单例（BUG-002 Phase 2 已删除全局单例）；</li>
 *   <li><b>共享时钟</b>：只由 {@link #updateAll(List, long)} 用共享 {@code roundTick} 驱动，
 *       不自建计时器（暂停、教程、掉帧都不独立推进）；</li>
 *   <li><b>无快照端口</b>：射线没有持久状态（行为完全由 {@code roundTick} 决定），
 *       因此不提供 {@code Snapshot}，聚合器也不得为它加具体类旁路（R5-B §10.2 结论保留）。</li>
 * </ul>
 */
public final class RayFactory {

    private RayFactory() {}

    /** 关卡数据里射线实体的 {@code entityType}。 */
    public static final String ENTITY_TYPE = "ray";

    /**
     * 从关卡数据装配全部射线。
     *
     * @param level 关卡数据（读取 {@code ray} 实体的端点与周期参数）
     * @param bus   关卡装配持有的事件总线（注入；不得为全局单例）
     */
    public static List<Ray> buildFrom(LevelData level, GameEventBus bus) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(bus, "bus");

        List<Ray> rays = new ArrayList<>();
        for (EntitySpawnInfo entity : level.getEntitySpawnList()) {
            if (!ENTITY_TYPE.equals(entity.getEntityType())) {
                continue;
            }
            rays.add(new Ray(
                    entity.getId(),
                    entity.getPos(),
                    new Vector2D(
                            requireDouble(entity, "endX"),
                            requireDouble(entity, "endY")),
                    requireLong(entity, "warningStartTick"),
                    requireLong(entity, "warningDurationTicks"),
                    requireLong(entity, "activeStartTick"),
                    requireLong(entity, "activeDurationTicks"),
                    bus));
        }
        return List.copyOf(rays);
    }

    /** 用共享 {@code roundTick} 驱动全部射线（由游戏循环每 tick 调用一次）。 */
    public static void updateAll(List<Ray> rays, long roundTick) {
        for (Ray ray : rays) {
            ray.update(roundTick);
        }
    }

    /** 释放全部射线的事件注册（场景退出 / 整局重开）。 */
    public static void disposeAll(List<Ray> rays) {
        for (Ray ray : rays) {
            ray.dispose();
        }
    }

    /**
     * 当前这一 tick 是否有射线命中该点。
     *
     * <p>只做「是否在激活线段判定宽度内」的几何判定；<b>减速结算与相位豁免属移动层</b>
     * （开发一 `core/**`、`entity/**` + PM 的 app 接线），本类不写任何减速状态。</p>
     */
    public static boolean hits(List<Ray> rays, Vector2D point, double width) {
        for (Ray ray : rays) {
            if (ray.getState() == Ray.State.ACTIVE && ray.containsPoint(point, width)) {
                return true;
            }
        }
        return false;
    }

    private static double requireDouble(EntitySpawnInfo entity, String key) {
        Object value = entity.getProperties().get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(
                    "射线实体缺少数值属性 " + key + ": id=" + entity.getId());
        }
        return number.doubleValue();
    }

    private static long requireLong(EntitySpawnInfo entity, String key) {
        Object value = entity.getProperties().get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(
                    "射线实体缺少数值属性 " + key + ": id=" + entity.getId());
        }
        return number.longValue();
    }
}
