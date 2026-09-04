package dev.yujiancraft.client;

import dev.yujiancraft.combat.technique.TechniqueMode;
import dev.yujiancraft.client.vfx.VfxTimelineDefinition;
import dev.yujiancraft.client.vfx.VfxLivePreviewBridge;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.ArrayList;
import java.util.List;

/** Client-only calligraphy notice and the staged sword-array finisher presentation. */
public final class ClientTechniqueOverlayState {
    private static final long NOTICE_DURATION_MS = 2400L;
    private static final long CONTROL_GUIDE_DURATION_MS = 5200L;
    private static final float NOTICE_SCALE = 1.48F;
    private static TechniqueMode technique;
    private static long noticeStartedAt;
    private static String comboStyleTranslationKey;
    private static long comboNoticeStartedAt;
    private static Component[] controlGuide;
    private static long controlGuideStartedAt;
    private static long finisherStartGameTick = Long.MIN_VALUE;
    private static Vec3 finisherBottom;
    private static Vec3 finisherTop;
    private static float finisherMaximumRadius;
    private static int finisherChargeTicks;
    private static int finisherHoldTicks;
    private static int finisherExpandTicks;
    private static int finisherSustainTicks;
    private static VfxTimelineDefinition finisherTimeline;

    private ClientTechniqueOverlayState() {
    }

    public static void showTechnique(int ordinal) {
        technique = TechniqueMode.fromOrdinal(ordinal);
        noticeStartedAt = Util.getMillis();
    }

    public static void showComboStyle(String translationKey) {
        comboStyleTranslationKey = translationKey;
        comboNoticeStartedAt = Util.getMillis();
    }

    /** Shows contextual controls in a dedicated right-side column, independent of the action bar. */
    public static void showFormationControls(Component quickSwitchKey, Component comboKey,
                                             Component techniqueKey) {
        controlGuide = new Component[]{
                Component.translatable("overlay.yujiancraft.guide.quick_switch", quickSwitchKey),
                Component.translatable("overlay.yujiancraft.guide.combo", comboKey),
                Component.translatable("overlay.yujiancraft.guide.technique", techniqueKey)
        };
        controlGuideStartedAt = Util.getMillis();
    }

    public static void showComboControls(Component quickSwitchKey, Component styleKey) {
        controlGuide = new Component[]{
                Component.translatable("overlay.yujiancraft.guide.quick_switch", quickSwitchKey),
                Component.translatable("overlay.yujiancraft.guide.combo_style", styleKey)
        };
        controlGuideStartedAt = Util.getMillis();
    }

    public static void clearControlGuide() {
        controlGuide = null;
    }

    public static void showFinisherFlash(long startGameTick, Vec3 bottom, Vec3 top, float maximumRadius,
                                         int chargeTicks, int holdTicks,
                                         int expandTicks, int sustainTicks) {
        if (!ClientOptions.hitImpactVisual()) return;
        if (finisherStartGameTick != Long.MIN_VALUE
                && Math.abs(startGameTick - finisherStartGameTick) <= 1L
                && finisherBottom != null && finisherTop != null
                && finisherBottom.distanceToSqr(bottom) < 1.0D
                && finisherTop.distanceToSqr(top) < 4.0D) {
            return;
        }
        finisherBottom = bottom;
        finisherTop = top;
        finisherMaximumRadius = Math.max(0.2F, maximumRadius);
        finisherChargeTicks = Math.max(1, chargeTicks);
        finisherHoldTicks = Math.max(1, holdTicks);
        finisherExpandTicks = Math.max(1, expandTicks);
        finisherSustainTicks = Math.max(1, sustainTicks);
        finisherTimeline = VfxTimelineDefinition.loadSwordArrayFinisher(
                Minecraft.getInstance().getResourceManager());
        finisherStartGameTick = startGameTick;
    }

