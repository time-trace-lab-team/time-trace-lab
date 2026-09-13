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

    /**
     * 将完整世界等比、居中地投影到给定视口。
     *
     * <p>四个尺寸均以各自坐标系的单位表示；返回的 {@code scale} 为“视口像素 / 世界单位”。
     * 本方法只计算显示投影，不会改变世界中的任何逻辑坐标。</p>
     *
     * @param worldWidth 世界宽度，必须为正有限数
     * @param worldHeight 世界高度，必须为正有限数
     * @param viewWidth 可绘制视口宽度，必须为正有限数
     * @param viewHeight 可绘制视口高度，必须为正有限数
     * @return 使整个世界位于视口内、未占用空间对称留边的不可变变换
     */
    public static WorldTransform fit(
            double worldWidth,
            double worldHeight,
            double viewWidth,
            double viewHeight) {
        requirePositiveFinite("worldWidth", worldWidth);
        requirePositiveFinite("worldHeight", worldHeight);
        requirePositiveFinite("viewWidth", viewWidth);
        requirePositiveFinite("viewHeight", viewHeight);

        double scale = Math.min(viewWidth / worldWidth, viewHeight / worldHeight);
        double originX = (viewWidth - worldWidth * scale) / 2.0;
        double originY = (viewHeight - worldHeight * scale) / 2.0;
        return new WorldTransform(scale, originX, originY);
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

    private static void requirePositiveFinite(String name, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " 必须为正有限数，实际 " + value);
        }
    }
}
