package dev.yujiancraft.client;

import dev.yujiancraft.combat.combo.ComboStyle;
import dev.yujiancraft.combat.technique.TechniqueMode;
import dev.yujiancraft.formation.FormationMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Mouse-driven direct selection for the three formation, six ranged-art and five combo choices.
 * It deliberately does not pause the world; opening a Screen releases the cursor for selection.
 */
public final class YujianQuickSwitchScreen extends Screen {
    private static final int CYAN = 0xFF78E8E1;
    private static final int CYAN_BRIGHT = 0xFFD9FAF6;
    private static final int PANEL = 0xD9102025;
    private static final int PANEL_INNER = 0xB8173036;
    private static final int BUTTON = 0xB21A353B;
    private static final int BUTTON_HOVER = 0xD12A5158;
    private static final int DISABLED = 0xB22A2E30;
    private static final int DISABLED_BORDER = 0xFF657074;
    private static final int DISABLED_TEXT = 0xFF8D9699;
    private static final int GAP = 6;

    private static final FormationMode[] FORMATIONS = {
            FormationMode.FAN_ALIGNED, FormationMode.RING, FormationMode.FAN
    };
    private static final TechniqueMode[] TECHNIQUES = TechniqueMode.values();
    private static final ComboStyle[] COMBO_STYLES = ComboStyle.values();

    private Layout layout;

    public YujianQuickSwitchScreen() {
        super(Component.translatable("screen.yujiancraft.quick_switch.title"));
    }

    @Override
    protected void init() {
        ClientQuickSwitchState.request();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        layout = Layout.create(width, height);
        boolean comboActive = ClientComboState.isLocalActive();

        graphics.fill(0, 0, width, height, 0x72030B0E);
        graphics.fill(layout.panel.x, layout.panel.y, layout.panel.right(), layout.panel.bottom(), PANEL);
        drawBorder(graphics, layout.panel, CYAN, 1);
        Rect inner = layout.panel.inset(4);
        graphics.fill(inner.x, inner.y, inner.right(), inner.bottom(), PANEL_INNER);
        graphics.drawCenteredString(font, title, width / 2, layout.panel.y + 9, CYAN_BRIGHT);

        drawSectionTitle(graphics, "screen.yujiancraft.quick_switch.section.formation",
                layout.formationColumn, comboActive);
        drawSectionTitle(graphics, "screen.yujiancraft.quick_switch.section.technique",
                layout.techniqueColumn, comboActive);
        drawSectionTitle(graphics, "screen.yujiancraft.quick_switch.section.combo",
                layout.comboColumn, false);

        FormationMode selectedFormation = ClientQuickSwitchState.formation();
        for (int index = 0; index < FORMATIONS.length; index++) {
            FormationMode mode = FORMATIONS[index];
            drawChoice(graphics, layout.formations[index],
                    Component.translatable("screen.yujiancraft.quick_switch.formation."
                            + mode.serializedName()),
                    mode == selectedFormation, !comboActive,
                    layout.formations[index].contains(mouseX, mouseY));
        }

        TechniqueMode selectedTechnique = ClientQuickSwitchState.technique();
        for (int index = 0; index < TECHNIQUES.length; index++) {
            TechniqueMode mode = TECHNIQUES[index];
            drawChoice(graphics, layout.techniques[index], Component.translatable(mode.translationKey()),
                    mode == selectedTechnique, !comboActive,
                    layout.techniques[index].contains(mouseX, mouseY));
        }

        drawChoice(graphics, layout.comboToggle, Component.translatable(comboActive
                        ? "screen.yujiancraft.quick_switch.combo.exit"
                        : "screen.yujiancraft.quick_switch.combo.enter"),
                comboActive, true, layout.comboToggle.contains(mouseX, mouseY));

        ComboStyle selectedStyle = ClientQuickSwitchState.comboStyle();
        for (int index = 0; index < COMBO_STYLES.length; index++) {
            ComboStyle style = COMBO_STYLES[index];
            drawChoice(graphics, layout.comboStyles[index], Component.translatable(style.translationKey()),
                    style == selectedStyle, true, layout.comboStyles[index].contains(mouseX, mouseY));
        }

        drawChoice(graphics, layout.exit, Component.translatable(
                        "screen.yujiancraft.quick_switch.close"),
                false, true, layout.exit.contains(mouseX, mouseY));

        if (comboActive) {
            Component locked = Component.translatable("screen.yujiancraft.quick_switch.combo_locked");
            drawFittedCentered(graphics, locked, layout.lockHint, DISABLED_TEXT);
            if (hoveringAny(layout.formations, mouseX, mouseY)
                    || hoveringAny(layout.techniques, mouseX, mouseY)) {
                graphics.renderTooltip(font, locked, mouseX, mouseY);
            }
        }
    }

