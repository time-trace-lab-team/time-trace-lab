package org.example.timeloop.ui;

/**
 * 第三关《追赶过去》的目标提示投影（开发三，卡 {@code L03-DEV3} §三 / 设定书 §9.2 §9.4）· <b>重排 v3</b>。
 *
 * <p>与 {@link Level02ObjectiveViewModel}、{@link ObjectiveViewModel} <b>并存</b>：三者都是只读
 * record，互不改签名，PM 侧接线时按当前关卡投影对应类型即可。</p>
 *
 * <p>v3 的判定链：门 A ← A 板；门 C ← C 板；门 B ← B 板；<b>出口闸 ← {S₂, S₃, K}</b>。
 * 本关要表达的四件事（设定书 §9.4）：</p>
 * <ol>
 *   <li><b>一级</b>：出口要 S₂ + S₃ + K 三块同时成立；三道门由过去开启；</li>
 *   <li><b>二级</b>：第二轮踩上 S₂ 与 B 板的时刻，会决定第三轮门 B 何时打开；</li>
 *   <li><b>三级</b>：三条路线（控制线 {@code A → C → K}、E₂ 支路 {@code J → S₂ → 射线 → 门C → B}、
 *       E₃ 主线 {@code J → 门B → S₃ → 门C → 出口}）及其关键时间节点；</li>
 *   <li>J 分岔引导（向南 E₂ 支路 / 向北 E₃ 主线）与 {@code E₁：最后有效轮}（设定书 §9.2）。</li>
 * </ol>
 *
 * <p>提示只显示信息：{@link #text()} 及其它 accessor 都是纯函数，不推进、不暂停、不修改任何时钟
 * （设定书 §9.4 末条）。</p>
 *
 * <p><b>校验只保留真命题</b>：本类曾写过三条「板与门的因果组合」校验（门 C 必须由第一残影在第三轮打开、
 * 出口供能只可能来自第一残影驻留 D 板…），它们在真实玩法里<b>每帧抛异常</b>并让 JavaFX 直接报错。
 * 教训：「某个时刻的因果」不是「状态组合的合法性」。v3 这里只校验轮次范围与「最后有效轮」，
 * 其余任意布尔组合都必须能给出人话 —— 是否可达由装配与刻表决定，不归只读投影管。</p>
 *
 * @param currentRound        当前轮次（1..maxRounds）
 * @param maxRounds           最大轮次（本关 = 3）
 * @param firstEchoFinalRound 第一残影是否已进入最后有效轮（= 当前已是最后一轮，设定书 §9.2）
 * @param doorAOpen           门 A <b>此刻</b>是否开着（E₁ 正压着 A 板）。它与 {@code inEcho2Branch}
 *                            <b>互相独立</b>：门只在一小段窗口里开着，而玩家完全可以已经身处支路、
 *                            门 A 却早已回锁 —— 这是本关的<b>正常</b>局面
 * @param inEcho2Branch       当前玩家是否已进入 E₂ 支路（J 以南 + 通往射线的走廊，会接触射线）
 * @param rayActive           射线此刻是否 ACTIVE（该按下潜了）
 * @param switchS2On          S₂ 开关是否已兑现（踩上即锁存到轮末）
 * @param switchS3On          S₃ 开关是否已兑现（在门 B 后面的开关室里，第三轮先穿门 B）
 * @param plateKHeld          K 板此刻是否被压住（出口闸的第三个条件）
 * @param doorBOpen           门 B 是否开着（E₂ 正压住 B 板）
 * @param doorCOpen           门 C 是否开着（E₁ 正压着 C 板，窗口 [600, 1056)）
 * @param exitUnlocked        出口闸是否已解锁（= 出口终端已被武装；S₂ + S₃ + K 同时成立）
 */
