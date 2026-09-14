# 第一关 MVP JavaFX 集成装配 · 交付说明

> 依据：`README.md`、`第一关MVP-集成装配技术指南.md`
> 分支：`codex/mvp1-integration`（基于 `origin/develop` `b76a8c7`）
> 基线证据：`.\mvnw.cmd test` → **Tests run: 242, Failures: 0, Errors: 0, BUILD SUCCESS**

## 一、本 PR 做了什么（app/ 集成装配）

| 文件 | 作用 |
| --- | --- |
| `app/Level01Assembly.java` | **核心装配**（纯 Java，无 JavaFX）：加载关卡 → 几何/autoDock/运动图 → 玩家与机关 → 每刻驱动（输入/autoDock/巡行或驻留/录制/事件/时钟）→ `renderViews()` → HUD 上下文 → `cleanup()` |
| `app/PathGraphBridge.java` | 关卡数据 `level.model.PathNode` → 运动图 `core.path.OrthogonalPathGraph` 的唯一桥接（集成层换算） |
| `app/InputAccumulator.java` | JavaFX 键 → `InputIntent` 的纯 Java 累加器：边沿/按住区分、失焦释放、方向边沿有序 |
| `app/TimeTraceLabApplication.java` | 窗口接线：`AnimationTimer` + `FixedStepLoop` → `assembly.tick(...)`；`assembly.renderViews()` → `CanvasAdapter` + 4 个图层；`SharedHud`；键映射；失焦 `releaseAll`；`stop()` 清理 |
| 测试 | `Level01AssemblyTest`（2 条，无头）：装配可构造/开始/逐刻推进/出渲染视图；HUD 上下文反映冻结参数 |

**接线要点**：`FixedStepLoop` 的 `TickUpdatePort` 每逻辑刻取走 `InputAccumulator.drain(roundTick)` 并交给 `assembly.tick(...)`；渲染每帧读 `assembly.renderViews()`；HUD 每帧 `render(assembly.hudContext())`。渲染只读，不回写玩法状态。

## 二、修掉的一个真实缺陷（装配时发现）

`ReplayPort.currentPlayerFrame()` 以**推进后的** `roundTick` 索引缓冲，而该刻尚未写入 → 越界 1（`IndexOutOfBounds: 180 / 时间线长度 180`）。
**处理**：装配在**推进时钟之前**缓存本刻帧（`lastFrame`/`lastTick`），`renderViews()` 使用缓存；残影轨迹按 `echo.durationTicks()-1` 钳制。属 app/ 装配层规避，未改 `replay/**`。

## 三、验收

### 自动化（已跑通）
```powershell
.\mvnw.cmd test      # 242 tests, Failures 0, BUILD SUCCESS
```

### JavaFX 窗口（需目标机手工验收，本环境不启动 GUI）
```powershell
.\mvnw.cmd javafx:run
```
逐项核对（装配指南 §六）：

1. 窗口出现第一关地图：墙、地面、两块驻留板、门、出口、玩家、HUD（读秒 + 轮数）。
2. 玩家松键仍恒速巡行、路口转向；进入驻留板自动停驻；**新方向键**离开。
3. 第一轮读秒归零自动切轮；残影下一轮从 tick 0 复现。
4. 双驻留板同时激活 → 门开 → `E` 交互出口通关。
5. 全量测试仍全绿。

## 四、已知限制 / 待验收确认

1. **窗口画面未在本机验证**：本环境不启动 JavaFX（无显示、且会阻塞），画面正确性以 `javafx:run` 目标机为准；无头测试只证明装配链路与数据。
2. **残影占板**：回放按 `EchoState.eventsAt(tick)` 把残影的 `DOCK_ENTERED/LEFT` 镜像到机关；第一关 MVP 的「E1 占左板」依赖第一轮确实在左板驻留并留下该事件。
3. **驻留吸附**：进入 autoDock 时当前策略在进入位置冻结（不强制吸附到停驻中心）；若画面显示偏移，属 `entity/C3DockController` 的后续增强（开发一）。
4. **门/射线/共振的完整状态机**不在本卡范围（第一关只需板—门—出口）。
5. `DOCK_LEFT` 仍记在出界刻（`tryLeave` 要求位置已在区域外），与 README「离开即释放」的偏差待 PM 裁决。

---

*本 PR 只改 `app/**` 与其测试；未修改 `core/**`、`entity/**`、`render/**`、`replay/**`、`snapshot/**`、`mechanism/**`、`level/**`、`ui/**`、`pom.xml`。*
