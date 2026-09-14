# 开发三步骤 2：稳定 ID、事件排序与 autoDock 规格冻结

> 项目：《时痕实验室：昨日的我》
> 分支：`feature/content-closeout-gate`
> 日期：2026-09-10
> 范围：开发三 `mechanism / level / ui` 的跨模块书面契约
> 状态：规则已经确认；W1/W4 最小公共接口已经落地，正式时间线事件由开发二 `TimelineEvent` 提供

## 1. 本步骤边界

本文件只冻结稳定标识、同刻事件排序和 autoDock 的语义。步骤 2 不修改 Java 源码、测试、`pom.xml`、资源或其他岗位目录。

下列内容是后续步骤的验收依据：

- 步骤 3 依据本文件落地 ID、引用和数据校验；
- 步骤 4 依据本文件落地只读 autoDock 查询和占用规则；
- 本步骤不创建 `TimelineEvent`，不修改 `GameEvent` 公共签名，不实现输入、移动、回放、Canvas 或页面接线；
- 本步骤不把现有类的行为描述为已完成验收。当前实现差距见第 7 节。

## 2. 稳定 ID 规则

### 2.1 命名空间与不变量

1. 稳定 ID 是全局唯一、稳定、不可变的 `String`。同一关卡重新加载、重置、重放或生成快照时，ID 不得因对象重建而变化。
2. 机制 ID 在整套项目数据中全局唯一；机制不能使用 actor 保留 ID。ID 只作为身份，不承载显示文本、状态或内存地址。
3. 机制 ID 使用小写 ASCII 语义词和下划线分隔，关卡前缀保留大写 `L01`～`L05`。规范形态为：

   ```text
   <关卡前缀>_<实体类型>_<语义或序号>
   ```

   例如：`L01_plate_left`、`L01_door_01`、`L01_exit_00`。
4. 实体类型词固定使用下列集合，不得用显示名替代：

   | 机制类型 | ID 类型词 | 示例 |
   | --- | --- | --- |
   | 驻留板 | `plate` | `L01_plate_left` |
   | 普通门 | `door` | `L01_door_01` |
   | 时滞射线 | `ray` | `L02_ray_01` |
   | 固定共振区 | `resonance` | `L04_resonance_00` |
   | 共振中继 I/II | `relay_i` / `relay_ii` | `L04_relay_i_00` |
   | 核心终端 | `core` | `L04_core_00` |
   | 出口终端 | `exit` | `L01_exit_00` |

5. actor ID 使用保留规则：

   | actor | 稳定 ID |
   | --- | --- |
   | 当前玩家 | `player` |
   | 第 `sourceRound` 轮生成的残影 | `echo_<sourceRound>`，例如 `echo_1`、`echo_2` |

   `sourceRound` 是回放语义字段，不拼入机制 ID，也不能用 `E1` 等显示简称替代。

   **（X-MOVE-COLLAPSE-01 v2 §B 冻结确认，2026-09-11）** `echo_<sourceRound>` 是**冻结约定**，`sourceRound` **从 1 起算**。
   当前实现有两处依赖它，任何一方改动都必须同步另一方：

   | 依赖点 | 位置 | 依赖内容 |
   | --- | --- | --- |
   | 端口校验 | `AutoDockService.requireActor(...)` | 强制 actorId 为 `player` 或 `echo_<n>`，且 `n` 必须等于传入的 `sourceRound` |
   | 残影消散释放 | `DockingPlate.onEvent(ECHO_DISAPPEARED)` | 仅当占用者等于 `"echo_" + event.sourceRound()` 时才释放占用 |

   推论：**回放侧必须以 `echo_<sourceRound>` 身份提交进入/离开边沿**。若沿用录制时的 `player` / `0`，
   残影消散时占用不会被释放，驻留板将**永久占用**（该缺陷由 `X-MOVE-COLLAPSE-01-ECHO-ACTOR` 跟踪）。
