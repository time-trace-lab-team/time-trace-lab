package org.example.timeloop.render;

import javafx.scene.paint.Color;

/**
 * C5 渲染色板。
 *
 * <p>仅用于显示，不参与任何玩法判定。交互状态之间保持足够明度差，
 * 且关键状态不只用颜色表达。</p>
 */
public final class RenderPalette {

    /** 深色背景（径向渐变的外圈）。 */
    public static final Color BACKGROUND = Color.web("#04070c");
    /** 背景中心色（径向渐变的内圈）。 */
    public static final Color BACKGROUND_CENTER = Color.web("#0c1826");

    // ---------- 地面 ----------
    /** 连续地面（保留旧名，仍作基准色使用）。 */
    public static final Color FLOOR = Color.web("#1d2b3a");
    /** 逐格明暗变化用的 5 档地面色。 */
    public static final Color[] FLOOR_SHADES = {
            Color.web("#1d2b3a"),
            Color.web("#1f2e3e"),
            Color.web("#1b2937"),
            Color.web("#20303f"),
            Color.web("#1c2a39"),
    };
    /** 地砖缝隙（每格边框）。 */
    public static final Color FLOOR_SEAM = Color.web("#060c14", 0.90);
    /** 地砖内侧高光。 */
    public static final Color FLOOR_HIGHLIGHT = Color.web("#7da0c3", 0.09);

    // ---------- 墙体 ----------
    /** 建筑顶面（渐变上端）。 */
    public static final Color WALL_TOP = Color.web("#5a6d86");
    /** 建筑顶面（渐变下端）。 */
    public static final Color WALL_TOP_DARK = Color.web("#37475c");
    /** 建筑侧面/阴影。 */
    public static final Color WALL_SIDE = Color.web("#03060a", 0.60);
    /** 墙面朝向地面的白色描边。 */
    public static final Color WALL_OUTLINE = Color.web("#eef4ff");

    // ---------- 机关 ----------
    /** 出生点。 */
    public static final Color SPAWN = Color.web("#f2c53d");
    /** 出生点描边。 */
    public static final Color SPAWN_RING = Color.web("#fff4c9");
    /** 出生点轴心。 */
    public static final Color SPAWN_CORE = Color.web("#7d5f10");
    /** 驻留板。 */
    public static final Color PLATE = Color.web("#2f6fc4");
    /** 驻留板描边。 */
    public static final Color PLATE_EDGE = Color.web("#d3e6ff");
    /** 门。 */
    public static final Color DOOR = Color.web("#e8443f");
    /** 门描边。 */
    public static final Color DOOR_EDGE = Color.web("#ffd9d2");
    /** 未激活机关轮廓。 */
    public static final Color INACTIVE_OUTLINE = Color.web("#4a5266");

    // ---------- 角色 ----------
    /** 当前玩家。 */
    public static final Color PLAYER = Color.web("#F4E7C5");
    /** 新残影/来源色 A。 */
    public static final Color ECHO_NEW = Color.web("#65D6D2");
    /** 旧残影/来源色 B。 */
    public static final Color ECHO_OLD = Color.web("#A88BE8");
    /** 可交互/目标（终点终端）。 */
    public static final Color INTERACTIVE = Color.web("#e6b85c");
    /** 相位下潜。 */
    public static final Color PHASE = Color.web("#7ADDE8");
    /** 正文与 HUD。 */
    public static final Color TEXT = Color.web("#D9DFEA");

    /** 墙面侧面相对顶面的像素偏移。 */
    public static final double WALL_SIDE_OFFSET_PX = 8.0;

    private RenderPalette() {
    }
}
