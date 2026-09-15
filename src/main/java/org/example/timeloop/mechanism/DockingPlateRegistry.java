package org.example.timeloop.mechanism;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 驻留板注册表（BUG-002-LIFECYCLE）。
 *
 * <p>实现 {@link DockingPlateOccupancyPort} 窄端口，供 {@link Door} 等消费者注入使用。
 * 权威形态是「每个关卡装配持有自己的实例」：同一实例内的驻留板 ID 必须唯一，跨实例同名 ID 互不冲突；
 * 场景退出 / 整局重开时随装配一起释放，不再依赖任何全局清理。</p>
 *
 * <p><b>全局单例已在 BUG-002-LIFECYCLE Phase 2 删除</b>（静态单例访问器与全部兼容构造器均已移除）：
 * 新代码一律通过 {@link #DockingPlateRegistry()} 构造独立实例。</p>
 */
public final class DockingPlateRegistry implements DockingPlateOccupancyPort {

    private final Map<String, DockingPlate> plates = new ConcurrentHashMap<>();

    /** 每个关卡装配应持有自己的实例；同一实例内的板 ID 必须唯一。 */
    public DockingPlateRegistry() {}

    @Override
    public void register(DockingPlate plate) {
        DockingPlate existing = plates.putIfAbsent(plate.getId(), plate);
        if (existing != null) {
            throw new IllegalArgumentException("重复的驻留板 ID: " + plate.getId());
        }
    }

    @Override
    public void unregister(String id) {
        plates.remove(id);
    }

    @Override
    public boolean isOccupied(String id) {
        DockingPlate plate = plates.get(id);
        return plate != null && plate.isOccupied();
    }

    public DockingPlate get(String id) {
        return plates.get(id);
    }

    public void resetAll() {
        for (DockingPlate plate : plates.values()) {
            plate.reset();
        }
    }

    public void clear() {
        plates.clear();
    }
}
