package org.example.timeloop.replay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EchoLifetimeManagerTest {

    private EchoLifetimeManager manager;

    @BeforeEach
    void setUp() {
        manager = new EchoLifetimeManager();
        manager.initialize(60, 4, 2);  // D=60, maxRounds=4, L=2
    }

    @Test
    void testAddEchoAndAdvanceRounds() {
        // 第 1 轮结束，生成 E1
        manager.addEcho(1);
        manager.advanceRound(0);

        // 第 2 轮：E1 活跃
        manager.advanceRound(0);
        EchoLifetime e1 = manager.getEcho(1);
        assertNotNull(e1);
        assertTrue(e1.isActive());
        assertEquals(2, e1.getRemainingRounds());

        // 第 2 轮结束，生成 E2
        manager.addEcho(2);
        manager.advanceRound(0);

        // 第 3 轮：E1 + E2 都活跃
        EchoLifetime e1_3 = manager.getEcho(1);
        EchoLifetime e2_3 = manager.getEcho(2);
        assertTrue(e1_3.isActive());
        assertTrue(e2_3.isActive());
        assertEquals(1, e1_3.getRemainingRounds());
        assertEquals(2, e2_3.getRemainingRounds());
        assertTrue(e1_3.getBodyAlpha() < e2_3.getBodyAlpha());
    }

    @Test
    void testE1DisappearsBeforeE2() {
        // 第 1 轮结束，生成 E1
        manager.addEcho(1);
        manager.advanceRound(0);

        // 第 2 轮结束，生成 E2
        manager.addEcho(2);
        manager.advanceRound(0);

        // 第 3 轮结束，E1 应该消散（最后有效轮结束），E2 保留
        manager.advanceRound(0);

        // 第 4 轮：E1 已消散，E2 保留
        manager.advanceRound(0);

        assertFalse(manager.getEcho(1).isActive());
        assertTrue(manager.getEcho(2).isActive());
        assertEquals(1, manager.getEcho(2).getRemainingRounds());
    }

    @Test
    void testGetEchoesToDisappear() {
        manager.addEcho(1);
        manager.advanceRound(0);
        manager.addEcho(2);
        manager.advanceRound(0);

        // 第 3 轮：E1 在轮末应消散
        manager.advanceRound(0);
        var toDisappear = manager.getEchoesToDisappear();
        assertTrue(toDisappear.contains(1));
        assertFalse(toDisappear.contains(2));
    }

    @Test
    void testLifetimeAlphaChanges() {
        manager.addEcho(1);
        manager.advanceRound(0);

        // 第 2 轮，tick=0：新生成，透明度较高
        EchoLifetime e1 = manager.getEcho(1);
        assertEquals(0.0, e1.getLifeProgress(), 0.01);
        assertEquals(0.82, e1.getBodyAlpha(), 0.01);

        // 更新到 tick=30（一半位置）
        manager.updateRoundTick(30);
        e1 = manager.getEcho(1);
        assertEquals(0.5, e1.getLifeProgress(), 0.01);
        assertEquals(0.66, e1.getBodyAlpha(), 0.01);

        // 第 3 轮，tick=0：进入最后有效轮
        manager.advanceRound(0);
        e1 = manager.getEcho(1);
        assertEquals(0.5, e1.getLifeProgress(), 0.01);
    }
}