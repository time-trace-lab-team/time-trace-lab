package org.example.timeloop.app;

import org.example.timeloop.core.Direction;
import org.example.timeloop.core.input.InputIntent;
import org.example.timeloop.core.input.LogicalKey;
import org.example.timeloop.mechanism.DockingPlateRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BUG-002-LIFECYCLE Phase 1 · app 侧生命周期验收（PM）。
 *
 * <p>裁决 §七 要求：① 同一 JVM 连续构造装配不再依赖全局清理；② 两个装配同时存活互不干扰；
 * ③ app 集成测试不再手工清理全局单例（四个集成测试类已移除 {@code @AfterEach} 全局清理）。</p>
 *
 * <p>注意：第一轮的逻辑时钟在**首个方向输入**出现后才开始推进（BUG-001 修复引入的门槛），
 * 因此本类所有推进都从一次真实方向输入开始。</p>
 */
class Level01AssemblyLifecycleTest {

    @Test
    void repeatedConstructionNoLongerNeedsGlobalCleanup() {
        Level01Assembly first = new Level01Assembly();
        Level01Assembly second = new Level01Assembly();
        Level01Assembly third = new Level01Assembly();

        first.start();
        second.start();
        third.start();
        drive(first, 0, LogicalKey.DIR_DOWN, 60);
        drive(second, 0, LogicalKey.DIR_DOWN, 60);
        drive(third, 0, LogicalKey.DIR_DOWN, 60);

        assertTrue(first.isPlaying());
        assertTrue(second.isPlaying());
        assertTrue(third.isPlaying());
        assertTrue(first.hudContext().roundTick() > 0, "时钟已随首个方向输入开始推进");
        assertTrue(second.hudContext().roundTick() > 0);
        assertTrue(third.hudContext().roundTick() > 0);

        first.cleanup();
        second.cleanup();
        third.cleanup();
    }

    @Test
    void twoAssembliesDoNotSharePlateOccupancy() {
        Level01Assembly a = new Level01Assembly();
        Level01Assembly b = new Level01Assembly();
        a.start();
        b.start();
        drive(a, 0, LogicalKey.DIR_DOWN, 20);
        drive(b, 0, LogicalKey.DIR_DOWN, 20);

        // 直接占用 A 的左驻留板（与地图几何无关，专测“注册表实例是否独立”）
        assertTrue(a.dockingPlate("L01_plate_left").orElseThrow().tryEnter("echo_1", 1, 5));

        assertTrue(a.isPlateOccupied("L01_plate_left"), "A 自己的左板应被占用");
        assertFalse(a.isPlateOccupied("L01_plate_right"));
        assertFalse(b.isPlateOccupied("L01_plate_left"), "B 不受 A 的占用影响");
        assertFalse(b.isPlateOccupied("L01_plate_right"));

        // app 不应再写入全局注册表单例（两套装配都不写）
        assertFalse(DockingPlateRegistry.getInstance().isOccupied("L01_plate_left"));
        assertFalse(DockingPlateRegistry.getInstance().isOccupied("L01_plate_right"));

        // B 独立推进：它的时钟自己在走，不被 A 影响
        long bTickBefore = b.hudContext().roundTick();
        drive(b, 0, LogicalKey.DIR_DOWN, 30);
        assertTrue(b.hudContext().roundTick() > bTickBefore, "B 的时钟独立推进");

        a.cleanup();
        b.cleanup();
    }

    @Test
    void appDoesNotTouchGlobalEventDispatcher() {
        Level01Assembly a = new Level01Assembly();
        a.start();
        drive(a, 0, LogicalKey.DIR_DOWN, 40);

        assertFalse(DockingPlateRegistry.getInstance().isOccupied("L01_plate_left"));
        assertFalse(DockingPlateRegistry.getInstance().isOccupied("L01_plate_right"));
        a.cleanup();
    }

    // ---------- 工具 ----------

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
