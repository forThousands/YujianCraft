package dev.yujiancraft.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import dev.yujiancraft.entity.FlyingSwordEntity;
import dev.yujiancraft.item.FlyingSwordItem;
import dev.yujiancraft.menu.FlyingSwordWorkbenchMenu;
import dev.yujiancraft.registry.ModEntities;
import dev.yujiancraft.upgrade.FlyingSwordModule;
import dev.yujiancraft.upgrade.SwordModuleData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class FlyingSwordWorkbenchScreen extends AbstractContainerScreen<FlyingSwordWorkbenchMenu> {
    private static final int MODULES_PER_PAGE = 6;
    private final List<Button> moduleButtons = new ArrayList<>();
    private Button previousPageButton;
    private Button nextPageButton;
    private int modulePage;
    private FlyingSwordEntity previewSword;
    private int previewTicks;
    private float previewYaw = -28.0F;
    private float previewPitch = 13.0F;
    private float previewZoom = 1.0F;
    private boolean draggingPreview;
    private int previewIdleTicks;

    public FlyingSwordWorkbenchScreen(FlyingSwordWorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 314;
        imageHeight = 230;
        inventoryLabelY = 140;
    }

    @Override
    protected void init() {
        super.init();
        moduleButtons.clear();
        for (FlyingSwordModule module : FlyingSwordModule.values()) {
            int index = module.ordinal();
            Button button = addRenderableWidget(Button.builder(Component.empty(), pressed -> selectModule(index))
                    .bounds(leftPos + 7, topPos + 18 + (index % MODULES_PER_PAGE) * 15, 68, 14).build());
            moduleButtons.add(button);
        }
        previousPageButton = addRenderableWidget(Button.builder(Component.literal("◀"), pressed -> changePage(-1))
                .bounds(leftPos + 7, topPos + 111, 28, 18).build());
        nextPageButton = addRenderableWidget(Button.builder(Component.literal("▶"), pressed -> changePage(1))
                .bounds(leftPos + 67, topPos + 111, 28, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.yujiancraft.workbench.install"),
                        pressed -> clickMenuButton(FlyingSwordWorkbenchMenu.INSTALL_BUTTON))
                .bounds(leftPos + 139, topPos + 55, 82, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.yujiancraft.workbench.remove"),
                        pressed -> clickMenuButton(FlyingSwordWorkbenchMenu.REMOVE_BUTTON))
                .bounds(leftPos + 139, topPos + 79, 82, 20).build());
        refreshModuleButtons();
    }

    private void changePage(int delta) {
        modulePage = Math.max(0, Math.min(pageCount() - 1, modulePage + delta));
        refreshModuleButtons();
    }

    private static int pageCount() {
        return Math.max(1, (FlyingSwordModule.values().length + MODULES_PER_PAGE - 1) / MODULES_PER_PAGE);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        previewTicks++;
        if (!draggingPreview && ++previewIdleTicks > 60) previewYaw += 0.45F;
        refreshModuleButtons();
    }

    private void selectModule(int index) {
        clickMenuButton(index);
    }

    private void clickMenuButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private void refreshModuleButtons() {
        if (moduleButtons.size() != FlyingSwordModule.values().length) return;
        int selected = menu.selectedModule().ordinal();
        for (FlyingSwordModule module : FlyingSwordModule.values()) {
            Button button = moduleButtons.get(module.ordinal());
            boolean onPage = module.ordinal() / MODULES_PER_PAGE == modulePage;
            button.visible = onPage;
            String marker = module.ordinal() == selected ? "▶ " : "  ";
            int level = SwordModuleDataView.level(menu, module);
            Component name = Component.translatable(module.translationKey());
            button.setMessage(Component.literal(marker).append(name)
                    .append(level > 0 ? Component.literal(" " + roman(level)) : Component.empty()));
        }
        if (previousPageButton != null) previousPageButton.active = modulePage > 0;
        if (nextPageButton != null) nextPageButton.active = modulePage + 1 < pageCount();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF171B22);
        graphics.fill(x + 4, y + 15, x + 98, y + 140, 0xFF222A35);
        graphics.fill(x + 101, y + 15, x + 132, y + 105, 0xFF202731);
        graphics.fill(x + 135, y + 15, x + 225, y + 105, 0xFF222A35);
        graphics.fill(x + 229, y + 15, x + 310, y + 140, 0xFF222A35);
        graphics.fill(x + 233, y + 32, x + 306, y + 136, 0xFF0D1219);
        graphics.fill(x + 234, y + 33, x + 305, y + 135, 0xFF151D27);
        graphics.fill(x + 28, y + 145, x + 202, y + 229, 0xFF202731);
        YujianScreenBackground.renderSlotFrames(graphics, menu, x, y);
        graphics.fill(x + 119, y + 52, x + 125, y + 61, 0xFF4CCDE3);
        graphics.fill(x + 116, y + 56, x + 128, y + 58, 0xFF4CCDE3);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 103, 5, 0xE8F7FF, false);
        graphics.drawString(font, Component.translatable("screen.yujiancraft.workbench.sword"), 101, 20,
                0xA8B8C8, false);
        graphics.drawString(font, Component.translatable("screen.yujiancraft.workbench.material"), 99, 56,
                0xA8B8C8, false);
        FlyingSwordModule selected = menu.selectedModule();
        graphics.drawString(font, Component.translatable(selected.translationKey()), 139, 20, 0x62DDF0, false);
        graphics.drawWordWrap(font, Component.translatable(selected.descriptionKey()), 139, 32, 82, 0xB8C4CE);
        graphics.drawString(font, Component.translatable("screen.yujiancraft.workbench.installed",
                menu.selectedLevel() == 0 ? "-" : roman(menu.selectedLevel())), 139, 105, 0xD0D7DF, false);
        Component costs = materialCosts(selected);
        graphics.drawWordWrap(font, Component.translatable("screen.yujiancraft.workbench.required_material", costs),
                158, 118, 63, 0x8FA5B5);
        graphics.drawCenteredString(font, Component.translatable("screen.yujiancraft.workbench.page",
                modulePage + 1, pageCount()), 51, 116, 0x778594);
        graphics.drawCenteredString(font, Component.translatable("screen.yujiancraft.workbench.preview"),
                269, 20, 0x62DDF0);
        if (!ClientOptions.workbenchPreview()) {
            graphics.drawWordWrap(font, Component.translatable("screen.yujiancraft.workbench.preview_disabled"),
                    239, 70, 62, 0x778594);
        } else if (menu.getSlot(0).getItem().isEmpty()) {
            graphics.drawWordWrap(font, Component.translatable("screen.yujiancraft.workbench.preview_empty"),
                    239, 70, 62, 0x778594);
        }
        graphics.drawString(font, playerInventoryTitle, 35, inventoryLabelY, 0xB8C4CE, false);
    }

    private static Component materialCosts(FlyingSwordModule module) {
        if (module.maxLevel() == 1) {
            return Component.literal(Integer.toString(module.materialCountForLevel(1)));
        }
        return Component.literal(module.materialCountForLevel(1) + " / "
                + module.materialCountForLevel(2) + " / " + module.materialCountForLevel(3));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        YujianScreenBackground.render(graphics, width, height);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderSwordPreview(graphics, partialTick);
        renderModuleMaterials(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBg(graphics, partialTick, mouseX, mouseY);
    }

    private void renderSwordPreview(GuiGraphics graphics, float partialTick) {
        if (!ClientOptions.workbenchPreview() || minecraft == null || minecraft.level == null) return;
        ItemStack stack = previewStack();
        if (!FlyingSwordItem.isUsableFlyingSword(stack)) return;
        if (previewSword == null || previewSword.level() != minecraft.level) {
            previewSword = new FlyingSwordEntity(ModEntities.FLYING_SWORD.get(), minecraft.level);
        }
        previewSword.configureVisualPreview(stack);
        previewSword.tickCount = previewTicks;
        previewSword.setXRot(-90.0F);
        previewSword.xRotO = -90.0F;
        previewSword.setYRot(0.0F);
        previewSword.yRotO = 0.0F;

        graphics.flush();
        graphics.enableScissor(leftPos + 233, topPos + 32, leftPos + 306, topPos + 136);
        Lighting.setupForEntityInInventory();
        graphics.pose().pushPose();
        graphics.pose().translate(leftPos + 269.5F, topPos + 94.0F, 180.0F);
        float scale = 43.0F * previewZoom;
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
        ItemStack preview = source.copy();
        ItemStack input = menu.getSlot(1).getItem();
        FlyingSwordModule candidate = FlyingSwordModule.fromIngredient(input);
        if (candidate != null) {
            int level = candidate.levelForAvailableCount(input.getCount());
            if (level > 0) SwordModuleData.setLevel(preview, candidate, level);
        }
        return preview;
    }

    private boolean isOverPreview(double mouseX, double mouseY) {
        return mouseX >= leftPos + 233 && mouseX < leftPos + 306
                && mouseY >= topPos + 32 && mouseY < topPos + 136;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && ClientOptions.workbenchPreview() && isOverPreview(mouseX, mouseY)) {
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
            previewPitch = Math.max(-35.0F, Math.min(35.0F, previewPitch - (float) dragY * 1.15F));
            previewIdleTicks = 0;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingPreview) {
            draggingPreview = false;
            previewIdleTicks = 0;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (ClientOptions.workbenchPreview() && isOverPreview(mouseX, mouseY)) {
            previewZoom = Math.max(0.68F, Math.min(1.48F, previewZoom + (float) scrollY * 0.08F));
            previewIdleTicks = 0;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void renderModuleMaterials(GuiGraphics graphics, int mouseX, int mouseY) {
        for (FlyingSwordModule module : FlyingSwordModule.values()) {
            Button button = moduleButtons.get(module.ordinal());
            if (!button.visible) continue;
            ItemStack ingredient = new ItemStack(module.ingredient());
            int iconX = leftPos + 79;
            int iconY = button.getY() - 1;
            graphics.renderItem(ingredient, iconX, iconY);
            if (mouseX >= iconX && mouseX < iconX + 16 && mouseY >= iconY && mouseY < iconY + 16) {
                graphics.renderTooltip(font, ingredient, mouseX, mouseY);
            }
        }
        ItemStack selectedIngredient = new ItemStack(menu.selectedModule().ingredient());
        int selectedX = leftPos + 139;
        int selectedY = topPos + 116;
        graphics.renderItem(selectedIngredient, selectedX, selectedY);
        if (mouseX >= selectedX && mouseX < selectedX + 16
                && mouseY >= selectedY && mouseY < selectedY + 16) {
            graphics.renderTooltip(font, selectedIngredient, mouseX, mouseY);
        }
    }

    private static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> Integer.toString(level);
        };
    }

    private static final class SwordModuleDataView {
        private static int level(FlyingSwordWorkbenchMenu menu, FlyingSwordModule module) {
            return dev.yujiancraft.upgrade.SwordModuleData.getLevel(menu.getSlot(0).getItem(), module);
        }
    }
}
