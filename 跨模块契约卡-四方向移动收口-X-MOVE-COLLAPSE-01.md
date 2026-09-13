# 跨模块契约卡 · 四方向移动收口（X-MOVE-COLLAPSE-01）

> 任务 ID：**X-MOVE-COLLAPSE-01**
> 责任：**开发一（provider）× PM（集成）× 开发三（关卡/机关）**
> 状态：**待 PM 发卡**（ENT-1 之前的 P0 修复见 `四方向受约束移动-任务卡-开发1-C-PLAYER-MOVE-02.md` §六）
> 日期：2026-09-10
> 依据：`README.md`（§一/§三/§四/§十六 已按选项 2 修订，`develop` `b44de66`）、`docs/decisions/R5-开工前裁决.md`、`docs/decisions/C4-前置跨界裁决.md`
> 前置分支：`feature/player-move-01` `22e8f8e`、`feature/player-move-02` `01d98da`、集成 `codex/player-move-02-app-adapt` `cd9aede`（含 PM 临时热修 `e17bf4a`，261 条测试全绿）

## 〇、为什么要这张卡

移动模型从「恒速巡行 + 五级自动选路」改成「玩家驱动 + 路径中心线约束」后，**四个模块的接缝同时失效**，但每一处单独看都像“顺手清理”，容易各自为政地改签名。本卡把接缝一次性冻结：谁改哪个公共签名、谁消费、谁验收、什么顺序合并。

**已完成、不要重做**：`core/MovementState.IDLE`、`entity/PatrolController` 四方向输入与真死路豁免（P0-A/P0-B）、`app/Level01Assembly` 签名适配（held 集合 + newestEdge + 单槽意图 + 驻留中心补走 + 离开方向优先）、`app/Level01AssemblyMovementTest`（9 条）。

---

## 一、契约清单

### CORE-1 · 四方向输入契约（核心词汇唯一化）

| 项 | 内容 |
| --- | --- |
| 现状 | `LogicalKey → Direction` 的映射在 **两处重复实现**：`app/InputAccumulator.toDirection`（L62）、`app/Level01Assembly.heldDirections`（L244）；四方向语义（held 集合 / newestEdge）目前只在 `entity` 与 `app` 的注释里 |
| 契约变更 | `core/input/LogicalKey` 新增 `Optional<Direction> direction()`（非方向键返回空）；`core/input/InputIntent` 新增 `Set<Direction> heldDirections()`，`lastDirectionEdge()` 语义写入 javadoc：「本刻最后新按下的方向；**是否保留到节点中心由消费方决定**」 |
| Provider | 开发一（`core/input/**`） |
| Consumer | `app/InputAccumulator`、`app/Level01Assembly`（删掉各自的 switch，只调用 core 映射） |
| 验收 | `grep -rn "case DIR_UP" src/main` 为空（当前唯一一处在 `app/InputAccumulator`）；`InputIntentTest` 补 `heldDirections()` 用例（含「按住前进+垂直」「同刻相反」两种组合） |
| 禁止 | 不得把 `Direction` 逻辑放进 `app/`；不得在 `InputIntent` 里加转向/排队状态（输入类型保持无状态） |

### CORE-2 · 速度与移动参数冻结

| 项 | 内容 |
| --- | --- |
| 现状 | `entity/PatrolConfig` 仍带 `turnLockDistance`（节点锁定区），但 `PatrolController` 已**完全不读**（只读 `baseSpeed()`/`epsilon()`），唯一引用是 `PatrolConfigTest` 断言 `7.2` → 死参数；`core/MovementState.SLOWED` 在 main 里**没有任何生产者**（射线减速的书写入没有接到速度上） |
| 契约变更 | ① `PatrolConfig` 删除 `turnLockDistance` 并更新 `c2Greybox()`（**破坏性签名变更**）；② 冻结 `tileSize=48`、`baseSpeed=2`、`epsilon=4.8e-5` 为唯一速度来源；③ 新增速度修正端口：`entity` 提供 `SpeedModifierPort`（本刻乘子，`1.0` = 正常），射线命中写入 `slowMultiplier`/`slowDurationTicks` 后，移动层按 `baseSpeed × 乘子` 推进，并在乘子 < 1 时输出 `MovementState.SLOWED` |
| Provider | `core`（枚举语义）+ `entity`（`PatrolConfig`、`SpeedModifierPort`） |
| Consumer | `mechanism/ray/**` 写入减速、`app/Level01Assembly` 注入端口、开发三的到达刻模拟 |
| 验收 | ① `PatrolConfigTest` 改为断言三个冻结参数；② 新增测试：命中射线后 N 刻内位移量 = `baseSpeed × 0.5`、`movementState == SLOWED`、恢复后回到 `CRUISING`；③ `mvn -o test -Dtest=PatrolController*` 1 秒内结束 |
| 禁止 | 不得在 `app/` 里再算速度；不得让渲染层参与速度 |

### CORE-3 · 自动选路移除（死代码清零）

| 项 | 内容 |
| --- | --- |
| 现状 | `core/path/PathExitSelector.select(...)` + `PathExitDecision` 在生产代码里**已无调用者**（仅 `PathExitSelectorTest`）；core `PathNode.defaultExit` 仅被 `select()`、`app/PathGraphBridge` 映射与两个测试使用；五级优先级/默认出口/死路自动返回全部属于被废止的自动巡行 |
| 契约变更 | ① 删除 `PathExitSelector.select` 与 `PathExitDecision`，**保留 `isPassable(...)`**（真死路豁免依赖它）；② core `PathNode` 删除 `defaultExit` 字段（**破坏性**），构造校验随之简化；③ `PathExitSelectorTest` 重写为纯判定测试，`PathNodeTest` 删掉 defaultExit 用例 |
| Provider | 开发一（`core/path/**`） |
| Consumer | `app/PathGraphBridge`（见 APP-1）、`level/**`（见 LEVEL-0） |
| 验收 | `grep -rn "defaultExit" src/main/java/org/example/timeloop/core` 为空；`grep -rn "PathExitSelector.select" src` 为空 |
| 禁止 | 不得保留「注释掉的五级优先级」；不得让任何自动选路逻辑以“兜底”名义复活 |

### ENT-1 · 玩家移动收口（P0-D/P0-E 的正式修复）

