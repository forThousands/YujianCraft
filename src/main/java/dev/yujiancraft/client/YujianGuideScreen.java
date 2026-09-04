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
    private static final float BOOK_TITLE_SCALE = 1.22F;
    private static final float PAGE_TITLE_SCALE = 1.16F;
    private static final float BODY_SCALE = 1.18F;
    private int page;
    private double bodyScroll;
    private int bodyMaxScroll;
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
        bodyScroll = 0.0D;
        bodyMaxScroll = 0;
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
        YujianScreenBackground.render(graphics, width, height);
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        int bottom = top + panelHeight;
        graphics.fill(left, top, left + panelWidth, bottom, 0xF11B1720);
        graphics.fill(left + 4, top + 4, left + panelWidth - 4, bottom - 4, 0xFF272130);
        graphics.fill(left + 13, top + 38, left + panelWidth - 13, bottom - 34, 0xFF15151C);
        drawCenteredScaled(graphics, title, width / 2, top + 11, 0x8DECF3, BOOK_TITLE_SCALE);
        graphics.drawCenteredString(font,
                Component.translatable("screen.yujiancraft.guide.page", page + 1, PAGE_COUNT),
                width / 2, top + 27, 0xA9A0B5);
        drawScaled(graphics, Component.translatable(pageTitleKey(page)),
                left + 22, top + 45, 0xF2E5C4, PAGE_TITLE_SCALE);
        if (page == CONTENTS_PAGE) {
            graphics.drawString(font, Component.translatable("screen.yujiancraft.guide.contents_hint"),
                    left + 22, top + 59, 0xA9A0B5, false);
        } else {
            renderBody(graphics, left, top, bottom, panelWidth);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Screen.render() invokes this after our authored book content in 1.21.1.
    }

    private void renderBody(GuiGraphics graphics, int left, int top, int bottom, int panelWidth) {
        int textLeft = left + 22;
        int textTop = top + 66;
        int textRight = left + panelWidth - 22;
        int textBottom = bottom - 36;
        int viewportHeight = Math.max(1, textBottom - textTop);
        int wrapWidth = Math.max(80, Math.round((textRight - textLeft) / BODY_SCALE));
        Component body = pageBody();
        int contentHeight = Math.round(font.split(body, wrapWidth).size() * font.lineHeight * BODY_SCALE);
        bodyMaxScroll = Math.max(0, contentHeight - viewportHeight + 2);
        bodyScroll = Math.max(0.0D, Math.min(bodyScroll, bodyMaxScroll));

        graphics.enableScissor(textLeft, textTop, textRight, textBottom);
        graphics.pose().pushPose();
        graphics.pose().scale(BODY_SCALE, BODY_SCALE, 1.0F);
        graphics.drawWordWrap(font, body,
                Math.round(textLeft / BODY_SCALE),
                (int) Math.round((textTop - bodyScroll) / BODY_SCALE),
                wrapWidth, 0xD8D3DE);
        graphics.pose().popPose();
        graphics.disableScissor();

        if (bodyMaxScroll > 0) {
            int trackTop = textTop;
            int trackBottom = textBottom;
            int thumbHeight = Math.max(14,
                    Math.round((float) viewportHeight * viewportHeight / (viewportHeight + bodyMaxScroll)));
            int travel = trackBottom - trackTop - thumbHeight;
            int thumbTop = trackTop + Math.round((float) (bodyScroll / bodyMaxScroll) * travel);
            graphics.fill(textRight + 6, trackTop, textRight + 8, trackBottom, 0x553F3948);
            graphics.fill(textRight + 5, thumbTop, textRight + 9, thumbTop + thumbHeight, 0xCC79C8CE);
        }
    }

    private void drawCenteredScaled(GuiGraphics graphics, Component text, int centreX, int y,
                                    int colour, float scale) {
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawCenteredString(font, text, Math.round(centreX / scale),
                Math.round(y / scale), colour);
        graphics.pose().popPose();
    }

    private void drawScaled(GuiGraphics graphics, Component text, int x, int y,
                            int colour, float scale) {
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, Math.round(x / scale), Math.round(y / scale), colour, false);
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (page != CONTENTS_PAGE && bodyMaxScroll > 0 && scrollY != 0.0D) {
            double step = font.lineHeight * BODY_SCALE * 2.0D;
            bodyScroll = Math.max(0.0D, Math.min(bodyMaxScroll, bodyScroll - scrollY * step));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private Component pageBody() {
        Minecraft minecraft = Minecraft.getInstance();
        if (page == 2) {
            return Component.translatable("guide.yujiancraft.page.2.body",
                    ClientModEvents.TOGGLE_SWORDS.getTranslatedKeyMessage(),
                    ClientModEvents.SWITCH_FORMATION.getTranslatedKeyMessage(),
                    ClientModEvents.OPEN_QUICK_SWITCH.getTranslatedKeyMessage(),
                    ClientModEvents.OPEN_CONFIG.getTranslatedKeyMessage(),
                    ClientModEvents.SWITCH_TECHNIQUE.getTranslatedKeyMessage(),
                    ClientModEvents.ACTIVATE_SWORD_ARRAY.getTranslatedKeyMessage(),
                    ClientModEvents.SWITCH_SWORD_ARRAY_STYLE.getTranslatedKeyMessage(),
                    ClientModEvents.TOGGLE_COMBO.getTranslatedKeyMessage(),
                    ClientModEvents.ARTIFACT_ACTION.getTranslatedKeyMessage(),
                    ClientModEvents.TOGGLE_TARGET_PROTECTION.getTranslatedKeyMessage());
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
        return Math.max(320, Math.min(520, width - 30));
    }

    private int panelHeight() {
        return Math.max(240, Math.min(360, height - 24));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