    public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick,
                              int screenWidth, int screenHeight) {
        long now = Util.getMillis();
        renderTechnique(graphics, screenWidth, screenHeight, now);
        renderComboStyle(graphics, screenWidth, screenHeight, now);
        renderControlGuide(graphics, screenWidth, screenHeight, now);
    }

    private static void renderTechnique(GuiGraphics graphics, int width, int height, long now) {
        if (technique == null) return;
        long age = now - noticeStartedAt;
        if (age < 0L || age >= NOTICE_DURATION_MS) {
            technique = null;
            return;
        }
        String localized = Component.translatable(technique.translationKey()).getString();
        renderCalligraphy(graphics, width, height, localized, age, 0.79F);
    }

    private static void renderComboStyle(GuiGraphics graphics, int width, int height, long now) {
        if (comboStyleTranslationKey == null) return;
        long age = now - comboNoticeStartedAt;
        if (age < 0L || age >= NOTICE_DURATION_MS) {
            comboStyleTranslationKey = null;
            return;
        }
        renderCalligraphy(graphics, width, height,
                Component.translatable(comboStyleTranslationKey).getString(), age, 0.84F);
    }

    private static void renderControlGuide(GuiGraphics graphics, int width, int height, long now) {
        if (controlGuide == null) return;
        long age = now - controlGuideStartedAt;
        if (age < 0L || age >= CONTROL_GUIDE_DURATION_MS) {
            controlGuide = null;
            return;
        }
        float fade = age < 260L ? age / 260.0F
                : Math.min(1.0F, (CONTROL_GUIDE_DURATION_MS - age) / 520.0F);
        int alpha = Math.max(0, Math.min(230, Math.round(230.0F * fade)));
        Font font = Minecraft.getInstance().font;
        int maxWidth = 0;
        for (Component line : controlGuide) maxWidth = Math.max(maxWidth, font.width(line));
        int right = width - 18;
        int lineHeight = font.lineHeight + 6;
        int panelHeight = controlGuide.length * lineHeight + 12;
        // Keep the controls below the technique/style calligraphy columns, which occupy the
        // vertical centre on the right. Clamp for short windows so the panel stays on-screen.
        int top = Math.min(height - panelHeight - 24, Math.max(18, Math.round(height * 0.70F)));
        int left = right - maxWidth - 18;
        graphics.fill(left, top - 9, right + 8, top + controlGuide.length * lineHeight + 3,
                (Math.round(alpha * 0.38F) << 24) | 0x071B22);
        graphics.fill(right + 5, top - 7, right + 7,
                top + controlGuide.length * lineHeight,
                (Math.round(alpha * 0.88F) << 24) | 0x78E8E1);
        for (int index = 0; index < controlGuide.length; index++) {
            int color = (alpha << 24) | 0xD9FAF6;
            graphics.drawString(font, controlGuide[index], left + 9,
                    top + index * lineHeight, color, false);
        }
    }

    private static void renderCalligraphy(GuiGraphics graphics, int width, int height,
                                          String localized, long age, float horizontalPosition) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        List<String> lines = verticalLines(localized);
        int sourceLineStep = font.lineHeight + 3;
        int totalHeight = Math.round((lines.size() * sourceLineStep - 3) * NOTICE_SCALE);
        int x = Math.round(width * horizontalPosition);
        int y = Math.max(18, (height - totalHeight) / 2);
        float fade = age < 240L ? age / 240.0F
                : Math.min(1.0F, (NOTICE_DURATION_MS - age) / 420.0F);
        int alpha = Math.max(0, Math.min(255, Math.round(235.0F * fade)));
        int main = alpha << 24 | 0xD9FAF6;
        int spirit = Math.round(alpha * 0.58F) << 24 | 0x55DCE7;
        int ruleAlpha = Math.round(alpha * 0.42F);
        graphics.fill(x - 14, y - 6, x - 13, y + totalHeight + 6,
                ruleAlpha << 24 | 0xA8F4EF);
        graphics.fill(x + 13, y + 4, x + 14, y + totalHeight - 4,
                Math.round(ruleAlpha * 0.55F) << 24 | 0x60D6E6);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(NOTICE_SCALE, NOTICE_SCALE, 1.0F);
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            int drawX = -font.width(line) / 2;
            int drawY = index * sourceLineStep;
            graphics.drawString(font, line, drawX + 1, drawY + 1, spirit, false);
            graphics.drawString(font, line, drawX, drawY, main, true);
        }
        graphics.pose().popPose();
    }

    private static List<String> verticalLines(String text) {
        List<String> lines = new ArrayList<>();
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.indexOf(' ') >= 0) {
            for (String word : trimmed.split("\\s+")) if (!word.isBlank()) lines.add(word);
        } else {
            trimmed.codePoints().forEach(codePoint -> lines.add(new String(Character.toChars(codePoint))));
        }
        if (lines.isEmpty()) lines.add("");
        return lines;
    }

    public static FinisherFrame sampleFinisher(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        FinisherFrame livePreview = VfxLivePreviewBridge.sample(partialTick);
        if (livePreview != null) return livePreview;
        if (finisherStartGameTick == Long.MIN_VALUE || finisherTimeline == null
                || minecraft.level == null || !ClientOptions.hitImpactVisual()) {
            clearFinisher();
            return null;
        }
        float ageTicks = minecraft.level.getGameTime() + partialTick - finisherStartGameTick;
        float durationTicks = finisherChargeTicks + finisherHoldTicks
                + finisherExpandTicks + finisherSustainTicks;
        if (ageTicks < -2.0F || ageTicks >= durationTicks) {
            clearFinisher();
            return null;
        }
        ageTicks = Math.max(0.0F, ageTicks);
        float authoredTick = finisherTimeline.mapRuntimeTick(ageTicks, finisherChargeTicks,
                finisherHoldTicks, finisherExpandTicks, finisherSustainTicks);
        return new FinisherFrame(finisherBottom, finisherTop, finisherMaximumRadius,
                ageTicks / 20.0F, authoredTick, finisherTimeline);
    }

    private static void clearFinisher() {
        finisherStartGameTick = Long.MIN_VALUE;
        finisherBottom = null;
        finisherTop = null;
        finisherTimeline = null;
    }

    public record FinisherFrame(Vec3 bottom, Vec3 top, float maximumRadius, float ageSeconds,
                                float authoredTick, VfxTimelineDefinition timeline) {
        public float value(String track, float fallback) {
            return timeline.sample(track, authoredTick, fallback);
        }

        public boolean enabled(String module) {
            return timeline.moduleEnabled(module);
        }

        public VfxTimelineDefinition.Center center(String module, float x, float y) {
            return timeline.moduleCenter(module, x, y);
        }

        public String anchor(String module) {
            return timeline.moduleAnchor(module);
        }
    }
}
