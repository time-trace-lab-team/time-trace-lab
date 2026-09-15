package org.example.timeloop.app;

import org.example.timeloop.core.GamePhase;
import org.example.timeloop.core.input.InputIntent;
import org.example.timeloop.level.Level01Footsteps;
import org.example.timeloop.level.Level02Corridor;
import org.example.timeloop.level.Level03Pursuit;
import org.example.timeloop.level.model.LevelData;
import org.example.timeloop.render.RenderViews;
import org.example.timeloop.replay.TickContext;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 关卡流（集成层，纯 Java，不依赖 JavaFX）：持有<b>当前载入的是哪一关</b>，并在且仅在
 * 当前关<b>通关</b>（{@link GamePhase#RESULT}）时切到下一关 —— 第一关 → 第二关 → 第三关。
 *
 * <p><b>切换守卫</b>（项目方原话：第一关通关后跳转到第二关；第一关没通过就不许跳转）：</p>
 * <ul>
 *   <li>{@link #switchToNextLevelIfCleared()} 只认「当前关 + {@link GamePhase#RESULT}」：
 *       {@code LEVEL_01 → LEVEL_02}、{@code LEVEL_02 → LEVEL_03}；</li>
 *   <li>{@link GamePhase#FAILED}（轮次耗尽）<b>不</b>满足条件 → 玩家留在当前关，
 *       既有的重开 / 返回行为完全不变；</li>
 *   <li>切换后 {@code activeLevel} 前进一关，条件不再成立 → <b>幂等</b>：
 *       第三关是最后一关，清关后不会去加载不存在的第四关（停在第三关的 RESULT）；</li>
 *   <li>切关前必须先 {@code stop()} 掉旧关（停止推进 + 释放事件消费），再装配并 {@code start()} 新关。</li>
 * </ul>
 *
 * <p><b>生命周期交接</b>：{@link #tick} 只把逻辑刻交给<b>当前那一关</b>，因此不存在「两关同时推进」，
 * 也不存在第二个 AnimationTimer；{@link #restart} 只重置<b>当前</b>关 ——
 * 第三关失败或通关后重开都回到第三关第 1 轮，绝不退回第二关。</p>
 */
final class LevelFlow {

    /** 关卡标识（应用层用它决定标题 / 关卡数据）。 */
    enum LevelId {
        LEVEL_01("第一关：留下的脚步"),
        LEVEL_02("第二关：闸链"),
        LEVEL_03("第三关：追赶过去");

        private final String title;

        LevelId(String title) {
            this.title = title;
        }

        String title() {
            return title;
        }
    }

    private LevelId activeLevel = LevelId.LEVEL_01;
    private final Level01Assembly level01 = new Level01Assembly();
    private Level02Assembly level02;
    private Level03Assembly level03;

    /** 启动第一关（游戏入口）。 */
    void start() {
        level01.start();
    }

    /** 当前载入的关卡。 */
    LevelId activeLevel() {
        return activeLevel;
    }

    /** 当前关卡的关卡数据（画布网格 / 世界尺寸 / 出生点从这里推导）。 */
    LevelData activeLevelData() {
        return switch (activeLevel) {
            case LEVEL_01 -> Level01Footsteps.build();
            case LEVEL_02 -> Level02Corridor.build();
            case LEVEL_03 -> Level03Pursuit.build();
        };
    }

    /**
     * 当前关通关后装载下一关；返回本次调用是否发生了切关。
     *
     * <p>只认 {@link GamePhase#RESULT}：{@link GamePhase#FAILED} 一律不切换（没通过就不许跳转）；
     * 第三关通关后没有第四关可切 → 返回 {@code false}（幂等）。</p>
     */
    boolean switchToNextLevelIfCleared() {
        return switch (activeLevel) {
            case LEVEL_01 -> {
                if (level01.phase() != GamePhase.RESULT) {
                    yield false;
                }
                // 生命周期交接：先把第一关停掉（不再推进、不再消费事件），再装配第二关。
                level01.stop();
                level02 = new Level02Assembly();
                activeLevel = LevelId.LEVEL_02;
                level02.start();
                yield true;
            }
            case LEVEL_02 -> {
                if (level02 == null || level02.phase() != GamePhase.RESULT) {
                    yield false;
                }
                level02.stop();
                level03 = new Level03Assembly();
                activeLevel = LevelId.LEVEL_03;
                level03.start();
                yield true;
            }
            // 第三关是最后一关：通关后停在原地，不加载不存在的第四关。
            case LEVEL_03 -> false;
        };
    }

    /** 推进一个逻辑刻：只交给当前关卡（切关后旧关不会再收到推进）。 */
    void tick(InputIntent input) {
        Objects.requireNonNull(input, "input");
        switch (activeLevel) {
            case LEVEL_01 -> level01.tick(input);
            case LEVEL_02 -> requireLevel02().tick(input);
            case LEVEL_03 -> requireLevel03().tick(input);
        }
    }

    /** 整局重开：只重置<b>当前</b>关（第三关失败 / 通关后重开仍回到第三关第 1 轮）。 */
    void restart() {
        switch (activeLevel) {
            case LEVEL_01 -> level01.restart();
            case LEVEL_02 -> requireLevel02().restart();
            case LEVEL_03 -> requireLevel03().restart();
        }
    }

    boolean isPlaying() {
        return switch (activeLevel) {
            case LEVEL_01 -> level01.isPlaying();
            case LEVEL_02 -> requireLevel02().isPlaying();
            case LEVEL_03 -> requireLevel03().isPlaying();
        };
    }

    /** 当前关卡的结算只读投影：仅 RESULT/FAILED 之后有值，供终局弹窗读取。 */
    java.util.Optional<org.example.timeloop.replay.LevelResult> activeResult() {
        return switch (activeLevel) {
            case LEVEL_01 -> level01.result();
            case LEVEL_02 -> requireLevel02().result();
            case LEVEL_03 -> requireLevel03().result();
        };
    }

    boolean isFinalPhase() {
        return switch (activeLevel) {
            case LEVEL_01 -> level01.isFinalPhase();
            case LEVEL_02 -> requireLevel02().isFinalPhase();
            case LEVEL_03 -> requireLevel03().isFinalPhase();
        };
    }

    GamePhase phase() {
        return switch (activeLevel) {
            case LEVEL_01 -> level01.phase();
            case LEVEL_02 -> requireLevel02().phase();
            case LEVEL_03 -> requireLevel03().phase();
        };
    }

    TickContext hudContext() {
        return switch (activeLevel) {
            case LEVEL_01 -> level01.hudContext();
            case LEVEL_02 -> requireLevel02().hudContext();
            case LEVEL_03 -> requireLevel03().hudContext();
        };
    }

    RenderViews.Frame renderViews() {
        return switch (activeLevel) {
            case LEVEL_01 -> level01.renderViews();
            case LEVEL_02 -> requireLevel02().renderViews();
            case LEVEL_03 -> requireLevel03().renderViews();
        };
    }

    List<RenderViews.PathNodeMarker> pathNodeMarkers() {
        return switch (activeLevel) {
            case LEVEL_01 -> level01.pathNodeMarkers();
            case LEVEL_02 -> requireLevel02().pathNodeMarkers();
            case LEVEL_03 -> requireLevel03().pathNodeMarkers();
        };
    }

    /**
     * 当前关卡的目标提示（一句话）。
     *
     * <p>地图与机关换关后，提示文本也必须换主人：这里取的是<b>当前关卡自己的只读目标投影</b> ——
     * 第一关 {@code ui.ObjectiveViewModel}、第二关 {@code ui.Level02ObjectiveViewModel}、
     * 第三关 {@code ui.Level03ObjectiveViewModel} 的 {@code text()}，
     * 不会残留上一关的文本。</p>
     */
    String objectiveText() {
        return switch (activeLevel) {
            case LEVEL_01 -> level01.objectiveView().text();
            case LEVEL_02 -> requireLevel02().objectiveView().text();
            case LEVEL_03 -> requireLevel03().objectiveView().text();
        };
    }

    /** 卸载当前关卡（场景退出）：停止推进并释放事件消费。 */
    void cleanup() {
        switch (activeLevel) {
            case LEVEL_01 -> level01.stop();
            case LEVEL_02 -> {
                if (level02 != null) {
                    level02.stop();
                }
            }
            case LEVEL_03 -> {
                if (level03 != null) {
                    level03.stop();
                }
            }
        }
    }

    /** 第一关装配（包内可见：集成测试用它核对切关后的停止状态）。 */
    Level01Assembly level01() {
        return level01;
    }

    /** 第二关装配；尚未装载时为空（包内可见：集成测试用）。 */
    Optional<Level02Assembly> level02() {
        return Optional.ofNullable(level02);
    }

    /** 第三关装配；尚未装载时为空（包内可见：集成测试用）。 */
    Optional<Level03Assembly> level03() {
        return Optional.ofNullable(level03);
    }

    /** 当前关是第二关但装配缺失（只有非法状态才会走到，用于给出可诊断的失败而不是 NPE）。 */
    private Level02Assembly requireLevel02() {
        if (level02 == null) {
            throw new IllegalStateException("当前关是 LEVEL_02 但第二关装配尚未装载");
        }
        return level02;
    }

    /** 当前关是第三关但装配缺失（同上）。 */
    private Level03Assembly requireLevel03() {
        if (level03 == null) {
            throw new IllegalStateException("当前关是 LEVEL_03 但第三关装配尚未装载");
        }
        return level03;
    }
}
