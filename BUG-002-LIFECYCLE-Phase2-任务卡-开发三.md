# 任务卡 · BUG-002-LIFECYCLE **Phase 2**（删单例）

> 任务 ID：**BUG-002-LIFECYCLE-P2**
> 主责：**开发三**（`mechanism/**` + 其测试）｜ 配合：**PM**（`app/**`，Phase 1 已完成注入接线）
> 依据：`BUG-002-注册表与事件总线生命周期-PM裁决.md` §三/§四/§五 + `docs/decisions/R5-B-聚合接口冻结-PM裁决.md` §二/§四
> 基线：`origin/develop` = **`1aa58ab`**（PM clean 复跑 **381 / 0 / 0**）
> 状态：**开工**（Phase 2 的唯一前置见 §一，已由 PM 复核满足）
> 日期：2026-09-13

## 一、前置复核（PM 已完成，结论：可开工）

| 前置 | 状态 | 证据 |
| --- | --- | --- |
| Phase 1 机制侧合入 | ✅ | PR #64 |
| Phase 1 app 侧注入接线合入 | ✅ | PR #66（`a057e81`） |
| `app/**` 内 `DockingPlateRegistry.getInstance()` / `EventDispatcher.getInstance()` | ✅ 零命中 | PM 残差检查（Phase 1 收尾记录 §八） |
| 两个装配实例同时存活互不干扰 | ✅ | `Level01AssemblyLifecycleTest.twoAssembliesDoNotSharePlateOccupancy` |
| 「快照族是否隐式依赖兼容单例」结论 | ⚠️ **本卡交付物之一** | 见 §二 第 6 条；结论未给完之前不得删单例最后一步 |

> 本卡不跨模块：**`app/**` 归 PM**。若发现 `app/**`、`render/**`、`replay/**` 仍有单例引用，
> **停手回报 PM**（附 `git grep` 输出），由 PM 开接线批；不要在别人路径上顺手改。

## 二、交付物

1. **删除 `DockingPlateRegistry` 全局单例**：移除 `getInstance()` 与静态持有；`DockingPlateRegistry` 保留公开构造器
   （Phase 1 已具备）与 `DockingPlateOccupancyPort` 实现。兼容构造器（自动取 `getInstance()` 的那几个）
   **一并删除**，并在交付文档里逐一列出删除了哪些签名。
2. **删除 `EventDispatcher` 全局单例**：同上，保留公开构造器与 `GameEventBus` 实现。
3. **删除 `mechanism/ray/Ray.java`**：先给**零引用证明**（`git grep -n "new Ray("`、`git grep -n "\bRay\b"` 的完整输出）。
   与已删的 `RayManager`/`PhaseManager` 同类：死代码不做生命周期改造，直接删。
   若发现**存在**生产引用（例如第二关已开工的射线接线），**停手回报 PM**，不擅自决定。
4. **`ResonanceResetReason` 增加 `SCENE_EXIT`**（`mechanism/resonance/**`）：
   - 与 `AutoDockResetReason = {ROUND_END, FULL_RESTART, SCENE_EXIT}` 对齐；
   - 确认无 ordinal 序列化依赖（`values()[i]`、`ordinal()`、持久化索引）；
   - 补测试：`SCENE_EXIT` 与 `FULL_RESTART` **都清空 ARMED 且互不混淆**；
   - 规格文档写清三值语义。
5. **`GameEvent.sourceRound` 不变量 javadoc**：`0` = 活玩家（不得被残影替换）；`N ≥ 1` = 第 N 轮残影，
   且必须与 actor `echo_<N>` 一致。
6. **R5-B §二「待补」端口结论**（书面，逐条）：
   - 射线：是否需要独立快照端口 → 写「不需要」或给规格；
   - 中继 / 核心：状态是否已被 `ResonanceStateSnapshot(state, armedAtRoundTick, armedSourceRound,
     currentPlayerInside, insideEchoSourceRounds)` 覆盖 → 写「由 #5 覆盖」或补第 6 / 第 7 个 typed port；
   - **不得**让聚合器用具体类旁路。
7. **测试迁移**：所有仍依赖单例的机制/关卡/快照测试改为「每用例独立实例」。已知相关类
   （以实际 `git grep` 为准）：`level/Level01CausalChainTest`、`level/Level01TwoRoundSimulationTest`、
   `snapshot/MechanismSnapshotTest`、`mechanism/MechanismSnapshotTest`、`mechanism/DockingPlateEchoDisappearanceTest`、
   `mechanism/StableMechanismIdTest`。
8. **规格文档**：`docs/development/开发3/开发三-稳定ID与排序-autoDock规格冻结.md` 的
   「注册表与事件总线的生命周期」节更新为：**单例已删除**、实例归属每关装配、场景退出/重开必须释放的引用清单。

## 三、允许 / 禁止路径

**允许**：`src/main/java/org/example/timeloop/mechanism/**`、`src/test/java/org/example/timeloop/mechanism/**`、
`src/test/java/org/example/timeloop/{level,snapshot}/**`（**仅**为迁移单例依赖所必需的改动）、
`docs/development/开发3/**`。

**禁止**：`app/**`（PM）、`render/**`、`replay/**`、`core/**`、`ui/**`、`pom.xml`、`README.md`、任何他人任务卡的未授权改动。
改动 `level/**` 或 `snapshot/**` 生产代码同样禁止（只能改它们的测试）。

## 四、验收（缺一不可）

1. `.\mvnw.cmd -o clean test` 退出码 0，**数字回填**（基线 `1aa58ab` = 381；本卡删除兼容构造器可能减少 0 个用例，
   如因测试迁移有增减请逐条说明）；
2. **任何单测 ≤ 1 s**（超时即回归，必须说明原因）；
3. `git grep -n "getInstance" -- src/main/java/org/example/timeloop/mechanism` → **零命中**；
4. `git grep -n "\bRay\b" -- src/main src/test` → 仅剩注释/文档（或零命中），证明 `Ray.java` 删除安全；
5. Phase 1 的两条关键测试仍绿：同一 JVM 连续 `new Level01Assembly()` 不抛异常、两装配互不干扰；
6. `SCENE_EXIT` 测试、`sourceRound` javadoc、R5-B 端口结论、规格文档更新**全部落地**。

## 五、证据与回传格式

```
branch: codex/bug002-phase2-dev3
SHA:    <40 位>
clean test: Tests run / Failures / Errors / Skipped = ? / ? / ? / ?
最慢单测: <名称> <秒>
grep 证据: getInstance（mechanism）→ 0；Ray → <输出>
删除清单: <被删的类/构造器/方法逐条>
前置结论: 快照族是否隐式依赖单例 → <是/否 + 依据文件与行号>
R5-B 端口结论: 射线 <…>；中继/核心 <…>
```

**交付以远端提交为唯一凭据**；禁止 `git add -A`（工作区可能含他人文档），一律精确白名单提交。

## 六、停止条件

1. 发现 `app/**` 或其它非授权模块仍有单例引用 → 停手回报（附 grep），等 PM 接线批；
2. 发现 `Ray` 有生产引用 → 停手回报，不擅自删除；
3. 删除兼容构造器导致**非本模块**测试无法编译且无法只改测试修复 → 停手回报，由 PM 决定分批顺序；
4. 出现任何单测 > 1 s 且无法解释 → 停手回报，不带着回归交付。
