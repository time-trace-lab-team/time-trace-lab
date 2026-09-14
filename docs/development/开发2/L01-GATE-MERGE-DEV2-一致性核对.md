# L01-GATE-MERGE-DEV2 · 一致性核对结论

> 任务卡 `L01-门与终点合并-任务卡-开发2.md` §二.2
> 主责：开发二（`snapshot/**`）　｜　日期：2026-09-14
> 基线：`origin/develop` = `7aa45bc`（锁存字段已冻结并合入）

## 一、问题 1：Door 与 ExitTerminal 恢复先后是否造成「终点已解锁但门 LOCKED」中间态？

**结论：不会。** 恢复过程不存在事件驱动的中间态。

**依据**（代码位置 `snapshot/MechanismSnapshot.java`）：

- `restore()` 的恢复顺序是 `plates → doors → exits`（L126–136），但三者的 `restore` 都是**纯状态照抄**，**不派发任何事件**：
  - `DockingPlate.restore`：javadoc 明确「直接恢复纯状态，不调用 tryEnter/tryExit，因而不会产生重复 gameplay 事件」。
  - `Door.restore`：直接 `this.state = snapshot.getState()`，**不触发** `updateDoorState`。
  - `ExitTerminal.restore`：直接 `this.doorUnlocked/this.triggered = 快照值`，**不依赖** `DOOR_UNLOCKED` 事件。
- 因此先恢复板、再恢复门、最后恢复终点，任何一步都不会「重算」下游状态，快照里 `Door=UNLOCKED` 与 `Exit.doorUnlocked=true` 本就一致（capture 时门解锁会派发 `DOOR_UNLOCKED` 令终点同步），恢复后仍一致。

## 二、问题 2：恢复到「开关锁存 ON、门 UNLOCKED」后，门态是否被重算锁回？

**结论：不会被重算，门照抄快照。** 锁存 ON 而占用为空时，门仍保持 UNLOCKED。

**依据**：

- `Door` 的「重算」入口只有一个：`onEvent(PLATE_ENTERED/PLATE_EXITED)` → `updateDoorState()` → `checkAllPlatesOccupied()`（`door.isUnlocked()` 由占用实时推导）。**`restore` 不派发这两个事件**，只 `this.state = snapshot.getState()`，所以不会走到重算路径。
- 关键边界：锁存 ON 时 `DockingPlate.getState()` 为 `UNOCCUPIED`（玩家已离开开关）、占用为空，若此刻「重算」会因 `occupancy.isOccupied()` 取不到真实占用而把门锁回 —— 但 `isOccupied()` 已含锁存语义（`state==OCCUPIED || latched`），且 `restore` 根本不重算，因此双重保险下不会锁回。

**实证**：`MechanismSnapshotSwitchLatchTest.restoreLatchedSwitchKeepsGateUnlocked` 已覆盖 —— 恢复到「开关锁存 ON（占用空）+ 左板残影占用 + 门解锁 + 终点未触发」后，`door.isUnlocked()==true`、`exit.interact(...)==true`。

## 三、第 3 条测试 `restoringNonSwitchLatchedFlagIsRejected` 的缺口（需开发三）

**结论：当前无法在 `snapshot/**` 层实现，机制侧缺一个校验，对应停止条件 §六.3，停手回报开发三。**

**根因**：

1. `DockingPlate` 不暴露 `latching`（无 `isLatching()` 或等价访问器），`snapshot/**` 层无法判断一个板是否为开关。
2. `DockingPlate.restore` 无条件透传锁存位：`this.latched = snapshot.isLatched()`（L269），**不校验**「非开关板（`latching=false`）不得带 `latched=true`」。

因此「对非开关机关设 `latched=true` 的快照」当前会被静默接受，非开关板被恢复成 `latched=true` → `isOccupied()` 恒真 → 门被错误解锁，属于机制侧锁存行为缺陷。

**建议开发三修改**（机制侧，内聚在 `DockingPlate.restore`）：

```java
// DockingPlate.restore，在 this.latched = snapshot.isLatched() 之前：
if (!latching && snapshot.isLatched()) {
    throw new IllegalArgumentException(
            "非开关驻留板不能恢复锁存位: id=" + id);
}
```

（备选：暴露 `public boolean isLatching()`，由 `snapshot/**` 层校验 —— 但推荐前者，语义内聚在机制侧、避免第二真相源。）

**待开发三补该校验后**，开发二补写 `restoringNonSwitchLatchedFlagIsRejected` 测试：构造「非开关板 + `latched=true` 快照」→ `MechanismSnapshot.restore` 应抛 `IllegalArgumentException` 且所有机制状态零变化（沿用 `restore` 现有「先校验后恢复」的半恢复防护骨架）。

## 四、交付物对照

| 任务卡 §二 | 状态 |
|---|---|
| §二.1 锁存字段覆盖 | ✅ `MechanismSnapshot.capture` 透传 `snapshot.isLatched()`（1 行） |
| §二.2 一致性核对 | ✅ 见本文件 一/二 |
| §二.3 测试 1 `restoreLatchedSwitchKeepsGateUnlocked` | ✅ 已写，通过 |
| §二.3 测试 2 `roundEndResetClearsLatchedSwitch` | ✅ 已写，通过 |
| §二.3 测试 3 `restoringNonSwitchLatchedFlagIsRejected` | ⏸ 阻塞于机制侧缺口（见 三），待开发三 |
