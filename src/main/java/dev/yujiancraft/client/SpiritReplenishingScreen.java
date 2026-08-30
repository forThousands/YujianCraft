package dev.yujiancraft.client;

import dev.yujiancraft.menu.SpiritReplenishingMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class SpiritReplenishingScreen extends AbstractContainerScreen<SpiritReplenishingMenu> {
    private Button replenishButton;

    public SpiritReplenishingScreen(SpiritReplenishingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 230;
        imageHeight = 192;
        inventoryLabelX = 35;
        inventoryLabelY = 101;
    }

    @Override protected void init() {
        super.init();
        replenishButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.yujiancraft.replenishing.repair"), button -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                                SpiritReplenishingMenu.REPLENISH_BUTTON);
                    }
                }).bounds(leftPos + 91, topPos + 67, 94, 22).build());
        refresh();
    }

    @Override protected void containerTick() { super.containerTick(); refresh(); }
    private void refresh() { if (replenishButton != null) replenishButton.active = menu.canReplenish(); }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF151D22);
        graphics.fill(x + 5, y + 17, x + 82, y + 99, 0xFF243139);
        graphics.fill(x + 87, y + 17, x + 225, y + 99, 0xFF18252B);
        graphics.fill(x + 29, y + 103, x + 201, y + 192, 0xFF20272C);
        slot(graphics, x + 55, y + 36);
        slot(graphics, x + 55, y + 72);
        graphics.fill(x + 60, y + 58, x + 68, y + 64, 0xFF58E7EC);
    }

    private static void slot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 20, y + 20, 0xFF071014);
        graphics.fill(x + 1, y + 1, x + 19, y + 19, 0xFF53656D);
        graphics.fill(x + 2, y + 2, x + 18, y + 18, 0xFF10191E);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 7, 5, 0xE9FDFF, false);
        graphics.drawString(font, Component.translatable("screen.yujiancraft.replenishing.sword"),
                8, 40, 0xB7CED4, false);
        graphics.drawString(font, Component.translatable("screen.yujiancraft.replenishing.crystal"),
                8, 76, 0xB7CED4, false);
        int maximum = menu.maximumDurability();
        int remaining = menu.remainingDurability();
        graphics.drawCenteredString(font, Component.translatable("screen.yujiancraft.replenishing.durability",
                remaining, maximum), 156, 31, 0xD6F7F4);
        int width = maximum <= 0 ? 0 : (int) Math.round(112.0D * remaining / maximum);
        graphics.fill(100, 47, 212, 54, 0xFF071014);
        graphics.fill(101, 48, 101 + Math.max(0, Math.min(110, width)), 53, 0xFF4DE2C6);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xB8C4CE, false);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
