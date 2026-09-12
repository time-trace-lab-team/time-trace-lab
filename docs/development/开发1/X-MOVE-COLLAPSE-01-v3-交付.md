# X-MOVE-COLLAPSE-01-DEV1-v3 · 交付与接线状态

> 分支：`origin/codex/dev1-move-v2`（已推送）
> 开发一代码交付提交：`c9b11f4`
> 当前合并基线：`origin/develop @ b76c14c`
> 状态：开发一代码、测试数自证、渲染矩阵已完成；等待推送、PM app 接线和目标机画面取证。

## 任务链进度

| v3 项 | 状态 | 结果 |
| --- | --- | --- |
| §一 追上 develop 并推送 | ✅ | 已从 `ed5bcac` 合并到 `b76c14c`；代码交付 `c9b11f4` 已推到 `origin/codex/dev1-move-v2`。 |
| §二 数字自证 | ✅ | `ed5bcac clean test = 288`，`1675c87 clean test = 300`；旧报告的 303 是未清理输出目录时多计的 3 条旧编译测试，不能作为交付数字。 |
| §四 状态 × 相位矩阵 | ✅ | 一条测试内部穷举 `IDLE` / `CRUISING` / `SLOWED` / `DOCKED` × phased true/false 共 8 组；每组断言形状、方向刻痕、减速反馈和相位环的确定投影。 |
| §三 PM R-2 接线 | ⏳ PM | 推送后，PM 将关卡路径节点投影为 `PathNodeMarker` 并注册 `PathNodeHintLayer`；开发一只在 API 不匹配时修 `render/**`。 |
| §五 四张画面证据 | ⏳ 协作 | PM 接线后由有显示器的目标机执行命令并保存截图。 |

## 测试类清单与数值

| 开发一新增测试类 | 条数 | 说明 |
| --- | ---: | --- |
| `PatrolControllerSpeedModifierTest` | 5 | 0.5 倍速、恢复、节点转向、复位、静止。 |
| `PlayerVisualProjectionTest` | 5 | 原有四态投影 4 条，加一条内部覆盖 8 组的状态×相位矩阵。 |
| `PathNodeHintLayerTest` | 3 | 1 格/2 格阈值、非法输入、静态节点数据校验。 |
| 开发一新增合计 | **13** | 5 + 5 + 3。 |

`./mvnw.cmd -o clean test`：**331 tests / 0 failures / 0 errors / 0 skipped**；52 个测试类；最长 `Level01AssemblyLevel01FlowTest` 为 0.30 秒。

## 测试数差额说明

1. 初始 PM 配对头 `ed5bcac` 的隔离 `clean test` 实测为 288。
2. 历史 v2 交付提交 `1675c87` 的隔离 `clean test` 实测为 300，正好增加 12 条（5 + 4 + 3）。
3. 因此此前“303”不是源码多出三条，而是在未 `clean` 的工作目录中让 Surefire 发现了旧编译产物。v3 交付只使用上述隔离 `clean test` 和当前分支的 `clean test` 数字。
4. 当前 `develop` 已继续合入 PR #57/#58 等变更；同一清理基线上的当前分支为 331，其中开发一新增 13 条，故最新 `develop` 对应 318 条。

## 目标机画面取证

PM 注册 `PathNodeHintLayer` 后，在项目根目录运行：

```powershell
.\mvnw.cmd -o javafx:run
```

预期保存四张图：

1. `IDLE`：圆角方形，无方向刻痕；
2. `CRUISING`：圆形，带朝向刻痕；
3. `SLOWED`：圆形、朝向刻痕、描边和反向短拖尾；
4. 同一节点在玩家距离大于 2 格、1–2 格、至多 1 格时分别为隐藏、暗 8px 菱形、亮 8px 菱形。

自动化测试不等同于 JavaFX 画面证据；截图由 PM/测试在可显示的目标机登记到 `FINAL-ACCEPT`。