public record Level03ObjectiveViewModel(int currentRound,
                                        int maxRounds,
                                        boolean firstEchoFinalRound,
                                        boolean doorAOpen,
                                        boolean inEcho2Branch,
                                        boolean rayActive,
                                        boolean switchS2On,
                                        boolean switchS3On,
                                        boolean plateKHeld,
                                        boolean doorBOpen,
                                        boolean doorCOpen,
                                        boolean exitUnlocked) {

    /**
     * 只校验<b>真</b>不变量：轮次范围，以及「最后有效轮」只可能出现在最后一轮。
     *
     * <p>刻意不校验任何「板与门之间的因果组合」：第 1 轮玩家踩 C 板（门 C 开、门 B 无人压、轮次 1）
     * 本就是官方解第一步，踩 S₂/S₃ 开关时轮次也可能是 2（E₂ 路过）—— 这些都是<b>合法</b>局面。</p>
     */
    public Level03ObjectiveViewModel {
        if (maxRounds < 1) {
            throw new IllegalArgumentException("maxRounds 必须 >= 1");
        }
        if (currentRound < 1 || currentRound > maxRounds) {
            throw new IllegalArgumentException("currentRound 必须在 [1, " + maxRounds + "]");
        }
        if (firstEchoFinalRound && currentRound != maxRounds) {
            throw new IllegalArgumentException(
                    "「E₁ 最后有效轮」只可能出现在最后一轮: currentRound=" + currentRound
                            + ", maxRounds=" + maxRounds);
        }
    }

    /**
     * 一句话行动提示。判定顺序：已解锁 → 在 E₂ 支路（射线在下潜窗口内）→ 第 1 轮录制
     * → S₃ 已踩（折回门 C）→ 第 2 轮录制 → 门 C 开着 → 门 B 开着 → 门 A 开着 → 兜底。
     */
    public String text() {
        if (exitUnlocked) {
            return "出口已供能：到出口旁按 E 通关";
        }
        if (inEcho2Branch) {
            return rayActive
                    ? "射线激活：按 Space 相位下潜穿过去（下潜期间不受射线判定）"
                    : "沿支路往南踩 S₂ 开关 → 走廊往东，见预警就按 Space 下潜，然后穿门 C 驻留 B 板到轮末";
        }
        if (currentRound == 1) {
            return "第 1 轮：依次踩 A → C → K；A 驻留 2 格、C 驻留 19 格、K 压到轮末 —— "
                    + "这三段窗口就是后面两轮的时间资源";
        }
        if (switchS3On) {
            return "S₃ 已锁存：折回门 B，沿第 20 列直下穿门 C，再到东南回环尽头的出口等 K 板供能";
        }
        if (currentRound == 2) {
            return doorAOpen
                    ? "门 A 开了：穿过去，在 J 向南踩 S₂，再过射线、门 C，驻留 B 板到轮末"
                    : "等残影 E₁ 压住 A 板开门（它第一轮踩过 A），门一开就穿进内区";
        }
        if (doorCOpen) {
            return "门 C 开着：趁 E₁ 的 C 窗口穿过去，再到东南回环尽头的出口等供能";
        }
        if (doorBOpen) {
            return "门 B 开了：进开关室踩 S₃（开关会锁存到轮末），再折回沿第 20 列下到门 C";
        }
        if (doorAOpen) {
            return "门 A 开了：进内区，在 J 向北走到门 B 外等 E₂ 开门";
        }
        return "等 E₁ 压住 A 板开门 A（它在刻 336 踩上、刻 384 离开；门一开就穿进内区）";
    }

    /** 一级提示（设定书 §9.4）：出口的供能来源与三道门由谁开。 */
    public String tierOne() {
        return "出口闸要 S₂ + S₃ + K 三块同时成立；门 A / 门 C / 门 B 各由过去的那一轮开启。";
    }

    /** 二级提示（设定书 §9.4）：S₂ 与 B 的到达时刻决定第三轮门 B 何时开。 */
    public String tierTwo() {
        return "第二轮踩上 S₂ 与 B 板的时刻，会决定第三轮门 B 何时打开。";
    }

    /** 三级提示（设定书 §9.4）：三条路线与关键时间节点（刻数以共享刻表为准）。 */
    public String tierThreeRoute() {
        return "控制线 A(336) → C(600) → K(1368)　｜　"
                + "E₂ 门A(336) → J(384) → S₂(456) → 射线(600) → 门C(624) → B(744)　｜　"
                + "E₃ 门A(336) → 门B(744) → S₃(816) → 门C(1032) → 出口(1248)";
    }

    /** J 分岔引导（设定书 §11.4）：向南是 E₂ 支路，向北是 E₃ 主线。 */
    public String forkHint() {
        return "J 分岔：向南是第二轮支路（S₂ → 射线 → 门 C → B 板）；"
                + "向北是第三轮主线（门 B → 开关室踩 S₃ → 折回门 C → 出口）";
    }

    /** 出口闸三个条件（S₂ / S₃ / K）的兑现进度，供 HUD 直接显示。 */
    public String gateProgress() {
        int done = (switchS2On ? 1 : 0) + (switchS3On ? 1 : 0) + (plateKHeld ? 1 : 0);
        return "出口闸 " + done + "/3：S₂" + mark(switchS2On) + " S₃" + mark(switchS3On)
                + " K" + mark(plateKHeld);
    }

    /** 残影代际角标（设定书 §9.2）：第一残影进入最后有效轮时必须显式提示。 */
    public String finalRoundBadge() {
        return firstEchoFinalRound ? "E₁：最后有效轮" : "";
    }

    private static String mark(boolean satisfied) {
        return satisfied ? "✓" : "✗";
    }
}
