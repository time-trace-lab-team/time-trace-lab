package org.example.timeloop.ui;

/**
 * 第二关「闸链」的目标提示投影（开发三，卡 {@code L02-A-DEV3} §二.3）。
 *
 * <p>与 {@link ObjectiveViewModel} <b>并存</b>而不是改它：卡要求「字段扩展由开发三决定，
 * 但不得破坏既有 {@code ObjectiveViewModel} 的构造调用方」，因此这里新增一个独立 record，
 * PM 侧接线时按需投影本类型；既有 L1 调用方零改动。</p>
 *
 * <p>闸链改版后要表达的三件事：</p>
 * <ol>
 *   <li>第三块板是 <b>(18,3) 的锁存开关</b>（{@code L02_plate_switch}）：踩上即锁存，
 *       <b>解锁那一刻不需要有人压着</b> —— 因此终局只要求内板 P3 + 主板 P4 两块板有人压住，
 *       开关是否「此刻有人站着」由 {@link #switchLatched} 表达；</li>
 *   <li>R1 提示「门外板要踩得够久，为下一轮留门」；</li>
 *   <li>R2 进房后提示「需驻留到本轮结束」（裁决 §十一.4 约束 6「进房即承诺」）。</li>
 * </ol>
 *
 * @param currentRound          当前轮次（1..maxRounds）
 * @param maxRounds             最大轮次
 * @param outerPlateHeld        外闸板 P1 是否被占用（外闸 D1 是否开着）
 * @param innerPlateHeld        内板 P3 是否被占
 * @param innerPlateSourceRound 内板占用者来源轮：{@code 0}=当前玩家，{@code >=1}=该代残影，{@code -1}=无人
 * @param mainPlateHeld         主板 P4 是否被占
 * @param mainPlateSourceRound  主板占用者来源轮（同上约定）
 * @param switchLatched         (18,3) 锁存开关是否已锁存：锁存后条件一直成立，不需要有人压着
 * @param gateUnlocked          终点闸 D3 是否已解锁（出口是否武装）
 */
public record Level02ObjectiveViewModel(int currentRound,
                                        int maxRounds,
                                        boolean outerPlateHeld,
                                        boolean innerPlateHeld,
                                        int innerPlateSourceRound,
                                        boolean mainPlateHeld,
                                        int mainPlateSourceRound,
                                        boolean switchLatched,
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
            throw new IllegalArgumentException("主板被占时必须给出占用者来源轮");
        }
    }

    /**
     * 一句话目标提示。判定顺序：通关 → 内板 + 主板（已锁存 / 还差开关）→ 开关已锁存（差分内板 / 主板）
     * → 进房承诺 → 单板 → 第 1 轮 → 第 3 轮起 → 兜底。
     */
    public String text() {
        if (gateUnlocked) {
            return "闸门已解锁：到闸门旁按 E 通关";
        }
        if (innerPlateHeld && mainPlateHeld) {
            return switchLatched
                    ? "开关已锁存、内板由" + actor(innerPlateSourceRound) + "压住、主板由"
                            + actor(mainPlateSourceRound) + "压住：闸门开的那一刻到旁边按 E"
                    : "内板由" + actor(innerPlateSourceRound) + "压住、主板由"
                            + actor(mainPlateSourceRound) + "压住：还差开关 —— 去踩一脚，踩上即锁存，不用压着";
        }
        if (switchLatched) {
            if (innerPlateHeld) {
                return "开关已锁存（不用再有人压着）：内板由" + actor(innerPlateSourceRound)
                        + "压住，还差主板 —— 你去踩门外板留窗口，再停在主板上";
            }
            if (mainPlateHeld) {
                return "开关已锁存（不用再有人压着）：主板由" + actor(mainPlateSourceRound)
                        + "压住，还差内板（等中继板窗口，借窗进内室踩内板）";
            }
            return "开关已锁存（不用再有人压着）：等两条残影分别压住内板与主板";
        }
        if (innerPlateHeld && innerPlateSourceRound == 0) {
            return "你已进房：需驻留到本轮结束（门外板一松，房门就回锁）";
        }
        if (innerPlateHeld) {
            return "内板已由" + actor(innerPlateSourceRound) + "压住：你去踩门外板踩久一点，再停到主板";
        }
        if (mainPlateHeld) {
            return "主板已由" + actor(mainPlateSourceRound) + "压住：还差内板（等中继板窗口，借窗进内室踩内板）";
        }
        if (currentRound == 1) {
            return outerPlateHeld
                    ? "门外板踩得好：踩久一点再离开，为下一轮留门，最后停在主板"
                    : "第 1 轮：先踩门外板（踩住才开门），踩够久再去主板停住";
        }
        if (currentRound >= 3) {
            return "第 " + currentRound + " 轮：踩下开关（锁存），等两条残影压住内板与主板 —— 然后走到终点按 E";
        }
        return "第 " + currentRound + " 轮：" + (outerPlateHeld
                ? "外闸开着，趁窗口跨过外闸进东翼，踩住中继板造窗口，再去内板"
                : "先等残影压住外闸板开窗，再跨过外闸进东翼");
    }

    /** 代际表达：0 = 当前玩家；≥1 = 该来源轮的残影（README §三 的 `E<n>`）。 */
    private static String actor(int sourceRound) {
        return sourceRound <= 0 ? "当前玩家" : "E" + sourceRound;
    }
}
