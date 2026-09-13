package org.example.timeloop.mechanism;

/**
 * 驻留板占用查询的窄端口（BUG-002-LIFECYCLE Phase 1）。
 *
 * <p>消费者（{@link Door} 等）只依赖它需要的三个能力，而不依赖 {@link DockingPlateRegistry}
 * 具体类，更不依赖全局单例。关卡装配应持有一个注册表实例并注入本端口。</p>
 *
 * <p>约定期限：Phase 2 在 app 与测试全部迁移到注入后删除单例，本端口的实现将成为唯一入口。</p>
 */
public interface DockingPlateOccupancyPort {

    /** 注册一块驻留板；同一实例内重复 ID 必须抛出异常。 */
    void register(DockingPlate plate);

    /** 反注册驻留板。 */
    void unregister(String plateId);

    /** 查询该驻留板当前是否被占用（不区分占用者身份，见规格冻结 §5.1 第 6 条）。 */
    boolean isOccupied(String plateId);
}
