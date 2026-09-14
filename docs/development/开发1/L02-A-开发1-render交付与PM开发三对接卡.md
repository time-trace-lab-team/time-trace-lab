# L02-A 开发一 render 交付与 PM/开发三对接卡

> 任务：`L02-A-DEV1` A1/A2/A3/A4/A5
> 开发一分支：`codex/l02-a-dev1`
> 当前基线：`origin/develop @ a8bb07139f8c298ebfa36a7803780389bd8f7a49`
> 对接对象：PM（`app/**`）、开发三（`level/**` / `mechanism/**` / `ui/**`）
> 日期：2026-09-14

## 1. 开发一已完成

| 项目 | 交付结论 | 证据 |
| --- | --- | --- |
| A1 双残影重合可辨 | 新残影实线 `+2 px`，旧残影虚线 `-2 px`；完全同路径时仍可靠线型与错位区分 | `EchoTrailLayerReadabilityTest` |
| A2 独立房门视觉 | `DOOR.active=false` 画实体门扇/竖栅；`true` 只留门柱/顶梁/中央缺口 | `MechanismLayerGateMergeVisualTest.closedAndOpenRoomDoorHaveDistinctPassageStructures` |
| A3 多块驻留板 | 多块 `PLATE` 同屏时，占用/空闲外观跟随状态，不依赖固定位置 | `MechanismLayerGateMergeVisualTest.multiplePlatesExposeOccupiedAndEmptyStateIndependentOfPosition` |
| A4 跨板块边界 | PM 已确认 HUD 不进 Canvas；`H` 从本批移出 | PM 裁决 §十二.1 |

本批没有修改 `RenderViews.EchoTrail` 或 `RenderViews.Mechanism` 签名，没有新增机关、事件或可写状态。

## 2. 精确交付白名单

```text
src/main/java/org/example/timeloop/render/MechanismLayer.java
src/test/java/org/example/timeloop/render/MechanismLayerGateMergeVisualTest.java
src/test/java/org/example/timeloop/render/EchoTrailLayerReadabilityTest.java
docs/development/开发1/L02-A-0A-0B-PM对接卡.md
docs/development/开发1/L02-A-开发1-render交付与PM开发三对接卡.md
```

暂存与提交时不得加入工作区里已存的其他修改/未跟踪文档，禁止使用 `git add -A`。

## 3. PM `app/**` 配对要求

1. 以开发三的 L02 只读状态构造 `RenderViews.Frame`，不把玩法判定放入 render；
2. `MechanismKind.PLATE.active` = 当前是否被占用；L02 三块板全部使用 `PLATE`，不是 `SWITCH`；
3. `MechanismKind.DOOR.active` = 房门当前是否已打开/可通行；L02 独立房门使用 `DOOR`；
4. L02 终点闸门仍使用 `EXIT.active`，不得用 `DOOR` 替代；
5. 双残影依旧构造 `EchoTrail(sourceRound, points, newer)`，`newer` 只表达当前两代的新/旧关系；
6. 配对后在真实 L02 画面检查图层顺序、机关位置与两门同时显示，不用 Canvas 偏移修补关卡坐标。

## 4. 开发三 `ui/level/mechanism` 对接要求

1. HUD 显示的 `E1/E2` 是相对代际文案，不改写 `sourceRound`；
2. HUD 文案与字段留在 `ui/**`，由 PM 在 `app/**` 用当前轮次 + `sourceRound` 配对；
3. L02 三块板必须保持普通驻留板语义，不锁存；
4. 房门离开门外板后立即回锁的语义由 `mechanism/level` 保证，render 只读 `active`；
5. L02 数据/装配进入配对分支后，请提供可同时看到三板、房门和终点闸门的验收场景。

## 5. 本批明确不做

- `H` 增强：PM 已移至待发的 `RENDER-H-ECHO-EMPHASIS`；
- 开发一不修改 `ui/**` 的 HUD，不修改 `app/**` 的输入或构造点；
- 不修改 `level/**`、`mechanism/**`、`replay/**`、`snapshot/**`；
- 不在本批开始 L02-B 射线渲染。

## 6. 验收分层与待回填证据

### 6.1 开发一可独立完成

- [x] 双残影重合离屏回归；
- [x] `DOOR` 开/关离屏回归；
- [x] 多 `PLATE` 占用/空闲离屏回归；
- [x] 最新 develop 基线全量测试：连续 3 次均为 `423 / 0 / 0 / 0`；
- [x] `git diff --check`；
- [ ] 交付 commit SHA / 远端分支（需用户授权提交与 push）。

### 6.2 不能由开发一单独宣告

- [ ] headless/app 已将 L02 三板两门与双残影投影到 `RenderViews.Frame`；
- [ ] JavaFX 真实窗口中双残影、三块板、房门和终点闸门均可辨；
- [ ] HUD 显示正确的相对 `E1/E2`；
- [ ] 完整三轮官方解人工验收。

## 7. 已知例外

`Level01MapRenderSnapshotTest` 由 PM 裁决为 L02-A 基线已知例外，本批门禁是“不较基线
中位数恶化 > 50%”。该用例归属开发三，由 `TECH-DEBT-SNAPSHOT-RUNTIME-v2`
另行收口，不阻塞开发一 render 交付。

本次在无 JavaFX 实跑/其它构建并发时的 3 次 `clean test` 实测为：

```text
3.780 s / 3.068 s / 3.014 s；中位数 3.068 s
```

相对开发一本机旧基线 `2.956–3.027 s` 未达到“中位数恶化 > 50%”，因此本批不构成回归。
第 3 次全量运行中，`EchoTrailLayerReadabilityTest = 0.018 s`，
`MechanismLayerGateMergeVisualTest = 0.097 s`。
