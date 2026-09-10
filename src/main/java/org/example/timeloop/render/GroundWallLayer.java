package org.example.timeloop.render;

import javafx.scene.canvas.GraphicsContext;
import org.example.timeloop.level.model.TileType;

/**
 * C5 地面与墙体图层。
 *
 * <p>只绘制连续地面与几何墙体色块（顶面 + 侧向偏移阴影），<b>不画全屏格线</b>；
 * 仅绘制与非墙相邻的可见墙面，避免整块边界墙堆叠。渲染只读，不改变碰撞坐标。</p>
 */
public final class GroundWallLayer implements RenderLayer {

    private final TileType[][] grid;
    private final double tileSize;
    private final WorldTransform transform;

    public GroundWallLayer(TileType[][] grid, double tileSize, WorldTransform transform) {
        this.grid = copyGrid(grid);
        this.tileSize = tileSize;
        this.transform = transform;
    }

    @Override
    public void render(GraphicsContext gc, double worldW, double worldH, double alpha) {
        double cell = transform.scaled(tileSize);
        int rows = grid.length;

        gc.setGlobalAlpha(1.0);
        gc.setFill(RenderPalette.FLOOR);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < grid[r].length; c++) {
                if (isWalkable(grid[r][c])) {
                    gc.fillRect(x(c), y(r), cell, cell);
                }
            }
        }

        double offset = RenderPalette.WALL_SIDE_OFFSET_PX;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < grid[r].length; c++) {
                if (grid[r][c] != TileType.WALL || !isVisibleWall(c, r)) {
                    continue;
                }
                gc.setFill(RenderPalette.WALL_SIDE);
                gc.fillRect(x(c) + offset, y(r) + offset, cell, cell);
                gc.setFill(RenderPalette.WALL_TOP);
                gc.fillRect(x(c), y(r), cell, cell);
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

    private double x(int col) {
        return transform.toCanvasX(col * tileSize);
    }

    private double y(int row) {
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
