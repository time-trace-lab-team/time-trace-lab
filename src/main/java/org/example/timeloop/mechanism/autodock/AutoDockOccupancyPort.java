package org.example.timeloop.mechanism.autodock;

import org.example.timeloop.level.model.PathNode;
import org.example.timeloop.level.model.Vector2D;

public interface AutoDockOccupancyPort {

    AutoDockResult tryEnter(String mechanismId,
                             String actorId,
                             int sourceRound,
                             long tick,
                             Vector2D worldPosition);

    AutoDockResult tryLeave(String mechanismId,
                             String actorId,
                             int sourceRound,
                             long tick,
                             PathNode.Dir exitDirection,
                             Vector2D worldPosition);

    /** 释放指定残影在所有 dock 上的占用，返回实际释放数量。 */
    int releaseActor(String actorId, int sourceRound, long tick);

    /** 轮末、整局重开或场景退出的幂等清理。 */
    void reset(AutoDockResetReason reason, long tick);
}
