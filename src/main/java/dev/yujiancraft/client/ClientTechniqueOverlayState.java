package dev.yujiancraft.client;

import dev.yujiancraft.combat.technique.TechniqueMode;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Camera;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import com.mojang.math.Axis;

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
        finisherBottom = bottom;
        finisherTop = top;
        finisherMaximumRadius = Math.max(0.2F, maximumRadius);
        finisherChargeTicks = Math.max(1, chargeTicks);
        finisherHoldTicks = Math.max(1, holdTicks);
        finisherExpandTicks = Math.max(1, expandTicks);
        finisherSustainTicks = Math.max(1, sustainTicks);
        finisherStartedAt = Util.getMillis();
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
        long burstStart = (finisherChargeTicks + finisherHoldTicks) * 50L;
        long expansionEnd = (finisherChargeTicks + finisherHoldTicks + finisherExpandTicks) * 50L;
        long duration = (finisherChargeTicks + finisherHoldTicks
                + finisherExpandTicks + finisherSustainTicks) * 50L;
        if (age < 0L || age >= duration) {
            finisherStartedAt = 0L;
            finisherBottom = null;
            finisherTop = null;
            return;
        }

        // The dark field arrives only after the narrow pillar has visibly charged. At nearly 99%
        // opacity it is a deliberate scene cut, not the former grey veil.
        if (age >= burstStart && age < expansionEnd) {
            float darkRamp = Math.min(1.0F, (age - burstStart) / 45.0F);
            int alpha = Math.round(252.0F * darkRamp);
            graphics.fill(0, 0, width, height, alpha << 24 | 0x000102);
        }

        long whiteStart = Math.max(burstStart, expansionEnd - 90L);
        long whitePeakEnd = expansionEnd + 170L;
        if (age >= whiteStart) {
            int alpha;
            if (age < expansionEnd) {
                float rise = (age - whiteStart) / (float) Math.max(1L, expansionEnd - whiteStart);
                alpha = Math.round(255.0F * rise * rise);
            } else if (age < whitePeakEnd) {
                alpha = 255;
            } else {
                float recovery = 1.0F - (age - whitePeakEnd)
                        / (float) Math.max(1L, duration - whitePeakEnd);
                alpha = Math.round(255.0F * Math.max(0.0F, recovery * recovery));
            }
            graphics.fill(0, 0, width, height, alpha << 24 | 0xFFFFFF);
        }

        // The world-space pillar is re-projected above the dark/white grading. This preserves a
        // differentiated white core and cyan shell without taking over Minecraft's post chain,
        // so shader packs keep ownership of their own render pipeline.
        if (age < expansionEnd || age >= whitePeakEnd) {
            renderProjectedPillar(graphics, width, height, age, burstStart, expansionEnd, duration);
        }
    }

    private static void renderProjectedPillar(GuiGraphics graphics, int width, int height,
                                              long age, long burstStart,
                                              long expansionEnd, long duration) {
        if (finisherBottom == null || finisherTop == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        double fov = minecraft.options.fov().get();
        double focal = height / (2.0D * Math.tan(Math.toRadians(fov) * 0.5D));
        ScreenPoint bottom = project(finisherBottom, camera, width, height, focal);
        ScreenPoint top = project(finisherTop, camera, width, height, focal);
        if (bottom == null || top == null) return;

        double dx = top.x - bottom.x;
        double dy = top.y - bottom.y;
        double length = Math.max(8.0D, Math.sqrt(dx * dx + dy * dy));
        double averageDepth = Math.max(0.25D, (bottom.depth + top.depth) * 0.5D);
        float worldRadius = projectedWorldRadius(age, burstStart, expansionEnd);
        float radiusPixels = (float) Math.min(Math.max(width, height) * 1.65D,
                Math.max(2.5D, worldRadius * focal / averageDepth));
        float fade = age < expansionEnd + 220L ? 1.0F
                : Math.max(0.35F, (duration - age) / (float) Math.max(1L, duration - expansionEnd - 220L));
        float angle = (float) Math.atan2(-dx, dy);
        float centreX = (float) ((top.x + bottom.x) * 0.5D);
        float centreY = (float) ((top.y + bottom.y) * 0.5D);

        graphics.pose().pushPose();
        graphics.pose().translate(centreX, centreY, 420.0F);
        graphics.pose().mulPose(Axis.ZP.rotation(angle));
        int halfLength = (int) Math.ceil(length * 0.5D + radiusPixels * 0.42F);
        drawPillarLayer(graphics, radiusPixels * 1.48F, halfLength,
                Math.round(82.0F * fade), 0x20EAF4);
        drawPillarLayer(graphics, radiusPixels, halfLength,
                Math.round(174.0F * fade), 0x7DFBFF);
        drawPillarLayer(graphics, radiusPixels * 0.64F, halfLength,
                Math.round(230.0F * fade), 0xD8FFFF);
        drawPillarLayer(graphics, Math.max(1.5F, radiusPixels * 0.34F), halfLength,
                Math.round(255.0F * fade), 0xFFFFFF);
        graphics.pose().popPose();
    }

    private static float projectedWorldRadius(long age, long burstStart, long expansionEnd) {
        float thin = Math.max(0.28F, finisherMaximumRadius * 0.065F);
        if (age < burstStart) return thin * (0.55F + 0.45F * age
                / Math.max(1.0F, burstStart));
        float progress = Math.min(1.0F, (age - burstStart)
                / (float) Math.max(1L, expansionEnd - burstStart));
        float explosive = 1.0F - (1.0F - progress) * (1.0F - progress) * (1.0F - progress);
        return thin + (finisherMaximumRadius - thin) * explosive;
    }

    private static void drawPillarLayer(GuiGraphics graphics, float halfWidth, int halfLength,
                                        int alpha, int rgb) {
        int extent = Math.max(1, Math.round(halfWidth));
        graphics.fill(-extent, -halfLength, extent, halfLength,
                Math.max(0, Math.min(255, alpha)) << 24 | rgb);
    }

    private static ScreenPoint project(Vec3 point, Camera camera, int width, int height, double focal) {
        Vec3 relative = point.subtract(camera.getPosition());
        Vec3 forward = Vec3.directionFromRotation(camera.getXRot(), camera.getYRot()).normalize();
        Vec3 right = Vec3.directionFromRotation(0.0F, camera.getYRot() + 90.0F).normalize();
        Vec3 up = right.cross(forward).normalize();
        double depth = relative.dot(forward);
        if (depth <= 0.08D) return null;
        double x = width * 0.5D + relative.dot(right) * focal / depth;
        double y = height * 0.5D - relative.dot(up) * focal / depth;
        double limitX = width * 3.0D;
        double limitY = height * 3.0D;
        return new ScreenPoint(Math.max(-limitX, Math.min(limitX, x)),
                Math.max(-limitY, Math.min(limitY, y)), depth);
    }

    private record ScreenPoint(double x, double y, double depth) { }
}
