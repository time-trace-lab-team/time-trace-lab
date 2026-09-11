
# P-MOVE-ALIGN 继续开发交接文档

> 项目：《时痕实验室：昨日的我》  
> 当前板块：开发三（玩法内容与机关）  
> 交接日期：2026-09-11  
> 适用对象：接手本项目继续开发、集成或验收的成员

## 1. 一页结论

开发三在 `level/**`、`mechanism/**` 范围内已经完成本轮自由移动适配所需的机关侧工作，并在当前 HEAD 中提供了 P4-A 共振纯 Java 状态机及模块内快照端口。

当前不能直接开始第四、第五关正式数据或到达刻调参。玩家移动真正属于开发一的 `core/**`、`entity/**` 与项目经理的 `app/**`；在这些公共契约冻结前，接手人不得越权补写移动实现，也不得把文档中的候选接口当成已经接通。

本交接的核心顺序是：

```text
保护并核对当前工作树
  -> 开发一完成移动/输入/减速契约
  -> 开发二确认共享时钟与 R5-B 快照聚合
  -> PM 完成 app 接线并冻结参数、README、schema
  -> QA 做跨模块和人工门禁
  -> 仅在全部门槛满足后开始 P4-B 正式关卡数据
```

## 2. 当前仓库和 Git 状态

### 2.1 基本定位

- Worktree：`C:/Users/dhls/.codex/worktrees/a45a/Java-project`
- 分支：`codex/dev3-1`
- 当前 HEAD：`ab1a766b84de84f83a27b4cd89026194eaf03b1d`
- `origin/codex/dev3-1` 当前与本地 HEAD 一致；本文件提交前的 `develop` 基线为 `c8a513c72435182cf3d2f5cdc69bc380d39279d4`（包含 PR #49 的部分玩家移动 app 适配和开发二 R5-B 交接文档）
- 本文件是文档提交，不代表 `codex/dev3-1` 的代码改动已经合入 `develop`；代码仍需按责任边界和后续集成流程处理
- 当前开发三改动仍未提交、未推送、未创建 PR
- 提交、推送、PR 创建和最终集成由用户/项目经理执行；接手人不要擅自代办
- 不要使用旧的 `codex/content-p3-merge-resolution` worktree

### 2.2 工作树中的既有改动

这些改动属于当前交接内容，接手人先阅读和保留，不要为了清理工作树而回滚或覆盖：

- `docs/development/开发3/开发三-稳定ID与排序-autoDock规格冻结.md`
- `docs/development/开发3/开发三c0-c1交接文档.md`
- `src/main/java/org/example/timeloop/level/Level01Footsteps.java`
- `src/main/java/org/example/timeloop/level/Level03Footsteps.java`
- `src/main/java/org/example/timeloop/level/LevelGeometry.java`
- `src/main/java/org/example/timeloop/level/LevelGeometryImpl.java`
- `src/main/java/org/example/timeloop/level/model/PathNode.java`
- `src/main/java/org/example/timeloop/mechanism/autodock/AutoDockOccupancyPort.java`
- `src/main/java/org/example/timeloop/mechanism/autodock/AutoDockService.java`
- 删除：`src/main/java/org/example/timeloop/mechanism/ray/PhaseManager.java`
- `src/test/java/org/example/timeloop/level/LevelGeometryImplTest.java`
- `src/test/java/org/example/timeloop/mechanism/autodock/AutoDockServiceTest.java`

当前还存在以下未跟踪的前置条件/交接文档；它们不是可以随意删除的临时文件：

- `docs/development/开发3/P-MOVE-ALIGN-前置条件卡-开发一.md`
- `docs/development/开发3/P-MOVE-ALIGN-前置条件卡-开发二.md`
- `docs/development/开发3/P-MOVE-ALIGN-前置条件卡-PM集成.md`
- `docs/development/开发3/P-MOVE-ALIGN-前置条件卡-QA.md`
- `docs/development/开发3/P-MOVE-ALIGN-新对话交接卡.md`
- 本文件：`docs/development/开发3/P-MOVE-ALIGN-继续开发交接文档.md`

接手后的第一步应保存以下只读证据：

