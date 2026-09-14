package org.example.timeloop.ui;

/**
 * 屏幕目标提示的不可变投影（开发三）。
 *
 * <p>存在的理由：第一关的两块驻留板画得完全一样，而"该压哪块、残影压着哪块"只体现在
 * <b>板有没有亮</b>上；玩家站在被残影占用的板上时既不会驻留、也不会有任何反馈，
 * 于是出现"我明明站在板上按 E 却没反应"的困惑。本类把"谁压着哪块板 + 开关开没开"
 * 变成一句人话，由 HUD 常驻显示。</p>
 *
 * <p>L01-GATE-MERGE 后右板改称<b>开关</b>（{@code role=switch}）：踩上即开启、离开不关闭，
 * 保持到本轮结束。因此 {@code rightHeldByPlayer} / {@code rightHeldByEcho} 表示的是
 * 「开关是否已开启（本轮锁存 ON）」，<b>不是</b>「此刻有没有人站在上面」。字段与结构按卡 §2.6 冻结不变。</p>
 *
 * <p>只做只读投影：不持有计时器、不推进任何游戏状态，也不参与胜负判定。</p>
 *
 * @param currentRound      当前轮次（1..maxRounds）
 * @param maxRounds         本关最大轮次
 * @param leftHeldByPlayer  左驻留板是否被当前玩家占用
 * @param leftHeldByEcho    左驻留板是否被残影占用
 * @param rightHeldByPlayer 开关是否已由当前玩家开启（本轮锁存 ON）
 * @param rightHeldByEcho   开关是否已由残影开启（本轮锁存 ON）
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
        // 注意：leftHeldByPlayer && leftHeldByEcho 是**合法**状态 —— 一块驻留板可以被多个 actor
        // 同时占用（L03-DEV3 裁决）。旧版本在这里抛异常，等于把「模型做不到」当成了「规则不允许」，
        // 而底层 DockingPlate 当时是静默丢弃第二人，两边一起把缺陷藏住了。
    }

    /**
     * 一句话目标提示。
     *
     * <p>判定顺序刻意是"门 → 开关已开且有板被压 → 只差开关 → 只差左板 → 第 1 轮 → 兜底"：
     * 门一旦解锁，玩家唯一的动作就是去闸门按 E。</p>
     */
    public String text() {
        boolean switchOn = rightHeldByPlayer || rightHeldByEcho;
        if (doorUnlocked) {
            return "门已解锁：到闸门（开关正上方一格）按 E 通关";
        }
        if (switchOn && leftHeldByEcho) {
            return "开关已开启、左板已被残影压住：去闸门按 E";
        }
        if (switchOn) {
            return leftHeldByPlayer
                    ? "开关已开启：保持不动，等本轮结束让残影记住这条路线"
                    : "开关已开启：去左驻留板停住，把「压住左板」留给下一轮";
        }
        if (leftHeldByEcho) {
            return "残影已压住左板：你去踩一下开关（踩上即开启，离开也不会关）";
        }
        if (currentRound == 1) {
            return leftHeldByPlayer
                    ? "已压住左驻留板：保持不动，等本轮结束让残影记住这条路线"
                    : "第 1 轮：先踩开关（踩上即开启），再去左驻留板停住";
        }
        return leftHeldByPlayer
                ? "你压着左板，但开关没开，门不会开：按 R 重开"
                : "开关没开、左板也空着：按 R 重开后，第 1 轮先踩开关再压左板";
    }
}
