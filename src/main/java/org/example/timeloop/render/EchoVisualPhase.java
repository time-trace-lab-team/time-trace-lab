package org.example.timeloop.render;

/** 残影视觉生命周期；由上游权威寿命投影决定。 */
public enum EchoVisualPhase {
    ACTIVE,
    LAST_EFFECTIVE_ROUND,
    DISSIPATING,
    GONE
}
