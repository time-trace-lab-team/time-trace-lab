package org.example.timeloop.replay;

import org.example.timeloop.core.ActorPhase;
import org.example.timeloop.core.AnimationState;
import org.example.timeloop.core.Direction;
import org.example.timeloop.core.MovementState;
import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.DockingPlate;
import org.example.timeloop.mechanism.DockingPlateRegistry;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.example.timeloop.mechanism.event.GameEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L02-A-DEV2 §二.1 / §二.2：第二关「双残影首次真跑」的回放正确性。
 *
 * <p>L=2 意味着同一轮里 <b>两条残影（E1、E2）同时回放</b>、同刻各自产生机关事件。
 * 本类锁定三件事：</p>
 * <ol>
 *   <li><b>双残影同刻回放确定性</b>：同一 roundTick 上两条残影各自的事件都生效、互不覆盖，
 *       合并顺序稳定（不依赖集合迭代顺序，重复运行结果一致）；</li>
 *   <li><b>同刻争抢胜者规则</b>：两条残影在同一刻争抢同一块板时，<b>较旧残影（sourceRound 小）
 *       优先</b>——应用顺序由 {@link EchoQueue#activeEchoes} 的来源轮次升序保证；</li>
 *   <li><b>淘汰生命周期</b>：L=2、maxRounds=4 下，E1 在第 3→4 轮边界被寿命淘汰，
 *       淘汰派发 {@code ECHO_DISAPPEARED} 后释放其占用的板，但<b>不清锁存</b>。</li>
 * </ol>
 *
 * <p>replay 层不持有机关状态：本类中「释放板 / 锁存」部分用真实 {@link DockingPlate}
 * 模拟装配层的应用与派发，验证 replay 暴露的淘汰信息足以驱动正确的机关释放。</p>
 */
class DualEchoReplayTest {

    private static final int D = 60;
    private static final long COMPETE_TICK = 40;

    /** 构造一条满长记录，位置随 sourceRound 平移（便于断言轨迹独立）。 */
    private static EchoState echoOfRound(int sourceRound) {
        TimelineRecording r = new TimelineRecording(D, sourceRound);
        for (int i = 0; i < D; i++) {
            r.record(new PlayerFrame(i, sourceRound * 1000.0 + i, 0, Direction.RIGHT, false,
                    MovementState.CRUISING, ActorPhase.AVAILABLE, 0, AnimationState.MOVING));
        }
        r.seal();
        return EchoState.of(r);
    }

    private static TimelineEvent entered(long tick, int sourceRound, String mechanismId) {
        return new TimelineEvent(tick, "player", sourceRound, mechanismId,
                TimelineEvent.EventType.DOCK_ENTERED, null, null);
    }

    /** 一条满长记录 + 若干事件，封装成残影。 */
    private static EchoState echoWithEvents(int sourceRound, List<TimelineEvent> events) {
        TimelineRecording r = new TimelineRecording(D, sourceRound);
        for (int i = 0; i < D; i++) {
            r.record(new PlayerFrame(i, sourceRound * 1000.0 + i, 0, Direction.RIGHT, false,
                    MovementState.CRUISING, ActorPhase.AVAILABLE, 0, AnimationState.MOVING));
        }
        for (TimelineEvent e : events) {
            r.recordEvent(e);
        }
        r.seal();
        return EchoState.of(r);
    }

    /** 模拟装配层：按 activeEchoes 的来源轮次升序，收集当前轮同刻所有残影事件。 */
    private static List<TimelineEvent> mergeSameTickEvents(EchoQueue queue, int currentRound, long tick) {
        List<TimelineEvent> merged = new ArrayList<>();
        for (EchoState echo : queue.activeEchoes(currentRound)) {
            merged.addAll(echo.eventsAt(tick));
        }
        return merged;
    }

    @Test
    void twoEchoesReplayTheSameTickIntoDifferentPlatesDeterministically() {
        EchoQueue queue = new EchoQueue(2);
        EchoState e1 = echoWithEvents(1, List.of(entered(COMPETE_TICK, 1, "L02_plate_door")));
        EchoState e2 = echoWithEvents(2, List.of(entered(COMPETE_TICK, 2, "L02_plate_inner")));
        queue.addOnRoundEnd(e1, 2);
        queue.addOnRoundEnd(e2, 3);

        // 同脚本重复 3 次，合并结果必须逐字段一致（非确定性即失败）。
        List<TimelineEvent> first = mergeSameTickEvents(queue, 3, COMPETE_TICK);
        List<TimelineEvent> second = mergeSameTickEvents(queue, 3, COMPETE_TICK);
        List<TimelineEvent> third = mergeSameTickEvents(queue, 3, COMPETE_TICK);

        assertEquals(2, first.size(), "两个残影各自的事件都必须生效");
        assertEquals("L02_plate_door", first.get(0).mechanismId(), "较旧残影 E1 的事件在前");
        assertEquals("L02_plate_inner", first.get(1).mechanismId(), "较新残影 E2 的事件在后");
        assertEquals(first, second, "第 2 次结果必须与第 1 次一致");
        assertEquals(first, third, "第 3 次结果必须与第 1 次一致");
    }

    @Test
    void twoEchoesCompetingForOnePlateHaveDeterministicWinner() {
        EchoQueue queue = new EchoQueue(2);
        EchoState e1 = echoWithEvents(1, List.of(entered(COMPETE_TICK, 1, "L02_plate_door")));
        EchoState e2 = echoWithEvents(2, List.of(entered(COMPETE_TICK, 2, "L02_plate_door")));
        queue.addOnRoundEnd(e1, 2);
        queue.addOnRoundEnd(e2, 3);

        // 合并顺序：E1（sourceRound=1）恒在 E2（sourceRound=2）之前。
        List<TimelineEvent> first = mergeSameTickEvents(queue, 3, COMPETE_TICK);
        List<TimelineEvent> second = mergeSameTickEvents(queue, 3, COMPETE_TICK);
        assertEquals(2, first.size());
        assertEquals(1, first.get(0).sourceRound(), "胜者 = 较旧残影（sourceRound 小）先应用");
        assertEquals(2, first.get(1).sourceRound());
        assertEquals(first, second, "争抢顺序必须确定");

        // 用真实机关验证：echo_1 先占（胜），echo_2 后到（败），不覆盖前者。
        // L03-DEV3 后驻留板支持多占用：echo_2 照样被登记（不再被静默丢弃），
        // 「不覆盖」现在体现在**主占用者仍然是 echo_1**；旧断言把「第二个人不存在」
        // 当成了「第二个人被拒绝」——那正是被修掉的缺陷本身。
        DockingPlateRegistry registry = new DockingPlateRegistry();
        EventDispatcher bus = new EventDispatcher();
        DockingPlate plate = new DockingPlate(
                "L02_plate_door", new Vector2D(100.0, 100.0), registry, bus);

        assertTrue(plate.tryEnter("echo_1", 1, COMPETE_TICK), "较旧残影 echo_1 先占，胜");
        assertTrue(plate.tryEnter("echo_2", 2, COMPETE_TICK), "多占用模型：echo_2 也被登记");
        assertEquals("echo_1", plate.getOccupantId(), "较旧残影仍是主占用者（不覆盖）");
        assertEquals(List.of("echo_1", "echo_2"), plate.getOccupantIds(), "两人都在板上");
    }

    @Test
    void echoTrailsAreIndependentPerSourceRound() {
        EchoState e1 = echoOfRound(1);
        EchoState e2 = echoOfRound(2);

        // 同 tick 两条轨迹的位置快照各自独立，不共享缓冲、不互相干扰。
        PlayerFrame f1 = e1.frameAt(COMPETE_TICK);
        PlayerFrame f2 = e2.frameAt(COMPETE_TICK);

        assertEquals(1 * 1000.0 + COMPETE_TICK, f1.x(), "E1 轨迹 x 独立");
        assertEquals(2 * 1000.0 + COMPETE_TICK, f2.x(), "E2 轨迹 x 独立");
        assertNotEquals(f1.x(), f2.x());
        assertEquals(1, e1.sourceRound());
        assertEquals(2, e2.sourceRound());
    }

    @Test
    void firstEchoExpiresAtRoundFourAndReleasesItsPlates() {
        EchoQueue queue = new EchoQueue(2);
        DockingPlateRegistry registry = new DockingPlateRegistry();
        EventDispatcher bus = new EventDispatcher();
        DockingPlate doorPlate = new DockingPlate(
                "L02_plate_door", new Vector2D(100.0, 100.0), registry, bus);
        DockingPlate mainPlate = new DockingPlate(
                "L02_plate_main", new Vector2D(200.0, 200.0), registry, bus);

        // R1：E1 占门外板 + 主驻留板（L2 多板场景）。
        assertTrue(doorPlate.tryEnter("echo_1", 1, 10));
        assertTrue(mainPlate.tryEnter("echo_1", 1, 20));
        queue.addOnRoundEnd(echoOfRound(1), 2);
        // R2：E2。
        queue.addOnRoundEnd(echoOfRound(2), 3);
        // R3 活跃 = {E1, E2}。
        assertEquals(List.of(1, 2),
                queue.activeEchoes(3).stream().map(EchoState::sourceRound).toList());

        // R3 末入队 E3 → E1 超龄被淘汰。
        List<EchoState> evicted = queue.addOnRoundEnd(echoOfRound(3), 4);
        assertEquals(List.of(1), evicted.stream().map(EchoState::sourceRound).toList(),
                "R4 边界应恰好淘汰 E1");
        assertEquals(List.of(2, 3),
                queue.activeEchoes(4).stream().map(EchoState::sourceRound).toList());

        // 装配层拿到 evicted 后派发 ECHO_DISAPPEARED("echo_1") → 释放 E1 占用的两块板。
        bus.dispatch(GameEvent.echoDisappeared("echo_1", D - 1, 1));
        assertFalse(doorPlate.isOccupied(), "E1 消散后门外板必须释放");
        assertFalse(mainPlate.isOccupied(), "E1 消散后主驻留板必须释放");
        assertNull(doorPlate.getOccupantId());
        assertNull(mainPlate.getOccupantId());
    }

    @Test
    void echoEvictionDoesNotClearLatchState() {
        DockingPlateRegistry registry = new DockingPlateRegistry();
        EventDispatcher bus = new EventDispatcher();
        // L1 开关（latching=true）：被踩上即锁存。
        DockingPlate latchSwitch = new DockingPlate(
                "L01_plate_right", new Vector2D(100.0, 100.0), registry, bus, true);
        // L2 门外板（latching=false）：不锁存。
        DockingPlate doorPlate = new DockingPlate(
                "L02_plate_door", new Vector2D(200.0, 200.0), registry, bus);

        // 开关被 echo_1 踩上并离开 → 锁存 ON（占用已释放）。
        assertTrue(latchSwitch.tryEnter("echo_1", 1, 10));
        assertTrue(latchSwitch.tryExit("echo_1", 1, 30));
        assertTrue(latchSwitch.isLatched(), "开关锁存 ON");

        // echo_1 消散（淘汰路径）：只释放占用，不得清锁存。
        bus.dispatch(GameEvent.echoDisappeared("echo_1", D - 1, 1));
        assertTrue(latchSwitch.isLatched(), "淘汰路径不得清锁存（锁存只由 reset 清）");
        assertEquals(DockingPlate.State.UNOCCUPIED, latchSwitch.getState());

        // L2 门外板不锁存：消散后彻底释放，锁存恒 false。
        assertTrue(doorPlate.tryEnter("echo_1", 1, 10));
        bus.dispatch(GameEvent.echoDisappeared("echo_1", D - 1, 1));
        assertFalse(doorPlate.isOccupied());
        assertFalse(doorPlate.isLatched(), "非开关板锁存恒 false");
    }
}
