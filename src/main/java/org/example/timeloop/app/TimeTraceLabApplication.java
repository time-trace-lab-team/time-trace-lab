package org.example.timeloop.app;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.example.timeloop.core.FixedStepLoop;
import org.example.timeloop.core.input.LogicalKey;
import org.example.timeloop.level.Level01Footsteps;
import org.example.timeloop.level.model.LevelData;
import org.example.timeloop.level.model.TileType;
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

    /** 默认窗口尺寸；实际窗口不会超过屏幕可用区的 {@value #SMALL_SCREEN_FIT} 比例。 */
    private static final double DEFAULT_WINDOW_WIDTH = 1280.0;
    private static final double DEFAULT_WINDOW_HEIGHT = 720.0;
    /** 屏幕可用区不足默认窗口时按此比例收缩。 */
    private static final double SMALL_SCREEN_FIT = 0.9;
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

        // 世界尺寸从关卡数据推导：网格改版（28×16）后这里不需要跟着改常量
        LevelData level = Level01Footsteps.build();
        double tileSize = level.getTileSize();
        TileType[][] grid = level.getTileGrid();
        int rows = grid.length;
        int cols = rows == 0 ? 0 : grid[0].length;
        if (rows <= 0 || cols <= 0) {
            throw new IllegalStateException("关卡网格为空，无法推导世界尺寸");
        }
        double worldWidth = cols * tileSize;
        double worldHeight = rows * tileSize;

        Canvas canvas = new Canvas(worldWidth, worldHeight);
        CanvasAdapter canvasAdapter = new CanvasAdapter(canvas);

        // 布局完成前视口尺寸未知，先用恒等变换占位；下方监听器会在拿到真实视口后立即重算。
        // 图层每帧只读一次该属性，因此窗口缩放与渲染之间不存在撕裂帧。
        ObjectProperty<WorldTransform> transform =
                new SimpleObjectProperty<>(WorldTransform.identity(), "worldTransform");
        Supplier<WorldTransform> transformSource = transform::get;
        Supplier<RenderViews.Frame> frames = assembly::renderViews;

        canvasAdapter.addLayer(new GroundWallLayer(grid, tileSize, transformSource));
        // 原生 SPAWN_POINT 瓦片在画面上与普通地板毫无区别，靠这一层补一个金色标记
        canvasAdapter.addLayer(new SpawnLayer(
                level.getSpawnPos().x(), level.getSpawnPos().y(), tileSize, transformSource));
        // 路径节点菱形提示（项目方要求保留）：档位已收紧到「自己这格 + 上下左右紧邻格」，
        // 并放在机关与玩家之下，避免遮挡角色、机关与终点信号。
        canvasAdapter.addLayer(new PathNodeHintLayer(
                frames, assembly.pathNodeMarkers(), tileSize, transformSource));
        canvasAdapter.addLayer(new MechanismLayer(frames, tileSize, transformSource));
        canvasAdapter.addLayer(new PlayerLayer(frames, tileSize, transformSource));
        canvasAdapter.addLayer(new EchoTrailLayer(frames, transformSource));

        Pane canvasHolder = new Pane(canvas);
        // 画布跟随容器尺寸；逻辑坐标仍是世界坐标，只有显示投影变化。
        canvas.widthProperty().bind(canvasHolder.widthProperty());
        canvas.heightProperty().bind(canvasHolder.heightProperty());
        VBox root = new VBox(hud, canvasHolder);
        // HUD 固定高度、画布吃掉剩余空间（HUD 不参与世界投影）
        VBox.setVgrow(canvasHolder, Priority.ALWAYS);

        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        double sceneWidth = Math.min(DEFAULT_WINDOW_WIDTH, visualBounds.getWidth() * SMALL_SCREEN_FIT);
        double sceneHeight = Math.min(DEFAULT_WINDOW_HEIGHT, visualBounds.getHeight() * SMALL_SCREEN_FIT);
        canvasHolder.setPrefSize(sceneWidth, Math.max(1.0, sceneHeight - HUD_HEIGHT));
        Scene scene = new Scene(new StackPane(root), sceneWidth, sceneHeight);

        // 视口一变就等比重投影：世界完整可见、居中留边；两个尺寸都有效时才计算，
        // 避免布局中间态（0 或负）触发 WorldTransform 的参数校验。
        Runnable refit = () -> {
            double viewWidth = canvas.getWidth();
            double viewHeight = canvas.getHeight();
            if (viewWidth > 0.0 && viewHeight > 0.0) {
                transform.set(WorldTransform.fit(worldWidth, worldHeight, viewWidth, viewHeight));
            }
        };
        canvas.widthProperty().addListener((obs, old, now) -> refit.run());
        canvas.heightProperty().addListener((obs, old, now) -> refit.run());

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
                canvasAdapter.renderFrame(worldWidth, worldHeight, loop.interpolationAlpha());
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
