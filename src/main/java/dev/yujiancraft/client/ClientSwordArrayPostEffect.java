package dev.yujiancraft.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.logging.LogUtils;
import dev.yujiancraft.YujianCraft;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.io.IOException;

/** Full-screen monochrome impact-frame compositor for the Sword Array finisher. */
@Mod.EventBusSubscriber(modid = YujianCraft.MOD_ID, value = Dist.CLIENT)
public final class ClientSwordArrayPostEffect {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation CHAIN = ResourceLocation.fromNamespaceAndPath(
            YujianCraft.MOD_ID, "shaders/post/sword_array_impact.json");
    private static PostChain postChain;
    private static int width = -1;
    private static int height = -1;
    private static boolean failed;
    private static long debugLastTriggered;

    private ClientSwordArrayPostEffect() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        if (!ClientOptions.swordArrayPostEffect()) {
            closeChain();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        // Hidden development hook used by automated client startup to compile and exercise the
        // post chain. It is inert in normal installations and deliberately has no menu surface.
        long now = Util.getMillis();
        if ((Boolean.getBoolean("yujiancraft.debugSwordArrayImpact")
                || "true".equalsIgnoreCase(System.getenv("YUJIANCRAFT_DEBUG_SWORD_ARRAY_IMPACT")))
                && minecraft.player != null && now - debugLastTriggered >= 8000L) {
            debugLastTriggered = now;
            Vec3 bottom = minecraft.player.position().add(minecraft.player.getLookAngle().scale(8.0D));
            ClientTechniqueOverlayState.showFinisherFlash(bottom, bottom.add(0.0D, 28.0D, 0.0D),
                    37.0F, 10, 8, 7, 32);
        }
        ClientTechniqueOverlayState.FinisherFrame frame =
                ClientTechniqueOverlayState.sampleFinisher(now);
        if (frame == null) {
            closeChain();
            return;
        }

        if (minecraft.level == null || minecraft.player == null || failed || !ensureChain(minecraft)) return;
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        if (mainTarget.width != width || mainTarget.height != height) {
            width = mainTarget.width;
            height = mainTarget.height;
            postChain.resize(width, height);
        }

        Projection bottom = project(frame.bottom(), event.getCamera(), width, height,
                minecraft.options.fov().get());
        Projection top = project(frame.top(), event.getCamera(), width, height,
                minecraft.options.fov().get());
        if (bottom == null || top == null) {
            bottom = new Projection(0.5F, 0.86F, 12.0D);
            top = new Projection(0.5F, 0.14F, 12.0D);
        }
        float thinRadius = Math.max(0.32F, frame.maximumRadius() * 0.045F);
        float worldRadius = thinRadius + (frame.maximumRadius() - thinRadius) * frame.expansion();
        double focal = height / (2.0D * Math.tan(Math.toRadians(minecraft.options.fov().get()) * 0.5D));
        double averageDepth = Math.max(0.2D, (bottom.depth + top.depth) * 0.5D);
        float radiusUv = (float) Math.min(2.2D, Math.max(0.0025D,
                worldRadius * focal / averageDepth / Math.max(1, height)));

        EffectInstance effect = postChain.passes.get(0).getEffect();
        effect.safeGetUniform("BeamBottom").set(bottom.x, bottom.y);
        effect.safeGetUniform("BeamTop").set(top.x, top.y);
        effect.safeGetUniform("BeamRadius").set(radiusUv);
        effect.safeGetUniform("Charge").set(frame.charge());
        effect.safeGetUniform("DarkAmount").set(frame.darkAmount());
        effect.safeGetUniform("Expansion").set(frame.expansion());
        effect.safeGetUniform("WhiteAmount").set(frame.whiteAmount());
        effect.safeGetUniform("InkAmount").set(frame.inkAmount());
        effect.safeGetUniform("Recovery").set(frame.recovery());
        effect.safeGetUniform("Distortion").set(frame.distortion());
        effect.safeGetUniform("ChromaAmount").set(frame.chromaAmount());
        effect.safeGetUniform("Time").set(frame.ageSeconds());
        postChain.process(event.getPartialTick());
        mainTarget.bindWrite(false);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && Minecraft.getInstance().level == null) closeChain();
    }

    private static boolean ensureChain(Minecraft minecraft) {
        if (postChain != null) return true;
        try {
            postChain = new PostChain(minecraft.getTextureManager(), minecraft.getResourceManager(),
                    minecraft.getMainRenderTarget(), CHAIN);
            width = minecraft.getMainRenderTarget().width;
            height = minecraft.getMainRenderTarget().height;
            postChain.resize(width, height);
            return true;
        } catch (IOException | RuntimeException exception) {
            failed = true;
            LOGGER.error("Unable to load the Yujian Craft Sword Array impact post effect", exception);
            closeChain();
            return false;
        }
    }

    private static Projection project(Vec3 world, Camera camera, int width, int height, double fov) {
        Vec3 relative = world.subtract(camera.getPosition());
        Vec3 forward = Vec3.directionFromRotation(camera.getXRot(), camera.getYRot()).normalize();
        Vec3 right = Vec3.directionFromRotation(0.0F, camera.getYRot() + 90.0F).normalize();
        Vec3 up = right.cross(forward).normalize();
        double depth = relative.dot(forward);
        if (depth <= 0.08D) return null;
        double focal = height / (2.0D * Math.tan(Math.toRadians(fov) * 0.5D));
        float x = (float) (0.5D + relative.dot(right) * focal / depth / Math.max(1, width));
        // Frame-buffer texture coordinates have their origin at the bottom-left.
        float y = (float) (0.5D + relative.dot(up) * focal / depth / Math.max(1, height));
        return new Projection(clamp(x, -1.5F, 2.5F), clamp(y, -1.5F, 2.5F), depth);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void closeChain() {
        if (postChain != null) {
            postChain.close();
            postChain = null;
        }
        width = -1;
        height = -1;
    }

    private record Projection(float x, float y, double depth) { }
}
