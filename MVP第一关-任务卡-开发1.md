# 第一关 MVP 任务卡 · 开发 1（基础引擎与玩家）

> 目标里程碑：README 8 天计划「第 3 天 · 集成首个玩法竖切片（前两关可通关）」中的第一关部分。
> 本卡只补第一关 MVP 需要的两个模块：**C3（方向输入 + autoDock）** 与 **C5（基础 Canvas 渲染）**。
> 依据：`开发1-基础引擎与玩家-技术指南.md` §5.4（C3）、§5.6（C5）、§2.2（路径边界）。

---

## 一、任务目标

让第一关「留下的脚步」在集成层装配后可看、可操作、可通关：玩家恒速巡行 → 方向键转向 → 进入驻留板自动停驻 → 新方向键离开 → 渲染出墙/地面/玩家/驻留板/门/出口/残影。

当前已就绪：C0（时钟）、C1（固定步长循环）、C2（路径图 + 巡行控制器）均已合入 develop。**本卡补齐 C3 与 C5 基础版。**

## 二、允许修改

```
src/main/java/org/example/timeloop/core/**
src/main/java/org/example/timeloop/entity/**
src/main/java/org/example/timeloop/render/**
src/test/java/org/example/timeloop/core/**
src/test/java/org/example/timeloop/entity/**
src/test/java/org/example/timeloop/render/**
```

## 三、禁止修改

```
app/**  replay/**  snapshot/**  mechanism/**  level/**  ui/**
persistence/**  audio/**  pom.xml  src/main/resources/**
```

> 尤其：**不碰 `app/`**。JavaFX `KeyEvent` 监听器绑定由项目经理在集成层做；开发 1 只提供**纯逻辑输入端口**（可脱离 JavaFX 单测），不要自己改 `TimeTraceLabApplication` 或新建启动类。

## 四、工作项

### W1 · 方向输入（C3 输入部分，纯逻辑，落在 `core/`）
- 定义 `InputIntent`（不可变逻辑意图）：逻辑方向、`pressed/released/held` 边沿、`E`/`Space` 边沿；`WASD` 与方向键映射为同一意图。
- 单槽方向队列：新方向替换**尚未提交**的旧方向；普通路口只接受直行/90° 转向，拒绝原地掉头；无效方向丢弃。
- 提供「把外部按键事件喂入」的纯端口方法，不依赖 JavaFX 类型。

### W2 · autoDock 停驻/离开（C3，与开发 3 机关语义对齐）
- 进入 autoDock 区域边沿 → `MovementState.DOCKED` + 清空方向队列 + 记录「进入前已按住」的旧键快照。
- 只有**进入后新出现**的合法方向键边沿才离开，离开立即恢复 `baseSpeed` 并**发布离开事件**（供开发 2 记录、开发 3 释放占用）。
- 持续按住的旧键不能误触发离开。
- 依赖开发 3 的 autoDock 区域只读查询规格（进入/停驻中心/合法出口）。

### W3 · 基础渲染层（C5 基础版，落在 `render/`）
- 在现有 `CanvasAdapter`/`RenderLayer` 之上实现具体图层：连续地面、内部/边界墙（几何色块，不画全屏格线）、当前玩家、驻留板、门、出口、残影轨迹（读开发 2 的最小只读视图）。
- 逻辑坐标 → Canvas 坐标转换；渲染只读，**不得回写**玩法坐标或机关状态。

### W4 · 事件边沿输出（供开发 2 记录）
- 在停驻/离开/转向提交等状态边沿产生稳定事件（含 tick、actor ID、事件类型、离开方向），字段以开发 2 的 `TimelineEvent` 契约为准。

## 五、前置依赖（需项目经理冻结，缺一即停止）

| 依赖 | 提供方 | 状态 |
| --- | --- | --- |
| `LevelGeometry`（不可变路径/墙/门） | 开发 3 | ✅ 已交付，直接读 |
| autoDock 区域只读查询规格 | 开发 3 | 需先冻结（见开发 3 卡） |
| `TimelineEvent` 字段（进入/离开载荷） | 开发 2 | ✅ 已交付（`feature/replay-r2p5-timeline-event`，待合入 develop；含 `DOCK_ENTERED/DOCK_LEFT/OCCUPANCY_RELEASED`） |
| 最小只读渲染视图（当前/残影位置状态） | 开发 2 | 需先交付（见开发 2 卡） |
| 稳定 actor/mechanism ID 规则 | 开发 3 | 需先冻结（见开发 3 卡） |

## 六、验收条件

1. 方向队列：新方向替换旧方向；松键后普通廊道仍 `baseSpeed`；普通路口掉头被拒绝。
2. autoDock：进入即 `DOCKED` 并清队列；进入前按住的旧键不触发离开；新方向键边沿离开并恢复 `baseSpeed`；离开事件带正确方向。
3. 渲染：不显示全屏格线也能看清墙、地面、玩家、双驻留板、门、出口；残影轨迹与当前玩家可区分。
4. 纯逻辑测试不初始化 JavaFX，`mvn test` 退出码 0；新增用例覆盖方向队列、autoDock 防误离开、E 缓冲、坐标转换。

## 七、验证命令

```bash
export JAVA_HOME="D:/1/jdk"; export PATH="$JAVA_HOME/bin:$PATH"
./mvnw.cmd -o test
```

## 八、停止条件

- 若 `TimelineEvent` 字段或 autoDock 查询规格尚未冻结：**只输出字段提案，不虚构接口**，交项目经理裁决后继续。
- 若发现需改 `app/**`、`pom.xml` 或跨板块实现：立即停止并通知项目经理拆卡。
