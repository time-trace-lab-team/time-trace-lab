package org.example.timeloop.level;

import org.example.timeloop.level.model.PathNode;
import org.example.timeloop.level.model.Vector2D;

import java.util.List;
import java.util.Set;

/**
 * 不可变关卡几何数据查询接口。
 * 由开发3提供，供开发1在C2恒速巡行中使用。
 *
 * 所有字段在关卡加载时冻结，运行时不可修改。
 */
public interface LevelGeometry {

    /**
     * 获取瓦片像素尺寸。
     * 所有关卡统一为 48.0。
     */
    double getTileSize();

    /**
     * 获取玩家出生位置（世界坐标）。
     */
    Vector2D getSpawnPosition();

    /**
     * 获取所有路径节点列表（不可修改）。
     */
    List<PathNode> getPathNodes();

    /**
     * 获取从指定节点出发的合法出口方向集合。
     */
    Set<PathNode.Dir> getValidExits(String nodeId);

    /**
     * 获取指定节点的默认出口方向。
     * 如果节点没有默认出口，返回 null。
     */
    PathNode.Dir getDefaultExit(String nodeId);

    /**
     * 查询从节点A到节点B是否可通行（有路径连接）。
     */
    boolean isConnected(String nodeIdA, String nodeIdB);

    /**
     * 查询指定世界坐标位置是否为墙体（不可通行）。
     */
    boolean isWall(Vector2D position);

    /**
     * 查询指定门ID是否处于关闭状态（不可通行）。
     */
    boolean isDoorClosed(String doorId);

    /**
     * 查询指定节点是否存在。
     */
    boolean hasNode(String nodeId);

    /**
     * 获取与指定节点相邻的所有节点ID列表。
     */
    Set<String> getNeighbors(String nodeId);
}