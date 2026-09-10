package org.example.timeloop.mechanism.autodock;

/** 不可变的 autoDock 占用快照。 */
public record DockOccupancyView(boolean occupied,
                                String occupantId,
                                int occupantSourceRound,
                                long occupiedAtTick) {

    public DockOccupancyView {
        if (occupied) {
            if (occupantId == null || occupantId.isBlank()) {
                throw new IllegalArgumentException("占用快照缺少 occupantId");
            }
            if (occupantSourceRound < 0) {
                throw new IllegalArgumentException("占用快照的 sourceRound 不能为负数");
            }
            if (occupiedAtTick < 0) {
                throw new IllegalArgumentException("占用快照的 occupiedAtTick 不能为负数");
            }
        } else if (occupantId != null || occupantSourceRound != 0 || occupiedAtTick != -1) {
            throw new IllegalArgumentException("未占用快照不能带有占用者数据");
        }
    }

    public static DockOccupancyView empty() {
        return new DockOccupancyView(false, null, 0, -1);
    }
}
