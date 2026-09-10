# C3 autoDock 事件 ↔ TimelineEvent 字段对齐确认（开发一 → 开发二 / PM）

> 发起：开发一（成员 B）｜日期：2026-09-10｜分支：`codex/dev1-c3`
> 结论依据：`entity/C3DockController`、`entity/DockEventReason`（开发一）与 `replay/TimelineEvent`、`replay/RecordingSession`（开发二）
> 证据：`.\mvnw.cmd test` → 201 tests / Failures 0 / BUILD SUCCESS（本次收紧原因后复跑）

## 1. tick 采集时点

- 事件 `tick` 等于产生该边沿的逻辑刻，即 `C3DockController.step(tick, input, position)` 的入参；**与该刻 tick 末记录的 `PlayerKinematics.tick` 相等**。
- 单刻顺序（开发一指南 §2.4）：采集输入边沿 → 更新队列/相位 → 沿路径推进并提交转向 → 解析 autoDock/终端 → **统一发布本 tick 事件** → 开发二在 tick 末记录规范帧。**事件先于帧记录。**
- 分事件归属：
  - `DOCK_ENTERED`：tick 末位置落在区域内那一该刻；
  - `DOCK_LEFT`：位置**出界**那一该刻（见第 5 节偏差）；
  - `OCCUPANCY_RELEASED`：调用方传入的边界刻（轮末 / 重开 / 退出 / 淘汰）。

## 2. `leaveDirection` 是否 null

- **仅 `DOCK_LEFT` 非 null**（= 离开方向）；`DOCK_ENTERED` 与 `OCCUPANCY_RELEASED` 一律 `null`。
- 代码位置：`C3DockController` 造 `DOCK_LEFT` 时传 `departureDirection`；造 `DOCK_ENTERED`/`OCCUPANCY_RELEASED` 时传 `null`。
- 请求：开发二可在 `TimelineEvent` 文档（或构造校验）固化该不变量，**无需改字段**。

## 3. `reason` 取值规范（已冻结）

| 事件 | `reason` |
| --- | --- |
| `DOCK_ENTERED` | `null` |
| `DOCK_LEFT` | `"NEW_DIRECTION"` |
| `OCCUPANCY_RELEASED` | 下列之一：`"ROUND_END"` / `"ECHO_EXPIRED"` / `"FULL_RESTART"` / `"SCENE_EXIT"` |

- 已在开发一侧实现为**冻结枚举** `entity/DockEventReason`：`NEW_DIRECTION`、`ROUND_END`、`ECHO_EXPIRED`、`FULL_RESTART`、`SCENE_EXIT`；写入 `TimelineEvent.reason` 时取其 `name()`。
- 其中 `ROUND_END`/`FULL_RESTART`/`SCENE_EXIT` 与开发三 `AutoDockResetReason` 同名对齐；`ECHO_EXPIRED` 为残影淘汰释放（`releaseActor`）。
- 本次已把 `releaseOccupancy` 由自由字符串**收紧为 `DockEventReason`**，使集合在编译期受约束；`DOCK_LEFT` 的原因常量 `REASON_NEW_DIRECTION` 也改为由枚举派生。
- **归属**：该规范属跨模块，枚举放在开发一 `entity/`；**不放进 `replay/`**（避免 `replay` 反向依赖 `core/entity`）。新增取值须 PM 批准。

## 4. 是否需要 `InputIntent → TimelineEvent` 转换工具类

- **不需要单独的转换工具类。** 卡 #1 定义「`TimelineEvent` 由开发二定义、开发一/三**产生**」，开发一的 `C3DockController` 即**生产者**，已在状态边沿**直接构造** `TimelineEvent`。
- **尤其不得放 `replay/`**：那会让 `replay` 依赖 `core.input`，破坏单向依赖（开发二指南 §2.2）。
- 结论：事件产生留在生产者板块（开发一 `entity/`）；日后若确实需要共享助手，由 PM 另开卡，默认「不引入转换类」。

## 5. 仍未决的偏差（影响开发二回放预期，请 PM 裁决）

`AutoDockOccupancyPort.tryLeave` 要求**位置已在区域外**，因此开发一当前把 `DOCK_LEFT` 记在**出界刻**；README §三要求「离开驻留型机关时，角色**立即**出发，对应占用**同时释放**」。二者冲突：

- **选项 A（README 优先）**：开发三放宽 `tryLeave`——占用者在离开刻按下合法出口即释放，不要求位置已出界；`DOCK_LEFT` 记在离开刻。
- **选项 B（API 优先，现状）**：维持按出界刻记录；README 措辞按此解释。

开发二在回放时应以**事件自带 tick** 为准；在 PM 裁决前，按选项 B 的 tick 预期准备用例。

## 6. 另两项（属集成/PM，非开发一）

1. **`RecordingSession` 两个 `Runnable` 注入**：注入点为 `completeNormalRound(Runnable)` 与 `restartFromFirstRound(Runnable)`。集成层应注入 `() -> autoDockService.reset(AutoDockResetReason.ROUND_END, 边界tick)` 与 `() -> autoDockService.reset(AutoDockResetReason.FULL_RESTART, 边界tick)`。注意 **`reset` 需要 tick**，`Runnable` 必须捕获边界刻；`RecordingSession` 内当前不传 tick，请 PM 在装配时明确该刻来源。
2. **R3 手工验收**：等 `app/` 场景装配 + 开发一 C3 接线完成后执行；开发一 C3 已在 `codex/dev1-c3` 交付。

---

*本确认为开发一单方面答复，未修改 `replay/**`、`mechanism/**`、`app/**` 等非本岗位路径；开发一侧改动为 `entity/DockEventReason`（新增）与 `C3DockController.releaseOccupancy`（收紧签名）。*
