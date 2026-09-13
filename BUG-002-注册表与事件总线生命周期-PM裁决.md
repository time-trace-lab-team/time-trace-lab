# BUG-002 · 注册表与事件总线的生命周期（PM 立项与方案裁决）

> 任务 ID：**BUG-002-LIFECYCLE**（独立技术债卡；不并入 `X-MOVE-COLLAPSE-01` 移动收口主线）
> 提出：开发三（2026-09-12 提案）｜ 主责：**开发三**（`mechanism/**` + 其测试）｜ 配合：**PM**（`app/**` 接线与集成测试清理）
> 状态：**已立项，Phase 1 可开工**（Phase 2 有前置，见 §4）
> 日期：2026-09-12

## 一、PM 复核（已在本地复现，证据可保留）

临时探针（已跑完即删，未提交）在同一 JVM 内连续构造：

```
[PROBE] 未 cleanup 时第二次构造 -> IllegalArgumentException: 重复的驻留板 ID: L01_plate_left
[PROBE] cleanup 之后第二次构造 -> OK
```

确认：`DockingPlate` 构造器自动注册到全局单例（`DockingPlate.java:26`），`dispose()` 反注册（`:86`），
`Door.checkAllPlatesOccupied()` 直接取单例（`Door.java:45`）。**同一 JVM 第二次构造装配即抛异常**。

**影响面（PM 扫全仓）**：生产代码 4 处（`DockingPlateRegistry`、`DockingPlate`、`Door`、`app/Level01Assembly`）；
**测试 10 个类**依赖全局单例并各自手工 `clear()`：
`app/Level01AssemblyTest`、`app/Level01AssemblyMovementTest`、`app/Level01AssemblyRoundLoopTest`、
`app/Level01AssemblyLevel01FlowTest`、`level/Level01CausalChainTest`、`level/Level01TwoRoundSimulationTest`、
`mechanism/DockingPlateEchoDisappearanceTest`、`mechanism/StableMechanismIdTest`、
`mechanism/MechanismSnapshotTest`、`snapshot/MechanismSnapshotTest`。

## 二、裁决 1：立项，责任人开发三

**立项**，卡号 `BUG-002-LIFECYCLE`。`mechanism/**` 归开发三；`app/**`（装配持有与清理、集成测试）归 PM。
README §三 要求「从第一轮重开」与关卡选择，`RecordingSession` 的契约也要求场景退出后不得残留旧会话引用 ——
全局单例与「每关一套机关实例」的生命周期模型确实冲突，值得作为技术债单独立项。

## 三、裁决 2：采用 **C（纯增量注入）→ 再 B（删单例）**；**不采纳 A**

| 方案 | 裁决 |
| --- | --- |
| A 构造时先 `clear()` | **不采纳**。只治顺序重建，两实例并存仍互相踩，且把隐式生命周期藏得更深 |
| **C 加法过渡** | **Phase 1 立即做**（纯新增重载，不破坏现有签名） |
| **B 实例化** | **Phase 2**：app 与测试全部迁移完成后，删除单例（有前置，见 §4） |

C 与 B **同一张卡、两个 PR**，不合并不跨批。

### 3.1 Phase 1 的 API 形态（PM 追加要求）

不要让 `Door` 依赖整个注册表实现，抽**窄端口**（与 C4 裁决「窄端口优于具体类」一致）：

```java
// mechanism/DockingPlateOccupancyPort（新，窄接口）
void register(DockingPlate plate);
void unregister(String plateId);
boolean isOccupied(String plateId);

// DockingPlateRegistry：保留 getInstance()，并公开构造器（implements 上述端口）
public DockingPlateRegistry();

// DockingPlate / Door：新增接收端口的构造器；旧构造器等价于传 getInstance()（兼容）
public DockingPlate(String id, Vector2D position, DockingPlateOccupancyPort occupancy);
public Door(String id, Vector2D position, Set<String> requiredPlateIds, DockingPlateOccupancyPort occupancy);
```

### 3.2 迁移纪律（防“分裂状态”）

Phase 1 落地后，**app 必须在同一批提交里切到注入实例**（不同批不得合）；
不允许长期「有的板注册到单例、有的注册到实例」——本项目已经吃过一次双源不一致的亏
（`DOCK_LEFT` 已发而占用仍被拒）。规格文档里写明「新代码一律用注入」。

## 四、Phase 2 前置（必须先给结论）

快照/恢复族**确实引用了注册表**（`mechanism/MechanismSnapshotTest`、`snapshot/MechanismSnapshotTest`），
因此开发三在 Phase 2 之前必须给出结论：`DockingPlate.Snapshot`、`MechanismSnapshot`、
`AutoDockSnapshotPort` 是否**隐式依赖单例**；若依赖，Phase 1 就要一并处理，否则 Phase 2 删单例会炸。
结论写进交付文档，PM 据此决定 Phase 2 是否开工。

