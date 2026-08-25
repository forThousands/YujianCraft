package dev.yujiancraft.client;

import dev.yujiancraft.combat.technique.TechniqueMode;
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
    private static final long NOTICE_DURATION_MS = 1900L;
    private static TechniqueMode technique;
    private static long noticeStartedAt;
    private static long finisherStartedAt;
    private static Vec3 finisherBottom;
    private static Vec3 finisherTop;
    private static float finisherMaximumRadius;
    private static int finisherChargeTicks;
    private static int finisherHoldTicks;
    private static int finisherExpandTicks;
    private static int finisherSustainTicks;

    private ClientTechniqueOverlayState() {
    }

    public static void showTechnique(int ordinal) {
        technique = TechniqueMode.fromOrdinal(ordinal);
        noticeStartedAt = Util.getMillis();
    }

    public static void showFinisherFlash(Vec3 bottom, Vec3 top, float maximumRadius,
                                         int chargeTicks, int holdTicks,
                                         int expandTicks, int sustainTicks) {
        if (!ClientOptions.hitImpactVisual()) return;
        long now = Util.getMillis();
        if (finisherStartedAt > 0L && now - finisherStartedAt < 3500L
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
        finisherStartedAt = now;
    }

    public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick,
                              int screenWidth, int screenHeight) {
        long now = Util.getMillis();
        renderTechnique(graphics, screenWidth, screenHeight, now);
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

    public static FinisherFrame sampleFinisher(long now) {
        if (finisherStartedAt <= 0L || !ClientOptions.hitImpactVisual()) {
            clearFinisher();
            return null;
        }
        long age = now - finisherStartedAt;
        long chargeEnd = finisherChargeTicks * 50L;
        long burstStart = (finisherChargeTicks + finisherHoldTicks) * 50L;
        long duration = (finisherChargeTicks + finisherHoldTicks
                + finisherExpandTicks + finisherSustainTicks) * 50L;
        if (age < 0L || age >= duration) {
            clearFinisher();
            return null;
        }

        float charge = clamp01(age / (float) Math.max(1L, chargeEnd));
        float postBurst = clamp01((age - burstStart) / (float) Math.max(1L, duration - burstStart));

        // The finisher deliberately spends most of its first post-impact beat in black.  The
        // former millisecond milestones let the white frame arrive after roughly 0.26 seconds,
        // so the eye perceived only a flash.  Proportional phases preserve the intended rhythm
        // when a server changes the technique duration.
        float dark = smoothstep(0.0F, 0.035F, postBurst);
        float blackGrowth = smoothstep(0.04F, 0.31F, postBurst);
        float partialExpansion = smoothstep(0.31F, 0.47F, postBurst);
        float fullExpansion = smoothstep(0.47F, 0.59F, postBurst);
        float expansion = age < burstStart ? 0.0F
                : 0.12F + blackGrowth * 0.18F + partialExpansion * 0.55F + fullExpansion * 0.15F;

        float whiteRise = smoothstep(0.31F, 0.47F, postBurst);
        float whiteRelease = smoothstep(0.59F, 0.68F, postBurst);
        float white = whiteRise * (1.0F - whiteRelease);
        float ink = smoothstep(0.59F, 0.68F, postBurst)
                * (1.0F - smoothstep(0.80F, 0.90F, postBurst));
        float recovery = smoothstep(0.80F, 1.0F, postBurst);

        float onsetShock = 1.0F - smoothstep(0.035F, 0.16F, postBurst);
        float expansionPosition = clamp01((postBurst - 0.31F) / 0.16F);
        float expansionShock = 1.0F - Math.abs(expansionPosition * 2.0F - 1.0F);
        float distortion = clamp01(dark * (onsetShock * 0.66F + expansionShock * 0.88F)
                + ink * 0.22F);
        float chroma = clamp01(dark * (1.0F - smoothstep(0.34F, 0.51F, postBurst))
                + recovery * (1.0F - recovery) * 0.32F);
        return new FinisherFrame(finisherBottom, finisherTop, finisherMaximumRadius,
                age / 1000.0F, charge, dark, expansion, white, ink, recovery,
                distortion, chroma);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float position = clamp01((value - edge0) / Math.max(0.0001F, edge1 - edge0));
        return position * position * (3.0F - 2.0F * position);
    }

    private static void clearFinisher() {
        finisherStartedAt = 0L;
        finisherBottom = null;
        finisherTop = null;
    }

    public record FinisherFrame(Vec3 bottom, Vec3 top, float maximumRadius, float ageSeconds,
                                float charge, float darkAmount, float expansion,
                                float whiteAmount, float inkAmount, float recovery,
                                float distortion, float chromaAmount) { }
}
