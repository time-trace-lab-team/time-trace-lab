# TEST-PR28-任务卡

## 1. 基本信息

| 项目 | 内容 |
|------|----|
| 任务编号 | TEST-PR28 |
| 对应 T 模块 | T1（核心灰盒：路口/路径决策） |
| 被测 PR | #28 fix(core): reject ambiguous C2 patrol junctions |
| 被测分支 | `pr-28` |
| 被测提交哈希 | `4bbd248` |
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
| 测试总数 / 通过 / 失败 / 跳过 | 94 / 94 / 0 / 0 |
| 手工测试范围 | 无 GUI 变更 |

---

## 3. 允许 / 禁止修改路径

- 允许：`src/test/java/org/example/timeloop/**`、`docs/testing/**`
- 禁止：`pom.xml`、`src/main/java/**`、`src/main/resources/**`

---

## 4. 测试执行记录

### 4.1 变更文件（`git show --stat 4bbd248`）

```
core/path/PathExitSelector.java     | 11 ++++++++
core/path/PathExitSelectorTest.java | 30 ++++++++++++++++++++++
2 files changed, 41 insertions(+)
```

即：让 C2 巡行路径在遇到**语义歧义的路口**时拒绝决策（而不是静默选一条），并**新增对应单元测试**。

### 4.2 自动化测试

```
[INFO] Running org.example.timeloop.core.FixedStepClockTest              Tests run: 11, Failures: 0
[INFO] Running org.example.timeloop.core.FixedStepLoopTest               Tests run: 10, Failures: 0
[INFO] Running org.example.timeloop.core.GamePhaseTest                   Tests run: 1,  Failures: 0
[INFO] Running org.example.timeloop.core.OrderedTickUpdatePortTest       Tests run: 2,  Failures: 0
[INFO] Running org.example.timeloop.core.PlayerKinematicsTest            Tests run: 3,  Failures: 0
[INFO] Running org.example.timeloop.core.TickStepResultTest              Tests run: 1,  Failures: 0
[INFO] Running org.example.timeloop.core.path.OrthogonalPathGraphTest    Tests run: N,  Failures: 0
[INFO] Running org.example.timeloop.core.path.PathExitSelectorTest       Tests run: M,  Failures: 0
[INFO] Running org.example.timeloop.core.path.PathNodeTest               Tests run: 3,  Failures: 0
[INFO] Running org.example.timeloop.entity.PatrolConfigTest              Tests run: 2,  Failures: 0
[INFO] Running org.example.timeloop.entity.PatrolControllerNodeBehaviorTest Tests run: 8, Failures: 0
[INFO] Running org.example.timeloop.entity.PatrolControllerStraightLineTest Tests run: 3, Failures: 0
[INFO] Running org.example.timeloop.replay.PlayerFrameTest               Tests run: 5,  Failures: 0
[INFO] Running org.example.timeloop.replay.RoundClockTest                Tests run: 13, Failures: 0
[INFO] Running org.example.timeloop.replay.TickContextTest               Tests run: 9,  Failures: 0
[INFO] Running org.example.timeloop.replay.TimelineRecordingTest         Tests run: 14, Failures: 0
[INFO] Results: Tests run: 94, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

> 注：`PathExitSelectorTest` 为本 PR 新增，随修复一并提交；上表 `N/M` 为未逐条抓取的用例数，以 Surefire 汇总 94 条为准。

### 4.3 覆盖情况（指南 3.3 出口优先级）

- 本 PR 的修复点**有对应测试**（`PathExitSelectorTest` + 巡逻控制器相关测试）。
- 关联覆盖：`PathNodeTest`、`PatrolControllerNodeBehaviorTest`、`PatrolControllerStraightLineTest`、`OrthogonalPathGraphTest`。
- 建议关注：歧义路口的判定是否与 README“有效队列 → 直行 → 唯一侧路 → defaultExit → 死路返回”一致，且拒绝决策时**不会产生可利用的静止状态**。

---

## 5. 手工验收

- 本 PR 为纯逻辑（路径决策），无 GUI 变更。

---

## 6. PR 测试结论（指南 6.4）

| 项目 | 结论 |
|----------|------------|
| 结论 | **通过** |
| 被测提交 | `4bbd248` |
| 阻塞/高风险缺陷 | 无 |
| 允许合并条件 | 94 条测试全过、无回归；修复带新测试。 |

**未覆盖风险**：歧义路口“拒绝后角色不停在路口”的行为建议在灰盒地图上做一次手工确认（观察项）。

---

*本任务卡依据《测试与质量保障-技术指南》第 0.1、1.3、2.2、3.3、6.4 节填写。*
