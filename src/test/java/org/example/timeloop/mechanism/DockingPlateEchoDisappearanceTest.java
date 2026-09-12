package org.example.timeloop.mechanism;

import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.example.timeloop.mechanism.event.GameEvent;
import org.junit.jupiter.api.AfterEach;
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
 * <p>本测试只覆盖机制侧行为，不改任何生产代码：
 * ① 残影占板后收到匹配的 {@code ECHO_DISAPPEARED} → 必须释放；
 * ② {@code sourceRound} 与占用者不匹配（另一残影 / 当前玩家）→ 不得误释放。</p>
 *
 * <p>回放侧必须以 {@code echo_<sourceRound>} 身份提交边沿；沿用录制时的 {@code player}/{@code 0}
 * 会导致残影消散后驻留板永久占用（由 {@code X-MOVE-COLLAPSE-01-ECHO-ACTOR} 跟踪）。</p>
 */
class DockingPlateEchoDisappearanceTest {

    @BeforeEach
    @AfterEach
    void clearGlobalMechanismState() {
        EventDispatcher.getInstance().clear();
        DockingPlateRegistry.getInstance().clear();
    }

    @Test
    void echoOccupantIsReleasedWhenItsEchoDisappears() {
        DockingPlate plate = new DockingPlate("L01_plate_left", new Vector2D(120.0, 264.0));
        assertTrue(plate.tryEnter("echo_1", 1, 10), "残影 echo_1 应能占板");
        assertTrue(plate.isOccupied());
        assertEquals("echo_1", plate.getOccupantId());

        EventDispatcher.getInstance().dispatch(GameEvent.echoDisappeared("echo_1", 11, 1));

        assertFalse(plate.isOccupied(), "残影消散必须释放它占用的驻留板");
        assertNull(plate.getOccupantId());
        assertEquals(0, plate.getOccupantSourceRound());
    }

    @Test
    void mismatchedEchoDisappearanceDoesNotRelease() {
        DockingPlate plate = new DockingPlate("L01_plate_left", new Vector2D(120.0, 264.0));

        // ① 占用者是另一个残影：ECHO_DISAPPEARED 的 sourceRound 与占用者不匹配
        assertTrue(plate.tryEnter("echo_2", 2, 10));
        EventDispatcher.getInstance().dispatch(GameEvent.echoDisappeared("echo_2", 11, 1));
        assertTrue(plate.isOccupied(), "sourceRound 与占用者不匹配时不得误释放");
        assertEquals("echo_2", plate.getOccupantId());
        assertTrue(plate.tryExit("echo_2", 2, 12), "占用者本人仍可正常离开");

        // ② 占用者是当前玩家：残影消散事件不得释放玩家的占用
        assertTrue(plate.tryEnter("player", 0, 20));
        EventDispatcher.getInstance().dispatch(GameEvent.echoDisappeared("player", 21, 1));
        assertTrue(plate.isOccupied(), "当前玩家的占用不得被 ECHO_DISAPPEARED 释放");
        assertEquals("player", plate.getOccupantId());
    }
}
