# EXPAND-MAP-VIEW-01 · 窗口/视口（开发三方案 A）PM 裁决

> 卡号：**EXPAND-MAP-VIEW-01**（开发三建议号，采纳）｜ 地图扩展部分登记为 **EXPAND-MAP-VIEW-02（未立项）**
> 主责：**PM（`app/**`）** ＋ **开发一（`render/**`：等比适配纯函数与图层取变换方式）**
> 状态：**已立项，A 可开工**；B 未立项（需项目方确认，见 §五）
> 基线：`origin/develop` = `8abd7e7`（PM clean 复跑 **334 / 0 / 0**，墙钟 7.8s）
> 日期：2026-09-12

## 一、PM 事实核对（含两处补充与一处更正）

1. **窗口硬编码属实**：`TimeTraceLabApplication:37-38`（`WORLD_WIDTH = 11*48`、`WORLD_HEIGHT = 9*48`）、
   `:68` Canvas、`:85-86` prefSize/Scene、`:111` `renderFrame(WORLD_WIDTH, WORLD_HEIGHT, ...)`。
2. **`WorldTransform` 是 record `(scale, originX, originY)`**，已有 `identity/toCanvasX/Y/toWorldX/Y/scaled/translated`
   → 等比适配可以做成**纯函数**并单测，符合开发三判断。
3. **⚠️ 补充（A2 的真实成本在这里）**：所有图层在**构造时**就把 `WorldTransform` 存成 final 字段
   （如 `PlayerLayer`、`GroundWallLayer`、`MechanismLayer`、`EchoTrailLayer`、`PathNodeHintLayer`），
   **resize 后重算出来的新变换传不进已有图层**。因此 A2 必须让图层**每帧取当前变换**
   （`Supplier<WorldTransform>` 或一个可变 `WorldTransformHolder`）。只改 `app/**` 做不出 A2。
4. **`CanvasAdapter.renderFrame` 已按 `canvas.getWidth()/getHeight()` 清屏** → 居中留边区域不会残留上一帧，A2 无需改清屏。
5. **C1 §2 引用属实**：默认世界 `20 × 12` 格 → `960 × 576`；最小窗口初始画布同为 `960 × 576`，初始 1:1；
   窗口尺寸改变后由渲染边界统一算等比缩放与居中留边、所有世界图层共用同一变换。
6. **更正**：轮长 `D` **不是冻结常量** —— README §九 明确「默认每轮 20 秒，建议范围 12–25 秒」「轮长 `D` 可随关卡调整」。
   但第一关现值 `16 s / L=1 / maxRounds=3` **写在 README §六 关卡规划表**（L330），改它等于改 README 表格 → 需 PM 先出变更记录。

## 二、五项裁决（对应开发三 §六）

### 2.1 需求本质：**先做 A（窗口/视口），B 另立项**

依据是硬事实而非偏好：当前窗口 `528 × 432` **远低于 C1 已批准的最小窗口 `960 × 576`**，
即"窗口偏小"本来就有一笔欠账，且 A 对玩法**零影响**（世界坐标、碰撞、到达刻、录制长度全不变）。
B（地图扩展）**未立项**：要立项需项目方给出"11×9 的玩法空间不够"的具体体验理由，
并接受 B1 等于**重做第一关**（节点重排 + 板/门/出口位置 + 到达刻重算 + 已通过的第一关端到端测试全部重跑）。

### 2.2 A 方案：**直接做 A2，不做 A1 的固定倍数临时常量**

- **窗口基准取 C1 的 `960 × 576`**（不是 792×648）；世界 `528 × 432` 在该窗口内由等比适配得到
  `scale = min(960/528, 576/432) = 1.333…`，水平居中留边 → 每格从 48 画到 64 像素，窗口面积是现在的 2.4 倍，
  观感一次到位，且不产生"临时倍数 → 正式适配"的两套路径。
- 若项目方要求"今天就要更大"，允许 A1 一行临时值（`VIEW_SCALE = 1.5`）**作为本卡第一个提交**，
  但**必须在 A2 落地的同一批里删除该常量**；禁止长期并存（与本项目对"双源/双路径"的一贯处理一致）。
- A2 必须同时排，且与 A1（若做）同卡。

### 2.3 B：**暂不选 B1/B2**；若立项，**优先 B2（轻度扩展）**

B1（20×12、重做第一关）会把已验证的双板因果链与到达刻证据全部作废；收益（更大空间）与风险不成比例。
B2（14×10 / 16×11）保留既有拓扑与解法，只增加边距与走廊长度，到达刻只需微调。
**选 B1 的前提**：项目方明确要求更大玩法空间，并接受第一关重做 + 全部关卡测试重跑。

### 2.4 冻结参数：轮长可在 12–25 秒内调，`maxRounds`/`L` 不建议动；到达刻**必须同卡**

- 轮长：若 B 卡需要更长，**允许**在 README §九 给的 12–25 秒区间内调整，但**PM 先改 README §六 表格**，再实施；
- `maxRounds = 3`、`L = 1`：动了会牵动残影代际与 §九 公式叙述，**不建议改**；
- **到达刻重算与 B 同卡**（禁止先改地图后补到达刻），且只用固定步长模拟值。

### 2.5 分支与同批：A 卡内 `render/**` + `app/**` **必须同批合并**

