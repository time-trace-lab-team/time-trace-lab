# L02-B / B4 阻塞与「闸链」换图 · PM 记录

> 记录人：**PM** ｜ 基线：`origin/develop` = **`6228d7a`** ｜ 日期：2026-09-14
> 状态：**B4-3 阻塞中**（缺开发一 B4-1/B4-2 组件）；**旧 L02 几何裁决作废待重写**

## 一、B4-3（PM app 接线）当前阻塞：**组件未交付**

PM 在 `develop @ 6228d7a` 实测：

| 期望组件 | 实际情况 |
| --- | --- |
| `core/**PhaseStateMachine`（AVAILABLE/PHASED/RECOVERING） | **不存在**（`core/` 下只有既有的 `ActorPhase` 枚举，是视觉/kinematic 标志，不是状态机） |
| `entity/**SlowdownController`（`beginTick` / `noteHit` / `speedMultiplier`） | **不存在**（`entity/` 下无任何 `Slowdown*` 文件） |

⇒ PM 无法对**不存在的接口**接线。**B4-3 保持阻塞**，直到开发一交付 B4-1（core 相位状态机）与 B4-2（entity 减速端口）并推送。
按上一份裁决：PM 不代写这两块（那是开发一的路径，且跨板需另行授权）。

## 二、B4 的冻结项（不因阻塞失效，交付后照此实现）

1. **顺序**：`updateAll → slowdown.beginTick → phase.advance(pressed) → 移动 → ACTIVE 命中 → PHASED 忽略 / 非 PHASED noteHit → 读取 speedMultiplier → 写 PlayerFrame → 生成 Frame`；
2. **明文 A**：`advance(boolean pressed, long roundTick)` 的边沿检测**在状态机内部**：按住 100 tick 只触发一次；`RECOVERING` 与 `RESULT/FAILED` 不接受新边沿；
3. **明文 B**：命中在**移动后**判定 ⇒ 本 tick 位移仍是满速，减速**从下一 tick 起**影响位移；但**帧里携带本 tick 命中后的 `speedMultiplier()`**（"画面已 SLOWED、位移是本 tick 的"是预期行为，必须有测试名写明）；
4. **明文 C**：去重单位 = `roundTick / RAY_CYCLE_TICKS`（一个周期一个 ACTIVE 段，与"同段只结算一次"等价），但 **`RAY_CYCLE_TICKS` 必须来自关卡数据**，不得在 `core/entity` 写死；
5. 参数：`PHASED 30` / `RECOVERING 45` / `减速 ×0.5` / `持续 60 tick` / 重复命中**刷新不叠加** / `DOCKED` 优先于 `SLOWED`；
6. 复位：`reset(reason)`，`reason ∈ {ROUND_END, FULL_RESTART, SCENE_EXIT}`，状态与计时器一起清零。

## 三、「闸链」换图（`57daa10`）造成的文档漂移 —— **旧结论作废**

新地图把第二关从「3 板 2 门 · 门房链」改成 **28×16 / 5 板（GATE/RELAY/INNER/MAIN/CORE）/ 3 门（GATE/RELAY/EXIT）**。
因此以下**已合入 develop 的 PM 文档与卡**描述的是**旧几何**，一律标记 **已演进/作废**，不得再作为实现依据：

| 文档 | 旧内容 | 处置 |
| --- | --- | --- |
| `L02-门房与双残影-PM裁决.md` §三（官方解）/§四（参数）/§十三（R2 绕行、窗口约束、三条测试） | 3 板 2 门 + 单束射线 + 窗口过门 | **§三/§十三 作废**，参数待按新图**重新裁决** |
| `L02-任务卡-开发{1,2,3}.md`、`L02-任务卡-PM接入.md` | 同上 | 标记**已演进**，仅作历史记录 |
| `README §六 第二关行`、`§十六 #22` | 3 板 2 门的通关链路 | **待重写**为闸链描述 |

**新图作者必须补交的证据（PM 收口条件）**：

1. **「轮次 × 格数 × 刻」时间表**（按新图重算；旧表的 216/396/684 等刻数一律作废）；
2. **不变量证据**：① **第一轮不可能单轮通关**（教学不变量，必须有测试）；② 每一个「窗口过门」的窗口时长 ≥ 走行刻 + 余量；③ **三个门互不影响**（各自的 `requiredPlateIds` 与解锁路径独立）；④ 五块板里哪些是"必须被占"、哪些是"中继/预备"，必须有文字说明；
3. 单束射线在新图上的**周期参数**（`RAY_CYCLE_TICKS` 等）与 OFF 段是否在轮内出现。

## 四、PM 的下一步（不依赖组件）

1. 等 B4-1/B4-2 交付 → 立刻接 **B4-3**（本地写完并跑出 B4-4 证据，推送恢复即提交）；
2. 期间做**文档重裁**：README §六/§十六 按闸链重写、旧卡标记作废、本记录并入裁决文档；
3. 推送/开 PR 仍受环境限制（`spawn EPERM`），交付以"本地绿 + 待推送"状态挂起，恢复后一次性提交。