```powershell
git status --short --branch
git diff --name-only
git diff --check
```

不要使用 `git reset --hard`、`git checkout --` 或清理命令处理上述工作树。

## 3. 必读资料

按以下顺序阅读：

1. `README.md`：冻结的玩法范围、五关规划、固定步长、移动、autoDock、射线、共振和验收标准。
2. `docs/development/开发3/开发3-玩法内容与界面-技术指南.md`：开发三的职责、允许/禁止路径和 P0–P7 模块边界。
3. `docs/development/开发3/开发三c0-c1交接文档.md`：开发三已有交付和历史依赖。
4. `docs/development/开发3/开发三-稳定ID与排序-autoDock规格冻结.md`：稳定 ID、同刻事件排序、autoDock 几何和快照方向。
5. 四张 `P-MOVE-ALIGN-前置条件卡-*.md`：当前各责任人的阻塞项和回传条件。
6. `docs/decisions/C1-启动与世界坐标约定.md`、`docs/decisions/C1-共享时钟与单刻顺序约定.md`、`docs/decisions/R2-PlayerFrame契约裁决.md`：坐标、共享时钟、PlayerFrame 和轮次语义。

## 4. 责任边界

| 范围 | 负责人 | 接手时的规则 |
| --- | --- | --- |
| `mechanism/**`、`level/**` | 开发三 | 机关状态、关卡数据、几何定义、稳定 ID、纯逻辑测试可在这里继续；仍需遵守本交接的前置门槛 |
| `ui/**`、`persistence/**`、`audio/**` | 开发三 | 只有对应任务卡明确开启后再做；不能用 UI 直接改 tick、残影或机关内部状态 |
| `core/**` | 开发一 | 输入、固定步长、碰撞、路径移动和通用状态；开发三不要代改 |
| `entity/**` | 开发一 | `PatrolController`、`C3DockController`、玩家移动和减速接线；开发三不要代改 |
| `render/**` | 开发一 | Canvas、玩家/残影/机关绘制；状态稿不等于已完成画面 |
| `app/**` | PM / 集成 | 场景骨架、`PathGraphBridge`、跨模块装配和最终接口裁决 |
| `replay/**`、`snapshot/**` | 开发二 | 共享时钟、记录、残影、轮次事务和快照聚合；开发三不要复制第二套类型或计时器 |
| `src/test/**` | 各责任人维护，QA验收 | 测试应跟随所属模块；跨模块结论由 QA 汇总 |

默认禁止开发三任务修改：`pom.xml`、`core/**`、`entity/**`、`app/**`、`render/**`、`replay/**`、`snapshot/**`。任何公共接口变更必须先由提供方、使用方和 PM 确认。

## 5. 已完成内容

### 5.1 开发三侧改动

- `level.model.PathNode.defaultExit` 已标记废弃，不再作为开发三移动决策依据。
- `LevelGeometry` 不再维护默认出口映射；`Level01Footsteps` 和 `Level03Footsteps` 已移除旧默认出口配置。
- `AutoDockOccupancyPort.tryLeave` 已支持合法方向在同一逻辑刻释放，即使角色位置仍在区域内。
- 已删除无引用的 `mechanism/ray/PhaseManager`。
- Level01 的稳定机制 ID、路径节点 ID、门引用和 autoDock 数据已按当前规格调整。
- autoDock 服务已提供只读查询、进入/离开、同 tick 再进入阻止、占用快照和恢复方向。

### 5.2 P4-A 共振接口

当前 HEAD 已提供以下 `mechanism/resonance` 类型：

- `ResonanceStateMachine`
- `ResonanceStateSnapshot`
- `ResonanceSnapshotPort`
- `ResonanceTickResult`
- `ResonanceState`
- `ResonanceResetReason`

主要能力：

- `observe(TickContext, boolean, Collection<EchoState>)`
- `state()`、`isLatched()`、`windowTicks()`
- `reset(ResonanceResetReason)`
- `createSnapshot()`、`restore(ResonanceStateSnapshot)`

当前状态语义为 `DORMANT -> ARMED -> LATCHED`。当前玩家只产生预览，第二轮不能正式锁存；窗口末刻仍有效，超时后一刻先复位再处理新的进入事件。该状态机读取既有 `TickContext` 和 `EchoState`，不得再造时钟、残影来源或 replay 类型。

