package org.example.timeloop.mechanism;

import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BUG-002-LIFECYCLE Phase 1 · 注册表与事件总线的实例隔离（PM 裁决 §七 验收 2 的机制侧）。
 *
 * <p>要点：同一 JVM 内可以并存多个"机制作用域"（各自一个 {@link DockingPlateRegistry} 与
 * 一个 {@link EventDispatcher}），彼此的板占用与事件互不可见；同一作用域内的重复 ID 仍须拒绝。
 * 本测试不触碰全局单例，因此无需任何 {@code clear()}。</p>
 */
class DockingPlateRegistryIsolationTest {

    @Test
    void twoScopesCoexistWithoutDuplicateIdConflict() {
        DockingPlateRegistry firstScope = new DockingPlateRegistry();
        DockingPlateRegistry secondScope = new DockingPlateRegistry();
        EventDispatcher firstBus = new EventDispatcher();
        EventDispatcher secondBus = new EventDispatcher();

        // 同一个板 ID 在两个作用域里各注册一次：历史缺陷在此抛"重复的驻留板 ID"
        DockingPlate first = new DockingPlate("L01_plate_left", new Vector2D(120.0, 264.0),
                firstScope, firstBus);
        DockingPlate second = new DockingPlate("L01_plate_left", new Vector2D(120.0, 264.0),
                secondScope, secondBus);

        assertTrue(firstScope.isOccupied("L01_plate_left") == false);
        assertEquals(first, firstScope.get("L01_plate_left"));
        assertEquals(second, secondScope.get("L01_plate_left"));
    }

    @Test
    void occupancyAndDoorConditionDoNotLeakAcrossScopes() {
        DockingPlateRegistry scopeA = new DockingPlateRegistry();
        DockingPlateRegistry scopeB = new DockingPlateRegistry();
        EventDispatcher busA = new EventDispatcher();
        EventDispatcher busB = new EventDispatcher();

        DockingPlate plateA = new DockingPlate("L01_plate_left", new Vector2D(120.0, 264.0),
                scopeA, busA);
        DockingPlate plateB = new DockingPlate("L01_plate_left", new Vector2D(120.0, 264.0),
                scopeB, busB);
        DockingPlate plateRightB = new DockingPlate("L01_plate_right", new Vector2D(408.0, 264.0),
                scopeB, busB);
        Door doorA = new Door("L01_door_01", new Vector2D(264.0, 264.0),
                Set.of("L01_plate_left"), scopeA, busA);

        // A 作用域：只占 A 的板 → A 的门解锁
        assertTrue(plateA.tryEnter("player", 0, 10));
        assertTrue(doorA.isUnlocked(), "A 的门应看到 A 的板占用");

        // B 作用域完全不受影响
        assertFalse(scopeB.isOccupied("L01_plate_left"),
                "A 的占用不得泄漏到 B 的注册表");
        assertFalse(plateB.isOccupied());

        // B 作用域：占满两块板也不会改变 A 的门
        assertTrue(plateB.tryEnter("echo_1", 1, 10));
        assertTrue(plateRightB.tryEnter("player", 0, 10));
        assertTrue(scopeB.isOccupied("L01_plate_left"));
        assertTrue(doorA.isUnlocked(), "A 的门状态不受 B 的占用变化影响");

        // A 释放后其门重新上锁，B 仍保持占用
        assertTrue(plateA.tryExit("player", 0, 11));
        assertFalse(doorA.isUnlocked(), "A 的板释放后 A 的门应回到 LOCKED");
        assertTrue(plateB.isOccupied(), "B 的占用不受 A 的释放影响");
    }

    @Test
    void duplicateIdIsStillRejectedWithinTheSameScope() {
        DockingPlateRegistry scope = new DockingPlateRegistry();
        EventDispatcher bus = new EventDispatcher();

        new DockingPlate("L01_plate_left", new Vector2D(120.0, 264.0), scope, bus);

        assertThrows(IllegalArgumentException.class,
                () -> new DockingPlate("L01_plate_left", new Vector2D(168.0, 264.0), scope, bus));
        assertEquals(new Vector2D(120.0, 264.0), scope.get("L01_plate_left").getPosition());
    }

    @Test
    void disposingOneScopeLeavesTheOtherIntact() {
        DockingPlateRegistry scopeA = new DockingPlateRegistry();
        DockingPlateRegistry scopeB = new DockingPlateRegistry();
        EventDispatcher busA = new EventDispatcher();
        EventDispatcher busB = new EventDispatcher();

        DockingPlate plateA = new DockingPlate("L01_plate_left", new Vector2D(120.0, 264.0),
                scopeA, busA);
        DockingPlate plateB = new DockingPlate("L01_plate_left", new Vector2D(120.0, 264.0),
                scopeB, busB);

        plateA.dispose();

        assertFalse(scopeA.isOccupied("L01_plate_left"));
        assertEquals(null, scopeA.get("L01_plate_left"), "A 释放后 A 的注册表不再持有该板");
        assertEquals(plateB, scopeB.get("L01_plate_left"), "B 的注册表不受 A 的 dispose 影响");
        assertTrue(plateB.tryEnter("player", 0, 12), "B 的板仍可正常使用");
    }
}
