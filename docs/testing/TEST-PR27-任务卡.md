# TEST-PR27-任务卡

## 1. 基本信息

| 项目 | 内容 |
|------|----|
| 任务编号 | TEST-PR27 |
| 对应 T 模块 | T2（单残影/轮次边界）/ T3 前置（机关快照） |
| 被测 PR | #27 feat(snapshot): implement MechanismSnapshot for round transition |
| 被测分支 | `pr-27` |
| 被测提交哈希 | `9ef5686` |
| 测试日期 | 2026-09-09 |
| 测试人员 | 成员 E |

---

## 2. 环境记录（指南 1.3）

| 项目 | 记录 |
| --- | --- |
| Windows 版本 | Windows 11 |
| JDK 供应商与完整版本 | Azul Zulu 17.0.16（OpenJDK 17.0.16 LTS） |
| Maven/Wrapper 版本 | 3.9.11 |
| JavaFX 版本 | 17.0.20 |
| 屏幕缩放与分辨率 | 100% / 1920×1080 |
| 音频设备/音量 | 默认（本 PR 无音频相关） |
| 测试命令 | `.\mvnw.cmd test` |
| 测试总数 / 通过 / 失败 / 跳过 | 70 / 70 / 0 / 0 |
| 手工测试范围 | 见 5.4（含 JavaFX 入口，未在本机启动） |

---

## 3. 允许 / 禁止修改路径

- 允许：`src/test/java/org/example/timeloop/**`、`docs/testing/**`
- 禁止：`pom.xml`、`src/main/java/**`、`src/main/resources/**`

---

## 4. 测试执行记录

### 4.1 变更文件（`git show --stat 9ef5686`）

```
mechanism/DockingPlate.java      | 39 ++++++++
mechanism/Door.java              | 16 ++++
mechanism/ExitTerminal.java      | 25 ++++++
snapshot/MechanismSnapshot.java  | 87 ++++++++++++++++++++++
4 files changed, 167 insertions(+)
```

即：为轮次切换新增 `MechanismSnapshot`，并给驻留板 / 门 / 出口终端补上快照/恢复所需的读写方法。

### 4.2 自动化测试

```
[INFO] Running org.example.timeloop.core.FixedStepClockTest         Tests run: 11, Failures: 0
[INFO] Running org.example.timeloop.core.FixedStepLoopTest          Tests run: 10, Failures: 0
[INFO] Running org.example.timeloop.core.GamePhaseTest              Tests run: 1,  Failures: 0
[INFO] Running org.example.timeloop.core.OrderedTickUpdatePortTest  Tests run: 2,  Failures: 0
[INFO] Running org.example.timeloop.core.PlayerKinematicsTest       Tests run: 3,  Failures: 0
[INFO] Running org.example.timeloop.core.TickStepResultTest         Tests run: 1,  Failures: 0
[INFO] Running org.example.timeloop.replay.PlayerFrameTest          Tests run: 5,  Failures: 0
[INFO] Running org.example.timeloop.replay.RoundClockTest           Tests run: 13, Failures: 0
[INFO] Running org.example.timeloop.replay.TickContextTest          Tests run: 9,  Failures: 0
[INFO] Running org.example.timeloop.replay.TimelineRecordingTest    Tests run: 14, Failures: 0
[INFO] Results: Tests run: 70, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### 4.3 覆盖情况

- 既有 70 条全部通过，**无回归**（含 R2 的 `PlayerFrameTest`、`TimelineRecordingTest` 等）。
- ⚠️ **本 PR 新增的 `snapshot/MechanismSnapshot` 没有专属测试**；本分支 `src/test` 下不存在 `MechanismSnapshotTest`，机关快照的“采集→恢复→幂等”未被断言。

---

## 5. 手工验收

- 本 PR 含 `app/` 入口（随分支带入）：有 GUI 的 PR 按交接文档用 `.\mvnw.cmd javafx:run` 确认窗口；**本环境未启动 GUI**，该窗口门禁为目标机手工项。

---

## 6. PR 测试结论（指南 6.4）

| 项目 | 结论 |
|----------|------------|
| 结论 | **有条件通过** |
| 被测提交 | `9ef5686` |
| 阻塞/高风险缺陷 | 无 |
| 允许合并条件 | 可编译、70 条既有测试全过；但 `MechanismSnapshot` 无测试，建议补充“快照采集/恢复/重复恢复幂等/淘汰后占用释放”单元测试后再完整放行。 |

**未覆盖风险与原因**：

1. `MechanismSnapshot` 的字段完整性与恢复语义无断言；轮次切换“恢复机关状态”依赖它，属高风险路径。
2. 与 PR #24 相同的单例状态隔离问题（`EventDispatcher` / `DockingPlateRegistry`）在本分支仍无测试约束。

---

*本任务卡依据《测试与质量保障-技术指南》第 0.1、1.3、2.2、3.5、6.4 节填写。*
