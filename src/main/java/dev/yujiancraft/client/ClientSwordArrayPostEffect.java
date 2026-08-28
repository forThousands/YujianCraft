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

/** Data-driven full-screen VFX compositor for the Sword Array finisher. */
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
            ClientTechniqueOverlayState.showFinisherFlash(minecraft.level.getGameTime(), bottom,
                    bottom.add(0.0D, 28.0D, 0.0D),
                    37.0F, 10, 8, 7, 32);
        }
        ClientTechniqueOverlayState.FinisherFrame frame =
                ClientTechniqueOverlayState.sampleFinisher(event.getPartialTick());
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

        Projection impact = project(frame.bottom(), event.getCamera(), width, height,
                minecraft.options.fov().get());
        if (impact == null) impact = new Projection(0.5F, 0.58F, 12.0D);
        Projection top = project(frame.top(), event.getCamera(), width, height, minecraft.options.fov().get());
        Vec3 cameraRight = Vec3.directionFromRotation(0.0F, event.getCamera().getYRot() + 90.0F).normalize();
        Projection radiusPoint = project(frame.top().add(cameraRight.scale(frame.maximumRadius())),
                event.getCamera(), width, height, minecraft.options.fov().get());
        if (top == null) top = new Projection(impact.x, Math.min(1.25F, impact.y + 0.42F), impact.depth);
        float signalRadius = radiusPoint == null ? 0.22F
                : Math.abs(radiusPoint.x - top.x) * width / (float)Math.max(1, height);
        float[] distortionCenter = resolveCenter(frame, "distortion", impact);
        float[] radialCenter = resolveCenter(frame, "radialBlur", impact);
        float[] chromaCenter = resolveCenter(frame, "chromatic", impact);
        float[] vignetteCenter = resolveCenter(frame, "vignette", impact);
        float[] flowCenter = resolveCenter(frame, "flowFlash", impact);

        EffectInstance effect = postChain.passes.get(0).getEffect();
        effect.safeGetUniform("DistortionCenter").set(distortionCenter[0], distortionCenter[1]);
        effect.safeGetUniform("RadialCenter").set(radialCenter[0], radialCenter[1]);
        effect.safeGetUniform("ChromaCenter").set(chromaCenter[0], chromaCenter[1]);
        effect.safeGetUniform("VignetteCenter").set(vignetteCenter[0], vignetteCenter[1]);
        effect.safeGetUniform("FlowCenter").set(flowCenter[0], flowCenter[1]);
        effect.safeGetUniform("SignalBottom").set(impact.x, impact.y);
        effect.safeGetUniform("SignalTop").set(top.x, top.y);
        effect.safeGetUniform("SignalRadius").set(Math.max(0.08F, signalRadius));
        effect.safeGetUniform("SignalBeamWidth").set(Math.max(0.025F, signalRadius * 0.16F));
        effect.safeGetUniform("DistortionStrength").set(value(frame, "distortion", "post.distortion.strength", 0.0F));
        effect.safeGetUniform("DistortionRadius").set(value(frame, "distortion", "post.distortion.radius", 0.1F));
        effect.safeGetUniform("DistortionWidth").set(value(frame, "distortion", "post.distortion.width", 0.08F));
        effect.safeGetUniform("RadialBlurStrength").set(value(frame, "radialBlur", "post.radialBlur.strength", 0.0F));
        effect.safeGetUniform("ChromaticStrength").set(value(frame, "chromatic", "post.chromatic.strength", 0.0F));
        effect.safeGetUniform("BlurStrength").set(value(frame, "blur", "post.blur.strength", 0.0F));
        effect.safeGetUniform("Exposure").set(value(frame, "colorGrade", "post.color.exposure", 0.0F));
        effect.safeGetUniform("Contrast").set(value(frame, "colorGrade", "post.color.contrast", 1.0F));
        effect.safeGetUniform("Saturation").set(value(frame, "colorGrade", "post.color.saturation", 1.0F));
        effect.safeGetUniform("ThresholdAmount").set(value(frame, "thresholdFlash", "post.threshold.amount", 0.0F));
        effect.safeGetUniform("ThresholdLevel").set(value(frame, "thresholdFlash", "post.threshold.level", 0.5F));
        effect.safeGetUniform("ThresholdSoftness").set(value(frame, "thresholdFlash", "post.threshold.softness", 0.03F));
        effect.safeGetUniform("InvertAmount").set(value(frame, "thresholdFlash", "post.threshold.invert", 0.0F));
        effect.safeGetUniform("WhiteoutAmount").set(value(frame, "thresholdFlash", "post.threshold.whiteout", 0.0F));
        effect.safeGetUniform("ThresholdIsolation").set(value(frame, "thresholdFlash", "post.threshold.isolation", 0.92F));
        effect.safeGetUniform("SignalFeather").set(value(frame, "thresholdFlash", "post.threshold.signalFeather", 0.08F));
        effect.safeGetUniform("FlowFlashAmount").set(value(frame, "flowFlash", "post.flowFlash.amount", 0.0F));
        effect.safeGetUniform("FlowInvertIntensity").set(value(frame, "flowFlash", "post.flowFlash.invertIntensity", 1.0F));
        effect.safeGetUniform("FlowTransitionStart").set(value(frame, "flowFlash", "post.flowFlash.transitionStart", 0.25F));
        effect.safeGetUniform("FlowTransitionRange").set(value(frame, "flowFlash", "post.flowFlash.transitionRange", 0.5F));
        effect.safeGetUniform("FlowInvertAmount").set(value(frame, "flowFlash", "post.flowFlash.invertAmount", 0.0F));
        effect.safeGetUniform("FlowStrength").set(value(frame, "flowFlash", "post.flowFlash.flowStrength", 0.18F));
        effect.safeGetUniform("FlowScale").set(value(frame, "flowFlash", "post.flowFlash.flowScale", 9.0F));
        effect.safeGetUniform("FlowSpeed").set(value(frame, "flowFlash", "post.flowFlash.flowSpeed", 1.4F));
        effect.safeGetUniform("FlowSharpness").set(value(frame, "flowFlash", "post.flowFlash.flowSharpness", 0.22F));
        effect.safeGetUniform("FlowEnabled").set(value(frame, "flowFlash", "post.flowFlash.flowEnabled", 0.0F));
        float[] highlight = colour(frame.timeline().moduleSetting("flowFlash", "highlightColor", "#000000"));
        float[] shadow = colour(frame.timeline().moduleSetting("flowFlash", "shadowColor", "#ffffff"));
        effect.safeGetUniform("FlowHighlightColor").set(highlight[0], highlight[1], highlight[2]);
        effect.safeGetUniform("FlowShadowColor").set(shadow[0], shadow[1], shadow[2]);
        effect.safeGetUniform("GrainStrength").set(value(frame, "grain", "post.grain.strength", 0.0F));
        effect.safeGetUniform("GrainScale").set(value(frame, "grain", "post.grain.scale", 1.0F));
        effect.safeGetUniform("VignetteStrength").set(value(frame, "vignette", "post.vignette.strength", 0.0F));
        effect.safeGetUniform("VignetteRadius").set(value(frame, "vignette", "post.vignette.radius", 0.7F));
        effect.safeGetUniform("VignetteSoftness").set(value(frame, "vignette", "post.vignette.softness", 0.3F));
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

    private static float value(ClientTechniqueOverlayState.FinisherFrame frame, String module,
                               String track, float neutral) {
        return frame.enabled(module) ? frame.value(track, neutral) : neutral;
    }

    private static float[] resolveCenter(ClientTechniqueOverlayState.FinisherFrame frame,
                                         String module, Projection impact) {
        if ("beamImpact".equals(frame.anchor(module))) return new float[]{impact.x, impact.y};
        var center = frame.center(module, 0.5F, 0.5F);
        return new float[]{center.x(), center.y()};
    }

    private static float[] colour(String value) {
        try {
            String clean = value == null ? "" : value.strip().replace("#", "");
            int rgb = Integer.parseInt(clean, 16);
            return new float[]{((rgb >> 16) & 255) / 255.0F,
                    ((rgb >> 8) & 255) / 255.0F, (rgb & 255) / 255.0F};
        } catch (RuntimeException ignored) {
            return new float[]{1.0F, 1.0F, 1.0F};
        }
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
