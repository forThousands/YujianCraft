package dev.yujiancraft.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import dev.yujiancraft.entity.SwordArrayFieldEntity;
import dev.yujiancraft.material.FlyingSwordMaterial;
import dev.yujiancraft.wanxiang.WanxiangSwordData;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** Procedural emissive geometry for the colossal seal and its heaven-to-ground finisher. */
public final class SwordArrayFieldRenderer extends EntityRenderer<SwordArrayFieldEntity> {
    /** Additive, depth-tested and colour-only: nested energy shells remain visible without z fighting. */
    private static final RenderType SPIRIT_LIGHT = SpiritRenderStates.create();

    public SwordArrayFieldRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(SwordArrayFieldEntity field, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        float age = field.tickCount + partialTick;
        float finisherAge = age - field.finisherStartTick();
        float[] material = color(WanxiangSwordData.material(field.getDisplayStack()));
        VertexConsumer vertices = buffers.getBuffer(SPIRIT_LIGHT);
        float ringRadius = arrayRadius(field, age);

        poseStack.pushPose();
        poseStack.translate(0.0D, field.beamHeight(), 0.0D);
        poseStack.mulPose(Axis.YP.rotation(age * 0.009F));
        renderSeal(vertices, poseStack.last().pose(), ringRadius, material, age, finisherAge >= 0.0F);
        poseStack.popPose();

        if (finisherAge >= 0.0F) {
            poseStack.pushPose();
            renderWhiteFinisher(vertices, poseStack.last().pose(), field, ringRadius,
                    beamRadius(field, finisherAge), finisherAge, beamFade(field, finisherAge), age);
            poseStack.popPose();
        }
        super.render(field, yaw, partialTick, poseStack, buffers, packedLight);
    }

    private static void renderSeal(VertexConsumer vertices, Matrix4f matrix, float radius,
                                   float[] material, float age, boolean monochrome) {
        float pulse = 0.92F + 0.08F * Mth.sin(age * 0.18F);
        float red = monochrome ? 0.93F : material[0];
        float green = monochrome ? 0.96F : material[1];
        float blue = monochrome ? 1.0F : material[2];
        drawBrokenAnnulus(vertices, matrix, radius * 0.945F, radius, 0.0F,
                red, green, blue, Math.round(225.0F * pulse), 112, 11, 2, age * 0.018F);
        drawBrokenAnnulus(vertices, matrix, radius * 0.815F, radius * 0.842F, -0.11F,
                0.86F, 0.93F, 1.0F, Math.round(172.0F * pulse), 96, 8, 1, -age * 0.021F);
        drawBrokenAnnulus(vertices, matrix, radius * 0.655F, radius * 0.682F, 0.09F,
                0.98F, 1.0F, 1.0F, Math.round(208.0F * pulse), 88, 7, 2, age * 0.014F);
        drawBrokenAnnulus(vertices, matrix, radius * 0.375F, radius * 0.415F, -0.05F,
                red, green, blue, Math.round(186.0F * pulse), 72, 6, 1, -age * 0.026F);
        drawAnnulus(vertices, matrix, radius * 0.145F, radius * 0.235F, 0.03F,
                1.0F, 1.0F, 1.0F, Math.round(198.0F * pulse), 48);

        for (int index = 0; index < 16; index++) {
            double angle = Math.PI * 2.0D * index / 16.0D + age * 0.006D;
            drawSpoke(vertices, matrix, radius * 0.25F, radius * 0.925F, angle,
                    radius * (index % 2 == 0 ? 0.0075F : 0.0045F),
                    0.87F, 0.94F, 1.0F, index % 2 == 0 ? 128 : 82);
        }
        for (int index = 0; index < 32; index++) {
            double angle = Math.PI * 2.0D * index / 32.0D - age * 0.011D;
            drawChevron(vertices, matrix, radius * (index % 2 == 0 ? 0.73F : 0.77F), angle,
                    radius * 0.055F, radius * 0.022F,
                    red, green, blue, index % 2 == 0 ? 168 : 112);
        }
        for (int index = 0; index < 12; index++) {
            double angle = Math.PI * 2.0D * index / 12.0D + age * 0.004D;
            float length = radius * (0.07F + 0.025F * (index % 3));
            drawCurtain(vertices, matrix, radius * 0.91F, angle, radius * 0.018F, length,
                    red, green, blue, 92);
        }
    }

