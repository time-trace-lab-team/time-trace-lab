package org.example.timeloop.render;

import javafx.scene.image.Image;
import org.example.timeloop.core.AnimationState;
import org.example.timeloop.core.Direction;

import java.util.Map;
import java.util.Objects;

/**
 * app 注入的精灵图集描述。
 *
 * <p>本类型只保存已经加载好的 {@link Image} 和帧布局；render 从不接受文件路径、URL 或资源名，
 * 因而不会在绘制过程中读取资源。每个方向/姿态都必须显式声明源格，避免 render 从 tick 推断帧序号。</p>
 */
public record SpriteSheet(Image image,
                          double sourceFrameWidth,
                          double sourceFrameHeight,
                          double worldWidth,
                          double worldHeight,
                          Map<Direction, Integer> rowByDirection,
                          Map<AnimationState, Integer> columnByAnimation) {

    public SpriteSheet {
        Objects.requireNonNull(image, "image");
        requirePositiveFinite("sourceFrameWidth", sourceFrameWidth);
        requirePositiveFinite("sourceFrameHeight", sourceFrameHeight);
        requirePositiveFinite("worldWidth", worldWidth);
        requirePositiveFinite("worldHeight", worldHeight);
        rowByDirection = checkedIndexes(rowByDirection, Direction.values(), "rowByDirection");
        columnByAnimation = checkedIndexes(columnByAnimation, AnimationState.values(), "columnByAnimation");
    }

    /** 返回由 app 声明的固定源矩形；没有任何时间或帧序号推算。 */
    public SourceFrame sourceFrame(Direction direction, AnimationState animation) {
        int row = rowByDirection.get(Objects.requireNonNull(direction, "direction"));
        int column = columnByAnimation.get(Objects.requireNonNull(animation, "animation"));
        return new SourceFrame(column * sourceFrameWidth, row * sourceFrameHeight,
                sourceFrameWidth, sourceFrameHeight);
    }

    /** 图集中一帧的源矩形，单位为图片像素。 */
    public record SourceFrame(double x, double y, double width, double height) {
    }

    private static <E extends Enum<E>> Map<E, Integer> checkedIndexes(Map<E, Integer> indexes,
                                                                         E[] required,
                                                                         String name) {
        Map<E, Integer> copy = Map.copyOf(Objects.requireNonNull(indexes, name));
        for (E value : required) {
            Integer index = copy.get(value);
            if (index == null || index < 0) {
                throw new IllegalArgumentException(name + " 必须为 " + value + " 提供非负索引");
            }
        }
        return copy;
    }

    private static void requirePositiveFinite(String name, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " 必须为正有限数");
        }
    }
}
