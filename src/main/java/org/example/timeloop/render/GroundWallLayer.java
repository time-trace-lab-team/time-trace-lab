package org.example.timeloop.render;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import org.example.timeloop.level.model.TileType;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * C5 地面与墙体图层。
 *
 * <p>地面逐格铺贴：每格按坐标取一档明暗，格内加一圈内高光，格边压深色缝隙。
 * 墙体先向右下偏移投一层阴影，再画顶面渐变，最后只在朝向地面的那一侧描白边。
 * 渲染只读，不改变碰撞坐标。</p>
 */
public final class GroundWallLayer implements RenderLayer {

    private final TileType[][] grid;
    private final double tileSize;
    private final Supplier<WorldTransform> transformSource;

    public GroundWallLayer(TileType[][] grid, double tileSize, WorldTransform transform) {
        this(grid, tileSize, fixedTransform(transform));
    }

    public GroundWallLayer(TileType[][] grid, double tileSize, Supplier<WorldTransform> transformSource) {
        this.grid = copyGrid(grid);
        this.tileSize = tileSize;
        this.transformSource = Objects.requireNonNull(transformSource, "transformSource");
    }

    @Override
    public void render(GraphicsContext gc, double worldW, double worldH, double alpha) {
        WorldTransform transform = currentTransform();
        double cell = transform.scaled(tileSize);
        int rows = grid.length;

        // ---------- 地面 ----------
        gc.setGlobalAlpha(1.0);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < grid[r].length; c++) {
                if (!isWalkable(grid[r][c])) {
                    continue;
                }
                double x = x(c, transform), y = y(r, transform);
                gc.setFill(RenderPalette.FLOOR_SHADES[Math.floorMod(c * 7 + r * 13, 5)]);
                gc.fillRect(x, y, cell, cell);

                gc.setStroke(RenderPalette.FLOOR_SEAM);
                gc.setLineWidth(2.0);
                gc.strokeRect(x + 1, y + 1, cell - 2, cell - 2);

                gc.setStroke(RenderPalette.FLOOR_HIGHLIGHT);
                gc.setLineWidth(1.0);
                gc.strokeRect(x + 3.5, y + 3.5, cell - 7, cell - 7);
            }
        }

        // ---------- 墙体阴影 ----------
        double offset = RenderPalette.WALL_SIDE_OFFSET_PX;
        gc.save();
        gc.setGlobalAlpha(0.60);
        gc.setFill(RenderPalette.WALL_SIDE);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < grid[r].length; c++) {
                if (grid[r][c] == TileType.WALL && isVisibleWall(c, r)) {
                    gc.fillRect(x(c, transform) + offset, y(r, transform) + offset, cell, cell);
                }
            }
        }
        gc.restore();

        // ---------- 墙体顶面 ----------
        gc.setGlobalAlpha(1.0);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < grid[r].length; c++) {
                if (grid[r][c] != TileType.WALL || !isVisibleWall(c, r)) {
                    continue;
                }
                double x = x(c, transform), y = y(r, transform);
                gc.setFill(new LinearGradient(0, y, 0, y + cell, false, CycleMethod.NO_CYCLE,
                        new Stop(0, RenderPalette.WALL_TOP),
                        new Stop(1, RenderPalette.WALL_TOP_DARK)));
                gc.fillRect(x, y, cell, cell);
            }
        }

        // ---------- 朝向地面的白描边 ----------
        gc.setStroke(RenderPalette.WALL_OUTLINE);
        gc.setLineWidth(3.5);
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        double o = 2.0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < grid[r].length; c++) {
                if (grid[r][c] != TileType.WALL) {
                    continue;
                }
                double x = x(c, transform), y = y(r, transform);
                if (isWalkable(cellAt(c, r - 1))) {
                    gc.strokeLine(x + o, y + o, x + cell - o, y + o);
                }
                if (isWalkable(cellAt(c, r + 1))) {
                    gc.strokeLine(x + o, y + cell - o, x + cell - o, y + cell - o);
                }
                if (isWalkable(cellAt(c - 1, r))) {
                    gc.strokeLine(x + o, y + o, x + o, y + cell - o);
                }
                if (isWalkable(cellAt(c + 1, r))) {
                    gc.strokeLine(x + cell - o, y + o, x + cell - o, y + cell - o);
                }
            }
        }
    }

    private boolean isVisibleWall(int col, int row) {
        return isWalkable(cellAt(col, row - 1))
                || isWalkable(cellAt(col, row + 1))
                || isWalkable(cellAt(col - 1, row))
                || isWalkable(cellAt(col + 1, row));
    }

    private TileType cellAt(int col, int row) {
        if (row < 0 || row >= grid.length || col < 0 || col >= grid[row].length) {
            return TileType.WALL;
        }
        return grid[row][col];
    }

    private WorldTransform currentTransform() {
        return Objects.requireNonNull(transformSource.get(), "GroundWallLayer transformSource 在 render 时返回 null");
    }

    private static Supplier<WorldTransform> fixedTransform(WorldTransform transform) {
        WorldTransform fixed = Objects.requireNonNull(transform, "transform");
        return () -> fixed;
    }

    private double x(int col, WorldTransform transform) {
        return transform.toCanvasX(col * tileSize);
    }

    private double y(int row, WorldTransform transform) {
        return transform.toCanvasY(row * tileSize);
    }

    private static boolean isWalkable(TileType type) {
        return type == TileType.FLOOR || type == TileType.SPAWN_POINT;
    }

    private static TileType[][] copyGrid(TileType[][] source) {
        TileType[][] copy = new TileType[source.length][];
        for (int r = 0; r < source.length; r++) {
            copy[r] = source[r].clone();
        }
        return copy;
    }
}
