package org.example.timeloop.ui;

/**
 * 第二关「门房链」的目标提示投影（开发三，卡 {@code L02-A-DEV3} §二.3）。
 *
 * <p>与 {@link ObjectiveViewModel} <b>并存</b>而不是改它：卡要求「字段扩展由开发三决定，
 * 但不得破坏既有 {@code ObjectiveViewModel} 的构造调用方」，因此这里新增一个独立 record，
 * PM 侧接线时按需投影本类型；既有 L1 调用方零改动。</p>
 *
 * <p>要表达的三件事（卡 §二.3）：</p>
 * <ol>
 *   <li>第三轮起，<b>两块板分别由哪一代残影压住</b>（README §三 代际：编号即来源轮，越旧越小）；</li>
 *   <li>R1 提示「门外板要踩得够久，为下一轮留门」；</li>
 *   <li>R2 进房后提示「需驻留到本轮结束」（裁决 §十一.4 约束 6「进房即承诺」）。</li>
 * </ol>
 *
 * @param currentRound          当前轮次（1..maxRounds）
 * @param maxRounds             最大轮次
 * @param outerPlateHeld        门外板是否被占用（房门是否开着）
 * @param innerPlateHeld        内板是否被占
 * @param innerPlateSourceRound 内板占用者来源轮：{@code 0}=当前玩家，{@code >=1}=该代残影，{@code -1}=无人
 * @param mainPlateHeld         主驻留板是否被占
 * @param mainPlateSourceRound  主驻留板占用者来源轮（同上约定）
 * @param gateUnlocked          终点闸门是否已解锁（出口是否武装）
 */
public record Level02ObjectiveViewModel(int currentRound,
                                        int maxRounds,
                                        boolean outerPlateHeld,
                                        boolean innerPlateHeld,
                                        int innerPlateSourceRound,
                                        boolean mainPlateHeld,
                                        int mainPlateSourceRound,
                                        boolean gateUnlocked) {

    public Level02ObjectiveViewModel {
        if (maxRounds < 1) {
            throw new IllegalArgumentException("maxRounds 必须 >= 1");
        }
        if (currentRound < 1 || currentRound > maxRounds) {
            throw new IllegalArgumentException("currentRound 必须在 [1, " + maxRounds + "]");
        }
        if (innerPlateHeld && innerPlateSourceRound < 0) {
            throw new IllegalArgumentException("内板被占时必须给出占用者来源轮（0=玩家，>=1=残影）");
        }
        if (mainPlateHeld && mainPlateSourceRound < 0) {
            throw new IllegalArgumentException("主驻留板被占时必须给出占用者来源轮");
        }
    }

    /** 一句话目标提示。判定顺序：通关 → 两板齐备 → 进房承诺 → 第 1 轮 → 兜底。 */
    public String text() {
        if (gateUnlocked) {
            return "闸门已解锁：到闸门旁按 E 通关";
        }
        if (innerPlateHeld && mainPlateHeld) {
            return "内板由" + actor(innerPlateSourceRound) + "压住、主驻留板由" + actor(mainPlateSourceRound)
                    + "压住：闸门开的那一刻到旁边按 E";
        }
        if (innerPlateHeld && innerPlateSourceRound == 0) {
            return "你已进房：需驻留到本轮结束（门外板一松，房门就回锁）";
        }
        if (innerPlateHeld) {
            return "内板已由" + actor(innerPlateSourceRound) + "压住：你去踩门外板踩久一点，再停到主驻留板";
        }
        if (mainPlateHeld) {
            return "主驻留板已由" + actor(mainPlateSourceRound) + "压住：还差内板（只有从第 5 列南侧借窗口进房）";
        }
        if (currentRound == 1) {
            return outerPlateHeld
                    ? "门外板踩得好：踩久一点再离开，为下一轮留门，最后停在主驻留板"
                    : "第 1 轮：先踩门外板（踩住才开门），踩够久再去主驻留板停住";
        }
        return "第 " + currentRound + " 轮：" + (outerPlateHeld ? "房门开着，趁窗口从南侧进房踩内板" : "先等残影压住门外板开窗，再进房踩内板");
    }

    /** 代际表达：0 = 当前玩家；≥1 = 该来源轮的残影（README §三 的 `E<n>`）。 */
    private static String actor(int sourceRound) {
        return sourceRound <= 0 ? "当前玩家" : "E" + sourceRound;
    }
}
