# TEST-PR25-任务卡

## 1. 基本信息

| 项目 | 内容 |
|------|----|
| 任务编号 | TEST-PR25 |
| 对应 T 模块 | T3 前置（残影整轮寿命） |
| 被测 PR | #25 fix(content): 修复 EchoLifetimeManager 生命周期管理 |
| 被测分支 | `pr-25` |
| 被测提交哈希 | `4d5d09e` |
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
| 测试总数 / 通过 / 失败 / 跳过 | 54 / 54 / 0 / 0 |
| 手工测试范围 | 无 GUI 变更 |

---

## 3. 允许 / 禁止修改路径

- 允许：`src/test/java/org/example/timeloop/**`、`docs/testing/**`
- 禁止：`pom.xml`、`src/main/java/**`、`src/main/resources/**`

---

## 4. 测试执行记录

### 4.1 变更文件（`git show --stat 4d5d09e`）

```
replay/EchoLifetimeManager.java     | 46 +++++---------
replay/EchoLifetimeManagerTest.java | 75 +++++++++-------------
2 files changed, 46 insertions(+), 75 deletions(-)
```

即：修复 `EchoLifetimeManager` 生命周期管理，并**同步更新了对应单元测试**。

### 4.2 自动化测试

```
[INFO] Running org.example.AppTest                              Tests run: 1,  Failures: 0
[INFO] Running org.example.timeloop.core.FixedStepClockTest    Tests run: 11, Failures: 0
[INFO] Running org.example.timeloop.core.FixedStepLoopTest     Tests run: 6,  Failures: 0
[INFO] Running org.example.timeloop.core.GamePhaseTest         Tests run: 1,  Failures: 0
[INFO] Running org.example.timeloop.replay.EchoLifetimeManagerTest Tests run: 4, Failures: 0
[INFO] Running org.example.timeloop.replay.EchoLifetimeTest    Tests run: 9,  Failures: 0
[INFO] Running org.example.timeloop.replay.RoundClockTest      Tests run: 13, Failures: 0
[INFO] Running org.example.timeloop.replay.TickContextTest     Tests run: 9,  Failures: 0
[INFO] Results: Tests run: 54, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### 4.3 覆盖情况（指南 3.5 寿命公式）

- `EchoLifetimeManagerTest`（4）与 `EchoLifetimeTest`（9）随本 PR 更新并通过，**本 PR 的修复点有对应断言**。
- 覆盖的寿命语义（README 统一公式）：`age = r - s`、`active = 1 ≤ age ≤ L`、`remainingRounds = L - age + 1`、`lifeProgress = clamp(((age-1)*D + roundTick) / (L*D), 0, 1)`、`bodyAlpha = 0.82 - 0.32 * lifeProgress`。
- 建议后续补测：`L=1/2` 边界、tick 0 与 `D-1`、第 3 轮 E1+E2 / 第 4 轮 E2+E3、淘汰仅在轮次边界发生。

---

## 5. 手工验收

- 本 PR 为纯逻辑（残影寿命），无 GUI 变更，无需窗口验收。

---

## 6. PR 测试结论（指南 6.4）

| 项目 | 结论 |
|----------|------------|
| 结论 | **通过** |
| 被测提交 | `4d5d09e` |
| 阻塞/高风险缺陷 | 无 |
| 允许合并条件 | 54 条测试全过、无回归；修复点有对应测试更新。 |

**未覆盖风险**：寿命公式的 `L=1/2` 与轮次边界组合建议按 T3 矩阵继续补充（观察项，非本 PR 阻塞）。

---

*本任务卡依据《测试与质量保障-技术指南》第 0.1、1.3、2.2、3.5、6.4 节填写。*
