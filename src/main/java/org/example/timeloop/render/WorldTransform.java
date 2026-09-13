package org.example.timeloop.render;

/**
 * 世界逻辑坐标 ↔ Canvas 屏幕坐标的纯逻辑变换（C5）。
 *
 * <p>不依赖任何 JavaFX 类型，可在无 Toolkit 的 JUnit 中直接测试。
 * 逻辑与碰撞永远使用世界坐标；本类只做显示投影，<b>不得</b>反向回写玩法坐标。</p>
 *
 * @param scale  缩放（像素 / 世界单位），必须为正有限数
 * @param originX 世界原点在画布上的 x 偏移
 * @param originY 世界原点在画布上的 y 偏移
 */
public record WorldTransform(double scale, double originX, double originY) {

    public WorldTransform {
        if (!Double.isFinite(scale) || scale <= 0.0) {
            throw new IllegalArgumentException("scale 必须为正有限数，实际 " + scale);
        }
        if (!Double.isFinite(originX) || !Double.isFinite(originY)) {
            throw new IllegalArgumentException("origin 必须为有限数");
        }
    }

    /** 1:1、无偏移的恒等变换。 */
    public static WorldTransform identity() {
        return new WorldTransform(1.0, 0.0, 0.0);
    }

    public double toCanvasX(double worldX) {
        return originX + worldX * scale;
    }

    public double toCanvasY(double worldY) {
        return originY + worldY * scale;
    }

    public double toWorldX(double canvasX) {
        return (canvasX - originX) / scale;
    }

    public double toWorldY(double canvasY) {
        return (canvasY - originY) / scale;
    }

    /** 把世界长度换算为画布长度（用于瓦片边长、角色半径等）。 */
    public double scaled(double worldLength) {
        return worldLength * scale;
    }

    /** 返回应用了偏移/缩放的新变换（例如摄像机平移）。 */
    public WorldTransform translated(double deltaOriginX, double deltaOriginY) {
        return new WorldTransform(scale, originX + deltaOriginX, originY + deltaOriginY);
    }
}
