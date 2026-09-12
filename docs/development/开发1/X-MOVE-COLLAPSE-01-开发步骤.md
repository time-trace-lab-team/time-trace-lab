# 《时痕实验室：昨日的我》自由移动收口开发步骤

> 任务 ID：`X-MOVE-COLLAPSE-01-DEV1`  
> 父卡：`X-MOVE-COLLAPSE-01`  
> 所属板块：开发一  
> 当前目标：优先完成自由移动收口，完成并确认后再领取下一块功能。

## 一、文档来源与执行边界

| 内容 | 处理方式 |
| --- | --- |
| 用户当前请求 | 阅读两份 PM 附件，将自由移动工作拆成完整任务链、步骤、前置条件和验收效果，并整理为 Markdown。 |
| `自由移动收口-任务卡-开发1-已答复.md` | 作为最新的 PM 授权、接口规格、任务顺序和验收依据。 |
| `自由移动收口-任务卡-开发1.md` | 作为原始任务卡；与最新 PM 回复冲突时，以最新回复为准。 |
| 本文档 | 只做计划和任务拆解，不代表已经编码、测试、提交或完成视觉验收。 |

两份附件的基线数字存在差异：最新回复写的是 `202e7ad`、286 条测试，原始任务卡写的是 `8140bdb`、283 条测试。正式开工前必须重新检查现场和实际测试结果，不能把附件中的数字直接当成当前证据。

## 二、总体任务链

```text
现场锁定
  -> CORE-1
  -> CORE-2
  -> ENT-1
  -> ENT-3
  -> CORE-3 + PM APP-1 配对接线
  -> ENT-2a + ENT-2b 成对实现与 PM 集成
  -> v2-A CORE-2 速度补测
  -> v2-B R-1 玩家状态可见
  -> v2-C R-2 节点转向提示（开发一 render provider）
  -> PM 静态节点数据投影与图层注册
  -> JavaFX 可读性截图与最终验收
```

开发二当前没有本任务链必须等待的接口或阻塞项。开发三实现 `ENT-2a`，开发一实现 `ENT-2b`，两者不得以任一半单独进入 `develop` 的方式验证或发布；PM 在集成分支 `codex/ent2-paired` 进行成对验证。

### PM 契约卡新增裁决（2026-09-10）

跨模块契约卡 §七为 `ENT-2` 增加以下高优先级规则，覆盖本文件原先“等待 `ENT-2a` 已进入 `develop`”的表述：

1. `ENT-2a` 与 `ENT-2b` **成对合并**。单独合入 `ENT-2a` 会在下一 tick 重入驻留；单独合入 `ENT-2b` 会因旧 `tryLeave` 拒绝区域内离开而永久冻结。
2. 开发一从与开发三相同的 `origin/develop` 基线独立实现 `ENT-2b`，仅使用假端口自证；不得依赖开发三未推送的工作树或代替其修改 `mechanism/**`。
3. `C3DockController` 必须以 `previousInsideMechanismIds`（按 stable mechanism ID）表达 `outside -> inside` 边沿。每个 `step()` 结束更新；只在 `reset(...)` 清空，**不得**在 `clearDockState()` 清空。
4. 合法出口边沿刻立即调用现有 `tryLeave(...)`。返回 `LEFT` 时同刻发 `DOCK_LEFT`、清本地 dock 状态并返回 `CRUISE`；非 `LEFT` 时保持 `FREEZE` 并保留 dock 状态，且不再保留跨 tick 的 `departureDirection`。
5. 开发一补五条实体层测试：同 tick 释放、区域内不二次进入、出界后再进入、`DOCK_LEFT.tick ==` 按键刻、离开后松键但仍在区域内不重入。

### PM v2-C 裁决与执行状态（2026-09-12）

PM 已确认 v2-A、v2-B 要以 `ed5bcac`（`CORE-3 + APP-1` 配对头）为基线重新交付，并为原先阻塞的 R-2 批准更小的渲染契约：

