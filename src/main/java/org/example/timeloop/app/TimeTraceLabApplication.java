package org.example.timeloop.app;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.example.timeloop.core.FixedStepLoop;
import org.example.timeloop.core.input.LogicalKey;
import org.example.timeloop.level.model.LevelData;
import org.example.timeloop.level.model.TileType;
import org.example.timeloop.render.CanvasAdapter;
import org.example.timeloop.render.EchoTrailLayer;
import org.example.timeloop.render.GroundWallLayer;
import org.example.timeloop.render.MechanismLayer;
import org.example.timeloop.render.PathNodeHintLayer;
import org.example.timeloop.render.PlayerLayer;
import org.example.timeloop.render.RayLayer;
import org.example.timeloop.render.RenderViews;
import org.example.timeloop.render.SpawnLayer;
import org.example.timeloop.render.WorldTransform;
import org.example.timeloop.ui.SharedHud;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 关卡 JavaFX 集成装配（PM / 集成层）。
 *
 * <p>把「当前关卡」接到 {@link AnimationTimer} + {@link FixedStepLoop}：每帧按固定步长推进逻辑刻、
 * 把 {@code flow.renderViews()} 交给渲染图层、刷新共享 HUD，并在退出时清理机关。
 * **本类不拥有玩法逻辑**，只做接线。</p>
 *
 * <p><b>关卡切换</b>：当前关卡由 {@link LevelFlow} 持有。第一关<b>通关</b>
 * （{@link org.example.timeloop.core.GamePhase#RESULT}）后，{@link LevelFlow#switchToNextLevelIfCleared()}
 * 返回 true：先停掉第一关（停止推进 + 注销事件监听），再装配第二关，然后
 * {@link #installWorldView()} 按新关卡的数据<b>重建</b>画布与全部图层
 * （旧图层绑定的网格 / 出生点 / 节点提示 / {@code frames} 供应者一并丢弃，
 * 不会再有图层从旧关卡取帧）。第一关失败（{@code FAILED}，轮次耗尽）不触发切换。</p>
 *
 * <p>目标提示面板只有一块 {@link org.example.timeloop.ui.SharedHud} 自带的
 * {@code objectiveLabel}（由 {@code SharedHud.render(context, phase, String)} 写入），
 * 内容来自 {@link LevelFlow#objectiveText()}
 * —— 即<b>当前关卡自己的</b>只读目标投影（第一关 {@code ui.ObjectiveViewModel}、
 * 第二关 {@code ui.Level02ObjectiveViewModel}），切关后不会残留上一关的文本。</p>
 */
public final class TimeTraceLabApplication extends Application {

    /** 默认窗口尺寸；实际窗口不会超过屏幕可用区的 {@value #SMALL_SCREEN_FIT} 比例。 */
    private static final double DEFAULT_WINDOW_WIDTH = 1280.0;
    private static final double DEFAULT_WINDOW_HEIGHT = 720.0;
    /** 屏幕可用区不足默认窗口时按此比例收缩。 */
    private static final double SMALL_SCREEN_FIT = 0.9;
    /** 顶部 HUD 占掉的高度，不预留会把画布挤扁。 */
    private static final double HUD_HEIGHT = 40.0;
    /** 窗口标题前缀（关卡名由 {@link LevelFlow.LevelId#title()} 追加）。 */
    private static final String WINDOW_TITLE = "时痕实验室：昨日的我";

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

    /** 关卡流：持有当前关卡，并负责「通关后切到第二关」的唯一判定。 */
    private LevelFlow flow;
    private InputAccumulator input;
    private SharedHud hud;
    private FixedStepLoop loop;
    private AnimationTimer animationTimer;
    /** 终局弹窗接线（边沿触发 + 确认驱动切关）。 */
    private ResultDialogPresenter resultPresenter;
    private Stage stage;

    /** 画布容器（固定不变；切关时只替换里面的画布与图层）。 */
    private Pane canvasHolder;
    /** 当前关卡的渲染适配器与推导出的世界尺寸（{@link #installWorldView()} 每次重建时覆盖）。 */
    private CanvasAdapter canvasAdapter;
    private double worldWidth;
    private double worldHeight;

    @Override
    public void start(Stage stage) {
        this.stage = Objects.requireNonNull(stage, "stage");
        flow = new LevelFlow();
        input = new InputAccumulator();
        hud = new SharedHud();

        // 每逻辑刻：取走本刻输入 → 交给**当前关卡**推进。
        // 这里只引用 flow，不捕获任何具体装配：切关后不存在第二个推进源，
        // 第一关也不会再被 tick（LevelFlow 只委托当前关卡，且 L1 已 stop）。
        loop = new FixedStepLoop(() -> flow.tick(input.drain(flow.hudContext().roundTick())));

        HBox topBar = new HBox(hud);
        topBar.setSpacing(16.0);
        canvasHolder = new Pane();
        VBox root = new VBox(topBar, canvasHolder);
        // HUD 固定高度、画布吃掉剩余空间（HUD 不参与世界投影）
        VBox.setVgrow(canvasHolder, Priority.ALWAYS);

        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        double sceneWidth = Math.min(DEFAULT_WINDOW_WIDTH, visualBounds.getWidth() * SMALL_SCREEN_FIT);
        double sceneHeight = Math.min(DEFAULT_WINDOW_HEIGHT, visualBounds.getHeight() * SMALL_SCREEN_FIT);
        canvasHolder.setPrefSize(sceneWidth, Math.max(1.0, sceneHeight - HUD_HEIGHT));
        StackPane sceneRoot = new StackPane(root);
        resultPresenter = new ResultDialogPresenter();
        sceneRoot.getChildren().add(resultPresenter.node());
        Scene scene = new Scene(sceneRoot, sceneWidth, sceneHeight);

        // 画布与图层按「当前关卡」（第一关）装配；切关时原地重建。
        installWorldView();

        scene.setOnKeyPressed(event -> {
            // 终局阶段（挑战失败 / 通关完成）不再接受 gameplay 输入。
            // 必须给一个明确的重开入口，否则玩家会停在"角色不能动、也没有下一步"的死画面里。
            if (flow.isFinalPhase() && isRestartKey(event.getCode())) {
                input.releaseAll();
                boolean enterNext = resultPresenter.confirmEntersNextLevel();
                resultPresenter.hide();
                if (enterNext && flow.switchToNextLevelIfCleared()) {
                    installWorldView();
                    stage.setTitle(WINDOW_TITLE + " — " + flow.activeLevel().title());
                } else {
                    flow.restart();
                }
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
                loop.onAnimationFrame(nanoTime, flow.phase());
                // 第一关通关（RESULT）在此切到第二关并重建世界视图。
                // FAILED（轮次耗尽）返回 false：玩家留在第一关，重开/返回行为与之前完全一致。
                // 有弹窗待玩家确认时先不自动切关，否则结算界面会被瞬间顶掉。
                if (!resultPresenter.isShown() && flow.switchToNextLevelIfCleared()) {
                    input.releaseAll();
                    installWorldView();
                    stage.setTitle(WINDOW_TITLE + " — " + flow.activeLevel().title());
                }
                resultPresenter.onFrame(flow);
                canvasAdapter.renderFrame(worldWidth, worldHeight, loop.interpolationAlpha());
                // 目标提示随当前关卡换主人：文本由 LevelFlow 从当前关卡的只读投影取，
                // 写进 SharedHud 自带的那一块 objectiveLabel（不再另起第二块 Label）。
                hud.render(flow.hudContext(), flow.phase(), flow.objectiveText());
            }
        };

        stage.setTitle(WINDOW_TITLE + " — " + flow.activeLevel().title());
        stage.setScene(scene);
        stage.show();

        flow.start();
        animationTimer.start();
    }

    /**
     * 按「当前关卡」的数据重建画布与全部图层（启动时与切关时复用同一条路径）。
     *
     * <p>图层必须重建而不是复用：{@code GroundWallLayer} / {@code SpawnLayer} /
     * {@code PathNodeHintLayer} 在构造时就把网格、出生点、节点标记固化下来，
     * 而 {@code frames} 供应者必须指向 {@code flow}（永远取当前关卡的帧），
     * 不能继续绑定已卸载的旧装配。</p>
     */
    private void installWorldView() {
        LevelData level = flow.activeLevelData();
        double tileSize = level.getTileSize();
        TileType[][] grid = level.getTileGrid();
        int rows = grid.length;
        int cols = rows == 0 ? 0 : grid[0].length;
        if (rows <= 0 || cols <= 0) {
            throw new IllegalStateException("关卡网格为空，无法推导世界尺寸");
        }
        worldWidth = cols * tileSize;
        worldHeight = rows * tileSize;

        Canvas canvas = new Canvas(worldWidth, worldHeight);
        canvasAdapter = new CanvasAdapter(canvas);

        // 布局完成前视口尺寸未知，先用恒等变换占位；下方监听器会在拿到真实视口后立即重算。
        ObjectProperty<WorldTransform> transform =
                new SimpleObjectProperty<>(WorldTransform.identity(), "worldTransform");
        Supplier<WorldTransform> transformSource = transform::get;
        // 永远向「当前关卡」取帧（切关后自动指向新装配，不需要重新绑定）。
        Supplier<RenderViews.Frame> frames = flow::renderViews;

        canvasAdapter.addLayer(new GroundWallLayer(grid, tileSize, transformSource));
        // 原生 SPAWN_POINT 瓦片在画面上与普通地板毫无区别，靠这一层补一个金色标记
        canvasAdapter.addLayer(new SpawnLayer(
                level.getSpawnPos().x(), level.getSpawnPos().y(), tileSize, transformSource));
        // 路径节点菱形提示（项目方要求保留）：档位已收紧到「自己这格 + 上下左右紧邻格」，
        // 并放在机关与玩家之下，避免遮挡角色、机关与终点信号。
        canvasAdapter.addLayer(new PathNodeHintLayer(
                frames, flow.pathNodeMarkers(), tileSize, transformSource));
        canvasAdapter.addLayer(new MechanismLayer(frames, tileSize, transformSource));
        // B3-3：射线必须在地图/机关之上（光束可见），且在玩家之下（ACTIVE 光晕不得盖住玩家与 PHASED 外观）。
        canvasAdapter.addLayer(new RayLayer(frames, transformSource));
        canvasAdapter.addLayer(new PlayerLayer(frames, tileSize, transformSource));
        canvasAdapter.addLayer(new EchoTrailLayer(frames, transformSource));

        // 画布跟随容器尺寸；逻辑坐标仍是世界坐标，只有显示投影变化。
        canvas.widthProperty().bind(canvasHolder.widthProperty());
        canvas.heightProperty().bind(canvasHolder.heightProperty());

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

        canvasHolder.getChildren().setAll(canvas);
        refit.run();
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
        if (flow != null) {
            // 停止 + 释放当前关卡的机关与事件监听（切换关卡时旧关已在此前卸载过）。
            flow.cleanup();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