| 项 | 内容 |
| --- | --- |
| 现状 | PM 集成实测：**P0-E 死循环**（站在段终点中心直行时 `nextDir == direction` 不推进 segment → `dist == 0` → `budget` 不减；开发一自己的 `PatrolControllerDeadEndTest#junctionWithSideExit_stillRejectsReversal` 挂死 >100s）；**P0-D 崩溃 + 规则偏差**（真死路豁免未判「反方向确有出口」→ 出生点按 `UP` 抛 `NoSuchElementException`；未判「当前朝向不可通行」→ 普通直廊节点也能掉头）。PM 已用 `e17bf4a` 临时热修死循环 |
| 契约变更 | ① 采用正式修复替换 `e17bf4a`（判定 `nextDir != direction || center.id().equals(segmentEndNodeId)`）；② 真死路豁免三条判定齐全：当前朝向**不可通行** + 反方向**确有出口** + 无 90° 可通行出口；③ **单槽方向意图归位**：把 PM 在 `app/Level01Assembly.pendingTurn` 里实现的「按住期间保留、松手或提交后作废」移进 `PatrolController`（`advance` 签名不变，只加内部状态与测试）；④ javadoc 写清「`newestEdge` 是**本刻**边沿，保留语义由控制器负责」 |
| Provider | 开发一（`entity/PatrolController`） |
| Consumer | `app/Level01Assembly`（删掉 `pendingTurn`，只做 `LogicalKey → Set<Direction>` 转换） |
| 验收 | ① `PatrolControllerDeadEndTest` 全绿且 **< 1s**；② 新增「连续直行穿过 ≥3 个节点」测试；③ 新增「出生点按反方向 → 不崩且静止」；④ `app/Level01AssemblyMovementTest` 里两条 `currentBehaviour*` 断言翻转为「静止 / 不允许掉头」，`pendingTurn` 相关测试改为直接打控制器 |
| 禁止 | 不得用 `try/catch NoSuchElementException` 掩盖出口缺失；不得恢复 `queueDirection` 式的“到路口才提交”外部 API |

### ENT-2 · autoDock 离开刻接线（R5 裁决 4）

| 项 | 内容 |
| --- | --- |
| 现状 | README §三：「离开驻留型机关时，角色立即按所选方向以 `baseSpeed` 出发，**对应占用同时释放**」。实现相反：`AutoDockService.tryLeave` 要求位置**已在区域外**（`NOT_OUTSIDE_REGION`），`DOCK_LEFT` 记在**出界刻**，`C3DockController` 类注释自认“已知偏差（待 PM 裁决）”；另外 autoDock 区域是边长 `tileSize` 的方格，玩家在离中心半格处就被判定进入，PM 只能在 `app/` 用“先走到机关中心再 `DOCKED`”兜住 |
| 契约变更 | ① （开发三）放宽 `AutoDockOccupancyPort.tryLeave`：占用者在离开刻**按下合法出口即返回 `LEFT` 并释放**，不再要求位置出界；② （开发一）`C3DockController` 在**离开边沿成立的同一刻**发 `DOCK_LEFT` 并释放，`DockEventReason.NEW_DIRECTION` 语义不变；③ 停驻位置语义写死为「**机关所在路径节点中心**」：要么由 entity 提供显式的 `snapToNodeCenter`/走到中心语义，要么在 README §三 明确“进入区域后先沿中心线走到机关中心再停驻”并把 PM 的 app 侧实现登记为契约行为（PM 倾向后者，避免 entity 依赖 autoDock 几何） |
| Provider | 开发三（`mechanism/autodock/**`）× 开发一（`entity/C3DockController`）× PM（`app` 接线 + README 措辞） |
| Consumer | `app/Level01Assembly`（删除“走不出区域就卡住”的兜底注释）、残影回放（离开刻参与 `TimelineEvent` 排序） |
| 验收 | ① 新增测试：进入驻留板 → 同刻按合法出口 → **同刻** `DOCK_LEFT` + 占用释放 + 位置仍在区域内；② 停驻位置 == 机关节点中心（`app/Level01AssemblyMovementTest.dockingSnapsToMechanismCenterAndLeavingReleasesOccupancy` 保持绿）；③ 轮末/整局重开/退出的 `OCCUPANCY_RELEASED` 语义不变 |
| 禁止 | 不得让 `app/` 自己释放占用；不得用“位置出界”当释放条件 |

### APP-1 · 旧 defaultExit bridge 清理

| 项 | 内容 |
| --- | --- |
| 现状 | `app/PathGraphBridge.toGraph()` L44-L51 把 `level.model.PathNode.getDefaultExit()` 映射进 core `PathNode`；CORE-3 删掉 core 字段后此处编译失败 |
| 契约变更 | `PathGraphBridge` 退化为**纯几何桥**（只做 1-tile 邻接 → `PathExit`，不再传 defaultExit）；关卡数据侧 `level.model.PathNode.defaultExit` 与 `LevelGeometry.getDefaultExit` 是否保留由 LEVEL-0 决定 |
| Provider | PM（`app/**`） |
| Consumer | 无（bridge 仅被 `Level01Assembly` 使用） |
| 验收 | `PathGraphBridge` 无 `defaultExit` 字样；`new LevelGeometryImpl(Level01Footsteps.build())` 仍可构造 |

### LEVEL-0 · 关卡数据侧 defaultExit 去留（开发三）

| 项 | 内容 |
| --- | --- |
| 契约变更 | `level.model.PathNode.defaultExit` 与 `LevelGeometry.getDefaultExit` **只保留给关卡编写/校验**，必须不参与任何移动决策；`Level01Footsteps` 的 `L01_node_fork` 默认出口可保留（编写用）或删除（推荐删除，避免“看着像玩法配置”） |
| 验收 | `grep -rn "getDefaultExit" src/main/java/org/example/timeloop/{app,entity}` 为空；`LevelGeometryImpl` 的 defaultExit 校验仍通过（若字段保留） |

### LEVEL-1 · 第二关 / 第五关到达刻重算（开发三，PM 冻结公式口径）

| 项 | 内容 |
| --- | --- |
| 现状 | 旧到达刻公式按**恒速巡行**（无需输入、速度恒定）成立；现在到达刻 = 玩家按键脚本的函数，且 `SLOWED` 目前**没有生产者**（CORE-2），所以两条公平性公式当下**无输入可用**：`noLagArrivalTick + interactionBufferTicks <= durationTicks - successMarginTicks`、`laggedArrivalTick > durationTicks`（第二关）；`abs(arrivalTickA - arrivalTickB) > resonanceWindowTicks`、`abs((arrivalTickA + delayTicks) - arrivalTickB) <= resonanceWindowTicks`（第五关） |
| 契约变更 | ① 到达刻**只能取固定步长模拟值**，禁止用估算公式（README §九 原文要求）；② 模拟输入 = 冻结的**按键脚本**（按住/松开的 tick 序列），脚本随关卡参数文档一起版本化；③ 第二关 L02 数据尚不存在 → 顺序为「先做 L02 灰盒与射线走廊 → 录制预期按键脚本 → 固定步长模拟 → 用实测刻定参 → 更新参数文档与测试」；④ 第五关在 `SLOWED` 生产者就位后重算 A/B 两条路线的到达刻对照 |
| Provider | 开发三（`level/**` 关卡数据 + 模拟）+ PM（README 参数文案） |
| 验收 | ① 第二关门禁条件由模拟断言（数值化）；② 第五关「避开时超窗、接受同一时滞后入窗」由模拟断言；③ 参数文档记录脚本与实测刻；④ 无“共振前 autoDock”、无“撞墙等待”、无普通绕路等价延迟 |
| 依赖 | 必须等 CORE-2（速度/`SLOWED`）与 ENT-2（离开刻）稳定后定稿 |
| 禁止 | 不得回填“旧公式算出来差不多”的估算值；不得用改关卡碰撞/绕路代替时滞 |