    private static void renderWhiteFinisher(VertexConsumer vertices, Matrix4f matrix,
                                            SwordArrayFieldEntity field, float ringRadius,
                                            float beamRadius, float finisherAge, float fade, float age) {
        float burst = field.chargeTicks() + field.holdTicks();
        float impact = finisherAge < burst ? 0.0F : Mth.clamp(
                (finisherAge - burst) / Math.max(1.0F, field.expandTicks()), 0.0F, 1.0F);
        float outerAlpha = Mth.lerp(impact, 116.0F, 205.0F) * fade;
        drawTurbulentColumn(vertices, matrix, beamRadius, field.beamHeight(), age,
                0.76F, 0.81F, 0.88F, Math.round(outerAlpha), 32, 8, 0.075F);
        drawTurbulentColumn(vertices, matrix, Math.max(0.28F, beamRadius * 0.72F), field.beamHeight(),
                age + 19.0F, 0.94F, 0.96F, 1.0F, Math.round(222.0F * fade), 28, 7, 0.045F);
        drawTurbulentColumn(vertices, matrix, Math.max(0.18F, beamRadius * 0.38F), field.beamHeight(),
                age + 43.0F, 1.0F, 1.0F, 1.0F, Math.round(255.0F * fade), 24, 6, 0.025F);

        float topHub = Math.min(ringRadius * 0.34F,
                Math.max(beamRadius * 1.22F, field.baseRadius() * 0.16F));
        float flareDepth = Math.min(field.beamHeight() * 0.22F, Math.max(3.0F, topHub * 0.46F));
        drawTurbulentFrustum(vertices, matrix, beamRadius * 0.96F, topHub,
                field.beamHeight() - flareDepth, field.beamHeight(), age + 61.0F,
                0.91F, 0.95F, 1.0F, Math.round(205.0F * fade), 36, 5, 0.055F);
        drawTurbulentFrustum(vertices, matrix, Math.max(beamRadius * 1.18F, 0.65F),
                beamRadius * 0.83F, 0.0F, Math.min(2.6F, field.beamHeight() * 0.12F), age + 89.0F,
                0.90F, 0.94F, 1.0F, Math.round(192.0F * fade), 32, 4, 0.065F);
        drawAnnulus(vertices, matrix, topHub * 0.42F, topHub, field.beamHeight() - 0.08F,
                0.96F, 0.98F, 1.0F, Math.round(224.0F * fade), 72);
        drawAnnulus(vertices, matrix, beamRadius * 0.68F, beamRadius * 1.42F, 0.04F,
                1.0F, 1.0F, 1.0F, Math.round(232.0F * fade), 64);
        renderImpactBands(vertices, matrix, field, finisherAge, fade, age);
    }

    private static void renderImpactBands(VertexConsumer vertices, Matrix4f matrix,
                                          SwordArrayFieldEntity field, float finisherAge,
                                          float fade, float age) {
        float burst = field.chargeTicks() + field.holdTicks();
        if (finisherAge < burst) return;
        float progress = Mth.clamp((finisherAge - burst) / Math.max(1.0F, field.expandTicks()), 0.0F, 1.0F);
        float radius = field.maximumBeamRadius() * (0.62F + progress * 0.72F);
        drawBrokenAnnulus(vertices, matrix, radius * 0.79F, radius, 0.06F,
                0.86F, 0.90F, 0.96F, Math.round(206.0F * fade), 96, 9, 2, age * 0.035F);
        drawBrokenAnnulus(vertices, matrix, radius * 0.43F, radius * 0.57F, 0.12F,
                1.0F, 1.0F, 1.0F, Math.round(242.0F * fade), 80, 7, 1, -age * 0.046F);
        for (int index = 0; index < 18; index++) {
            double angle = Math.PI * 2.0D * index / 18.0D + age * 0.017D;
            drawSpoke(vertices, matrix, radius * 0.24F, radius * 1.08F, angle,
                    radius * 0.006F, 1.0F, 1.0F, 1.0F, Math.round(128.0F * fade));
        }
    }

    private static float arrayRadius(SwordArrayFieldEntity field, float age) {
        float finisherAge = age - field.finisherStartTick();
        float base = field.baseRadius();
        if (finisherAge < field.chargeTicks() + field.holdTicks()) {
            return base * (1.0F + 0.025F * Mth.sin(age * 0.14F));
        }
        float expansionAge = finisherAge - field.chargeTicks() - field.holdTicks();
        float progress = Mth.clamp(expansionAge / Math.max(1.0F, field.expandTicks()), 0.0F, 1.0F);
        float eased = 1.0F - (1.0F - progress) * (1.0F - progress) * (1.0F - progress);
        return Mth.lerp(eased, base, field.expandedArrayRadius());
    }

