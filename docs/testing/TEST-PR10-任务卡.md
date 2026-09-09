# 测试任务卡：PR #10

> 对应文档：《测试与质量保障-技术指南》0.1、1.3、2.2

## 1. 基本信息

| 项目 | 内容 |
|------|------|
| 任务编号 | TEST-PR10 |
| 对应 T 模块 | T1（核心灰盒） |
| 被测 PR | #10 c1框架与JavaFx窗口 |
| 被测分支 | `pr-10` |
| 被测提交哈希 | `df1a42b` |
| 测试日期 | 2026-09-09 |
| 测试人员 | 王健琨 |

## 2. 环境记录

| 项目 | 记录 |
|------|------|
| Windows 版本 | Windows 11 |
| JDK 供应商与完整版本 | Eclipse Adoptium 17.0.12 |
| Maven/Wrapper 版本 | 3.9.11 |
| JavaFX 版本 | 17.0.20 |
| 测试命令 | `mvnw.cmd test`、`mvnw.cmd javafx:run` |
| 测试总数 / 通过 / 失败 / 跳过 | 12 / 12 / 0 / 0 |
| 手工测试范围 | JavaFX 窗口启动 + 功能集成验证 |

## 3. 新增文件

- `src/main/java/org/example/timeloop/app/TimeTraceLabApplication.java`
- `docs/decisions/c1-启动与世界坐标约定.md`

## 4. 功能验证

| 验证项 | 结果 |
|--------|------|
| JavaFX 启动类存在，包含 main 和 start 方法 | ✅ |
| 窗口正常弹出，无报错 | ✅ |
| 集成 FixedStepClock / CanvasAdapter / RenderLayer | ✅ |
| 未破坏已有测试（12 个全绿） | ✅ |

## 5. 测试局限性

- JAR 包缺少主清单属性，需在 pom.xml 中配置 maven-jar-plugin
- 窗口内容尚待后续 PR 完善

## 6. PR 测试结论

| 项目 | 结论 |
|------|------|
| 结论 | ✅ 通过 |
| 阻塞/高风险缺陷 | 无 |
| 允许合并条件 | 无阻塞缺陷，建议合并 |

## 7. 关联证据

- 测试报告已发布至 PR #10 评论区
- 无缺陷登记

*本任务卡依据《测试与质量保障-技术指南》第 0.1、1.3、2.2、3.3、5.1、6.4 节填写。*