6. 路径节点不是机制，但所有机制关联的节点也必须有稳定节点 ID。推荐形态为 `<关卡前缀>_node_<语义>`，例如 `L01_node_left_end`。机制与路径节点之间只能通过 ID 引用，不能通过列表位置或坐标最近值隐式绑定。
7. 以下值禁止作为稳定 ID 或 ID 决胜依据：UUID、对象地址、显示名、集合迭代序号、数组下标、`HashMap`/`HashSet` 的遍历顺序和 enum `ordinal()`。

### 2.2 已有数据的规范映射

下表是根据当前源码坐标计算出的目标 ID 映射。步骤 2 只书面冻结，不在本步修改关卡构造器。

| 关卡 | 目标机制 ID | 当前世界坐标 | 关联节点 | 合法出口方向 | 当前代码差距 |
| --- | --- | ---: | --- | --- | --- |
| L01 | `L01_plate_left` | `(96, 240)` | 当前 `left_end`，目标 `L01_node_left_end` | `UP` | `EntitySpawnInfo` 没有实体 ID |
| L01 | `L01_plate_right` | `(384, 240)` | 当前 `right_end`，目标 `L01_node_right_end` | `UP` | `EntitySpawnInfo` 没有实体 ID |
| L01 | `L01_door_01` | `(240, 240)` | 由关卡门数据引用 | 不适用 | 当前门 ID 为 `door_1` |
| L01 | `L01_exit_00` | `(432, 240)` | 目标 `L01_node_exit_terminal` | `UP` | 当前出口实体没有 ID |
| L03 | `L03_plate_left` | `(168, 408)` | 当前 `left_plate`，目标 `L03_node_left_plate` | `UP`, `DOWN` | 实体类型和 ID 均未规范化 |
| L03 | `L03_plate_right` | `(600, 408)` | 当前 `right_plate`，目标 `L03_node_right_plate` | `UP`, `DOWN` | 实体类型和 ID 均未规范化 |
| L03 | `L03_door_01` | `(264, 264)` | 目标 `L03_node_door` | 不适用 | 当前门 ID 为 `door_1` |
| L03 | `L03_exit_00` | `(264, 72)` | 当前 `exit_terminal`，目标 `L03_node_exit_terminal` | `UP` | 出口实体没有 ID |

L02 尚无正式关卡数据，L04/L05 必须等待 PM 卡 #5；本表不预创建它们的关卡数据，也不为它们虚构坐标。

### 2.3 ID 校验要求

步骤 3 实现时，关卡加载或构造必须在进入可玩状态前拒绝：

- 空、全空白或格式非法的 ID；
- 同一关卡或全局命名空间内的重复 ID；
- 机制类型词与数据类型不匹配的 ID；
- 机制引用不存在的路径节点、门、出口或其他机制；
- 通过显示名、坐标模糊匹配或列表位置补全引用的情况。

错误信息至少带关卡 ID、字段名和冲突/悬空的具体 ID。验证失败不能产生半初始化的可玩关卡。

## 3. 同刻事件稳定排序

### 3.1 排序键

任何需要在一个 tick 内批量处理的 autoDock 事件，必须使用下面的三段式键，按从左到右升序比较：

```text
tick 升序
→ 固定事件类别优先级升序
→ 事件主体 stableId 的字典序升序
```

字典序使用 Java `String` 的确定性自然序，不使用本地化排序。`stableId` 必须是事件主体的稳定机制 ID 或 actor ID，不能从 payload、对象地址或集合位置推导。

### 3.2 固定事件类别优先级

下表是本契约目前冻结的类别表。数值是文档中的显式优先级，不得以 enum `ordinal()` 代替。未列入的类别不得自行插入排序；需要扩展时必须先更新本契约。

