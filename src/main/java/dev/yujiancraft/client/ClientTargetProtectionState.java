package dev.yujiancraft.client;

import dev.yujiancraft.network.ModNetwork;
import net.minecraft.client.Minecraft;

import java.util.List;

public final class ClientTargetProtectionState {
    private static List<ModNetwork.TargetProtectionEntry> entries = List.of();
    private ClientTargetProtectionState() { }
    public static List<ModNetwork.TargetProtectionEntry> entries() { return entries; }
    public static void request() { ModNetwork.sendToServer(new ModNetwork.RequestTargetProtectionsPacket()); }
    public static void accept(List<ModNetwork.TargetProtectionEntry> updated) {
        entries = List.copyOf(updated);
        if (Minecraft.getInstance().screen instanceof TargetProtectionScreen screen) screen.refreshFromServer();
    }
}
