package org.example.timeloop.app;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.example.timeloop.core.FixedStepLoop;
import org.example.timeloop.core.GamePhase;
import org.example.timeloop.level.Level03Footsteps;
import org.example.timeloop.level.model.LevelData;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.example.timeloop.replay.EchoLifetime;
import org.example.timeloop.replay.EchoLifetimeManager;
import org.example.timeloop.render.CanvasAdapter;
import org.example.timeloop.ui.LifetimeUI;

public final class TimeTraceLabApplication extends Application {

    private static final double TILE_SIZE = 48.0;
    private static final int WORLD_COLUMNS = 20;
    private static final int WORLD_ROWS = 12;
    private static final double WORLD_WIDTH = WORLD_COLUMNS * TILE_SIZE;
    private static final double WORLD_HEIGHT = WORLD_ROWS * TILE_SIZE;

    private final FixedStepLoop loop = new FixedStepLoop(() -> {});
    private GamePhase gamePhase = GamePhase.BOOT;
    private AnimationTimer animationTimer;

    private EchoLifetimeManager lifetimeManager;
    private LevelData levelData;
    private long roundTick = 0;
    private int currentRound = 0;
    private int maxRounds = 4;
    private long durationTicks = 1080;
    private boolean simulationComplete = false;
    private boolean isFirstFrame = true;

    @Override
    public void start(Stage stage) {
        EventDispatcher.getInstance().clear();

        levelData = Level03Footsteps.create();
        durationTicks = levelData.getDurationTicks();
        maxRounds = levelData.getMaxRounds();

        System.out.println("=== 第3关加载完成 ===");
        System.out.println("轮长: " + durationTicks + " tick");
        System.out.println("最大轮数: " + maxRounds);
        System.out.println("残影寿命 L: " + levelData.getEchoLifeL());

        lifetimeManager = new EchoLifetimeManager();
        lifetimeManager.initialize(durationTicks, maxRounds, levelData.getEchoLifeL());

        Canvas canvas = new Canvas(WORLD_WIDTH, WORLD_HEIGHT);
        Pane root = new Pane(canvas);
        canvas.widthProperty().bind(root.widthProperty());
        canvas.heightProperty().bind(root.heightProperty());

        CanvasAdapter canvasAdapter = new CanvasAdapter(canvas);
        Scene scene = new Scene(root, WORLD_WIDTH, WORLD_HEIGHT);

        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long nanoTime) {
                loop.onAnimationFrame(nanoTime, gamePhase);

                if (!simulationComplete) {
                    runSimulationStep();
                }

                canvasAdapter.renderFrame(WORLD_WIDTH, WORLD_HEIGHT, loop.interpolationAlpha());
            }
        };

        stage.setTitle("时痕实验室：昨日的我 - 第3关 寿命教学");
        stage.setScene(scene);
        stage.show();

        gamePhase = GamePhase.PLAYING;
        animationTimer.start();
    }

    private void runSimulationStep() {
        if (isFirstFrame) {
            currentRound = 1;
            lifetimeManager.startRound();
            isFirstFrame = false;
            return;
        }

        roundTick++;
        lifetimeManager.updateRoundTick(roundTick);

        if (roundTick >= durationTicks) {
            lifetimeManager.endRound(roundTick);

            if (currentRound < maxRounds) {
                currentRound++;
                lifetimeManager.startRound();
            } else {
                System.out.println("\n=== 所有轮次结束 ===");
                simulationComplete = true;
                printFinalState();
                gamePhase = GamePhase.PAUSED;
                animationTimer.stop();
                return;
            }

            roundTick = 0;
        }

        if (roundTick % 60 == 0 && roundTick > 0) {
            printCurrentState();
        }
    }

    private void printCurrentState() {
        var activeEchoes = lifetimeManager.getAllActiveEchoes();
        if (activeEchoes.isEmpty()) {
            return;
        }

        System.out.println("\n--- 当前状态 (轮 " + currentRound + ", tick " + roundTick + ") ---");
        for (EchoLifetime echo : activeEchoes) {
            String status = LifetimeUI.getEchoStatusText(echo);
            String pathType = LifetimeUI.shouldUseDashedPath(echo) ? "虚线" : "实线";
            String outerRing = LifetimeUI.shouldShowOuterRing(echo) ? "外环" : "无外环";
            System.out.printf("  %s | 路径: %s | 标记: %s%n", status, pathType, outerRing);
        }
    }

    private void printFinalState() {
        System.out.println("\n=== 最终状态 ===");
        for (int i = 1; i <= maxRounds; i++) {
            EchoLifetime echo = lifetimeManager.getEcho(i);
            if (echo == null) {
                System.out.println("E" + i + " 从未生成");
            } else if (echo.isActive()) {
                System.out.println("E" + i + " 仍活跃，剩余 " + echo.getRemainingRounds() + " 轮");
            } else {
                System.out.println("E" + i + " 已消散");
            }
        }
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