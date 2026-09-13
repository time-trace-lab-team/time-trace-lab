package org.example.timeloop.ui;

/**
 * 屏幕目标提示的不可变投影（开发三）。
 *
 * <p>存在的理由：第一关的两块驻留板画得完全一样，而"该压哪块、残影压着哪块"只体现在
 * <b>板有没有亮</b>上；玩家站在被残影占用的板上时既不会驻留、也不会有任何反馈，
 * 于是出现"我明明站在板上按 E 却没反应"的困惑。本类把"谁压着哪块板 + 门开没开"
 * 变成一句人话，由 HUD 常驻显示。</p>
 *
 * <p>只做只读投影：不持有计时器、不推进任何游戏状态，也不参与胜负判定。</p>
 *
 * @param currentRound      当前轮次（1..maxRounds）
 * @param maxRounds         本关最大轮次
 * @param leftHeldByPlayer  左驻留板是否被当前玩家占用
 * @param leftHeldByEcho    左驻留板是否被残影占用
 * @param rightHeldByPlayer 右驻留板是否被当前玩家占用
 * @param rightHeldByEcho   右驻留板是否被残影占用
 * @param doorUnlocked      门是否已解锁（出口终端的锁存状态）
 */
public record ObjectiveViewModel(int currentRound,
                                 int maxRounds,
                                 boolean leftHeldByPlayer,
                                 boolean leftHeldByEcho,
                                 boolean rightHeldByPlayer,
                                 boolean rightHeldByEcho,
                                 boolean doorUnlocked) {

    public ObjectiveViewModel {
        if (maxRounds < 1) {
            throw new IllegalArgumentException("maxRounds 必须 >= 1");
        }
        if (currentRound < 1 || currentRound > maxRounds) {
            throw new IllegalArgumentException(
                    "currentRound 必须在 [1, " + maxRounds + "]");
        }
        if (leftHeldByPlayer && leftHeldByEcho) {
            throw new IllegalArgumentException("左驻留板不可能同时被玩家与残影占用");
        }
        if (rightHeldByPlayer && rightHeldByEcho) {
            throw new IllegalArgumentException("右驻留板不可能同时被玩家与残影占用");
        }
    }

    /**
     * 一句话目标提示。
     *
     * <p>判定顺序刻意是"门 → 右板被残影占 → 残影压左板 → 第 1 轮 → 兜底"：
     * 门一旦解锁，玩家唯一的动作就是去按 E；右板被残影占则本轮已无解，应引导重开。</p>
     */
    public String text() {
        if (doorUnlocked) {
            return "门已解锁：站到右驻留板上按 E 通关";
        }
        if (rightHeldByEcho) {
            return "残影占着右驻留板，本轮无解：按 R 重开";
        }
        if (leftHeldByEcho && rightHeldByPlayer) {
            return "两块板都已压住，等门解锁后按 E";
        }
        if (leftHeldByEcho) {
            return "残影已压住左板：你去右驻留板站住，门一解锁就按 E";
        }
        if (currentRound == 1) {
            return leftHeldByPlayer
                    ? "已压住左驻留板：保持不动，等本轮结束让残影记住这条路线"
                    : "第 1 轮：走到左驻留板并停住（松开方向键）";
        }
        if (leftHeldByPlayer) {
            return "你压着左板，但残影没压右板，门不会开：按 R 重开";
        }
        return "左驻留板空着（残影没留在左板）：按 R 重开后，第 1 轮务必站上左板";
    }
}
