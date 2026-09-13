# R2 `PlayerFrame` 契约裁决

- 状态：**已裁决**（项目经理 2026-09-09）
- 决策人：项目经理（采纳开发 2 提案《R2-契约提案-PlayerFrame与三枚举归属》的推荐值）
- 适用模块：`core`（枚举落地）、`replay`（`PlayerFrame` / `TimelineRecording`）
- 依据：`README.md` §9、`docs/decisions/C1-共享时钟与单刻顺序约定.md` §1.1 / §5.3
- 解锁对象：**R2「当前轮定长记录」主体**

---

## 1. 三条裁决

| # | 事项 | 裁决 |
| --- | --- | --- |
| 1 | `Direction` / `MovementState` / `AnimationState` 归属 | **放 `core`**（与 `GamePhase` 同级，方案 A） |
| 2 | `Direction` 值集 | `UP / DOWN / LEFT / RIGHT` |
| 3 | `AnimationState` 值集 | `MOVING / DOCKED / INTERACTING` |

`MovementState` 值集沿用 README 已冻结的 `CRUISING / SLOWED / DOCKED`，无需再裁。

---

## 2. `phaseState` 之谜：已解析，不是阻塞

提案 §4.1 曾标记「`phaseState` 全项目检索不到任何定义」。**实际已定义，只是指南用了别名。**

| 来源 | 写法 |
| --- | --- |
| `README.md` 第 578 行 | `boolean phaseDodging` |
| `开发2-时间与状态系统-技术指南.md` §3.2 第 176 行 | `phaseState` |

按文档优先级（README 开头：「若两者冲突，以 README 的产品范围为准」；指南 §0.1：README > 已批准接口 > 本指南）：

> **`phaseState` 即 `boolean phaseDodging` —— 玩家侧布尔，表示本刻是否处于 `PHASED` 相位下潜状态。**

与 README 的相位下潜机制（按 `Space` 进入 30 tick `PHASED`；第二关躲避、第五关故意不躲）一致。**无需开发 1 或开发 3 再定义。**

---

## 3. `PlayerFrame` 最终字段（开发 2 据此实现）

```java
public record PlayerFrame(
        long tick,                    // 必须等于其在记录列表中的索引
        double x,                     // 世界坐标
        double y,
        Direction direction,          // core.Direction
        boolean interacting,
        MovementState movementState,  // CRUISING / SLOWED / DOCKED
        boolean phaseDodging,         // 见第 2 节
        AnimationState animationState // MOVING / DOCKED / INTERACTING
) {}
```

### 3.1 一处类型修正

`tick` 类型取 **`long`**（README §9 原文），不取提案表里写的 `int`。
理由：README 优先；且与 `TickContext.roundTick`（`long`）保持一致，避免逐刻转换。

### 3.2 两项非阻塞澄清（按下列默认值实现，有异议再提）

| 项 | 默认实现 | 依据 |
| --- | --- | --- |
| `interacting` 是边沿还是持续 | 记录**本刻交互输入是否生效**（含 6–10 tick 缓冲窗口）；**边沿判定交给 `TimelineEvent`**，不重复用帧字段表达 | README「需要按 `E` 的交互使用宽容半径和 6–10 tick 输入缓冲」 |
| `x` / `y` 单位与基准 | **世界坐标**（C1 决策：960 × 576、`tileSize` 48.0），基准为**角色中心** | C1-启动与世界坐标约定 §2 |

> 配套待办（不阻塞 R2）：开发 3 的 `Level01Footsteps.TILE_SIZE` 仍是 64.0，需统一为 48.0。

---

## 4. 依赖方向确认

三个枚举入 `core` 后，依赖方向仍为 C1 §1.1 确立的**单向**：

```text
replay → core      （允许）
core   → replay    （禁止）
```

`replay` 侧只 import `core.Direction` / `core.MovementState` / `core.AnimationState` / `core.GamePhase`，不反向依赖。

---

## 5. 落地顺序（不可颠倒）

| 步 | 板块 | 内容 | 阻塞对象 |
| --- | --- | --- | --- |
| 1 | **开发 1** | 在 `core/` 新建三个枚举（纯常量，约 30 行，无逻辑） | 开发 2 的 R2 主体 |
| 2 | **开发 2** | 在 `replay/` 实现 `PlayerFrame` + `TimelineRecording` | — |

**原因**：`core/**` 在开发 2 的禁止路径内，开发 2 无权创建这三个文件。
规格见 `docs/development/开发2/R2-给开发1的三枚举规格.md`。

---

## 6. 本次裁决未覆盖（另行处理）

- ~~C1 §5.1：`TickStepResult` 与 `replay.AdvanceResult` 重复问题~~ → **已裁决（2026-09-09 晚，开发 1 提出、按方案 A 执行）**：
  `RoundClock.advance()` 改返回 `core.TickStepResult`；迁移 `replay` 测试并删除 `replay.AdvanceResult`。
  ⚠️ `core.TickStepResult` 文件本身在开发 1 路径，须由开发 1 与三枚举一并创建后，开发 2 才能做签名迁移。
- C1 §2 已冻结的 `TickStepResult stepOnce()`：开发 1 当前交付为 `void`，属缺陷，见三枚举规格文档附录
- `TimelineEvent` 类型集（开发 3）：R2 事件排序部分仍待其冻结，但不阻塞 `PlayerFrame` / `TimelineRecording` 主体
