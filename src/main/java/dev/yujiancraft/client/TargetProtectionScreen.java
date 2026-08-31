package dev.yujiancraft.client;

import dev.yujiancraft.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class TargetProtectionScreen extends Screen {
    private static final int ROWS = 6;
    private final Screen parent;
    private int page;

    public TargetProtectionScreen(Screen parent) {
        super(Component.translatable("screen.yujiancraft.protection.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuildRows();
        ClientTargetProtectionState.request();
    }

    public void refreshFromServer() {
        page = Math.min(page, maximumPage());
        clearWidgets();
        rebuildRows();
    }

    private void rebuildRows() {
        int centre = width / 2;
        int top = Math.max(54, height / 2 - 75);
        List<ModNetwork.TargetProtectionEntry> entries = ClientTargetProtectionState.entries();
        int start = page * ROWS;
        for (int row = 0; row < ROWS && start + row < entries.size(); row++) {
            ModNetwork.TargetProtectionEntry entry = entries.get(start + row);
            int y = top + row * 23;
            addRenderableWidget(Button.builder(Component.literal(displayName(entry)), button -> { })
                    .bounds(centre - 150, y, 240, 20).build()).active = false;
            addRenderableWidget(Button.builder(Component.translatable("screen.yujiancraft.protection.remove"), button ->
                            ModNetwork.CHANNEL.sendToServer(new ModNetwork.RemoveTargetProtectionPacket(entry.uuid())))
                    .bounds(centre + 94, y, 56, 20).build());
        }
        int navY = top + ROWS * 23 + 5;
        Button previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> changePage(-1))
                .bounds(centre - 150, navY, 40, 20).build());
        Button next = addRenderableWidget(Button.builder(Component.literal(">"), button -> changePage(1))
                .bounds(centre - 105, navY, 40, 20).build());
        previous.active = page > 0;
        next.active = page < maximumPage();
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(centre + 50, navY, 100, 20).build());
    }

    private String displayName(ModNetwork.TargetProtectionEntry entry) {
        String name = entry.name().isBlank() ? entry.typeId() : entry.name();
        String suffix = entry.uuid().toString().substring(0, 8);
        return name + "  [" + suffix + "]";
    }

    private int maximumPage() {
        return Math.max(0, (ClientTargetProtectionState.entries().size() - 1) / ROWS);
    }

    private void changePage(int delta) {
        page = Math.max(0, Math.min(maximumPage(), page + delta));
        clearWidgets();
        rebuildRows();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int top = Math.max(54, height / 2 - 75);
        graphics.drawCenteredString(font, title, width / 2, top - 42, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.yujiancraft.protection.hint"),
                width / 2, top - 28, 0x9FE8DC);
        graphics.drawCenteredString(font, Component.translatable("screen.yujiancraft.protection.natural_hint"),
                width / 2, top - 16, 0xB7B0C4);
        if (ClientTargetProtectionState.entries().isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.yujiancraft.protection.empty"),
                    width / 2, top + 50, 0xA0A0A0);
        }
        graphics.drawString(font, Component.translatable("screen.yujiancraft.protection.page",
                page + 1, maximumPage() + 1), width / 2 - 58, top + ROWS * 23 + 11, 0xB7B0C4, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
