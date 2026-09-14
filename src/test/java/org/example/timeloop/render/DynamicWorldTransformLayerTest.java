package org.example.timeloop.render;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import org.example.timeloop.core.Direction;
import org.example.timeloop.core.MovementState;
import org.example.timeloop.level.model.TileType;
import org.example.timeloop.level.model.Vector2D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 动态世界变换的图层契约测试；使用离屏 Canvas，不启动游戏窗口。 */
class DynamicWorldTransformLayerTest {

    private static final WorldTransform FIRST = new WorldTransform(1.0, 0.0, 0.0);
    private static final WorldTransform SECOND = new WorldTransform(0.5, 20.0, 10.0);
    private static final Supplier<RenderViews.Frame> FRAME_SOURCE = () -> new RenderViews.Frame(
            new RenderViews.Player(48.0, 48.0, Direction.RIGHT, MovementState.CRUISING, false),
            List.of(new RenderViews.Mechanism("plate", 48.0, 48.0, RenderViews.MechanismKind.PLATE, true)),
            List.of(new RenderViews.EchoTrail(1, List.of(new Vector2D(0.0, 0.0), new Vector2D(48.0, 48.0)), true)));

    @BeforeAll
    static void initializeJavafx() {
        ensureFxStarted();
    }

    @Test
    void fixedTransformConstructorsRemainCompatible() {
        assertDoesNotThrow(() -> new GroundWallLayer(new TileType[][]{{TileType.FLOOR}}, 48.0, FIRST));
        assertDoesNotThrow(() -> new SpawnLayer(48.0, 48.0, 48.0, FIRST));
        assertDoesNotThrow(() -> new PathNodeHintLayer(FRAME_SOURCE,
                List.of(new RenderViews.PathNodeMarker("node", 48.0, 48.0)), 48.0, FIRST));
        assertDoesNotThrow(() -> new MechanismLayer(FRAME_SOURCE, 48.0, FIRST));
        assertDoesNotThrow(() -> new PlayerLayer(FRAME_SOURCE, 48.0, FIRST));
        assertDoesNotThrow(() -> new EchoTrailLayer(FRAME_SOURCE, FIRST));
        assertDoesNotThrow(() -> new RayLayer(FRAME_SOURCE, FIRST));
    }

    @Test
    void supplierConstructorsRejectNullSourceImmediately() {
        assertThrows(NullPointerException.class,
                () -> new GroundWallLayer(new TileType[][]{{TileType.FLOOR}}, 48.0, (Supplier<WorldTransform>) null));
        assertThrows(NullPointerException.class,
                () -> new SpawnLayer(48.0, 48.0, 48.0, (Supplier<WorldTransform>) null));
        assertThrows(NullPointerException.class,
                () -> new PathNodeHintLayer(FRAME_SOURCE, List.of(), 48.0, (Supplier<WorldTransform>) null));
        assertThrows(NullPointerException.class,
                () -> new MechanismLayer(FRAME_SOURCE, 48.0, (Supplier<WorldTransform>) null));
        assertThrows(NullPointerException.class,
                () -> new PlayerLayer(FRAME_SOURCE, 48.0, (Supplier<WorldTransform>) null));
        assertThrows(NullPointerException.class,
                () -> new EchoTrailLayer(FRAME_SOURCE, (Supplier<WorldTransform>) null));
        assertThrows(NullPointerException.class,
                () -> new RayLayer(FRAME_SOURCE, (Supplier<WorldTransform>) null));
    }

    @Test
    void everyLayerReadsDynamicTransformExactlyOncePerRender() throws Exception {
        onFxThread(graphics -> {
            for (LayerFactory factory : allLayerFactories()) {
                AtomicInteger reads = new AtomicInteger();
                Supplier<WorldTransform> source = () -> reads.getAndIncrement() == 0 ? FIRST : SECOND;
                RenderLayer layer = factory.create(source);

                layer.render(graphics, 96.0, 96.0, 0.0);
                layer.render(graphics, 96.0, 96.0, 0.0);

                assertEquals(2, reads.get(), factory.name() + " 必须每帧只读取一次 transformSource");
            }
        });
    }

    @Test
    void everyLayerRejectsNullTransformReturnedDuringRenderWithLayerContext() throws Exception {
        onFxThread(graphics -> {
            for (LayerFactory factory : allLayerFactories()) {
                NullPointerException exception = assertThrows(NullPointerException.class,
                        () -> factory.create(() -> null).render(graphics, 96.0, 96.0, 0.0));
                assertTrue(exception.getMessage().contains(factory.name()),
                        "空变换错误必须标明图层: " + factory.name());
            }
        });
    }

    private static List<LayerFactory> allLayerFactories() {
        return List.of(
                new LayerFactory("GroundWallLayer", source ->
                        new GroundWallLayer(new TileType[][]{{TileType.FLOOR}}, 48.0, source)),
                new LayerFactory("SpawnLayer", source -> new SpawnLayer(48.0, 48.0, 48.0, source)),
                new LayerFactory("PathNodeHintLayer", source -> new PathNodeHintLayer(FRAME_SOURCE,
                        List.of(new RenderViews.PathNodeMarker("node", 48.0, 48.0)), 48.0, source)),
                new LayerFactory("MechanismLayer", source -> new MechanismLayer(FRAME_SOURCE, 48.0, source)),
                new LayerFactory("PlayerLayer", source -> new PlayerLayer(FRAME_SOURCE, 48.0, source)),
                new LayerFactory("EchoTrailLayer", source -> new EchoTrailLayer(FRAME_SOURCE, source)),
                new LayerFactory("RayLayer", source -> new RayLayer(FRAME_SOURCE, source)));
    }

    private static void onFxThread(FxAssertion assertion) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        Platform.runLater(() -> {
            try {
                Canvas canvas = new Canvas(96.0, 96.0);
                assertion.run(canvas.getGraphicsContext2D());
            } catch (Throwable throwable) {
                failure[0] = throwable;
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(10, TimeUnit.SECONDS), "JavaFX 离屏 Canvas 渲染超时");
        if (failure[0] != null) {
            throw new AssertionError("动态变换图层契约失败", failure[0]);
        }
    }

    private static void ensureFxStarted() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // 同一测试 JVM 中已初始化 Toolkit，直接复用。
        }
    }

    private record LayerFactory(String name, java.util.function.Function<Supplier<WorldTransform>, RenderLayer> creator) {
        private RenderLayer create(Supplier<WorldTransform> source) {
            return creator.apply(source);
        }
    }

    @FunctionalInterface
    private interface FxAssertion {
        void run(GraphicsContext graphics) throws Exception;
    }
}