| 优先级 | 类别 | 语义 |
| ---: | --- | --- |
| 10 | `DOCK_LEFT` | actor 在 tick `t` 合法离开某 dock 的边沿事实 |
| 20 | `OCCUPANCY_RELEASED` | 该 dock 在 tick `t` 完成占用释放 |
| 30 | `DOCK_ENTERED` | actor 在 tick `t` 从区域外进入某 dock |
| 40 | `MECHANISM_STATE_CHANGED` | 由上述占用变化派生的机关状态变化 |
| 50 | `EXIT_REQUESTED` | 当前玩家在满足权限后发起出口请求 |

因此，同一 tick 中必须满足：

```text
DOCK_LEFT < OCCUPANCY_RELEASED < DOCK_ENTERED
```

这保证释放先于后续占用判断；但同一 dock 在同一 tick 的“离开后再次进入”仍由第 4.6 节的禁止标记拦截，不能靠排序绕过。

### 3.3 Comparator 语义草案

实现时可以等价地表达为以下逻辑，但本步骤不新增公共 Java 类型或签名：

```text
compare(a, b):
  compare a.tick and b.tick
  compare priority(a.category) and priority(b.category)
  compare a.stableId and b.stableId using deterministic lexicographic order
```

类别优先级表必须是显式查表或显式分支，缺失类别直接报告契约错误。相同 `tick + category + stableId` 的重复事件不是新的排序情况：生产者应避免重复，接收方应拒绝或按明确的重复诊断处理，不能使用对象 identity 作第四排序键。

当前 `GameEvent.sourceId` 同时承载 actor 和机制语义，不能直接被当作已冻结的记录协议。本步骤只冻结 autoDock 事件的 stable ID 语义，不修改 `GameEvent`；正式记录事件使用开发二已经交付的 `TimelineEvent` 和显式优先级 Comparator。

## 4. autoDock 几何规格

### 4.1 世界坐标和区域形状

1. autoDock 使用与 `LevelGeometry` 相同的世界坐标，区域中心必须与其关联路径节点的世界坐标完全一致。
2. 当前版本的唯一区域形状是轴对齐闭合矩形，不旋转、不使用圆形近似。给定 `tileSize` 和中心 `(cx, cy)`，区域为：

   ```text
   [cx - tileSize / 2, cx + tileSize / 2]
   × [cy - tileSize / 2, cy + tileSize / 2]
   ```

   宽、高均为一个 tile。不得用绘制尺寸、轨迹宽度或 actor 半径偷偷改变该碰撞区域。
3. 边界包含。比较时使用冻结的世界坐标 epsilon：

   ```text
   epsilon = 1e-6 × tileSize
   inside = minX - epsilon <= x <= maxX + epsilon
         && minY - epsilon <= y <= maxY + epsilon
   ```

   `tileSize` 必须为有限正数，epsilon 只用于浮点边界容差，不得扩大为玩法缓冲区。
4. 区域中心、边界、关联节点和合法出口在进关时冻结；运行时只能改变占用状态，不能改变区域几何。
5. 同一关卡的 autoDock 区域不得重叠；区域相邻时仍按各自闭合边界和稳定 ID 处理，不能由集合顺序决定归属。

### 4.2 关联节点与合法出口

- 每个 dock 必须引用一个存在的路径节点 ID；不存在或坐标不一致时构造失败。
- `legalExitDirections` 使用现有 `PathNode.Dir` 的 `UP / DOWN / LEFT / RIGHT`，本步骤不新增或修改 `core.Direction`。
- 静态合法出口等于关联路径节点的允许方向集合，并以不可变集合返回。与关闭门、临时占用或当前 tick 状态有关的可用性是动态查询结果，不能篡改静态合法出口。
- 合法方向必须对应真实相邻节点；孤立方向、主动 U-turn 或依赖默认 enum 顺序的方向选择均不合法。
- `defaultExit` 只用于没有有效方向队列时的路径决策，不得替代 autoDock 的合法出口校验。

### 4.3 最近区域查询

最近查询输入为：

- 有限的世界坐标 `worldPosition`；
- 非负有限的 `maxDistance`，按世界单位解释。

候选区域为到矩形的最短欧氏距离不超过 `maxDistance + epsilon` 的 dock。点在区域内时距离为零；点在外部时使用到矩形的最近点距离。选择顺序固定为：

