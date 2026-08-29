package dev.yujiancraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class YujianGuideScreen extends Screen {
    private static final int PAGE_COUNT = 16;
    private static final int CONTENTS_PAGE = 1;
    private int page;
    private Button previous;
    private Button next;
    private Button contents;
    private final List<Button> contentsButtons = new ArrayList<>();

    public YujianGuideScreen() {
        super(Component.translatable("screen.yujiancraft.guide.title"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new YujianGuideScreen());
    }

    @Override
    protected void init() {
        contentsButtons.clear();
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        int bottom = top + panelHeight;
        previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> changePage(-1))
                .bounds(left + 12, bottom - 25, 34, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), button -> changePage(1))
                .bounds(left + panelWidth - 46, bottom - 25, 34, 20).build());
        contents = addRenderableWidget(Button.builder(Component.translatable(
                        "screen.yujiancraft.guide.contents"), button -> setPage(CONTENTS_PAGE))
                .bounds(width / 2 - 94, bottom - 25, 74, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 + 20, bottom - 25, 74, 20).build());

        int gap = 6;
        int buttonWidth = (panelWidth - 36 - gap) / 2;
        int buttonTop = top + 66;
        for (int targetPage = 2; targetPage < PAGE_COUNT; targetPage++) {
            int entry = targetPage - 2;
            int column = entry / 7;
            int row = entry % 7;
            int x = left + 18 + column * (buttonWidth + gap);
            int destination = targetPage;
            contentsButtons.add(addRenderableWidget(Button.builder(
                            Component.translatable(pageTitleKey(targetPage)), button -> setPage(destination))
                    .bounds(x, buttonTop + row * 18, buttonWidth, 17).build()));
        }
        refreshButtons();
    }

    private void changePage(int direction) {
        setPage(page + direction);
    }

    private void setPage(int destination) {
        page = Math.max(0, Math.min(PAGE_COUNT - 1, destination));
        refreshButtons();
    }

    private void refreshButtons() {
        if (previous == null) return;
        previous.active = page > 0;
        next.active = page + 1 < PAGE_COUNT;
        contents.active = page != CONTENTS_PAGE;
        contentsButtons.forEach(button -> button.visible = page == CONTENTS_PAGE);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        int bottom = top + panelHeight;
        graphics.fill(left, top, left + panelWidth, bottom, 0xF11B1720);
        graphics.fill(left + 4, top + 4, left + panelWidth - 4, bottom - 4, 0xFF272130);
        graphics.fill(left + 13, top + 38, left + panelWidth - 13, bottom - 34, 0xFF15151C);
        graphics.drawCenteredString(font, title, width / 2, top + 12, 0x8DECF3);
        graphics.drawCenteredString(font,
                Component.translatable("screen.yujiancraft.guide.page", page + 1, PAGE_COUNT),
                width / 2, top + 25, 0xA9A0B5);
        graphics.drawString(font, Component.translatable(pageTitleKey(page)),
                left + 22, top + 46, 0xF2E5C4, false);
        if (page == CONTENTS_PAGE) {
            graphics.drawString(font, Component.translatable("screen.yujiancraft.guide.contents_hint"),
                    left + 22, top + 56, 0xA9A0B5, false);
        } else {
            graphics.drawWordWrap(font, pageBody(), left + 22, top + 65,
                    panelWidth - 44, 0xD8D3DE);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private Component pageBody() {
        Minecraft minecraft = Minecraft.getInstance();
        if (page == 2) {
            return Component.translatable("guide.yujiancraft.page.2.body",
                    ClientModEvents.TOGGLE_SWORDS.getTranslatedKeyMessage(),
                    ClientModEvents.SWITCH_FORMATION.getTranslatedKeyMessage(),
                    ClientModEvents.OPEN_CONFIG.getTranslatedKeyMessage(),
                    ClientModEvents.SWITCH_TECHNIQUE.getTranslatedKeyMessage(),
                    ClientModEvents.ACTIVATE_SWORD_ARRAY.getTranslatedKeyMessage(),
                    ClientModEvents.SWITCH_SWORD_ARRAY_STYLE.getTranslatedKeyMessage(),
                    ClientModEvents.TOGGLE_COMBO.getTranslatedKeyMessage(),
                    ClientModEvents.ARTIFACT_ACTION.getTranslatedKeyMessage());
        }
        if (page == 3) {
            return Component.translatable("guide.yujiancraft.page.3.body",
                    minecraft.options.keyAttack.getTranslatedKeyMessage(),
                    minecraft.options.keyAttack.getTranslatedKeyMessage(),
                    minecraft.options.keyUse.getTranslatedKeyMessage(),
                    minecraft.options.keyAttack.getTranslatedKeyMessage(),
                    minecraft.options.keyJump.getTranslatedKeyMessage(),
                    minecraft.options.keyJump.getTranslatedKeyMessage(),
                    minecraft.options.keyShift.getTranslatedKeyMessage());
        }
        return Component.translatable("guide.yujiancraft.page." + page + ".body");
    }

    private static String pageTitleKey(int page) {
        return "guide.yujiancraft.page." + page + ".title";
    }

    private int panelWidth() {
        return Math.max(300, Math.min(420, width - 30));
    }

    private int panelHeight() {
        return Math.max(230, Math.min(300, height - 24));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