---

## 二、依赖与合并顺序

```
CORE-3 ─┬─> APP-1 ─────────────────┐
        └─> LEVEL-0 ───────────────┤
CORE-1 ────────────────────────────┤
CORE-2 ──> ENT-2 ──> LEVEL-1 ──────┤
ENT-1（P0 修复，最高优先）─────────┘
```

1. **ENT-1** 必须先落地（否则任何直行过节点都会挂死，其余验证无意义）；
2. **CORE-1/CORE-3** 可并行；CORE-3 合并后 **APP-1** 立刻跟上（否则 `app` 编译失败）；
3. **CORE-2** 是 **LEVEL-1** 的前置（到达刻需要 `baseSpeed` 与减速乘子稳定）；
4. **ENT-2** 与 **LEVEL-1** 串行：离开刻语义会影响残影回放的停驻长度；
5. 合并顺序：`feature/player-move-02`（含 ENT-1）→ `codex/player-move-02-app-adapt`（app 适配，PM）→ CORE-1/CORE-3+APP-1 → CORE-2+ENT-2 → LEVEL-0/LEVEL-1。

---

## 三、公共签名变更审批（R5 裁决 2）

以下为**破坏性公共签名**，需 provider + 全部 consumer + PM 三方确认后才可改：

| 签名 | 变更 | Provider | Consumer |
| --- | --- | --- | --- |
| `core/input/LogicalKey#direction()` | 新增 | 开发一 | `app/InputAccumulator`、`app/Level01Assembly` |
| `core/input/InputIntent#heldDirections()` | 新增 | 开发一 | `entity/PatrolController` 调用方、`app` |
| `core/path/PathNode`（去 `defaultExit`） | 删除字段 | 开发一 | `app/PathGraphBridge`、`level/**`、测试 |
| `core/path/PathExitSelector#select` / `PathExitDecision` | 删除 | 开发一 | 测试、`PathNodeTest` |
| `entity/PatrolConfig`（去 `turnLockDistance`） | 删除字段 | 开发一 | `app/Level01Assembly`、测试 |
| `mechanism/autodock/AutoDockOccupancyPort#tryLeave` | 语义放宽（签名可不变） | 开发三 | `entity/C3DockController`、`app` |
| `entity/PatrolController#advance` | **不变**（单槽意图转为内部状态） | 开发一 | `app/Level01Assembly` |

---

## 四、允许 / 禁止修改路径

- 开发一：`core/**`、`entity/**`、对应 `src/test/java/.../{core,entity}/**`、`docs/development/开发1/**`。
- 开发三：`level/**`、`mechanism/**`、`ui/**`、`persistence/**`、对应测试、`docs/development/开发3/**`。
- PM：`app/**`、`README.md`、`docs/decisions/**`、任务卡。
- 全员禁止：`pom.xml`、`replay/**`、`snapshot/**`、`src/main/resources/**`；跨板块改动一律先出提案（本卡即提案载体）。

---

## 五、总验收

1. `.\mvnw.cmd -o test` 退出码 0，且**无任何单个测试超过 1 秒**（死循环/挂死防线）；
2. `grep -rn "defaultExit" src/main/java/org/example/timeloop/{core,entity,app}` 为空；
3. `grep -rn "turnLockDistance" src/main` 为空；
4. `MovementState.SLOWED` 至少有一个生产者与一条测试；
5. `app/PathGraphBridge` 只做几何换算；
6. 第一关链路可走通（分两段验收）：① 单轮内走到任一块驻留板、离开并把占用释放干净（已有 `dockingSnapsToMechanismCenterAndLeavingReleasesOccupancy` 覆盖）；② **两轮内**由上一轮残影占板 + 本轮玩家占另一块板 → 门开 → 出口终端按 `E` 结算通关（需集成测试或录制证据）；
7. 第二关/第五关到达刻用固定步长模拟值定稿并写入参数文档。

---

## 六、开工授权（PM 已批，2026-09-10，致开发一）

前置状态：`develop` = `8140bdb`（PR #49 已合入 C-PLAYER-MOVE-02，全量 283 条 0 失败）。

### 6.1 任务 ID

**唯一 ID = `X-MOVE-COLLAPSE-01`（本卡）。** `P-MOVE-ALIGN` 是**开发三**那张《四方向受约束自由移动-任务卡-开发3》的 ID
（关卡对齐/到达刻），两张卡不要混用：`X-MOVE-COLLAPSE-01` 管 core/entity/app 的跨模块收口，
`P-MOVE-ALIGN` 管 level/mechanism/ui 侧与到达刻。开发一的交付文档、分支、PR 一律引用 `X-MOVE-COLLAPSE-01`。

### 6.2 分支与边界（确认）

- 分支名：**`feature/player-move-align` 批准**（建议 PR 标题写成 `X-MOVE-COLLAPSE-01 ...` 便于检索）。
- 允许：`core/**`、`entity/**`、对应 `src/test/java/.../{core,entity}/**`、`docs/development/开发1/**`。
- **禁止（确认）**：`level/**`、`mechanism/**`、`ui/**`、`persistence/**`、`replay/**`、`snapshot/**`、`pom.xml`、`src/main/resources/**`，以及 `app/**`（PM 的路径）。
- **遇到 `app/` 编译失败的唯一正确动作是停下来交接**，不要为了“让全量绿”去动 app。

### 6.3 交付文档

`docs/development/开发1/X-MOVE-COLLAPSE-01-交付.md`（标题里注明“原称 P-MOVE-ALIGN”即可，路径按本卡 ID）。

### 6.4 提交顺序（**已修正，按此执行**）

原提议 `CORE-3 → CORE-2 → CORE-1 → ENT-1 → ENT-2` 会在**第 1 个提交就跑不出全绿**：
CORE-3 删掉 core `PathNode.defaultExit` 后 `app/PathGraphBridge` 立刻编译失败（它是 PM 的路径）。
改为：

| # | 提交 | 全量测试 | 说明 |
| --- | --- | --- | --- |
| 1 | **CORE-1** | ✅ 绿 | 纯新增（`LogicalKey#direction()`、`InputIntent#heldDirections()`），不破坏任何调用方 |
| 2 | **CORE-2** | ✅ 绿 | 删 `turnLockDistance`（app 只用 `c2Greybox()`）+ 新增 `SpeedModifierPort` + `PatrolController` 按乘子推进并产出 `MovementState.SLOWED`。**射线的写入端不在本卡**（`mechanism/ray` 尚未实现），本卡只做端口 + 移动层消费 + 用假端口写的单测 |
| 3 | **ENT-1** | ✅ 绿 | 单槽意图移入 `PatrolController`（`advance` 签名不变；`app` 仍编译）。作废规则必须与 app 现有实现一致：**按住期间保留、松手作废、提交后作废**；PM 随后成对删除 `app.pendingTurn`（行为等价，双份 latch 也不会重复转向） |
| 4 | **CORE-3** | ⚠️ 预期红 → 停 | 删 `PathExitDecision`/`PathExitSelector.select`/core `PathNode.defaultExit`。此提交后 `app/PathGraphBridge` 编译失败是**预期**，push 后立即通知 PM；PM 在同一集成分支上补 **APP-1**（bridge 只做几何换算），随后全量必须绿 |
| 5 | **ENT-2b** | 仅当 2a 已进 develop | `C3DockController` 在离开边沿成立的**同一刻**发 `DOCK_LEFT` 并释放。**必须在开发三的 ENT-2a 之后**（见 6.5） |

