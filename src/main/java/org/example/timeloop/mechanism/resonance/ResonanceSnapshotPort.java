package org.example.timeloop.mechanism.resonance;

/**
 * 固定区域共振的模块内快照/恢复端口。
 *
 * <p>开发 2 的后续聚合器只需读取这个端口的不可变值；本端口不依赖或修改
 * {@code snapshot/} 聚合类型。</p>
 */
public interface ResonanceSnapshotPort {

    ResonanceStateSnapshot createSnapshot();

    void restore(ResonanceStateSnapshot snapshot);
}
