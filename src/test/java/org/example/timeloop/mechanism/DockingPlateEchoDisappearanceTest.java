package org.example.timeloop.mechanism;

import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.example.timeloop.mechanism.event.GameEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * X-MOVE-COLLAPSE-01 v2 §B · 残影 actor 归属的机制侧确认（E-2）。
 *
 * <p>冻结约定（见 `开发三-稳定ID与排序-autoDock规格冻结.md` §2.1 第 5 条）：
 * 残影身份是 {@code echo_<sourceRound>}，<b>{@code sourceRound} 从 1 起算</b>；
 * {@link DockingPlate#onEvent} 只在占用者等于 {@code "echo_" + event.sourceRound()} 时释放占用。</p>
 *
 * <p>BUG-002-LIFECYCLE Phase 1 起，本测试为每个用例创建**独立的注册表与事件总线**，
 * 不再依赖也不再手工清理全局单例。</p>
 */
class DockingPlateEchoDisappearanceTest {

    private DockingPlateRegistry registry;
    private EventDispatcher bus;

    @BeforeEach
    void freshMechanismScope() {
        registry = new DockingPlateRegistry();
        bus = new EventDispatcher();
    }

    private DockingPlate leftPlate() {
        return new DockingPlate("L01_plate_left", new Vector2D(120.0, 264.0), registry, bus);
    }

    @Test
    void echoOccupantIsReleasedWhenItsEchoDisappears() {
        DockingPlate plate = leftPlate();
        assertTrue(plate.tryEnter("echo_1", 1, 10), "残影 echo_1 应能占板");
        assertTrue(plate.isOccupied());
        assertEquals("echo_1", plate.getOccupantId());

        bus.dispatch(GameEvent.echoDisappeared("echo_1", 11, 1));

        assertFalse(plate.isOccupied(), "残影消散必须释放它占用的驻留板");
        assertNull(plate.getOccupantId());
        assertEquals(0, plate.getOccupantSourceRound());
    }

    @Test
    void mismatchedEchoDisappearanceDoesNotRelease() {
        DockingPlate plate = leftPlate();

        // ① 占用者是另一个残影：ECHO_DISAPPEARED 的 sourceRound 与占用者不匹配
        assertTrue(plate.tryEnter("echo_2", 2, 10));
        bus.dispatch(GameEvent.echoDisappeared("echo_2", 11, 1));
        assertTrue(plate.isOccupied(), "sourceRound 与占用者不匹配时不得误释放");
        assertEquals("echo_2", plate.getOccupantId());
        assertTrue(plate.tryExit("echo_2", 2, 12), "占用者本人仍可正常离开");

        // ② 占用者是当前玩家：残影消散事件不得释放玩家的占用
        assertTrue(plate.tryEnter("player", 0, 20));
        bus.dispatch(GameEvent.echoDisappeared("player", 21, 1));
        assertTrue(plate.isOccupied(), "当前玩家的占用不得被 ECHO_DISAPPEARED 释放");
        assertEquals("player", plate.getOccupantId());
    }
}
