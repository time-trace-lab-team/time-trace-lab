package org.example.timeloop.mechanism.autodock;

/** autoDock 动态状态的纯快照/恢复端口。 */
public interface AutoDockSnapshotPort {

    AutoDockStateSnapshot createSnapshot();

    void restore(AutoDockStateSnapshot snapshot);
}