1. 距离平方升序；
2. 距离相等（差值不超过 epsilon）时，机制 stable ID 字典序升序。

没有候选时返回空结果，不返回任意第一个 dock。若需要返回多个候选，集合必须按 stable ID 排序并且不可修改。

## 5. autoDock 占用与事件规格

### 5.1 占用模型

1. 一个 dock 同时最多由一个 actor 占用。`player` 和任意合法的 `echo_<sourceRound>` 都可以占用；权限差异不改变“单 actor”约束。
2. 占用记录至少包含 `mechanismId`、`actorId`、`sourceRound` 和首次占用 tick。actor ID 必须符合第 2.1 节规则。
3. 持续停留不重复产生 `DOCK_ENTERED`。只有前一采样在区域外、当前 tick 的有效位置进入区域内，才产生一次进入事实。
4. 非占用者调用释放不能改变状态，不能产生 `OCCUPANCY_RELEASED`，并返回明确的 `NOT_OCCUPANT` 诊断。
5. 同一个 actor 不能以第二个占用记录覆盖现有占用；另一个 actor 进入已占用 dock 返回 `ALREADY_OCCUPIED`，不改变原占用者。
6. **`Door` 计数口径（X-MOVE-COLLAPSE-01 v2 §B 明确，2026-09-11）**：`Door` 只读 `DockingPlateRegistry.isOccupied(plateId)`，
   **不区分占用者身份**。因此残影占板与当前玩家占板在门条件上**等价** —— 一枚板不会因占用者是 `echo_<n>` 而少算。
   若后续需要按身份区分（例如只允许玩家触发某类门），必须先修改本节契约并经 PM 确认，不得在实现里隐式区分。

### 5.2 进入、离开和 tick 归属

- 位置转换以固定 tick 的采样结果为准。前一有效位置在外、tick `t` 的有效位置在内，进入事件归属于 tick `t`。
- 占用者在 tick `t` 按下**合法出口方向**即产生 `DOCK_LEFT` 并**在同一逻辑刻释放占用**（`LEFT`），**不要求位置已出区域**；同一 tick 内先产生离开事实，再产生释放事实。位置不再参与释放判定（X-MOVE-COLLAPSE-01-DEV3 L-1，PM 2026-09-10 批准；依据 README §三 与 `R5-开工前裁决.md` 裁决 4）。
- 非法方向离开不释放占用，返回 `INVALID_EXIT_DIRECTION`；不能通过瞬移到区域外绕过出口规则。
- 同一 dock 在 tick `t` 发生合法离开后，设置 `reentryBlockedAtTick=t`。该 dock 在同一个 tick 再次进入一律返回 `SAME_TICK_REENTRY_BLOCKED`，即使排序上释放事件已经先发生。
- 同 tick 离开 dock A、进入 dock B 只有在调用方提供了真实的合法移动结果时才允许；不能通过 autoDock 查询制造跨区域瞬移。对同一 dock 的离开/再进入禁止规则始终有效。

### 5.3 轮次和场景边界清理

| 情况 | 清理时点 | 规则 |
| --- | --- | --- |
| 普通轮末 | 当前轮最后一个 gameplay tick 的事件批次完成后、下一轮 tick 0 前 | 清空所有占用和同 tick 防重入标记；不把旧占用带入下一轮 |
| 残影淘汰/消失 | 消失事实所属 tick 的占用处理阶段 | 若残影仍占用 dock，按 `ECHO_ELIMINATED` 原因释放；不得伪造玩家离开事件 |
| 整局重开 | 新会话开始前 | 清空占用、边沿缓存、事件批次和动态状态；旧会话事件不得进入新会话 |
| 场景退出 | 场景销毁阶段 | 释放所有占用并解除场景注册；不得把旧场景对象泄漏给下一关 |

边界清理是状态事务的一部分，必须幂等；重复调用不会产生第二次释放或残留事件。清理产生的诊断信息不能混入新会话的 gameplay 排序批次。

