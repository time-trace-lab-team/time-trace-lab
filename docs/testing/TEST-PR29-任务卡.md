# TEST-PR29-任务卡

## 1. 基本信息

| 项目 | 内容 |
|------|----|
| 任务编号 | TEST-PR29 |
| 对应 T 模块 | T1/T4 前置（关卡几何契约） |
| 被测 PR | #29 feat(level): 完成不可变 LevelGeometry 接口 |
| 被测分支 | `pr-29` |
| 被测提交哈希 | `abcfe23` |
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

### 4.1 变更文件（`git show --stat abcfe23`）

```
level/{model => }/Level01Footsteps.java | 42 +++----
level/Level03Footsteps.java             | 14 ++--
level/LevelGeometry.java                | 38 --------
level/LevelGeometryImpl.java            | 71 ++++---------
level/model/DoorInfo.java               | 26 ++++++
level/model/LevelData.java              | 43 +++-----
level/model/PathNode.java               | 32 +++---
7 files changed, 98 insertions(+), 168 deletions(-)
```

即：完成**不可变 `LevelGeometry`** 接口（`PathNode`/`LevelData`/`DoorInfo` 调整，`Level01Footsteps` 由 `level/model` 移到 `level`，`LevelGeometryImpl` 收敛）。

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

- 既有 70 条全部通过，**无回归**；`Level01Footsteps` 的包移动未破坏编译与测试。
- ⚠️ **本 PR 的 `LevelGeometry` / `LevelGeometryImpl` / `LevelData` / `PathNode` / `DoorInfo` 无专属测试**；本分支 `src/test` 下没有 level/geometry 相关测试类。不可变契约（集合不可修改、ID 稳定、引用合法）未被断言。

---

## 5. 手工验收

- 本 PR 含 `app/` 入口（随分支带入）：有 GUI 的 PR 按交接文档用 `.\mvnw.cmd javafx:run` 确认窗口；**本环境未启动 GUI**，该窗口门禁为目标机手工项。

---

## 6. PR 测试结论（指南 6.4）

| 项目 | 结论 |
|----------|------------|
| 结论 | **有条件通过** |
| 被测提交 | `abcfe23` |
| 阻塞/高风险缺陷 | 无 |
| 允许合并条件 | 可编译、70 条测试全过；但 `LevelGeometry` 不可变契约无测试，建议补充“返回集合不可修改、路径/门引用合法、字段单位与 ID 稳定”单元测试（或纳入 T0/T4 schema 校验）后再完整放行。 |

**未覆盖风险与原因**：

1. **不可变性未被断言**：接口声明为不可变，但无测试验证返回集合/对象真的不可修改。
2. **数据模型仍缺测试**：与交接文档“数据模型类后续补充单元测试”一致（PR #8/#24 同源问题）。
3. **重构范围较大**（包移动 + 删除 `LevelGeometry.java`）：已由编译与既有测试证明无回归，但建议后续补一次关卡数据的加载/引用合法性校验。

---

*本任务卡依据《测试与质量保障-技术指南》第 0.1、1.3、2.2、6.4 节填写。*
