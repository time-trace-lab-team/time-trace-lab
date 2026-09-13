# TEST-PR17-任务卡

## 1. 基本信息

| 项目 | 内容 |
|------|----|
| 任务编号 | TEST-PR17 |
| 对应 T 模块 | T1（核心灰盒）/ T2 前置（机关事件链） |
| 被测 PR | #17 feat(content): 实现 P1 事件与基础机关（驻留板/门/出口终端） |
| 被测分支 | `pr-17` |
| 被测提交哈希 | `9f91dea` |
| 测试日期 | 2026-09-09 |
| 测试人员 | 成员 E |

---

## 2. 环境记录（指南 1.3）

| 项目 | 记录 |
| --- | --- |
| Windows 版本 | Windows 11 |
| JDK 供应商与完整版本 | Azul Zulu 17.0.16（`java -version`：OpenJDK 17.0.16 LTS） |
| Maven/Wrapper 版本 | 3.9.11（`.\mvnw.cmd -version`） |
| JavaFX 版本 | 17.0.20（`pom.xml`） |
| 屏幕缩放与分辨率 | 100% / 1920×1080（默认记录） |
| 音频设备/音量 | 默认（本 PR 无音频相关） |
| 测试命令 | `.\mvnw.cmd test` |
| 测试总数 / 通过 / 失败 / 跳过 | 33 / 33 / 0 / 0 |
| 手工测试范围 | 无 GUI 入口（本 PR 为纯逻辑机关/数据，未含 JavaFX 启动） |

---

## 3. 本次任务允许修改的路径（指南 2.2）

- `src/test/java/org/example/timeloop/**`（补充机制/事件测试）
- `docs/testing/**`（本文件）

---

## 4. 本次任务禁止修改的路径（红线）

- `pom.xml`
- `src/main/java/**`
- `src/main/resources/**`

---

## 5. 测试执行记录

### 5.1 变更文件（`git diff main --name-status` 摘要）

本次 PR 新增（`src/main/java/org/example/timeloop/`）：

- `mechanism/DockingPlate.java`、`DockingPlateRegistry.java`、`Door.java`、`ExitTerminal.java`
- `mechanism/event/EventDispatcher.java`、`GameEvent.java`、`GameObserver.java`
- `level/model/EntitySpawnInfo.java`、`Level01Footsteps.java`、`LevelData.java`、`PathNode.java`、`TileType.java`、`Vector2D.java`

### 5.2 自动化测试（JUnit）

执行方式：`.\mvnw.cmd test`（项目根目录）

**测试结果摘要：**

```
[INFO] Running org.example.AppTest                              Tests run: 1,  Failures: 0, Errors: 0
[INFO] Running org.example.timeloop.core.FixedStepClockTest    Tests run: 11, Failures: 0, Errors: 0
[INFO] Running org.example.timeloop.replay.RoundClockTest      Tests run: 12, Failures: 0, Errors: 0
[INFO] Running org.example.timeloop.replay.TickContextTest     Tests run: 9,  Failures: 0, Errors: 0
[INFO] Results: Tests run: 33, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**关键发现：本 PR 未新增任何机关/事件/关卡数据的测试。** 上述 33 条全部来自既有 `core/`（时钟）与 `replay/`（R0/R1），**无一条覆盖 P1 的 `mechanism/**` 或 `level/model/**`**。

### 5.3 覆盖的核心功能验证点（指南 3.4 / 3.1）

本 PR 引入的 P1 机关逻辑（**均未由自动化测试覆盖**）：

- `DockingPlate`：`tryEnter`（UNOCCUPIED→OCCUPIED，派发 `PLATE_ENTERED`）、`tryExit`（同 actor 才释放，派发 `PLATE_EXITED`）、非空闲拒绝进入、非占用/异 actor 拒绝退出。
- `Door`：对所有 `requiredPlateIds` 均被占用时 LOCKED→UNLOCKED（派发 `DOOR_UNLOCKED`），任一块释放后回到 LOCKED；仅监听相关板事件；解锁只在状态变化沿派发一次。
- `ExitTerminal`：仅当关联门 `DOOR_UNLOCKED` 后 `interact` 才触发（派发 `EXIT_TRIGGERED`），`triggered` 后不再触发。
- `EventDispatcher`：同步派发、`register/unregister/unregisterAll/dispatch/dispatchAll/clear`。
- `level/model`：`Vector2D`、`PathNode`、`LevelData`、`Level01Footsteps`、`TileType`、`EntitySpawnInfo`（数据模型，含第一关路径/驻留配置）。

**测试隔离风险（供补充测试时处理）**：`EventDispatcher` 与 `DockingPlateRegistry` 均为**全局单例**，跨用例状态会泄漏；用例间需 `EventDispatcher.getInstance().clear()` 与 `DockingPlateRegistry.getInstance().clear()`，否则上一用例的已驻留板会继续影响 `Door` 判定。

### 5.4 手工验收（指南 3.8）

- 本 PR 为纯逻辑机关/数据，无 JavaFX 启动入口；`java -jar` 会报“没有主清单属性”，属预期，不阻塞。
- 说明：机关事件为同步、确定性（单例 + `CopyOnWriteArrayList`），具备可纯 JUnit 测试的前提，无需 JavaFX Toolkit。

---

## 6. PR 测试结论（指南 6.4）

| 项目 | 结论 |
|----------|------------|
| 结论 | **有条件通过** |
| 被测提交 | `9f91dea` |
| 阻塞/高风险缺陷 | 无 |
| 允许合并条件 | 代码可编译、未破坏既有 33 条测试；但 **P1 机关/事件逻辑无任何单元测试**，机制行为未被自动化证据覆盖。应在放行前补充机制单元测试（见下方“未覆盖风险”），或由开发 3 补其本单位测试后再完整放行。 |

**未覆盖风险与原因：**

1. **P1 机关行为未验证**：驻留板进入/离开、门解锁基于“全部板占用”、出口终端“门解锁后才可交互”这组因果链，没有任何测试；单例事件派发顺序也未被覆盖。
2. **单例全局状态**：`EventDispatcher`、`DockingPlateRegistry` 为进程级单例，测试隔离需显式 `clear()`；当前无测试来约束“上一轮机关状态不得影响本轮判定”。
3. **数据模型未测**：`level/model` 各字段/校验逻辑未测（交接文档已注明“数据模型类后续补充单元测试”）。

---

## 7. 关联证据

- 测试结论基于真实执行：`.\mvnw.cmd test` → `Tests run: 33, Failures: 0, Errors: 0, Skipped: 0`，源码 `src/test/java/org/example/timeloop/**` 下无任何 `mechanism`/`level` 测试类。
- 建议按指南 T1/T2 建立独立的“机关事件链”测试矩阵（覆盖上述 5.3 清单 + 单例清理），作为后续测试任务或本 PR 放行前补充。

---

*本任务卡依据《测试与质量保障-技术指南》第 0.1、1.3、2.2、3.1、3.4、6.4 节填写。*
