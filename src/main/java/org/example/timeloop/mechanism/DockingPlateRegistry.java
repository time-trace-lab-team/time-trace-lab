package org.example.timeloop.mechanism;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 驻留板注册表（BUG-002-LIFECYCLE）。
 *
 * <p>实现 {@link DockingPlateOccupancyPort} 窄端口，供 {@link Door} 等消费者注入使用。
 * {@link #getInstance()} 是<b>兼容层</b>：Phase 2 将在 app 与测试全部迁移后删除全局单例；
 * 新代码一律通过 {@link #DockingPlateRegistry()} 构造独立实例，由关卡装配持有。</p>
 */
public final class DockingPlateRegistry implements DockingPlateOccupancyPort {

    private static final DockingPlateRegistry INSTANCE = new DockingPlateRegistry();
    private final Map<String, DockingPlate> plates = new ConcurrentHashMap<>();

    /**
     * 兼容层单例（Phase 2 删除）。新代码请用 {@link #DockingPlateRegistry()}，
     * 让实例随关卡装配创建与销毁。
     */
    public static DockingPlateRegistry getInstance() {
        return INSTANCE;
    }

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