## 五、裁决 3：`EventDispatcher` 与 `RayManager` 一并纳入同卡

避免三次返工，纳入同一张卡，但粒度分清：

- `EventDispatcher`（`mechanism/event/`）：与注册表同模式（全局监听器表、测试需手工 `clear()`）→ 与注册表**同批做注入**；
- `RayManager`（`mechanism/ray/`）：**目前零接线** → 若确认无任何生产引用，**直接删除**（与已删的 `PhaseManager` 同类），
  不要为死代码做生命周期改造；请开发三先给一句结论。

## 六、裁决 4：机制规格文档新增冻结节

`docs/development/开发3/开发三-稳定ID与排序-autoDock规格冻结.md` 新增一节
**「注册表与事件总线的生命周期」**，内容至少包含：实例归属（每关装配持有）、注入优先、
单例仅为兼容层并在 Phase 2 删除、场景退出/重开时必须释放的引用清单。**批准**。

## 七、验收（Phase 1）

1. 同一 JVM 连续 `new Level01Assembly()`（不 `cleanup()`）不再抛异常；PM 会把这次的探针**转正**为 app 集成测试；
2. **两个装配实例同时存活互不干扰**：A 的门不感知 B 的板占用，B 释放不影响 A（新增 mechanism 测试 + app 测试各一条）；
3. app 侧 4 个集成测试**移除 `@AfterEach` 全局 `clear()`** 后仍全绿；
4. mechanism 侧测试改为每用例独立实例（不再手工清单例）；
5. `.\mvnw.cmd -o clean test` 退出码 0，**任何单测 ≤ 1s**；数字与基线哈希回填交付文档；
6. 规格文档新增节已落地；Phase 2 的删除清单与前置结论一并给出。

## 八、Phase 1 收尾完成记录（PM，2026-09-12）

机制侧随 **PR #64** 合入 `develop`（`442b895`）；PM 侧 app 收尾已开 **PR #66**
（`codex/app-bug002-p1-app` @ `cffbf4a`，6 files / +184-40，`mergeable=clean`）：

| 验收项 | 状态 | 证据 |
| --- | --- | --- |
| ① 同 JVM 连续构造不依赖全局清理 | ✅ | `Level01AssemblyLifecycleTest.repeatedConstructionNoLongerNeedsGlobalCleanup` |
| ② 两装配同时存活互不干扰 | ✅ | `twoAssembliesDoNotSharePlateOccupancy`（A 的残影占左板，B 的板不受影响；全局注册表仍为空） |
| ③ app 测试移除全局 `clear()` 仍全绿 | ✅ | 四个 app 集成测试类已移除 `@AfterEach` 清理 |
| ④ 机制侧每用例独立实例 | ✅ | 开发三 Phase 1 交付 |
| ⑤ 全量绿、单测 ≤1s | ✅ | `.\mvnw.cmd -o clean test` → **350 / 0 / 0**，墙钟 10s |
| ⑥ 规格文档新节 | ✅ | 开发三 Phase 1 交付 |

**改前反证**（临时探针，未提交）：未 `cleanup()` 时第二次构造 → `IllegalArgumentException: 重复的驻留板 ID: L01_plate_left`。
**残差检查**：`app/**` 内 `DockingPlateRegistry.getInstance()` / `EventDispatcher.getInstance()` **零命中**。

**Phase 2 状态**：前置只剩一条 —— 开发三需给出「`DockingPlate.Snapshot` / `MechanismSnapshot` /
`AutoDockSnapshotPort` 是否隐式依赖兼容单例」的结论；结论到位后 PM 发 Phase 2 开工令。

### 8.1 附带发现：第一轮时钟门槛（另议）

`develop` 上 `Level01Assembly.tick()` 有一段门槛（来自 PR #65 BUG-001 修复）：
**第一轮在首个方向输入出现前不写帧、不推进 `roundTick`**。影响：

1. 该规则**未写入 README**；
2. 第一关 16 秒读秒从「首次方向输入」而非「进入 `PLAYING`」起算 → 与 README §三「进入 PLAYING 后从
   `roundTick = 0` 启动」的表述不一致，也会影响到达刻口径与残影代际对齐；
3. PM 侧测试已按此门槛调整推进方式（所有推进都从一次真实方向输入开始）。

PM 将与开发一/测试确认后决定：**补进 README 并写清**，或**调整实现**（例如只在 README 允许的 READY 冻结内生效）。

## 九、附：BUG-001 报告未到手

`BUG-001-段中间转向锁死与驻留板原路返回.md` **不在本地工作树，也不在本地对象库的任何 ref 里**
（PM 检索过）。不过其修复已随 **PR #65**（`codex/bug-001-turn-assist`）进入 `develop` ——
若你手头有原报告，请补推或贴给 PM，用于核对修复是否覆盖报告里的全部场景
（尤其是「驻留板原路返回」那一条，与 §8.1 的时钟门槛可能相互影响）。

