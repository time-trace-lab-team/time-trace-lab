package org.example.timeloop.mechanism.ray;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.example.timeloop.level.model.Vector2D;

public final class RayManager {

    private static final RayManager INSTANCE = new RayManager();
    private final ConcurrentMap<String, Ray> rays = new ConcurrentHashMap<>();

    private RayManager() {}

    public static RayManager getInstance() {
        return INSTANCE;
    }

    public void register(Ray ray) {
        rays.put(ray.getId(), ray);
    }

    public void unregister(String id) {
        rays.remove(id);
    }

    public Ray get(String id) {
        return rays.get(id);
    }

    public List<Ray> getAll() {
        return new ArrayList<>(rays.values());
    }

    /**
     * 每 tick 更新所有射线状态。
     */
    public void updateAll(long roundTick) {
        for (Ray ray : rays.values()) {
            ray.update(roundTick);
        }
    }

    /**
     * 获取某个位置激活的射线（用于碰撞检测）。
     */
    public List<Ray> getActiveRaysAt(Vector2D position, double width) {
        List<Ray> result = new ArrayList<>();
        for (Ray ray : rays.values()) {
            if (ray.getState() == Ray.State.ACTIVE && ray.containsPoint(position, width)) {
                result.add(ray);
            }
        }
        return result;
    }

    public void resetAll() {
        for (Ray ray : rays.values()) {
            ray.reset();
        }
    }

    public void clear() {
        rays.clear();
    }
}