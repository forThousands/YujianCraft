package dev.yujiancraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import dev.yujiancraft.entity.SwordArrayFieldEntity;
import dev.yujiancraft.material.FlyingSwordMaterial;
import dev.yujiancraft.wanxiang.WanxiangSwordData;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** Continuous emissive geometry for the large seal and its sustained heaven-to-ground pillar. */
public final class SwordArrayFieldRenderer extends EntityRenderer<SwordArrayFieldEntity> {
    /**
     * The vanilla lightning layer writes depth, so the outer cylinder can hide the cyan and
     * white cores drawn inside it. This additive layer still respects world depth, but does not
     * write its own depth; every energy layer therefore contributes to the final white-hot beam.
     */
    private static final RenderType SPIRIT_LIGHT = SpiritRenderStates.create();

    public SwordArrayFieldRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(SwordArrayFieldEntity field, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        float age = field.tickCount + partialTick;
        float[] material = color(WanxiangSwordData.material(field.getDisplayStack()));
        VertexConsumer vertices = buffers.getBuffer(SPIRIT_LIGHT);

        poseStack.pushPose();
        poseStack.translate(0.0D, field.beamHeight(), 0.0D);
        poseStack.mulPose(Axis.YP.rotation(age * 0.012F));
        float ringRadius = arrayRadius(field, age);
        renderSeal(vertices, poseStack.last().pose(), ringRadius, material, age);
        poseStack.popPose();

        float finisherAge = age - field.finisherStartTick();
        if (finisherAge >= 0.0F) {
            float beamRadius = beamRadius(field, finisherAge);
            float fade = beamFade(field, finisherAge);
            poseStack.pushPose();
            Matrix4f matrix = poseStack.last().pose();
            drawCylinder(vertices, matrix, beamRadius, field.beamHeight(),
                    material[0], material[1], material[2], Math.round(178.0F * fade), 16);
            drawCylinder(vertices, matrix, beamRadius * 0.72F, field.beamHeight(),
                    0.22F, 0.94F, 1.0F, Math.round(218.0F * fade), 14);
            drawCylinder(vertices, matrix, Math.max(0.22F, beamRadius * 0.42F), field.beamHeight(),
                    1.0F, 1.0F, 0.98F, Math.round(255.0F * fade), 12);
            renderImpactBands(vertices, matrix, field, finisherAge, material, fade);
            poseStack.popPose();
        }
        super.render(field, yaw, partialTick, poseStack, buffers, packedLight);
    }

    private static void renderSeal(VertexConsumer vertices, Matrix4f matrix, float radius,
                                   float[] material, float age) {
        float pulse = 0.92F + 0.08F * Mth.sin(age * 0.18F);
        drawAnnulus(vertices, matrix, radius * 0.91F, radius, 0.0F,
                material[0], material[1], material[2], Math.round(214.0F * pulse), 72);
        drawAnnulus(vertices, matrix, radius * 0.68F, radius * 0.715F, -0.045F,
                0.30F, 0.96F, 1.0F, Math.round(178.0F * pulse), 64);
        drawAnnulus(vertices, matrix, radius * 0.43F, radius * 0.458F, 0.04F,
                0.94F, 1.0F, 1.0F, Math.round(205.0F * pulse), 56);
        for (int index = 0; index < 12; index++) {
            double angle = Math.PI * 2.0D * index / 12.0D + age * 0.009D;
            drawSpoke(vertices, matrix, radius * 0.47F, radius * 0.89F, angle, radius * 0.012F,
                    0.68F, 0.98F, 1.0F, 128);
        }
    }

    private static void renderImpactBands(VertexConsumer vertices, Matrix4f matrix,
                                          SwordArrayFieldEntity field, float finisherAge,
                                          float[] material, float fade) {
        float burst = field.chargeTicks() + field.holdTicks();
        if (finisherAge < burst) return;
        float progress = Mth.clamp((finisherAge - burst) / Math.max(1.0F, field.expandTicks()), 0.0F, 1.0F);
        float radius = field.maximumBeamRadius() * (0.55F + progress * 0.75F);
        drawAnnulus(vertices, matrix, radius * 0.86F, radius, 0.035F,
                material[0], material[1], material[2], Math.round(205.0F * fade), 64);
        drawAnnulus(vertices, matrix, radius * 0.48F, radius * 0.56F, 0.075F,
                0.9F, 1.0F, 1.0F, Math.round(232.0F * fade), 56);
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
        float thin = Math.max(0.24F, field.baseRadius() * 0.075F);
        if (finisherAge < field.chargeTicks()) {
            float progress = Mth.clamp(finisherAge / Math.max(1.0F, field.chargeTicks()), 0.0F, 1.0F);
            return Mth.lerp(progress * progress, thin * 0.42F, thin);
        }
        if (finisherAge < field.chargeTicks() + field.holdTicks()) {
            return thin * (0.94F + 0.06F * Mth.sin(finisherAge * 0.9F));
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
        return Mth.clamp(0.38F + 0.62F * remaining
                / Math.max(1.0F, field.sustainTicks() - 8.0F), 0.0F, 1.0F);
    }

    private static void drawCylinder(VertexConsumer vertices, Matrix4f matrix, float radius, float height,
                                     float red, float green, float blue, int alpha, int segments) {
        for (int index = 0; index < segments; index++) {
            double a0 = Math.PI * 2.0D * index / segments;
            double a1 = Math.PI * 2.0D * (index + 1) / segments;
            float x0 = (float) Math.cos(a0) * radius;
            float z0 = (float) Math.sin(a0) * radius;
            float x1 = (float) Math.cos(a1) * radius;
            float z1 = (float) Math.sin(a1) * radius;
            vertex(vertices, matrix, x0, 0.0F, z0, red, green, blue, alpha);
            vertex(vertices, matrix, x1, 0.0F, z1, red, green, blue, alpha);
            vertex(vertices, matrix, x1, height, z1, red, green, blue, alpha);
            vertex(vertices, matrix, x0, height, z0, red, green, blue, alpha);
        }
    }

    private static void drawAnnulus(VertexConsumer vertices, Matrix4f matrix, float inner, float outer,
                                    float y, float red, float green, float blue, int alpha, int segments) {
        for (int index = 0; index < segments; index++) {
            double a0 = Math.PI * 2.0D * index / segments;
            double a1 = Math.PI * 2.0D * (index + 1) / segments;
            vertex(vertices, matrix, (float) Math.cos(a0) * inner, y, (float) Math.sin(a0) * inner,
                    red, green, blue, alpha);
            vertex(vertices, matrix, (float) Math.cos(a0) * outer, y, (float) Math.sin(a0) * outer,
                    red, green, blue, alpha);
            vertex(vertices, matrix, (float) Math.cos(a1) * outer, y, (float) Math.sin(a1) * outer,
                    red, green, blue, alpha);
            vertex(vertices, matrix, (float) Math.cos(a1) * inner, y, (float) Math.sin(a1) * inner,
                    red, green, blue, alpha);
        }
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
            return RenderType.create(
                    "yujiancraft_spirit_light",
                    DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS,
                    RenderType.SMALL_BUFFER_SIZE,
                    false,
                    true,
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