| 子项 | 状态 | 开发一实现 / 验收 |
| --- | --- | --- |
| v2-A `CORE-2` 速度补测 | ✅ 完成 | `PatrolControllerSpeedModifierTest` 覆盖 0.5 倍速、恢复 1.0、节点转向、`resetTo` 后倍率保持、无输入 `IDLE`。 |
| v2-B `R-1` 玩家状态可见 | ✅ 完成 | `PlayerVisualProjection` 把权威 `movementState` 映射成形状与反馈；`PlayerLayer` 只读绘制。 |
| v2-C `R-2` 节点转向提示 | ✅ 开发一 provider 完成 | `RenderViews.PathNodeMarker` 只承载静态节点几何；`PathNodeHintLayer` 按中心距离显示 8 px 菱形：≤1 格亮、≤2 格暗、其余隐藏。 |
| PM 接线与截图 | ⏳ PM / 最终验收 | PM 在 `app/**` 投影 `levelData.getPathNodes()` 并注册图层；完成后补 `IDLE` / `CRUISING` / `SLOWED` 三态 JavaFX 截图。 |

R-2 不扩展 `RenderViews.Frame`。节点不是玩法状态：不能从地格或玩家状态反推，不能编码门、开关或占用状态，也不得写回任何玩法数据。开发一完成渲染 provider 后，PM 负责在 `Level01Assembly` 暴露不可变节点列表并在 `TimeTraceLabApplication` 注册图层。

### PM v3 收口状态（2026-09-12）

| 任务链位置 | 状态 | 结论 / 后续责任 |
| --- | --- | --- |
| v2-A / v2-B / v2-C 开发一实现 | ✅ | 以原配对头 `ed5bcac` 为起点的实现已完成，随后已合并最新 `origin/develop @ b76c14c`。 |
| v3 §二 测试数自证 | ✅ | `ed5bcac clean test = 288`；历史 v2 提交 `1675c87 clean test = 300`，即 288 + 速度 5 + 视觉投影 4 + 节点提示 3。先前 303 是未执行 `clean` 时旧编译测试产物多计 3 条。 |
| v3 §四 状态 × 相位矩阵 | ✅ | `PlayerVisualProjectionTest` 新增一条 4×2 矩阵用例：4 个状态、每个状态各验证 phased true / false，确认全枚举分支都有确定投影。 |
| v3 §一 推送 | ✅ | `origin/codex/dev1-move-v2` 已推送；开发一代码交付提交为 `c9b11f4`，PM 现在可进行 `app/**` R-2 接线。 |
| v3 §五 画面取证 | ⏳ | PM 接线后在目标机以 `./mvnw.cmd -o javafx:run` 启动；记录三态与节点远/中/近的四张图。 |

当前干净基线的 `clean test` 为 331/0/0：最新 `origin/develop` 的 318 条，加开发一 13 条（速度 5、视觉投影 4 + 矩阵 1、节点提示 3）。

## 三、分步骤任务表

