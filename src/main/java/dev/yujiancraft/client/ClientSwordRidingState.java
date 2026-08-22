package dev.yujiancraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Abilities;

public final class ClientSwordRidingState {
    private static boolean active;

    private ClientSwordRidingState() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean value) {
        active = value;
    }

    /** Applies the authoritative result after vanilla's double-jump handling has run. */
    public static void applyServerState(boolean value, boolean mayfly, boolean flying, float flyingSpeed) {
        active = value;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        Abilities abilities = minecraft.player.getAbilities();
        abilities.mayfly = mayfly;
        abilities.flying = flying && mayfly;
        abilities.setFlyingSpeed(flyingSpeed);
        minecraft.player.fallDistance = 0.0F;
    }
}