每步都要跑 `.\mvnw.cmd -o test`，并在交付文档里记录每步的 `Tests run` 数字与耗时（**任何单测 > 1s 视为死循环回归**）。

### 6.5 ENT-2 拆卡（PM 裁决）

事实核对（`develop` `8140bdb` 实测）：

- `AutoDockService` **就是** `AutoDockOccupancyPort` 的实现类
  （`public final class AutoDockService implements AutoDockReadPort, AutoDockOccupancyPort, AutoDockSnapshotPort`），
  按类名搜 `*AutoDockOccupancyPort*` 找不到是正常的；搜实现请搜 `implements AutoDockOccupancyPort`。
- `tryLeave` **仍是旧语义**：`if (state.definition.region().contains(worldPosition)) return NOT_OUTSIDE_REGION;`
  —— 位置在区域内一律拒绝。
- 开发三卡里那句是**要求**（“放宽 `NOT_OUTSIDE_REGION`：占用者同刻按下合法出口即可释放” + 验收第 4 条），
  **不是已完成状态**；`R5-开工前裁决.md` 裁决 4 也把主责判给开发三。develop 上没有任何 autodock 语义改动。

因此 ENT-2 拆为：

- **ENT-2a（开发三，`mechanism/autodock/**`）**：放宽 `tryLeave`——占用者按合法出口时**同刻返回 `LEFT` 并清占用**，不要求位置出界；补 `AutoDockServiceTest` 用例（在区域内 → `LEFT` + `occupancy` 清空）。
- **ENT-2b（开发一，`entity/C3DockController`）**：在离开刻调用并记录事件。
- ENT-2b 的接线由 PM 在 `app/` 校验（`mirrorDockEvents` 与残影回放的 tick 归属）。

---

## 七、ENT-2a/ENT-2b 成对合并裁决（PM，2026-09-10）

开发一提交了 L-1 技术核查与实施方案（`previousInsideMechanismIds` 边沿检测 + 同 tick `tryLeave`）。
**技术结论 PM 认可**（复核：`cruiseStep` 只做本刻 `findNearest` + `tryEnter`，无“上一刻是否在区域内”的记忆；
`dockedStep` 在边沿刻只设 `departureDirection`、下一 tick 才 `tryLeave`；
`C3DockControllerTest.exitingRegionReleasesAndEmitsDockLeftWithDirectionAndReason:130-138` 正是把“出界刻才 LEFT”写死）。

### 7.1 裁决：**成对合并**，任一单独进 develop 都是回归

PM 推演了两半单独合入的后果，两者都会把玩家钉在驻留板上：

| 单独合入 | 后果 |
| --- | --- |
| **只合 ENT-2a（放宽 tryLeave）** | 旧控制器仍在**下一 tick** 才 `tryLeave`，此时位置还在区域内 → 立刻返回 `LEFT` 并清占用 → 再下一 tick `cruiseStep` 发现仍在区域内 → `tryEnter` 成功（同 tick 防重入只挡一刻）→ **重新 DOCK_ENTERED**。此后玩家需要**新的方向边沿**才能再离开，而按住不放不产生边沿 → 永久驻留、驻留板永久占用 → **第一关无解**（比现状更糟） |
| **只合 ENT-2b（新控制器）** | 方案 B 规定 `tryLeave` 非 `LEFT` 时保持 `FREEZE`；在旧语义下玩家永远无法走到区域外 → 无法离开 → 同样永久驻留 |
| 两者一起 | 边沿刻 `tryLeave` 返回 `LEFT`（区域内也放行）+ 实体层 `outside → inside` 边沿抑制二次进入 → 正确 |

**结论**：ENT-2a 与 ENT-2b 必须落在**同一个 develop 状态**（同一个 PR，或 2a 先行但在同一批次内立刻接 2b，中间不得发版/不得让别人基于中间态验证）。

### 7.2 授权与基线

| 责任 | 基线 | 允许路径 | 交付 |
| --- | --- | --- | --- |
| 开发三 ENT-2a | `origin/develop`（现 `202e7ad`） | `mechanism/**` + 其测试 | 分支 + `AutoDockServiceTest` 新用例 |
| 开发一 ENT-2b | **同一个 `origin/develop`**（不要挂在他们尚未推送的 CORE 分支上；ENT-2b 只碰 `entity/C3DockController` + 其测试，独立可编译，用假端口即可自证） | `entity/C3DockController`、`entity/C3DockControllerTest` | 分支 + 4~5 条新用例 |
| PM 整合与验收 | 集成分支 `codex/ent2-paired` = `develop` + 2a + 2b + app 轮转/接线 | `app/**`、合并与验收 | 全量绿 + app 层集成证据 + 合并哈希回报 |

### 7.3 对开发一方案的 4 点补充（其余照办）

1. **`previousInsideMechanismIds` 的清理时机**：每 `step()` 末尾更新；**只在 `reset(...)`（轮末/整局重开/场景退出）清空**，**不要在 `clearDockState()` 里清**——离开刻清掉它就会立刻丢掉“仍在区域内不重入”的保护。清空后若玩家正好站在区域内，首个 `step()` 会正常判定为 `outside → inside` 并驻留（这是期望行为）。
2. **`tryLeave` 非 `LEFT` 时**：保持 `FREEZE` 且**保留本地驻留状态**（不要清 `dockedMechanismId`）；不要保留跨 tick 的 `departureDirection` 状态（方案 B 已如此）。
3. **补第 5 条测试**（组合后才出现的真实序列）：离开刻之后玩家**松开按键**、位置仍在区域内 → 后续 tick **不得**产生新的 `DOCK_ENTERED`，占用保持已释放。PM 会在 app 层补等价集成测试。
4. `DOCK_LEFT` 的契约不变：`event.tick()` = 按键/离开刻、`leaveDirection` = 请求方向、`reason` = `NEW_DIRECTION`。

### 7.4 前置阻塞（两人都未推送）

截至 2026-09-10，`git ls-remote --heads origin` 中**不存在**：

- 开发一 `codex/dev1-next-module`（CORE-1 `c3b8464` … CORE-3 `82fe73d` 五个哈希在任何远端 ref 都找不到）；
- 任何 `codex/dev3-*`（开发三的 L-1 / defaultExit / PhaseManager 改动与四张前置条件卡）。

