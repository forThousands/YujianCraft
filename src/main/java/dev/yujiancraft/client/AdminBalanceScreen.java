package dev.yujiancraft.client;

import dev.yujiancraft.config.EffectConfigGroup;
import dev.yujiancraft.config.EffectParameter;
import dev.yujiancraft.config.SwordBalanceConfig;
import dev.yujiancraft.combat.SwordSettings;
import dev.yujiancraft.material.FlyingSwordMaterial;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class AdminBalanceScreen extends Screen {
    private enum Tab { SWORDS, EFFECTS, FLIGHT, PRESENTATION }

    private final Screen parent;
    private final List<Button> editingButtons = new ArrayList<>();
    private Tab tab = Tab.SWORDS;
    private FlyingSwordMaterial material = FlyingSwordMaterial.IRON;
    private EffectConfigGroup effectGroup = EffectConfigGroup.GLOBAL;
    private int presentationPage;
    private boolean balanceSynced;
    private boolean settingsSynced;
    private boolean requested;

    public AdminBalanceScreen(Screen parent) {
        super(Component.translatable("screen.yujiancraft.balance.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        buildWidgets();
        if (!requested) {
            requested = true;
            ClientBalanceState.requestFromServer();
            ClientSettingsState.requestFromServer();
        }
    }

    private void buildWidgets() {
        clearWidgets();
        editingButtons.clear();
        int panelWidth = Math.min(360, width - 20);
        int left = (width - panelWidth) / 2;
        int top = Math.max(18, height / 2 - 104);

        int tabWidth = (panelWidth - 6) / 4;
        addRenderableWidget(Button.builder(Component.translatable("screen.yujiancraft.balance.tab_swords"),
                        button -> switchTab(Tab.SWORDS)).bounds(left, top, tabWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.yujiancraft.balance.tab_effects"),
                        button -> switchTab(Tab.EFFECTS)).bounds(left + tabWidth + 2, top, tabWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.yujiancraft.balance.tab_flight"),
                        button -> switchTab(Tab.FLIGHT)).bounds(left + (tabWidth + 2) * 2, top, tabWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.yujiancraft.balance.tab_presentation"),
                        button -> switchTab(Tab.PRESENTATION))
                .bounds(left + (tabWidth + 2) * 3, top, panelWidth - (tabWidth + 2) * 3, 20).build());

        if (tab == Tab.SWORDS) buildSwordControls(left, top + 27, panelWidth);
        else if (tab == Tab.EFFECTS) buildEffectControls(left, top + 27, panelWidth);
        else if (tab == Tab.FLIGHT) buildFlightControls(left, top + 27, panelWidth);
        else buildPresentationControls(left, top + 27, panelWidth);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 - 50, top + 181, 100, 20).build());
        setEditingEnabled(balanceSynced && settingsSynced);
    }

    private void buildSwordControls(int left, int top, int panelWidth) {
        for (FlyingSwordMaterial value : FlyingSwordMaterial.values()) {
            Button button = addRenderableWidget(Button.builder(Component.translatable(value.translationKey()),
                            pressed -> { material = value; buildWidgets(); })
                    .bounds(left, top + value.ordinal() * 21, 100, 19).build());
            button.active = balanceSynced && settingsSynced && value != material;
            editingButtons.add(button);
        }
        int controlsLeft = left + 108;
        SwordBalanceConfig.Balance balance = ClientBalanceState.get(material);
        addNumericRow(controlsLeft, top, panelWidth - 108,
                Component.translatable("screen.yujiancraft.balance.damage"), balance.damage(), 0.5D,
                () -> material.defaultDamage(), value -> {
                    SwordBalanceConfig.Balance current = ClientBalanceState.get(material);
                    ClientBalanceState.update(material, value, current.flightSpeed());
                });
        addNumericRow(controlsLeft, top + 25, panelWidth - 108,
                Component.translatable("screen.yujiancraft.balance.speed"), balance.flightSpeed(), 0.05D,
                () -> material.defaultFlightSpeed(), value -> {
                    SwordBalanceConfig.Balance current = ClientBalanceState.get(material);
                    ClientBalanceState.update(material, current.damage(), value);
                });
    }

    private void buildEffectControls(int left, int top, int panelWidth) {
        EffectConfigGroup[] groups = EffectConfigGroup.values();
        for (int index = 0; index < groups.length; index++) {
            EffectConfigGroup group = groups[index];
            Button button = addRenderableWidget(Button.builder(Component.translatable(group.translationKey()),
                            pressed -> { effectGroup = group; buildWidgets(); })
                    .bounds(left, top + index * 21, 100, 19).build());
            button.active = balanceSynced && settingsSynced && group != effectGroup;
            editingButtons.add(button);
        }
        int controlsLeft = left + 108;
        List<EffectParameter> parameters = EffectParameter.forGroup(effectGroup);
        for (int row = 0; row < parameters.size(); row++) {
            EffectParameter parameter = parameters.get(row);
            addNumericRow(controlsLeft, top + row * 25, panelWidth - 108,
                    Component.translatable(parameter.translationKey()), ClientBalanceState.get(parameter),
                    parameter.step(), parameter::defaultValue,
                    value -> ClientBalanceState.update(parameter, value));
        }
    }

    private void buildFlightControls(int left, int top, int panelWidth) {
        Button category = addRenderableWidget(Button.builder(
                        Component.translatable("screen.yujiancraft.balance.flight_group"), button -> { })
                .bounds(left, top, 100, 19).build());
        category.active = false;
        int controlsLeft = left + 108;
        SwordSettings settings = ClientSettingsState.get();
        addNumericRow(controlsLeft, top, panelWidth - 108,
                Component.translatable("screen.yujiancraft.balance.minimum_dock"),
                settings.minimumDockTicks(), 5.0D, () -> SwordSettings.DEFAULT_MINIMUM_DOCK_TICKS,
                value -> updateFlight((int) Math.round(value), settings.automaticTargetRadius(),
                        settings.crosshairLockRadius()));
        addNumericRow(controlsLeft, top + 25, panelWidth - 108,
                Component.translatable("screen.yujiancraft.balance.automatic_radius"),
                settings.automaticTargetRadius(), 1.0D, () -> SwordSettings.DEFAULT_AUTOMATIC_RADIUS,
                value -> updateFlight(settings.minimumDockTicks(), value, settings.crosshairLockRadius()));
        addNumericRow(controlsLeft, top + 50, panelWidth - 108,
                Component.translatable("screen.yujiancraft.balance.lock_radius"),
                settings.crosshairLockRadius(), 1.0D, () -> SwordSettings.DEFAULT_LOCK_RADIUS,
                value -> updateFlight(settings.minimumDockTicks(), settings.automaticTargetRadius(), value));
    }

    private static void updateFlight(int dockTicks, double automaticRadius, double lockRadius) {
        SwordSettings settings = ClientSettingsState.get();
        ClientSettingsState.update(new SwordSettings(dockTicks, automaticRadius, lockRadius,
                settings.targetingMode(), settings.attackMode(), settings.techniqueMode()));
    }

    private void buildPresentationControls(int left, int top, int panelWidth) {
        int pageCount = 4;
        Button category = addRenderableWidget(Button.builder(
                        Component.translatable("screen.yujiancraft.balance.presentation_group_page",
                                presentationPage + 1, pageCount), button -> { })
                .bounds(left, top, 100, 19).build());
        category.active = false;
        Button previous = addRenderableWidget(Button.builder(Component.literal("<"),
                        button -> { presentationPage = Math.max(0, presentationPage - 1); buildWidgets(); })
                .bounds(left, top + 25, 48, 20).build());
        Button next = addRenderableWidget(Button.builder(Component.literal(">"),
                        button -> { presentationPage = Math.min(pageCount - 1, presentationPage + 1); buildWidgets(); })
                .bounds(left + 52, top + 25, 48, 20).build());
        previous.active = presentationPage > 0;
        next.active = presentationPage < pageCount - 1;

        int controlsLeft = left + 108;
        int controlsWidth = panelWidth - 108;
        if (presentationPage == 0) {
            addToggleRow(controlsLeft, top, controlsWidth, "screen.yujiancraft.balance.flight_sound",
                    ClientOptions::flightSound, ClientOptions::setFlightSound, ClientOptions.DEFAULT_FLIGHT_SOUND);
            addToggleRow(controlsLeft, top + 25, controlsWidth, "screen.yujiancraft.balance.sword_trail",
                    ClientOptions::swordTrail, ClientOptions::setSwordTrail, ClientOptions.DEFAULT_SWORD_TRAIL);
            addToggleRow(controlsLeft, top + 50, controlsWidth, "screen.yujiancraft.balance.sword_body_glow",
                    ClientOptions::swordBodyGlow, ClientOptions::setSwordBodyGlow, ClientOptions.DEFAULT_SWORD_BODY_GLOW);
            addToggleRow(controlsLeft, top + 75, controlsWidth, "screen.yujiancraft.balance.inventory_glint",
                    ClientOptions::inventoryGlint, ClientOptions::setInventoryGlint, ClientOptions.DEFAULT_INVENTORY_GLINT);
        } else if (presentationPage == 1) {
            addToggleRow(controlsLeft, top, controlsWidth, "screen.yujiancraft.balance.sword_energy_highlight",
                    ClientOptions::swordEnergyHighlight, ClientOptions::setSwordEnergyHighlight,
                    ClientOptions.DEFAULT_SWORD_ENERGY_HIGHLIGHT);
            addToggleRow(controlsLeft, top + 25, controlsWidth, "screen.yujiancraft.balance.sword_outline",
                    ClientOptions::swordOutline, ClientOptions::setSwordOutline, ClientOptions.DEFAULT_SWORD_OUTLINE);
        } else if (presentationPage == 2) {
            addToggleRow(controlsLeft, top, controlsWidth, "screen.yujiancraft.balance.flame_module_visual",
                    ClientOptions::flameModuleVisual, ClientOptions::setFlameModuleVisual,
                    ClientOptions.DEFAULT_FLAME_MODULE_VISUAL);
            addToggleRow(controlsLeft, top + 25, controlsWidth, "screen.yujiancraft.balance.lightning_module_visual",
                    ClientOptions::lightningModuleVisual, ClientOptions::setLightningModuleVisual,
                    ClientOptions.DEFAULT_LIGHTNING_MODULE_VISUAL);
            addToggleRow(controlsLeft, top + 50, controlsWidth, "screen.yujiancraft.balance.poison_module_visual",
                    ClientOptions::poisonModuleVisual, ClientOptions::setPoisonModuleVisual,
                    ClientOptions.DEFAULT_POISON_MODULE_VISUAL);
            addToggleRow(controlsLeft, top + 75, controlsWidth, "screen.yujiancraft.balance.explosion_module_visual",
                    ClientOptions::explosionModuleVisual, ClientOptions::setExplosionModuleVisual,
                    ClientOptions.DEFAULT_EXPLOSION_MODULE_VISUAL);
        } else {
            addToggleRow(controlsLeft, top, controlsWidth, "screen.yujiancraft.balance.arrow_rain_module_visual",
                    ClientOptions::arrowRainModuleVisual, ClientOptions::setArrowRainModuleVisual,
                    ClientOptions.DEFAULT_ARROW_RAIN_MODULE_VISUAL);
            addToggleRow(controlsLeft, top + 25, controlsWidth, "screen.yujiancraft.balance.hit_impact_visual",
                    ClientOptions::hitImpactVisual, ClientOptions::setHitImpactVisual,
                    ClientOptions.DEFAULT_HIT_IMPACT_VISUAL);
            addToggleRow(controlsLeft, top + 50, controlsWidth, "screen.yujiancraft.balance.workbench_preview",
                    ClientOptions::workbenchPreview, ClientOptions::setWorkbenchPreview,
                    ClientOptions.DEFAULT_WORKBENCH_PREVIEW);
        }
    }

    private void addToggleRow(int left, int y, int width, String translationKey,
                              BooleanSupplier getter, Consumer<Boolean> setter, boolean defaultValue) {
        int resetWidth = 46;
        Button toggle = addRenderableWidget(Button.builder(toggleLabel(translationKey, getter.getAsBoolean()),
                        button -> { setter.accept(!getter.getAsBoolean()); buildWidgets(); })
                .bounds(left, y, width - resetWidth - 3, 20).build());
        Button reset = addRenderableWidget(Button.builder(
                        Component.translatable("screen.yujiancraft.config.reset_short"),
                        button -> { setter.accept(defaultValue); buildWidgets(); })
                .bounds(left + width - resetWidth, y, resetWidth, 20).build());
        editingButtons.add(toggle);
        editingButtons.add(reset);
    }

    private static Component toggleLabel(String translationKey, boolean enabled) {
        return Component.translatable(translationKey,
                Component.translatable(enabled ? "options.on" : "options.off"));
    }

    private void addNumericRow(int left, int y, int width, Component label, double current, double step,
                               ValueSupplier defaultValue, ValueConsumer consumer) {
        int resetWidth = 46;
        int smallWidth = 24;
        int valueWidth = Math.max(70, width - resetWidth - smallWidth * 2 - 9);
        Button minus = addRenderableWidget(Button.builder(Component.literal("-"),
                        button -> { consumer.accept(current - step); buildWidgets(); })
                .bounds(left, y, smallWidth, 20).build());
        Button value = addRenderableWidget(Button.builder(label.copy().append(": ")
                        .append(format(current)), button -> { })
                .bounds(left + smallWidth + 3, y, valueWidth, 20).build());
        value.active = false;
        Button plus = addRenderableWidget(Button.builder(Component.literal("+"),
                        button -> { consumer.accept(current + step); buildWidgets(); })
                .bounds(left + smallWidth + valueWidth + 6, y, smallWidth, 20).build());
        Button reset = addRenderableWidget(Button.builder(
                        Component.translatable("screen.yujiancraft.config.reset_short"),
                        button -> { consumer.accept(defaultValue.get()); buildWidgets(); })
                .bounds(left + width - resetWidth, y, resetWidth, 20).build());
        editingButtons.add(minus);
        editingButtons.add(plus);
        editingButtons.add(reset);
    }

    private static Component format(double value) {
        return Component.literal(Math.abs(value - Math.rint(value)) < 1.0E-6D
                ? Long.toString(Math.round(value)) : String.format(Locale.ROOT, "%.2f", value));
    }

    private void switchTab(Tab newTab) {
        tab = newTab;
        buildWidgets();
    }

    public void onBalanceSynced() {
        balanceSynced = true;
        buildWidgets();
    }

    public void onSettingsSynced() {
        settingsSynced = true;
        buildWidgets();
    }

    private void setEditingEnabled(boolean enabled) {
        for (Button button : editingButtons) {
            if (button.active || !enabled) button.active = enabled;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, Math.max(4, height / 2 - 121), 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @FunctionalInterface
    private interface ValueSupplier { double get(); }

    @FunctionalInterface
    private interface ValueConsumer { void accept(double value); }
}