## 6. 已确认的最小只读接口

以下字段和方向已经由步骤 3/4 落地。开发一应依赖只读端口和值对象，不得依赖 `DockingPlate` 实例。

### 6.1 只读返回值

`AutoDockView` 至少包含：

- `mechanismId: String`；
- `pathNodeId: String`；
- `region: DockRegionView`，包含 `minX / minY / maxX / maxY / epsilon`；
- `center: Vector2D`；
- `legalExitDirections: Set<PathNode.Dir>`，不可变；
- `occupancy: OccupancyView`，包含是否占用、`occupantId`、`occupantSourceRound` 和占用 tick；
- 当前 tick 的 `reentryBlocked` 状态或等价的只读边沿信息。

`DockRegionView`、`OccupancyView` 和 `AutoDockView` 都必须是不可变值。返回的集合使用不可修改副本；不能暴露 `DockingPlate`、注册表、UI 节点、可变属性 Map 或其他内部对象。

### 6.2 查询和状态操作方向

方向类型沿用 `PathNode.Dir`。最小能力分为两类，避免 UI 直接操作机关：

```text
AutoDockReadPort
  findById(mechanismId)
  findNearest(worldPosition, maxDistance)

AutoDockOccupancyPort
  tryEnter(mechanismId, actorId, sourceRound, tick, worldPosition)
  tryLeave(mechanismId, actorId, sourceRound, tick, exitDirection, worldPosition)
  reset(reason, tick)
```

返回值应携带明确的结果状态和新的只读 `AutoDockView`，至少区分 `ENTERED`、`ALREADY_OCCUPIED`、`OUTSIDE_REGION`、`NOT_OCCUPANT`、`INVALID_EXIT_DIRECTION`、`SAME_TICK_REENTRY_BLOCKED` 和清理成功。具体名称、参数封装和跨模块端口归属必须在步骤 3/4 开始前由项目经理与调用方确认。

## 7. 当前实现与本规格的差距

这是步骤 2 的现状记录，不是本步骤的实现任务：

- `DockingPlate` 已有非空 ID和点坐标，但未拒绝空白/重复 ID，没有矩形区域、关联节点、合法出口、最近查询或同 tick 防重入；
- `DockingPlateRegistry` 目前以 `ConcurrentHashMap` 注册并可能覆盖重复 ID，没有只读快照，也不能作为全局场景生命周期的最终方案；
- `EntitySpawnInfo` 当前没有稳定实体 ID，并向外暴露可变属性 Map；
- L01/L03 的实体构造器仍使用无 ID 的生成描述，且实体类型字符串不一致；
- 当前 `GameEvent.sourceId` 混用 actor 和机制语义，尚不是稳定记录协议；
- 现有代码没有本文件定义的显式同刻 Comparator；
- 当前 `LevelGeometry` 提供节点和邻接查询，但没有 autoDock 只读查询/占用端口；
- 当前 L01/L03 数据仍需后续步骤验证节点几何、ID唯一性和坐标引用，不能因本规格文档存在而标记为游戏验收完成；
- JavaFX 当前没有可据此证明玩家、机关、残影或 HUD 已接线可见的运行证据。

## 8. 本步骤验收清单

- [x] 机制和 actor 的稳定 ID 规则、禁用来源和现有数据映射已书面固定；
- [x] 排序键固定为 `tick → 类别优先级 → stableId`；
- [x] `DOCK_LEFT < OCCUPANCY_RELEASED < DOCK_ENTERED` 已明确；
- [x] autoDock 区域形状、世界坐标、边界包含和 epsilon 已明确；
- [x] 关联节点、唯一中心、合法出口和最近查询的 tie-break 已明确；
- [x] 单 actor、玩家/残影权限、重复进入、非占用者释放和同 tick 再进入规则已明确；
- [x] 普通轮末、残影淘汰、整局重开和场景退出的清理时机已明确；
- [x] 只读返回字段和最小接口方向已经确认并落地；
- [x] 本步骤不实施代码，不修改禁止路径。
- [x] **（2026-09-10 追加，X-MOVE-COLLAPSE-01-DEV3 L-1 / `ENT-2a`）** 同刻释放：合法出口按下即 `LEFT` 并清占用，不要求位置出界；`NOT_OUTSIDE_REGION` 保留枚举但标 `@Deprecated`，不再由 `tryLeave` 产生。经 PM 批准，与开发一 `ENT-2b` **成对合并**（集成分支 `codex/ent2-paired`），不单独进入 `develop`。

