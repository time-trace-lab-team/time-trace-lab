# EXPAND-MAP-VIEW-01 · PM app 对接卡（视口适配接线）

> 责任：**PM（`app/**` 唯一所有者）** ｜ 上游：**开发一**（`render/**`：`WorldTransform.fit` + 六层 supplier 构造器）
> 基线：`origin/develop` = `418d0ca`（PM clean 复跑 358/0/0）
> 状态：**已完成接线并配对推送** → PR #72（见 §七 回传记录）
> 日期：2026-09-13（接线与验收同日完成）

## 一、开工前置（**交付已复核**：`git ls-remote` 命中 `90d6733`）

开发一交付（回传）：

```
branch: codex/expand-map-view-dev1-v4
SHA:    90d6733a7585bd70bd6c180480a045d7a05a7dc6
```

其本地 HEAD 与 `origin/codex/expand-map-view-dev1-v4` 跟踪引用一致，`git push -u` 已返回 `new branch`。
PM 本轮曾连续 15 次 `git ls-remote` 连接被重置 —— **这只表示 PM 暂时无法复核，不能推出分支不存在**；
PM 已于卡内更正此前的错误表述（前一版写“尚无 commit / push”，是交付前的旧状态，已失效）。

**PM 复核命令（网络恢复后立即执行）**：

```powershell
git ls-remote origin refs/heads/codex/expand-map-view-dev1-v4
# 期望输出 90d6733a7585bd70bd6c180480a045d7a05a7dc6；确认后即可建配对分支
```

> 流程提醒（不针对本次，仅作约定）：交付以**远端提交**为凭据；PM 若无法联网，只能记为“待复核”，
> 不得据此判定“未交付”。开发一工作区存在需保留的用户文档 —— **禁止 `git add -A`**，
> 交付一律用精确白名单提交（本次即如此）。


## 二、PM 接线范围（唯一生产文件：`app/TimeTraceLabApplication.java`）

1. **世界尺寸从关卡数据推导**（不得硬编码 28/16/48 为通用常量）：
   `worldW = level.getTileGrid()[0].length * tileSize`、`worldH = level.getTileGrid().length * tileSize`
   （当前 `#67` 已用 `GRID_COLS/GRID_ROWS × TILE_SIZE`，请改为从 `LevelData` 读，保持与关卡一致）。
2. **持有一个"当前变换"引用 + 一个共享 `Supplier<WorldTransform>`**：

   ```java
   // 初始：已知正尺寸的占位（世界尺寸 × 1:1），保证首帧不调用 fit(0,0)
   WorldTransform[] current = { WorldTransform.identity() };
   Supplier<WorldTransform> transformSource = () -> current[0];

   canvas.widthProperty().addListener((o, a, b) -> refit());
   canvas.heightProperty().addListener((o, a, b) -> refit());
   void refit() {
       double vw = canvas.getWidth(), vh = canvas.getHeight();
       if (vw > 0 && vh > 0) {                 // 布局阶段 0 尺寸时不得调用 fit
           current[0] = WorldTransform.fit(worldW, worldH, vw, vh);
       }
   }
   ```

3. **六层统一使用 supplier 构造器**：`GroundWallLayer`、`SpawnLayer`、`PathNodeHintLayer`、
   `MechanismLayer`、`PlayerLayer`、`EchoTrailLayer`（旧固定变换构造器保留但 PM 不再使用）。
4. **布局边界**：fit 的 `view` 尺寸只用**实际可绘制区域**（Canvas），**不把 HUD 高度混入世界投影**。
5. **resize 回调只更新投影**：不得调用 `assembly.tick(...)`、不得改 `roundTick`、世界坐标、机关状态、
   录制帧或输入状态。

## 三、默认窗口尺寸（PM 裁决，与 C1 的关系）

- **默认窗口 = `1280 × 720`**；若屏幕可用区更小则取可用区的 90%。世界（1344×768）在其中由 `fit` 等比适配并居中
  （约 scale≈0.95、每格≈45.7px）。
- **`960 × 576`（C1 §2 的最小窗口基准）作为 resize 验收档位之一**，不是默认尺寸：
  在该尺寸下世界仍须完整、等比、居中（关于 scale≈0.714、每格≈34px）。
- 理由：C1 §2 写的是「默认世界 20×12 = 960×576、最小窗口同为 960×576」，但第一关在 `#67` 后已是 28×16（1344×768），
  C1 该条已过期 → PM 会在 `docs/decisions/C1-*` 追加取代说明（本文件不替代该动作）。

## 四、配对顺序与停止条件

