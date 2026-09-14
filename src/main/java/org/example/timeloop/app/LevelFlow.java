package org.example.timeloop.app;

import org.example.timeloop.core.GamePhase;
import org.example.timeloop.core.input.InputIntent;
import org.example.timeloop.level.Level01Footsteps;
import org.example.timeloop.level.Level02Corridor;
import org.example.timeloop.level.model.LevelData;
import org.example.timeloop.render.RenderViews;
import org.example.timeloop.replay.TickContext;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 关卡流（集成层，纯 Java，不依赖 JavaFX）：持有<b>当前载入的是哪一关</b>，并在且仅在
 * 第一关<b>通关</b>（{@link GamePhase#RESULT}）时切到第二关。
 *
 * <p><b>切换守卫</b>（项目方原话：第一关通关后跳转到第二关；第一关没通过就不许跳转）：</p>
 * <ul>
 *   <li>{@link #switchToNextLevelIfCleared()} 的唯一条件是
 *       {@code activeLevel == LEVEL_01 && level01.phase() == GamePhase.RESULT}；</li>
 *   <li>{@link GamePhase#FAILED}（轮次耗尽）<b>不</b>满足条件 → 玩家留在第一关，
 *       既有的重开 / 返回行为完全不变；</li>
 *   <li>切换后 {@code activeLevel} 变成 {@code LEVEL_02}，条件不再成立 → <b>幂等</b>：
 *       第二关清关后不会去加载不存在的第三关（停在第二关的 RESULT）。</li>
 * </ul>
 *
 * <p><b>生命周期交接</b>：切关时先 {@link Level01Assembly#stop()}（停止推进 + 释放事件消费），
 * 再装配并启动第二关；{@link #tick} 只把逻辑刻交给<b>当前那一关</b>，
 * 因此不存在「两关同时推进」，也不存在第二个 AnimationTimer。</p>
 */
final class LevelFlow {

    /** 关卡标识（应用层用它决定标题 / 关卡数据）。 */
    enum LevelId {
        LEVEL_01("第一关：留下的脚步"),
        LEVEL_02("第二关：闸链");

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
        return activeLevel == LevelId.LEVEL_01 ? Level01Footsteps.build() : Level02Corridor.build();
    }

    /**
     * 第一关通关后装载第二关；返回本次调用是否发生了切关。
     *
     * <p>只认 {@link GamePhase#RESULT}：{@link GamePhase#FAILED} 一律不切换（第一关没通过就不许跳转）。</p>
     */
    boolean switchToNextLevelIfCleared() {
        if (activeLevel != LevelId.LEVEL_01 || level01.phase() != GamePhase.RESULT) {
            return false;
        }
        // 生命周期交接：先把第一关停掉（不再推进、不再消费事件），再装配第二关。
        level01.stop();
        level02 = new Level02Assembly();
        activeLevel = LevelId.LEVEL_02;
        level02.start();
        return true;
    }

    /** 推进一个逻辑刻：只交给当前关卡（切关后第一关不会再收到推进）。 */
    void tick(InputIntent input) {
        Objects.requireNonNull(input, "input");
        if (activeLevel == LevelId.LEVEL_01) {
            level01.tick(input);
        } else {
            level02.tick(input);
        }
    }

    void restart() {
        if (activeLevel == LevelId.LEVEL_01) {
            level01.restart();
        } else {
            level02.restart();
        }
    }

    boolean isPlaying() {
        return activeLevel == LevelId.LEVEL_01 ? level01.isPlaying() : level02.isPlaying();
    }

    /** 当前关卡的结算只读投影：仅 RESULT/FAILED 之后有值，供终局弹窗读取。 */
    java.util.Optional<org.example.timeloop.replay.LevelResult> activeResult() {
        return activeLevel == LevelId.LEVEL_01 ? level01.result() : level02.result();
    }

    boolean isFinalPhase() {
        return activeLevel == LevelId.LEVEL_01 ? level01.isFinalPhase() : level02.isFinalPhase();
    }

    GamePhase phase() {
        return activeLevel == LevelId.LEVEL_01 ? level01.phase() : level02.phase();
    }

    TickContext hudContext() {
        return activeLevel == LevelId.LEVEL_01 ? level01.hudContext() : level02.hudContext();
    }

    RenderViews.Frame renderViews() {
        return activeLevel == LevelId.LEVEL_01 ? level01.renderViews() : level02.renderViews();
    }

    List<RenderViews.PathNodeMarker> pathNodeMarkers() {
        return activeLevel == LevelId.LEVEL_01 ? level01.pathNodeMarkers() : level02.pathNodeMarkers();
    }

    /**
     * 当前关卡的目标提示（一句话）。
     *
     * <p>地图与机关从第一关换成第二关后，提示文本也必须换主人：这里取的是
     * <b>当前关卡自己的只读目标投影</b> —— 第二关为
     * {@code Level02Assembly.objectiveView()}（{@code ui.Level02ObjectiveViewModel}）的 {@code text()}，
     * 不是第一关 {@code ui.ObjectiveViewModel} 的残留文本。</p>
     */
    String objectiveText() {
        return activeLevel == LevelId.LEVEL_01
                ? level01.objectiveView().text()
                : level02.objectiveView().text();
    }

    /** 卸载当前关卡（场景退出）：停止推进并释放事件消费。 */
    void cleanup() {
        if (activeLevel == LevelId.LEVEL_01) {
            level01.stop();
        } else if (level02 != null) {
            level02.stop();
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
}