PM 侧 APP-1 接线（`InputAccumulator`/`Level01Assembly`/`PathGraphBridge`，含轮初 `resetTo`）**已写完但无法推送**：它引用 CORE-1/ENT-3/CORE-3 的新 API，缺前者必然编译失败。
**因此两人的第一动作都是 `git push` 各自分支**，PM 随后在一个基线上完成 `CORE-3 + APP-1` 与 `ENT-2a + ENT-2b` 两组配对验证。

---

## 八、L-1 确认请求逐条答复（PM，2026-09-10，致开发三）

| # | 问题 | 裁决 |
| --- | --- | --- |
| 1 | 批准 `tryLeave` 语义变更 | **批准**（`R5-开工前裁决.md` 裁决 4 + README §三）。验收：区域内 + 合法出口 → 同刻 `LEFT` + 清占用 + `reentryBlockedAtTick = tick`；`NOT_OCCUPANT` / `INVALID_EXIT_DIRECTION` / `UNKNOWN_DOCK` 语义不变 |
| 2 | `NOT_OUTSIDE_REGION` 处置 | **保留枚举 + `@Deprecated`**，javadoc 写明“不再由 `tryLeave` 产生”。理由（`git grep NOT_OUTSIDE_REGION` 实测）：命中 `entity/C3DockControllerTest:252`（开发一路径）与 `mechanism/autodock/AutoDockServiceTest:123`（你的）；**`app/**` 里没有任何引用**，所以“删了会破坏 app 测试”不成立。删除会强迫跨模块同刻改测试，得不偿失 |
| 3 | L-1 与 ENT-2b 的合并顺序 | **成对合并**（见 §七）。集成分支 `codex/ent2-paired` = `develop` + ENT-2a + ENT-2b + app 轮转/接线；**不要单独把 L-1 合进 develop**。你的实测“264→262→260 后冻结”与 PM 推演一致，正是“离开后被立刻重新驻留” |
| 4 | `app/Level01AssemblyMovementTest` 的断言 | **由 PM 负责，你不要动 `app/**`**。配对状态下该断言推演仍成立（离开刻起持续按 UP：262→260→…→238 出界，`isOccupied == false`）；若配对后实测失败，PM 会改成“占用已释放”这一更强的断言 |
| 5 | 开 GitHub Issue 并给编号 | **PM 开不了**：本机 `gh NOT found`，也没有可用的 GitHub API 凭证（访问受限）。改用卡号追踪：PR 描述写 `X-MOVE-COLLAPSE-01 / ENT-2a`；如需 `Closes #N`，请用 GitHub UI 建 Issue（正文见 §8.1），把编号回给 PM，PM 补进 PR 描述 |
| 6 | 残影回放 actor 归属是否单独发卡 | **单独发卡**：`自由移动收口-任务卡-残影actor归属.md`（ID `X-MOVE-COLLAPSE-01-ECHO-ACTOR`）。它不是文书问题而是**真 bug**：`DockingPlate.java:95-99` 只在占用者等于 `"echo_" + sourceRound` 时于 `ECHO_DISAPPEARED` 释放，而回放现在传录制时的 `"player"` → 残影消失后驻留板**永久占用**；`AutoDockService.requireActor:336-357` 也已强制 `player` 或 `echo_<round>` 约定 |

### 8.1 Issue 正文模板（复制到 GitHub 新建 Issue）

标题：`X-MOVE-COLLAPSE-01 / ENT-2a+2b：autoDock 离开刻释放与二次进入防护（成对合并）`

```markdown
## 背景
README §三 要求“离开驻留型机关时角色立即按所选方向出发，对应占用同时释放”，
R5 裁决 4 判定 tryLeave 采用 README 优先：占用者按下合法出口即同刻释放，位置不参与判定。

## 当前问题
1. AutoDockService.tryLeave 仍以 NOT_OUTSIDE_REGION 拒绝区域内离开；
2. C3DockController 在离开边沿刻只设 departureDirection，下一 tick 才 tryLeave → DOCK_LEFT 记在出界刻；
3. 任一半单独合入都会把玩家钉在驻留板上：
   - 只合 L-1：旧控制器下一 tick 释放后，再下一 tick 在区域内重新 tryEnter → 需要“新的方向边沿”才能再离开 → 永久驻留；
   - 只合 ENT-2b：tryLeave 非 LEFT 就 FREEZE，旧语义下永远走不出去。

## 交付（成对）
- ENT-2a（开发三，mechanism/**）：tryLeave 放宽为合法出口同刻释放（区域内亦可），保留 reentryBlockedAtTick；AutoDockServiceTest 补用例；
- ENT-2b（开发一，entity/**）：outside→inside 边沿检测（previousInsideMechanismIds，仅在 reset 时清空）+ 边沿刻同 tick tryLeave + DOCK_LEFT 记在离开刻；C3DockControllerTest 补 5 条用例。

## 验收
- 全量 .\mvnw.cmd -o test 退出码 0，任何单测 ≤ 1s；
- 区域内按合法出口 → 同刻 DOCK_LEFT + 占用释放；随后松开按键仍留在区域内也不得重新 DOCK_ENTERED；
- 出界后再次进入 → 恰好一次新的 DOCK_ENTERED；
- app 侧集成：第一关双板链路（残影占左板 + 玩家占右板 → 门解锁）仍绿。
```


> 你们侦察里列的“12 项中 10 项待做”，按本卡口径就是：CORE-1/2/3 + ENT-1 共 4 项可立即开工，
> ENT-2b 等 2a，其余为 PM 的 APP-1、开发三的 LEVEL-0/LEVEL-1。

---

## 九、ENT-2 集成与其余项粒度裁决（PM，2026-09-10，致开发三）

开发三已推送 `origin/feature/content-x-move-collapse-01-dev3`（`86553c9` ENT-2a + `0a3321b` 归档）。

### 9.1 集成分支与 PR 目标（已建，`cd776b9`）

- **已建并推送**：`origin/codex/ent2-paired`，tip = **`cd776b9`** = `origin/develop`（`202e7ad`）
  + 开发三 **ENT-2a `86553c9`（cherry-pick，作者保留）**。
- 开发三分支 tip `7bed288` 同时带着 `2d6ade0`（L-4a）与两份文档提交，因此**只取 ENT-2a 那一个提交**进入配对分支；
  L-4a / 文档按 §9.3 走各自的分支与 PR。
- **PR 机制**：开发三**不要**从自己的分支对 `codex/ent2-paired` 开 PR——配对 PR 由 PM 在 ENT-2b 落地后开
  `codex/ent2-paired → develop`（内容 = 2a + 2b + 必要 app 接线）。L-4a 与 L-2 各自开 PR 指向 `develop`。
- PM 独立复跑该分支：**286 / 1**，唯一失败 `Level01AssemblyMovementTest.dockingSnapsToMechanismCenterAndLeavingReleasesOccupancy:179`
  （`expected true but was false`，实测 `y=260`）——与开发三报告一致；该断言是“ENT-2b 尚未进入”的**刻意标记**，由 PM 负责。
