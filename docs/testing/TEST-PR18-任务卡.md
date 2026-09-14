# TEST-PR18-任务卡

## 1. 基本信息

| 项目 | 内容 |
|------|----|
| 任务编号 | TEST-PR18 |
| 对应 T 模块 | T1（核心灰盒：固定步长接入 + 统一阶段） |
| 被测 PR | #18 feat(core): 接入固定步长循环与统一游戏阶段 |
| 被测分支 | `pr-18` |
| 被测提交哈希 | `6484191` |
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
| 屏幕缩放与分辨率 | 100% / 1920×1080（默认记录） |
| 音频设备/音量 | 默认（本 PR 无音频相关） |
| 测试命令 | `.\mvnw.cmd test` |
| 测试总数 / 通过 / 失败 / 跳过 | 40 / 40 / 0 / 0 |
| 手工测试范围 | JavaFX 窗口（`javafx:run`），见 5.4 |

---

## 3. 本次任务允许修改的路径（指南 2.2）

- `src/test/java/org/example/timeloop/**`
- `docs/testing/**`（本文件）

---

## 4. 本次任务禁止修改的路径（红线）

- `pom.xml`
- `src/main/java/**`
- `src/main/resources/**`

---

## 5. 测试执行记录

### 5.1 变更文件摘要（`git diff main --name-status`）

本 PR（C1）主要新增/涉及：

- `src/main/java/org/example/timeloop/app/TimeTraceLabApplication.java`（JavaFX 应用入口）
- `src/main/java/org/example/timeloop/core/FixedStepClock.java`、`FixedStepLoop.java`、`GamePhase.java`、`TickUpdatePort.java`
- `src/test/java/org/example/timeloop/core/FixedStepLoopTest.java`、`GamePhaseTest.java`
- 关联 `replay/RoundClock/RoundPhase/AdvanceResult/TickContext` 及对应测试（随分支一并带入）
- `pom.xml`（JavaFX/启动相关，见 5.4）

### 5.2 自动化测试（JUnit）

执行方式：`.\mvnw.cmd test`（项目根目录）

**测试结果摘要：**

```
[INFO] Running org.example.AppTest                              Tests run: 1,  Failures: 0, Errors: 0
[INFO] Running org.example.timeloop.core.FixedStepClockTest    Tests run: 11, Failures: 0, Errors: 0
[INFO] Running org.example.timeloop.core.FixedStepLoopTest     Tests run: 6,  Failures: 0, Errors: 0
[INFO] Running org.example.timeloop.core.GamePhaseTest         Tests run: 1,  Failures: 0, Errors: 0
[INFO] Running org.example.timeloop.replay.RoundClockTest      Tests run: 12, Failures: 0, Errors: 0
[INFO] Running org.example.timeloop.replay.TickContextTest     Tests run: 9,  Failures: 0, Errors: 0
[INFO] Results: Tests run: 40, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### 5.3 覆盖的核心功能验证点（指南 3.3）

本 PR 核心 C1 逻辑，均由新增测试覆盖：

- **FixedStepLoopTest（6）**：
  - 首帧仅建立时间基线，不推进逻辑刻；
  - 一个渲染帧可派发多个逻辑刻；
  - 非 PLAYING 阶段不派发逻辑、不累计暂停时长；
  - 渲染帧率变化（30/60/120 FPS）不改变逻辑刻总数（同输入同 tick，64=60）；
  - 插值 alpha 从时钟转发且落在 [0,1)；
  - 空 `TickUpdatePort` 被拒绝。
- **GamePhaseTest（1）**：
  - `GamePhase` 恰好声明 10 个阶段且顺序正确；
  - 仅 `PLAYING.advancesLogic()` 为 `true`，其余冻结。

### 5.4 手工验收（指南 3.8 / 1.4）

- 本 PR 含 JavaFX 应用入口 `TimeTraceLabApplication` 与 `pom.xml` 变更；按交接文档，有 GUI 的 PR 用 `.\mvnw.cmd javafx:run` 确认窗口正常弹出。**本环境未启动 GUI**（避免阻塞/需显示器），该窗口门禁建议在目标机手工验证，归项目经理/集成验收项，不构成自动化失败或阻塞。
- 说明：`TimeTraceLabApplication` 是否为 JavaFX `Application` 且 `javafx:run` 可启动，未在本机执行；若目标机窗口无法弹出，请以该现象单独立缺陷。

---

## 6. PR 测试结论（指南 6.4）

| 项目 | 结论 |
|----------|------------|
| 结论 | **通过** |
| 被测提交 | `6484191` |
| 阻塞/高风险缺陷 | 无 |
| 允许合并条件 | 自动化测试 40 条全过、无回归；JavaFX 窗口（`javafx:run`）为目标机手工门禁项，待确认（不阻塞合并）。 |

**未覆盖风险与原因（观察项，非缺陷）：**

1. **JavaFX 窗口未实测**：`javafx:run` 未在本机运行（需显示/可能阻塞），窗口启动门禁建议目标机确认。
2. **两套阶段枚举并存**：`core/GamePhase`（本 PR 的“全项目唯一正式阶段”，10 阶段）与 `replay/RoundPhase`（R1 引入，同为 10 阶段）暂并存；`RoundClock` 仍使用 `RoundPhase`。建议团队（开发 2 + 项目经理）确认是否收敛到单一 `GamePhase`，避免阶段语义出现两处来源。
3. `FixedStepLoop` 依赖 `FixedStepClock` 的 `MAX_STEPS_PER_FRAME` 上限；严重掉帧的“宁可慢不欠时间债”行为已在 `FixedStepClockTest` 覆盖，未新增独立断言。

---

## 7. 关联证据

- 自动化测试基于真实执行：`.\mvnw.cmd test` → `Tests run: 40, Failures: 0, Errors: 0, Skipped: 0`。
- 本 PR 对 C1 逻辑提供了对应单元测试（`FixedStepLoopTest`、`GamePhaseTest`），无 PR #17 那种“机关逻辑无测试”的缺口。

---

*本任务卡依据《测试与质量保障-技术指南》第 0.1、1.3、2.2、3.3、6.4 节填写。*
