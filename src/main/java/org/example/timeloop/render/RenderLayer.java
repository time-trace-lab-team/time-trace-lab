package org.example.timeloop.render;

import javafx.scene.canvas.GraphicsContext;

/**
 * 渲染层契约。开发 1 负责实现，开发 2/3 通过只读数据驱动。
 * 禁止反向修改玩法状态。
 */
public interface RenderLayer {
    void render(GraphicsContext gc, double worldW, double worldH, double alpha);
}
