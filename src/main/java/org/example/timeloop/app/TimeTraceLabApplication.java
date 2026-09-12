package org.example.timeloop.app;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.example.timeloop.core.FixedStepLoop;
import org.example.timeloop.core.GamePhase;
import org.example.timeloop.core.input.LogicalKey;
import org.example.timeloop.render.CanvasAdapter;
import org.example.timeloop.render.EchoTrailLayer;
import org.example.timeloop.render.GroundWallLayer;
import org.example.timeloop.render.MechanismLayer;
import org.example.timeloop.render.PathNodeHintLayer;
import org.example.timeloop.render.PlayerLayer;
import org.example.timeloop.render.RenderViews;
import org.example.timeloop.render.WorldTransform;
import org.example.timeloop.ui.SharedHud;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 第一关 MVP JavaFX 集成装配（PM / 集成层）。
 *
 * <p>把 {@link Level01Assembly} 接到 {@link AnimationTimer} + {@link FixedStepLoop}：
 * 每帧按固定步长推进逻辑刻、把 {@code assembly.renderViews()} 交给渲染图层、刷新共享 HUD，
 * 并在退出时清理机关与全局注册表。**本类不拥有玩法逻辑**，只做接线。</p>
 */
public final class TimeTraceLabApplication extends Application {

    private static final double WORLD_WIDTH = 11 * 48.0;
    private static final double WORLD_HEIGHT = 9 * 48.0;

    private static final Map<KeyCode, LogicalKey> KEY_MAP = Map.ofEntries(
            Map.entry(KeyCode.W, LogicalKey.DIR_UP),
            Map.entry(KeyCode.UP, LogicalKey.DIR_UP),
            Map.entry(KeyCode.S, LogicalKey.DIR_DOWN),
            Map.entry(KeyCode.DOWN, LogicalKey.DIR_DOWN),
            Map.entry(KeyCode.A, LogicalKey.DIR_LEFT),
            Map.entry(KeyCode.LEFT, LogicalKey.DIR_LEFT),
            Map.entry(KeyCode.D, LogicalKey.DIR_RIGHT),
            Map.entry(KeyCode.RIGHT, LogicalKey.DIR_RIGHT),
            Map.entry(KeyCode.E, LogicalKey.INTERACT),
            Map.entry(KeyCode.SPACE, LogicalKey.PHASE));

    private Level01Assembly assembly;
    private InputAccumulator input;
    private SharedHud hud;
    private FixedStepLoop loop;
    private AnimationTimer animationTimer;

    @Override
    public void start(Stage stage) {
        assembly = new Level01Assembly();
        input = new InputAccumulator();
        hud = new SharedHud();

        // 每逻辑刻：取走本刻输入 → 交给装配推进。
        loop = new FixedStepLoop(() ->
                assembly.tick(input.drain(assembly.hudContext().roundTick())));

        Canvas canvas = new Canvas(WORLD_WIDTH, WORLD_HEIGHT);
        CanvasAdapter canvasAdapter = new CanvasAdapter(canvas);

        WorldTransform transform = WorldTransform.identity();
        Supplier<RenderViews.Frame> frames = assembly::renderViews;
        canvasAdapter.addLayer(new GroundWallLayer(
                org.example.timeloop.level.Level01Footsteps.build().getTileGrid(), 48.0, transform));
        canvasAdapter.addLayer(new MechanismLayer(frames, 48.0, transform));
        canvasAdapter.addLayer(new PlayerLayer(frames, 48.0, transform));
        canvasAdapter.addLayer(new EchoTrailLayer(frames, transform));
        // R-2：路径节点转向提示（静态几何来自装配层投影，逐帧只读玩家位置决定亮度档位）
        canvasAdapter.addLayer(new PathNodeHintLayer(frames, assembly.pathNodeMarkers(), 48.0, transform));

        Pane canvasHolder = new Pane(canvas);
        canvas.widthProperty().bind(canvasHolder.widthProperty());
        canvas.heightProperty().bind(canvasHolder.heightProperty());
        VBox root = new VBox(hud, canvasHolder);
        canvasHolder.setPrefSize(WORLD_WIDTH, WORLD_HEIGHT);
        Scene scene = new Scene(new StackPane(root), WORLD_WIDTH, WORLD_HEIGHT);

        scene.setOnKeyPressed(event -> {
            LogicalKey key = KEY_MAP.get(event.getCode());
            if (key != null) {
                // 操作系统自动重复不产生新边沿：InputAccumulator 只在首次按下时记录。
                input.onKeyPressed(key);
            }
        });
        scene.setOnKeyReleased(event -> {
            LogicalKey key = KEY_MAP.get(event.getCode());
            if (key != null) {
                input.onKeyReleased(key);
            }
        });
        stage.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) {
                input.releaseAll();
            }
        });

        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long nanoTime) {
                loop.onAnimationFrame(nanoTime, assembly.phase());
                canvasAdapter.renderFrame(WORLD_WIDTH, WORLD_HEIGHT, loop.interpolationAlpha());
                hud.render(assembly.hudContext());
            }
        };

        stage.setTitle("时痕实验室：昨日的我");
        stage.setScene(scene);
        stage.show();

        assembly.start();
        animationTimer.start();
    }

    @Override
    public void stop() {
        if (animationTimer != null) {
            animationTimer.stop();
        }
        if (assembly != null) {
            assembly.cleanup();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