## 6. 当前仍未完成的事项

### 6.1 开发一：移动和输入前置

当前源码仍能看到以下未完成项：

- `core/input/LogicalKey.java` 尚未提供唯一的 `direction()` 映射。
- `core/input/InputIntent.java` 尚未提供 `heldDirections()`；当前只有 `held` 和 `directionEdges`。
- `entity/PatrolConfig.java` 仍包含 `turnLockDistance`。
- `entity/PatrolController.java` 在最新 `develop` 基线中已经接收 `heldDirections/newestEdge`，但仍依赖 `PathExitSelector` 和旧的 `turnLockDistance`。
- `core/path/PathExitSelector.java`、`core/path/PathExitDecision.java` 和 `core/path/PathNode.defaultExit` 仍存在。
- `app/Level01Assembly.java` 已由 PR #49 部分接入 held direction / newest edge 调用，但 `app/PathGraphBridge.java` 仍读取并传递 `defaultExit`。
- `SpeedModifierPort` 尚未提供，`MovementState.SLOWED` 尚未形成完整生产者—测试链路。
- `C3DockController` 的当前注释仍记录旧偏差：`tryLeave` 过去要求位置已出区域，导致 `DOCK_LEFT` 记在出界刻；需要开发一接入新的同刻离开语义。
- 节点中心死循环、真死路、出生点反向、连续穿越多个节点和单槽方向意图仍需开发一修复并测试。

开发三不要直接修改这些文件。收到开发一的 provider commit SHA 后，先重新读取实际源码和测试，再更新本交接状态。

### 6.2 开发二：共享时间和 R5-B

尚需确认：

- `TickContext`、`EchoQueue`、`sourceRound` 的正式只读契约。
- `ResonanceSnapshotPort` 和 `AutoDockSnapshotPort` 的只读聚合方式。
- `ROUND_END`、`FULL_RESTART` 的机关恢复顺序。
- 恢复后不残留共振来源、autoDock 占用或区域边沿记忆。

开发三不接入 `snapshot/**` 聚合器，也不在 `snapshot/**` 复制共振窗口、残影来源或 autoDock 计时状态。

### 6.3 PM / 集成

尚需完成：

- 从 `app/PathGraphBridge` 删除 `defaultExit` 映射。
- 继续完成 `Level01Assembly` 的四方向输入和同刻 autoDock 离开接线；PR #49 已有部分 held direction / newest edge 适配，但不能视为 APP-1 完成。
- 提供当前有效的 README、C4/R5 决策版本或明确替代版本。
- 冻结 `baseSpeed`、`tileSize`、`epsilon`、`SLOWED` 倍率、持续 tick 和固定步长按键脚本。
- 批准 P4-B 共振区、中继 I/II、核心终端的稳定 ID 与 schema。

### 6.4 QA / 当前风险

QA 需要单独验证：

- 合法/非法 `tryLeave`、同 tick 重入、轮末和整局重开。
- core/entity/app 中旧自动选路引用的清理情况。
- `MovementState.SLOWED` 的生产者和测试。
- C3DockController 同刻 `DOCK_LEFT` 与占用释放。
- R5-B 快照恢复的确定性和不可变性。
- 第一关两轮移动、驻留、同刻离开和门开启因果链。
- 改动路径是否仍只属于对应责任人。

另外，当前 `Level03Footsteps` 中 `left_path_2` 和 `left_return` 使用了相同世界坐标，而 `LevelGeometryImpl` 会拒绝重复节点坐标。开始依赖第三关几何前，必须先用测试或构造调用确认这一点，并由负责 `level/**` 的成员修复或给出明确设计解释。

## 7. 推荐接手流程

### 阶段 A：保护现状

1. 在本 worktree 执行 `git status --short --branch`、`git diff --name-only` 和 `git diff --check`。
2. 阅读第 3 节资料和本目录四张前置条件卡。
3. 不清理、回滚、覆盖当前未提交改动；不把历史 commit SHA 当作当前 live 状态。

### 阶段 B：等待跨模块契约

