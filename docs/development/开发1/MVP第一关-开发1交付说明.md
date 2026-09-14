# 第一关 MVP · 开发 1 交付说明

> 任务卡：`MVP第一关-任务卡-开发1.md`
> 分支：`codex/dev1-c3`（基于 `develop`）
> 基线证据：`.\mvnw.cmd test` → **Tests run: 201, Failures: 0, Errors: 0, BUILD SUCCESS**

## W1 · 方向输入（core/）✅
- `core/input/LogicalKey.java`：逻辑键 `DIR_UP/DOWN/LEFT/RIGHT`、`INTERACT`(E)、`PHASE`(Space)；WASD 与方向键映射为同一逻辑键。
- `core/input/InputIntent.java`：不可变 `{tick, pressedThisTick, releasedThisTick, held, directionEdges}`；严格区分**边沿**与**按住**；`lastDirectionEdge()` 末位胜出；纯 Java，可脱离 JavaFX 单测。
- 单槽方向队列与“直行/90°、拒绝掉头、无效丢弃”由既有 C2 `PatrolController` + `PathExitSelector` 提供（已合入 develop）；本卡补的是**喂入端口**（`InputIntent`）。

## W2 · autoDock 停驻/离开（entity/）✅
- `entity/C3DockController.java` + `entity/C3DockDecision.java`：进入区域 → `FREEZE` + 发 `DOCK_ENTERED`；只有**进入后新出现且合法**的方向边沿才离开；离开发 `DOCK_LEFT`（带方向）。
- 持续按住的旧键不产生边沿 → **不会误离开**（`heldOldKeyDoesNotLeaveDock`）。
- E 缓冲 8 tick（README 要求 6–10）；`releaseOccupancy/reset` 发 `OCCUPANCY_RELEASED`。
- 策略**不拥有位置、不推进运动**，可与 C2 `PatrolController` 或集成层运动组合。

## W3 · 基础渲染层（render/）✅
- `render/WorldTransform.java`：世界↔Canvas 坐标变换（纯逻辑，**不初始化 JavaFX**，7 条单测）。
- `render/RenderPalette.java`：README §六 暗调色板 + 墙面侧向偏移。
- `render/RenderViews.java`：只读渲染视图（`Frame/Player/Mechanism/EchoTrail`），全部不可变。
- 具体图层（均实现 `RenderLayer`，只读绘制、不回写）：
  - `GroundWallLayer`：连续地面 + 可见墙面（顶面 + 侧向偏移阴影），**不画全屏格线**；
  - `MechanismLayer`：驻留板 / 门 / 出口（形状 + 明度区分，不只靠颜色）；
  - `PlayerLayer`：当前玩家 + 朝向刻痕 + 相位脚下圆环；
  - `EchoTrailLayer`：残影轨迹（较新实线 0.82、较旧虚线 0.50，重叠 2–4px 平行错位）。

## W4 · 事件边沿输出 ✅（部分）
- 停驻/离开/占用释放边沿产出开发二 `TimelineEvent`（`DOCK_ENTERED/DOCK_LEFT/OCCUPANCY_RELEASED`），字段含 `tick/actorId/sourceRound/mechanismId/离开方向/reason`，可直接进入 `STABLE_ORDER`。
- ⚠️ **“转向提交”事件未发**：开发二的 `TimelineEvent.EventType` 只有上述三种，没有 `TURN_COMMITTED`。新增类型属 `replay/**`（本卡禁止修改），需开发二扩展后再补。

## 未包含 / 依赖

| 项 | 原因 |
| --- | --- |
| JavaFX `KeyEvent → InputIntent` 适配 | 属 `app/**`（禁止修改），由 PM 在集成层做（裁决草案 B） |
| `FixedStepLoop + 输入 + 控制器` 装配 | 属 `app/**`（裁决草案 F） |
| 与 `PatrolController` 的实际接线（FREEZE 停推进、到达停驻点吸附中心） | 集成步骤；本卡交付的是策略与端口 |
| 残影轨迹的权威数据 | 开发二**最小只读渲染视图**尚未交付；本卡用 `render/RenderViews` 定义渲染侧只读契约，集成层负责投影 |
| `DOCK_LEFT` 记在出界刻（与 README“离开即释放”偏差） | `AutoDockOccupancyPort.tryLeave` 要求位置已在区域外；已在代码注释标 TODO，待 PM 裁决 |

## 验收对照（任务卡 §六）

1. 方向队列：由 C2 覆盖（新方向替换、松键仍 `baseSpeed`、拒绝掉头）✅
2. autoDock：`enteringRegionFreezesAndEmitsDockEntered` / `heldOldKeyDoesNotLeaveDock` / `exitingRegionReleasesAndEmitsDockLeftWithDirectionAndReason` ✅
3. 渲染：图层齐备且不含全屏格线；**画面效果需在集成后由目标机手工验收**（本环境不启动 GUI）
4. 纯逻辑测试不初始化 JavaFX、`mvn test` 退出码 0、覆盖方向队列/autoDock 防误离开/E 缓冲/坐标转换 ✅（201 tests）

---

*本说明随 `codex/dev1-c3` 提交；未修改 `app/**`、`replay/**`、`snapshot/**`、`mechanism/**`、`level/**`、`ui/**`、`pom.xml`、资源等非本卡允许路径。*
