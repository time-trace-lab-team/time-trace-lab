# 测试综合报告：PR #6、#7、#8

> 测试周期：2026-09-08 ~ 2026-09-09  
> 测试人员：王健琨  
> 对应文档：《测试与质量保障-技术指南》


## 1. 整体概览

| PR   | 标题                              | 测试日期   | 测试结果 |
| ---- | --------------------------------- | ---------- | -------- |
| #6   | C0 固定步长时钟与 Canvas 边界骨架 | 2026-09-08 | ✅ 通过   |
| #7   | feat(replay): r0 工程与契约门禁   | 2026-09-09 | ✅ 通过   |
| #8   | 第一关地图及部分机关设置          | 2026-09-09 | ✅ 通过   |


## 2. PR #6 测试记录

### 2.1 基本信息

| 项目        | 内容                                 |
| ----------- | ------------------------------------ |
| 任务编号    | TEST-PR6                             |
| 对应 T 模块 | T0（环境基线）+ T1（核心灰盒）       |
| 被测 PR     | #6 C0 固定步长时钟与 Canvas 边界骨架 |
| 被测分支    | `pr-6`                               |
| 测试日期    | 2026-09-08                           |
| 测试人员    | 王健琨                               |

### 2.2 环境记录

| 项目                          | 记录                        |
| ----------------------------- | --------------------------- |
| Windows 版本                  | Windows 11                  |
| JDK 供应商与完整版本          | Eclipse Adoptium 17.0.12    |
| Maven/Wrapper 版本            | 3.9.11                      |
| JavaFX 版本                   | 17.0.20                     |
| 测试命令                      | `mvnw.cmd test`             |
| 测试总数 / 通过 / 失败 / 跳过 | 8 / 8 / 0 / 0               |
| 手工测试范围                  | 代码审查 + 时钟功能验证     |
| 已知限制                      | PR 无可运行 GUI，属骨架状态 |

### 2.3 新增文件

- `src/main/java/org/example/timeloop/core/FixedStepClock.java`
- `src/main/java/org/example/timeloop/core/GamePhase.java`
- `src/main/java/org/example/timeloop/render/CanvasAdapter.java`
- `src/main/java/org/example/timeloop/render/RenderLayer.java`
- `src/test/java/org/example/timeloop/core/FixedStepClockTest.java`

### 2.4 自动化测试结果