- `codex/ent2-paired` 只承载**成对行为**（ENT-2a + ENT-2b + 必要 app 接线），**不掺其他改动**，便于整体回退。

### 9.2 ENT-2b 的共同基线（答复开发一/开发三）

**基线 = `codex/ent2-paired` 的 tip**（= develop + ENT-2a），**不是** `develop @ 202e7ad`。理由：

1. ENT-2b 的行为只有在 L-1（放宽后的 `tryLeave`）在场时才有意义；基于 develop 时真实 `AutoDockService` 仍是旧语义，
   开发一即使写对，也会在 app 层看到“离开后被冻结/重新驻留”，无法自证；
2. 基于集成分支 tip，开发一可以本地跑出**含 app 层释放断言**的完整绿（`Level01AssemblyMovementTest` 等）；
3. 要求：**直接在该 tip 上增量提交，不要 rebase / 不要重写历史**，提交只碰 `entity/C3DockController.java` 与
   `entity/C3DockControllerTest.java`。

### 9.3 其余项的粒度（答复开发三第 3 问）

| 项 | 归属 | 裁决 |
| --- | --- | --- |
| `L-4a`（你本地 `2be263b`，level 侧清理） | 独立分支 | **独立分支 + 独立 PR**（建议 `feature/content-x-move-collapse-01-dev3-l4a`），**不并入 `codex/ent2-paired`** |
| `L-2`（第一关两轮 headless 模拟，纯 `level/**` 测试） | 独立分支 | **独立分支 + 独立 PR**；它验证关卡数据与到达刻，与 ENT-2 的行为配对无因果关系 |
| `L-1`(ENT-2a) + `ENT-2b` | `codex/ent2-paired` | 唯一的“成对”内容 |

**理由**：配对 PR 的验收就是那一对行为；掺入关卡数据清理会让 bisect/回退变脏，也可能掩盖配对证据。
`L-4a` 与 `L-2` 都**不阻塞** `codex/ent2-paired`，可并行推进。

### 9.4 `L-4b`（删 `defaultExit` 字段/接口）——**必须排在 `CORE-3 + APP-1` 之后**

顺序硬约束：`CORE-3`（core 删字段，开发一）→ `APP-1`（`app/PathGraphBridge` 去转换，PM）→ `L-4b`
（`level.model.PathNode` 与 `LevelGeometry.getDefaultExit` 清理，开发三）。
若 `L-4b` 先落 develop，`PathGraphBridge` 仍在调用 `getDefaultExit()` → 直接编译失败。
PM 侧 `APP-1` 已写好（等开发一推送 CORE 分支后合并验证），届时会主动通知开发三开始 `L-4b`。

### 9.5 `L-3` 出口交互半径——**批准方案 A，半径 72（1.5 格）**

- 签名：`mechanism/ExitTerminal` 新增 `boolean isInInteractRange(Vector2D worldPosition)`（纯函数，不引入计时器，与残影无关）。
- 半径 = **1.5 × tileSize = 72**；硬下限是 1 格（48）——必须覆盖「右驻留板 (8,5) → 出口终端 (9,5)」这 1 格距离，
  否则玩家必须离开右板才能按 E，而 `Door` 不闩锁、离开即重新锁门 → 第一关无解。
- 补测：从右驻留板中心与出口终端中心判定为 `true`；从分叉 (5,3) 判定为 `false`。
- app 侧调用点（PM）：`Level01Assembly.interactIfRequested` 增加 `exit.isInInteractRange(playerPos)` 判定，记为 **APP-2**，
  在 `L-3` 落地后由 PM 接线。

### 9.6 PM 侧当前状态（同一批）

- `codex/app-move-round-loop` = `7c3de3e`（轮末回 PLAYING + `recordEvent` + 轮末清 C3 状态），**已在 origin**，286/0/0；
- `codex/app-move-app1-wiring` = 见 §十（CORE-3 + APP-1 配对，已验证）；
- 新卡 `自由移动收口-任务卡-残影actor归属.md`（`X-MOVE-COLLAPSE-01-ECHO-ACTOR`）由 PM 主责，不阻塞 ENT-2。

---

## 十、PM 集成验收记录（2026-09-10，两组配对各自独立验证）

### 10.1 ENT-2 成对集成（`codex/ent2-paired`，已推送）

| 项 | 值 |
| --- | --- |
| ENT-2a（开发三） | `86553c9`（cherry-pick → `cd776b9`，作者保留） |
| ENT-2b（开发一） | `d440354`（cherry-pick → `c0a0c3c`，作者保留） |
| app 注释同步（PM） | `9b2aee4`（同刻释放语义；**未改任何行为**） |
| 分支 tip | **`9b2aee4`** |
| 全量 | **Tests run 289, Failures 0, Errors 0, BUILD SUCCESS**；墙钟 6.3s；最慢单测 **0.031s** |

**场景证据**

| 场景 | 证据（均在配对状态下通过） |
| --- | --- |
| A 同 tick 离开 | `C3DockControllerTest.legalLeaveEdgeReleasesOccupancyInTheSameTick`、`dockLeftBelongsToTheLegalExitEdgeTick`；机制侧 `AutoDockServiceTest`（区域内 + 合法出口 → `LEFT`） |
| B 区域内不重入 | `C3DockControllerTest.remainingInsideAfterLeavingDoesNotProduceSecondDockEntered`、`leavingThenReleasingTheKeyInsideRegionDoesNotReenter`（PM 要求的第 5 条） |
| C 出界再进入 | `C3DockControllerTest.leavingThenCrossingOutsideAndEnteringAgainProducesNewEdge` |
| D 第一关 app 链路 | `Level01AssemblyMovementTest.dockingSnapsToMechanismCenterAndLeavingReleasesOccupancy` **未改一行即转绿** |

**中间态记录（刻意保留的证据）**：只含 ENT-2a 的 `cd776b9` 上全量为 **286/1**，唯一失败就是场景 D 那条
（`expected true but was false`，实测 `y=260`）——证实「两半单独合入都会锁死」的推演，也正是本对必须一起合的理由。

### 10.2 CORE-3 + APP-1 配对集成（`codex/app-move-app1-wiring`）

| 项 | 值 |
| --- | --- |
| 开发一输入 | `origin/codex/dev1-next-module`（`c3b8464` CORE-1、`f92cec9` CORE-2、`ac9637f` ENT-1、`b171b0c` ENT-3、`82fe73d` CORE-3、`582594f` 文档） |
| PM 输入 | `7c3de3e`（轮末回 PLAYING + `recordEvent`）、`9e0afbb`（APP-1 接线）、`ed5bcac`（配对验证修正） |
| 分支 tip | `ed5bcac` |
| 全量 | **Tests run 288, Failures 0, Errors 0, BUILD SUCCESS**；最慢单测 **0.059s** |

**验收 grep（全部通过）**

