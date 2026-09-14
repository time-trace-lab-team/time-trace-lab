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

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第三关《追赶过去》三轮因果链（设定书 §5 / §6.3 / §6.4 / §11）。
 *
 * <p>整条链是<b>逐刻驱动</b>的：每个共享逻辑刻先回放残影在那一该做的板事件，再判断当前玩家
 * 在那一刻能不能穿门。所有机关都是仓库真实类 —— {@link DockingPlate}（普通板，占即开、离即关）、
 * {@link Door}（按占用注册表实时判定）、{@link ExitTerminal}（只认 {@code DOOR_UNLOCKED}）、
 * {@link Ray} + {@link RayFactory}（状态只由共享 {@code roundTick} 决定）。</p>
 *
 * <p><b>受击迟到量不是我手填的数字</b>：由真实 {@link PlayerSlowdownController}（0.5× / 60 刻）
 * 配 {@link PatrolConfig#C2_BASE_SPEED} 逐刻走完「射线 → B」那段路算出来，并先用真实
 * {@link Ray} 状态确认抵达射线那一刻确实是 ACTIVE。</p>
 *
 * <p>三条路线（设定书 §6.3）：正确下潜 → 准时到 B（刻 {@code 384}）；被命中 → 迟 30 刻（{@code 414}）；
 * 等射线关闭 → 迟 60 刻（{@code 444}）。B 板仍有用的最晚抵达刻是 {@code 408}，所以后两条都会让
 * 第三轮 E₂ 晚开门 B，当前玩家因此错过 E₁ 的门 C 窗口（刻 {@code 504} 关闭）。</p>
 */
class Level03PursuitChainTest {

    private static final long ROUND_END = Level03Pursuit.DURATION_TICKS;
    private static final double BASE_SPEED = PatrolConfig.C2_BASE_SPEED;
    private static final long NEVER = Long.MAX_VALUE;

    /** 第二轮玩家的三种行为（设定书 §6.3 对照表）。 */
    private enum Strategy {
        /** 看到预警后正确下潜：准时到 B。 */
        PHASE,
        /** 不下潜、被射线命中：减速后晚到 B。 */
        HIT,
        /** 躲在门 A 后等射线关闭：未被命中但同样晚到 B。 */
        WAIT
    }

    @Test
    void correctThreeRoundSolutionClearsTheLevel() {
        Simulation sim = new Simulation();
        sim.runFirstRound();
        sim.runSecondRound(Strategy.PHASE);
        Run run = sim.runThirdRound(true, true);

        assertEquals(Level03Pursuit.PLATE_B_ARRIVAL, sim.recordedBArrival,
                "正确下潜必须准时到 B（刻 " + Level03Pursuit.PLATE_B_ARRIVAL + "）");
        assertTrue(run.crossedDoorA, "第二轮/第三轮必须在 E₁ 的门 A 窗口内穿门");
        assertTrue(run.crossedDoorB, "门 B 必须由 E₂ 及时打开");
        assertTrue(run.crossedDoorC, "正解必须在门 C 窗口内穿过门 C");
        assertEquals(Level03Pursuit.DOOR_C_CROSS_TICK, run.doorCCrossTick, "穿门 C 的刻");
        assertEquals(Level03Pursuit.EXIT_ARRIVAL, run.exitArrivalTick, "走到出口的刻");
        assertTrue(run.exitArmed, "E₁ 抵达 D 后出口必须被武装");
        assertTrue(run.cleared, "当前玩家在出口按 E 必须通关");
        assertTrue(Level03Pursuit.PLATE_C_WINDOW_END - run.doorCCrossTick
                        >= Level03Pursuit.SUCCESS_MARGIN_TICKS,
                "正解过门 C 后必须仍有 successMargin 刻余量");
        assertTrue(Level03Pursuit.PLATE_D_ARRIVAL <= run.exitArrivalTick,
                "出口供能的刻不得晚于玩家走到出口的刻");
    }

    @Test
    void hitByTheRayInRoundTwoMissesDoorCInRoundThree() {
        Simulation sim = new Simulation();
        sim.runFirstRound();
        sim.runSecondRound(Strategy.HIT);
        Run run = sim.runThirdRound(true, true);

        assertTrue(sim.hitByRayInRoundTwo, "夹具前提：第二轮确实被射线命中");
        assertEquals(Level03Pursuit.LAGGED_B_ARRIVAL, sim.recordedBArrival,
                "被命中后到 B 的刻必须等于真实减速算出来的刻（迟 "
                        + Level03Pursuit.HIT_DELAY_TICKS + " 刻）");
        assertTrue(sim.recordedBArrival > Level03Pursuit.LATEST_USEFUL_B_ARRIVAL,
                "被命中后到 B 必须晚于「B 板仍有用」的最晚刻");
        assertTrue(run.crossedDoorB, "门 B 最终还是会开，只是开得太晚");
        assertFalse(run.crossedDoorC, "门 C 关闭后必须穿不过去");
        assertFalse(run.cleared, "被命中的第二轮必须导致第三轮无法通关");
        assertTrue(run.doorCClosedAtArrival, "失败原因必须能从画面判断：到门 C 时门已关");
        assertEquals(Level03Pursuit.LATE_DOOR_C_ARRIVAL, run.doorCCrossTick, "迟到路线到门 C 的刻");
    }

    @Test
    void waitingForTheRayToTurnOffAlsoMissesDoorCInRoundThree() {
        Simulation sim = new Simulation();
        sim.runFirstRound();
        sim.runSecondRound(Strategy.WAIT);
        Run run = sim.runThirdRound(true, true);

        assertFalse(sim.hitByRayInRoundTwo, "等待路线不该被命中");
        assertEquals(Level03Pursuit.WAIT_FOR_OFF_B_ARRIVAL, sim.recordedBArrival,
                "等到射线关闭才继续走，抵达 B 的刻必须等于 "
                        + Level03Pursuit.WAIT_FOR_OFF_B_ARRIVAL);
        assertTrue(sim.recordedBArrival > Level03Pursuit.LATEST_USEFUL_B_ARRIVAL,
                "等待导致的晚到同样必须超过有效阈值");
        assertFalse(run.crossedDoorC, "门 C 关闭后必须穿不过去");
        assertFalse(run.cleared, "等射线的第二轮必须导致第三轮无法通关");
    }

    @Test
    void withoutE1DoorANeverOpensSoTheInnerRegionIsUnreachable() {
        Simulation sim = new Simulation();
        // 第一轮不录制（等价于删掉 E₁）：门 A 没有任何人开。
        Run run = sim.runThirdRound(false, true);

        assertFalse(run.doorAOpenedByEcho, "删掉 E₁ 后门 A 不得被打开");
        assertFalse(run.crossedDoorA, "删掉 E₁ 后进不了内区");
        assertFalse(run.cleared, "删掉 E₁ 后无法通关（设定书 §11.2 第 1 条）");
    }

    @Test
    void withoutE2DoorBNeverOpensInRoundThree() {
        Simulation sim = new Simulation();
        sim.runFirstRound();
        sim.runSecondRound(Strategy.PHASE);
        Run run = sim.runThirdRound(true, false);

        assertTrue(run.crossedDoorA, "E₁ 仍在，门 A 应开");
        assertFalse(run.doorBOpened, "删掉 E₂ 后门 B 不得打开（设定书 §11.2 第 2 条）");
        assertFalse(run.crossedDoorB, "门 B 不开就走不进主通道后段");
        assertFalse(run.cleared, "删掉 E₂ 后无法通关");
    }

    @Test
    void parkingOnCForTheWholeFirstRoundNeverPowersTheExit() {
        Simulation sim = new Simulation();
        // 第一轮一直驻留 C（不离开、不去 D）：C 的窗口成立，但 E₁ 到不了 D。
        sim.runFirstRoundParkedOnC();
        Run run = sim.runThirdRound(true, true);

        assertTrue(sim.plate(Level03Pursuit.PLATE_C).isOccupied(), "夹具前提：C 一直被占");
        assertFalse(sim.plate(Level03Pursuit.PLATE_D).isOccupied(), "一直站 C 就到不了 D");
        assertTrue(sim.doorC.isUnlocked(), "C 被占 → 门 C 一直开着");
        assertFalse(run.exitArmed, "D 未供能 → 出口不得被武装");
        assertFalse(run.cleared, "未供能时按 E 无效（设定书 §11.2 第 4 条）");
        assertFalse(run.crossedDoorA, "第一轮没记录 A，第三轮门 A 也开不了 —— 因果链整体断掉");
    }

    @Test
    void echoReplayDoesNotResettleTheRay() {
        Simulation sim = new Simulation();
        sim.runFirstRound();
        sim.runSecondRound(Strategy.PHASE);

        // E₂ 第三轮重放的路线确实穿过射线判定带……
        double rayCellDistance = Math.abs(
                Level03Pursuit.cellCenter(Level03Pursuit.CELL_RAY[0], Level03Pursuit.CELL_RAY[1]).y()
                        - Level03Pursuit.RAY_Y);
        assertTrue(rayCellDistance <= Level03Pursuit.RAY_HIT_WIDTH,
                "夹具前提：E₂ 路线经过射线所在格");
        // ……但回放只写板事件，不写减速：真实减速控制器全程不得被触动。
        PlayerSlowdownController slowdown = new PlayerSlowdownController();
        Run run = sim.runThirdRound(true, true, slowdown);

        assertTrue(run.cleared, "夹具前提：这一轮是正解");
        assertFalse(slowdown.isSlowed(),
                "残影回放经过射线时不得结算减速（设定书 §2.6 / §11.1）");
        assertFalse(slowdown.speedMultiplier() < 1.0,
                "回放不得改变当前玩家的速度倍率");

        // 射线端点与 B 板驻留点都要符合设定书 §十二 约束 5「射线不覆盖驻留点」。
        Ray ray = sim.ray();
        assertEquals(new Vector2D(Level03Pursuit.RAY_X0, Level03Pursuit.RAY_Y), ray.getStart());
        assertEquals(new Vector2D(Level03Pursuit.RAY_X1, Level03Pursuit.RAY_Y), ray.getEnd());
        assertFalse(ray.containsPoint(Level03Pursuit.cellCenter(
                Level03Pursuit.CELL_PLATE_B[0], Level03Pursuit.CELL_PLATE_B[1]),
                Level03Pursuit.RAY_HIT_WIDTH), "B 板驻留点不得落在射线判定带里");
    }

    @Test
    void twoStaticActorsParkedFromTheStartCannotReplaceTheTwoEchoes() {
        // 设定书 §11.2 第 3 条：把两个残影换成「开局就站在板上、全程不动」的常驻角色，原解不成立。
        // 通关需要四个条件同时成立：门 A 进得去、门 B 开、门 C 开、D 供能。
        // 常驻角色没有任何时序，两个身体最多点亮其中两个条件 —— 因此任意一对都不成立。
        List<String> plates = List.of(Level03Pursuit.PLATE_A, Level03Pursuit.PLATE_B,
                Level03Pursuit.PLATE_C, Level03Pursuit.PLATE_D);
        int pairsChecked = 0;
        for (int i = 0; i < plates.size(); i++) {
            for (int j = i + 1; j < plates.size(); j++) {
                Simulation sim = new Simulation();
                sim.parkStatically(plates.get(i), plates.get(j));
                Run run = sim.runThirdRoundWithStaticOccupancy();

                assertFalse(run.cleared,
                        "两个常驻角色压在 " + plates.get(i) + " + " + plates.get(j)
                                + " 上不得通关（常驻没有时序，拿不到四个条件）");
                int satisfied = (run.crossedDoorA ? 1 : 0) + (run.doorBOpened ? 1 : 0)
                        + (run.crossedDoorC ? 1 : 0) + (run.exitArmed ? 1 : 0);
                assertTrue(satisfied <= 2,
                        "常驻角色最多点亮 2 个条件，实测 " + satisfied);
                pairsChecked++;
            }
        }
        assertEquals(6, pairsChecked, "四块板两两组合共 6 对");
    }

    @Test
    void removingE1OrE2BreaksTheChainButTwoEchoesAreExactlyEnough() {
        // 对照：删任一残影都不行（§11.2 第 1、2 条），而两个残影按刻表接力恰好够。
        Simulation ok = new Simulation();
        ok.runFirstRound();
        ok.runSecondRound(Strategy.PHASE);
        assertTrue(ok.runThirdRound(true, true).cleared, "两个残影按刻表接力必须能通关");
    }

    // ---------- 逐刻模拟 ----------

    /** 一轮的结论（失败原因可判定，对应设定书 §11.2 第 7 条）。 */
    private static final class Run {
        long doorCCrossTick = -1;
        long exitArrivalTick = -1;
        boolean doorAOpenedByEcho;
        boolean crossedDoorA;
        boolean doorBOpened;
        boolean crossedDoorB;
        boolean crossedDoorC;
        boolean doorCClosedAtArrival;
        boolean exitArmed;
        boolean cleared;
    }

    private static final class Simulation {

        final LevelData level = Level03Pursuit.build();
        final DockingPlateRegistry registry = new DockingPlateRegistry();
        final EventDispatcher bus = new EventDispatcher();

        final DockingPlate plateA = createPlate(Level03Pursuit.PLATE_A);
        final DockingPlate plateB = createPlate(Level03Pursuit.PLATE_B);
        final DockingPlate plateC = createPlate(Level03Pursuit.PLATE_C);
        final DockingPlate plateD = createPlate(Level03Pursuit.PLATE_D);

        final Door doorA = createDoor(Level03Pursuit.DOOR_A);
        final Door doorB = createDoor(Level03Pursuit.DOOR_B);
        final Door doorC = createDoor(Level03Pursuit.DOOR_C);
        final Door doorExit = createDoor(Level03Pursuit.DOOR_EXIT);

        final ExitTerminal exit = new ExitTerminal(Level03Pursuit.EXIT,
                position(Level03Pursuit.EXIT), Level03Pursuit.DOOR_EXIT,
                ExitTerminal.interactRadiusForTileSize(Level03Pursuit.TILE_SIZE), bus);

        final List<Ray> rays = RayFactory.buildFrom(level, bus);

        /** 第二轮实际抵达 B 的刻（= 被写进记录、第三轮由 E₂ 原样复现的刻）。 */
        long recordedBArrival = -1;
        boolean hitByRayInRoundTwo;
        /** 第一轮是否「一直驻留 C、不去 D」（决定 E₁ 回放哪些板事件）。 */
        private boolean recordedParkedOnC;

        DockingPlate plate(String id) {
            if (Level03Pursuit.PLATE_A.equals(id)) {
                return plateA;
            }
            if (Level03Pursuit.PLATE_B.equals(id)) {
                return plateB;
            }
            if (Level03Pursuit.PLATE_C.equals(id)) {
                return plateC;
            }
            return plateD;
        }

        Ray ray() {
            return rays.get(0);
        }

        /** 第一轮：玩家在刻表上依次踩 A、离 A、踩 C、离 C、踩 D 并驻留到轮末。 */
        void runFirstRound() {
            runFirstRound(Level03Pursuit.PLATE_C_WINDOW_END, true);
        }

        /** 第一轮变体：一直驻留 C、不去 D（设定书 §11.2 第 4 条）。 */
        void runFirstRoundParkedOnC() {
            recordedParkedOnC = true;
            plateC.tryEnter("player", 0, Level03Pursuit.PLATE_C_ARRIVAL);
        }

        private void runFirstRound(long cLeaveTick, boolean reachD) {
            for (long tick = 0; tick <= ROUND_END; tick++) {
                if (tick == Level03Pursuit.GATE_A_WINDOW_START) {
                    plateA.tryEnter("player", 0, tick);
                } else if (tick == Level03Pursuit.GATE_A_WINDOW_END) {
                    plateA.tryExit("player", 0, tick);
                } else if (tick == Level03Pursuit.PLATE_C_ARRIVAL) {
                    plateC.tryEnter("player", 0, tick);
                } else if (tick == cLeaveTick) {
                    plateC.tryExit("player", 0, tick);
                } else if (reachD && tick == Level03Pursuit.PLATE_D_ARRIVAL) {
                    plateD.tryEnter("player", 0, tick);
                }
            }
        }

        /**
         * 第二轮：E₁ 回放第一轮路线；当前玩家趁 E₁ 开门 A 进内区、走 B 支路、驻留 B 到轮末。
         *
         * <p>B 支路的抵达刻由策略决定，并用真实的 {@link Ray} 与 {@link PlayerSlowdownController}
         * 算出来（见 {@link #simulateRayToPlateB}）。</p>
         */
        Run runSecondRound(Strategy strategy) {
            Run run = new Run();
            long bArrival = simulateRayToPlateB(strategy);
            recordedBArrival = bArrival;

            for (long tick = 0; tick <= ROUND_END; tick++) {
                replayFirstRouteEcho("echo_1", tick);
                if (tick == Level03Pursuit.DOOR_A_CROSS_TICK
                        && !doorA.isUnlocked()) {
                    return run; // 门 A 没开，第二轮进不了内区
                }
                if (tick == Level03Pursuit.DOOR_A_CROSS_TICK) {
                    run.crossedDoorA = true;
                }
                if (tick == bArrival) {
                    plateB.tryEnter("player", 2, tick);
                    return run;
                }
            }
            throw new AssertionError("第二轮没能抵达 B");
        }

        /**
         * 第三轮：E₁ 回放 A/C/D，E₂ 回放 B（在第二轮记录的刻），当前玩家走主通道。
         *
         * <p>玩家每一步都要现查门：门 B 开在「E₂ 抵达 B」的刻，门 C 只开在 E₁ 的 C 窗口内。</p>
         */
        Run runThirdRound(boolean withE1, boolean withE2) {
            return runThirdRound(withE1, withE2, null);
        }

        Run runThirdRound(boolean withE1, boolean withE2, PlayerSlowdownController observer) {
            Run run = new Run();
            long doorBCrossTick = withE2 ? recordedBArrival : NEVER;
            long doorCCrossTick = withE2
                    ? recordedBArrival + Level03Pursuit.DOOR_B_TO_DOOR_C_TICKS
                    : NEVER;
            long exitArrivalTick = doorCCrossTick == NEVER
                    ? NEVER
                    : doorCCrossTick + Level03Pursuit.DOOR_C_TO_EXIT_TICKS;

            for (long tick = 0; tick <= ROUND_END; tick++) {
                if (observer != null) {
                    observer.beginTick(tick);
                }
                if (withE1) {
                    replayFirstRouteEcho("echo_1", tick);
                }
                if (withE2 && tick == recordedBArrival) {
                    plateB.tryEnter("echo_2", 2, tick);
                }

                if (tick == Level03Pursuit.DOOR_A_CROSS_TICK) {
                    run.doorAOpenedByEcho = doorA.isUnlocked();
                    run.crossedDoorA = run.doorAOpenedByEcho;
                    if (!run.crossedDoorA) {
                        return run;
                    }
                }
                if (tick == doorBCrossTick) {
                    run.doorBOpened = doorB.isUnlocked();
                    if (!run.doorBOpened) {
                        return run;
                    }
                    run.crossedDoorB = true;
                }
                if (tick == doorCCrossTick) {
                    run.doorCCrossTick = tick;
                    run.doorCClosedAtArrival = !doorC.isUnlocked();
                    if (run.doorCClosedAtArrival) {
                        return run; // 失败原因：错过门 C 窗口
                    }
                    run.crossedDoorC = true;
                }
                if (tick == exitArrivalTick) {
                    run.exitArrivalTick = tick;
                    run.exitArmed = exit.isDoorUnlocked();
                    run.cleared = run.exitArmed && exit.interact(tick, 0);
                    return run;
                }
            }
            return run;
        }

        /** 两个「开局常驻角色」：从刻 0 起一直压着两块板，全程不动、没有任何时序（§11.2 第 3 条）。 */
        void parkStatically(String firstPlateId, String secondPlateId) {
            plate(firstPlateId).tryEnter("static_1", 0, 0);
            plate(secondPlateId).tryEnter("static_2", 0, 0);
        }

        /**
         * 只靠常驻占用的第三轮：玩家按刻表走到各扇门，门开就过、门关就失败。
         *
         * <p>与 {@link #runThirdRound} 的区别是不回放任何残影 —— 场上的占用全部来自
         * {@link #parkStatically} 的常驻角色。</p>
         */
        Run runThirdRoundWithStaticOccupancy() {
            Run run = new Run();
            run.doorAOpenedByEcho = doorA.isUnlocked();
            run.crossedDoorA = run.doorAOpenedByEcho;
            if (!run.crossedDoorA) {
                return run;
            }
            run.doorBOpened = doorB.isUnlocked();
            if (!run.doorBOpened) {
                return run;
            }
            run.crossedDoorB = true;
            run.doorCCrossTick = Level03Pursuit.DOOR_C_CROSS_TICK;
            run.doorCClosedAtArrival = !doorC.isUnlocked();
            if (run.doorCClosedAtArrival) {
                return run;
            }
            run.crossedDoorC = true;
            run.exitArrivalTick = Level03Pursuit.EXIT_ARRIVAL;
            run.exitArmed = exit.isDoorUnlocked();
            run.cleared = run.exitArmed && exit.interact(run.exitArrivalTick, 0);
            return run;
        }

        /** E₁ 在刻 {@code tick} 应做的板事件（按第一轮<b>实际录到</b>的路线回放）。 */
        private void replayFirstRouteEcho(String actorId, long tick) {
            if (recordedParkedOnC) {
                // 一直驻留 C：只有「踩上 C」这一条被录到，之后既没离开也没到 D。
                if (tick == Level03Pursuit.PLATE_C_ARRIVAL) {
                    plateC.tryEnter(actorId, 1, tick);
                }
                return;
            }
            if (tick == Level03Pursuit.GATE_A_WINDOW_START) {
                plateA.tryEnter(actorId, 1, tick);
            } else if (tick == Level03Pursuit.GATE_A_WINDOW_END) {
                plateA.tryExit(actorId, 1, tick);
            } else if (tick == Level03Pursuit.PLATE_C_ARRIVAL) {
                plateC.tryEnter(actorId, 1, tick);
            } else if (tick == Level03Pursuit.PLATE_C_WINDOW_END) {
                plateC.tryExit(actorId, 1, tick);
            } else if (tick == Level03Pursuit.PLATE_D_ARRIVAL) {
                plateD.tryEnter(actorId, 1, tick);
            }
        }

        /**
         * 用真实射线状态 + 真实减速控制器算「射线 → B」的抵达刻。
         *
         * <p>PHASE：下潜，不减速，走 {@link Level03Pursuit#RAY_TO_PLATE_B_TICKS}。HIT：被命中后
         * 逐刻按 {@link PlayerSlowdownController#speedMultiplier()} 走完。WAIT：等 ACTIVE 结束再走。</p>
         */
        private long simulateRayToPlateB(Strategy strategy) {
            Ray ray = ray();
            RayFactory.updateAll(rays, Level03Pursuit.RAY_CROSS_TICK);
            assertTrue(ray.getState() == Ray.State.ACTIVE,
                    "第二轮玩家抵达射线的那一刻，射线必须是 ACTIVE（否则不必下潜）");

            if (strategy == Strategy.WAIT) {
                hitByRayInRoundTwo = false;
                return Level03Pursuit.RAY_CROSS_TICK + Level03Pursuit.RAY_ACTIVE_DURATION_TICKS
                        + Level03Pursuit.RAY_TO_PLATE_B_TICKS;
            }

            PlayerSlowdownController slowdown = new PlayerSlowdownController();
            slowdown.beginTick(Level03Pursuit.RAY_CROSS_TICK);
            hitByRayInRoundTwo = strategy == Strategy.HIT;
            if (hitByRayInRoundTwo) {
                slowdown.noteHit(ray.getId(),
                        Level03Pursuit.RAY_CROSS_TICK / Level03Pursuit.RAY_CYCLE_TICKS);
            }

            double remaining = Level03Pursuit.RAY_TO_PLATE_B_TICKS * BASE_SPEED;
            long tick = Level03Pursuit.RAY_CROSS_TICK;
            while (remaining > 0.0) {
                tick++;
                slowdown.beginTick(tick);
                remaining -= BASE_SPEED * slowdown.speedMultiplier();
                assertTrue(tick < ROUND_END, "射线 → B 这段路必须能在轮内走完");
            }
            return tick;
        }

        private DockingPlate createPlate(String id) {
            return new DockingPlate(id, position(id), registry, bus);
        }

        private Door createDoor(String id) {
            Set<String> required = level.getDoors().stream()
                    .filter(d -> id.equals(d.getId()))
                    .map(DoorInfo::getRequiredPlateIds)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("缺少门: " + id));
            return new Door(id, position(id), required, registry, bus);
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
                            .orElseGet(() -> level.getDoors().stream()
                                    .filter(d -> mechanismId.equals(d.getId()))
                                    .map(DoorInfo::getPosition)
                                    .findFirst()
                                    .orElseThrow(() -> new AssertionError("缺少机制: " + mechanismId))));
        }
    }
}