W1/W4 已在 `mechanism/**`、`level/**` 及开发三测试范围内按本规格落地；下一步 W2 负责修复第一关路径图并完成 `LevelGeometryImpl` 构造验收。

---

## 9. 注册表与事件总线的生命周期（BUG-002-LIFECYCLE Phase 1 + Phase 2）

> 依据：PM 裁决 `BUG-002-注册表与事件总线生命周期-PM裁决.md` §六；Phase 2 任务卡
> `BUG-002-LIFECYCLE-Phase2-任务卡-开发三.md`；射线条目经 `L02-门房与双残影-PM裁决.md` §六 修订。
> 日期 2026-09-12（Phase 1）／**2026-09-14（Phase 2：单例已删除）**。

### 9.1 实例归属（Phase 2 起：不存在全局单例）

- `DockingPlateRegistry` 与 `EventDispatcher` 的**唯一形态是"每个关卡装配持有自己的实例"**；
  同一实例内的驻留板 ID 必须唯一，**跨实例同名 ID 互不冲突**。
- **静态单例访问器与全部"取单例"的兼容构造器已在 Phase 2 删除**（`DockingPlate` / `Door` / `ExitTerminal`
  只剩注入构造器），因此"有的板注册到单例、有的注册到实例"的分裂状态**在类型层面已不可能出现**。

### 9.2 注入优先（强制）

- 唯一可用的构造器即注入形态：
  - `DockingPlate(id, position, DockingPlateOccupancyPort, GameEventBus)`（开关变体另有 `+boolean latching`）
  - `Door(id, position, requiredPlateIds, DockingPlateOccupancyPort, GameEventBus)`
  - `ExitTerminal(id, position, associatedDoorId, interactRadius, GameEventBus)`
- 消费者（`Door` 等）只依赖窄端口 `DockingPlateOccupancyPort`，**不得**直接引用 `DockingPlateRegistry`。

### 9.3 场景退出 / 重开时必须释放的引用

| 时机 | 必须做的动作 |
| --- | --- |
| 普通轮末 | `DockingPlate.reset()` + `Door.reset()` + `ExitTerminal.reset()`（现有轮末事务） |
| 整局重开 / 场景退出 | 释放装配持有的注册表与总线实例（**不再存在任何全局清理入口**）；机关 `dispose()` 反注册 |
| 关卡装配销毁 | 旧实例不得被 Canvas / listener / 缓存继续持有（与 `RecordingSession` 契约同款要求） |

### 9.4 Door 计数口径（与 §5.1 第 6 条一致）

`Door` 通过 `DockingPlateOccupancyPort.isOccupied(plateId)` 判定，**不区分占用者身份**；
残影占板与当前玩家占板在门条件上等价。

### 9.5 Phase 2 前置结论与执行结果

- 结论（Phase 1 已核，Phase 2 复用）：`DockingPlate.Snapshot`、`MechanismSnapshot`、`AutoDockSnapshotPort`
  的**生产代码不引用**注册表或事件总线单例；其**测试**曾在 `snapshot/**`、`level/**`、`app/**` 中出现
  全局清理调用，已在 Phase 2（及一次性的 `app/**` 清理）中全部迁移为每用例独立实例。