    private void drawSectionTitle(GuiGraphics graphics, String key, Rect column, boolean disabled) {
        Rect titleArea = new Rect(column.x, column.y, column.width, 11);
        drawFittedCentered(graphics, Component.translatable(key), titleArea,
                disabled ? DISABLED_TEXT : CYAN);
    }

    private void drawChoice(GuiGraphics graphics, Rect area, Component label,
                            boolean selected, boolean enabled, boolean hovered) {
        int background = enabled ? (hovered ? BUTTON_HOVER : BUTTON) : DISABLED;
        int border = enabled ? (selected ? CYAN_BRIGHT : CYAN) : DISABLED_BORDER;
        graphics.fill(area.x, area.y, area.right(), area.bottom(), background);
        if (enabled && hovered) {
            Rect glow = area.inset(2);
            graphics.fill(glow.x, glow.y, glow.right(), glow.bottom(), 0x246DE6E0);
        }
        drawBorder(graphics, area, border, selected ? 3 : 1);
        drawFittedCentered(graphics, label, area.inset(selected ? 5 : 4),
                enabled ? CYAN_BRIGHT : DISABLED_TEXT);
    }

    private void drawFittedCentered(GuiGraphics graphics, Component text, Rect area, int color) {
        String[] lines = fittedLines(text.getString(), area);
        int widest = 1;
        for (String line : lines) widest = Math.max(widest, font.width(line));
        int lineStep = font.lineHeight + 1;
        float scale = Math.min(1.0F, Math.min((area.width - 2.0F) / widest,
                (area.height - 2.0F) / (lines.length * lineStep - 1.0F)));
        scale = Math.max(0.1F, scale);
        graphics.pose().pushPose();
        graphics.pose().translate(area.x + area.width / 2.0F,
                area.y + (area.height - (lines.length * lineStep - 1) * scale) / 2.0F, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            graphics.drawString(font, line, -font.width(line) / 2, index * lineStep, color, false);
        }
        graphics.pose().popPose();
    }

    private String[] fittedLines(String text, Rect area) {
        String trimmed = text == null ? "" : text.trim();
        if (font.width(trimmed) <= area.width - 2 || area.height < font.lineHeight + 6
                || trimmed.indexOf(' ') < 0) return new String[]{trimmed};
        int best = -1;
        int bestWidth = Integer.MAX_VALUE;
        for (int index = trimmed.indexOf(' '); index >= 0; index = trimmed.indexOf(' ', index + 1)) {
            String first = trimmed.substring(0, index).trim();
            String second = trimmed.substring(index + 1).trim();
            if (first.isEmpty() || second.isEmpty()) continue;
            int candidateWidth = Math.max(font.width(first), font.width(second));
            if (candidateWidth < bestWidth) {
                bestWidth = candidateWidth;
                best = index;
            }
        }
        return best < 0 ? new String[]{trimmed}
                : new String[]{trimmed.substring(0, best).trim(), trimmed.substring(best + 1).trim()};
    }

    private static void drawBorder(GuiGraphics graphics, Rect area, int color, int thickness) {
        int size = Math.max(1, Math.min(thickness, Math.min(area.width, area.height) / 2));
        graphics.fill(area.x, area.y, area.right(), area.y + size, color);
        graphics.fill(area.x, area.bottom() - size, area.right(), area.bottom(), color);
        graphics.fill(area.x, area.y + size, area.x + size, area.bottom() - size, color);
        graphics.fill(area.right() - size, area.y + size, area.right(), area.bottom() - size, color);
    }

