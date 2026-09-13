package org.example.timeloop.app;

import org.example.timeloop.core.Direction;
import org.example.timeloop.core.GamePhase;
import org.example.timeloop.core.input.InputIntent;
import org.example.timeloop.core.input.LogicalKey;
import org.example.timeloop.replay.TickContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「失败可以，卡死不行」的回归测试。
 *
 * <p>现场症状（项目方实机）：没能在第 2 轮通关，第 3 轮读秒归零后 HUD 仍显示「第 3 / 3 轮」，
 * 角色再也不动。无头复现确认：轮次切换本身是好的（每轮都交回 {@code PLAYING} 且角色能走），
 * 真正的问题在终局阶段 —— {@code FAILED} / {@code RESULT} 下 {@code tick()} 直接返回，
 * 而集成层当时<b>没有任何重开入口</b>，玩家只能停在死画面里。</p>
 *
 * <p>因此本测试锁两件事：</p>
 * <ol>
 *   <li>三个轮次都能正常开始（{@code PLAYING}、{@code roundTick=0}），第 3 轮角色确实能移动；</li>
 *   <li>走到 {@code FAILED} 之后，{@link Level01Assembly#restart()} 必须把会话拉回第 1 轮
 *       {@code PLAYING}，并且角色重新能动。</li>
 * </ol>
 *
 * <p>本测试不关心输赢：第 3 轮走不完、进入 {@code FAILED} 都是允许的。</p>
 */
class Level01AssemblyRoundThreeMovementTest {

    private static final long TICKS_PER_ROUND = 960L;
    /** 出生点格 (10,2) 的世界坐标；初始朝向 DOWN 且 DOWN 是合法出口。 */
    private static final double SPAWN_Y = 120.0;

    @Test
    void playerStillMovesWhenRoundThreeStarts() {
        Level01Assembly assembly = new Level01Assembly();
        StringBuilder log = new StringBuilder();
        try {
            assembly.start();
            assertTrue(assembly.isPlaying(), "start() 之后应处于 PLAYING");

            InputIntent holdDown = hold(Direction.DOWN);
            int round = assembly.hudContext().currentRound();
            log.append("第 ").append(round).append(" 轮开始: phase=").append(assembly.phase())
                    .append(" pos=").append(position(assembly)).append('\n');

            boolean reachedRoundThree = false;

            for (long i = 0; i < TICKS_PER_ROUND * 3L + 300L; i++) {
                TickContext context = assembly.hudContext();
                if (context.currentRound() != round) {
                    round = context.currentRound();
                    log.append(">>> 进入第 ").append(round).append(" 轮: phase=").append(assembly.phase())
                            .append(" roundTick=").append(context.roundTick())
                            .append(" pos=").append(position(assembly)).append('\n');

                    // 普通轮末事务必须把阶段交回 PLAYING；停在 READY 就是"卡死"。
                    assertEquals(GamePhase.PLAYING, assembly.phase(),
                            "第 " + round + " 轮开始时应处于 PLAYING（停在 READY 即为轮末事务没有复位）");
                    assertEquals(0L, context.roundTick(),
                            "第 " + round + " 轮开始时 roundTick 应归零");

                    if (round == 3) {
                        reachedRoundThree = true;
                    }
                }
                if (!assembly.isPlaying()) {
                    log.append("第 ").append(round).append(" 轮 t=").append(context.roundTick())
                            .append(" 停止推进: phase=").append(assembly.phase()).append('\n');
                    break;
                }
                assembly.tick(holdDown);
            }

            System.out.println(log);

            assertTrue(reachedRoundThree, "本测试必须真的走到第 3 轮，否则判定不了卡死问题");
            // 第 3 轮里按住 DOWN（出生点的合法出口）跑了一整轮：位置必须变过。
            assertNotEquals(SPAWN_Y, playerY(assembly),
                    "第 3 轮按住 DOWN 后 y 仍停在出生点 y=120，说明角色完全无法移动");
        } finally {
            assembly.cleanup();
        }
    }

    @Test
    void failedRunCanBeRestartedAndThePlayerMovesAgain() {
        Level01Assembly assembly = new Level01Assembly();
        try {
            assembly.start();
            InputIntent holdDown = hold(Direction.DOWN);

            // 一直不走机关：三整轮必然以 FAILED 收场（本测试要的正是这个终局态）。
            for (long i = 0; i < TICKS_PER_ROUND * 3L + 300L && !assembly.isFinalPhase(); i++) {
                if (!assembly.isPlaying()) {
                    break;
                }
                assembly.tick(holdDown);
            }

            assertEquals(GamePhase.FAILED, assembly.phase(),
                    "三整轮未通关后应进入 FAILED");
            assertTrue(assembly.isFinalPhase(), "FAILED 应被判定为终局阶段");
            assertFalse(assembly.isPlaying(), "终局阶段不再是 PLAYING");
            double frozenY = playerY(assembly);

            // 终局阶段 tick() 直接返回：这正是"不能动"的表现，所以必须有重开入口。
            assembly.tick(holdDown);
            assertEquals(frozenY, playerY(assembly), "前提：终局阶段角色确实不再移动");

            // 重开：必须回到第 1 轮 PLAYING，并且角色重新能动。
            assembly.restart();
            assertEquals(GamePhase.PLAYING, assembly.phase(), "重开后应回到 PLAYING");
            assertEquals(1, assembly.hudContext().currentRound(), "重开后应回到第 1 轮");
            assertEquals(0L, assembly.hudContext().roundTick(), "重开后 roundTick 应归零");
            assertEquals(SPAWN_Y, playerY(assembly), "重开后角色应回到出生点");

            for (int i = 0; i < 60; i++) {
                assembly.tick(holdDown);
            }
            assertNotEquals(SPAWN_Y, playerY(assembly),
                    "重开后按住 DOWN 角色必须能重新移动（失败可以，卡死不行）");
        } finally {
            assembly.cleanup();
        }
    }

    private static InputIntent hold(Direction direction) {
        LogicalKey key = switch (direction) {
            case UP -> LogicalKey.DIR_UP;
            case DOWN -> LogicalKey.DIR_DOWN;
            case LEFT -> LogicalKey.DIR_LEFT;
            case RIGHT -> LogicalKey.DIR_RIGHT;
        };
        return new InputIntent(0L, Set.of(key), Set.of(), Set.of(key), List.of(direction));
    }

    private static String position(Level01Assembly assembly) {
        var player = assembly.renderViews().player();
        return String.format("(%.1f, %.1f)", player.x(), player.y());
    }

    private static double playerY(Level01Assembly assembly) {
        return assembly.renderViews().player().y();
    }
}
