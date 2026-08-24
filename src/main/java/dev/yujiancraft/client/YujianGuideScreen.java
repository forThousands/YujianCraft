package dev.yujiancraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class YujianGuideScreen extends Screen {
    private static final int PAGE_COUNT = 10;
    private int page;
    private Button previous;
    private Button next;

    public YujianGuideScreen() {
        super(Component.translatable("screen.yujiancraft.guide.title"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new YujianGuideScreen());
    }

    @Override
    protected void init() {
        int left = width / 2 - 142;
        int bottom = height / 2 + 94;
        previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> changePage(-1))
                .bounds(left + 18, bottom - 22, 38, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), button -> changePage(1))
                .bounds(left + 228, bottom - 22, 38, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 - 42, bottom - 22, 84, 20).build());
        refreshButtons();
    }

    private void changePage(int direction) {
        page = Math.max(0, Math.min(PAGE_COUNT - 1, page + direction));
        refreshButtons();
    }

    private void refreshButtons() {
        if (previous == null) return;
        previous.active = page > 0;
        next.active = page + 1 < PAGE_COUNT;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = width / 2 - 142;
        int top = height / 2 - 106;
        graphics.fill(left, top, left + 284, top + 200, 0xF11B1720);
        graphics.fill(left + 4, top + 4, left + 280, top + 196, 0xFF272130);
        graphics.fill(left + 13, top + 35, left + 271, top + 165, 0xFF15151C);
        graphics.drawCenteredString(font, title, width / 2, top + 12, 0x8DECF3);
        graphics.drawCenteredString(font,
                Component.translatable("screen.yujiancraft.guide.page", page + 1, PAGE_COUNT),
                width / 2, top + 25, 0xA9A0B5);
        graphics.drawString(font, Component.translatable("guide.yujiancraft.page." + page + ".title"),
                left + 22, top + 45, 0xF2E5C4, false);
        graphics.drawWordWrap(font, Component.translatable("guide.yujiancraft.page." + page + ".body"),
                left + 22, top + 65, 240, 0xD8D3DE);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