- **Phase 2 执行清单（已完成）**：删除 `DockingPlateRegistry` 与 `EventDispatcher` 的静态单例访问器；
  删除 `DockingPlate`（2 个）、`Door`（2 个）、`ExitTerminal`（2 个）兼容构造器；
  迁移 `level/Level01TwoRoundSimulationTest`、`snapshot/MechanismSnapshotTest` 到独立实例。

### 9.6 `mechanism/ray/Ray.java` 登记（**改为保留**）

- `mechanism/ray/RayManager.java`：**已删除**（零生产引用）。
- `mechanism/ray/Ray.java`：**保留**。`L02-门房与双残影-PM裁决.md` **§六 已作废**「Phase 2 删除 Ray.java」一条：
  第二关 L2-B 需要真实的时滞射线，Ray 从"零引用死代码"转为"有待接线需求"。
- **因 Phase 2 删除单例，Ray 已在本批改为注入形态**（构造器接收 `GameEventBus`，不再取全局单例）；
  「共享 `roundTick` 驱动 + 关卡接线 + 相位/减速语义」仍属 **L2-B**。
- R5-B §10.2 的结论继续成立：**射线不需要快照端口**（无持久状态），聚合器不得为其加具体类旁路。

---

## 10. 共振复位边界与 R5-B 聚合端口结论（R5-B 冻结，2026-09-13）

> 依据：`R5-B-聚合接口冻结-PM裁决.md` §四、§五。本节只冻结语义，不含实现细节。

### 10.1 `ResonanceResetReason` 三值语义

| 取值 | 触发时机 | 结果 |
| --- | --- | --- |
| `ROUND_END` | 普通轮末（非最终轮） | 轮内状态与边沿记忆全部清空，下一轮从 `DORMANT` 开始 |
| `FULL_RESTART` | 整局从第一轮重新开始 | 同左（清除全部共振轮内状态） |
| `SCENE_EXIT` | 退出关卡场景 | 同左（放弃本会话） |

三条不变量：

1. **三个原因的实现行为完全一致** —— `ResonanceStateMachine.reset(reason)` 不按原因分支
   （只 `requireNonNull` + 清态）。区分的意义在**调用方语义**：聚合器与 app **不再**把
   "场景退出"降级映射成 `FULL_RESTART`。
2. **枚举按名字序列化、无 ordinal 依赖** —— 全仓 `ResonanceResetReason` 引用点极少
   （枚举定义、`reset` 的 `requireNonNull`、javadoc、测试），**无 `ordinal()` / `values()` 用法**，
   因此新增 `SCENE_EXIT` 是源码兼容变更。
3. **取值集合与 `AutoDockResetReason` 对齐**（均为 `{ROUND_END, FULL_RESTART, SCENE_EXIT}`），
   并有测试断言两者的常量名集合相等。

### 10.2 R5-B 聚合端口结论：射线 / 中继 / 核心

R5-B 交接文档提到"射线 / 中继 / 核心"是否需要各自快照端口。开发三按 live source 核对后的结论：

| 机制 | 是否存在实现 | 端口结论 |
| --- | --- | --- |
| **射线** | `mechanism/ray/Ray.java` 存在但**零引用**，且已列入 BUG-002 Phase 2 删除清单（见 §9.6） | **不需要端口**。若第二关将来真要实现"时滞射线"，须重新立项并补端口提案；**不得让聚合器用具体类旁路** |
| **中继**（`relay_i` / `relay_ii`） | **不存在实现类**（`mechanism/**` 下无 relay 相关文件） | **暂无端口需求**；实现时另开卡补第 6 个 typed port |
| **核心**（`core` 机关） | **不存在实现类** | 同上 |

补充：`ResonanceStateSnapshot` 覆盖的是**固定区域共振**（`DORMANT` / `ARMED` / `LATCHED` 等状态），
**不覆盖**中继或核心 —— 后两者是否复用共振状态机，须在其实现时单独评估，**不得假定已被覆盖**。

---

## 11. 锁存开关与快照字段冻结（L01-GATE-MERGE，2026-09-14）

