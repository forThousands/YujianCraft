package dev.yujiancraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.yujiancraft.YujianCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Non-recursive qi silhouettes, destination previews and compact sword-array aureoles. */
@Mod.EventBusSubscriber(modid = YujianCraft.MOD_ID, value = Dist.CLIENT)
public final class ClientComboWarpEffects {
    private ClientComboWarpEffects() { }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void hidePlayerDuringTransition(RenderPlayerEvent.Pre event) {
        if (ClientComboState.shouldHidePlayer(event.getEntity().getId(), event.getPartialTick())) {
            // Cancelling this pass hides equipment and the nameplate together. No invisibility
            // effect, recursive PlayerRenderer call or potion particles are involved.
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void renderWarp(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || !ClientOptions.hitImpactVisual()) return;
        var visuals = ClientComboState.warpVisuals(event.getPartialTick());
        if (visuals.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        if (!visuals.isEmpty()) {
            VertexConsumer qi = buffers.getBuffer(RenderType.lightning());
            for (ClientComboState.WarpVisual visual : visuals) {
                if (!visual.transition()) continue;
                float lead = Mth.clamp(visual.age() / Math.max(1.0F, visual.executeTick()), 0.0F, 1.0F);
                if (visual.age() < visual.executeTick() + 0.2F) {
                    int alpha = Math.round((22.0F + lead * 82.0F) * visual.strength());
                    drawQiSilhouette(qi, poseStack, visual.destination(), camera, alpha,
                            0.88F + lead * 0.10F, visual.age() * 0.24F);
                }
                if (visual.age() >= visual.executeTick() - 0.2F) {
                    float afterAge = visual.age() - visual.executeTick();
                    float fade = 1.0F - Mth.clamp(afterAge / 6.0F, 0.0F, 1.0F);
                    if (fade > 0.0F) {
                        drawQiSilhouette(qi, poseStack, visual.origin(), camera,
                                Math.round(150.0F * fade * visual.strength()),
                                1.0F + afterAge * 0.055F, -afterAge * 0.31F);
                        drawArrivalBurst(qi, poseStack, visual.destination(), camera,
                                afterAge, fade, visual.strength());
                    }
                }
            }
            buffers.endBatch(RenderType.lightning());
        }
    }

    private static void drawQiSilhouette(VertexConsumer vertices, PoseStack stack, Vec3 world,
                                         Vec3 camera, int alpha, float scale, float phase) {
        stack.pushPose();
        Vec3 local = world.subtract(camera);
        stack.translate(local.x, local.y, local.z);
        stack.scale(scale, scale, scale);
        Matrix4f pose = stack.last().pose();
        for (int index = 0; index < 6; index++) {
            double angle = Math.PI * index / 3.0D + phase;
            float x = (float) Math.cos(angle) * (0.18F + (index & 1) * 0.08F);
            float z = (float) Math.sin(angle) * (0.18F + (index & 1) * 0.08F);
            float width = 0.055F + (index % 3) * 0.012F;
            quad(vertices, pose, x - width, 0.12F, z, x + width, 2.02F, z,
                    176, 255, 239, Mth.clamp(alpha - index * 8, 0, 255));
        }
        // Head/core diamonds keep the image recognisably human-shaped without cloning a skin.
        diamond(vertices, pose, 0.0F, 1.78F, 0.0F, 0.24F, 210, 255, 248, alpha);
        diamond(vertices, pose, 0.0F, 1.10F, 0.0F, 0.36F, 92, 231, 221,
                Math.round(alpha * 0.72F));
        stack.popPose();
    }

    private static void drawArrivalBurst(VertexConsumer vertices, PoseStack stack, Vec3 world,
                                         Vec3 camera, float age, float fade, float strength) {
        stack.pushPose();
        Vec3 local = world.subtract(camera).add(0.0D, 1.0D, 0.0D);
        stack.translate(local.x, local.y, local.z);
        Matrix4f pose = stack.last().pose();
        float radius = 0.35F + Math.max(0.0F, age) * 0.42F;
        int alpha = Math.round(190.0F * fade * strength);
        for (int i = 0; i < 12; i++) {
            double angle = Math.PI * 2.0D * i / 12.0D + age * 0.15D;
            float x = (float) Math.cos(angle) * radius;
            float z = (float) Math.sin(angle) * radius;
            quad(vertices, pose, x * 0.22F, -0.04F, z * 0.22F, x, 0.06F, z,
                    224, 255, 246, alpha);
        }
        stack.popPose();
    }

    private static void quad(VertexConsumer vertices, Matrix4f pose,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             int red, int green, int blue, int alpha) {
        float side = 0.045F;
        vertex(vertices, pose, x0 - side, y0, z0 - side, red, green, blue, 0);
        vertex(vertices, pose, x1 - side, y1, z1 - side, red, green, blue, alpha);
        vertex(vertices, pose, x1 + side, y1, z1 + side, red, green, blue, alpha);
        vertex(vertices, pose, x0 + side, y0, z0 + side, red, green, blue, 0);
    }

    private static void diamond(VertexConsumer vertices, Matrix4f pose, float x, float y, float z,
                                float radius, int red, int green, int blue, int alpha) {
        vertex(vertices, pose, x, y + radius, z, red, green, blue, alpha);
        vertex(vertices, pose, x + radius, y, z, red, green, blue, Math.round(alpha * 0.72F));
        vertex(vertices, pose, x, y - radius, z, red, green, blue, alpha);
        vertex(vertices, pose, x - radius, y, z, red, green, blue, Math.round(alpha * 0.72F));
    }

    private static void vertex(VertexConsumer vertices, Matrix4f pose, float x, float y, float z,
                               int red, int green, int blue, int alpha) {
        vertices.vertex(pose, x, y, z).color(red, green, blue, Mth.clamp(alpha, 0, 255)).endVertex();
    }
}
