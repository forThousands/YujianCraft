package dev.yujiancraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Informational result screen; the weapon has already been tempered when this opens. */
public final class ManualSpiritTrialResultScreen extends Screen {
    private final double totalDamage;
    private final double dps;
    private final int attempt;

    private ManualSpiritTrialResultScreen(double totalDamage, double dps, int attempt) {
        super(Component.translatable("screen.yujiancraft.trial.result_title"));
        this.totalDamage = totalDamage;
        this.dps = dps;
        this.attempt = attempt;
    }

    public static void open(double totalDamage, double dps, int attempt) {
        Minecraft.getInstance().setScreen(new ManualSpiritTrialResultScreen(totalDamage, dps, attempt));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 - 50, height / 2 + 47, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = width / 2 - 132;
        int top = height / 2 - 82;
        graphics.fill(left, top, left + 264, top + 152, 0xF0141219);
        graphics.fill(left + 3, top + 3, left + 261, top + 149, 0xFF24202D);
        graphics.drawCenteredString(font, title, width / 2, top + 14, 0x92F2F6);
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2.0F, top + 54.0F, 0.0F);
        graphics.pose().scale(1.8F, 1.8F, 1.0F);
        graphics.drawCenteredString(font, Component.translatable("screen.yujiancraft.trial.dps_value",
                format(dps)), 0, 0, 0xF5EBC8);
        graphics.pose().popPose();
        graphics.drawCenteredString(font, Component.translatable("screen.yujiancraft.trial.total_value",
                format(totalDamage)), width / 2, top + 84, 0xB7AFC1);
        graphics.drawCenteredString(font, Component.translatable("screen.yujiancraft.trial.completed_attempt",
                attempt, 2), width / 2, top + 103, 0x9ADDE4);
        graphics.drawCenteredString(font, Component.translatable("screen.yujiancraft.trial.completed_hint"),
                width / 2, top + 119, 0xD6C7E2);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
