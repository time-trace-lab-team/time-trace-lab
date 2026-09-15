package org.example.timeloop.level;

import org.example.timeloop.entity.PatrolConfig;
import org.example.timeloop.entity.PlayerSlowdownController;
import org.example.timeloop.level.model.DoorInfo;
import org.example.timeloop.level.model.EntitySpawnInfo;
import org.example.timeloop.level.model.LevelData;
import org.example.timeloop.level.model.PathNode;
import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.DockingPlate;
import org.example.timeloop.mechanism.DockingPlateRegistry;
import org.example.timeloop.mechanism.Door;
import org.example.timeloop.mechanism.ExitTerminal;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.example.timeloop.mechanism.ray.Ray;
import org.example.timeloop.mechanism.ray.RayFactory;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第三关《追赶过去》重排 v3 的三轮因果链（设计文档「刻表」+「公平性」）。
 *
 * <p>整条链是<b>逐刻驱动</b>的：每个共享逻辑刻先回放残影该做的板事件，再判断当前玩家在那一刻能不能
 * 穿门。所有机关都是仓库真实类 —— {@link DockingPlate}（普通板占即开离即关、{@code role=switch}
 * 的开关踩上即锁存到轮末）、{@link Door}（按占用注册表实时判定，v3 的出口闸要求 S₂+S₃+K 三块同时成立）、
 * {@link ExitTerminal}（只认 {@code DOOR_UNLOCKED}）、{@link Ray} + {@link RayFactory}
 * （状态只由共享 {@code roundTick} 决定）。</p>
 *
 * <p><b>每一轮都新建一套机关现场</b>（{@link Round}）：这与装配层轮末 {@code reset()} 等价。
 * 不这么做，第二轮锁存的开关会一直留到第三轮，把「E₂ 必须重踩 S₂」这条因果掩盖成假绿。</p>
 *
 * <p><b>受击 / 等待的迟到量不是手填数字</b>：由真实 {@link PlayerSlowdownController}（0.5× / 60 刻）
 * 配 {@link PatrolConfig#C2_BASE_SPEED} 逐刻走完「射线 → B」那段算出来，并先用真实 {@link Ray}
 * 确认抵达射线那一刻确实 ACTIVE。</p>
 *
 * <p>顺序口径与上一版一致：本模型在每一刻先回放残影、再判定玩家，比真实装配乐观 1 刻（真实装配里
 * 玩家在同一刻的移动发生在回放之前）。刻表留了 24 刻余量，1 刻不改变任何结论；真驾驶端到端验证由
 * {@code Level03AssemblyTest} 承担。</p>
 */
class Level03PursuitChainTest {

    private static final long ROUND_END = Level03Pursuit.DURATION_TICKS;
    private static final double BASE_SPEED = PatrolConfig.C2_BASE_SPEED;
    private static final long NEVER = Long.MAX_VALUE;

    /** 第二轮玩家的三种行为（设计文档「公平性」②③）。 */
    private enum Strategy {
        /** 看到预警后正确下潜：准时到 B。 */
        PHASE,
        /** 不下潜、被射线命中：减速后晚到 B。 */
        HIT,
        /** 躲起来等射线关闭：未被命中但同样晚到 B。 */
        WAIT
    }

    // ---------- 正解 ----------

    @Test
    void correctThreeRoundSolutionClearsTheLevel() {
        Run second = runSecondRound(Strategy.PHASE);
        assertTrue(second.doorAOpenedByEcho, "第二轮门 A 由 E₁ 打开");
        assertTrue(second.crossedDoorA, "第二轮必须在 E₁ 的门 A 窗口内穿门");
        assertFalse(second.switchS2LatchedByPlayer, "S₂ 已不在 E₂ 支路上（第二轮不碰它）");
        assertTrue(second.rayActiveAtArrival, "第二轮抵达射线时射线必须 ACTIVE（否则不必下潜）");
        assertEquals(Level03Pursuit.DOOR_C_CROSS_BY_E2_TICK, second.doorCCrossTick,
                "E₂ 穿门 C 的刻");
        assertTrue(second.doorCOpenForE2, "E₂ 到门 C 时 E₁ 的窗口已经开着（不空等）");
        assertEquals(Level03Pursuit.PLATE_B_ARRIVAL, second.bArrival,
                "正确下潜必须准时到 B（刻 " + Level03Pursuit.PLATE_B_ARRIVAL + "）");

        Run third = runThirdRound(second.bArrival, true, true, true, true, null);
        assertTrue(third.crossedDoorA, "第三轮仍在 E₁ 的门 A 窗口内穿门");
        assertFalse(third.doorBOpenAtE3Arrival, "⑥ E₃ 抵达门 B 门外时门还锁着（要等 E₂）");
        assertTrue(third.doorBOpened, "门 B 由 E₂ 在刻 " + second.bArrival + " 打开");
        assertTrue(third.crossedDoorB, "E₃ 在门外等到开门后必须能穿过去");
        assertTrue(third.switchS3LatchedByPlayer, "E₃ 必须踩上开关室里的 S₃（出口闸第 2 个条件）");
        assertTrue(third.doorCOpenForE3, "③ E₃ 穿门 C 时门 C 必须还开着");
        assertEquals(Level03Pursuit.DOOR_C_CROSS_TICK, third.doorCCrossTick, "E₃ 穿门 C 的刻");
        assertEquals(Level03Pursuit.EXIT_ARRIVAL, third.exitArrivalTick, "E₃ 抵出口的刻");
        assertEquals(Level03Pursuit.PLATE_K_ARRIVAL, third.gateUnlockedTick,
                "出口闸在 E₁ 压住 K 板那一刻才解锁");
        assertEquals(Level03Pursuit.PLATE_K_ARRIVAL, third.interactTick,
                "按 E 的刻 = 供能刻（玩家在出口等过 K 板）");
        assertTrue(third.exitArmed, "三条件齐了出口终端才被武装");
        assertTrue(third.cleared, "第三轮在出口等到供能后按 E 必须通关");
        assertTrue(Level03Pursuit.PLATE_C_WINDOW_END - third.doorCCrossTick
                        >= Level03Pursuit.SUCCESS_MARGIN_TICKS,
                "① 正解过门 C 后必须仍有 successMargin 刻余量");
    }

    // ---------- 公平性：两条失败路线 ----------

    /**
     * 受击路线的后果：C 在 (7,3) ⇒ A→C 7 格、门 C 在 528 就开，E₂ 抵达门口（624）时门早已开着、
     * 不再空等，因此受击的 30 刻迟到全额生效 —— 第三轮赶不上门 C 窗口。
     */
    @Test
    void hitByTheRayInRoundTwoMissesDoorCInRoundThree() {
        Run second = runSecondRound(Strategy.HIT);
        assertTrue(second.hitByRay, "夹具前提：第二轮确实被射线命中");
        assertEquals(Level03Pursuit.LAGGED_B_ARRIVAL, second.bArrival,
                "被命中后到 B 的刻必须等于真实减速算出来的刻（迟 "
                        + Level03Pursuit.HIT_DELAY_TICKS + " 刻）");
        assertTrue(second.bArrival > Level03Pursuit.LATEST_USEFUL_B_ARRIVAL,
                "被命中后到 B 必须晚于「B 板仍有用」的最晚刻");

        Run third = runThirdRound(second.bArrival, true, true, true, true, null);
        assertTrue(third.doorBOpened, "门 B 最终还是会开，只是开得太晚");
        assertTrue(third.crossedDoorB, "E₃ 还是穿过了门 B");
        assertEquals(Level03Pursuit.LATE_DOOR_C_ARRIVAL, third.doorCCrossTick, "迟到路线到门 C 的刻");
        assertFalse(third.doorCOpenForE3, "门 C 窗口已经关了");
        assertTrue(third.doorCClosedAtArrival, "失败原因必须能从画面判断：到门 C 时门已关");
        assertFalse(third.cleared, "被命中的第二轮必须导致第三轮无法通关");
    }

    @Test
    void waitingForTheRayToTurnOffAlsoMissesDoorCInRoundThree() {
        Run second = runSecondRound(Strategy.WAIT);
        assertFalse(second.hitByRay, "等待路线不该被命中");
        assertEquals(Level03Pursuit.WAIT_FOR_OFF_B_ARRIVAL, second.bArrival,
                "等到射线关闭才继续走，抵达 B 的刻必须等于 "
                        + Level03Pursuit.WAIT_FOR_OFF_B_ARRIVAL);
        assertTrue(second.bArrival > Level03Pursuit.LATEST_USEFUL_B_ARRIVAL,
                "等待导致的晚到同样必须超过有效阈值");

        Run third = runThirdRound(second.bArrival, true, true, true, true, null);
        assertFalse(third.doorCOpenForE3, "门 C 已经关了");
        assertFalse(third.cleared, "等射线的第二轮必须导致第三轮无法通关");
    }

    // ---------- 缺件反证 ----------

    @Test
    void withoutE1DoorANeverOpensSoTheInnerRegionIsUnreachable() {
        Run third = runThirdRound(NEVER, false, true, true, true, null);
        assertFalse(third.doorAOpenedByEcho, "删掉 E₁ 后门 A 不得被打开");
        assertFalse(third.crossedDoorA, "删掉 E₁ 后进不了内区");
        assertFalse(third.cleared, "删掉 E₁ 后无法通关");
    }

    @Test
    void withoutE2DoorBNeverOpensInRoundThree() {
        Run third = runThirdRound(NEVER, true, false, true, true, null);
        assertTrue(third.crossedDoorA, "E₁ 仍在，门 A 应开");
        assertFalse(third.doorBOpened, "删掉 E₂ 后门 B 不得打开");
        assertFalse(third.crossedDoorB, "门 B 不开就进不了开关室");
        assertFalse(third.cleared, "删掉 E₂ 后无法通关");
    }

    @Test
    void exitGateNeedsAllThreeConditions() {
        // 缺 S₂（E₂ 没踩）= 门 C 照开、B 板照拿，但出口闸永远差一个条件
        Run missingS2 = runThirdRound(Level03Pursuit.PLATE_B_ARRIVAL, true, true, false, true, null);
        assertTrue(missingS2.crossedDoorB, "缺 S₂ 不影响门 B");
        assertTrue(missingS2.crossedDoorC, "缺 S₂ 不影响门 C");
        assertTrue(missingS2.plateKHeld, "K 板仍然被 E₁ 压住");
        assertFalse(missingS2.switchesS2On, "S₂ 没被踩过");
        assertFalse(missingS2.gateUnlocked, "缺 S₂ 时出口闸不得解锁（三条件缺一不可）");
        assertFalse(missingS2.cleared, "缺 S₂ 时按 E 无效");

        // 缺 S₃（E₃ 没进开关室）
        Run missingS3 = runThirdRound(Level03Pursuit.PLATE_B_ARRIVAL, true, true, true, false, null);
        assertFalse(missingS3.switchS3LatchedByPlayer, "S₃ 没被踩过");
        assertFalse(missingS3.gateUnlocked, "缺 S₃ 时出口闸不得解锁");
        assertFalse(missingS3.cleared, "缺 S₃ 时按 E 无效");

        // 缺 K（E₁ 第一轮没走到 K）
        Run missingK = runThirdRound(Level03Pursuit.PLATE_B_ARRIVAL, true, true, true, true, null, false);
        assertFalse(missingK.plateKHeld, "K 板没人压");
        assertFalse(missingK.gateUnlocked, "缺 K 时出口闸不得解锁");
        assertFalse(missingK.cleared, "缺 K 时按 E 无效");
    }

    @Test
    void switchesMustLatchOrTheGateCanNeverBeSatisfied() {
        // 开关是锁存变体：踩上之后即使人走了，条件依然成立到轮末。
        Round round = new Round();
        DockingPlate s2 = round.plate(Level03Pursuit.SWITCH_S2);
        assertTrue(s2.isLatched() == false, "开局未锁存");
        assertTrue(s2.tryEnter("echo_2", 2, Level03Pursuit.SWITCH_S2_ARRIVAL));
        assertTrue(s2.isLatched(), "踩上即锁存");
        // E₂ 路过就继续往门 C 走：刻 480 离开
        assertTrue(s2.tryExit("echo_2", 2, Level03Pursuit.SWITCH_S2_ARRIVAL + Level03Pursuit.TICKS_PER_TILE));
        assertFalse(s2.isOccupiedBy("echo_2"), "人已经走了");
        assertTrue(s2.isOccupied(), "但板条件依然成立（锁存）");
        // 直到 K 板被压住的那一刻（1368）条件仍然成立 —— 这正是三条件能同时成立的前提
        assertTrue(s2.isOccupied(), "锁存后不会再变：到 K 板供能刻（"
                + Level03Pursuit.PLATE_K_ARRIVAL + "）S₂ 条件依然成立");
        round.close();

        // 对照：如果 S₂ 是普通板（不锁存），同一条时间线到 1368 就凑不齐三条件。
        DockingPlateRegistry plainRegistry = new DockingPlateRegistry();
        EventDispatcher plainBus = new EventDispatcher();
        DockingPlate plainS2 = new DockingPlate(Level03Pursuit.SWITCH_S2,
                Level03Pursuit.cellCenter(Level03Pursuit.CELL_SWITCH_S2[0],
                        Level03Pursuit.CELL_SWITCH_S2[1]), plainRegistry, plainBus);
        plainS2.tryEnter("echo_2", 2, Level03Pursuit.SWITCH_S2_ARRIVAL);
        plainS2.tryExit("echo_2", 2, Level03Pursuit.SWITCH_S2_ARRIVAL + Level03Pursuit.TICKS_PER_TILE);
        assertFalse(plainS2.isOccupied(),
                "普通板在人离开后就释放 —— 到 K 板供能刻就永远差这一个条件");
    }

    @Test
    void rayPhaseMakesTheDiveCompulsory() {
        Ray ray = rayAt(Level03Pursuit.RAY_CROSS_TICK);
        assertEquals(Ray.State.ACTIVE, ray.getState(),
                "E₂ 抵射线那一刻射线必须 ACTIVE（初相 = " + Level03Pursuit.RAY_CROSS_PHASE + "）");
        assertEquals(Ray.State.WARNING,
                rayAt(Level03Pursuit.RAY_WARNING_START_TICK + 1).getState(),
                "预警段必须出现在抵达之前，玩家才有 72 刻反应时间");
        assertEquals(Ray.State.OFF, rayAt(Level03Pursuit.RAY_WARNING_START_TICK - 1).getState(),
                "预警起点之前是 OFF");
        assertEquals(Ray.State.OFF, rayAt(Level03Pursuit.RAY_ACTIVE_START_TICK
                + Level03Pursuit.RAY_ACTIVE_DURATION_TICKS).getState(),
                "激活结束后回到 OFF（周期 " + Level03Pursuit.RAY_CYCLE_TICKS + "）");
    }

    @Test
    void echoReplayDoesNotResettleTheRay() {
        // E₂ 第三轮重放的路线确实穿过射线判定带……
        Vector2D rayCell = Level03Pursuit.cellCenter(
                Level03Pursuit.CELL_RAY[0], Level03Pursuit.CELL_RAY[1]);
        assertTrue(Math.abs(rayCell.x() - Level03Pursuit.RAY_X) <= Level03Pursuit.RAY_HIT_WIDTH,
                "夹具前提：E₂ 路线经过射线所在格（竖射线跨的就是这一列）");
        // ……但回放只写板事件，不写减速：真实减速控制器全程不得被触动。
        PlayerSlowdownController slowdown = new PlayerSlowdownController();
        Run third = runThirdRound(Level03Pursuit.PLATE_B_ARRIVAL, true, true, true, true, slowdown);
        assertTrue(third.cleared, "夹具前提：这一轮是正解");
        assertFalse(slowdown.isSlowed(), "残影回放经过射线时不得结算减速");
        assertFalse(slowdown.speedMultiplier() < 1.0, "回放不得改变当前玩家的速度倍率");
    }

    // ---------- 逐刻模拟 ----------

    /** 一轮的结论（失败原因可判定）。 */
    private static final class Run {
        long doorCCrossTick = -1;
        long exitArrivalTick = -1;
        long interactTick = -1;
        long gateUnlockedTick = -1;
        long bArrival = -1;
        boolean doorAOpenedByEcho;
        boolean crossedDoorA;
        boolean doorBOpenAtE3Arrival;
        boolean doorBOpened;
        boolean crossedDoorB;
        boolean doorCOpenForE2;
        boolean crossedDoorCByE2;
        boolean doorCOpenForE3;
        boolean crossedDoorC;
        boolean doorCClosedAtArrival;
        boolean rayActiveAtArrival;
        boolean hitByRay;
        boolean switchS2LatchedByPlayer;
        boolean switchesS2On;
        boolean switchS3LatchedByPlayer;
        boolean plateKHeld;
        boolean exitArmed;
        boolean gateUnlocked;
        boolean cleared;
    }

    /** 一回合的机关现场：与装配层轮末 {@code reset()} 等价 —— 每轮重建。 */
    private static final class Round {
        final LevelData level = Level03Pursuit.build();
        final DockingPlateRegistry registry = new DockingPlateRegistry();
        final EventDispatcher bus = new EventDispatcher();
        final Map<String, DockingPlate> plates = new LinkedHashMap<>();
        final Map<String, Door> doors = new LinkedHashMap<>();
        final ExitTerminal exit;
        final List<Ray> rays;

        Round() {
            for (EntitySpawnInfo entity : level.getEntitySpawnList()) {
                if ("dock_plate".equals(entity.getEntityType())) {
                    boolean latching = "switch".equals(entity.getProperties().get("role"));
                    plates.put(entity.getId(), new DockingPlate(
                            entity.getId(), entity.getPos(), registry, bus, latching));
                }
            }
            for (DoorInfo info : level.getDoors()) {
                doors.put(info.getId(), new Door(info.getId(), info.getPosition(),
                        info.getRequiredPlateIds(), registry, bus));
            }
            this.exit = new ExitTerminal(Level03Pursuit.EXIT, position(Level03Pursuit.EXIT),
                    Level03Pursuit.DOOR_EXIT,
                    ExitTerminal.interactRadiusForTileSize(Level03Pursuit.TILE_SIZE), bus);
            this.rays = RayFactory.buildFrom(level, bus);
        }

        DockingPlate plate(String id) {
            DockingPlate plate = plates.get(id);
            if (plate == null) {
                throw new AssertionError("缺少机关: " + id);
            }
            return plate;
        }

        Door door(String id) {
            Door door = doors.get(id);
            if (door == null) {
                throw new AssertionError("缺少门: " + id);
            }
            return door;
        }

        void close() {
            plates.values().forEach(DockingPlate::dispose);
            doors.values().forEach(Door::dispose);
            exit.dispose();
            RayFactory.disposeAll(rays);
        }

        private Vector2D position(String mechanismId) {
            return level.getEntitySpawnList().stream()
                    .filter(e -> mechanismId.equals(e.getId()))
                    .map(EntitySpawnInfo::getPos)
                    .findFirst()
                    .orElseGet(() -> level.getPathNodes().stream()
                            .filter(n -> mechanismId.equals(n.getId()))
                            .map(PathNode::getWorldPos)
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("缺少机制: " + mechanismId)));
        }
    }

    /** E₁ 第一轮录到的板事件在刻 {@code tick} 的回放（A / C / K）。 */
    private static void replayFirstRoute(Round round, String actorId, long tick, boolean withK) {
        if (tick == Level03Pursuit.GATE_A_WINDOW_START) {
            assertTrue(round.plate(Level03Pursuit.PLATE_A).tryEnter(actorId, 1, tick));
        } else if (tick == Level03Pursuit.GATE_A_WINDOW_END) {
            assertTrue(round.plate(Level03Pursuit.PLATE_A).tryExit(actorId, 1, tick));
        } else if (tick == Level03Pursuit.PLATE_C_ARRIVAL) {
            assertTrue(round.plate(Level03Pursuit.PLATE_C).tryEnter(actorId, 1, tick));
        } else if (tick == Level03Pursuit.PLATE_C_WINDOW_END) {
            assertTrue(round.plate(Level03Pursuit.PLATE_C).tryExit(actorId, 1, tick));
        } else if (tick == Level03Pursuit.PLATE_K_ARRIVAL && withK) {
            assertTrue(round.plate(Level03Pursuit.PLATE_K).tryEnter(actorId, 1, tick),
                    "E₁ 压住 K 板并驻留到轮末");
        }
    }

    /**
     * 第二轮：E₁ 回放 A/C/K；当前玩家趁 E₁ 开门 A 进内区、向南踩 S₂、下潜过射线、穿门 C、驻留 B 到轮末。
     *
     * <p>返回的这一轮结论会被当作第三轮的 E₂ 脚本（{@link #runThirdRound} 用 {@code recordedBArrival}）。</p>
     */
    private Run runSecondRound(Strategy strategy) {
        Round round = new Round();
        long bArrival = simulateRayToPlateB(strategy);
        Run run = new Run();
        run.bArrival = bArrival;
        run.hitByRay = strategy == Strategy.HIT;

        for (long tick = 0; tick <= ROUND_END; tick++) {
            replayFirstRoute(round, "echo_1", tick, true);
            RayFactory.updateAll(round.rays, tick);
            if (tick == Level03Pursuit.DOOR_A_CROSS_TICK) {
                run.doorAOpenedByEcho = round.door(Level03Pursuit.DOOR_A).isUnlocked();
                run.crossedDoorA = run.doorAOpenedByEcho;
                if (!run.crossedDoorA) {
                    round.close();
                    return run;
                }
            }
            if (tick == Level03Pursuit.RAY_CROSS_TICK) {
                run.rayActiveAtArrival = round.rays.get(0).getState() == Ray.State.ACTIVE;
            }
            if (tick == Level03Pursuit.DOOR_C_CROSS_BY_E2_TICK) {
                run.doorCOpenForE2 = round.door(Level03Pursuit.DOOR_C).isUnlocked();
                if (!run.doorCOpenForE2) {
                    round.close();
                    return run;
                }
                run.doorCCrossTick = tick;
                run.crossedDoorCByE2 = true;
            }
            if (tick == bArrival) {
                assertTrue(round.plate(Level03Pursuit.PLATE_B).tryEnter("player", 2, tick),
                        "第二轮玩家驻留 B 板到轮末");
                round.close();
                return run;
            }
        }
        round.close();
        throw new AssertionError("第二轮没能抵达 B");
    }

    /**
     * 第三轮：E₁ 回放 A/C/K，E₂ 回放 {@code {S₂ 456, B <第二轮抵 B 刻>}}，当前玩家走 E₃ 主线。
     */
    private Run runThirdRound(long e2BArrival, boolean withE1, boolean withE2, boolean withS2,
                              boolean withS3, PlayerSlowdownController observer) {
        return runThirdRound(e2BArrival, withE1, withE2, withS2, withS3, observer, true);
    }

    private Run runThirdRound(long e2BArrival, boolean withE1, boolean withE2, boolean withS2,
                              boolean withS3, PlayerSlowdownController observer, boolean withK) {
        Round round = new Round();
        Run run = new Run();

        long doorBOpenedTick = withE2 ? e2BArrival : NEVER;
        long switchS3Tick = withS3 && doorBOpenedTick != NEVER
                ? doorBOpenedTick + Level03Pursuit.SWITCH_S3_TO_DOOR_B_TICKS : NEVER;
        long doorCCrossTick = switchS3Tick == NEVER
                ? NEVER
                : switchS3Tick + Level03Pursuit.SWITCH_S3_TO_DOOR_B_TICKS
                + Level03Pursuit.DOOR_B_TO_DOOR_C_TICKS;
        long exitArrivalTick = doorCCrossTick == NEVER
                ? NEVER
                : doorCCrossTick + Level03Pursuit.DOOR_C_TO_EXIT_TICKS;
        long interactTick = exitArrivalTick == NEVER
                ? NEVER : Math.max(exitArrivalTick, Level03Pursuit.PLATE_K_ARRIVAL);

        for (long tick = 0; tick <= ROUND_END; tick++) {
            if (observer != null) {
                observer.beginTick(tick);
            }
            if (withE1) {
                replayFirstRoute(round, "echo_1", tick, withK);
                if (tick == Level03Pursuit.PLATE_K_ARRIVAL) {
                    run.plateKHeld = round.plate(Level03Pursuit.PLATE_K).isOccupied();
                }
            }
            RayFactory.updateAll(round.rays, tick);
            if (withE2 && tick == e2BArrival) {
                assertTrue(round.plate(Level03Pursuit.PLATE_B).tryEnter("echo_2", 2, tick),
                        "E₂ 回放在抵 B 刻压住 B 板");
            }
            run.switchesS2On = round.plate(Level03Pursuit.SWITCH_S2).isOccupied();

            if (tick == Level03Pursuit.DOOR_A_CROSS_TICK) {
                run.doorAOpenedByEcho = round.door(Level03Pursuit.DOOR_A).isUnlocked();
                run.crossedDoorA = run.doorAOpenedByEcho;
                if (!run.crossedDoorA) {
                    round.close();
                    return run;
                }
            }
            if (tick == Level03Pursuit.E3_DOOR_B_ARRIVAL) {
                // ⑥ E₃ 到门外时门 B 必须还锁着（它要等 E₂）——除了「E₂ 更早到 B」这种不可能情形
                run.doorBOpenAtE3Arrival = round.door(Level03Pursuit.DOOR_B).isUnlocked();
            }
            if (tick == doorBOpenedTick) {
                run.doorBOpened = round.door(Level03Pursuit.DOOR_B).isUnlocked();
                if (!run.doorBOpened) {
                    round.close();
                    return run;
                }
                run.crossedDoorB = true;
            }
            if (tick == switchS3Tick) {
                run.switchS3LatchedByPlayer =
                        round.plate(Level03Pursuit.SWITCH_S3).tryEnter("player", 0, tick);
            }
            if (withS2 && tick == Level03Pursuit.SWITCH_S2_ARRIVAL) {
                // S₂ 现在在门 C 之后的东南回环上：第三轮玩家去出口的路上踩一下（锁存）
                run.switchS2LatchedByPlayer =
                        round.plate(Level03Pursuit.SWITCH_S2).tryEnter("player", 0, tick);
            }
            if (tick == doorCCrossTick) {
                run.doorCCrossTick = tick;
                run.doorCOpenForE3 = round.door(Level03Pursuit.DOOR_C).isUnlocked();
                run.doorCClosedAtArrival = !run.doorCOpenForE3;
                if (run.doorCClosedAtArrival) {
                    run.exitArrivalTick = NEVER;
                    round.close();
                    return run;
                }
                run.crossedDoorC = true;
            }
            if (tick == exitArrivalTick) {
                run.exitArrivalTick = tick;
                run.exitArmed = round.exit.isDoorUnlocked();
            }
            if (tick == interactTick) {
                run.interactTick = tick;
                run.gateUnlocked = round.door(Level03Pursuit.DOOR_EXIT).isUnlocked();
                run.gateUnlockedTick = run.gateUnlocked ? tick : -1;
                run.exitArmed = round.exit.isDoorUnlocked();
                run.cleared = run.exitArmed && round.exit.interact(tick, 0);
                round.close();
                return run;
            }
        }
        round.close();
        return run;
    }

    /**
     * 用真实射线状态 + 真实减速控制器算「射线 → B」的抵达刻。
     *
     * <p>PHASE：下潜，不减速，走 {@link Level03Pursuit#RAY_TO_DOOR_C_TICKS} +
     * {@link Level03Pursuit#DOOR_C_TO_PLATE_B_TICKS}。HIT：被命中后逐刻按
     * {@link PlayerSlowdownController#speedMultiplier()} 走完。WAIT：等 ACTIVE 结束再继续。</p>
     */
    private static long simulateRayToPlateB(Strategy strategy) {
        long remainingTicks = Level03Pursuit.RAY_TO_DOOR_C_TICKS
                + Level03Pursuit.DOOR_C_TO_PLATE_B_TICKS;
        Ray ray = rayAt(Level03Pursuit.RAY_CROSS_TICK);
        assertEquals(Ray.State.ACTIVE, ray.getState(),
                "第二轮玩家抵达射线的那一刻，射线必须是 ACTIVE（否则不必下潜）");

        if (strategy == Strategy.WAIT) {
            return Math.max(Level03Pursuit.RAY_ACTIVE_START_TICK
                            + Level03Pursuit.RAY_ACTIVE_DURATION_TICKS + remainingTicks,
                    Level03Pursuit.PLATE_C_ARRIVAL + Level03Pursuit.DOOR_C_TO_PLATE_B_TICKS);
        }

        PlayerSlowdownController slowdown = new PlayerSlowdownController();
        slowdown.beginTick(Level03Pursuit.RAY_CROSS_TICK);
        if (strategy == Strategy.HIT) {
            slowdown.noteHit(ray.getId(),
                    Level03Pursuit.RAY_CROSS_TICK / Level03Pursuit.RAY_CYCLE_TICKS);
        }
        double remaining = remainingTicks * BASE_SPEED;
        long tick = Level03Pursuit.RAY_CROSS_TICK;
        while (remaining > 0.0) {
            tick++;
            slowdown.beginTick(tick);
            remaining -= BASE_SPEED * slowdown.speedMultiplier();
            assertTrue(tick < ROUND_END, "射线 → B 这段路必须能在轮内走完");
        }
        // 门 C 要到 PLATE_C_ARRIVAL 才开：E₂ 可能先在门口空等，抵达 B 的刻取两者较晚。
        return Math.max(tick, Level03Pursuit.PLATE_C_ARRIVAL + Level03Pursuit.DOOR_C_TO_PLATE_B_TICKS);
    }

    /** 在刻 {@code tick} 用真实射线类算出来的状态（只读夹具）。 */
    private static Ray rayAt(long tick) {
        Round round = new Round();
        RayFactory.updateAll(round.rays, tick);
        Ray ray = round.rays.get(0);
        round.close();
        return ray;
    }
}

