package dev.yujiancraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.entity.ComboAureoleEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Body-aligned, double-sided light wheel that follows its owner like a formation sword. */
public final class ComboAureoleRenderer extends EntityRenderer<ComboAureoleEntity> {
    private static final ResourceLocation BASE = ResourceLocation.fromNamespaceAndPath(
            YujianCraft.MOD_ID, "textures/effect/sword_array/tricolor_outer_base.png");
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(
            YujianCraft.MOD_ID, "textures/effect/sword_array/tricolor_outer_glow.png");

    public ComboAureoleRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(ComboAureoleEntity aureole, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        Entity owner = aureole.owner();
        if (owner == null || !owner.isAlive()) return;

        Vec3 ownerPosition = interpolated(owner, partialTick);
        Vec3 entityPosition = interpolated(aureole, partialTick);
        float bodyYaw = owner instanceof LivingEntity living
                ? Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot)
                : Mth.rotLerp(partialTick, owner.yRotO, owner.getYRot());
        Vec3 forward = Vec3.directionFromRotation(0.0F, bodyYaw).normalize();
        float age = aureole.renderAge(partialTick);
        float fadeIn = Mth.clamp(age / 3.0F, 0.0F, 1.0F);
        float fadeOut = Mth.clamp((aureole.lifetimeTicks() - age) / 7.0F, 0.0F, 1.0F);
        float pulse = (0.92F + 0.08F * (float) Math.sin(age * 0.40F))
                * Math.min(fadeIn, fadeOut);
        if (pulse <= 0.001F) return;

        poseStack.pushPose();
        Vec3 correction = ownerPosition.subtract(entityPosition)
                .add(0.0D, Math.max(0.35F, owner.getEyeHeight() - 0.12F), 0.0D)
                .subtract(forward.scale(0.38D));
        poseStack.translate(correction.x, correction.y, correction.z);
        // The wheel remains anchored behind the body, but its plane faces the camera. This keeps
        // the circular seal readable from every third-person angle instead of becoming edge-on.
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.ZP.rotation(age * 0.060F));
        drawLayer(buffers.getBuffer(RenderType.entityTranslucentEmissive(BASE)), poseStack,
                aureole.radius(), Math.round(156.0F * pulse));
        poseStack.mulPose(Axis.ZP.rotation(-age * 0.134F));
        drawLayer(buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW)), poseStack,
                aureole.radius() * 1.105F, Math.round(78.0F * pulse));
        poseStack.popPose();
        super.render(aureole, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    private static Vec3 interpolated(Entity entity, float partialTick) {
        return new Vec3(Mth.lerp(partialTick, entity.xo, entity.getX()),
                Mth.lerp(partialTick, entity.yo, entity.getY()),
                Mth.lerp(partialTick, entity.zo, entity.getZ()));
    }

    private static void drawLayer(VertexConsumer vertices, PoseStack poseStack,
                                  float radius, int alpha) {
        Matrix4f pose = poseStack.last().pose();
        quad(vertices, pose, new Vec3(1.0D, 0.0D, 0.0D),
                new Vec3(0.0D, 1.0D, 0.0D), radius, alpha, false);
        quad(vertices, pose, new Vec3(1.0D, 0.0D, 0.0D),
                new Vec3(0.0D, 1.0D, 0.0D), radius, alpha, true);
    }

    private static void quad(VertexConsumer vertices, Matrix4f pose, Vec3 right, Vec3 up,
                             float radius, int alpha, boolean reverse) {
        Vec3 a = right.scale(-radius).add(up.scale(-radius));
        Vec3 b = right.scale(-radius).add(up.scale(radius));
        Vec3 c = right.scale(radius).add(up.scale(radius));
        Vec3 d = right.scale(radius).add(up.scale(-radius));
        if (reverse) {
            vertex(vertices, pose, d, 1, 0, alpha); vertex(vertices, pose, c, 1, 1, alpha);
            vertex(vertices, pose, b, 0, 1, alpha); vertex(vertices, pose, a, 0, 0, alpha);
        } else {
            vertex(vertices, pose, a, 0, 0, alpha); vertex(vertices, pose, b, 0, 1, alpha);
            vertex(vertices, pose, c, 1, 1, alpha); vertex(vertices, pose, d, 1, 0, alpha);
        }
    }

    private static void vertex(VertexConsumer vertices, Matrix4f pose, Vec3 point,
                               float u, float v, int alpha) {
        vertices.vertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .color(212, 255, 244, Mth.clamp(alpha, 0, 255)).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(0.0F, 0.0F, 1.0F).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(ComboAureoleEntity entity) {
        return BASE;
    }
}