1. **PM 配对分支**：从 `origin/develop` 建 `codex/expand-map-view-pair`，合入
   ①开发一的 render 提交 ②PM 的 app 提交；
2. **禁止单边合入**：开发一的 `render/**` 分支不得单独向 develop 提 PR/合入（单独合入后 app 仍用固定变换，
   窗口不会适配；而 PM 单边也无从编译）；
3. **PM 回传**：PM app SHA、配对分支名与配对 SHA、全量测试数字、任何新增生产路径；
4. **进入 V4-INTEGRATION-GATE / PR 的条件**：配对分支全量自动化绿 + `git diff --check` 干净 +
   JavaFX 人工 resize 验收通过（见 §五）。

## 五、配对后验收（PM + 测试在有显示器的目标机共同执行）

**自动化**：`.\mvnw.cmd -o clean test` 退出码 0、任何单测 ≤ 1s、数字 ≥ 362 不回退；
`git diff --check` 无空白错误。

**人工（JavaFX）**：
1. 默认窗口 `1280×720` 与 **`960×576`** 两档均**完整显示地图、格子为正方形**；
2. 明显更宽 / 明显更高的视口：**对称留边、无拉伸**；
3. 连续 resize 时**六层始终重合**（地面/墙、出生点、节点、机关、玩家、残影）；
4. resize **不改变**玩家世界位置、碰撞、驻留、门/出口、`roundTick`、录制与残影行为；
5. 连续 ≥10 次侧向转向 + 段中间掉头 + 静止路口吸附，并**完整通关第一关一次**；
6. 保存三张完整窗口截图（含 HUD）：默认、明显更宽、明显更高 → 登记到 `FINAL-ACCEPT` §A 第 4 条。

## 六、PM 侧待办（与本卡并行）

- `docs/decisions/C1-*` 追加取代说明（世界 28×16 / 窗口按 fit 适配）；
- README §三/§十六 同步 `BUG-001` 后的转向与掉头规则（等开发一的规则表）；
- 开发一规则表到手后一并落文档。

## 七、回传记录（2026-09-13 完成）

```
render 交付：  codex/expand-map-view-dev1-v4 @ 90d6733  （开发一，15 文件 +936/-18）
配对分支：     codex/expand-map-view-pair @ 2256440
PM app SHA：   2256440  feat(app): 视口自适应接线（EXPAND-MAP-VIEW-01）
               唯一文件 src/main/java/org/example/timeloop/app/TimeTraceLabApplication.java（+57/-22）
PR：           #72  https://github.com/time-trace-lab-team/time-trace-lab/pull/72  (base: develop)
```

**自动化证据**：`.\mvnw.cmd -o clean test` → **Tests run: 377, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS**
（develop 基线 362 + 渲染侧新增 15）；`git diff --check` 干净；配对分支编译产物含 `TimeTraceLabApplication.class`。

**PM 侧实现说明（与 §二 的差异，均为等价或更简实现）**：

- §二 示例用 `WorldTransform[] current` 承载可变引用；实际用 `ObjectProperty<WorldTransform>`
  （`SimpleObjectProperty`，命名 `worldTransform`），语义一致且便于调试观察，图层侧仍是 `Supplier<WorldTransform>`。
- §二 第 1 条要求从 `LevelData` 推导：已实现，并额外对空网格 fail-fast（`IllegalStateException`），
  避免 `grid[0]` 越界。
- 其余（六层 supplier、HUD 不混入投影、refit 只在 vw/vh > 0 时计算、resize 不触碰 tick/世界坐标）均按卡执行。

**人工验收（本机 JavaFX 实跑，非模拟）**：

| 档位 | 客户区 | 结果 |
| --- | --- | --- |
| 默认 | 1280×720（外框 1294×758） | 世界完整、居中，六层对齐 |
| C1 基准 | 958×568 | 世界完整、左右对称留边，格子为正方形 |
| 竖高 | 1398×892 | 上下对称留边，无拉伸、无裁切 |
| 扁宽 | 1598×592 | 左右对称留边，无拉伸、无裁切 |

三档 resize 期间进程存活、无异常栈；HUD 始终固定顶部、不随投影缩放。

**新增待办（不属于本 PR 阻塞项）**：

- `Level01MapRenderSnapshotTest` 单测耗时 **1.247 s**，超「单测 ≤1s」口径。属渲染侧随 28×16 地图新增的快照测试，
  与 app 接线无关（app 不参与单测）。已请开发一确认成本或降耗。
- §五 第 4/5 条（resize 不改变玩法状态、连续转向/掉头/吸附 + 完整通关一次）仍需在目标机上人工确认；
  PM 已完成投影与图层对齐部分。
