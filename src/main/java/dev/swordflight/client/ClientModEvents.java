package dev.swordflight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.platform.InputConstants;
import dev.swordflight.Swordflight;
import dev.swordflight.entity.FlyingSwordEntity;
import dev.swordflight.formation.FormationGeometry;
import dev.swordflight.registry.ModEntities;
import dev.swordflight.registry.ModItems;
import dev.swordflight.material.FlyingSwordMaterial;
import dev.swordflight.visual.FlyingSwordModelStyle;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = Swordflight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    public static final KeyMapping SWITCH_FORMATION = new KeyMapping(
            "key.swordflight.switch_formation",
            KeyConflictContext.IN_GAME,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.swordflight"
    );
    public static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "key.swordflight.open_config",
            KeyConflictContext.IN_GAME,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            "key.categories.swordflight"
    );

    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.FLYING_SWORD.get(), FlyingSwordRenderer::new);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(SWITCH_FORMATION);
        event.register(OPEN_CONFIG);
    }

    @SubscribeEvent
    public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("optimized_third_person_crosshair",
                OptimizedThirdPersonController::renderCrosshair);
    }

    @SubscribeEvent
    public static void registerMenuScreens(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ClientOptions.load();
            ResourceLocation modelStyleProperty = ResourceLocation.fromNamespaceAndPath(
                    Swordflight.MOD_ID, "model_style");
            for (FlyingSwordMaterial material : FlyingSwordMaterial.values()) {
                ItemProperties.register(ModItems.getFlyingSword(material), modelStyleProperty,
                        (stack, level, living, seed) -> material == FlyingSwordMaterial.IRON
                                ? ClientOptions.swordModelStyle().predicateValue() : 0.0F);
            }
            MenuScreens.register(dev.swordflight.registry.ModMenus.FLYING_SWORD_WORKBENCH.get(),
                    FlyingSwordWorkbenchScreen::new);
        });
    }

    private static final class FlyingSwordRenderer extends EntityRenderer<FlyingSwordEntity> {
        private static final ResourceLocation SPIRIT_SHELL_TEXTURE = ResourceLocation.fromNamespaceAndPath(
                Swordflight.MOD_ID, "textures/effect/spirit_shell.png");
        private static final ResourceLocation SPIRIT_PULSE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
                Swordflight.MOD_ID, "textures/effect/spirit_pulse.png");
        private static final int SPIRIT_SIDES = 8;
        private final ItemRenderer itemRenderer;

        private FlyingSwordRenderer(EntityRendererProvider.Context context) {
            super(context);
            itemRenderer = context.getItemRenderer();
            shadowRadius = 0.0F;
        }

        @Override
        public void render(FlyingSwordEntity sword, float yaw, float partialTick, PoseStack poseStack,
                           MultiBufferSource buffers, int packedLight) {
            poseStack.pushPose();

            Vec3 renderedSwordPosition = new Vec3(
                    Mth.lerp(partialTick, sword.xo, sword.getX()),
                    Mth.lerp(partialTick, sword.yo, sword.getY()),
                    Mth.lerp(partialTick, sword.zo, sword.getZ())
            );
            if (!sword.isVisuallyDocked()) {
                renderTrail(sword, renderedSwordPosition, poseStack, buffers);
            }

            if (sword.isVisuallyDocked()) {
                Player owner = sword.getVisualOwner();
                if (owner != null) {
                    Vec3 ownerPosition = new Vec3(
                            Mth.lerp(partialTick, owner.xo, owner.getX()),
                            Mth.lerp(partialTick, owner.yo, owner.getY()),
                            Mth.lerp(partialTick, owner.zo, owner.getZ())
                    );
                    float ownerYaw = Mth.rotLerp(partialTick, owner.yRotO, owner.getYRot());
                    Vec3 desired = FormationGeometry.dockPosition(ownerPosition, ownerYaw,
                            sword.getVisualFormationSlot(), sword.getVisualFormationMode());
                    Vec3 correction = desired.subtract(renderedSwordPosition);
                    poseStack.translate(correction.x, correction.y, correction.z);
                }
            }

            float renderYaw = Mth.rotLerp(partialTick, sword.yRotO, sword.getYRot());
            float renderPitch = Mth.lerp(partialTick, sword.xRotO, sword.getXRot());
            Vec3 direction = Vec3.directionFromRotation(renderPitch, renderYaw);
            poseStack.mulPose(new Quaternionf().rotationTo(
                    0.0F, 1.0F, 0.0F,
                    (float) direction.x, (float) direction.y, (float) direction.z
            ));

            // FIXED rotates the vanilla item 180 degrees around Y. -45 aligns the real blade axis.
            // Mode A keeps its earlier +45 appearance only while docked; once launched, every
            // formation and material must put the blade tip on the velocity vector.
            boolean legacyDockPose = sword.isVisuallyDocked()
                    && sword.getVisualFormationMode().usesLegacyVisualAxis();
            boolean customThreeDimensionalModel = sword.getVisualMaterial() == FlyingSwordMaterial.IRON
                    && ClientOptions.swordModelStyle() != FlyingSwordModelStyle.ORIGINAL;
            float axisCorrection = customThreeDimensionalModel
                    ? (legacyDockPose ? 90.0F : 0.0F)
                    : (legacyDockPose ? 45.0F : -45.0F);
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(axisCorrection));
            if (ClientOptions.swordBodyGlow()) {
                poseStack.pushPose();
                if (!customThreeDimensionalModel) {
                    // Vanilla swords are diagonal sprites. FIXED mirrors X, so a local +45 degree
                    // correction puts this independently-rendered aura on the same blade axis.
                    poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(45.0F));
                }
                renderBladeAura(sword, partialTick, poseStack, buffers, customThreeDimensionalModel);
                poseStack.popPose();
            }
            poseStack.scale(1.25F, 1.25F, 1.25F);
            if (!customThreeDimensionalModel) {
                // Elongate the vanilla sprite along its own post-FIXED diagonal instead of scaling
                // X or Y globally. Its familiar pixel width remains intact while the flying sword
                // gains a longer silhouette; inventory and ordinary held rendering are untouched.
                poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(135.0F));
                poseStack.scale(1.22F, 0.98F, 1.0F);
                poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-135.0F));
            }
            int swordLight = ClientOptions.swordBodyGlow() ? LightTexture.FULL_BRIGHT : packedLight;
            itemRenderer.renderStatic(sword.getDisplayItem(), ItemDisplayContext.FIXED, swordLight,
                    OverlayTexture.NO_OVERLAY, poseStack, buffers, sword.level(), sword.getId());
            poseStack.popPose();
            super.render(sword, yaw, partialTick, poseStack, buffers, packedLight);
        }

        private void renderTrail(FlyingSwordEntity sword, Vec3 renderedPosition, PoseStack poseStack,
                                 MultiBufferSource buffers) {
            List<Vec3> samples = ClientFlightEffects.trailPoints(sword);
            if (samples.isEmpty()) return;
            List<Vec3> path = new ArrayList<>(samples.size() + 1);
            path.add(renderedPosition);
            for (Vec3 sample : samples) {
                if (path.get(path.size() - 1).distanceToSqr(sample) > 0.0025D) path.add(sample);
            }
            if (path.size() < 2) return;

            int color = sword.getVisualMaterial().glowColor();
            int red = color >> 16 & 0xFF;
            int green = color >> 8 & 0xFF;
            int blue = color & 0xFF;
            VertexConsumer vertices = buffers.getBuffer(RenderType.lightning());
            Matrix4f pose = poseStack.last().pose();
            Vec3 cameraPosition = entityRenderDispatcher.camera.getPosition();
            renderRibbonLayer(vertices, pose, path, renderedPosition, cameraPosition,
                    red, green, blue, 0.24D, 140);
            renderRibbonLayer(vertices, pose, path, renderedPosition, cameraPosition,
                    mixWithWhite(red, 0.48F), mixWithWhite(green, 0.48F), mixWithWhite(blue, 0.48F),
                    0.092D, 235);
            renderRibbonLayer(vertices, pose, path, renderedPosition, cameraPosition,
                    mixWithWhite(red, 0.88F), mixWithWhite(green, 0.88F), mixWithWhite(blue, 0.88F),
                    0.026D, 255);
        }

        private static void renderBladeAura(FlyingSwordEntity sword, float partialTick, PoseStack poseStack,
                                            MultiBufferSource buffers, boolean formalModel) {
            int color = sword.getVisualMaterial().glowColor();
            int red = color >> 16 & 0xFF;
            int green = color >> 8 & 0xFF;
            int blue = color & 0xFF;
            float pulse = 0.88F + 0.12F * Mth.sin((sword.tickCount + partialTick) * 0.18F
                    + sword.getVisualFormationSlot() * 0.9F);
            int flightAge = ClientFlightEffects.flightAge(sword);
            float launchSurge = flightAge > 0 && flightAge <= 7 ? (8.0F - flightAge) / 7.0F : 0.0F;

            float expansion = 1.0F + launchSurge * 0.08F;
            float base = formalModel ? -0.25F : -0.18F;
            float middle = formalModel ? 0.88F : 0.64F;
            float shoulder = formalModel ? 1.12F : 0.88F;
            float tip = formalModel ? 1.31F : 1.16F;
            float[] y = {base, middle, shoulder, tip};
            float[] faceRadius = formalModel
                    ? new float[]{0.105F, 0.152F, 0.112F, 0.004F}
                    : new float[]{0.075F, 0.125F, 0.096F, 0.004F};
            float[] depthRadius = formalModel
                    ? new float[]{0.042F, 0.066F, 0.052F, 0.003F}
                    : new float[]{0.030F, 0.055F, 0.044F, 0.003F};
            for (int index = 0; index < faceRadius.length; index++) {
                faceRadius[index] *= expansion;
                depthRadius[index] *= expansion;
            }
            float[] shellAlpha = {0.48F, 1.0F, 0.82F, 0.08F};
            int shellAlphaScale = Mth.clamp((int) ((96.0F + launchSurge * 58.0F) * pulse), 0, 255);
            VertexConsumer shell = buffers.getBuffer(RenderType.entityTranslucentEmissive(
                    SPIRIT_SHELL_TEXTURE, false));
            renderSpiritPrism(shell, poseStack.last(), y, faceRadius, depthRadius, shellAlpha,
                    red, green, blue, shellAlphaScale);

            float[] coreFaceRadius = {0.014F, 0.021F, 0.017F, 0.002F};
            float[] coreDepthRadius = {0.012F, 0.018F, 0.014F, 0.002F};
            int coreAlphaScale = Mth.clamp((int) ((202.0F + launchSurge * 45.0F) * pulse), 0, 255);
            renderSpiritPrism(shell, poseStack.last(), y, coreFaceRadius, coreDepthRadius,
                    new float[]{0.52F, 1.0F, 0.90F, 0.12F},
                    mixWithWhite(red, 0.90F), mixWithWhite(green, 0.90F), mixWithWhite(blue, 0.90F),
                    coreAlphaScale);

            float cycleTicks = sword.isVisuallyDocked() ? 38.0F : 13.0F;
            float flow = (sword.tickCount + partialTick) / cycleTicks
                    + sword.getVisualFormationSlot() * 0.137F;
            float progress = flow - Mth.floor(flow);
            float pulseCenter = Mth.lerp(0.06F + progress * 0.88F, base, tip);
            float pulseHalfLength = sword.isVisuallyDocked() ? 0.15F : 0.23F;
            float pulseStart = Math.max(base, pulseCenter - pulseHalfLength);
            float pulseEnd = Math.min(tip, pulseCenter + pulseHalfLength);
            float[] pulseY = {pulseStart, pulseCenter, pulseEnd};
            float[] pulseFace = {
                    radiusAt(y, faceRadius, pulseStart) + 0.012F,
                    radiusAt(y, faceRadius, pulseCenter) + 0.020F,
                    radiusAt(y, faceRadius, pulseEnd) + 0.012F
            };
            float[] pulseDepth = {
                    radiusAt(y, depthRadius, pulseStart) + 0.008F,
                    radiusAt(y, depthRadius, pulseCenter) + 0.014F,
                    radiusAt(y, depthRadius, pulseEnd) + 0.008F
            };
            int pulseAlphaScale = Mth.clamp((int) ((214.0F + launchSurge * 41.0F) * pulse), 0, 255);
            VertexConsumer flowingEnergy = buffers.getBuffer(RenderType.entityTranslucentEmissive(
                    SPIRIT_PULSE_TEXTURE, false));
            renderSpiritPrism(flowingEnergy, poseStack.last(), pulseY, pulseFace, pulseDepth,
                    new float[]{0.22F, 1.0F, 0.22F},
                    mixWithWhite(red, 0.72F), mixWithWhite(green, 0.72F), mixWithWhite(blue, 0.72F),
                    pulseAlphaScale);
        }

        private static void renderSpiritPrism(VertexConsumer vertices, PoseStack.Pose pose,
                                              float[] y, float[] faceRadius, float[] depthRadius,
                                              float[] ringAlpha, int red, int green, int blue, int alphaScale) {
            int ringCount = y.length;
            for (int ring = 0; ring < ringCount - 1; ring++) {
                float startV = ring / (float) (ringCount - 1);
                float endV = (ring + 1) / (float) (ringCount - 1);
                for (int side = 0; side < SPIRIT_SIDES; side++) {
                    double startAngle = Math.PI * 2.0D * side / SPIRIT_SIDES;
                    double endAngle = Math.PI * 2.0D * (side + 1) / SPIRIT_SIDES;
                    double normalAngle = (startAngle + endAngle) * 0.5D;
                    float normalX = (float) (Math.cos(normalAngle)
                            / Math.max(0.001F, (faceRadius[ring] + faceRadius[ring + 1]) * 0.5F));
                    float normalZ = (float) (Math.sin(normalAngle)
                            / Math.max(0.001F, (depthRadius[ring] + depthRadius[ring + 1]) * 0.5F));
                    float normalLength = Mth.sqrt(normalX * normalX + normalZ * normalZ);
                    normalX /= normalLength;
                    normalZ /= normalLength;

                    int startAlpha = Mth.clamp(Math.round(alphaScale * ringAlpha[ring]), 0, 255);
                    int endAlpha = Mth.clamp(Math.round(alphaScale * ringAlpha[ring + 1]), 0, 255);
                    float startX = (float) Math.cos(startAngle) * faceRadius[ring];
                    float startZ = (float) Math.sin(startAngle) * depthRadius[ring];
                    float startNextX = (float) Math.cos(endAngle) * faceRadius[ring];
                    float startNextZ = (float) Math.sin(endAngle) * depthRadius[ring];
                    float endX = (float) Math.cos(startAngle) * faceRadius[ring + 1];
                    float endZ = (float) Math.sin(startAngle) * depthRadius[ring + 1];
                    float endNextX = (float) Math.cos(endAngle) * faceRadius[ring + 1];
                    float endNextZ = (float) Math.sin(endAngle) * depthRadius[ring + 1];

                    spiritVertex(vertices, pose, startX, y[ring], startZ, red, green, blue, startAlpha,
                            0.0F, startV, normalX, normalZ);
                    spiritVertex(vertices, pose, endX, y[ring + 1], endZ, red, green, blue, endAlpha,
                            0.0F, endV, normalX, normalZ);
                    spiritVertex(vertices, pose, endNextX, y[ring + 1], endNextZ, red, green, blue, endAlpha,
                            1.0F, endV, normalX, normalZ);
                    spiritVertex(vertices, pose, startNextX, y[ring], startNextZ, red, green, blue, startAlpha,
                            1.0F, startV, normalX, normalZ);
                }
            }
        }

        private static void spiritVertex(VertexConsumer vertices, PoseStack.Pose pose,
                                         float x, float y, float z, int red, int green, int blue, int alpha,
                                         float u, float v, float normalX, float normalZ) {
            vertices.vertex(pose.pose(), x, y, z)
                    .color(red, green, blue, alpha)
                    .uv(u, v)
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(LightTexture.FULL_BRIGHT)
                    .normal(pose.normal(), normalX, 0.0F, normalZ)
                    .endVertex();
        }

        private static float radiusAt(float[] y, float[] radii, float point) {
            for (int index = 0; index < y.length - 1; index++) {
                if (point <= y[index + 1]) {
                    float length = y[index + 1] - y[index];
                    float progress = length <= 1.0E-5F ? 0.0F : (point - y[index]) / length;
                    return Mth.lerp(Mth.clamp(progress, 0.0F, 1.0F), radii[index], radii[index + 1]);
                }
            }
            return radii[radii.length - 1];
        }

        private static int mixWithWhite(int channel, float amount) {
            return Mth.clamp(Math.round(channel + (255 - channel) * amount), 0, 255);
        }

        private static void renderRibbonLayer(VertexConsumer vertices, Matrix4f pose, List<Vec3> path,
                                              Vec3 renderedPosition, Vec3 cameraPosition,
                                              int red, int green, int blue, double headWidth, int headAlpha) {
            int segmentCount = path.size() - 1;
            for (int index = 0; index < segmentCount; index++) {
                Vec3 worldStart = path.get(index);
                Vec3 worldEnd = path.get(index + 1);
                Vec3 segment = worldEnd.subtract(worldStart);
                if (segment.lengthSqr() < 1.0E-6D) continue;
                Vec3 midpoint = worldStart.add(worldEnd).scale(0.5D);
                Vec3 towardCamera = cameraPosition.subtract(midpoint);
                Vec3 side = segment.cross(towardCamera);
                if (side.lengthSqr() < 1.0E-6D) side = segment.cross(new Vec3(0.0D, 1.0D, 0.0D));
                if (side.lengthSqr() < 1.0E-6D) side = new Vec3(1.0D, 0.0D, 0.0D);
                side = side.normalize();

                double startFade = 1.0D - index / (double) segmentCount;
                double endFade = 1.0D - (index + 1) / (double) segmentCount;
                double startWidth = headWidth * (0.18D + startFade * 0.82D);
                double endWidth = headWidth * (0.18D + endFade * 0.82D);
                int startAlpha = (int) (headAlpha * startFade * startFade);
                int endAlpha = (int) (headAlpha * endFade * endFade);
                Vec3 localStart = worldStart.subtract(renderedPosition);
                Vec3 localEnd = worldEnd.subtract(renderedPosition);
                Vec3 startSide = side.scale(startWidth);
                Vec3 endSide = side.scale(endWidth);

                trailVertex(vertices, pose, localStart.add(startSide), red, green, blue, startAlpha);
                trailVertex(vertices, pose, localEnd.add(endSide), red, green, blue, endAlpha);
                trailVertex(vertices, pose, localEnd.subtract(endSide), red, green, blue, endAlpha);
                trailVertex(vertices, pose, localStart.subtract(startSide), red, green, blue, startAlpha);
            }
        }

        private static void trailVertex(VertexConsumer vertices, Matrix4f pose, Vec3 point,
                                        int red, int green, int blue, int alpha) {
            vertices.vertex(pose, (float) point.x, (float) point.y, (float) point.z)
                    .color(red, green, blue, alpha).endVertex();
        }

        @Override
        public ResourceLocation getTextureLocation(FlyingSwordEntity entity) {
            return new ResourceLocation("minecraft", "textures/item/"
                    + entity.getVisualMaterial().serializedName() + "_sword.png");
        }
    }
}
