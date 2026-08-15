package dev.swordflight.client;

import dev.swordflight.combat.AttackMode;
import dev.swordflight.combat.SwordSettings;
import dev.swordflight.combat.TargetingMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class SwordflightConfigScreen extends Screen {
    private final Screen parent;
    private final List<Button> serverEditingButtons = new ArrayList<>();
    private SwordSettings settings = SwordSettings.defaults();
    private Button targetingButton;
    private Button attackButton;
    private Button thirdPersonButton;
    private Button developerButton;
    private boolean synced;

    public SwordflightConfigScreen(Screen parent) {
        super(Component.translatable("screen.swordflight.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ClientOptions.load();
        settings = ClientSettingsState.get();
        serverEditingButtons.clear();
        int centerX = width / 2;
        int top = height / 2 - 58;

        targetingButton = addModeRow(centerX, top, () -> update(new SwordSettings(
                        settings.minimumDockTicks(), settings.automaticTargetRadius(), settings.crosshairLockRadius(),
                        settings.targetingMode().next(), settings.attackMode())),
                () -> update(new SwordSettings(settings.minimumDockTicks(), settings.automaticTargetRadius(),
                        settings.crosshairLockRadius(), TargetingMode.AUTOMATIC, settings.attackMode())), true);

        attackButton = addModeRow(centerX, top + 25, () -> update(new SwordSettings(
                        settings.minimumDockTicks(), settings.automaticTargetRadius(), settings.crosshairLockRadius(),
                        settings.targetingMode(), settings.attackMode().next())),
                () -> update(new SwordSettings(settings.minimumDockTicks(), settings.automaticTargetRadius(),
                        settings.crosshairLockRadius(), settings.targetingMode(), AttackMode.SORTIE)), true);

        thirdPersonButton = addModeRow(centerX, top + 50,
                () -> ClientOptions.setOptimizedThirdPerson(!ClientOptions.optimizedThirdPerson()),
                () -> ClientOptions.setOptimizedThirdPerson(false), false);

        addRenderableWidget(Button.builder(Component.translatable("screen.swordflight.config.reset_all"), button -> {
                    update(new SwordSettings(settings.minimumDockTicks(), settings.automaticTargetRadius(),
                            settings.crosshairLockRadius(), TargetingMode.AUTOMATIC, AttackMode.SORTIE));
                    ClientOptions.setOptimizedThirdPerson(false);
                    refreshLabels();
                }).bounds(centerX - 145, top + 84, 92, 20).build());
        developerButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.swordflight.config.developer"),
                        button -> minecraft.setScreen(new AdminBalanceScreen(this)))
                .bounds(centerX - 47, top + 84, 92, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(centerX + 51, top + 84, 94, 20).build());

        refreshLabels();
        setEditingEnabled(false);
        ClientSettingsState.requestFromServer();
    }

    private Button addModeRow(int centerX, int y, Runnable cycle, Runnable reset, boolean serverControlled) {
        Button mode = addRenderableWidget(Button.builder(Component.empty(), button -> {
                    cycle.run();
                    refreshLabels();
                }).bounds(centerX - 145, y, 240, 20).build());
        Button resetButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.swordflight.config.reset_short"), button -> {
                            reset.run();
                            refreshLabels();
                        }).bounds(centerX + 99, y, 46, 20).build());
        if (serverControlled) {
            serverEditingButtons.add(mode);
            serverEditingButtons.add(resetButton);
        }
        return mode;
    }

    private void update(SwordSettings updated) {
        if (!synced) return;
        settings = updated;
        refreshLabels();
        ClientSettingsState.update(updated);
    }

    public void onSettingsSynced(SwordSettings syncedSettings) {
        settings = syncedSettings;
        synced = true;
        setEditingEnabled(true);
        refreshLabels();
    }

    private void setEditingEnabled(boolean enabled) {
        serverEditingButtons.forEach(button -> button.active = enabled);
        if (developerButton != null) {
            developerButton.visible = enabled && ClientSettingsState.canEditBalance()
                    && ClientOptions.showDeveloperOptions();
        }
    }

    private void refreshLabels() {
        if (targetingButton != null) {
            targetingButton.setMessage(Component.translatable("screen.swordflight.config.targeting",
                    Component.translatable(settings.targetingMode().translationKey())));
        }
        if (attackButton != null) {
            attackButton.setMessage(Component.translatable("screen.swordflight.config.attack",
                    Component.translatable(settings.attackMode().translationKey())));
        }
        if (thirdPersonButton != null) {
            thirdPersonButton.setMessage(Component.translatable("screen.swordflight.config.optimized_third_person",
                    Component.translatable(ClientOptions.optimizedThirdPerson() ? "options.on" : "options.off")));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int top = height / 2 - 58;
        graphics.drawCenteredString(font, title, width / 2, top - 30, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.swordflight.config.description"),
                width / 2, top - 16, 0xA0A0A0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
