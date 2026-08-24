package dev.yujiancraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.yujiancraft.entity.SwordQiEntity;
import dev.yujiancraft.material.FlyingSwordMaterial;
import dev.yujiancraft.wanxiang.WanxiangSwordData;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** Low-polygon emissive crescent with a small depth shell, independent of particle density. */
public final class SwordQiRenderer extends EntityRenderer<SwordQiEntity> {
    public SwordQiRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(SwordQiEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();
        float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        float[] color = color(WanxiangSwordData.material(entity.getDisplayStack()));
        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();
        drawCrescent(consumer, matrix, color[0], color[1], color[2], -0.07F, 150);
        drawCrescent(consumer, matrix, 0.92F, 1.0F, 1.0F, 0.07F, 225);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    private static void drawCrescent(VertexConsumer consumer, Matrix4f matrix,
                                     float red, float green, float blue, float z, int alpha) {
        int segments = 12;
        for (int index = 0; index < segments; index++) {
            double a0 = -Math.PI * 0.72D + Math.PI * 1.44D * index / segments;
            double a1 = -Math.PI * 0.72D + Math.PI * 1.44D * (index + 1) / segments;
            float outer = 1.75F;
            float inner = 1.12F + 0.10F * (float) Math.cos((a0 + a1) * 0.5D);
            vertex(consumer, matrix, (float) Math.sin(a0) * inner, (float) Math.cos(a0) * inner, z,
                    red, green, blue, alpha);
            vertex(consumer, matrix, (float) Math.sin(a0) * outer, (float) Math.cos(a0) * outer, z,
                    red, green, blue, alpha);
            vertex(consumer, matrix, (float) Math.sin(a1) * outer, (float) Math.cos(a1) * outer, z,
                    red, green, blue, alpha);
            vertex(consumer, matrix, (float) Math.sin(a1) * inner, (float) Math.cos(a1) * inner, z,
                    red, green, blue, alpha);
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z,
                               float red, float green, float blue, int alpha) {
        consumer.vertex(matrix, x, y, z).color(red, green, blue, alpha / 255.0F).endVertex();
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
    public ResourceLocation getTextureLocation(SwordQiEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
