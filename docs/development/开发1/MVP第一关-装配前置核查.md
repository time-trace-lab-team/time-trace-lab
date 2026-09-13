# 第一关 MVP 装配前置核查（开发一 / 集成）

> 依据：`README.md`、`第一关MVP-集成装配技术指南.md`、`MVP第一关-任务卡-开发1.md`
> 日期：2026-09-10｜分支：`codex/dev1-c3`
> 目的：核查"补充装配框架"的前置，给出可做/阻塞与解除条件。

## 一、结论

**装配框架当前无法在本机完成**，两处硬阻塞：**A 本地缺件**（指南引用的 develop 版本与 4 个类不在本地，且 `git fetch` 被认证拦）；**B 第一关路径图不合法**（实测 `LevelGeometryImpl` 构造抛异常，属开发三 W2 未完成）。两者都不在"仅补框架"能解决的范围内。

## 二、阻塞 A · 本地缺件（指南引用的版本/类型不存在）

- 指南称"三个开发模块全部合并进 develop（`a862fc8`），240 条测试全绿"；本地 `develop` = `ea468eb`，**`a862fc8` 不可达**。
- 指南 §三列为"已核实现有接口"的 4 个类型，在**全部本地 ref** 中均不存在：

  | 类型 | 本地状态 |
  | --- | --- |
  | `ReplayPort` | ❌ 全 refs 无 |
  | `MvpRenderSnapshot` | ❌ 全 refs 无 |
  | `SharedHud` | ❌ 全 refs 无 |
  | `HudViewModel` | ❌ 全 refs 无 |

- `git fetch origin` → `schannel: AcquireCredentialsHandle failed: SEC_E_NO_CREDENTIALS`（私有仓库需认证，本 shell 无法拉取）。
- **解除**：请在本机终端 `git fetch origin && git pull --ff-only origin develop`；同步后我即可按指南 §三 的真实签名装配。

## 三、阻塞 B · 第一关路径图不合法（已实测）

诊断探针 `src/test/java/org/example/timeloop/app/AssemblyProbeTest.java` 实测：

```
[ASSEMBLY-PROBE] LevelGeometryImpl(Level01Footsteps.build()) -> FAILED:
IllegalArgumentException: 非法孤点: 节点 L01_node_left_end 没有任何相邻节点
```

- **原因**：`LevelGeometryImpl` 要求每个节点的每个合法出口在**恰好 1 tile**（`tileSize`）处存在相邻节点；而 `Level01Footsteps` 的路径节点相距 **3+ tiles**：`fork(240,144)`、`left_end(96,240)`、`right_end(384,240)`、`exit_terminal(432,240)`（tileSize=48）。`left_end` 只声明 `UP`，其 1-tile 上方无节点 → 被判"孤点"。
- **佐证**：开发三自己的规格文档《开发三-稳定ID与排序-autoDock规格冻结.md》结尾写明「**下一步 W2 负责修复第一关路径图并完成 `LevelGeometryImpl` 构造验收**」——即该验收**尚未完成**。
- **旁证**：全仓（含测试）没有任何 `new LevelGeometryImpl(...)` 的调用/测试，说明它从未被验收过。
- **属性**：`level/**` 属**开发三**允许路径，开发一/集成不得修改。
- **解除**：由开发三完成 W2——修正 `L01`（及 `L03`）路径节点，使其满足 1-tile 相邻，或放宽 `LevelGeometryImpl` 的相邻判定并补验收测试。

## 四、装配框架骨架（按指南 5 步，标注当前可做性）

| 步 | 内容 | 现有可用接口 | 当前状态 |
| --- | --- | --- | --- |
| 1 | 加载关卡 | `Level01Footsteps.build()` → `LevelData` | ✅ 可做 |
| 1 | 关卡几何 | `new LevelGeometryImpl(LevelData)` | ❌ **阻塞 B**：L01 构造抛异常 |
| 2 | 创建玩家 | `PatrolController(OrthogonalPathGraph, startNodeId, dir, PatrolConfig)` | 🟡 需自建 `core.path` 图（level→core 桥接），且依赖步 1 的节点有效性 |
| 2 | 创建机关 | `new DockingPlate/Door/ExitTerminal(...)` | ✅ 可做（需在 `app/` 写小工厂，指南 §五.1） |
| 2 | autoDock | `new AutoDockService(LevelData)` | ✅ 可做（L01 数据满足其校验） |
| 3 | 每 tick 驱动 | `FixedStepLoop` + `InputIntent` + `C3DockController` + `PatrolController.advance` + `RecordingSession.recordFrame` | 🟡 各件齐（含开发一已交付的 `C3DockController`/`InputIntent`），但巡行依赖步 1/2 的图 |
| 3 | 残影读取 | `EchoQueue.activeEchoes(round)` + `EchoState.frameAt(tick)` | ✅ 可做（**替代**指南的 `ReplayPort`） |
| 4 | 渲染 | `CanvasAdapter` + 开发一图层 + `WorldTransform` | ✅ 可做（**替代**指南的 `MvpRenderSnapshot`，用 `render/RenderViews`） |
| 5 | HUD | `ui/LifetimeUI`（现有） | 🟡 指南的 `SharedHud`/`HudViewModel` 不存在；需新建或待同步 |
| — | 输入适配 | JavaFX `KeyEvent → InputIntent` | ✅ 可做（`app/` 内） |

> 说明：`ReplayPort`/`MvpRenderSnapshot` 可用**现有** `EchoQueue`+`EchoState` 与 `render/RenderViews` 在 `app/` 内等价替代；`SharedHud`/`HudViewModel` 需同步或新建。**真正卡死装配的是阻塞 B**（几何图无法构造）。

## 五、开发一已交付、可直接用于装配的零件（`codex/dev1-c3`）

- `core/input/LogicalKey`、`core/input/InputIntent`（输入契约，纯 Java）
- `entity/C3DockController`、`entity/C3DockDecision`、`entity/DockEventReason`（停放/离开策略 + 冻结原因）
- `render/WorldTransform`、`render/RenderPalette`、`render/RenderViews`
- `render/GroundWallLayer`、`render/MechanismLayer`、`render/PlayerLayer`、`render/EchoTrailLayer`
- 证据：`.\mvnw.cmd test` → 202 tests（含本探针）全绿

## 六、建议

1. **开发三**：完成 W2（修正 L01/L03 路径图并补 `LevelGeometryImpl` 构造验收）——**这是装配的硬前置**。
2. **你/终端**：`git fetch` 同步 `develop` 到含 `a862fc8` 的版本，解决缺件 A。
3. 两项齐备后，我按指南 5 步在 `app/` 落地装配框架（含 level→core 图桥接、机关小工厂、输入适配、图层与 HUD 装配），并用**无头单测**验证装配链路，JavaFX 窗口留目标机手工验收。

---

*本文为装配前置核查，未修改 `core/**`、`entity/**`、`render/**`、`replay/**`、`mechanism/**`、`level/**`、`ui/**`、`pom.xml`；仅新增 `app/` 下的诊断探针测试。*
