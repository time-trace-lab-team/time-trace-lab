package org.example.timeloop.app;

import org.example.timeloop.core.Direction;
import org.example.timeloop.core.input.InputIntent;
import org.example.timeloop.core.input.LogicalKey;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

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

        // B 独立推进：它的时钟自己在走，不被 A 影响
        long bTickBefore = b.hudContext().roundTick();
        drive(b, 0, LogicalKey.DIR_DOWN, 30);
        assertTrue(b.hudContext().roundTick() > bTickBefore, "B 的时钟独立推进");

        a.cleanup();
        b.cleanup();
    }

    /**
     * app 层不得引用任何全局单例 —— BUG-002-LIFECYCLE 的架构不变量。
     *
     * <p><b>为什么改成静态扫描</b>（PM 复核，2026-09-14）：本用例原先把
     * {@code DockingPlateRegistry.getInstance().isOccupied(...)} 当断言，Phase 2 删除单例后无法编译；
     * 中间一版改成「B 的 {@code drainEvents()} 为空」，但那是**永远通过的假绿**——
     * {@code Level01Assembly.events} 只承载**本装配玩家自己的驻留决策**
     * （`events.addAll(decision.events())`），B 不驱动就恒为空，无论两条装配是否共享总线。
     * 「两套装配互不串扰」这条运行时性质已由 {@link #twoAssembliesDoNotSharePlateOccupancy()} 真正覆盖。
     * 因此这里回到原本的意图（app 不碰全局状态），改用**能失败**的形式：直接扫描 app 源码。</p>
     */
    @Test
    void appSourcesDoNotReferenceGlobalSingletons() throws IOException {
        Path appSources = Path.of("src", "main", "java", "org", "example", "timeloop", "app");
        assertTrue(Files.isDirectory(appSources),
                "找不到 app 源码目录（测试工作目录应为项目根）：" + appSources.toAbsolutePath());

        List<String> offenders;
        try (Stream<Path> files = Files.walk(appSources)) {
            offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            return Files.readString(path).contains("getInstance");
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .map(Path::toString)
                    .sorted()
                    .toList();
        }

        assertTrue(offenders.isEmpty(),
                "app/** 不得引用全局单例 getInstance()：" + offenders);
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
