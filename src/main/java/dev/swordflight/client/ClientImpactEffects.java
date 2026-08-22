package dev.swordflight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.swordflight.Swordflight;
import dev.swordflight.material.FlyingSwordMaterial;
import dev.swordflight.upgrade.FlyingSwordModule;
import dev.swordflight.upgrade.SwordModuleData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** Short-lived, client-rendered hit feedback. The server sends only one compact impact event. */
@Mod.EventBusSubscriber(modid = Swordflight.MOD_ID, value = Dist.CLIENT)
public final class ClientImpactEffects {
    private static final int LIFETIME_TICKS = 11;
    private static final int MAX_ACTIVE_IMPACTS = 48;
    private static final List<Impact> IMPACTS = new ArrayList<>();

    private ClientImpactEffects() {
    }

    public static void accept(Vec3 position, Vec3 direction, int visualModules, int materialOrdinal) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ClientOptions.hitImpactVisual() || minecraft.level == null || minecraft.player == null) return;
        if (position.distanceToSqr(minecraft.player.position()) > 64.0D * 64.0D) return;
        Vec3 safeDirection = direction.lengthSqr() < 1.0E-6D
                ? new Vec3(0.0D, 1.0D, 0.0D) : direction.normalize();
        if (IMPACTS.size() >= MAX_ACTIVE_IMPACTS) IMPACTS.remove(0);
        FlyingSwordMaterial material = FlyingSwordMaterial.fromOrdinal(materialOrdinal);
        IMPACTS.add(new Impact(position, safeDirection, visualModules, material.glowColor()));
        playImpactSound(minecraft, position, visualModules);
    }

    private static void playImpactSound(Minecraft minecraft, Vec3 position, int modules) {
        minecraft.level.playLocalSound(position.x, position.y, position.z, SoundEvents.TRIDENT_HIT,
                SoundSource.PLAYERS, 0.42F, 1.35F, false);
        SoundEvent accent = null;
        float volume = 0.25F;
        float pitch = 1.25F;
        if (enabledLevel(modules, FlyingSwordModule.EXPLOSION, ClientOptions.explosionModuleVisual()) > 0) {
            accent = SoundEvents.GENERIC_EXPLODE;
            volume = 0.24F;
            pitch = 1.65F;
        } else if (enabledLevel(modules, FlyingSwordModule.LIGHTNING, ClientOptions.lightningModuleVisual()) > 0) {
            accent = SoundEvents.LIGHTNING_BOLT_IMPACT;
            volume = 0.20F;
            pitch = 1.72F;
        } else if (enabledLevel(modules, FlyingSwordModule.FLAME, ClientOptions.flameModuleVisual()) > 0) {
            accent = SoundEvents.FIRECHARGE_USE;
            volume = 0.22F;
            pitch = 1.45F;
        } else if (enabledLevel(modules, FlyingSwordModule.POISON, ClientOptions.poisonModuleVisual()) > 0) {
            accent = SoundEvents.BREWING_STAND_BREW;
            volume = 0.18F;
            pitch = 1.55F;
        } else if (enabledLevel(modules, FlyingSwordModule.ARROW_RAIN, ClientOptions.arrowRainModuleVisual()) > 0) {
            accent = SoundEvents.ARROW_HIT_PLAYER;
            volume = 0.20F;
            pitch = 1.35F;
        }
        if (accent != null) {
            minecraft.level.playLocalSound(position.x, position.y, position.z, accent,
                    SoundSource.PLAYERS, volume, pitch, false);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            IMPACTS.clear();
            return;
        }
        IMPACTS.removeIf(impact -> ++impact.age >= LIFETIME_TICKS);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || IMPACTS.isEmpty() || !ClientOptions.hitImpactVisual()) return;
        Minecraft minecraft = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer vertices = buffers.getBuffer(RenderType.lightning());
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();

        for (Impact impact : IMPACTS) {
            float age = impact.age + partialTick;
            float progress = Mth.clamp(age / LIFETIME_TICKS, 0.0F, 1.0F);
            float fade = 1.0F - progress;
            poseStack.pushPose();
            Vec3 local = impact.position.subtract(camera);
            poseStack.translate(local.x, local.y, local.z);
            Vec3 viewNormal = camera.subtract(impact.position);
            if (viewNormal.lengthSqr() < 1.0E-6D) viewNormal = impact.direction.scale(-1.0D);
            renderImpact(vertices, poseStack.last().pose(), impact, viewNormal.normalize(), progress, fade);
            poseStack.popPose();
        }
        buffers.endBatch(RenderType.lightning());
    }

    private static void renderImpact(VertexConsumer vertices, Matrix4f pose, Impact impact,
                                     Vec3 viewNormal, float progress, float fade) {
        // Face the principal rings toward the camera. The old directional ring could become
        // completely edge-on, making a plain sword appear to have no hit feedback at all.
        Vec3 normal = viewNormal;
        Vec3 basisA = normal.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (basisA.lengthSqr() < 1.0E-6D) basisA = normal.cross(new Vec3(1.0D, 0.0D, 0.0D));
        basisA = basisA.normalize();
        Vec3 basisB = normal.cross(basisA).normalize();
        int materialRed = impact.materialColor >> 16 & 0xFF;
        int materialGreen = impact.materialColor >> 8 & 0xFF;
        int materialBlue = impact.materialColor & 0xFF;
        int[] accent = accentColor(impact.visualModules, materialRed, materialGreen, materialBlue);

        float radius = 0.14F + progress * 0.92F;
        int outerAlpha = Mth.clamp(Math.round(205.0F * fade * fade), 0, 255);
        renderRing(vertices, pose, basisA, basisB, radius, 14, 0.018F,
                accent[0], accent[1], accent[2], outerAlpha);

        float coreRadius = 0.07F + progress * 0.36F;
        int coreAlpha = Mth.clamp(Math.round(255.0F * Math.max(0.0F, 1.0F - progress * 1.7F)), 0, 255);
        renderRing(vertices, pose, basisA, basisB, coreRadius, 10, 0.025F,
                255, 255, 255, coreAlpha);

        float rayLength = 0.18F + progress * 0.38F;
        int rayAlpha = Mth.clamp(Math.round(225.0F * Math.max(0.0F, 1.0F - progress * 2.2F)), 0, 255);
        renderRay(vertices, pose, basisA.scale(-rayLength), basisA.scale(rayLength), basisB.scale(0.022F), rayAlpha);
        renderRay(vertices, pose, basisB.scale(-rayLength), basisB.scale(rayLength), basisA.scale(0.022F), rayAlpha);

        float pierceLength = 0.10F + progress * 0.24F;
        renderRay(vertices, pose, impact.direction.scale(-pierceLength),
                impact.direction.scale(pierceLength), basisA.scale(0.018F), rayAlpha);
    }

    private static void renderRing(VertexConsumer vertices, Matrix4f pose, Vec3 basisA, Vec3 basisB,
                                   float radius, int sides, float width,
                                   int red, int green, int blue, int alpha) {
        for (int index = 0; index < sides; index++) {
            double a = Math.PI * 2.0D * index / sides;
            double b = Math.PI * 2.0D * (index + 1) / sides;
            Vec3 start = basisA.scale(Math.cos(a) * radius).add(basisB.scale(Math.sin(a) * radius));
            Vec3 end = basisA.scale(Math.cos(b) * radius).add(basisB.scale(Math.sin(b) * radius));
            Vec3 startSide = start.normalize().scale(width);
            Vec3 endSide = end.normalize().scale(width);
            vertex(vertices, pose, start.add(startSide), red, green, blue, alpha);
            vertex(vertices, pose, end.add(endSide), red, green, blue, alpha);
            vertex(vertices, pose, end.subtract(endSide), red, green, blue, alpha);
            vertex(vertices, pose, start.subtract(startSide), red, green, blue, alpha);
        }
    }

    private static void renderRay(VertexConsumer vertices, Matrix4f pose, Vec3 start, Vec3 end,
                                  Vec3 side, int alpha) {
        vertex(vertices, pose, start.add(side), 255, 255, 255, 0);
        vertex(vertices, pose, end.add(side), 255, 255, 255, alpha);
        vertex(vertices, pose, end.subtract(side), 255, 255, 255, alpha);
        vertex(vertices, pose, start.subtract(side), 255, 255, 255, 0);
    }

    private static void vertex(VertexConsumer vertices, Matrix4f pose, Vec3 point,
                               int red, int green, int blue, int alpha) {
        vertices.vertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .color(red, green, blue, alpha).endVertex();
    }

    private static int[] accentColor(int modules, int red, int green, int blue) {
        if (enabledLevel(modules, FlyingSwordModule.EXPLOSION, ClientOptions.explosionModuleVisual()) > 0) {
            return new int[]{255, 92, 30};
        }
        if (enabledLevel(modules, FlyingSwordModule.LIGHTNING, ClientOptions.lightningModuleVisual()) > 0) {
            return new int[]{126, 222, 255};
        }
        if (enabledLevel(modules, FlyingSwordModule.FLAME, ClientOptions.flameModuleVisual()) > 0) {
            return new int[]{255, 157, 45};
        }
        if (enabledLevel(modules, FlyingSwordModule.POISON, ClientOptions.poisonModuleVisual()) > 0) {
            return new int[]{91, 224, 108};
        }
        if (enabledLevel(modules, FlyingSwordModule.ARROW_RAIN, ClientOptions.arrowRainModuleVisual()) > 0) {
            return new int[]{207, 244, 255};
        }
        return new int[]{red, green, blue};
    }

    private static int enabledLevel(int modules, FlyingSwordModule module, boolean enabled) {
        return enabled ? SwordModuleData.visualEffectLevel(modules, module) : 0;
    }

    private static final class Impact {
        private final Vec3 position;
        private final Vec3 direction;
        private final int visualModules;
        private final int materialColor;
        private int age;

        private Impact(Vec3 position, Vec3 direction, int visualModules, int materialColor) {
            this.position = position;
            this.direction = direction;
            this.visualModules = visualModules;
            this.materialColor = materialColor;
        }
    }
}