    private static float beamRadius(SwordArrayFieldEntity field, float finisherAge) {
        float thin = Math.max(0.38F, field.baseRadius() * 0.055F);
        if (finisherAge < field.chargeTicks()) {
            float progress = Mth.clamp(finisherAge / Math.max(1.0F, field.chargeTicks()), 0.0F, 1.0F);
            return Mth.lerp(progress * progress, thin * 0.32F, thin);
        }
        if (finisherAge < field.chargeTicks() + field.holdTicks()) {
            return thin * (0.93F + 0.07F * Mth.sin(finisherAge * 0.9F));
        }
        float expansionAge = finisherAge - field.chargeTicks() - field.holdTicks();
        float progress = Mth.clamp(expansionAge / Math.max(1.0F, field.expandTicks()), 0.0F, 1.0F);
        float explosive = 1.0F - (1.0F - progress) * (1.0F - progress) * (1.0F - progress);
        return Mth.lerp(explosive, thin, field.maximumBeamRadius());
    }

    private static float beamFade(SwordArrayFieldEntity field, float finisherAge) {
        float recoveryStart = field.chargeTicks() + field.holdTicks() + field.expandTicks() + 8.0F;
        if (finisherAge <= recoveryStart) return 1.0F;
        float remaining = field.chargeTicks() + field.holdTicks() + field.expandTicks()
                + field.sustainTicks() - finisherAge;
        return Mth.clamp(0.30F + 0.70F * remaining
                / Math.max(1.0F, field.sustainTicks() - 8.0F), 0.0F, 1.0F);
    }

    private static void drawTurbulentColumn(VertexConsumer vertices, Matrix4f matrix, float radius, float height,
                                            float phase, float red, float green, float blue, int alpha,
                                            int segments, int layers, float distortion) {
        drawTurbulentFrustum(vertices, matrix, radius, radius, 0.0F, height, phase,
                red, green, blue, alpha, segments, layers, distortion);
    }

    private static void drawTurbulentFrustum(VertexConsumer vertices, Matrix4f matrix,
                                             float bottomRadius, float topRadius, float bottomY, float topY,
                                             float phase, float red, float green, float blue, int alpha,
                                             int segments, int layers, float distortion) {
        for (int layer = 0; layer < layers; layer++) {
            float t0 = layer / (float) layers;
            float t1 = (layer + 1) / (float) layers;
            float y0 = Mth.lerp(t0, bottomY, topY);
            float y1 = Mth.lerp(t1, bottomY, topY);
            float base0 = Mth.lerp(t0, bottomRadius, topRadius);
            float base1 = Mth.lerp(t1, bottomRadius, topRadius);
            for (int index = 0; index < segments; index++) {
                double a0 = Math.PI * 2.0D * index / segments;
                double a1 = Math.PI * 2.0D * (index + 1) / segments;
                float r00 = base0 * irregularRadius(a0, t0, phase, distortion);
                float r01 = base0 * irregularRadius(a1, t0, phase, distortion);
                float r10 = base1 * irregularRadius(a0, t1, phase, distortion);
                float r11 = base1 * irregularRadius(a1, t1, phase, distortion);
                vertex(vertices, matrix, (float) Math.cos(a0) * r00, y0, (float) Math.sin(a0) * r00,
                        red, green, blue, alpha);
                vertex(vertices, matrix, (float) Math.cos(a1) * r01, y0, (float) Math.sin(a1) * r01,
                        red, green, blue, alpha);
                vertex(vertices, matrix, (float) Math.cos(a1) * r11, y1, (float) Math.sin(a1) * r11,
                        red, green, blue, alpha);
                vertex(vertices, matrix, (float) Math.cos(a0) * r10, y1, (float) Math.sin(a0) * r10,
                        red, green, blue, alpha);
            }
        }
    }

    private static float irregularRadius(double angle, float heightT, float phase, float amount) {
        double wave = Math.sin(angle * 3.0D + phase * 0.09D + heightT * 11.0D) * 0.52D
                + Math.sin(angle * 7.0D - phase * 0.14D + heightT * 19.0D) * 0.31D
                + Math.sin(angle * 13.0D + phase * 0.05D - heightT * 7.0D) * 0.17D;
        return 1.0F + (float) wave * amount;
    }

    private static void drawBrokenAnnulus(VertexConsumer vertices, Matrix4f matrix, float inner, float outer,
                                          float y, float red, float green, float blue, int alpha, int segments,
                                          int cadence, int gap, float rotation) {
        for (int index = 0; index < segments; index++) {
            if (index % cadence < gap) continue;
            double a0 = Math.PI * 2.0D * index / segments + rotation;
            double a1 = Math.PI * 2.0D * (index + 1) / segments + rotation;
            annulusSegment(vertices, matrix, inner, outer, y, red, green, blue, alpha, a0, a1);
        }
    }

    private static void drawAnnulus(VertexConsumer vertices, Matrix4f matrix, float inner, float outer,
                                    float y, float red, float green, float blue, int alpha, int segments) {
        for (int index = 0; index < segments; index++) {
            double a0 = Math.PI * 2.0D * index / segments;
            double a1 = Math.PI * 2.0D * (index + 1) / segments;
            annulusSegment(vertices, matrix, inner, outer, y, red, green, blue, alpha, a0, a1);
        }
    }

