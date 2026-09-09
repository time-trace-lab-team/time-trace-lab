# 测试任务卡：PR #8

> 对应文档：《测试与质量保障-技术指南》0.1、1.3、2.2

## 1. 基本信息

| 项目 | 内容 |
|------|------|
| 任务编号 | TEST-PR8 |
| 对应 T 模块 | T1（核心灰盒） |
| 被测 PR | #8 第一关地图及部分机关设置 |
| 被测分支 | `pr-8` |
| 被测提交哈希 | `a561abc` |
| 测试日期 | 2026-09-09 |
| 测试人员 | 王健琨 |

## 2. 环境记录

| 项目 | 记录 |
|------|------|
| Windows 版本 | Windows 11 |
| JDK 供应商与完整版本 | Eclipse Adoptium 17.0.12 |
| Maven/Wrapper 版本 | 3.9.11 |
| JavaFX 版本 | 17.0.20 |
| 测试命令 | `mvnw.cmd test` |
| 测试总数 / 通过 / 失败 / 跳过 | 1 / 1 / 0 / 0 |
| 手工测试范围 | 代码审查 + 数据模型验证 |
| 已知限制 | PR 未新增单元测试，无可运行 GUI |

## 3. 新增文件

- `src/main/java/org/example/timeloop/level/model/Vector2D.java`
- `src/main/java/org/example/timeloop/level/model/TileType.java`
- `src/main/java/org/example/timeloop/level/model/LevelData.java`
- `src/main/java/org/example/timeloop/level/model/PathNode.java`
- `src/main/java/org/example/timeloop/level/model/EntitySpawnInfo.java`
- `src/main/java/org/example/timeloop/level/model/Level01Footsteps.java`

## 4. 功能验证

| 验证项 | 结果 |
|--------|------|
| Vector2D 使用 `record`，字段自动为 `final` | ✅ |
| Vector2D 提供 `add()`、`scale()` 方法，无 `setter` | ✅ |
| TileType 枚举包含 `WALL` / `FLOOR` / `SPAWN_POINT` | ✅ |
| LevelData 字段全部 `final`，无 `setter` | ✅ |
| LevelData 提供完整 `getter` | ✅ |
| 未触碰 `pom.xml` / `core/` / `render/` 红线 | ✅ |
| 无 JavaFX 图形代码 | ✅ |

## 5. 测试局限性

- 未为数据模型编写单元测试
- 关卡数据（`Level01Footsteps`）的硬编码正确性需集成测试验证
- 地图视觉与路径连通性问题需等待 GUI 集成后手工验证

## 6. PR 测试结论

| 项目 | 结论 |
|------|------|
| 结论 | ✅ 通过 |
| 阻塞/高风险缺陷 | 无 |
| 允许合并条件 | 无阻塞缺陷，建议合并 |

## 7. 关联证据

- 测试报告已发布至 PR #8 评论区
- 无缺陷登记

---

*本任务卡依据《测试与质量保障-技术指南》第 0.1、1.3、2.2、3.3、5.1、6.4 节填写。*