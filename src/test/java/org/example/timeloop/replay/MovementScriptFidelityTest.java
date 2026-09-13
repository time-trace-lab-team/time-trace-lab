package org.example.timeloop.replay;

import org.example.timeloop.core.ActorPhase;
import org.example.timeloop.core.AnimationState;
import org.example.timeloop.core.Direction;
import org.example.timeloop.core.GamePhase;
import org.example.timeloop.core.MovementState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * X-MOVE-COLLAPSE-01-DEV2 · R-1 移动脚本逐刻保真（吸收 R2.7-IDLE-FRAMES）。
 *
 * <p>验证：一条覆盖自由移动全部形态的位置快照序列，在第 1 轮录制、第 2 轮由残影回放时，
 * 逐 tick 全等（x / y / direction / movementState / actorPhase / animationState），
 * 无空档、帧索引严格等于 tick、0..D-1 无缺无重。</p>
 *
 * <p>形态覆盖（对应任务卡 R-1 的输入描述）：</p>
 * <ul>
 *   <li>按住走（CRUISING）→ 松开停若干刻（IDLE）→ 段中间按垂直方向停在段中间（IDLE）</li>
 *   <li>真死路掉头（朝向反转）→ 驻留停留 ≥12 刻（DOCKED）→ 离开（CRUISING）</li>
 * </ul>
 *
 * <p>本测试只验证 replay/** 的录制与回放保真，不引入第二套时钟，
 * 也不按 {@link MovementState} 值分支去改变索引/回放规则。</p>
 */
class MovementScriptFidelityTest {

    private static final int D = 60;

    /**
     * 生成一段 60 帧的混合移动脚本。
     * 相邻 tick 的 x 变化保持连贯（走 +1 / 停不变 / 掉头 -1），无跳跃空档。
     */
    private static List<PlayerFrame> mixedScript() {
        List<PlayerFrame> frames = new ArrayList<>(D);
        double x = 100.0;
        final double y = 200.0;
        Direction dir = Direction.RIGHT;

        for (int t = 0; t < D; t++) {
            MovementState state;
            if (t <= 9) {
                // A. 按住走（CRUISING）：x 每刻 +1，朝右
                x = 100.0 + t;
                dir = Direction.RIGHT;
                state = MovementState.CRUISING;
            } else if (t <= 15) {
                // B. 松开停（IDLE）：x 停住，朝向保持右
                state = MovementState.IDLE;
            } else if (t <= 21) {
                // C. 段中间按垂直方向、停在段中间（IDLE）：朝向记为按下的 UP，位置不动
                dir = Direction.UP;
                state = MovementState.IDLE;
            } else if (t <= 29) {
                // D. 真死路掉头：朝向反转为 LEFT，x 每刻 -1 往回走
                x = 109.0 - (t - 21);
                dir = Direction.LEFT;
                state = MovementState.CRUISING;
            } else if (t <= 41) {
                // E. 驻留（DOCKED）：连续 12 刻（t=30..41），x/y 完全停住
                state = MovementState.DOCKED;
            } else {
                // F. 离开（CRUISING）：朝右，x 每刻 +1
                x = 101.0 + (t - 41);
                dir = Direction.RIGHT;
                state = MovementState.CRUISING;
            }
            AnimationState anim = (state == MovementState.DOCKED)
                    ? AnimationState.DOCKED : AnimationState.MOVING;
            frames.add(new PlayerFrame(t, x, y, dir, false, state,
                    ActorPhase.AVAILABLE, 0, anim));
        }
        return frames;
    }

    /** 构造一个已进入 PLAYING 的时钟（与 RecordingSessionTest 相同的起步序列）。 */
    private static RoundClock playingClock(int durationTicks, int maxRounds) {
        RoundClock c = new RoundClock(durationTicks, maxRounds);
        c.transition(GamePhase.MENU);
        c.transition(GamePhase.LEVEL_SELECT);
        c.transition(GamePhase.READY);
        c.transition(GamePhase.PLAYING);
        return c;
    }

    /** 第 1 轮录制整段混合脚本并封装，返回第 2 轮可回放的残影。 */
    private static EchoState replayFirstRound() {
        RoundClock clock = playingClock(D, 3);
        RecordingSession session = new RecordingSession(clock, new EchoQueue(2));
        session.beginRound();
        for (PlayerFrame f : mixedScript()) {
            session.recordFrame(f);
        }
        session.completeNormalRound(() -> {});
        List<EchoState> active = session.echoQueue().activeEchoes(2);
        assertEquals(1, active.size(), "第 2 轮应恰好有 1 个活跃残影（第 1 轮产生）");
        return active.get(0);
    }

