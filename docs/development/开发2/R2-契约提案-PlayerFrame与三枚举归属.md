# R2 契约提案：`PlayerFrame` 字段与三个状态枚举的归属

- 提出方：开发 2（时间与状态系统）
- 日期：2026-09-09
- 目的：推动 C1 §5.3 裁决，解除 R2「当前轮定长记录」主体的阻塞
- 状态：**待项目经理裁决**（本文件不含任何代码改动）

---

## 1. 为什么这份提案是 R2 的钥匙

R2 要记录每 tick 的规范玩家状态，而 `PlayerFrame` 需要三个枚举：`Direction` / `MovementState` / `AnimationState`。
C1 §5.3 写明：

> `PlayerFrame` 仍需要 `Direction` / `MovementState` / `AnimationState`，三者均为玩家侧概念（开发 1 范围），
> 未在本决策中指定放置位置。**R2 主体仍被此项阻塞**。

也就是说：**只要 PM 拍板这三个枚举放哪、并让开发 1 补齐值集，R2 主体就能开工。**

---

## 2. 归属方案对比

| 方案 | 放置 | 产生的依赖方向 | 代价 | 评价 |
| --- | --- | --- | --- | --- |
| **A（开发 2 推荐）** | `core`（与 `GamePhase` 同级） | `replay → core`、`entity → core` | `core` 的定位从「时钟与循环」扩成「跨板块共享契约」 | 与 C1 §1.1 已确认的 `replay → core` 单向完全一致，不新增方向；若 §5.1 也选方案 A（`TickStepResult` 入 `core`），共享包语义统一 |
| B | `entity`（开发 1 范围，符合 C1 §5.3 字面表述） | **新增 `replay → entity`** | 开发 2 首次依赖实体包；日后 `entity` 若反向依赖 `replay` 即成环 | 需 PM **显式批准新依赖方向**（C1 §1.1 只确认过 `replay → core`） |
| C | `replay`（开发 2 范围） | `entity → replay` | 玩家侧概念反向依赖回放包，边界不合理 | 不推荐 |
| D | 新建 `core.state` 或 `model` 子包 | 同 A | 引入新目录，需 PM 批准结构 | 本质是 A 的细化，可一并考虑 |

### 开发 2 的推荐：**方案 A**（或 D）

理由三条：

1. **方向最干净。** 全项目只保留 `replay → core` 与 `entity → core` 两条指向共享层的箭头，不产生 `core → replay` 或 `replay → entity`。
2. **与 C1 §1 同构。** 第 1 项已把「唯一正式阶段枚举」放 `core`，共享契约放 `core` 是同一套逻辑。
3. **避免重复创建。** 若放 `entity`，开发 2 为了不依赖实体包，很可能被迫在 `replay` 里再抄一份枚举 —— 那正是技术指南明令禁止的「重复创建模型」。

> 诚实说明：方案 A 与 C1 §5.3 里「三者均为玩家侧概念（开发 1 范围）」的字面表述有张力。
> 我的理解是：那句话说的是**语义归属**（值集由开发 1 定义），不必然等于**物理放置**。
> 值集仍由开发 1 定，只是类型放在共享层。**这一点请 PM 明确。**

---

## 3. `PlayerFrame` 字段提案

依据技术指南 §3.2 的连续帧最小表达：`tick, x, y, direction, interacting, movementState, phaseState, animationState`。

| 字段 | 类型 | 数据来自 | 状态 |
| --- | --- | --- | --- |
| `tick` | `int` | 开发 2 | **已冻结**，必须等于列表索引 |
| `x`, `y` | `double` | 开发 1 | ⚠️ 需确认单位（像素 / 格）与基准点（中心 / 左上角） |
| `direction` | `Direction` | 开发 1 | 🔴 归属待裁决；值集待开发 1 给 |
| `interacting` | `boolean` | 开发 1 | ⚠️ 需澄清是**边沿**（本刻刚按下）还是**持续**（按住中） |
| `movementState` | `MovementState` | 开发 1 | 值集**已冻结**：`CRUISING / SLOWED / DOCKED`；归属待裁决 |
| `phaseState` | **未定义** | 开发 3？ | 🔴 **全项目检索不到任何定义**，见第 4 节 |
| `animationState` | `AnimationState` | 开发 1 | 🔴 归属待裁决；值集未定 |

### 开发 2 对 `PlayerFrame` 的承诺（不变量）

- 不可变值对象，构造后字段全 final；
- `tick` 严格等于其在记录列表中的索引；
- **不持有开发 1 的可变玩家对象引用**（指南 §5.3 完成定义：记录不持有可变玩家对象）—— 无论开发 1 给的 `PlayerKinematics` 是否可变，开发 2 都会复制成自己的帧；
- 封装后集合不可修改，拒绝后续写入；
- 时间系统不使用随机数。

---

## 4. 需要澄清的三件事（按阻塞严重度排序）

### 4.1 🔴 `phaseState` 是什么？（**隐藏阻塞，此前没人提**）

技术指南 §3.2 把它列为连续帧必录字段，但全项目检索无任何定义。歧义有三层：

- 它是**玩家**的相位/时滞状态，还是**机关**的？（第二关有相位机制、第五关有时滞）
- 类型是什么：枚举、整数相位计数、还是布尔？
- 若属机关状态，它就不该出现在 `PlayerFrame` 里，而应走 `RoundSnapshot` / 机关快照。

**这一条不定，`PlayerFrame` 就无法定稿，R2 就无法开工。请 PM 指派开发 1 或开发 3 给出定义。**

### 4.2 ⚠️ `interacting` 是边沿还是持续？

影响两件事：逐 tick 写帧的语义，以及 `TimelineEvent` 的边沿判定（「终端交互」事件何时发）。

### 4.3 ⚠️ 位置单位与基准

`x` / `y` 用像素还是格？基准是玩家中心还是左上角？
已知 C1 世界坐标约定为 960×576、`tileSize` 48.0（但开发 3 的 `Level01Footsteps.TILE_SIZE` 仍是 64.0，这个不一致也还没修）。

---

## 5. 请 PM 拍板的最小决策（三句话即可解锁 R2）

1. **三枚举放 `core`（方案 A / D）还是 `entity`（方案 B）？**
   → 推荐 `core`：不新增依赖方向、不诱发重复模型。
2. **指派开发 1 定义 `Direction` / `AnimationState` 的值集**，并确认 `MovementState` 三值沿用 `CRUISING / SLOWED / DOCKED`。
3. **指派开发 1 或开发 3 定义 `phaseState`**，明确它是玩家侧还是机关侧、以及类型。

配套（不阻塞但请一并处理）：

- 开发 1 给出 tick 末 `PlayerKinematics` 的签名草案（开发 2 只需稳定字段，会自行复制成不可变帧，不要求共用对象）
- 澄清 `interacting` 的边沿/持续语义
- 统一 `TILE_SIZE` 64.0 → 48.0，以及 `LevelData.durationTicks` 的 `long` 与 `RoundClock` 构造参数 `int` 的不一致

---

## 6. 裁决后开发 2 的开工范围（预告）

R2 主体，仅在 `src/main/java/org/example/timeloop/replay/` 内：

- `PlayerFrame`（不可变值对象）
- `TimelineRecording`（当前轮缓冲，满 `D` 帧一次封装后冻结）
- 事件收集与稳定排序（按类别优先级 + 稳定 ID）
- 边界：重复 tick、缺 tick、乱序事件、`DOCKED` 长静止段、通关时未满丢弃

**不实现**：双残影（R4，须先过 R3 单残影门禁）、快照恢复（R5）、渲染快照（R6）。