> 依据：PM 裁决 `L01-门与终点合并-PM裁决.md`（含 §十/§十一 追加裁决）+ 项目方 2026-09-14 冻结确认。
> 本节记录**已落地并被冻结**的接口，不再是提案。
> 分支：`feature/content-l01-gate-merge`（开发三）。

### 11.1 闸门终点节点

| 项 | 冻结值 |
| --- | --- |
| 稳定 ID | **`L01_node_c18_r7`** |
| 世界坐标 | **(888.0, 360.0)**（格 (18,7)，tileSize 48） |
| 与开关 `L01_plate_right` (18,8) 距离 | 48（**1 格**，可读性 + 站在开关上即可按 E） |
| 与左驻留板 `L01_plate_left` (216,312) 距离 | **673.71 > 72**（防「占着左板直接按 E」的单轮通关） |
| 割点检查 | 删掉该节点后 `spawn→左板`、`spawn→开关` 仍连通（非割点） |
| 门与出口 | `L01_door_01` 与 `L01_exit_00` **同格同节点**；原终点节点 `L01_node_exit_terminal` 保留未删 |

### 11.2 开关变体（`role=switch`）的锁存语义

- 实现位置：**`DockingPlate` 的变体状态**（不新建机关类）；5 参构造 `DockingPlate(id, position, occupancy, bus, latching)`，4 参构造等价 `latching = false`。
- 触发：`tryEnter` 成功即置 `latched = true`，**复用 `PLATE_ENTERED`，不新增事件类型**；残影可触发。
- **离开不清锁存**；**残影淘汰（`ECHO_DISAPPEARED`）也不清锁存**（只释放占用）。
- 单向：本轮内只 `false → true`，重复触发幂等；锁存不阻止再次踩上。
- 复位：`reset()` 归零（普通轮末 / `FULL_RESTART` / 场景退出共用）；app 的 `restart()` 与轮末事务均已调用 `reset()`。
- 只读查询：**`isLatched()`**（供 `RenderViews.MechanismKind.SWITCH.active`）。

### 11.3 `isOccupied()` 的语义扩展（**PM 已认可**）

`DockingPlate.isOccupied()` = `state == OCCUPIED || latched`。理由与边界：

1. `Door` 只依赖 `DockingPlateOccupancyPort.isOccupied(plateId)`，且**行为不得修改**；锁存若不并入本判定，玩家离开开关的瞬间门会重新上锁。
2. 因此本扩展**只对开关变体**有可观察影响；普通驻留板语义不变。
3. 「此刻是否有人站着」= `getState()`；「开关是否已触发」= `isLatched()`；**渲染不得用 `isOccupied()` 代替锁存 ON**。
4. 原提案中「改 `DockingPlateRegistry` 一行」的方案因**不在本卡允许路径内**而未采用；替代方案即本节。

### 11.4 快照字段（**冻结**）

```java
public interface Snapshot {
    default boolean isLatched() { return false; }        // 新增，默认值保证既有实现可编译
}
public record StateSnapshot(String mechanismId, State state,
                            String occupantId, int occupantSourceRound,
                            boolean latched) implements Snapshot { }   // 第 5 个分量
```

| 项 | 冻结值 |
| --- | --- |
| 字段 | **`DockingPlate.StateSnapshot.latched`**（`boolean`） |
| `reset()` | `false`（OFF） |
| `restore()` | 取快照值；无锁存位的旧快照（4 参兼容构造器）等价 `false` |
| 交叉不变量 | `latched ⟹ latching`；与 `state` **独立**（`(UNOCCUPIED, latched=true)` 是常态）；本轮单调 `false→true`；与 `reentryBlockedAtTick` 无耦合 |
| **不改** | `validateState(...)` 无需改动 |
| **不加** | `AutoDockStateSnapshot.DockSnapshot` **不加** `latched`（避免第二个真相源） |
| 开发二待办 | `snapshot/MechanismSnapshot` 的装配处（L62–64）改为透传 `snapshot.isLatched()`，并补 3 条往返测试 |
