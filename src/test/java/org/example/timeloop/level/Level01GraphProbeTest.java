package org.example.timeloop.level;

import org.example.timeloop.level.model.LevelData;
import org.example.timeloop.level.model.PathNode;
import org.example.timeloop.level.model.Vector2D;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 关卡一路径图的诊断探针：把新地图的真实图结构（度数分布、死路、路口、到关键机关的最短路）落成文本。
 *
 * <p>用途：测试迁移时用真实数据代替猜测 —— 死路/路口清单、到左右驻留板与终点的最短路与所需刻数，
 * 全部由 {@link Level01Footsteps#build()} 的节点数据推导，并顺带核对
 * 「每个 allowDir 都能落到真实节点且互为反向」这条 {@code LevelGeometryImpl} 规则。</p>
 *
 * <p>输出：{@code target/observations/level01-graph-probe.txt}</p>
 */
class Level01GraphProbeTest {

    private static final double TILE = 48.0;
    private static final String OUT_PATH = "target/observations/level01-graph-probe.txt";
    /** 速度 2.0 世界单位/刻 ⇒ 每格 24 刻。 */
    private static final int TICKS_PER_CELL = (int) (TILE / 2.0);

    @Test
    void probePathGraph() throws Exception {
        LevelData level = Level01Footsteps.build();

        Map<String, PathNode> byId = new LinkedHashMap<>();
        Map<String, String> idByPosition = new HashMap<>();
        for (PathNode node : level.getPathNodes()) {
            byId.put(node.getId(), node);
            idByPosition.put(key(node.getWorldPos()), node.getId());
        }

        // 邻接：由 allowDirs 推导（同时核对方向与真实邻格一致）
        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        List<String> brokenEdges = new ArrayList<>();
        for (PathNode node : level.getPathNodes()) {
            List<String> neighbours = new ArrayList<>();
            for (PathNode.Dir dir : node.getAllowDirs()) {
                String neighbourId = idByPosition.get(key(step(node.getWorldPos(), dir)));
                if (neighbourId == null) {
                    brokenEdges.add(node.getId() + " -> " + dir + " 没有对应节点");
                    continue;
                }
                neighbours.add(neighbourId);
                PathNode neighbour = byId.get(neighbourId);
                if (!neighbour.getAllowDirs().contains(opposite(dir))) {
                    brokenEdges.add(node.getId() + " -> " + dir + " 但 " + neighbourId + " 没有反向出口");
                }
            }
            neighbours.sort(Comparator.naturalOrder());
            adjacency.put(node.getId(), neighbours);
        }

        Map<String, Integer> distance = bfs(adjacency, "L01_node_spawn");

        StringBuilder out = new StringBuilder();
        out.append("可走格/节点数 = ").append(byId.size()).append('\n');
        out.append("网格 = ").append(level.getTileGrid()[0].length).append(" 列 × ")
                .append(level.getTileGrid().length).append(" 行\n");
        out.append("断裂/不一致的边 = ").append(brokenEdges.size()).append('\n');
        for (String broken : brokenEdges) {
            out.append("  ! ").append(broken).append('\n');
        }

        List<String> deadEnds = new ArrayList<>();
        List<String> junctions = new ArrayList<>();
        TreeMap<Integer, Integer> degreeHistogram = new TreeMap<>();
        for (Map.Entry<String, List<String>> entry : adjacency.entrySet()) {
            int degree = entry.getValue().size();
            degreeHistogram.merge(degree, 1, Integer::sum);
            String cell = cell(byId.get(entry.getKey()).getWorldPos());
            if (degree == 1) {
                deadEnds.add(entry.getKey() + " " + cell);
            } else if (degree >= 3) {
                junctions.add(entry.getKey() + " " + cell + " 度=" + degree);
            }
        }

        out.append("\n度数分布 (度=节点数) = ").append(degreeHistogram).append('\n');
        out.append("死路 (度=1) 共 ").append(deadEnds.size()).append(" 个:\n");
        for (String deadEnd : deadEnds) {
            out.append("  ").append(deadEnd).append('\n');
        }
        out.append("\n路口 (度>=3) 共 ").append(junctions.size()).append(" 个:\n");
        for (String junction : junctions) {
            out.append("  ").append(junction).append('\n');
        }

        for (String target : List.of("L01_node_plate_left", "L01_node_plate_right", "L01_node_exit_terminal")) {
            PathNode node = byId.get(target);
            assertNotNull(node, "缺少节点 " + target);
            Integer dist = distance.get(target);
            out.append("\n到 ").append(target).append(" (").append(cell(node.getWorldPos())).append(")：");
            out.append(dist == null ? "不可达" : (dist + " 格 = " + (dist * TICKS_PER_CELL) + " 刻"));
            out.append('\n');
            if (dist != null) {
                out.append("  路线: ").append(route(adjacency, distance, target, byId)).append('\n');
            }
        }

        File file = new File(OUT_PATH);
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (PrintWriter writer = new PrintWriter(file, "UTF-8")) {
            writer.print(out);
        }
        System.out.println(out);

        assertEquals(265, byId.size(), "新地图节点数应为 265");
        assertEquals(0, brokenEdges.size(), "allowDirs 与真实邻接必须完全一致");
        assertNotNull(distance.get("L01_node_plate_left"));
        assertNotNull(distance.get("L01_node_plate_right"));
        assertNotNull(distance.get("L01_node_exit_terminal"));
    }

    private static Map<String, Integer> bfs(Map<String, List<String>> adjacency, String start) {
        Map<String, Integer> distance = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        distance.put(start, 0);
        queue.add(start);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String neighbour : adjacency.getOrDefault(current, List.of())) {
                if (!distance.containsKey(neighbour)) {
                    distance.put(neighbour, distance.get(current) + 1);
                    queue.add(neighbour);
                }
            }
        }
        return distance;
    }

    /** 从起点到目标的 BFS 最短路，输出成 (列,行) 序列与每步方向。 */
    private static String route(Map<String, List<String>> adjacency,
                                Map<String, Integer> distance,
                                String target,
                                Map<String, PathNode> byId) {
        List<String> reversed = new ArrayList<>();
        String current = target;
        while (distance.get(current) != 0) {
            reversed.add(current);
            String next = null;
            for (String neighbour : adjacency.get(current)) {
                if (distance.get(neighbour) == distance.get(current) - 1) {
                    next = neighbour;
                    break;
                }
            }
            if (next == null) {
                return "（路径重建失败）";
            }
            current = next;
        }
        reversed.add(current);
        List<String> path = new ArrayList<>(reversed);
        java.util.Collections.reverse(path);

        StringBuilder builder = new StringBuilder();
        String previous = null;
        for (String id : path) {
            Vector2D position = byId.get(id).getWorldPos();
            if (previous != null) {
                builder.append(directionWord(byId.get(previous).getWorldPos(), position)).append(' ');
            }
            builder.append(cell(position));
            if (previous != null) {
                builder.append(" → ");
            }
            previous = id;
        }
        return builder.toString();
    }

    private static String directionWord(Vector2D from, Vector2D to) {
        if (to.x() > from.x()) {
            return "[右]";
        }
        if (to.x() < from.x()) {
            return "[左]";
        }
        if (to.y() > from.y()) {
            return "[下]";
        }
        return "[上]";
    }

    private static Vector2D step(Vector2D position, PathNode.Dir dir) {
        return switch (dir) {
            case UP -> new Vector2D(position.x(), position.y() - TILE);
            case DOWN -> new Vector2D(position.x(), position.y() + TILE);
            case LEFT -> new Vector2D(position.x() - TILE, position.y());
            case RIGHT -> new Vector2D(position.x() + TILE, position.y());
        };
    }

    private static PathNode.Dir opposite(PathNode.Dir dir) {
        return switch (dir) {
            case UP -> PathNode.Dir.DOWN;
            case DOWN -> PathNode.Dir.UP;
            case LEFT -> PathNode.Dir.RIGHT;
            case RIGHT -> PathNode.Dir.LEFT;
        };
    }

    private static String key(Vector2D position) {
        return position.x() + "," + position.y();
    }

    private static String cell(Vector2D position) {
        int col = (int) Math.round(position.x() / TILE - 0.5);
        int row = (int) Math.round(position.y() / TILE - 0.5);
        return "(" + col + "," + row + ")";
    }
}
