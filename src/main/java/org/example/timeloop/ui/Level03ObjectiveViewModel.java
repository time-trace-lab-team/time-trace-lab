package org.example.timeloop.ui;

/**
 * 第三关《追赶过去》的目标提示投影（开发三，卡 {@code L03-DEV3} §三 / 设定书 §9.2 §9.4）。
 *
 * <p>与 {@link Level02ObjectiveViewModel}、{@link ObjectiveViewModel} <b>并存</b>：三者都是只读
 * record，互不改签名，PM 侧接线时按当前关卡投影对应类型即可。</p>
 *
 * <p>本关要表达的四件事（设定书 §9.4）：</p>
 * <ol>
 *   <li><b>一级</b>：出口需要 D 板供能；两道中间门由过去开启；</li>
 *   <li><b>二级</b>：第二轮到达 B 的时刻，会决定第三轮门 B 何时打开；</li>
 *   <li><b>三级</b>：两条推荐路线 {@code A → C → D} 与 {@code 门A → 射线 → B} 及其关键节点；</li>
 *   <li>J 分岔引导（上方 B 支路 / 右侧主通道）与 {@code E₁：最后有效轮}（设定书 §9.2）。</li>
 * </ol>
 *
 * <p>提示只显示信息：{@link #text()} 及其它 accessor 都是纯函数，不推进、不暂停、不修改任何时钟
 * （设定书 §9.4 末条）。</p>
 *
 * <p><b>校验只保留真命题</b>：紧凑构造器里曾经有过一条
 * {@code !doorAOpen && inBranchB → 抛异常}（「门 A 没开时不可能已经在 B 支路里」），
 * 它把「门<b>此刻</b>是否被打开」误当成「玩家<b>能不能</b>已经在里面」—— 与第一关那条被删掉的
 * 假不变量同类。第三关的真实玩法里它<b>必然</b>触发：E₁ 在刻 384 离开 A 板后门 A 就回锁，
 * 而第二轮 / 第三轮的玩家此时正在 B 支路里跑到轮末（见 {@code Level03Pursuit.GATE_A_WINDOW_END}）。
 * 因此该条校验已删除，{@code doorAOpen=false && inBranchB=true} 是<b>合法</b>状态。</p>
 *
 * @param currentRound         当前轮次（1..maxRounds）
 * @param maxRounds            最大轮次（本关 = 3）
 * @param firstEchoFinalRound  第一残影是否已进入最后有效轮（= 当前已是最后一轮，设定书 §9.2）
 * @param doorAOpen            门 A <b>此刻</b>是否开着（E₁ 正压着 A 板）。它与 {@code inBranchB}
 *                             <b>互相独立</b>：门只在一小段窗口里开着，而玩家（尤其在第二轮 / 第三轮）
 *                             完全可以已经身处 B 支路、门 A 却早已回锁 —— 这是本关的<b>正常</b>局面，
 *                             不是矛盾状态
 * @param inBranchB            当前玩家是否已进入 B 支路（会接触射线）
 * @param rayActive            射线此刻是否 ACTIVE（该按下潜了）
 * @param doorBOpen            门 B 是否开着（E₂ 已压住 B 板）
 * @param doorCOpen            门 C 是否开着（E₁ 正压着 C 板）
 * @param exitPowered          出口是否已供能（E₁ 正压着 D 板）
 */
public record Level03ObjectiveViewModel(int currentRound,
                                        int maxRounds,
                                        boolean firstEchoFinalRound,
                                        boolean doorAOpen,
                                        boolean inBranchB,
                                        boolean rayActive,
                                        boolean doorBOpen,
                                        boolean doorCOpen,
                                        boolean exitPowered) {

    /**
     * 只校验<b>真</b>不变量：轮次范围，以及「最后有效轮」只可能出现在最后一轮。
     *
     * <p><b>刻意不再校验「板与门之间的因果组合」</b>：本类曾在这里要求「门 C 开着时门 B 必须也开着且轮次 ≥3」
     * 与「供能只可能来自残影」，结果在真实玩法里<b>每帧抛异常</b> —— 第 1 轮玩家踩 C 板（门 C 开、
     * 门 B 无人压、轮次 1）本就是官方解的第一步，踩 D 板供能时门 C 早已松开同理。
     * 教训与 L1、L3 那两条被删的假不变量一致：**「某个时刻的因果」不是「状态组合的合法性」**，
     * 把前者写进构造校验，就会在玩家按正常解法游玩时崩溃。当前状态组合是否可达由装配与刻表决定，
     * 不归一个只读投影管；本类只保证对<b>任意</b>合法轮次/布尔组合都能给出一句人话。</p>
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
     * 一句话行动提示。判定顺序：已供能 → 两门齐开 → 在 B 支路（射线在下潜窗口内）→ 第 1 轮录制
     * → 等门 A → 等门 B → 赶门 C 窗口 → 兜底。
     */
    public String text() {
        if (exitPowered) {
            return "出口已供能：到出口旁按 E 通关";
        }
        if (inBranchB) {
            return rayActive
                    ? "射线激活：按 Space 相位下潜穿过去（下潜期间不受射线判定）"
                    : "沿 B 支路往上走，看到预警就准备按 Space 下潜，然后驻留 B 板到轮末";
        }
        if (doorBOpen && doorCOpen) {
            return "门 B 与门 C 同时开着：沿主通道冲过去，再到出口等 D 板供能";
        }
        if (currentRound == 1) {
            return "第 1 轮：依次踩 A → C → D；A 要踩够久（给下一轮留门），C 短暂停留，D 压到轮末";
        }
        if (currentRound == 2) {
            return doorAOpen
                    ? "门 A 开了：穿过去，在 J 处向上进 B 支路，看到预警按下潜，然后驻留 B 到轮末"
                    : "等残影 E₁ 压住 A 板开门（它第一轮踩过 A），门一开就穿进内区";
        }
        if (doorBOpen) {
            return "门 B 开了：走主通道，趁 E₁ 的 C 窗口穿过门 C，再到出口等 D 供能";
        }
        if (doorAOpen) {
            return "门 A 开了：和 E₂ 一起进内区，在 J 处向右走主通道，等 E₂ 压住 B 开门 B";
        }
        return "等 E₁ 压住 A 板开门 A（E₁ 处于最后有效轮，它第一轮的窗口仍会按时出现）";
    }

    /** 一级提示（设定书 §9.4）：出口的供能来源与两道中间门由谁开。 */
    public String tierOne() {
        return "出口需要 D 板供能；两道中间门由过去开启。";
    }

    /** 二级提示（设定书 §9.4）：B 的到达时刻决定门 B 何时开。 */
    public String tierTwo() {
        return "第二轮到达 B 的时刻，会决定第三轮门 B 何时打开。";
    }

    /** 三级提示（设定书 §9.4）：两条推荐路线与关键时间节点（刻数以共享刻表为准）。 */
    public String tierThreeRoute() {
        return "控制线 A(336) → C(600) → D(1056)　｜　支路 门A(336) → J(432) → 射线(552) → B(624)";
    }

    /** J 分岔引导（设定书 §11.4）：上方是 B 支路，右侧是最终主通道。 */
    public String forkHint() {
        return "J 分岔：上方通向 B 板（支路，有射线，第二轮走这条）；右侧是主通道（门 B → 门 C → 出口，第三轮走这条）";
    }

    /** 残影代际角标（设定书 §9.2）：第一残影进入最后有效轮时必须显式提示。 */
    public String finalRoundBadge() {
        return firstEchoFinalRound ? "E₁：最后有效轮" : "";
    }
}