    @Test
    void mixedMovementScript_replaysTickByTickIdentical() {
        EchoState echo = replayFirstRound();
        List<PlayerFrame> script = mixedScript();

        assertEquals(D, echo.durationTicks(), "残影时间线长度必须等于轮长 D");

        for (int t = 0; t < D; t++) {
            PlayerFrame orig = script.get(t);
            PlayerFrame replayed = echo.frameAt(t);

            // 帧索引严格 == tick（无缺无重的前提）
            assertEquals(t, replayed.tick(), "帧索引必须严格等于 tick");
            // 任务卡 R-1 要求逐 tick 全等的 6 个字段
            assertEquals(orig.x(), replayed.x(), 1e-9, "tick " + t + " 的 x 偏移");
            assertEquals(orig.y(), replayed.y(), 1e-9, "tick " + t + " 的 y 偏移");
            assertEquals(orig.direction(), replayed.direction(), "tick " + t + " 的朝向");
            assertEquals(orig.movementState(), replayed.movementState(), "tick " + t + " 的移动状态");
            assertEquals(orig.actorPhase(), replayed.actorPhase(), "tick " + t + " 的相位");
            assertEquals(orig.animationState(), replayed.animationState(), "tick " + t + " 的姿态");
        }
    }

    @Test
    void consecutiveIdleFrames_replayWithoutGaps() {
        EchoState echo = replayFirstRound();

        // 连续 IDLE 段 t=10..21（松开停 + 段中间按垂直方向），逐刻都能取到且状态正确
        for (int t = 10; t <= 21; t++) {
            assertEquals(MovementState.IDLE, echo.frameAt(t).movementState(),
                    "IDLE 段 tick " + t + " 不得缺失或改变状态");
        }

        // 边界连续：CRUISING → IDLE → IDLE → CRUISING，无跳帧
        assertEquals(MovementState.CRUISING, echo.frameAt(9).movementState(),
                "进入 IDLE 段的前一刻应为 CRUISING");
        assertEquals(MovementState.IDLE, echo.frameAt(10).movementState());
        assertEquals(MovementState.IDLE, echo.frameAt(21).movementState());
        assertEquals(MovementState.CRUISING, echo.frameAt(22).movementState(),
                "离开 IDLE 段的后一刻应为 CRUISING");
    }

    @Test
    void dockedSegment_replaysEveryTickForAtLeast12Ticks() {
        EchoState echo = replayFirstRound();

        int dockedStart = 30;
        int dockedEnd = 41;
        assertEquals(12, dockedEnd - dockedStart + 1, "驻留必须 >= 12 刻");

        for (int t = dockedStart; t <= dockedEnd; t++) {
            assertEquals(MovementState.DOCKED, echo.frameAt(t).movementState(),
                    "驻留段 tick " + t + " 不得缺失或提前滑出");
        }

        // 离开边界：驻留结束后立即回到 CRUISING，不得永久停住
        assertEquals(MovementState.DOCKED, echo.frameAt(41).movementState());
        assertEquals(MovementState.CRUISING, echo.frameAt(42).movementState());
    }

    @Test
    void recordingRejectsOutOfOrderOrSkippedTicks() {
        // 「帧索引严格 == tick、无缺无重」由 TimelineRecording.record 在写入时强制：
        // 缺 tick / 重复 tick / 乱序都必须明确失败，而不是静默错位。
        TimelineRecording r = new TimelineRecording(3, 1);

        // 跳过 tick 0，直接写 tick 1 → 拒绝
        PlayerFrame skip = new PlayerFrame(1, 0, 0, Direction.RIGHT, false,
                MovementState.CRUISING, ActorPhase.AVAILABLE, 0, AnimationState.MOVING);
        assertThrows(IllegalArgumentException.class, () -> r.record(skip),
                "跳过 tick 0 直接写 tick 1 必须失败");

        // 正确写入 tick 0 后，重复写 tick 0 → 拒绝
        r.record(new PlayerFrame(0, 0, 0, Direction.RIGHT, false,
                MovementState.CRUISING, ActorPhase.AVAILABLE, 0, AnimationState.MOVING));
        assertThrows(IllegalArgumentException.class, () -> r.record(
                        new PlayerFrame(0, 0, 0, Direction.RIGHT, false,
                                MovementState.CRUISING, ActorPhase.AVAILABLE, 0, AnimationState.MOVING)),
                "重复写入 tick 0 必须失败");
    }
}
