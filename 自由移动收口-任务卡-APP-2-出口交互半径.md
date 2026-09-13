# 任务卡 · APP-2 出口交互宽容半径接线（PM）

> 任务 ID：**X-MOVE-COLLAPSE-01-APP-2**（父卡 `跨模块契约卡-四方向移动收口-X-MOVE-COLLAPSE-01.md` §9.5）
> 主责：**PM（`app/**`）** ｜ 上游：**开发三 L-3**（`cf4714f`，`origin/feature/content-x-move-collapse-01-dev3-l3`）
> 状态：**已发卡**，待 L-3 进入 develop 后接线（可先基于 L-3 分支本地验证）
> 日期：2026-09-10

## 一、问题（实测）

`Level01Assembly.interactIfRequested(...)` 现在只判 `exit.isDoorUnlocked() && !exit.isTriggered()`，
**没有任何位置判定** → 玩家在关卡任何角落（哪怕还没走到出口终端）按一下 `E` 就能结算通关。
README §三 要求「需要按 `E` 的交互使用**宽容半径**和 6–10 tick 输入缓冲」
（缓冲已由 `C3DockController.INTERACT_BUFFER_TICKS = 8` 提供）。

## 二、上游契约（开发三已实现）

`cf4714f` 在 `mechanism/ExitTerminal` 新增（方案 A）：`boolean isInInteractRange(Vector2D worldPosition)`，
半径 = **1.5 × tileSize = 72**。硬下限是 1 格（48）：必须覆盖「右驻留板 (8,5) → 出口终端 (9,5)」这一格，
否则玩家必须离开右板才能按 E，而 `Door` 不闩锁（非全占即 `LOCKED`）→ 第一关无解。

## 三、任务（只碰 `app/**`）

1. `Level01Assembly.interactIfRequested(...)` 增加位置判定：用 `patrol.position()` 构造 `Vector2D`
   传给 `exit.isInInteractRange(...)`，超距直接 return，**不**调用 `exit.interact(...)`；
2. 保持「按 `E` 的新按下边沿 + 宽容半径」语义：不改 `InputIntent` 缓冲逻辑、不在 app 造第二套计时器；
3. 不改 `mechanism/**`；半径数值与常量一律来自机制侧，不在 app 里硬编码。

## 四、验收

1. 新 app 集成测试「**距出口终端 1 格内可按 E 结算**」：把玩家走到右驻留板中心 (8,5)（等价于第一关既定解法位置）
   并让门处于解锁状态，按一次 `E` → `hudContext().phase() == RESULT`（`completeGoal` 生效）；
2. 新 app 集成测试「**超距按 E 不结算**」：把玩家放在分叉 (5,3)，门解锁状态下按 `E` → 仍在 `PLAYING`、
   `exit.isTriggered() == false`；
3. `.\mvnw.cmd -o test` 退出码 0；任何单测 ≤ 1 秒；
4. 机制侧 headless 链路 `Level01CausalChainTest`（直接调 `exit.interact(...)`，不经 app 距离判定）保持绿。

## 五、依赖与顺序

- 上游 L-3（`cf4714f`）进入 develop 后，PM 把 develop 并入 APP-2 分支再接线并验证；
- 与 `X-MOVE-COLLAPSE-01-ECHO-ACTOR` 互不依赖，可并行；
- 本卡不阻塞 PR #54 / #55 的合并。
