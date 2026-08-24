package dev.yujiancraft.client;

import dev.yujiancraft.combat.technique.TechniqueMode;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.ArrayList;
import java.util.List;

/** Client-only calligraphy notice and the deliberately brief sword-array finisher flash. */
public final class ClientTechniqueOverlayState {
    private static final long NOTICE_DURATION_MS = 1900L;
    private static final long FINISHER_DURATION_MS = 560L;
    private static TechniqueMode technique;
    private static long noticeStartedAt;
    private static long finisherStartedAt;

    private ClientTechniqueOverlayState() {
    }

    public static void showTechnique(int ordinal) {
        technique = TechniqueMode.fromOrdinal(ordinal);
        noticeStartedAt = Util.getMillis();
    }

    public static void showFinisherFlash() {
        if (ClientOptions.hitImpactVisual()) finisherStartedAt = Util.getMillis();
    }

    public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick,
                              int screenWidth, int screenHeight) {
        long now = Util.getMillis();
        renderTechnique(graphics, screenWidth, screenHeight, now);
        renderFinisher(graphics, screenWidth, screenHeight, now);
    }

    private static void renderTechnique(GuiGraphics graphics, int width, int height, long now) {
        if (technique == null) return;
        long age = now - noticeStartedAt;
        if (age < 0L || age >= NOTICE_DURATION_MS) {
            technique = null;
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        String localized = Component.translatable(technique.translationKey()).getString();
        List<String> lines = verticalLines(localized);
        int lineStep = font.lineHeight + 3;
        int totalHeight = lines.size() * lineStep - 3;
        int x = Math.round(width * 0.79F);
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
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            int drawX = x - font.width(line) / 2;
            int drawY = y + index * lineStep;
            graphics.drawString(font, line, drawX + 1, drawY + 1, spirit, false);
            graphics.drawString(font, line, drawX, drawY, main, true);
        }
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

    private static void renderFinisher(GuiGraphics graphics, int width, int height, long now) {
        if (finisherStartedAt <= 0L || !ClientOptions.hitImpactVisual()) return;
        long age = now - finisherStartedAt;
        if (age < 0L || age >= FINISHER_DURATION_MS) {
            finisherStartedAt = 0L;
            return;
        }
        // Four beats: one neutral high-key impact frame, a brief dark contrast hold, the
        // layered white/cyan/material-coloured in-world slash, then a smooth return to normal.
        // This deliberately avoids a second flash or a repeating strobe. A neutral overlay is
        // used instead of taking ownership of Minecraft's post chain, which keeps shader packs
        // and other mods' post-processing effects intact.
        int color;
        if (age < 42L) {
            int alpha = 218 - (int) (age * 34L / 42L);
            color = Math.max(184, alpha) << 24 | 0xF8F8F4;
        } else if (age < 138L) {
            int alpha = 136 - (int) ((age - 42L) * 12L / 96L);
            color = Math.max(124, alpha) << 24 | 0x020506;
        } else {
            float remaining = (FINISHER_DURATION_MS - age) / (float) (FINISHER_DURATION_MS - 138L);
            int alpha = Math.round(124.0F * remaining * remaining);
            color = Math.max(0, alpha) << 24 | 0x020809;
        }
        graphics.fill(0, 0, width, height, color);
    }
}
