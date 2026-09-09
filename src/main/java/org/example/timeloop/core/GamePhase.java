package org.example.timeloop.core;

/**
 * C0 最小阶段集合。后续由项目经理/开发 2 扩展。
 * 规则：READY/PAUSED 不推进逻辑 tick。
 */
public enum GamePhase {
    BOOT,
    READY,
    PLAYING,
    PAUSED
}
