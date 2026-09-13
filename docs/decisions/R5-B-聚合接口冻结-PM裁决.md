# R5-B 聚合接口冻结（PM 裁决）

> 依据：`docs/development/开发2/R5-B-P-MOVE-ALIGN-开发二交接文档.md` Gate B/C；开发三 2026-09-13《R5-B 聚合接口提案》
> 基线：`origin/develop` = `418d0ca`（PM clean 复跑 **358/0/0**；#66/#68 合并后为 362/0/0）
> 状态：**Gate B 三项契约已冻结**；字段清单作为输入采纳（附一处必改 + 一处待补）
> 责任：开发三（per-mechanism typed port、`mechanism/**`）｜开发二（聚合器，`snapshot/**`）｜PM（app 接线、README/决策文档）

## 一、Gate A 复核：**已满足**（PM 独立核对）

| dev2 文档要求 | PM 实测（`418d0ca`） | 结论 |
| --- | --- | --- |
| `core/MovementState.IDLE` | `core/MovementState` 含 `IDLE/CRUISING/SLOWED/DOCKED` | ✅ |
| 共振源码在同一基线 | `mechanism/resonance/**` 6 个文件在 develop | ✅ |
| R5-A `MechanismSnapshot` | 在 develop（`snapshot/**`） | ✅ |

## 二、Gate B 输入：typed port 清单（采纳开发三逐字实测）

1. `DockingPlate.Snapshot(mechanismId, state, occupantId, occupantSourceRound)`
2. `Door.Snapshot(mechanismId, state)`
3. `ExitTerminal.Snapshot(mechanismId, doorUnlocked, triggered)`
4. `AutoDockSnapshotPort → AutoDockStateSnapshot(List<DockSnapshot(mechanismId, occupancy, reentryBlockedAtTick)>)`
5. `ResonanceSnapshotPort → ResonanceStateSnapshot(state, armedAtRoundTick, armedSourceRound, currentPlayerInside, insideEchoSourceRounds)`

**待补（开发三 + 开发二在冻结前给结论）**：R5-B 交接文档提到「射线 / 中继 / 核心」快照。
- 射线：`Ray.java` 已按 BUG-002 Phase 2 裁决**计划删除** → 若确认无端口需求，请写明「不需要」；
- 中继 / 核心：若其状态已在 `ResonanceStateSnapshot` 内（`state/…`）覆盖，请写明「由 #5 覆盖」；
- 否则补第 6/第 7 个端口，不要在聚合器里用具体类旁路。

## 三、Gate B 三项契约（**冻结**）

### 3.1 B-3/B-4 前半：`ROUND_END` 与 `FULL_RESTART` **一律 `reset(reason)`，禁止恢复快照**

采纳开发三的论证，PM 已独立复核其两个前提：

1. `ResonanceStateMachine.observe(...)` 在共享 `roundTick` 回退时抛
   `IllegalStateException("共享 roundTick 回退；轮次边界必须先调用 resonance.reset(ROUND_END)")`（源码 L182）；
2. `RecordingSession.restartFromFirstRound(restorer)` 的顺序是
   `echoQueue.clear() → restorer.run() → clock.transition(READY)`，而 **READY 会把 `roundTick` 归零**
   → 在归零前恢复一个 `armedAtRoundTick = T > 0` 的快照，下一次 `observe` 必然抛错。

**冻结条款**：
- **快照恢复只允许「轮内、时钟不回退」的场景**（同轮暂停/重试/回滚）；
- 聚合器在 `apply` 前必须校验 `armedAtRoundTick <= 当前共享 roundTick`（**非 ARMED 状态免校验**）；
- 任何「先恢复、后归零时钟」的调用序都视为**契约违反**，请在评审中直接打回。

### 3.2 B-4 后半：`FULL_RESTART` 恢复顺序（冻结）

```
① validate(aggregateSnapshot)          // 纯读校验，任何失败在此返回
② autoDock.reset(FULL_RESTART, tick)
③ resonance.reset(REASON)              // 见 §四：SCENE_EXIT 补完后按场景区分
④ 驻留板 / 门 / 出口 reset()
⑤ 清空残影队列与录制缓冲
⑥ 恢复玩家到出生点（app 侧 resetTo）
⑦ 时钟回 READY（由 RecordingSession 负责，不归聚合器）
```
②③④ 必须发生在时钟归零之前，且都不依赖时钟。该顺序由 **app 注入的 restorer** 调用聚合器实现，
`RecordingSession` 只负责在 READY 之前调用 restorer（现状一致，无需改 replay）。

### 3.3 B-5：两段式 `validate` → `apply`（冻结）

- `validate(snapshot)`：**纯函数**（不写任何状态）—— 校验 ID 集合完整匹配、null、状态组合、跨端口不变量；
- `apply(snapshot)`：**通过 `validate` 后不得再抛错**；禁止「边校验边写」；
- 沿用 R5-A `MechanismSnapshot.restore` 的既有范式（先校验三张 Map 的 ID 集合再逐个 restore），
  不允许退化成半恢复；
- 必须有测试：**故意损坏的快照被 `validate` 拒绝，且世界状态零变化**（半恢复防护）。

## 四、`ResonanceResetReason` 缺 `SCENE_EXIT`：**批准补齐**

PM 复核属实：`AutoDockResetReason = {ROUND_END, FULL_RESTART, SCENE_EXIT}`，
而 `ResonanceResetReason = {ROUND_END, FULL_RESTART}`。

**裁决**：**由开发三在 `mechanism/resonance/**` 内为 `ResonanceResetReason` 增加 `SCENE_EXIT`**，
与 `AutoDockResetReason` 对齐（枚举加常量属源码兼容，请确认无 ordinal 序列化依赖）。
理由：让聚合器把「场景退出」降级映射成 `FULL_RESTART` 会把语义泄漏进实现，
而 README §三 明确区分「暂停只冻结本轮」「重开或退出关卡是放弃整个会话」。

**要求**：
1. 三值语义写进 `开发三-稳定ID与排序-autoDock规格冻结.md`；
2. 补一条测试：`SCENE_EXIT` 与 `FULL_RESTART` 都清空 ARMED 且互不混淆；
3. 聚合器不再做原因降级映射（补完后不需要 `mapToFullRestart` 之类代码）。

## 五、所有权与实施顺序

1. **开发三**：补 `SCENE_EXIT`；给出「射线/中继/核心」端口结论；为 `GameEvent.sourceRound` 补不变量 javadoc
   （0 = 活玩家不得替换；N ≥ 1 = 第 N 轮残影且必须与 `echo_<N>` 一致）；
2. **开发二**：按 §二 冻结字段 + §三 三项契约实现聚合器（`snapshot/**`），产出 `validate/apply` 与测试；
3. **PM**：在 app 的 restorer 里接线（`start` / `restartFromFirstRound` / 轮末三处），并把本文件登记为冻结依据；
4. **签名冻结**：本文件即三方（provider 开发三 / consumer 开发二 + PM）确认记录；后续任何字段或语义变更须走新提案。

## 六、验收（R5-B）

1. `validate` / `apply` 两段式 + 半恢复防护测试通过；
2. `ROUND_END` 与 `FULL_RESTART` 走 `reset` 的测试通过，且不存在「恢复 ARMED 快照后时钟归零」的调用路径；
3. 轮内恢复（时钟不回退）路径有测试：恢复后 `observe` 不抛错、状态与快照一致；
4. `SCENE_EXIT` 三值语义测试通过；
5. `.\mvnw.cmd -o clean test` 退出码 0、任何单测 ≤ 1s、数字回填。