- `case DIR_UP` 仅剩 `core/input/LogicalKey.java:31`（映射唯一化）；
- `core|entity|app` 内 `defaultExit` 仅剩 `PathGraphBridge` 的一条说明注释，无代码引用；
- `turnLockDistance`、`pendingTurn` 归零；
- `MovementState.SLOWED` 由 `PatrolController:236` 按速度乘子产出。

**`resetTo` 调用点**：`Level01Assembly.start()`（READY 之后、PLAYING 之前）与 `onRoundEnd()`（接回 PLAYING 之后），
各一次，封装在 `resetPlayerForNewRound()`；并在复位后清 `lastFrame`，避免轮初渲染出上一轮落点。

### 10.3 给开发一的 6 项回报

1. 子项哈希：ENT-2a `86553c9`、ENT-2b `d440354`；集成提交 `cd776b9`、`c0a0c3c`、`9b2aee4`（tip）；
   CORE 配对侧集成提交 `d9bdf84`（merge）、`ed5bcac`（tip）。
2. 全量数字：ENT-2 配对 289/0/0（墙钟 6.3s）；CORE-3+APP-1 配对 288/0/0。
3. 无单测 > 1s：**确认**（最慢 0.031s / 0.059s）。
4. 场景 A–D 证据：见 §10.1 表。
5. `app/**` 需要同步的断言/注释：只有两处**注释**（`Level01Assembly` 的“出界刻释放”措辞）已同步为“离开边沿刻释放”；
   断言**无需修改**；app 仍不自行释放占用，释放一律走 C3 → `AutoDockOccupancyPort`。
6. **允许开发一进入最终交付审计** —— 前提与顺序见 §10.4。

### 10.4 合入顺序（两个配对必须各自成 PR）

1. `codex/app-move-app1-wiring` → `develop`（**CORE-3 + APP-1 必须同一 PR**，否则 develop 编译断）；
2. 重新把 develop 并入 `codex/ent2-paired` 后 → `develop`（ENT-2a + ENT-2b 同一 PR）；
3. `X-MOVE-COLLAPSE-01-ECHO-ACTOR`（残影 actor 归属，PM 主责）单独一个 PR；
4. 三者中任何一步都不得只合一半。

### 10.5 PR 与已签发卡（2026-09-10）

| 项 | 链接 / 文件 | 状态 |
| --- | --- | --- |
| PR #54 CORE-3 + APP-1 | https://github.com/time-trace-lab-team/time-trace-lab/pull/54 | open，`mergeable=true/clean`，10 commits / 22 files / +733-340 |
| PR #55 ENT-2a + ENT-2b | https://github.com/time-trace-lab-team/time-trace-lab/pull/55 | open，`mergeable=true/clean`，3 commits / 9 files / +164-56 |
| **APP-2** 出口交互半径 | `自由移动收口-任务卡-APP-2-出口交互半径.md` | **已发卡**（PM）；上游开发三 L-3 `cf4714f`（方案 A，1.5 格）已推送 |
| **ECHO-ACTOR** 残影 actor 归属 | `自由移动收口-任务卡-残影actor归属.md` | **已发卡**（PM 主责 + 开发三/二配合），可立即开工 |
| **FINAL-ACCEPT** 最终验收 | `自由移动收口-最终验收卡-测试.md` | **已发卡**（测试/QA）；前置 = PR #54/#55 + ECHO-ACTOR + APP-2 |
| 开发一 / 二 / 三 卡（v1） | `自由移动收口-任务卡-开发{1,2,3}.md` | 已被 v2 取代 |
| 开发一 / 二 / 三 卡（v2） | `自由移动收口-任务卡-开发{1,2,3}-v2.md` | 已被 v3 取代（v2 各子项均已完成或转入 v3） |
| **开发一 / 二 / 三 卡（v3，当前有效）** | `自由移动收口-任务卡-开发{1,2,3}-v3.md` | **已发卡**：开发一=推 v2 分支+配合接线+画面取证；开发二=开 PR+E-4 淘汰列表；开发三=C/B/D |
| PR #57 APP-2 + ECHO-ACTOR E-1 | https://github.com/time-trace-lab-team/time-trace-lab/pull/57 | open，clean，1 commit / 2 files，316/0/0 |
| 开发三 L-2 / L-3 / L-4a | `origin/feature/content-x-move-collapse-01-dev3-{l2,l3,l4a}` | ✅ 已随 #51/#52/#53 合入 develop |
| 测试口径更新 | `测试口径更新-C-PLAYER-MOVE-系列.md` | 已发出，作为 FINAL-ACCEPT §B 的验收清单 |

---

## 十一、v2-C（R-2 节点转向提示）接口裁决（PM，2026-09-10）

开发一请求「扩展 `RenderViews` + 由 PM 在 app 接线」。**PM 部分否决、给出更小的契约**：

### 11.1 不批准扩展 `RenderViews.Frame`

节点几何是**本关静态常量**，逐帧塞进 `Frame` 等于让每帧视图承担静态数据；而 `Frame` 是共享契约，
扩展会牵动所有构造点（当前唯一生产点 `Level01Assembly:226` 与 `Frame.empty()`）与全部消费方，收益为零。
**因此 `RenderViews.Frame` 保持不变，不需要 R5 的公共签名三方审批。**

### 11.2 批准的契约（全部落在 `render/**`，即开发一自己的路径）

```java
// render/RenderViews.java —— 只读、不可变、纯静态几何
public record PathNodeMarker(String id, double x, double y) { /* 校验非空 id、有限坐标 */ }

// render/PathNodeHintLayer.java —— 新图层
public final class PathNodeHintLayer implements RenderLayer {
    public PathNodeHintLayer(Supplier<RenderViews.Frame> frameSource,
                             List<RenderViews.PathNodeMarker> nodes,
                             double tileSize,
                             WorldTransform transform);
    public enum HintLevel { HIDDEN, DIM, BRIGHT }
    public static HintLevel levelFor(double distanceWorld, double tileSize);   // 纯函数
}
```

- **档位阈值（采纳开发一建议）**：`d ≤ 1.0 × tileSize → BRIGHT`；`d ≤ 2.0 × tileSize → DIM`；其余 `HIDDEN`。
  距离 = 玩家世界坐标与节点中心的世界距离（中心距，非轴向）。
- 绘制：节点中心 **6–10 px 菱形或短刻痕**（经 `WorldTransform` 投影后的 canvas 像素），亮度用 alpha 分档，
  形状本身不得因档位变化到不可辨识；**绝不绘制全屏网格**。
- 数据来源只能是关卡路径节点几何（`levelData.getPathNodes()` 或已构建的路径图）；
- **禁止**：从地格或玩家状态反推节点；在提示中编码门/开关/占用等玩法状态；写回任何玩法状态。

### 11.3 PM 接线（`app/**`）

1. `Level01Assembly` 在构造时用 `levelData.getPathNodes()` 投影出 `List<RenderViews.PathNodeMarker>`
   （id + `getWorldPos()`），并暴露只读 `pathNodeMarkers()`；
2. `TimeTraceLabApplication` 在现有 `addLayer` 三行之后注册
   `new PathNodeHintLayer(frames, assembly.pathNodeMarkers(), 48.0, transform)`；
