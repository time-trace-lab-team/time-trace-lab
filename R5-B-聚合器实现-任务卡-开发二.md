# 任务卡 · R5-B 聚合器实现（开发二）

> 任务 ID：**R5-B-AGGREGATOR**
> 主责：**开发二**（`snapshot/**` + 其测试）｜ 上游 provider：**开发三**（typed port、`mechanism/**`）
> 依据（**冻结，不得改签名**）：`docs/decisions/R5-B-聚合接口冻结-PM裁决.md`
> 基线：`origin/develop` = **`1aa58ab`**（PM clean 复跑 **381 / 0 / 0**）
> 状态：**开工**；其中依赖 `ResonanceResetReason.SCENE_EXIT` 的部分与 `BUG-002-LIFECYCLE-P2` 并行（见 §四）
> 日期：2026-09-13

## 一、目标

按冻结契约实现**机制状态聚合器**：一次调用即可**校验并恢复**整关机关状态（驻留板 / 门 / 出口 / autoDock / 共振），
供轮内暂停-恢复与重开路径使用。冻结契约的核心不是「能恢复」，而是**不会半恢复**、**不会把时钟回退**。

## 二、冻结输入（逐字采纳，不得自行增删字段）

1. `DockingPlate.Snapshot(mechanismId, state, occupantId, occupantSourceRound)`
2. `Door.Snapshot(mechanismId, state)`
3. `ExitTerminal.Snapshot(mechanismId, doorUnlocked, triggered)`
4. `AutoDockSnapshotPort → AutoDockStateSnapshot(List<DockSnapshot(mechanismId, occupancy, reentryBlockedAtTick)>)`
5. `ResonanceSnapshotPort → ResonanceStateSnapshot(state, armedAtRoundTick, armedSourceRound,
   currentPlayerInside, insideEchoSourceRounds)`

**明确不做**：`lastEvictedEchoes()` 之类的「顺手加个查询」已在裁决中**否决**（残影淘汰不在聚合器职责内）。
射线 / 中继 / 核心是否需要第 6 / 第 7 个端口，等开发三在 `BUG-002-LIFECYCLE-P2` 给出书面结论；
**结论到位前不要用具体类旁路**。

## 三、三项冻结契约（缺一项即打回）

### 3.1 `ROUND_END` 与 `FULL_RESTART` 一律 `reset(reason)`，禁止恢复快照

`ResonanceStateMachine.observe(...)` 在共享 `roundTick` 回退时抛
`IllegalStateException("共享 roundTick 回退；轮次边界必须先调用 resonance.reset(ROUND_END)")`，
而 `RecordingSession.restartFromFirstRound(restorer)` 的顺序是
`echoQueue.clear() → restorer.run() → clock.transition(READY)`，**READY 会把 `roundTick` 归零**。
→ 任何「先恢复 `armedAtRoundTick = T > 0` 的快照、后归零时钟」的调用序都是**契约违反**。

**快照恢复只允许「轮内、时钟不回退」**：`apply` 前必须校验 `armedAtRoundTick <= 当前共享 roundTick`
（**非 ARMED 状态免校验**）。

### 3.2 重开顺序（冻结）

```
① validate(aggregateSnapshot)     // 纯读校验，任何失败在此返回，世界零变化
② autoDock.reset(FULL_RESTART, tick)
③ resonance.reset(reason)         // ROUND_END / FULL_RESTART / SCENE_EXIT
④ 驻留板 / 门 / 出口 reset()
⑤ 清空残影队列与录制缓冲           // 由 replay 侧负责，聚合器不碰
⑥ 恢复玩家到出生点                 // app 侧 resetTo
⑦ 时钟回 READY                    // RecordingSession 负责，不归聚合器
```

②③④ 必须发生在时钟归零之前，且都不依赖时钟。

### 3.3 两段式 `validate` → `apply`

