package org.example.timeloop.render;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.example.timeloop.core.ActorPhase;
import org.example.timeloop.core.AnimationState;
import org.example.timeloop.core.Direction;
import org.example.timeloop.core.MovementState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 精灵帧投影与图层的离屏 Canvas 回归。 */
class SpriteLayersTest {

    private static final int WIDTH = 240;
    private static final int HEIGHT = 160;

    @BeforeAll
    static void initializeJavafx() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // 同一测试 JVM 的其它 Canvas 测试已启动 Toolkit。
        }
    }

    @Test
    void playerProjectionCarriesExplicitAnimationAndCompatibilityConstructorStaysAvailable() {
        RenderViews.Player explicit = new RenderViews.Player(48.0, 48.0, Direction.RIGHT,
                MovementState.CRUISING, false, AnimationState.INTERACTING);
        RenderViews.Player compatible = new RenderViews.Player(48.0, 48.0, Direction.DOWN,
                MovementState.DOCKED, false);

        assertEquals(AnimationState.INTERACTING, explicit.animation(),
                "app 投影必须能把 PlayerFrame.animationState 原样带入 render");
        assertEquals(AnimationState.DOCKED, compatible.animation(),
                "既有五参调用点仍可构造，且停靠态采用保守兼容帧");
    }

    @Test
    void playerSpriteSelectsStructurallyDistinctFramesAndHonorsExplicitFallback() throws Exception {
        onFxThread(() -> {
            SpriteSheet sheet = testSheet();
            AtomicInteger reads = new AtomicInteger();
            AtomicReference<AnimationState> state = new AtomicReference<>(AnimationState.MOVING);
            SpriteLayer layer = new SpriteLayer(() -> {
                reads.incrementAndGet();
                return new PlayerSpriteVisual(new RenderViews.Player(80.0, 60.0, Direction.DOWN,
                        MovementState.CRUISING, false, state.get()), sheet, true);
            }, WorldTransform::identity);

            InkBounds moving = renderBounds(layer);
            state.set(AnimationState.DOCKED);
            InkBounds docked = renderBounds(layer);
            state.set(AnimationState.INTERACTING);
            InkBounds interacting = renderBounds(layer);

            assertNotEquals(moving, docked, "不同 AnimationState 必须稳定选中结构不同的图集帧");
            assertNotEquals(docked, interacting, "交互帧不能退化为停靠帧");
            assertEquals(3, reads.get(), "每次 render 仅读取一次玩家精灵投影");

            SpriteLayer fallback = new SpriteLayer(() -> new PlayerSpriteVisual(
                    new RenderViews.Player(80.0, 60.0, Direction.DOWN, MovementState.CRUISING, false,
                            AnimationState.MOVING), sheet, false), WorldTransform::identity);
            assertFalse(renderBounds(fallback).painted(), "enabled=false 时精灵层不绘制，旧 PlayerLayer 可独立回退");
        });
    }

    @Test
    void echoSpritesAreDistinctBySourceRoundAndReprojectWithWorldTransform() throws Exception {
        onFxThread(() -> {
            SpriteSheet sheet = testSheet();
            AtomicReference<WorldTransform> transform = new AtomicReference<>(WorldTransform.identity());
            EchoSpriteLayer layer = new EchoSpriteLayer(() -> List.of(
                    echo(1, 48.0, 48.0, AnimationState.MOVING, sheet, true),
                    echo(2, 112.0, 48.0, AnimationState.DOCKED, sheet, true)), transform::get);

            InkBounds first = renderBounds(layer);
            assertTrue(first.painted(), "E1 与 E2 均应绘制精灵、代际外框和编号");
            assertTrue(first.width() > 70, "双残影按 sourceRound 分列，不能叠成一个不可辨实体");

            transform.set(new WorldTransform(0.5, 100.0, 20.0));
            InkBounds updated = renderBounds(layer);
            assertTrue(updated.minX() > first.minX(), "缩放后精灵必须跟随其权威世界坐标重新投影");
            assertNotEquals(first, updated, "缩放/平移后的精灵边界不应遗留在旧屏幕位置");
        });
    }

    private static EchoSpriteVisual echo(int sourceRound,
                                         double x,
                                         double y,
                                         AnimationState animation,
                                         SpriteSheet sheet,
                                         boolean enabled) {
        return new EchoSpriteVisual(new EchoActor(sourceRound, x, y, Direction.DOWN, animation,
                ActorPhase.AVAILABLE), sheet, enabled);
    }

    private static InkBounds renderBounds(RenderLayer layer) {
        Canvas canvas = new Canvas(WIDTH, HEIGHT);
        layer.render(canvas.getGraphicsContext2D(), WIDTH, HEIGHT, 0.0);
        return InkBounds.of(canvas.snapshot(null, null));
    }

    /** 人造图集只用于验证帧的结构选择：不依赖仓库中的任何 PNG 或具体像素颜色。 */
    private static SpriteSheet testSheet() {
        int cell = 16;
        WritableImage image = new WritableImage(cell * 3, cell * 4);
        PixelWriter writer = image.getPixelWriter();
        for (int row = 0; row < 4; row++) {
            paintMoving(writer, 0, row * cell, cell);
            paintDocked(writer, cell, row * cell, cell);
            paintInteracting(writer, cell * 2, row * cell, cell);
        }
        return new SpriteSheet(image, cell, cell, 16.0, 16.0,
                Map.of(Direction.UP, 0, Direction.DOWN, 1, Direction.LEFT, 2, Direction.RIGHT, 3),
                Map.of(AnimationState.MOVING, 0, AnimationState.DOCKED, 1, AnimationState.INTERACTING, 2));
    }

    private static void paintMoving(PixelWriter writer, int left, int top, int cell) {
        for (int y = 0; y < cell; y++) {
            for (int x = 6; x < 10; x++) {
                writer.setColor(left + x, top + y, Color.DARKSLATEBLUE);
            }
        }
    }

    private static void paintDocked(PixelWriter writer, int left, int top, int cell) {
        for (int y = 6; y < 10; y++) {
            for (int x = 0; x < cell; x++) {
                writer.setColor(left + x, top + y, Color.DARKORANGE);
            }
        }
    }

    private static void paintInteracting(PixelWriter writer, int left, int top, int cell) {
        for (int i = 0; i < cell; i++) {
            writer.setColor(left + i, top + i, Color.DARKCYAN);
            writer.setColor(left + cell - i - 1, top + i, Color.DARKCYAN);
        }
    }

    private static void onFxThread(FxAssertion assertion) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        Platform.runLater(() -> {
            try {
                assertion.run();
            } catch (Throwable throwable) {
                failure[0] = throwable;
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(10, TimeUnit.SECONDS), "精灵图层离屏渲染超时");
        if (failure[0] != null) {
            throw new AssertionError("精灵图层验证失败", failure[0]);
        }
    }

    @FunctionalInterface
    private interface FxAssertion {
        void run() throws Exception;
    }

    private record InkBounds(boolean painted, int minX, int minY, int maxX, int maxY, long fingerprint) {
        static InkBounds of(WritableImage image) {
            int minX = (int) image.getWidth();
            int minY = (int) image.getHeight();
            int maxX = -1;
            int maxY = -1;
            int background = image.getPixelReader().getArgb(0, 0);
            long fingerprint = 1L;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getPixelReader().getArgb(x, y);
                    fingerprint = fingerprint * 31 + argb;
                    if (argb != background) {
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                    }
                }
            }
            return maxX < 0 ? new InkBounds(false, 0, 0, 0, 0, fingerprint)
                    : new InkBounds(true, minX, minY, maxX, maxY, fingerprint);
        }

        int width() {
            return maxX - minX + 1;
        }
    }
}
