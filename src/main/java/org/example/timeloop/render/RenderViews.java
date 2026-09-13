package org.example.timeloop.render;

import org.example.timeloop.core.Direction;
import org.example.timeloop.core.MovementState;
import org.example.timeloop.level.model.Vector2D;

import java.util.List;
import java.util.Objects;

/**
 * C5 只读渲染视图（开发一定义的渲染侧契约）。
 *
 * <p>所有类型不可变，只承载显示所需数据；渲染层不得通过它们回写玩法状态。
 * 集成层负责把玩家/机关/残影的权威状态投影成这些视图。</p>
 */
public final class RenderViews {

    private RenderViews() {
    }

    /** 机关类别。 */
    public enum MechanismKind {
        PLATE,
        DOOR,
        EXIT
    }

    /** 一帧的世界状态投影。 */
    public record Frame(Player player,
                        List<Mechanism> mechanisms,
                        List<EchoTrail> echoes) {

        public Frame {
            Objects.requireNonNull(player, "player");
            mechanisms = List.copyOf(Objects.requireNonNull(mechanisms, "mechanisms"));
            echoes = List.copyOf(Objects.requireNonNull(echoes, "echoes"));
        }

        public static Frame empty() {
            return new Frame(new Player(0, 0, Direction.DOWN, MovementState.CRUISING, false),
                    List.of(), List.of());
        }
    }

    /** 当前玩家。 */
    public record Player(double x,
                         double y,
                         Direction direction,
                         MovementState movementState,
                         boolean phased) {
        public Player {
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(movementState, "movementState");
        }
    }

    /** 关卡路径图投影出的静态节点中心；不携带玩法状态。 */
    public record PathNodeMarker(String id, double x, double y) {
        public PathNodeMarker {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("path node marker id 不能为空白");
            }
            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                throw new IllegalArgumentException("path node marker 坐标必须为有限数");
            }
        }
    }

    /** 驻留板 / 门 / 出口终端。 */
    public record Mechanism(String id,
                            double x,
                            double y,
                            MechanismKind kind,
                            boolean active) {
        public Mechanism {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("mechanism id 不能为空白");
            }
            Objects.requireNonNull(kind, "kind");
        }
    }

    /** 一条残影轨迹。 */
    public record EchoTrail(int sourceRound,
                            List<Vector2D> points,
                            boolean newer) {
        public EchoTrail {
            if (sourceRound < 1) {
                throw new IllegalArgumentException("sourceRound 必须 >= 1");
            }
            points = List.copyOf(Objects.requireNonNull(points, "points"));
        }

        /** 两条轨迹重叠时的平行错位像素量（按代际区分）。 */
        public double overlapOffsetPx() {
            return newer ? 2.0 : -2.0;
        }
    }
}
