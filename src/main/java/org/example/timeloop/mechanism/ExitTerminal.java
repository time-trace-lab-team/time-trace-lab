package org.example.timeloop.mechanism;

import org.example.timeloop.level.StableIdValidator;
import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.example.timeloop.mechanism.event.GameEvent;
import org.example.timeloop.mechanism.event.GameEventBus;
import org.example.timeloop.mechanism.event.GameObserver;

import java.util.Objects;

public class ExitTerminal implements GameObserver {

    /** 交互半径的默认格数倍数（1.5 × tileSize）。 */
    public static final double INTERACT_RADIUS_TILES = 1.5;

    /**
     * 兼容下限半径：1 × tileSize（第一关 tileSize = 48）。关卡应优先按
     * {@code 1.5 × tileSize} 显式传入（第一关 = 72，见 {@link #interactRadiusForTileSize(double)}）；
     * 本常量只保证「半径不小于 1 格」这一硬下限。
     *
     * <p>硬下限的由来（L01-GATE-MERGE 后按实际数据订正）：第一关关卡的开关在 (18, 8)、
     * 闸门终点在 (18, 7)，两者相距恰好 1 格；半径小于 1 格会迫使玩家离开开关才能按 E，
     * 而离开即释放占用、门重新上锁 → 关卡无解。旧注释引用的 (8,5)/(9,5) 是地图改造前的坐标，已失效。</p>
     */
    public static final double DEFAULT_INTERACT_RADIUS = 48.0;

    private final String id;
    private final Vector2D position;
    private final String associatedDoorId;
    private final double interactRadius;
    private final GameEventBus bus;
    private boolean doorUnlocked = false;
    private boolean triggered = false;

    /** 兼容构造器：半径取 {@link #DEFAULT_INTERACT_RADIUS}（1 × tileSize），总线取兼容单例。 */
    public ExitTerminal(String id, Vector2D position, String associatedDoorId) {
        this(id, position, associatedDoorId, DEFAULT_INTERACT_RADIUS, EventDispatcher.getInstance());
    }

    /**
     * @param interactRadius 宽容交互半径（世界单位）；关卡应按 {@code 1.5 × tileSize} 传入
     *                       （第一关 = 72，见 {@link #interactRadiusForTileSize(double)}）
     */
    public ExitTerminal(String id, Vector2D position, String associatedDoorId, double interactRadius) {
        this(id, position, associatedDoorId, interactRadius, EventDispatcher.getInstance());
    }

    /** 完全注入（推荐，BUG-002-LIFECYCLE Phase 1）：事件总线由关卡装配持有。 */
    public ExitTerminal(String id,
                        Vector2D position,
                        String associatedDoorId,
                        double interactRadius,
                        GameEventBus bus) {
        this.id = StableIdValidator.requireMechanismId(id, "exit", "exitTerminal.id");
        this.position = Objects.requireNonNull(position);
        this.associatedDoorId = StableIdValidator.requireMechanismId(
                associatedDoorId, "door", "exitTerminal.associatedDoorId");
        if (!Double.isFinite(interactRadius) || interactRadius <= 0.0) {
            throw new IllegalArgumentException(
                    "exitTerminal.interactRadius 必须为有限正数: " + interactRadius);
        }
        this.interactRadius = interactRadius;
        this.bus = Objects.requireNonNull(bus, "bus");
        bus.register(GameEvent.DOOR_UNLOCKED, this);
    }

    /** 按关卡 {@code tileSize} 计算推荐交互半径（{@link #INTERACT_RADIUS_TILES} × tileSize；第一关 = 72）。 */
    public static double interactRadiusForTileSize(double tileSize) {
        if (!Double.isFinite(tileSize) || tileSize <= 0.0) {
            throw new IllegalArgumentException("tileSize 必须为有限正数: " + tileSize);
        }
        return INTERACT_RADIUS_TILES * tileSize;
    }

    public String getId() { return id; }
    public Vector2D getPosition() { return position; }
    public boolean isDoorUnlocked() { return doorUnlocked; }
    public boolean isTriggered() { return triggered; }
    public double getInteractRadius() { return interactRadius; }

    /**
     * 纯判定：世界坐标到终端位置的距离是否落在宽容交互半径内（闭区间）。
     *
     * <p>只读、无副作用、不引入任何计时器，也不接收 actor 参数（残影是否可结算由调用方决定）。
     * 是否真正结算仍由 {@link #interact(long, int)} 的门权限与未触发状态决定。</p>
     */
    public boolean isInInteractRange(Vector2D worldPosition) {
        Objects.requireNonNull(worldPosition, "worldPosition");
        double dx = worldPosition.x() - position.x();
        double dy = worldPosition.y() - position.y();
        return Math.sqrt(dx * dx + dy * dy) <= interactRadius;
    }

    public boolean interact(long tick, int sourceRound) {
        if (!doorUnlocked || triggered) return false;
        triggered = true;
        bus.dispatch(GameEvent.exitTriggered(id, tick, sourceRound));
        return true;
    }

    public void reset() {
        doorUnlocked = false;
        triggered = false;
    }

    public void dispose() {
        bus.unregisterAll(this);
    }

    @Override
    public void onEvent(GameEvent event) {
        if (event.isType(GameEvent.DOOR_UNLOCKED) && associatedDoorId.equals(event.sourceId())) {
            doorUnlocked = true;
        }
    }

    @Override
    public String toString() {
        return String.format("ExitTerminal{id='%s', doorUnlocked=%s, triggered=%s}", id, doorUnlocked, triggered);
    }

    // ========== Snapshot 接口 ==========

    public interface Snapshot {
        /** 快照所属的稳定机制 ID。 */
        default String getMechanismId() { return null; }

        boolean isDoorUnlocked();
        boolean isTriggered();
    }

    /** 不可变的出口终端状态快照。 */
    public record StateSnapshot(String mechanismId,
                                boolean doorUnlocked,
                                boolean triggered) implements Snapshot {

        public StateSnapshot {
            mechanismId = StableIdValidator.requireMechanismId(
                    mechanismId, "exit", "exitTerminal.snapshot.mechanismId");
            validateState(doorUnlocked, triggered);
        }

        @Override
        public String getMechanismId() { return mechanismId; }

        @Override
        public boolean isDoorUnlocked() { return doorUnlocked; }

        @Override
        public boolean isTriggered() { return triggered; }
    }

    public Snapshot createSnapshot() {
        return new StateSnapshot(id, doorUnlocked, triggered);
    }

    public void restore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "exitTerminal.snapshot");
        String snapshotId = snapshot.getMechanismId();
        if (!id.equals(snapshotId)) {
            throw new IllegalArgumentException(
                    "出口终端快照 ID 不匹配: expected=" + id + ", actual=" + snapshotId);
        }
        boolean restoredDoorUnlocked = snapshot.isDoorUnlocked();
        boolean restoredTriggered = snapshot.isTriggered();
        validateState(restoredDoorUnlocked, restoredTriggered);
        this.doorUnlocked = restoredDoorUnlocked;
        this.triggered = restoredTriggered;
    }

    private static void validateState(boolean doorUnlocked, boolean triggered) {
        if (triggered && !doorUnlocked) {
            throw new IllegalArgumentException("已触发出口终端的门必须已解锁");
        }
    }



}