    private static boolean hoveringAny(Rect[] areas, double mouseX, double mouseY) {
        for (Rect area : areas) if (area.contains(mouseX, mouseY)) return true;
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || layout == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (layout.exit.contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        if (layout.comboToggle.contains(mouseX, mouseY)) {
            ClientQuickSwitchState.toggleCombo();
            return true;
        }
        for (int index = 0; index < layout.comboStyles.length; index++) {
            if (layout.comboStyles[index].contains(mouseX, mouseY)) {
                ClientQuickSwitchState.selectComboStyle(COMBO_STYLES[index]);
                onClose();
                return true;
            }
        }
        if (ClientComboState.isLocalActive()) return true;
        for (int index = 0; index < layout.formations.length; index++) {
            if (layout.formations[index].contains(mouseX, mouseY)) {
                ClientQuickSwitchState.selectFormation(FORMATIONS[index]);
                return true;
            }
        }
        for (int index = 0; index < layout.techniques.length; index++) {
            if (layout.techniques[index].contains(mouseX, mouseY)) {
                ClientQuickSwitchState.selectTechnique(TECHNIQUES[index]);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ClientModEvents.OPEN_QUICK_SWITCH.matches(keyCode, scanCode)
                && ClientModEvents.OPEN_QUICK_SWITCH.getKeyModifier().isActive(null)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Rect(int x, int y, int width, int height) {
        private int right() { return x + width; }
        private int bottom() { return y + height; }
        private boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
        }
        private Rect inset(int amount) {
            int safe = Math.max(0, Math.min(amount, Math.min(width, height) / 2));
            return new Rect(x + safe, y + safe,
                    Math.max(1, width - safe * 2), Math.max(1, height - safe * 2));
        }
    }

    private record Layout(Rect panel, Rect formationColumn, Rect techniqueColumn, Rect comboColumn,
                          Rect[] formations, Rect[] techniques, Rect comboToggle,
                          Rect[] comboStyles, Rect exit, Rect lockHint) {
        private static Layout create(int screenWidth, int screenHeight) {
            int panelWidth = Math.max(240, Math.min(620, screenWidth - 16));
            int panelHeight = Math.max(170, Math.min(310, screenHeight - 16));
            int left = (screenWidth - panelWidth) / 2;
            int top = (screenHeight - panelHeight) / 2;
            Rect panel = new Rect(left, top, panelWidth, panelHeight);

            int innerLeft = left + 10;
            int innerWidth = panelWidth - 20;
            int available = innerWidth - GAP * 2;
            int formationWidth = available * 23 / 100;
            int techniqueWidth = available * 36 / 100;
            int comboWidth = available - formationWidth - techniqueWidth;
            int contentTop = top + 32;
            int contentBottom = top + panelHeight - 31;
            int contentHeight = Math.max(90, contentBottom - contentTop);

            Rect formationColumn = new Rect(innerLeft, contentTop, formationWidth, contentHeight);
            Rect techniqueColumn = new Rect(formationColumn.right() + GAP, contentTop,
                    techniqueWidth, contentHeight);
            Rect comboColumn = new Rect(techniqueColumn.right() + GAP, contentTop,
                    comboWidth, contentHeight);

            int gridTop = contentTop + 14;
            int gridHeight = Math.max(72, contentBottom - gridTop);
            Rect[] formations = squareGrid(formationColumn.x, gridTop, formationColumn.width,
                    gridHeight, 1, 3, FORMATIONS.length);
            Rect[] techniques = squareGrid(techniqueColumn.x, gridTop, techniqueColumn.width,
                    gridHeight, 3, 2, TECHNIQUES.length);

            int toggleHeight = Math.max(20, Math.min(28, gridHeight / 5));
            Rect comboToggle = new Rect(comboColumn.x, gridTop, comboColumn.width, toggleHeight);
            int comboGridTop = comboToggle.bottom() + GAP;
            Rect[] comboStyles = squareGrid(comboColumn.x, comboGridTop, comboColumn.width,
                    Math.max(48, contentBottom - comboGridTop), 2, 3, COMBO_STYLES.length);

            int exitWidth = Math.min(82, Math.max(58, panelWidth / 7));
            Rect exit = new Rect(left + panelWidth - exitWidth - 9,
                    top + panelHeight - 24, exitWidth, 18);
            Rect lockHint = new Rect(innerLeft, top + panelHeight - 23,
                    Math.max(30, exit.x - innerLeft - GAP), 17);
            return new Layout(panel, formationColumn, techniqueColumn, comboColumn,
                    formations, techniques, comboToggle, comboStyles, exit, lockHint);
        }

        private static Rect[] squareGrid(int x, int y, int width, int height,
                                         int columns, int rows, int count) {
            int size = Math.max(14, Math.min((width - GAP * (columns - 1)) / columns,
                    (height - GAP * (rows - 1)) / rows));
            int usedWidth = columns * size + GAP * (columns - 1);
            int usedHeight = rows * size + GAP * (rows - 1);
            int startX = x + (width - usedWidth) / 2;
            int startY = y + Math.max(0, (height - usedHeight) / 2);
            Rect[] result = new Rect[count];
            for (int index = 0; index < count; index++) {
                int column = index % columns;
                int row = index / columns;
                result[index] = new Rect(startX + column * (size + GAP),
                        startY + row * (size + GAP), size, size);
            }
            return result;
        }
    }
}
