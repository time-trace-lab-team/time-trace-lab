package org.example.timeloop.app;

import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.ray.Ray;
import org.example.timeloop.mechanism.ray.RayManager;
import org.example.timeloop.mechanism.ray.PhaseManager;

public class Level02Integration {

    public static void main(String[] args) {
        RayManager.getInstance().clear();

        Ray ray = new Ray("teach_ray",
                new Vector2D(100, 200),
                new Vector2D(300, 200),
                10, 10,
                20, 10
        );
        RayManager.getInstance().register(ray);

        PhaseManager phase = new PhaseManager();

        System.out.println("=== P2 射线与相位集成验证 ===");

        System.out.println("\n[测试1] 射线状态切换");
        for (int tick = 0; tick < 35; tick++) {
            ray.update(tick);
            if (tick == 0) System.out.println("  tick=0: " + ray.getState());
            if (tick == 10) System.out.println("  tick=10: " + ray.getState() + " (应 WARNING)");
            if (tick == 20) System.out.println("  tick=20: " + ray.getState() + " (应 ACTIVE)");
            if (tick == 30) System.out.println("  tick=30: " + ray.getState() + " (应 OFF)");
        }

        System.out.println("\n[测试2] 相位下潜");
        System.out.println("  初始: " + phase.getState());
        phase.pressSpace();
        System.out.println("  按下Space: " + phase.getState() + " (应 PHASED)");
        for (int i = 0; i < 35; i++) {
            phase.update();
            if (i == 29) System.out.println("  30 tick后: " + phase.getState() + " (应 RECOVERING)");
            if (i == 74) System.out.println("  75 tick后: " + phase.getState() + " (应 NORMAL)");
        }

        System.out.println("\n[测试3] 碰撞检测");
        Vector2D onRay = new Vector2D(200, 200);
        Vector2D offRay = new Vector2D(200, 250);
        ray.update(20);
        System.out.println("  射线状态: " + ray.getState());
        System.out.println("  点在射线上: " + ray.containsPoint(onRay, 5) + " (应 true)");
        System.out.println("  点不在射线上: " + ray.containsPoint(offRay, 5) + " (应 false)");

        System.out.println("\n=== 验证完成 ===");
    }
}