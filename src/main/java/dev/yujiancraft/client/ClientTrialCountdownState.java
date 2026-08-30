package dev.yujiancraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.network.chat.Component;

/** A movable large countdown; vanilla titles are fixed over the screen centre. */
public final class ClientTrialCountdownState {
    private static int seconds;
    private static long expiresAtMillis;

    private ClientTrialCountdownState() {
    }

    public static void show(int remainingSeconds) {
        seconds = Math.max(0, remainingSeconds);
        expiresAtMillis = net.minecraft.Util.getMillis() + 1250L;
    }

    public static void clear() {
        seconds = 0;
        expiresAtMillis = 0L;
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (seconds <= 0 || net.minecraft.Util.getMillis() > expiresAtMillis) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.screen != null) return;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        Component text = Component.literal(Integer.toString(seconds));
        float scale = seconds <= 3 ? 3.0F : 2.5F;
        int color = seconds <= 3 ? 0xFFFF6868 : 0xFFEAFBFF;
        float x = width * 0.69F / scale - minecraft.font.width(text) * 0.5F;
        float y = height * 0.39F / scale;
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);
        RenderSystem.enableBlend();
        graphics.drawString(minecraft.font, text, (int) x + 1, (int) y + 1, 0xA0000000, false);
        graphics.drawString(minecraft.font, text, (int) x, (int) y, color, false);
        graphics.pose().popPose();
    }
}
