package org.example.timeloop.render;

import org.example.timeloop.core.Direction;
import org.example.timeloop.core.MovementState;
import org.example.timeloop.level.model.Vector2D;

import java.util.Comparator;
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
        /**
         * {@code dock_plate} 的开关表现变体（关卡数据 {@code role=switch}），不是独立机关类型；
         * README §五 / §七 的机关范围不因此变更。
         */
        SWITCH,
        DOOR,
        EXIT
    }

    /** 一帧的世界状态投影。 */
    public record Frame(Player player,
                        List<Mechanism> mechanisms,
                        List<EchoTrail> echoes,
                        List<RayBeam> rays) {

        public Frame {
            Objects.requireNonNull(player, "player");
            mechanisms = List.copyOf(Objects.requireNonNull(mechanisms, "mechanisms"));
            echoes = List.copyOf(Objects.requireNonNull(echoes, "echoes"));
            rays = List.copyOf(Objects.requireNonNull(rays, "rays")).stream()
                    .sorted(Comparator.comparing(RayBeam::id))
                    .toList();
        }

        public Frame(Player player, List<Mechanism> mechanisms, List<EchoTrail> echoes) {
            this(player, mechanisms, echoes, List.of());
        }

        public static Frame empty() {
            return new Frame(new Player(0, 0, Direction.DOWN, MovementState.CRUISING, false),
                    List.of(), List.of(), List.of());
        }
    }

    /** 射线的可视状态；与机制层状态隔离，由 app 侧负责映射。 */
    public enum RayVisualState {
        OFF,
        WARNING,
        ACTIVE
    }

    /** 射线的一个只读渲染投影。坐标一律为世界坐标。 */
    public record RayBeam(String id,
                          double startX,
                          double startY,
                          double endX,
                          double endY,
                          RayVisualState state) {
        public RayBeam {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("ray beam id 不能为空白");
            }
            if (!Double.isFinite(startX) || !Double.isFinite(startY)
                    || !Double.isFinite(endX) || !Double.isFinite(endY)) {
                throw new IllegalArgumentException("ray beam 坐标必须为有限数");
            }
            Objects.requireNonNull(state, "state");
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

    /**
     * 驻留板 / 开关表现变体 / 门 / 出口终端。
     *
     * <p>{@code tag} 与 {@code gateGroup} 是第二关「闸链」改版的<b>纯加法</b>分量：用简单符号把
     * 「板 ↔ 它作用的那扇门」关联起来（开门组板与门共用数字），并让作用于终点闸的板换成与终点闸
     * 同族的琥珀色系。两者都不参与任何玩法判定，只影响显示。</p>
     *
     * @param id        稳定机制 ID
     * @param x         世界坐标 x
     * @param y         世界坐标 y
     * @param kind      机关类别（决定画法与外形）
     * @param active    {@code PLATE}=是否被占；{@code SWITCH}=本轮是否已锁存；{@code DOOR}/{@code EXIT}=是否已解锁
     * @param tag       画在图标上的数字角标；{@code null} = 不画（例如终点闸组的板与出口）
     * @param gateGroup {@code true} = 终点闸组（用 {@code RenderPalette.INTERACTIVE} 琥珀色系画板）
     */
    public record Mechanism(String id,
                            double x,
                            double y,
                            MechanismKind kind,
                            boolean active,
                            String tag,
                            boolean gateGroup) {

        /** 兼容构造器：既有调用点（第一关、既有离屏测试）不受影响，等价于「无角标、非终点闸组」。 */
        public Mechanism(String id, double x, double y, MechanismKind kind, boolean active) {
            this(id, x, y, kind, active, null, false);
        }

        public Mechanism {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("mechanism id 不能为空白");
            }
            Objects.requireNonNull(kind, "kind");
            if (tag != null && tag.isBlank()) {
                throw new IllegalArgumentException("mechanism tag 不能为空白字符串（不需要就传 null）");
            }
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