| 顺序 | 任务 ID / 责任范围 | 开始前置条件 | 本步主要工作 | 任务完成需要实现的效果 | 停止条件与交接 |
| --- | --- | --- | --- | --- | --- |
| 0 | 开工前现场锁定（计划准备，不改代码） | PM 已批准最新任务卡；开发二、开发三无前置 | 复核分支、`HEAD` 与 `origin/develop` 的关系、工作树、允许修改路径和实际 Maven 全量测试。 | 确认工作基线正确，避免把旧数字、旧接口或无关改动带入。 | 分支基线过旧、存在无关改动或测试失败时停止，交 PM 决定分支和处理方式。 |
| 1 | `CORE-1` 四方向输入契约 | PM 已批准精确签名；开发二、开发三无前置。 | 只修改 `core/input/LogicalKey.java`、`InputIntent.java` 及对应测试；新增 `Optional<Direction> direction()` 和 `Set<Direction> heldDirections()`；不改调用方。 | 四个方向键统一映射到 `Direction`；`INTERACT`、`PHASE` 返回 `Optional.empty()`；按住方向投影为不可变集合；不改变 record 构造器和既有边沿接口。 | 定向测试和 `.mvnw.cmd -o test` 通过后提交。`app/**` 中旧映射由 PM 后续配对改造，不能由开发一顺手修改。 |
| 2 | `CORE-2` 速度参数冻结与减速端口 | `CORE-1` 完成且全量通过；PM 已批准速度契约；射线写入端不属于本步。 | 删除 `entity/PatrolConfig.turnLockDistance`；冻结 `tileSize=48`、`baseSpeed=2`、`epsilon=4.8e-5`；新增 `entity/SpeedModifierPort`；让 `PatrolController` 消费本刻速度乘子。 | 速度来源统一；乘子 `0.5` 时每刻位移为 `1.0` 并输出 `SLOWED`；恢复 `1.0` 后输出 `CRUISING`。真实射线写入仍由其他模块负责。 | 假端口测试、速度参数检查和全量测试通过后提交；不得修改 `mechanism/**`。 |
| 3 | `ENT-1` 单槽方向意图归位 | `CORE-2` 完成且全量通过；PM 已批准三条作废规则；开发二、开发三无前置。 | 将方向意图从 app 语义收进 `PatrolController`；`advance` 签名保持不变；新按下的 90° 方向在按住期间保留，到节点中心提交。 | 玩家提前按住侧向键时，角色到达节点中心仍能转向；松手后意图作废；提交成功后意图作废；当前方向或反方向不参与提交。 | 三条行为测试通过后停止并通知 PM 删除 `Level01Assembly.pendingTurn`；开发一不得修改 `app/**`。 |
| 4 | `ENT-3` 轮初复位 API | `ENT-1` 完成且全量通过；PM 需要在轮初调用该 API；开发二、开发三无前置。 | 按已批准契约增加 `resetTo(String startNodeId, Direction initialDirection)` 或经 PM 确认的等价复位 API；清空内部 segment 和未提交方向意图。 | 新轮开始时位置回到起点节点中心、方向恢复；复位不写帧、不发事件、不清空残影；复位后可立即继续移动。 | 复位位置、方向、继续推进和无副作用测试通过后交 PM；同步说明异常条件及 app 调用点。 |
| 5 | `CORE-3` 自动选路清零 | `ENT-3` 完成；PM 必须准备配对完成 `APP-1`；开发二、开发三无前置。 | 删除 `PathExitDecision`、`PathExitSelector.select(...)`、`PathNode.defaultExit` 及相关构造校验；保留 `isPassable(...)`。 | 路口不再自动选路，只有玩家按住对应方向才转向；真死路判断仍可使用。 | `app/PathGraphBridge` 暂时编译失败是预期结果；立即停止并交 PM，禁止修改 `app/**` 来凑全绿。 |
| 6 | PM 配对接线 `APP-1`（不属于开发一编码） | 分别等待 `CORE-1`、`ENT-1`、`ENT-3`、`CORE-3` 交付。 | PM 将 `InputAccumulator` 和 `Level01Assembly` 改为调用 core 输入接口；删除 `pendingTurn`；轮初调用 `resetTo`；将 `PathGraphBridge` 改为纯几何换算。 | app 不再维护重复移动语义；`CORE-3` 后恢复完整编译；自由移动真正接入装配链。 | 由 PM 完成并回报全量测试。开发一不代改 `app/**`。 |
| 7 | 开发三 `ENT-2a`（配对前置） | PM 已裁决与 `ENT-2b` 成对合并；开发三从共同 `origin/develop` 基线实现。 | 开发三放宽 `AutoDockOccupancyPort.tryLeave`：合法出口按下刻即返回 `LEFT` 并释放，不再要求位置出界。 | 同一刻允许释放占用，但该半项不得单独合入或发布。 | 开发一不实现 `mechanism/**`；等待开发三分支提交供 PM 配对集成。 |
| 8 | `ENT-2b` autoDock 离开刻 | PM 已授权成对合并；开发一从共同 `origin/develop` 基线独立实现，使用假端口模拟 `ENT-2a` 的 `LEFT` 行为。 | `C3DockController` 在合法离开边沿同刻调用 `tryLeave`、发 `DOCK_LEFT`；以跨 tick 的区域集合检测进入边沿，防止离开后仍在区域内重入。 | 玩家离开驻留区域时，驻留释放、`DOCK_LEFT`、门状态在同一刻保持一致；下一 tick 仍在区域内或松键均不二次进入；出界再进入才产生新边沿。 | 开发一分支与开发三分支由 PM 合入 `codex/ent2-paired`；全量测试及 app 集成验证通过后才可进入最终交付。 |
| 9 | 开发一最终交付与收口审计 | 开发一子项完成；PM 配对项和开发三前置均已回报。 | 编写交付文档；记录每步提交哈希、测试数量、耗时、公共签名变化和 PM 调用点；审计 Git 状态、禁止路径和暂存区。 | 能独立解释最终代码；自由移动具备统一输入、节点转向、速度变化、轮初复位、无自动寻路和驻留离开闭环。 | 全量测试退出码为 0；单测均不超过 1 秒；未完成和确认前不得领取下一模块。 |

