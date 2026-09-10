package org.example.timeloop.app;

import org.example.timeloop.mechanism.DockingPlate;
import org.example.timeloop.mechanism.DockingPlateRegistry;
import org.example.timeloop.mechanism.Door;
import org.example.timeloop.mechanism.ExitTerminal;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.example.timeloop.level.model.Vector2D;

import java.util.Set;

public class Level01Integration {

    public static void main(String[] args) {
        EventDispatcher.getInstance().clear();
        DockingPlateRegistry.getInstance().clear();

        DockingPlate plateLeft = new DockingPlate("plate_left", new Vector2D(3 * 32, 8 * 32));
        DockingPlate plateRight = new DockingPlate("plate_right", new Vector2D(12 * 32, 8 * 32));
        DockingPlateRegistry.getInstance().register(plateLeft);
        DockingPlateRegistry.getInstance().register(plateRight);

        Door door = new Door("door_1", new Vector2D(5.5 * 32, 5.5 * 32),
                Set.of("plate_left", "plate_right"));
        ExitTerminal exit = new ExitTerminal("exit_1", new Vector2D(5.5 * 32, 1.5 * 32), "door_1");

        System.out.println("=== P1 第一关集成验证 ===");
        System.out.println("初始状态: 门=" + door.getState() + ", 出口=" + exit.isDoorUnlocked());

        System.out.println("\n[步骤1] 玩家进入左板");
        plateLeft.tryEnter("player", 0, 10);
        System.out.println("  左板状态: " + plateLeft.getState());
        System.out.println("  门状态: " + door.getState());

        System.out.println("\n[步骤2] 玩家进入右板");
        plateRight.tryEnter("player2", 0, 20);
        System.out.println("  右板状态: " + plateRight.getState());
        System.out.println("  门状态: " + door.getState());

        if (door.isUnlocked()) {
            System.out.println("\n✅ 门已解锁");
            boolean interactResult = exit.interact(30, 0);
            System.out.println("  出口交互结果: " + (interactResult ? "✅ 通关成功" : "❌ 失败"));
        } else {
            System.out.println("\n❌ 门未解锁");
        }

        System.out.println("\n[步骤3] 玩家离开左板");
        plateLeft.tryExit("player", 0, 40);
        System.out.println("  左板状态: " + plateLeft.getState());
        System.out.println("  门状态: " + door.getState());

        System.out.println("\n=== 验证完成 ===");
        System.out.println("预期: LOCKED -> UNLOCKED -> LOCKED");
    }
}