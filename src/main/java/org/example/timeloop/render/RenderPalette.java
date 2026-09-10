package org.example.timeloop.render;

import javafx.scene.paint.Color;

/**
 * C5 渲染色板（开发一实现，取自 README §六「建议色板」）。
 *
 * <p>仅用于显示，不参与任何玩法判定。交互状态之间保持足够明度差，
 * 且关键状态不只用颜色表达（残影另有实/虚线、编号）。</p>
 */
public final class RenderPalette {

    /** 深色背景。 */
    public static final Color BACKGROUND = Color.web("#0C1018");
    /** 连续地面（不画全屏格线）。 */
    public static final Color FLOOR = Color.web("#1B2130");
    /** 建筑顶面。 */
    public static final Color WALL_TOP = Color.web("#2A3040");
    /** 建筑侧面/阴影（4–8 像素偏移）。 */
    public static final Color WALL_SIDE = Color.web("#171C28");
    /** 当前玩家。 */
    public static final Color PLAYER = Color.web("#F4E7C5");
    /** 新残影/来源色 A。 */
    public static final Color ECHO_NEW = Color.web("#65D6D2");
    /** 旧残影/来源色 B。 */
    public static final Color ECHO_OLD = Color.web("#A88BE8");
    /** 可交互/目标。 */
    public static final Color INTERACTIVE = Color.web("#E6B85C");
    /** 相位下潜。 */
    public static final Color PHASE = Color.web("#7ADDE8");
    /** 正文与 HUD。 */
    public static final Color TEXT = Color.web("#D9DFEA");
    /** 未激活机关轮廓。 */
    public static final Color INACTIVE_OUTLINE = Color.web("#4A5266");

    /** 墙面侧面相对顶面的像素偏移。 */
    public static final double WALL_SIDE_OFFSET_PX = 6.0;

    private RenderPalette() {
    }
}