1. 等开发一提供 CORE-1、CORE-2、CORE-3、ENT-1、ENT-2 的 commit SHA 和破坏性变更清单。
2. 等开发二提供 R5-B 聚合接口、轮次恢复顺序和测试证据。
3. 等 PM 完成 APP-1，并确认 README、决策文档、移动参数和按键脚本版本。
4. 对每个 provider commit 重新执行源码引用搜索；不能只相信交接文字。

### 阶段 C：跨模块验证

在不修改他人工作树的前提下，至少执行：

```powershell
.\mvnw.cmd -q test
.\mvnw.cmd -Dtest=LevelDataValidatorTest,AutoDockServiceTest,StableMechanismIdTest test
git diff --check
```

如果 GUI 代码已接入，再由目标机手工执行：

```powershell
.\mvnw.cmd javafx:run
```

自动化/headless 测试不能证明玩家移动、页面切换、HUD、Canvas 或机关视觉已经正确显示；这些结果必须单独记录为手工/视觉证据。

### 阶段 D：才开始 P4-B

只有第 8 节所有启动门槛都打勾后，才可以：

- 创建第四、第五关稳定数据；
- 固定共振区、中继 I/II、核心终端 ID 和 schema；
- 通过固定步长模拟计算到达刻；
- 校准第五关的必要时滞和共振窗口；
- 接入 UI/Canvas 的机关状态投影。

禁止用猜测的 `60 tick` 延迟代替正式模拟结果，也禁止在参数未冻结时写正式关卡到达刻。

## 8. P4-B 启动门槛

- [ ] 当前分支改动已由用户提交并推送，记录新的 commit SHA。
- [ ] 开发一 CORE-1、CORE-2、CORE-3、ENT-1、ENT-2 已稳定。
- [ ] PM 已完成 APP-1，`app` 不再依赖 `defaultExit`。
- [ ] 开发二已确认 R5-B 快照聚合和轮次恢复顺序。
- [ ] PM 已提供有效 README / 决策文档版本。
- [ ] `tileSize`、`baseSpeed`、`epsilon`、`SLOWED` 倍率和持续 tick 已冻结。
- [ ] 固定步长按键脚本已冻结，并有第二关/第五关模拟证据。
- [ ] 开发一提供 `resonanceAura` 与固定区域几何相交结果。
- [ ] P4-B 稳定 ID、schema、路线和必要减速规则已获批准。
- [ ] QA 已完成跨模块、路径和至少一次目标机手工验收。

任何测试失败、越权文件、公共契约缺失、重复计时器或参数漂移，都应停止 P4-B，退回对应责任人。

## 9. 最小回传格式

接手人完成一项前置后，请按以下格式回传，不要只说“已完成”：

```text
负责人：
任务卡：
provider commit SHA：
修改路径：
新增/删除公共类型：
破坏性变更：
验证命令及结果：
自动化证据：
手工/视觉证据：
剩余阻塞：
是否允许进入下一阶段：是 / 否
```

## 10. 可直接转发的接手摘要

你接手的是《时痕实验室：昨日的我》的 P-MOVE-ALIGN 继续开发工作。开发三工作树分支是 `codex/dev3-1`，HEAD 为 `ab1a766b84de84f83a27b4cd89026194eaf03b1d`，工作树有未提交的开发三改动；本文件提交前的 `develop` 基线是 `c8a513c72435182cf3d2f5cdc69bc380d39279d4`。开发三侧的 autoDock、Level01 稳定 ID/几何适配和 P4-A 共振纯 Java 状态机已经提供；PR #49 已部分接入玩家移动 app 调用，但 defaultExit、旧自动选路、减速端口、同刻 autoDock 语义、开发二 R5-B 聚合以及参数冻结仍未全部完成。

请先保护并审计现有工作树，按责任边界推进前置条件；不要代改 `core/**`、`entity/**`、`app/**`、`replay/**` 或 `snapshot/**`，不要开始 P4-B 正式关卡数据和到达刻定参。每项交付都必须提供 commit SHA、改动路径、破坏性变更、测试结果和剩余阻塞；提交、推送和 PR 由用户/PM 执行。