窗口尺寸与适配变换强耦合，拆开会出现"窗口大了但世界没缩放/偏移错"的中间态。
B 另卡、另分支。**并且 A2 要把 `WORLD_WIDTH/HEIGHT` 改为从 `levelData` 推导**（网格尺寸 × tileSize）——
这正是 C1「窗口与世界解耦」要求的落地，也是让 B 将来变成**纯 `level/**` 改动**的前提。

## 三、A 卡任务拆分

### 开发一（`render/**`）

1. 新增纯函数（可放 `WorldTransform`）：

   ```java
   public static WorldTransform fit(double worldW, double worldH, double viewW, double viewH);
   // scale = min(viewW / worldW, viewH / worldH)（等比，不拉伸）
   // originX = (viewW - worldW * scale) / 2, originY = (viewH - worldH * scale) / 2（居中留边）
   // 非有限或非正输入抛 IllegalArgumentException（防御 0/负尺寸）
   ```
2. **图层改为每帧取当前变换**：`Supplier<WorldTransform>` 或 `WorldTransformHolder`（二选一，写进交付说明）；
   所有既有图层（GroundWall / Mechanism / Player / EchoTrail / PathNodeHint）一并迁移；渲染仍只读、不回写玩法状态。
3. 单测：1:1、极端宽高比（很扁/很高）、留边对称、非正尺寸防御、`toWorld`/`toCanvas` 往返一致。

### PM（`app/**`）

1. 窗口基准 `960 × 576`，保持可缩放；HUD 不参与缩放；
2. `WORLD_WIDTH/HEIGHT` 改为从 `levelData` 推导（`getTileGrid()` 的列/行 × `getTileSize()`）；
3. resize 接线：canvas 尺寸变化 → `transform = WorldTransform.fit(worldW, worldH, canvasW, canvasH)` 写入 holder；
4. `renderFrame(...)` 传世界尺寸（清屏已按 canvas 尺寸，无需改）。

## 七、地图改造后的重新定界（PM，2026-09-13）

PR #67（`58adc0e`，已随 `418d0ca` 合入 develop）把第一关改成 **28 × 16（265 可走格）** 并带来多处渲染/装配改动，
本卡据此更新：

### 7.1 已完成（#67 替本卡做掉的部分）

`TimeTraceLabApplication` 的 `WORLD_WIDTH/HEIGHT` 已改为从网格推导（`GRID_COLS/ROWS × TILE_SIZE`）——
即本卡 §三 PM 侧第 2 条**已完成**，无需重做。

### 7.2 仍然有效（本卡剩余范围）

1. **开发一（`render/**`）**：`WorldTransform.fit(worldW, worldH, viewW, viewH)` 纯函数 + **五个图层改为每帧取当前变换**
   （构造时存 final 字段的现状会让 resize 后的新变换失效）+ 单测；
2. **PM（`app/**`）**：resize 接线（canvas 尺寸变化 → 重算 fit 写入 holder）+ HUD 不缩放 +
   世界尺寸继续从 `levelData` 推导。

### 7.3 窗口基准调整（因世界变大）

世界现在是 `1344 × 768`。直接 1:1 开窗在小屏笔记本上会超出可视区，因此**默认窗口改为
`1280 × 720`（若屏幕更小则取屏幕可用区的 90%）**，世界在其中由 `fit(...)` 等比适配并居中留边
（约 `scale ≈ 0.95`，每格约 45.7 px）；窗口仍可自由缩放，缩放时重算 fit。**HUD 始终不参与缩放。**

### 7.4 `EXPAND-MAP-VIEW-02`（地图扩展 B 卡）：**作废关闭**

需求已由 PR #67 直接实现（28×16、到达刻重算与地图改造同批、轮长仍为 `16 s`、`L=1`、`maxRounds=3` 未改），
超出原 B1/B2 讨论范围，**不再立项**。

### 7.5 文档同步（PM）

`docs/decisions/C1-启动与世界坐标约定.md` §2 写的「默认世界 `20 × 12`（960 × 576）、最小窗口同为 960 × 576」
已被第一关的 28×16 取代 → PM 将在 C1 追加**取代说明**（保留历史决策记录，不删原文），
并在 README §三 的视觉/窗口表述里同步（与 §十六 门禁的截图口径一致）。


## 四、验收（A 卡）

1. 窗口默认按 §7.3 的基准，世界**完整可见、等比、居中留边**（不裁切、不拉伸）；

2. 拉大/拉小/极端窄高窗口时仍完整且不变形；HUD 不缩放；
3. `fit(...)` 与 `toWorld/toCanvas` 往返有单测；极端输入有防御；
4. 世界坐标零变化：`.\mvnw.cmd -o clean test` 退出码 0、**任何单测 ≤ 1s**、数字 ≥ 334 不回退；
5. 第一关端到端（`Level01AssemblyLevel01FlowTest`：残影占左板 + 玩家占右板 → 门开 → 按 E）在新窗口下仍绿；
6. 画面取证重取：IDLE / CRUISING / SLOWED 三态 + 节点菱形远近 + **新窗口整体截图**，登记到 `FINAL-ACCEPT`。

## 五、EXPAND-MAP-VIEW-02（B 卡，**已作废关闭**）

改由 PR #67 直接实现（见 §7.4）；本卡不再立项。若将来仍要更大玩法空间，须重新立项并按当时的关卡现状评估到达刻。

## 六、边界

- PM：`app/**`、`README.md`、任务卡；开发一：`render/**` + 测试；开发三：`level/**`（B 卡）+ 测试。
- 全员禁止：`pom.xml`、`src/main/resources/**`、`replay/**`、`snapshot/**`；跨模块改动先出提案。
