package org.example.timeloop.level;

import org.example.timeloop.level.model.DoorInfo;
import org.example.timeloop.level.model.EntitySpawnInfo;
import org.example.timeloop.level.model.LevelData;
import org.example.timeloop.level.model.PathNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 开发三关卡数据的构造前校验。
 *
 * <p>当前 W1 只启用第一关校验；后续关卡迁移时应继续使用同一套稳定 ID 规则。</p>
 */
public final class LevelDataValidator {

    private static final String LEVEL_PREFIX = "L01_";

    private LevelDataValidator() {
    }

    public static void validateFirstLevel(LevelData levelData) {
        if (levelData == null) {
            throw new IllegalArgumentException("第一关数据不能为空");
        }

        Map<String, PathNode> nodesById = validatePathNodes(levelData.getPathNodes());
        Map<String, String> mechanismsById = new HashMap<>();

        List<EntitySpawnInfo> entities = levelData.getEntitySpawnList();
        for (int index = 0; index < entities.size(); index++) {
            EntitySpawnInfo entity = entities.get(index);
            String field = "第一关 entities[" + index + "]";
            String kind = expectedMechanismKind(entity.getEntityType(), field + ".entityType");
            String id = StableIdValidator.requireMechanismId(entity.getId(), kind, field + ".id");
            requireLevelPrefix(id, field + ".id");
            registerMechanism(mechanismsById, id, entity.getEntityType(), field + ".id");

            String pathNodeId = StableIdValidator.requirePathNodeId(
                    entity.getPathNodeId(), field + ".pathNodeId");
            requireLevelPrefix(pathNodeId, field + ".pathNodeId");
            PathNode pathNode = nodesById.get(pathNodeId);
            if (pathNode == null) {
                throw new IllegalArgumentException(
                        field + " 引用了不存在的路径节点: " + pathNodeId
                );
            }
            if (entity.getPos() == null) {
                throw new IllegalArgumentException(field + ".pos 不能为空");
            }
            if (!samePosition(entity.getPos().x(), entity.getPos().y(),
                    pathNode.getWorldPos().x(), pathNode.getWorldPos().y())) {
                throw new IllegalArgumentException(
                        field + " 的世界坐标与路径节点 " + pathNodeId + " 不一致"
                );
            }

            if ("dock_plate".equals(entity.getEntityType())
                    && !Boolean.TRUE.equals(entity.getProperties().get("autoDock"))) {
                throw new IllegalArgumentException(field + " 驻留板必须明确 autoDock=true");
            }
        }

        List<DoorInfo> doors = levelData.getDoors();
        for (int index = 0; index < doors.size(); index++) {
            DoorInfo door = doors.get(index);
            String field = "第一关 doors[" + index + "]";
            String id = StableIdValidator.requireMechanismId(door.getId(), "door", field + ".id");
            requireLevelPrefix(id, field + ".id");
            registerMechanism(mechanismsById, id, "door", field + ".id");

            if (door.getRequiredPlateIds().isEmpty()) {
                throw new IllegalArgumentException(field + " 至少需要一个驻留板引用");
            }
            Set<String> referencesSeen = new HashSet<>();
            for (String plateId : door.getRequiredPlateIds()) {
                StableIdValidator.requireMechanismId(plateId, "plate", field + ".requiredPlateIds");
                requireLevelPrefix(plateId, field + ".requiredPlateIds");
                if (!referencesSeen.add(plateId)) {
                    throw new IllegalArgumentException(field + " 重复引用驻留板: " + plateId);
                }
                if (!"dock_plate".equals(mechanismsById.get(plateId))) {
                    throw new IllegalArgumentException(
                            field + " 引用了不存在的驻留板: " + plateId
                    );
                }
            }
        }
    }

    private static Map<String, PathNode> validatePathNodes(List<PathNode> pathNodes) {
        if (pathNodes == null || pathNodes.isEmpty()) {
            throw new IllegalArgumentException("第一关路径节点列表不能为空");
        }

        Map<String, PathNode> nodesById = new HashMap<>();
        for (int index = 0; index < pathNodes.size(); index++) {
            PathNode node = pathNodes.get(index);
            String field = "第一关 pathNodes[" + index + "]";
            String id = StableIdValidator.requirePathNodeId(node.getId(), field + ".id");
            requireLevelPrefix(id, field + ".id");
            if (nodesById.putIfAbsent(id, node) != null) {
                throw new IllegalArgumentException(field + " 重复的路径节点 ID: " + id);
            }
            if (node.getWorldPos() == null) {
                throw new IllegalArgumentException(field + ".worldPos 不能为空");
            }
        }
        return nodesById;
    }

    private static String expectedMechanismKind(String entityType, String fieldName) {
        if ("dock_plate".equals(entityType)) {
            return "plate";
        }
        if ("exit_terminal".equals(entityType)) {
            return "exit";
        }
        throw new IllegalArgumentException(fieldName + " 不支持的第一关机关类型: " + entityType);
    }

    private static void requireLevelPrefix(String id, String fieldName) {
        if (!id.startsWith(LEVEL_PREFIX)) {
            throw new IllegalArgumentException(fieldName + " 必须使用 L01 命名空间: " + id);
        }
    }

    private static void registerMechanism(Map<String, String> mechanismsById,
                                          String id,
                                          String entityType,
                                          String fieldName) {
        if (mechanismsById.putIfAbsent(id, entityType) != null) {
            throw new IllegalArgumentException(fieldName + " 重复的机关 ID: " + id);
        }
    }

    private static boolean samePosition(double x1, double y1, double x2, double y2) {
        return Math.abs(x1 - x2) <= 1e-9 && Math.abs(y1 - y2) <= 1e-9;
    }
}