3. 接线前需要开发一先把 `PathNodeMarker` 与 `PathNodeHintLayer` 推到远端，否则 app 无法编译。

### 11.4 交付与推分支要求（阻塞项）

- 开发一 v2-A/v2-B 的提交 `6ab4465`、`efba32d`、`1105b76` **目前不在任何远端 ref**（自称“仅本地”），
  PM 无法复核其 **297/0/0** 与 9 条新测试，也无法把它们带进 PR #54；
- 请推送**新分支 `codex/dev1-move-v2`（base = `ed5bcac`）**，**不要**改已推送的 `codex/dev1-next-module`
  （PR #54 的历史引用它）；
- 交叉核对：PM 在 `ed5bcac` 上实测 288/0/0，288 + 9 = **297**，与开发一报告一致；
- v2-B 只做了投影级断言、未起 JavaFX 窗口 → 请在能起窗口时补一张
  **IDLE / CRUISING / SLOWED 三态截图**，并登记进 `FINAL-ACCEPT` 的可读性门禁（§十六 第 4 条 5 人试玩）。

---

## 十二、develop 合并台账与「合并后必验」门禁（PM，2026-09-11）

### 12.1 已合并（develop head = `6dc5cad`）

| PR | 内容 | 备注 |
| --- | --- | --- |
| #51 | 开发三 L-2 第一关两轮模拟 | 其测试仍按**四参**构造 core `PathNode` |
| #52 | 开发三 L-3 出口宽容半径 | **APP-2 尚未接线**（PM 待办） |
| #53 | 开发三 L-4a 第一关不再声明默认出口 | |
| #54 | CORE-3 + APP-1 成对 | PM 在自己分支上验证 288/0/0 |
| #55 | ENT-2a + ENT-2b 成对 | PM 验证 289/0/0 |
| #56 | 开发三 CORE-3 后的 `PathNode` 构造修复（`1c60c4c`） | **急救**：`#54` 与 `#51` 合并后 develop 编译失败 |

### 12.2 事故复盘（固化为门禁）

`#51`（L-2 测试，四参构造）与 `#54`（CORE-3 删字段）**各自验证都通过，合并到一起才断**。
根因不是谁的代码，而是**合并进 develop 之后没有人再跑一次全量**。新增两条门禁：

1. **每次合并后，PM 立刻在 `develop` 上跑一次全量并把数字回填本表**；
2. 各开发请求合并前，先把最新 `develop` 同步进自己的分支并跑全量（PR 描述附基线哈希与数字）。

**PM 于 2026-09-11 在 `develop @ 6dc5cad` 实测：Tests run 313, Failures 0, Errors 0, BUILD SUCCESS，墙钟 7.8s。**

### 12.3 当前仍未合并 / 未开工

| 项 | 归属 | 状态 |
| --- | --- | --- |
| `codex/dev2-x-move-collapse`（R1–R4 + `fe1f0d0` E-3 契约注记） | 开发二 | 已推送，**待开 PR** |
| `X-MOVE-COLLAPSE-01-APP-2`（出口宽容半径 app 接线） | PM | ✅ 已完成并开 **PR #57**（`codex/app-move-app2-echo-actor` @ `788820c`） |
| `X-MOVE-COLLAPSE-01-ECHO-ACTOR` E-1（回放 actor 改写） | PM | ✅ 同上（dev2 E-3 契约注记已推 `fe1f0d0`，dev3 E-2 开工中） |
| **新发现**：`GameEvent.echoDisappeared` 无派发点 | 开发二/PM | `src/main` 内零调用 → `DockingPlate` 的释放分支是死代码；第一关 `L=1` + 轮末 `plate.reset()` 掩盖了它，**L≥2 关卡前必须补**（`replay/**` 暴露淘汰列表或 `app/**` 轮末 diff） |
| 开发一 v2-A/B（`6ab4465`/`efba32d`/`1105b76`）与 v2-C | 开发一 | 未推送；v2-C 契约见 §十一 |
| 开发三 C（删 `PhaseManager`）、B（E-2）、D（L-4b） | 开发三 | 卡在 v2 §二 已发卡；**L-4b 开工已批准**（见 §12.4） |

### 12.4 L-4b 开工批准（2026-09-11）

前置已满足（CORE-3 + APP-1 已随 #54 进 develop）。允许清理 `level.model.PathNode.defaultExit` 与
`LevelGeometry.getDefaultExit`（含 `LevelGeometryImpl` 的映射与校验）；`Level01Footsteps` 已在 L-4a 去掉声明。
**不要**改 `app/**`（`PathGraphBridge` 已不再使用它）；完成后全量必须绿并把数字回填交付文档。

---

## 十三、v2（开发一）交付核对与 PM 接线待办（2026-09-11）

### 13.1 阻塞：`codex/dev1-move-v2` 未推送（第二次）

开发一报告「分支 `codex/dev1-move-v2`、基线 `ed5bcac`、v2-A/B/C 已完成、全量 303/0/0」，
但 `git ls-remote --heads origin` 里**没有该分支**（`codex/dev1-*` 只有 `dev1-c3`、`dev1-next-module`）。
→ PM 无法复核 303/0/0、无法执行 §11.3 的 app 接线（缺 `RenderViews.PathNodeMarker` 与 `PathNodeHintLayer` 类型，app 编译不过）。

**要求**：
1. `git push -u origin codex/dev1-move-v2`；
2. 推送前先把 `origin/develop`（现 `6dc5cad`，含 #55/#56/#51–#53）并入你的分支 —— 你的基线 `ed5bcac` 落后 develop；
3. push 后 PM 复核 + 接线 + 全量，并在本表回填数字。

### 13.2 数字待核

开发一报新用例 5（v2-A）+ 4（v2-B）+ 3（v2-C）= **12** 条，而基线 `ed5bcac` 实测 **288/0/0** →
288 + 12 = **300**，与其报告的全量 **303** 差 **3** 条。push 后以实际 `Tests run` 为准并说明差额来源。

### 13.3 PM 接线（两处，等 push 后执行）

```java
// 1) Level01Assembly：构造期投影静态几何 + 只读访问器
private final List<RenderViews.PathNodeMarker> pathNodeMarkers;   // = levelData.getPathNodes()
                                                                  //   → new PathNodeMarker(id, worldPos.x, worldPos.y)
public List<RenderViews.PathNodeMarker> pathNodeMarkers() { return pathNodeMarkers; }

// 2) TimeTraceLabApplication（现有 addLayer 之后）
canvasAdapter.addLayer(new PathNodeHintLayer(frames, assembly.pathNodeMarkers(), 48.0, transform));
```

### 13.4 人工画面验收（PM 或开发一任一方执行）

窗口跑起来后截 **IDLE / CRUISING / SLOWED** 三态 + 「远→近节点菱形由隐藏变亮」各一张，
登记进 `FINAL-ACCEPT` §A 第 4 条（5 人 3 分钟内理解「按住走、松开停」与路口提示）的取证材料。