## 四、跨团队前置与配合表

| 参与方 | 必须配合的内容 | 发生时机 | 是否属于开发一代码 |
| --- | --- | --- | --- |
| PM | 确认分支基线和工作树没有无关改动。 | 开工前 | 否 |
| PM | 接入 `LogicalKey.direction()` 与 `InputIntent.heldDirections()`。 | `CORE-1` 后 | 否 |
| PM | 删除 `Level01Assembly.pendingTurn`。 | `ENT-1` 后 | 否 |
| PM | 在轮初调用 `PatrolController.resetTo(...)`。 | `ENT-3` 后 | 否 |
| PM | 将 `PathGraphBridge` 改为纯几何换算并恢复编译。 | `CORE-3` 后 | 否 |
| 开发二 | 当前没有自由移动收口必须等待的接口或阻塞项。 | 全流程 | 否 |
| 开发三 | 完成 `ENT-2a` 的同刻离开接口。 | `ENT-2b` 前 | 否，开发一只消费接口 |
| PM | 确认 `APP-1` 配对完成并恢复全量测试全绿。 | `CORE-3` 后、最终验收前 | 否 |

## 五、最终可观察效果

完成并经 PM 配对接线后，实际游戏中的自由移动应表现为：

1. 按住方向键持续移动，松开后停止。
2. 提前按住侧向键，角色到达节点中心时转向。
3. 速度乘子变化时，角色移动速度和 `MovementState` 同步变化。
4. 每轮开始时角色回到指定起点和初始方向。
5. 路口不再自动选路，移动方向由玩家输入决定。
6. 离开驻留区域时，驻留释放、`DOCK_LEFT`、门状态在同一刻保持一致。

自由移动收口完成不等于第一关全部功能完成；录制、回放、完整通关、JavaFX 页面切换等其他问题仍按各自模块边界处理。

## 六、开发一阶段禁止事项

- 不批量完成 `CORE-1/2/3`、`ENT-1/2`。
- 不修改 `app/**`、`replay/**`、`snapshot/**`、`mechanism/**`、`level/**`、`ui/**`、`pom.xml` 等禁止路径。
- 不代替开发二或开发三实现其模块。
- 不顺手重构无关代码。
- 不使用 `git add .`。
- 遇到公共接口版本不一致、app 编译失败或开发三接口未就绪时，停止编码并提交最小接口交接。