```text
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

| 测试类                           | 用例数 | 结果       |
| :------------------------------- | :----- | :--------- |
| AppTest（基线）                  | 1      | ✅          |
| FixedStepClockTest（PR #6 新增） | 7      | ✅          |
| **总计**                         | **8**  | **✅ 全绿** |

### 2.5 功能验证

| 验证项                                         | 结果 |
| :--------------------------------------------- | :--- |
| 首帧记录基线但不推进 tick                      | ✅    |
| 60fps 下每帧恰好推进 1 步                      | ✅    |
| 120fps 下总 tick 数与 60fps 一致（跨帧率稳定） | ✅    |
| 暂停阶段不推进 tick                            | ✅    |
| 超大时间增量不爆炸（MAX_STEPS 保护）           | ✅    |
| 插值 alpha 在 [0,1) 范围内                     | ✅    |
| 重置功能清空所有状态                           | ✅    |

### 2.6 手工验收

**执行方式**：`java -jar target/java-project-1.0-SNAPSHOT.jar`

**实际结果**：提示 `没有主清单属性`，无可运行 GUI。
**结论**：PR #6 为纯逻辑骨架（时钟 + Canvas 适配器），未包含 JavaFX 启动入口，属于预期状态，不阻塞合并。

### 2.7 PR 测试结论

| 项目            | 结论                 |
| :-------------- | :------------------- |
| 结论            | ✅ 通过               |
| 阻塞/高风险缺陷 | 无                   |
| 允许合并条件    | 无阻塞缺陷，建议合并 |

## 3. PR #7 测试记录

### 3.1 基本信息

| 项目        | 内容                               |
| :---------- | :--------------------------------- |
| 任务编号    | TEST-PR7                           |
| 对应 T 模块 | T0（环境基线）+ T1（核心灰盒）     |
| 被测 PR     | #7 feat(replay): r0 工程与契约门禁 |
| 被测分支    | `pr-7`                             |
| 测试日期    | 2026-09-09                         |
| 测试人员    | 王健琨                             |

### 3.2 环境记录

| 项目                          | 记录                                |
| :---------------------------- | :---------------------------------- |
| Windows 版本                  | Windows 11                          |
| JDK 供应商与完整版本          | Eclipse Adoptium 17.0.12            |
| Maven/Wrapper 版本            | 3.9.11                              |
| JavaFX 版本                   | 17.0.20                             |
| 测试命令                      | `mvnw.cmd test`                     |
| 测试总数 / 通过 / 失败 / 跳过 | 10 / 10 / 0 / 0                     |
| 手工测试范围                  | 代码审查 + TickContext 功能验证     |
| 已知限制                      | PR 无可运行 GUI，属纯逻辑契约（R0） |

### 3.3 新增文件

- `src/main/java/org/example/timeloop/replay/TickContext.java`
- `src/test/java/org/example/timeloop/replay/TickContextTest.java`

### 3.4 自动化测试结果

text

```
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```



| 测试类                        | 用例数 | 结果       |
| :---------------------------- | :----- | :--------- |
| AppTest（基线）               | 1      | ✅          |
| TickContextTest（PR #7 新增） | 9      | ✅          |
| **总计**                      | **10** | **✅ 全绿** |

### 3.5 功能验证（代码审查）

| 验证项                                               | 结果 |
| :--------------------------------------------------- | :--- |
| 使用 `record` 声明，字段自动为 `final`               | ✅    |
| 无 `setter` 方法                                     | ✅    |
| `durationTicks ≥ 1` 校验                             | ✅    |
| `maxRounds ≥ 1` 校验                                 | ✅    |
| `currentRound ∈ [1, maxRounds]` 校验                 | ✅    |
| `roundTick ∈ [0, durationTicks-1]` 校验              | ✅    |
| `isLastTick()` 返回 `roundTick == durationTicks - 1` | ✅    |
| `isLastTick()` 纯只读，无副作用                      | ✅    |
| Javadoc 标注"候选 v1 / 待团队确认"                   | ✅    |
| 仅修改 `replay/` 包，未触碰红线                      | ✅    |

### 3.6 手工验收

**执行方式**：`java -jar target/java-project-1.0-SNAPSHOT.jar`

**实际结果**：提示 `没有主清单属性`，无可运行 GUI。
**结论**：PR #7 为纯逻辑契约（R0），预期无 GUI，不阻塞合并。

### 3.7 PR 测试结论

| 项目            | 结论                 |
| :-------------- | :------------------- |
| 结论            | ✅ 通过               |
| 阻塞/高风险缺陷 | 无                   |
| 允许合并条件    | 无阻塞缺陷，建议合并 |

## 4. PR #8 测试记录

### 4.1 基本信息

| 项目         | 内容                        |
| :----------- | :-------------------------- |
| 任务编号     | TEST-PR8                    |
| 对应 T 模块  | T1（核心灰盒）              |
| 被测 PR      | #8 第一关地图及部分机关设置 |
| 被测分支     | `pr-8`                      |
| 被测提交哈希 | `a561abc`                   |
| 测试日期     | 2026-09-09                  |
| 测试人员     | 王健琨                      |

### 4.2 环境记录

| 项目                          | 记录                            |
| :---------------------------- | :------------------------------ |
| Windows 版本                  | Windows 11                      |
| JDK 供应商与完整版本          | Eclipse Adoptium 17.0.12        |
| Maven/Wrapper 版本            | 3.9.11                          |
| JavaFX 版本                   | 17.0.20                         |
| 测试命令                      | `mvnw.cmd test`                 |
| 测试总数 / 通过 / 失败 / 跳过 | 1 / 1 / 0 / 0                   |
| 手工测试范围                  | 代码审查 + 数据模型验证         |
| 已知限制                      | PR 未新增单元测试，无可运行 GUI |

### 4.3 新增文件

- `src/main/java/org/example/timeloop/level/model/Vector2D.java`
- `src/main/java/org/example/timeloop/level/model/TileType.java`
- `src/main/java/org/example/timeloop/level/model/LevelData.java`
- `src/main/java/org/example/timeloop/level/model/PathNode.java`
- `src/main/java/org/example/timeloop/level/model/EntitySpawnInfo.java`
- `src/main/java/org/example/timeloop/level/model/Level01Footsteps.java`

### 4.4 自动化测试结果

text

```
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```



| 测试类          | 用例数 | 结果 |
| :-------------- | :----- | :--- |
| AppTest（基线） | 1      | ✅    |
| PR #8 新增测试  | 0      | 无   |

**说明**：PR #8 未新增单元测试，仅验证项目基线通过。

### 4.5 功能验证（代码审查）

| 验证项                                             | 结果 |
| :------------------------------------------------- | :--- |
| Vector2D 使用 `record`，字段自动为 `final`         | ✅    |
| Vector2D 提供 `add()`、`scale()` 方法，无 `setter` | ✅    |
| TileType 枚举包含 `WALL` / `FLOOR` / `SPAWN_POINT` | ✅    |
| LevelData 字段全部 `final`，无 `setter`            | ✅    |
| LevelData 提供完整 `getter`                        | ✅    |
| 未触碰 `pom.xml` / `core/` / `render/` 红线        | ✅    |
| 无 JavaFX 图形代码                                 | ✅    |

### 4.6 测试局限性

- 未为数据模型编写单元测试
- 关卡数据（`Level01Footsteps`）的硬编码正确性需集成测试验证
- 地图视觉与路径连通性问题需等待 GUI 集成后手工验证

### 4.7 PR 测试结论

| 项目            | 结论                 |
| :-------------- | :------------------- |
| 结论            | ✅ 通过               |
| 阻塞/高风险缺陷 | 无                   |
| 允许合并条件    | 无阻塞缺陷，建议合并 |

## 5. 综合结论

| PR   | 测试结果 | 可合并 |
| :--- | :------- | :----- |
| #6   | ✅ 通过   | ✅ 是   |
| #7   | ✅ 通过   | ✅ 是   |
| #8   | ✅ 通过   | ✅ 是   |

**阻塞/高风险缺陷**：无

**总体建议**：三个 PR 均已通过测试，无阻塞缺陷，建议全部合并。

## 6. 关联证据

- 各 PR 测试报告已分别发布至对应 PR 评论区
- 无缺陷登记

*本报告依据《测试与质量保障-技术指南》第 0.1、1.3、2.2、3.3、5.1、6.4 节整理归档。*