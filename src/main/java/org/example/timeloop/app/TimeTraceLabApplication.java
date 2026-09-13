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
import org.example.timeloop.level.Level01Footsteps;
import org.example.timeloop.level.model.LevelData;
import org.example.timeloop.render.CanvasAdapter;
import org.example.timeloop.render.EchoTrailLayer;
import org.example.timeloop.render.GroundWallLayer;
import org.example.timeloop.render.MechanismLayer;
import org.example.timeloop.render.PathNodeHintLayer;
import org.example.timeloop.render.PlayerLayer;
import org.example.timeloop.render.RenderViews;
import org.example.timeloop.render.SpawnLayer;
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

    /** 第一关格子边长（世界单位），与关卡数据的 tileSize 一致。 */
    private static final double TILE_SIZE = 48.0;
    /** 第一关网格列数（28 列 × 16 行 = 1344 × 768 世界单位）。 */
    private static final int GRID_COLS = 28;
    /** 第一关网格行数。 */
    private static final int GRID_ROWS = 16;
    private static final double WORLD_WIDTH = GRID_COLS * TILE_SIZE;
    private static final double WORLD_HEIGHT = GRID_ROWS * TILE_SIZE;
    /** 顶部 HUD 占掉的高度，不预留会把画布挤扁。 */
    private static final double HUD_HEIGHT = 40.0;

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

        // 关卡只构建一次：图层和出生点标记共用同一份数据
        LevelData level = Level01Footsteps.build();
        canvasAdapter.addLayer(new GroundWallLayer(level.getTileGrid(), TILE_SIZE, transform));
        // 原生 SPAWN_POINT 瓦片在画面上与普通地板毫无区别，靠这一层补一个金色标记
        canvasAdapter.addLayer(new SpawnLayer(
                level.getSpawnPos().x(), level.getSpawnPos().y(), TILE_SIZE, transform));
        // 路径节点菱形提示（项目方要求保留）：档位已收紧到「自己这格 + 上下左右紧邻格」，
        // 并放在机关与玩家之下，避免遮挡角色、机关与终点信号。
        canvasAdapter.addLayer(new PathNodeHintLayer(
                frames, assembly.pathNodeMarkers(), TILE_SIZE, transform));
        canvasAdapter.addLayer(new MechanismLayer(frames, TILE_SIZE, transform));
        canvasAdapter.addLayer(new PlayerLayer(frames, TILE_SIZE, transform));
        canvasAdapter.addLayer(new EchoTrailLayer(frames, transform));

        Pane canvasHolder = new Pane(canvas);
        canvas.widthProperty().bind(canvasHolder.widthProperty());
        canvas.heightProperty().bind(canvasHolder.heightProperty());
        VBox root = new VBox(hud, canvasHolder);
        canvasHolder.setPrefSize(WORLD_WIDTH, WORLD_HEIGHT);
        Scene scene = new Scene(new StackPane(root), WORLD_WIDTH, WORLD_HEIGHT + HUD_HEIGHT);

        scene.setOnKeyPressed(event -> {
            // 终局阶段（挑战失败 / 通关完成）不再接受 gameplay 输入。
            // 必须给一个明确的重开入口，否则玩家会停在"角色不能动、也没有下一步"的死画面里。
            if (assembly.isFinalPhase() && isRestartKey(event.getCode())) {
                input.releaseAll();
                assembly.restart();
                return;
            }
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
                hud.render(assembly.hudContext(), assembly.phase(), assembly.objectiveView());
            }
        };

        stage.setTitle("时痕实验室：昨日的我");
        stage.setScene(scene);
        stage.show();

        assembly.start();
        animationTimer.start();
    }

    /** 终局阶段的重开键：R / Enter / Space。 */
    private static boolean isRestartKey(KeyCode code) {
        return code == KeyCode.R || code == KeyCode.ENTER || code == KeyCode.SPACE;
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