- `validate(snapshot)`：**纯函数**，不写任何状态；校验 ID 集合完整匹配、null、状态组合、跨端口不变量；
- `apply(snapshot)`：**通过 `validate` 后不得再抛错**；禁止「边校验边写」；
- 沿用 R5-A `MechanismSnapshot.restore` 的既有范式（先校验三张 Map 的 ID 集合，再逐个 restore）；
- **必须有**「故意损坏的快照被 `validate` 拒绝，且世界状态零变化」的测试（半恢复防护）。

## 四、并行与前置

- 本卡的 `validate` / `apply` / 半恢复防护 / `ROUND_END`·`FULL_RESTART` 走 `reset` 的部分**现在就做**；
- `ResonanceResetReason.SCENE_EXIT` 由开发三在 `BUG-002-LIFECYCLE-P2` 内补齐。
  **在它落地前**：不要写 `SCENE_EXIT`、也不要写「把 SCENE_EXIT 映射成 FULL_RESTART」的降级代码
  （裁决已明确不许可）。先留 TODO 注释并在交付文档里写明等待项；
- 若开发三的端口结论要求第 6 / 第 7 个端口，PM 会补一张增量卡，不在本卡内自行设计。

## 五、允许 / 禁止路径

**允许**：`src/main/java/org/example/timeloop/snapshot/**`、`src/test/java/org/example/timeloop/snapshot/**`。

**禁止**：`mechanism/**`（开发三）、`app/**`（PM）、`replay/**`、`core/**`、`render/**`、`pom.xml`、`README.md`。
需要 provider 侧改动时**提接口申请给 PM**，不要自己动 `mechanism/**`。

## 六、附带交付（同批，避免二次返工）

1. `EchoQueue` 的 javadoc 钉死口径：**淘汰清单只记「寿命淘汰」，容量超出仅作防御性断言**（不是淘汰来源）；
2. app 侧 restorer 接线由 PM 负责，开发二只需保证聚合器的调用面在 `snapshot/**` 内自洽、
   并提供一段**最小调用示例**（javadoc 或文档）说明 `validate` → `apply` 与 ②③④ 的先后。

## 七、验收

1. `.\mvnw.cmd -o clean test` 退出码 0，**任何单测 ≤ 1 s**，数字回填；
2. `validate` 拒绝损坏快照且世界零变化（半恢复防护）测试通过；
3. `ROUND_END` / `FULL_RESTART` 走 `reset` 的测试通过，且**代码中不存在**「恢复 ARMED 快照后时钟归零」的路径
   （交付文档里给出你的排查方式，例如列出所有调用 `apply` 的位置）；
4. 轮内恢复（时钟不回退）测试：恢复后 `observe` 不抛错、状态与快照一致；
5. `EchoQueue` javadoc 已钉口径；
6. 未新增未被冻结的字段 / 方法（特别是 `lastEvictedEchoes()`）。

## 八、证据与回传格式

```
branch: codex/r5b-aggregator-dev2
SHA:    <40 位>
clean test: Tests run / Failures / Errors / Skipped = ? / ? / ? / ?
最慢单测: <名称> <秒>
新增 API: <类/方法逐条，标注是否来自冻结契约>
等待项:   SCENE_EXIT（开发三 BUG-002-P2）/ 第 6·7 端口结论
调用点清单: 所有 apply 调用位置（证明无「先恢复后归零」）
```

**交付以远端提交为唯一凭据**；禁止 `git add -A`，一律精确白名单提交。

## 九、停止条件

1. 冻结契约里的字段在 `mechanism/**` 实际接口中**对不上** → 停手回报（附实际签名），不要自行改契约；
2. 发现必须改 `mechanism/**` 才能实现 → 停手回报，转 provider 卡；
3. `validate` 无法做到「纯读」（例如某个 provider 的读取会改状态）→ 停手回报，这是 provider 侧缺陷；
4. 出现单测 > 1 s 且无法解释 → 停手回报。