    private static void annulusSegment(VertexConsumer vertices, Matrix4f matrix, float inner, float outer,
                                       float y, float red, float green, float blue, int alpha,
                                       double a0, double a1) {
        vertex(vertices, matrix, (float) Math.cos(a0) * inner, y, (float) Math.sin(a0) * inner,
                red, green, blue, alpha);
        vertex(vertices, matrix, (float) Math.cos(a0) * outer, y, (float) Math.sin(a0) * outer,
                red, green, blue, alpha);
        vertex(vertices, matrix, (float) Math.cos(a1) * outer, y, (float) Math.sin(a1) * outer,
                red, green, blue, alpha);
        vertex(vertices, matrix, (float) Math.cos(a1) * inner, y, (float) Math.sin(a1) * inner,
                red, green, blue, alpha);
    }

    private static void drawSpoke(VertexConsumer vertices, Matrix4f matrix, float inner, float outer,
                                  double angle, float halfWidth, float red, float green, float blue, int alpha) {
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float px = -sin * halfWidth;
        float pz = cos * halfWidth;
        vertex(vertices, matrix, cos * inner + px, -0.02F, sin * inner + pz, red, green, blue, alpha);
        vertex(vertices, matrix, cos * outer + px, -0.02F, sin * outer + pz, red, green, blue, alpha);
        vertex(vertices, matrix, cos * outer - px, -0.02F, sin * outer - pz, red, green, blue, alpha);
        vertex(vertices, matrix, cos * inner - px, -0.02F, sin * inner - pz, red, green, blue, alpha);
    }

    private static void drawChevron(VertexConsumer vertices, Matrix4f matrix, float radius, double angle,
                                    float length, float halfWidth, float red, float green, float blue, int alpha) {
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float tx = -sin;
        float tz = cos;
        float cx = cos * radius;
        float cz = sin * radius;
        float tipX = cx + cos * length;
        float tipZ = cz + sin * length;
        vertex(vertices, matrix, cx + tx * halfWidth, 0.14F, cz + tz * halfWidth, red, green, blue, alpha);
        vertex(vertices, matrix, tipX, 0.14F, tipZ, red, green, blue, alpha);
        vertex(vertices, matrix, cx - tx * halfWidth, 0.14F, cz - tz * halfWidth, red, green, blue, alpha);
        vertex(vertices, matrix, cx, 0.14F, cz, red, green, blue, alpha);
    }

    private static void drawCurtain(VertexConsumer vertices, Matrix4f matrix, float radius, double angle,
                                    float halfWidth, float length, float red, float green, float blue, int alpha) {
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float px = -sin * halfWidth;
        float pz = cos * halfWidth;
        float x = cos * radius;
        float z = sin * radius;
        vertex(vertices, matrix, x + px, 0.0F, z + pz, red, green, blue, alpha);
        vertex(vertices, matrix, x - px, 0.0F, z - pz, red, green, blue, alpha);
        vertex(vertices, matrix, x - px * 0.18F, -length, z - pz * 0.18F, red, green, blue, 0);
        vertex(vertices, matrix, x + px * 0.18F, -length, z + pz * 0.18F, red, green, blue, 0);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z,
                               float red, float green, float blue, int alpha) {
        consumer.vertex(matrix, x, y, z).color(red, green, blue,
                Mth.clamp(alpha, 0, 255) / 255.0F).endVertex();
    }

    private static float[] color(FlyingSwordMaterial material) {
        return switch (material) {
            case WOODEN -> new float[]{0.60F, 0.88F, 0.68F};
            case STONE -> new float[]{0.72F, 0.82F, 0.92F};
            case IRON -> new float[]{0.65F, 0.96F, 1.0F};
            case GOLDEN -> new float[]{1.0F, 0.78F, 0.24F};
            case DIAMOND -> new float[]{0.16F, 1.0F, 0.98F};
            case NETHERITE -> new float[]{0.72F, 0.28F, 0.42F};
        };
    }

    @Override
    public ResourceLocation getTextureLocation(SwordArrayFieldEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    private static final class SpiritRenderStates extends RenderStateShard {
        private SpiritRenderStates() {
            super("yujiancraft_spirit_light_states", () -> { }, () -> { });
        }

        private static RenderType create() {
            return RenderType.create("yujiancraft_spirit_light", DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS, RenderType.SMALL_BUFFER_SIZE, false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                            .setTransparencyState(ADDITIVE_TRANSPARENCY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setCullState(NO_CULL)
                            .setLightmapState(NO_LIGHTMAP)
                            .setOverlayState(NO_OVERLAY)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false));
        }
    }
}
