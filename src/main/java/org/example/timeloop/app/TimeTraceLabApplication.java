package org.example.timeloop.app;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.example.timeloop.core.FixedStepClock;
import org.example.timeloop.core.GamePhase;
import org.example.timeloop.render.CanvasAdapter;

/**
 * C1 使用的最小 JavaFX 启动壳。
 *
 * <p>这里只负责 Stage、Scene、Canvas 和 AnimationTimer 的装配，不包含菜单、
 * 页面切换、关卡选择或具体游戏逻辑。</p>
 */
public final class TimeTraceLabApplication extends Application {

    private static final double TILE_SIZE = 48.0;
    private static final int WORLD_COLUMNS = 20;
    private static final int WORLD_ROWS = 12;
    private static final double WORLD_WIDTH = WORLD_COLUMNS * TILE_SIZE;
    private static final double WORLD_HEIGHT = WORLD_ROWS * TILE_SIZE;

    private final FixedStepClock fixedStepClock = new FixedStepClock();
    private GamePhase gamePhase = GamePhase.BOOT;
    private AnimationTimer animationTimer;

    @Override
    public void start(Stage stage) {
        Canvas canvas = new Canvas(WORLD_WIDTH, WORLD_HEIGHT);
        Pane root = new Pane(canvas);
        canvas.widthProperty().bind(root.widthProperty());
        canvas.heightProperty().bind(root.heightProperty());

        CanvasAdapter canvasAdapter = new CanvasAdapter(canvas);
        Scene scene = new Scene(root, WORLD_WIDTH, WORLD_HEIGHT);

        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long nanoTime) {
                fixedStepClock.advance(nanoTime, gamePhase);
                canvasAdapter.renderFrame(
                        WORLD_WIDTH,
                        WORLD_HEIGHT,
                        fixedStepClock.getInterpolationAlpha());
            }
        };

        stage.setTitle("时痕实验室：昨日的我");
        stage.setScene(scene);
        stage.show();

        // 当前空壳没有菜单/READY 交互，直接进入 C1 灰盒运行阶段。
        gamePhase = GamePhase.PLAYING;
        animationTimer.start();
    }

    @Override
    public void stop() {
        gamePhase = GamePhase.PAUSED;
        if (animationTimer != null) {
            animationTimer.stop();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
