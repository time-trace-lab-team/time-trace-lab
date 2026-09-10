package org.example.timeloop.app;

import org.example.timeloop.level.Level01Footsteps;
import org.example.timeloop.level.LevelGeometryImpl;
import org.junit.jupiter.api.Test;

/**
 * 装配前置探针（开发一/集成）：实测第一关数据能否构造出 LevelGeometryImpl。
 * 仅打印结果，不判定失败，供装配前核查。
 */
class AssemblyProbeTest {

    @Test
    void reportLevel01GeometryConstruction() {
        String outcome;
        try {
            new LevelGeometryImpl(Level01Footsteps.build());
            outcome = "OK（可构造）";
        } catch (RuntimeException e) {
            outcome = "FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        System.out.println("[ASSEMBLY-PROBE] LevelGeometryImpl(Level01Footsteps.build()) -> " + outcome);
    }
}
