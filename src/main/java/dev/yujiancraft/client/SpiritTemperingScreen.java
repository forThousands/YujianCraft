package dev.yujiancraft.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import dev.yujiancraft.entity.FlyingSwordEntity;
import dev.yujiancraft.item.FlyingSwordItem;
import dev.yujiancraft.menu.SpiritTemperingMenu;
import dev.yujiancraft.registry.ModEntities;
import dev.yujiancraft.wanxiang.WanxiangSwordData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class SpiritTemperingScreen extends AbstractContainerScreen<SpiritTemperingMenu> {
    private Button shapingButton;
    private Button enterTrialButton;
    private Button presetButton;
    private Button glowButton;
    private Button flipButton;
    private Button scaleDownButton;
    private Button scaleUpButton;
    private Button radiusDownButton;
    private Button radiusUpButton;
    private Button lengthDownButton;
    private Button lengthUpButton;
    private Button confirmShapeButton;
    private Button backButton;
    private boolean shapingPage;
    private FlyingSwordEntity previewSword;
    private int previewTicks;
    private float previewYaw = -28.0F;
    private float previewPitch = 13.0F;
    private float previewZoom = 1.0F;
    private boolean draggingPreview;
    private int previewIdleTicks;

    public SpiritTemperingScreen(SpiritTemperingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 360;
        imageHeight = 270;
        inventoryLabelX = 99;
        inventoryLabelY = 178;
    }

    @Override
    protected void init() {
        super.init();
        shapingButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.yujiancraft.tempering.shaping"), button -> {
                            shapingPage = true;
                            refreshButtons();
                        }).bounds(leftPos + 135, topPos + 101, 104, 20).build());
        enterTrialButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.yujiancraft.tempering.enter_trial"),
                        button -> click(SpiritTemperingMenu.ENTER_TRIAL_BUTTON))
                .bounds(leftPos + 135, topPos + 132, 104, 22).build());

        presetButton = addRenderableWidget(Button.builder(Component.empty(),
                        button -> click(SpiritTemperingMenu.PRESET_BUTTON))
                .bounds(leftPos + 135, topPos + 20, 104, 18).build());
        glowButton = addRenderableWidget(Button.builder(Component.empty(),
                        button -> click(SpiritTemperingMenu.GLOW_BUTTON))
                .bounds(leftPos + 135, topPos + 42, 104, 18).build());
        flipButton = addRenderableWidget(Button.builder(Component.empty(),
                        button -> click(SpiritTemperingMenu.FLIP_BUTTON))
                .bounds(leftPos + 135, topPos + 64, 104, 18).build());
        scaleDownButton = addRenderableWidget(Button.builder(Component.literal("−"),
                        button -> click(SpiritTemperingMenu.SCALE_DOWN_BUTTON))
                .bounds(leftPos + 135, topPos + 86, 28, 18).build());
        scaleUpButton = addRenderableWidget(Button.builder(Component.literal("+"),
                        button -> click(SpiritTemperingMenu.SCALE_UP_BUTTON))
                .bounds(leftPos + 211, topPos + 86, 28, 18).build());
        radiusDownButton = addRenderableWidget(Button.builder(Component.literal("−"),
                        button -> click(SpiritTemperingMenu.AURA_RADIUS_DOWN_BUTTON))
                .bounds(leftPos + 135, topPos + 108, 28, 18).build());
        radiusUpButton = addRenderableWidget(Button.builder(Component.literal("+"),
                        button -> click(SpiritTemperingMenu.AURA_RADIUS_UP_BUTTON))
                .bounds(leftPos + 211, topPos + 108, 28, 18).build());
        lengthDownButton = addRenderableWidget(Button.builder(Component.literal("−"),
                        button -> click(SpiritTemperingMenu.AURA_LENGTH_DOWN_BUTTON))
                .bounds(leftPos + 135, topPos + 130, 28, 18).build());
        lengthUpButton = addRenderableWidget(Button.builder(Component.literal("+"),
                        button -> click(SpiritTemperingMenu.AURA_LENGTH_UP_BUTTON))
                .bounds(leftPos + 211, topPos + 130, 28, 18).build());
        confirmShapeButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.yujiancraft.tempering.confirm_shaping"), button -> {
                            click(SpiritTemperingMenu.CONFIRM_SHAPE_BUTTON);
                            shapingPage = false;
                            refreshButtons();
                        }).bounds(leftPos + 135, topPos + 155, 74, 20).build());
        backButton = addRenderableWidget(Button.builder(Component.literal("↩"), button -> {
                    shapingPage = false;
                    refreshButtons();
                }).bounds(leftPos + 213, topPos + 155, 26, 20).build());
        refreshButtons();
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        previewTicks++;
        if (!draggingPreview && ++previewIdleTicks > 60) previewYaw += 0.45F;
        refreshButtons();
    }

    private void refreshButtons() {
        if (shapingButton == null) return;
        boolean hasItems = menu.hasRequiredItems();
        shapingButton.visible = !shapingPage;
        enterTrialButton.visible = !shapingPage;
        shapingButton.active = hasItems;
        enterTrialButton.active = hasItems && menu.shapeConfirmed();

        presetButton.visible = shapingPage;
        glowButton.visible = shapingPage;
        flipButton.visible = shapingPage;
        scaleDownButton.visible = shapingPage;
        scaleUpButton.visible = shapingPage;
        radiusDownButton.visible = shapingPage;
        radiusUpButton.visible = shapingPage;
        lengthDownButton.visible = shapingPage;
        lengthUpButton.visible = shapingPage;
        confirmShapeButton.visible = shapingPage;
        backButton.visible = shapingPage;
        confirmShapeButton.active = hasItems;

        presetButton.setMessage(Component.translatable(menu.preset().translationKey()));
        glowButton.setMessage(Component.translatable(menu.glowMode().translationKey()));
        flipButton.setMessage(Component.translatable("screen.yujiancraft.tempering.flip_value",
                Component.translatable(menu.flipped() ? "options.on" : "options.off")));
        scaleDownButton.active = menu.scalePercent() > 50;
        scaleUpButton.active = menu.scalePercent() < 200;
        radiusDownButton.active = menu.auraRadiusPercent() > 50;
        radiusUpButton.active = menu.auraRadiusPercent() < 250;
        lengthDownButton.active = menu.auraLengthPercent() > 50;
        lengthUpButton.active = menu.auraLengthPercent() < 250;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF171520);
        graphics.fill(x + 5, y + 16, x + 124, y + 181, 0xFF282236);
        graphics.fill(x + 128, y + 16, x + 245, y + 181, 0xFF211E2B);
        graphics.fill(x + 249, y + 16, x + 355, y + 181, 0xFF181D27);
        graphics.fill(x + 252, y + 31, x + 352, y + 178, 0xFF0D1118);
        graphics.fill(x + 93, y + 183, x + 267, y + 270, 0xFF20202A);
        drawSlot(graphics, x + 102, y + 41);
        drawSlot(graphics, x + 102, y + 77);
        graphics.fill(x + 109, y + 63, x + 115, y + 72, 0xFFB86CFF);
        graphics.fill(x + 106, y + 66, x + 118, y + 69, 0xFF6DE6FF);
    }

    private static void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF0C0F14);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF5B506C);
        graphics.fill(x + 2, y + 2, x + 16, y + 16, 0xFF12131A);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 7, 5, 0xF1E9FF, false);
        graphics.drawString(font, Component.translatable("screen.yujiancraft.tempering.weapon"),
                8, 31, 0xC8B9DD, false);
        graphics.drawString(font, Component.translatable("screen.yujiancraft.tempering.core"),
                8, 67, 0xC8B9DD, false);
        graphics.drawCenteredString(font, Component.translatable("screen.yujiancraft.tempering.preview"),
                302, 20, 0x83E8F5);
        if (shapingPage) renderShapingLabels(graphics); else renderRitualLabels(graphics);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xB8C4CE, false);
    }

    private void renderRitualLabels(GuiGraphics graphics) {
        ItemStack source = menu.getSlot(0).getItem();
        int attempts = WanxiangSwordData.temperCount(source);
        graphics.drawCenteredString(font, Component.translatable("screen.yujiancraft.tempering.ritual"),
                187, 26, 0x83E8F5);
        graphics.drawWordWrap(font, Component.translatable("screen.yujiancraft.tempering.ritual_rule"),
                135, 43, 104, 0xC8B9DD);
        graphics.drawCenteredString(font, Component.translatable("screen.yujiancraft.tempering.attempts",
                attempts, WanxiangSwordData.MAX_TEMPERINGS), 187, 87,
                attempts >= WanxiangSwordData.MAX_TEMPERINGS ? 0xF08F7D : 0xE2C768);
        graphics.drawCenteredString(font, Component.translatable(menu.shapeConfirmed()
                        ? "screen.yujiancraft.tempering.shaping_ready"
                        : "screen.yujiancraft.tempering.shaping_needed"),
                187, 123, menu.shapeConfirmed() ? 0x7CE6B2 : 0xB9A7C6);
        graphics.drawWordWrap(font, Component.translatable("screen.yujiancraft.tempering.cost_entry",
                menu.experienceCost()), 135, 158, 104, 0xE2C768);
    }

    private void renderShapingLabels(GuiGraphics graphics) {
        graphics.drawCenteredString(font, Component.translatable("screen.yujiancraft.tempering.shaping"),
                187, 8, 0x83E8F5);
        graphics.drawCenteredString(font, Component.translatable("screen.yujiancraft.tempering.scale_compact",
                menu.scalePercent()), 187, 91, 0xD8D0E2);
        graphics.drawCenteredString(font, Component.translatable("screen.yujiancraft.tempering.radius_compact",
                menu.auraRadiusPercent()), 187, 113, 0xD8D0E2);
        graphics.drawCenteredString(font, Component.translatable("screen.yujiancraft.tempering.length_compact",
                menu.auraLengthPercent()), 187, 135, 0xD8D0E2);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderPreview(graphics, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderPreview(GuiGraphics graphics, float partialTick) {
        if (minecraft == null || minecraft.level == null || !ClientOptions.workbenchPreview()) return;
        ItemStack preview = previewStack();
        if (!FlyingSwordItem.isUsableFlyingSword(preview)) return;
        if (previewSword == null || previewSword.level() != minecraft.level) {
            previewSword = new FlyingSwordEntity(ModEntities.FLYING_SWORD.get(), minecraft.level);
        }
        previewSword.configureVisualPreview(preview);
        previewSword.tickCount = previewTicks;
        previewSword.setXRot(-90.0F);
        previewSword.xRotO = -90.0F;
        previewSword.setYRot(0.0F);
        previewSword.yRotO = 0.0F;
        graphics.flush();
        graphics.enableScissor(leftPos + 252, topPos + 31, leftPos + 352, topPos + 178);
        Lighting.setupForEntityInInventory();
        graphics.pose().pushPose();
        graphics.pose().translate(leftPos + 302.0F, topPos + 105.0F, 180.0F);
        float scale = 47.0F * previewZoom;
        graphics.pose().scale(scale, -scale, scale);
        graphics.pose().mulPose(Axis.XP.rotationDegrees(previewPitch));
        graphics.pose().mulPose(Axis.YP.rotationDegrees(previewYaw));
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        minecraft.getEntityRenderDispatcher().render(previewSword, 0.0D, 0.0D, 0.0D,
                0.0F, partialTick, graphics.pose(), buffers, LightTexture.FULL_BRIGHT);
        buffers.endBatch();
        graphics.pose().popPose();
        Lighting.setupFor3DItems();
        graphics.disableScissor();
    }

    private ItemStack previewStack() {
        ItemStack source = menu.getSlot(0).getItem();
        if (source.isEmpty()) return ItemStack.EMPTY;
        ItemStack core = menu.getSlot(1).getItem();
        double damage = Math.max(1.0D, WanxiangSwordData.pierceDamage(source));
        if (core.getItem() instanceof FlyingSwordItem sword) {
            return WanxiangSwordData.preview(source, sword.getMaterialType(), menu.preset(), menu.glowMode(),
                    menu.flipped(), menu.scalePercent(), menu.auraRadiusPercent(), menu.auraLengthPercent(), damage);
        }
        if (WanxiangSwordData.isUsable(source)) {
            ItemStack preview = source.copy();
            return WanxiangSwordData.applyShape(preview, menu.preset(), menu.glowMode(), menu.flipped(),
                    menu.scalePercent(), menu.auraRadiusPercent(), menu.auraLengthPercent());
        }
        return ItemStack.EMPTY;
    }

    private boolean overPreview(double mouseX, double mouseY) {
        return mouseX >= leftPos + 252 && mouseX < leftPos + 352
                && mouseY >= topPos + 31 && mouseY < topPos + 178;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && overPreview(mouseX, mouseY)) {
            draggingPreview = true;
            previewIdleTicks = 0;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingPreview && button == 0) {
            previewYaw += (float) dragX * 1.6F;
            previewPitch = Math.max(-35.0F, Math.min(35.0F,
                    previewPitch - (float) dragY * 1.15F));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingPreview = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (overPreview(mouseX, mouseY)) {
            previewZoom = Math.max(0.65F, Math.min(1.75F, previewZoom + (float) delta * 0.08F));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
}
