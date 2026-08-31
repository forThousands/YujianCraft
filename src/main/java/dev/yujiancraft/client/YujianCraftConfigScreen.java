package dev.yujiancraft.client;

import dev.yujiancraft.combat.AttackMode;
import dev.yujiancraft.combat.SwordSettings;
import dev.yujiancraft.combat.TargetingMode;
import dev.yujiancraft.combat.technique.TechniqueMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Compact category-based settings screen; Ctrl+I no longer presents one overfilled list. */
public final class YujianCraftConfigScreen extends Screen {
    private enum Page { COMBAT, VISUAL, PROTECTION }

    private final Screen parent;
    private final List<Button> serverEditingButtons = new ArrayList<>();
    private SwordSettings settings = SwordSettings.defaults();
    private Page page = Page.COMBAT;
    private Button targetingButton;
    private Button attackButton;
    private Button techniqueButton;
    private Button thirdPersonButton;
    private Button swordRidingButton;
    private Button swordGlowButton;
    private Button glowBrightnessButton;
    private Button developerButton;
    private boolean synced;

    public YujianCraftConfigScreen(Screen parent) {
        super(Component.translatable("screen.yujiancraft.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ClientOptions.load();
        settings = ClientSettingsState.get();
        buildPage();
        ClientSettingsState.requestFromServer();
    }

    private void buildPage() {
        serverEditingButtons.clear();
        targetingButton = attackButton = techniqueButton = null;
        thirdPersonButton = swordRidingButton = swordGlowButton = glowBrightnessButton = null;
        int centre = width / 2;
        int top = height / 2 - 56;
        addTab(centre - 147, top - 32, 96, Page.COMBAT, "screen.yujiancraft.config.tab.combat");
        addTab(centre - 48, top - 32, 96, Page.VISUAL, "screen.yujiancraft.config.tab.visual");
        addTab(centre + 51, top - 32, 96, Page.PROTECTION, "screen.yujiancraft.config.tab.protection");

        if (page == Page.COMBAT) buildCombat(centre, top);
        else if (page == Page.VISUAL) buildVisual(centre, top);
        else buildProtection(centre, top);

        developerButton = addRenderableWidget(Button.builder(Component.translatable(
                        "screen.yujiancraft.config.developer"), button -> minecraft.setScreen(new AdminBalanceScreen(this)))
                .bounds(centre - 145, top + 126, 140, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(centre + 5, top + 126, 140, 20).build());
        refreshLabels();
        setEditingEnabled(synced);
    }

    private void addTab(int x, int y, int width, Page destination, String key) {
        Button tab = addRenderableWidget(Button.builder(Component.translatable(key), button -> switchPage(destination))
                .bounds(x, y, width, 20).build());
        tab.active = page != destination;
    }

    private void switchPage(Page destination) {
        if (page == destination) return;
        page = destination;
        clearWidgets();
        buildPage();
    }

    private void buildCombat(int centre, int top) {
        targetingButton = addModeRow(centre, top, () -> update(new SwordSettings(
                        settings.minimumDockTicks(), settings.automaticTargetRadius(), settings.crosshairLockRadius(),
                        settings.targetingMode().next(), settings.attackMode(), settings.techniqueMode())),
                () -> update(new SwordSettings(settings.minimumDockTicks(), settings.automaticTargetRadius(),
                        settings.crosshairLockRadius(), TargetingMode.CROSSHAIR_LOCK, settings.attackMode(),
                        settings.techniqueMode())), true);
        attackButton = addModeRow(centre, top + 24, () -> update(new SwordSettings(
                        settings.minimumDockTicks(), settings.automaticTargetRadius(), settings.crosshairLockRadius(),
                        settings.targetingMode(), settings.attackMode().next(), settings.techniqueMode())),
                () -> update(new SwordSettings(settings.minimumDockTicks(), settings.automaticTargetRadius(),
                        settings.crosshairLockRadius(), settings.targetingMode(), AttackMode.SORTIE,
                        settings.techniqueMode())), true);
        techniqueButton = addModeRow(centre, top + 48, () -> update(new SwordSettings(
                        settings.minimumDockTicks(), settings.automaticTargetRadius(), settings.crosshairLockRadius(),
                        settings.targetingMode(), settings.attackMode(), settings.techniqueMode().next())),
                () -> update(new SwordSettings(settings.minimumDockTicks(), settings.automaticTargetRadius(),
                        settings.crosshairLockRadius(), settings.targetingMode(), settings.attackMode(),
                        TechniqueMode.PIERCE)), true);
        addRenderableWidget(Button.builder(Component.translatable("screen.yujiancraft.config.reset_page"), button -> {
                    update(new SwordSettings(settings.minimumDockTicks(), settings.automaticTargetRadius(),
                            settings.crosshairLockRadius(), TargetingMode.CROSSHAIR_LOCK, AttackMode.SORTIE,
                            TechniqueMode.PIERCE)); refreshLabels();
                }).bounds(centre - 145, top + 79, 290, 20).build());
    }

    private void buildVisual(int centre, int top) {
        thirdPersonButton = addModeRow(centre, top,
                () -> ClientOptions.setOptimizedThirdPerson(!ClientOptions.optimizedThirdPerson()),
                () -> ClientOptions.setOptimizedThirdPerson(false), false);
        swordRidingButton = addModeRow(centre, top + 24, this::toggleSwordRidingOption,
                () -> setSwordRidingOption(false), false);
        swordGlowButton = addModeRow(centre, top + 48,
                () -> ClientOptions.setSwordBodyGlow(!ClientOptions.swordBodyGlow()),
                () -> ClientOptions.setSwordBodyGlow(ClientOptions.DEFAULT_SWORD_BODY_GLOW), false);
        glowBrightnessButton = addModeRow(centre, top + 72,
                () -> ClientOptions.setGlowBrightness(ClientOptions.glowBrightness().next()),
                () -> ClientOptions.setGlowBrightness(ClientOptions.DEFAULT_GLOW_BRIGHTNESS), false);
        addRenderableWidget(Button.builder(Component.translatable("screen.yujiancraft.config.reset_page"), button -> {
                    ClientOptions.setOptimizedThirdPerson(false); setSwordRidingOption(true);
                    ClientOptions.setSwordBodyGlow(ClientOptions.DEFAULT_SWORD_BODY_GLOW);
                    ClientOptions.setGlowBrightness(ClientOptions.DEFAULT_GLOW_BRIGHTNESS); refreshLabels();
                }).bounds(centre - 145, top + 101, 290, 20).build());
    }

    private void buildProtection(int centre, int top) {
        addRenderableWidget(Button.builder(Component.translatable("screen.yujiancraft.config.protection.manage"),
                        button -> minecraft.setScreen(new TargetProtectionScreen(this)))
                .bounds(centre - 145, top + 60, 290, 22).build());
    }

    private Button addModeRow(int centre, int y, Runnable cycle, Runnable reset, boolean serverControlled) {
        Button mode = addRenderableWidget(Button.builder(Component.empty(), button -> {
                    cycle.run(); refreshLabels();
                }).bounds(centre - 145, y, 240, 20).build());
        Button resetButton = addRenderableWidget(Button.builder(Component.translatable(
                        "screen.yujiancraft.config.reset_short"), button -> { reset.run(); refreshLabels(); })
                .bounds(centre + 99, y, 46, 20).build());
        if (serverControlled) { serverEditingButtons.add(mode); serverEditingButtons.add(resetButton); }
        return mode;
    }

    private void update(SwordSettings updated) {
        if (!synced) return;
        settings = updated; refreshLabels(); ClientSettingsState.update(updated);
    }

    private void toggleSwordRidingOption() { setSwordRidingOption(!ClientOptions.swordRidingEnabled()); }

    private void setSwordRidingOption(boolean enabled) {
        if (!enabled && ClientSwordRidingState.isActive()) {
            dev.yujiancraft.network.ModNetwork.CHANNEL.sendToServer(
                    new dev.yujiancraft.network.ModNetwork.ToggleSwordRidingPacket(false));
        }
        ClientOptions.setSwordRidingEnabled(enabled);
    }

    public void onSettingsSynced(SwordSettings syncedSettings) {
        settings = syncedSettings; synced = true; setEditingEnabled(true); refreshLabels();
    }

    private void setEditingEnabled(boolean enabled) {
        serverEditingButtons.forEach(button -> button.active = enabled);
        if (developerButton != null) developerButton.visible = enabled && ClientSettingsState.canEditBalance()
                && ClientOptions.showDeveloperOptions();
    }

    private void refreshLabels() {
        if (targetingButton != null) targetingButton.setMessage(Component.translatable(
                "screen.yujiancraft.config.targeting", Component.translatable(settings.targetingMode().translationKey())));
        if (attackButton != null) attackButton.setMessage(Component.translatable(
                "screen.yujiancraft.config.attack", Component.translatable(settings.attackMode().translationKey())));
        if (techniqueButton != null) techniqueButton.setMessage(Component.translatable(
                "screen.yujiancraft.config.technique", Component.translatable(settings.techniqueMode().translationKey())));
        if (thirdPersonButton != null) thirdPersonButton.setMessage(ClientModCompatibility.isShoulderSurfingLoaded()
                ? Component.translatable("screen.yujiancraft.config.optimized_third_person_external")
                : Component.translatable("screen.yujiancraft.config.optimized_third_person",
                Component.translatable(ClientOptions.optimizedThirdPerson() ? "options.on" : "options.off")));
        if (swordRidingButton != null) swordRidingButton.setMessage(Component.translatable(
                "screen.yujiancraft.config.sword_riding",
                Component.translatable(ClientOptions.swordRidingEnabled() ? "options.on" : "options.off")));
        if (swordGlowButton != null) swordGlowButton.setMessage(Component.translatable(
                "screen.yujiancraft.config.sword_glow",
                Component.translatable(ClientOptions.swordBodyGlow() ? "options.on" : "options.off")));
        if (glowBrightnessButton != null) glowBrightnessButton.setMessage(Component.translatable(
                "screen.yujiancraft.config.glow_brightness",
                Component.translatable(ClientOptions.glowBrightness().translationKey())));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int top = height / 2 - 56;
        graphics.drawCenteredString(font, title, width / 2, top - 55, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.yujiancraft.config.page_description." +
                page.name().toLowerCase()), width / 2, top - 43, 0xA7DCD6);
        if (page == Page.PROTECTION) {
            int textWidth = Math.min(480, this.width - 36);
            graphics.drawWordWrap(font, Component.translatable("screen.yujiancraft.config.protection.description"),
                    (this.width - textWidth) / 2, top + 2, textWidth, 0xD2CFD8);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
}
