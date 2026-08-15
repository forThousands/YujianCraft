package dev.swordflight.client;

import dev.swordflight.combat.SwordSettings;
import dev.swordflight.network.ModNetwork;
import net.minecraft.client.Minecraft;

public final class ClientSettingsState {
    private static SwordSettings settings = SwordSettings.defaults();
    private static boolean canEditBalance;

    private ClientSettingsState() {
    }

    public static SwordSettings get() {
        return settings;
    }

    public static void requestFromServer() {
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.RequestSettingsPacket());
    }

    public static void update(SwordSettings updated) {
        settings = updated;
        ModNetwork.CHANNEL.sendToServer(ModNetwork.UpdateSettingsPacket.from(updated));
    }

    public static boolean canEditBalance() {
        return canEditBalance;
    }

    public static void acceptFromServer(SwordSettings synced, boolean canEdit) {
        settings = synced;
        canEditBalance = canEdit;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof SwordflightConfigScreen screen) {
            screen.onSettingsSynced(synced);
        } else if (minecraft.screen instanceof AdminBalanceScreen screen) {
            screen.onSettingsSynced();
        }
    }
}
