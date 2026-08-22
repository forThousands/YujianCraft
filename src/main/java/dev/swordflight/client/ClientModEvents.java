package dev.swordflight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.platform.InputConstants;
import dev.swordflight.Swordflight;
import dev.swordflight.entity.FlyingSwordEntity;
import dev.swordflight.formation.FormationGeometry;
import dev.swordflight.registry.ModEntities;
import dev.swordflight.material.FlyingSwordMaterial;
import dev.swordflight.visual.FlyingSwordSeries;
import dev.swordflight.upgrade.FlyingSwordModule;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.model.pipeline.VertexConsumerWrapper;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = Swordflight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private static ShaderInstance whiteHotEnergyShader;

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
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(
                        event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(Swordflight.MOD_ID, "rendertype_white_hot_energy"),
                        DefaultVertexFormat.NEW_ENTITY),
                shader -> whiteHotEnergyShader = shader);
    }

    /**
     * Uses the same state layout as vanilla's translucent emissive entity layer, but replaces
     * its fragment shader. Item material colour is sampled from the atlas in the fragment stage,
     * so this is the first point in the pipeline where a real white-hot colour mix can occur.
     */
    private static final class SwordflightRenderTypes extends RenderType {
        private SwordflightRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode,
                                       int bufferSize, boolean affectsCrumbling, boolean sortOnUpload,
                                       Runnable setupState, Runnable clearState) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
        }

        private static final RenderType WHITE_HOT_ENERGY = create(
                "swordflight_white_hot_energy",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                256,
                true,
                true,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> whiteHotEnergyShader))
                        .setTextureState(new TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .setOverlayState(OVERLAY)
                        .createCompositeState(true));
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

            if (sword.isVisuallyDocked() || sword.isVisualRideSupport()) {
                Player owner = sword.getVisualOwner();
                if (owner != null) {
                    Vec3 ownerPosition = new Vec3(
                            Mth.lerp(partialTick, owner.xo, owner.getX()),
                            Mth.lerp(partialTick, owner.yo, owner.getY()),
                            Mth.lerp(partialTick, owner.zo, owner.getZ())
                    );
                    float ownerYaw = Mth.rotLerp(partialTick, owner.yRotO, owner.getYRot());
                    Vec3 desired;
                    if (sword.isVisualRideSupport()) {
                        Vec3 forward = Vec3.directionFromRotation(0.0F, ownerYaw).normalize();
                        desired = ownerPosition.add(forward.scale(0.10D)).add(0.0D, -0.28D, 0.0D);
                    } else {
                        desired = FormationGeometry.dockPosition(ownerPosition, ownerYaw,
                                sword.getVisualFormationSlot(), sword.getVisualFormationMode());
                    }
                    Vec3 correction = desired.subtract(renderedSwordPosition);
                    poseStack.translate(correction.x, correction.y, correction.z);
                }
            }

            float renderYaw = Mth.rotLerp(partialTick, sword.yRotO, sword.getYRot());
            float renderPitch = Mth.lerp(partialTick, sword.xRotO, sword.getXRot());
            if (sword.isVisualRideSupport() && sword.getVisualOwner() != null) {
                Player owner = sword.getVisualOwner();
                renderYaw = Mth.rotLerp(partialTick, owner.yRotO, owner.getYRot());
                renderPitch = 0.0F;
            }
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
            boolean customThreeDimensionalModel = sword.getVisualSeries() == FlyingSwordSeries.SPIRITFORGED;
            float axisCorrection = customThreeDimensionalModel
                    ? (legacyDockPose ? 90.0F : 0.0F)
                    : (legacyDockPose ? 45.0F : -45.0F);
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(axisCorrection));
            // Keep the sword, its aura and its moving energy pulse in the same scale space.
            // Previously the aura was rendered before this shared enlargement, causing most of
            // its already-thin shell to disappear inside the vanilla sword silhouette.
            poseStack.scale(1.25F, 1.25F, 1.25F);

            // Render the material-coloured sword first so the following translucent aura layers
            // can sit around it instead of depth-flattening it into an opaque plastic shell.
            poseStack.pushPose();
            if (!customThreeDimensionalModel) {
                // Elongate the vanilla sprite along its own post-FIXED diagonal instead of scaling
                // X or Y globally. Its familiar pixel width remains intact while the flying sword
                // gains a longer silhouette; inventory and ordinary held rendering are untouched.
                poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(135.0F));
                poseStack.scale(1.22F, 0.98F, 1.0F);
                poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-135.0F));
            }
            int swordLight = ClientOptions.swordBodyGlow() ? LightTexture.FULL_BRIGHT : packedLight;
            if (ClientOptions.swordBodyGlow()) {
                SwordGlowBrightness brightness = ClientOptions.glowBrightness();
                if (brightness.usesLegacyRenderer()) {
                    // This is deliberately the untouched 0.9.6 body path. DEFAULT must never
                    // travel through brightness arithmetic, even when that arithmetic is nominally 1.0.
                    renderLegacyEnergySword(sword, partialTick, poseStack, buffers);
                } else {
                    renderLayeredEnergySword(sword, partialTick, poseStack, buffers, packedLight, brightness);
                }
            } else {
                itemRenderer.renderStatic(sword.getDisplayItem(), ItemDisplayContext.FIXED, swordLight,
                        OverlayTexture.NO_OVERLAY, poseStack, buffers, sword.level(), sword.getId());
            }
            poseStack.popPose();

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
            renderModuleAccents(sword, partialTick, poseStack, buffers, customThreeDimensionalModel);
            poseStack.popPose();
            super.render(sword, yaw, partialTick, poseStack, buffers, packedLight);
        }

        private void renderLegacyEnergySword(FlyingSwordEntity sword, float partialTick, PoseStack poseStack,
                                             MultiBufferSource buffers) {
            ItemStack stack = sword.getDisplayItem();
            BakedModel model = itemRenderer.getModel(stack, sword.level(), null, sword.getId());
            poseStack.pushPose();
            model = ForgeHooksClient.handleCameraTransforms(
                    poseStack, model, ItemDisplayContext.FIXED, false);
            poseStack.translate(-0.5F, -0.5F, -0.5F);

            RenderType bodyRenderType = sword.hasVisualWhiteHotModule()
                    ? SwordflightRenderTypes.WHITE_HOT_ENERGY
                    : RenderType.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS, false);
            VertexConsumer energyBody = new EnergyVertexConsumer(
                    buffers.getBuffer(bodyRenderType), 188, 0.0F);
            for (BakedModel pass : model.getRenderPasses(stack, true)) {
                itemRenderer.renderModelLists(pass, stack, LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, poseStack, energyBody);
            }

            if (ClientOptions.swordEnergyHighlight()) {
                float time = sword.tickCount + partialTick;
                float slowPulse = 0.5F + 0.5F * Mth.sin(time * 0.115F
                        + sword.getVisualFormationSlot() * 0.83F);
                float hotPulse = 0.5F + 0.5F * Mth.sin(time * 0.197F
                        + sword.getVisualFormationSlot() * 1.17F + 1.4F);

                // A close white-hot skin keeps the original pixel pattern visible while making
                // its brightest regions resemble heated, light-emitting metal.
                poseStack.pushPose();
                poseStack.scale(1.012F, 1.012F, 1.018F);
                VertexConsumer hotSkin = new EnergyVertexConsumer(
                        buffers.getBuffer(RenderType.entityTranslucentEmissive(
                                TextureAtlas.LOCATION_BLOCKS, false)),
                        Math.round(214.0F + hotPulse * 41.0F), 0.92F + hotPulse * 0.08F);
                for (BakedModel pass : model.getRenderPasses(stack, true)) {
                    itemRenderer.renderModelLists(pass, stack, LightTexture.FULL_BRIGHT,
                            OverlayTexture.NO_OVERLAY, poseStack, hotSkin);
                }
                poseStack.popPose();

                // A slightly expanded, low-alpha material-coloured skin supplies the hot bloom
                // that Minecraft's renderer lacks without turning this into a hard outline.
                poseStack.pushPose();
                poseStack.scale(1.060F, 1.060F, 1.072F);
                VertexConsumer materialBloom = new EnergyVertexConsumer(
                        buffers.getBuffer(RenderType.entityTranslucentEmissive(
                                TextureAtlas.LOCATION_BLOCKS, false)),
                        Math.round(102.0F + slowPulse * 68.0F), 0.58F + slowPulse * 0.16F);
                for (BakedModel pass : model.getRenderPasses(stack, true)) {
                    itemRenderer.renderModelLists(pass, stack, LightTexture.FULL_BRIGHT,
                            OverlayTexture.NO_OVERLAY, poseStack, materialBloom);
                }
                poseStack.popPose();
            }
            poseStack.popPose();
        }

        /**
         * Non-default brightness path. The ordinary item pass is the opaque visual anchor and is
         * never brightness-scaled; only the separate emissive passes are adjustable.
         */
        private void renderLayeredEnergySword(FlyingSwordEntity sword, float partialTick, PoseStack poseStack,
                                              MultiBufferSource buffers, int packedLight,
                                              SwordGlowBrightness brightness) {
            ItemStack stack = sword.getDisplayItem();
            itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, packedLight,
                    OverlayTexture.NO_OVERLAY, poseStack, buffers, sword.level(), sword.getId());

            BakedModel model = itemRenderer.getModel(stack, sword.level(), null, sword.getId());
            poseStack.pushPose();
            model = ForgeHooksClient.handleCameraTransforms(
                    poseStack, model, ItemDisplayContext.FIXED, false);
            poseStack.translate(-0.5F, -0.5F, -0.5F);

            RenderType bodyRenderType = sword.hasVisualWhiteHotModule()
                    ? SwordflightRenderTypes.WHITE_HOT_ENERGY
                    : RenderType.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS, false);
            VertexConsumer energyOverlay = new EnergyVertexConsumer(
                    buffers.getBuffer(bodyRenderType), brightness.bodyOverlayAlpha(), brightness.whiteMix());
            for (BakedModel pass : model.getRenderPasses(stack, true)) {
                itemRenderer.renderModelLists(pass, stack, LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, poseStack, energyOverlay);
            }

            // The option owns these layers completely: OFF draws none; ON only adds bloom and
            // never changes either the opaque body or the main emissive overlay.
            if (ClientOptions.swordEnergyHighlight()) {
                float time = sword.tickCount + partialTick;
                float slowPulse = 0.5F + 0.5F * Mth.sin(time * 0.115F
                        + sword.getVisualFormationSlot() * 0.83F);
                float hotPulse = 0.5F + 0.5F * Mth.sin(time * 0.197F
                        + sword.getVisualFormationSlot() * 1.17F + 1.4F);

                poseStack.pushPose();
                poseStack.scale(1.012F, 1.012F, 1.018F);
                int hotAlpha = scaleEffectAlpha(Math.round(214.0F + hotPulse * 41.0F),
                        brightness.bloomStrength());
                VertexConsumer hotSkin = new EnergyVertexConsumer(
                        buffers.getBuffer(RenderType.entityTranslucentEmissive(
                                TextureAtlas.LOCATION_BLOCKS, false)), hotAlpha,
                        combineWhiteMix(0.92F + hotPulse * 0.08F, brightness.whiteMix()));
                for (BakedModel pass : model.getRenderPasses(stack, true)) {
                    itemRenderer.renderModelLists(pass, stack, LightTexture.FULL_BRIGHT,
                            OverlayTexture.NO_OVERLAY, poseStack, hotSkin);
                }
                poseStack.popPose();

                poseStack.pushPose();
                poseStack.scale(1.060F, 1.060F, 1.072F);
                int bloomAlpha = scaleEffectAlpha(Math.round(102.0F + slowPulse * 68.0F),
                        brightness.bloomStrength());
                VertexConsumer materialBloom = new EnergyVertexConsumer(
                        buffers.getBuffer(RenderType.entityTranslucentEmissive(
                                TextureAtlas.LOCATION_BLOCKS, false)), bloomAlpha,
                        combineWhiteMix(0.58F + slowPulse * 0.16F, brightness.whiteMix()));
                for (BakedModel pass : model.getRenderPasses(stack, true)) {
                    itemRenderer.renderModelLists(pass, stack, LightTexture.FULL_BRIGHT,
                            OverlayTexture.NO_OVERLAY, poseStack, materialBloom);
                }
                poseStack.popPose();
            }
            poseStack.popPose();
        }

        private void renderTrail(FlyingSwordEntity sword, Vec3 renderedPosition, PoseStack poseStack,
                                 MultiBufferSource buffers) {
            int poisonLevel = sword.getVisualModuleLevel(FlyingSwordModule.POISON);
            if (!ClientOptions.swordTrail()
                    && (poisonLevel == 0 || !ClientOptions.poisonModuleVisual())) return;
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
            SwordGlowBrightness brightness = ClientOptions.glowBrightness();
            if (!brightness.usesLegacyRenderer()) {
                red = mixWithWhite(red, brightness.whiteMix());
                green = mixWithWhite(green, brightness.whiteMix());
                blue = mixWithWhite(blue, brightness.whiteMix());
            }
            VertexConsumer vertices = buffers.getBuffer(RenderType.lightning());
            Matrix4f pose = poseStack.last().pose();
            Vec3 cameraPosition = entityRenderDispatcher.camera.getPosition();
            int outerAlpha = brightness.usesLegacyRenderer() ? 140
                    : scaleEffectAlpha(140, brightness.trailStrength());
            int middleAlpha = brightness.usesLegacyRenderer() ? 235
                    : scaleEffectAlpha(235, brightness.trailStrength());
            int coreAlpha = brightness.usesLegacyRenderer() ? 255
                    : scaleEffectAlpha(255, brightness.trailStrength());
            if (ClientOptions.swordTrail()) {
                renderRibbonLayer(vertices, pose, path, renderedPosition, cameraPosition,
                        red, green, blue, 0.24D, outerAlpha);
                renderRibbonLayer(vertices, pose, path, renderedPosition, cameraPosition,
                        mixWithWhite(red, 0.48F), mixWithWhite(green, 0.48F), mixWithWhite(blue, 0.48F),
                        0.092D, middleAlpha);
                renderRibbonLayer(vertices, pose, path, renderedPosition, cameraPosition,
                        mixWithWhite(red, 0.88F), mixWithWhite(green, 0.88F), mixWithWhite(blue, 0.88F),
                        0.026D, coreAlpha);
            }
            if (poisonLevel > 0 && ClientOptions.poisonModuleVisual()) {
                renderPoisonTrail(vertices, pose, path, renderedPosition, cameraPosition,
                        sword.tickCount, poisonLevel, brightness);
            }
        }

        private static void renderPoisonTrail(VertexConsumer vertices, Matrix4f pose, List<Vec3> path,
                                              Vec3 renderedPosition, Vec3 cameraPosition, int tick,
                                              int level, SwordGlowBrightness brightness) {
            int strandCount = level >= 3 ? 2 : 1;
            for (int strand = 0; strand < strandCount; strand++) {
                List<Vec3> mist = new ArrayList<>(path.size());
                for (int index = 0; index < path.size(); index++) {
                    float wave = tick * 0.11F + index * 0.72F + strand * 2.3F;
                    double amplitude = 0.035D + index * 0.006D;
                    mist.add(path.get(index).add(Mth.sin(wave) * amplitude,
                            Mth.cos(wave * 0.73F) * amplitude * 0.65D,
                            Mth.cos(wave) * amplitude));
                }
                int alpha = 70 + level * 13;
                if (!brightness.usesLegacyRenderer()) {
                    alpha = scaleEffectAlpha(alpha, brightness.trailStrength());
                }
                renderRibbonLayer(vertices, pose, mist, renderedPosition, cameraPosition,
                        102, 238, 116, 0.072D + level * 0.008D, alpha);
            }
        }

        private void renderModuleAccents(FlyingSwordEntity sword, float partialTick,
                                         PoseStack poseStack, MultiBufferSource buffers,
                                         boolean formalModel) {
            int flame = sword.getVisualModuleLevel(FlyingSwordModule.FLAME);
            int lightning = sword.getVisualModuleLevel(FlyingSwordModule.LIGHTNING);
            int poison = sword.getVisualModuleLevel(FlyingSwordModule.POISON);
            int explosion = sword.getVisualModuleLevel(FlyingSwordModule.EXPLOSION);
            int arrowRain = sword.getVisualModuleLevel(FlyingSwordModule.ARROW_RAIN);
            if (flame + lightning + poison + explosion + arrowRain == 0) return;

            Vec3 camera = entityRenderDispatcher.camera.getPosition();
            double distanceSquared = sword.isVisualPreview() ? 0.0D : sword.position().distanceToSqr(camera);
            if (distanceSquared > 48.0D * 48.0D) return;
            boolean reduced = distanceSquared > 24.0D * 24.0D;
            float time = sword.tickCount + partialTick;
            float base = formalModel ? -0.25F : -0.18F;
            float tip = formalModel ? 1.33F : 1.20F;
            Matrix4f pose = poseStack.last().pose();
            VertexConsumer vertices = buffers.getBuffer(RenderType.lightning());
            SwordGlowBrightness brightness = ClientOptions.glowBrightness();
            float strength = brightness.usesLegacyRenderer() ? 1.0F : brightness.auraStrength();

            if (flame > 0 && ClientOptions.flameModuleVisual()) {
                renderFlameSparks(vertices, pose, sword, time, base, tip, flame, reduced, strength);
            }
            if (lightning > 0 && ClientOptions.lightningModuleVisual()) {
                renderLightningArc(vertices, pose, sword, time, base, tip, lightning, reduced, strength);
            }
            if (poison > 0 && ClientOptions.poisonModuleVisual()) {
                renderPoisonWisps(vertices, pose, sword, time, base, tip, poison, reduced, strength);
            }
            if (explosion > 0 && ClientOptions.explosionModuleVisual()) {
                renderExplosionPulse(vertices, pose, sword, time, base, tip, explosion, strength);
            }
            if (arrowRain > 0 && ClientOptions.arrowRainModuleVisual()) {
                renderArrowWind(vertices, pose, sword, time, base, tip, arrowRain, reduced, strength);
            }
        }

        private static void renderFlameSparks(VertexConsumer vertices, Matrix4f pose,
                                              FlyingSwordEntity sword, float time, float base, float tip,
                                              int level, boolean reduced, float strength) {
            int count = reduced ? 1 : 1 + level;
            for (int index = 0; index < count; index++) {
                float phase = fractional(time / (16.0F - level) + index * 0.31F
                        + sword.getId() * 0.071F);
                float visibility = Mth.sin((float) Math.PI * phase);
                if (visibility < 0.12F) continue;
                float anchorY = Mth.lerp(fractional(index * 0.37F + time * 0.009F), base + 0.22F, tip - 0.08F);
                float side = (index & 1) == 0 ? -1.0F : 1.0F;
                Vec3 start = new Vec3(side * 0.13F, anchorY, Mth.sin(time * 0.07F + index) * 0.045F);
                Vec3 middle = start.add(side * (0.035F + phase * 0.045F),
                        -0.035F - phase * 0.07F, Mth.cos(index + time * 0.11F) * 0.035F);
                Vec3 end = middle.add(side * 0.035F, -0.07F - phase * 0.10F,
                        Mth.sin(index * 1.7F + time * 0.09F) * 0.04F);
                int alpha = Mth.clamp(Math.round(220.0F * visibility * strength), 0, 255);
                renderWispSegment(vertices, pose, start, middle, 0.012F,
                        255, 176, 54, alpha, Math.round(alpha * 0.75F));
                renderWispSegment(vertices, pose, middle, end, 0.007F,
                        255, 92, 24, Math.round(alpha * 0.75F), 0);
            }
        }

        private static void renderLightningArc(VertexConsumer vertices, Matrix4f pose,
                                                FlyingSwordEntity sword, float time, float base, float tip,
                                                int level, boolean reduced, float strength) {
            int period = 54 - level * 7;
            int cycle = Math.floorMod((int) time + sword.getId() * 11, period);
            if (cycle > (reduced ? 3 : 5 + level)) return;
            int segments = reduced ? 3 : 4 + level;
            Vec3 previous = new Vec3(-0.12F, base + 0.25F, 0.02F);
            int alpha = Mth.clamp(Math.round((205.0F - cycle * 20.0F) * strength), 0, 255);
            for (int index = 1; index <= segments; index++) {
                float progress = index / (float) segments;
                float jitterX = Mth.sin(sword.getId() * 0.73F + index * 4.17F + cycle * 1.9F) * 0.055F;
                float jitterZ = Mth.cos(sword.getId() * 0.51F + index * 3.31F + cycle * 1.3F) * 0.045F;
                Vec3 next = new Vec3((index == segments ? 0.12F : jitterX),
                        Mth.lerp(progress, base + 0.25F, tip - 0.04F), jitterZ);
                renderWispSegment(vertices, pose, previous, next, 0.010F,
                        132, 226, 255, alpha, alpha);
                renderWispSegment(vertices, pose, previous, next, 0.003F,
                        245, 253, 255, 255, 255);
                previous = next;
            }
        }

        private static void renderPoisonWisps(VertexConsumer vertices, Matrix4f pose,
                                              FlyingSwordEntity sword, float time, float base, float tip,
                                              int level, boolean reduced, float strength) {
            int count = reduced ? 1 : Math.min(3, 1 + level);
            for (int index = 0; index < count; index++) {
                float phase = fractional(time / 27.0F + index * 0.39F + sword.getId() * 0.037F);
                float anchorY = Mth.lerp(fractional(index * 0.47F + time * 0.004F), base + 0.15F, tip - 0.12F);
                float angle = index * 2.4F + time * 0.025F;
                Vec3 start = new Vec3(Mth.cos(angle) * 0.11F, anchorY, Mth.sin(angle) * 0.055F);
                Vec3 middle = new Vec3(Mth.cos(angle + 0.55F) * (0.14F + phase * 0.05F),
                        anchorY - 0.08F * phase, Mth.sin(angle + 0.55F) * (0.08F + phase * 0.04F));
                Vec3 end = new Vec3(Mth.cos(angle + 1.0F) * (0.18F + phase * 0.08F),
                        anchorY - 0.18F * phase, Mth.sin(angle + 1.0F) * (0.11F + phase * 0.05F));
                int alpha = Mth.clamp(Math.round(105.0F * Mth.sin((float) Math.PI * phase) * strength), 0, 255);
                renderWispSegment(vertices, pose, start, middle, 0.020F,
                        106, 229, 119, alpha, Math.round(alpha * 0.65F));
                renderWispSegment(vertices, pose, middle, end, 0.014F,
                        76, 174, 92, Math.round(alpha * 0.65F), 0);
            }
        }

        private static void renderExplosionPulse(VertexConsumer vertices, Matrix4f pose,
                                                 FlyingSwordEntity sword, float time, float base, float tip,
                                                 int level, float strength) {
            float phase = fractional(time / (38.0F - level * 4.0F) + sword.getId() * 0.023F);
            float visibility = Mth.sin((float) Math.PI * phase);
            float y = Mth.lerp(phase, base + 0.10F, tip - 0.04F);
            float faceRadius = 0.14F + visibility * (0.035F + level * 0.008F);
            float depthRadius = faceRadius * 0.58F;
            int alpha = Mth.clamp(Math.round(190.0F * visibility * strength), 0, 255);
            int sides = 8;
            for (int side = 0; side < sides; side++) {
                float a = (float) (Math.PI * 2.0D * side / sides);
                float b = (float) (Math.PI * 2.0D * (side + 1) / sides);
                Vec3 start = new Vec3(Mth.cos(a) * faceRadius, y, Mth.sin(a) * depthRadius);
                Vec3 end = new Vec3(Mth.cos(b) * faceRadius, y, Mth.sin(b) * depthRadius);
                renderWispSegment(vertices, pose, start, end, 0.009F,
                        255, 103, 35, alpha, alpha);
            }
        }

        private static void renderArrowWind(VertexConsumer vertices, Matrix4f pose,
                                            FlyingSwordEntity sword, float time, float base, float tip,
                                            int level, boolean reduced, float strength) {
            int count = reduced ? 1 : 1 + level;
            for (int index = 0; index < count; index++) {
                float phase = fractional(time / 18.0F + index * 0.29F + sword.getId() * 0.019F);
                float angle = index * 2.399F + time * 0.012F;
                float radius = 0.17F + index * 0.018F;
                float centerY = Mth.lerp(phase, base - 0.20F, tip - 0.15F);
                Vec3 start = new Vec3(Mth.cos(angle) * radius, centerY - 0.18F,
                        Mth.sin(angle) * radius * 0.52F);
                Vec3 middle = new Vec3(Mth.cos(angle + 0.14F) * (radius + 0.025F), centerY,
                        Mth.sin(angle + 0.14F) * (radius + 0.025F) * 0.52F);
                Vec3 end = new Vec3(Mth.cos(angle + 0.25F) * radius, centerY + 0.20F,
                        Mth.sin(angle + 0.25F) * radius * 0.52F);
                int alpha = Mth.clamp(Math.round(135.0F * Mth.sin((float) Math.PI * phase) * strength), 0, 255);
                renderWispSegment(vertices, pose, start, middle, 0.008F,
                        205, 242, 255, 0, alpha);
                renderWispSegment(vertices, pose, middle, end, 0.006F,
                        229, 249, 255, alpha, 0);
            }
        }

        private static void renderBladeAura(FlyingSwordEntity sword, float partialTick, PoseStack poseStack,
                                            MultiBufferSource buffers, boolean formalModel) {
            int color = sword.getVisualMaterial().glowColor();
            int red = color >> 16 & 0xFF;
            int green = color >> 8 & 0xFF;
            int blue = color & 0xFF;
            SwordGlowBrightness brightness = ClientOptions.glowBrightness();
            if (!brightness.usesLegacyRenderer()) {
                red = mixWithWhite(red, brightness.whiteMix());
                green = mixWithWhite(green, brightness.whiteMix());
                blue = mixWithWhite(blue, brightness.whiteMix());
            }
            float pulse = 0.88F + 0.12F * Mth.sin((sword.tickCount + partialTick) * 0.18F
                    + sword.getVisualFormationSlot() * 0.9F);
            int flightAge = ClientFlightEffects.flightAge(sword);
            float launchSurge = flightAge > 0 && flightAge <= 7 ? (8.0F - flightAge) / 7.0F : 0.0F;

            float expansion = 1.0F + launchSurge * 0.08F;
            float base = formalModel ? -0.25F : -0.18F;
            float lowerBlade = formalModel ? 0.24F : 0.18F;
            float middle = formalModel ? 0.72F : 0.60F;
            float shoulder = formalModel ? 1.08F : 0.92F;
            float tip = formalModel ? 1.33F : 1.20F;
            float[] y = {base, lowerBlade, middle, shoulder, tip};
            float[] faceRadius = formalModel
                    ? new float[]{0.125F, 0.140F, 0.138F, 0.118F, 0.004F}
                    : new float[]{0.112F, 0.132F, 0.128F, 0.108F, 0.004F};
            float[] depthRadius = formalModel
                    ? new float[]{0.060F, 0.066F, 0.064F, 0.055F, 0.003F}
                    : new float[]{0.054F, 0.063F, 0.061F, 0.052F, 0.003F};
            for (int index = 0; index < faceRadius.length; index++) {
                faceRadius[index] *= expansion;
                depthRadius[index] *= expansion;
            }

            float[] outerFaceRadius = faceRadius.clone();
            float[] outerDepthRadius = depthRadius.clone();
            for (int index = 0; index < outerFaceRadius.length - 1; index++) {
                outerFaceRadius[index] *= 1.34F;
                outerDepthRadius[index] *= 1.45F;
            }

            VertexConsumer shell = buffers.getBuffer(RenderType.entityTranslucentEmissive(
                    SPIRIT_SHELL_TEXTURE, false));

            // Submit the nested layers from inside to outside. Brightness now concentrates in the
            // spine and moving pulse while the complete shells remain deliberately restrained.
            float[] coreFaceRadius = {0.013F, 0.017F, 0.018F, 0.015F, 0.002F};
            float[] coreDepthRadius = {0.011F, 0.014F, 0.015F, 0.013F, 0.002F};
            int coreAlphaScale = Mth.clamp((int) ((238.0F + launchSurge * 30.0F) * pulse), 0, 255);
            if (!brightness.usesLegacyRenderer()) {
                coreAlphaScale = scaleEffectAlpha(coreAlphaScale, brightness.auraStrength());
            }
            renderSpiritPrism(shell, poseStack.last(), y, coreFaceRadius, coreDepthRadius,
                    new float[]{0.62F, 0.88F, 1.0F, 0.92F, 0.14F},
                    mixWithWhite(red, 0.90F), mixWithWhite(green, 0.90F), mixWithWhite(blue, 0.90F),
                    coreAlphaScale);

            float[] shellAlpha = {0.54F, 0.78F, 1.0F, 0.82F, 0.08F};
            int shellAlphaScale = Mth.clamp((int) ((122.0F + launchSurge * 54.0F) * pulse), 0, 255);
            if (!brightness.usesLegacyRenderer()) {
                shellAlphaScale = scaleEffectAlpha(shellAlphaScale, brightness.auraStrength());
            }
            renderSpiritPrism(shell, poseStack.last(), y, faceRadius, depthRadius, shellAlpha,
                    mixWithWhite(red, 0.12F), mixWithWhite(green, 0.12F), mixWithWhite(blue, 0.12F),
                    shellAlphaScale);

            int outerAlphaScale = Mth.clamp((int) ((54.0F + launchSurge * 34.0F) * pulse), 0, 255);
            if (!brightness.usesLegacyRenderer()) {
                outerAlphaScale = scaleEffectAlpha(outerAlphaScale, brightness.auraStrength());
            }
            renderSpiritPrism(shell, poseStack.last(), y, outerFaceRadius, outerDepthRadius,
                    new float[]{0.30F, 0.58F, 1.0F, 0.70F, 0.04F},
                    red, green, blue, outerAlphaScale);

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
            int pulseAlphaScale = Mth.clamp((int) ((244.0F + launchSurge * 24.0F) * pulse), 0, 255);
            if (!brightness.usesLegacyRenderer()) {
                pulseAlphaScale = scaleEffectAlpha(pulseAlphaScale, brightness.auraStrength());
            }
            VertexConsumer flowingEnergy = buffers.getBuffer(RenderType.entityTranslucentEmissive(
                    SPIRIT_PULSE_TEXTURE, false));
            renderSpiritPrism(flowingEnergy, poseStack.last(), pulseY, pulseFace, pulseDepth,
                    new float[]{0.22F, 1.0F, 0.22F},
                    mixWithWhite(red, 0.72F), mixWithWhite(green, 0.72F), mixWithWhite(blue, 0.72F),
                    pulseAlphaScale);

            renderSpiritWisps(sword, partialTick, poseStack.last().pose(), buffers,
                    y, faceRadius, depthRadius, red, green, blue, brightness);
        }

        private static void renderSpiritWisps(FlyingSwordEntity sword, float partialTick, Matrix4f pose,
                                              MultiBufferSource buffers, float[] y,
                                              float[] faceRadius, float[] depthRadius,
                                              int red, int green, int blue,
                                              SwordGlowBrightness brightness) {
            VertexConsumer wisps = buffers.getBuffer(RenderType.lightning());
            float time = sword.tickCount + partialTick;
            boolean docked = sword.isVisuallyDocked();
            int wispCount = docked ? 3 : 5;
            float lifetime = docked ? 29.0F : 17.0F;

            for (int index = 0; index < wispCount; index++) {
                float phase = fractional(time / lifetime + index * 0.271F
                        + sword.getVisualFormationSlot() * 0.113F);
                float visibility = Mth.sin((float) Math.PI * phase);
                if (visibility < 0.04F) continue;

                float bladeProgress = fractional(index * 0.381966F + time * 0.0055F
                        + sword.getVisualFormationSlot() * 0.071F);
                float anchorY = Mth.lerp(bladeProgress, y[0] + 0.18F, y[y.length - 2]);
                float angle = index * 2.399963F + time * (docked ? 0.018F : 0.032F)
                        + sword.getVisualFormationSlot() * 0.47F;
                float cos = Mth.cos(angle);
                float sin = Mth.sin(angle);
                float startFace = radiusAt(y, faceRadius, anchorY) * 0.94F;
                float startDepth = radiusAt(y, depthRadius, anchorY) * 0.94F;
                float outward = (docked ? 0.15F : 0.24F) * phase;
                float backward = (docked ? 0.13F : 0.28F) * phase;
                float curve = Mth.sin(phase * (float) Math.PI) * 0.055F;

                Vec3 start = new Vec3(cos * startFace, anchorY, sin * startDepth);
                Vec3 middlePoint = new Vec3(
                        cos * (startFace + outward * 0.52F) - sin * curve,
                        anchorY - backward * 0.42F,
                        sin * (startDepth + outward * 0.52F) + cos * curve);
                Vec3 end = new Vec3(
                        cos * (startFace + outward) - sin * curve * 1.35F,
                        anchorY - backward,
                        sin * (startDepth + outward) + cos * curve * 1.35F);

                int outerAlpha = Mth.clamp(Math.round(104.0F * visibility), 0, 255);
                int coreAlpha = Mth.clamp(Math.round(220.0F * visibility), 0, 255);
                if (!brightness.usesLegacyRenderer()) {
                    outerAlpha = scaleEffectAlpha(outerAlpha, brightness.auraStrength());
                    coreAlpha = scaleEffectAlpha(coreAlpha, brightness.auraStrength());
                }
                renderWispSegment(wisps, pose, start, middlePoint, 0.018F,
                        red, green, blue, outerAlpha, Math.round(outerAlpha * 0.82F));
                renderWispSegment(wisps, pose, middlePoint, end, 0.013F,
                        red, green, blue, Math.round(outerAlpha * 0.82F), 0);
                renderWispSegment(wisps, pose, start, middlePoint, 0.005F,
                        mixWithWhite(red, 0.84F), mixWithWhite(green, 0.84F), mixWithWhite(blue, 0.84F),
                        coreAlpha, Math.round(coreAlpha * 0.72F));
                renderWispSegment(wisps, pose, middlePoint, end, 0.003F,
                        mixWithWhite(red, 0.84F), mixWithWhite(green, 0.84F), mixWithWhite(blue, 0.84F),
                        Math.round(coreAlpha * 0.72F), 0);
            }
        }

        private static void renderWispSegment(VertexConsumer vertices, Matrix4f pose,
                                              Vec3 start, Vec3 end, float width,
                                              int red, int green, int blue,
                                              int startAlpha, int endAlpha) {
            Vec3 direction = end.subtract(start);
            if (direction.lengthSqr() < 1.0E-7D) return;
            direction = direction.normalize();
            Vec3 sideA = direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
            if (sideA.lengthSqr() < 1.0E-7D) sideA = direction.cross(new Vec3(1.0D, 0.0D, 0.0D));
            sideA = sideA.normalize().scale(width);
            Vec3 sideB = direction.cross(sideA).normalize().scale(width);
            renderWispQuad(vertices, pose, start, end, sideA, red, green, blue, startAlpha, endAlpha);
            renderWispQuad(vertices, pose, start, end, sideB, red, green, blue, startAlpha, endAlpha);
        }

        private static void renderWispQuad(VertexConsumer vertices, Matrix4f pose,
                                           Vec3 start, Vec3 end, Vec3 side,
                                           int red, int green, int blue,
                                           int startAlpha, int endAlpha) {
            wispVertex(vertices, pose, start.add(side), red, green, blue, startAlpha);
            wispVertex(vertices, pose, end.add(side), red, green, blue, endAlpha);
            wispVertex(vertices, pose, end.subtract(side), red, green, blue, endAlpha);
            wispVertex(vertices, pose, start.subtract(side), red, green, blue, startAlpha);
        }

        private static void wispVertex(VertexConsumer vertices, Matrix4f pose, Vec3 point,
                                       int red, int green, int blue, int alpha) {
            vertices.vertex(pose, (float) point.x, (float) point.y, (float) point.z)
                    .color(red, green, blue, alpha).endVertex();
        }

        private static float fractional(float value) {
            return value - Mth.floor(value);
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

        private static int scaleEffectAlpha(int alpha, float strength) {
            return Mth.clamp(Math.round(alpha * strength), 0, 255);
        }

        private static float combineWhiteMix(float localMix, float profileMix) {
            localMix = Mth.clamp(localMix, 0.0F, 1.0F);
            profileMix = Mth.clamp(profileMix, 0.0F, 1.0F);
            return 1.0F - (1.0F - localMix) * (1.0F - profileMix);
        }

        private static final class EnergyVertexConsumer extends VertexConsumerWrapper {
            private final int alpha;
            private final float whiteMix;

            private EnergyVertexConsumer(VertexConsumer parent, int alpha, float whiteMix) {
                super(parent);
                this.alpha = Mth.clamp(alpha, 0, 255);
                this.whiteMix = Mth.clamp(whiteMix, 0.0F, 1.0F);
            }

            @Override
            public VertexConsumer color(int red, int green, int blue, int sourceAlpha) {
                parent.color(mixWithWhite(red, whiteMix), mixWithWhite(green, whiteMix),
                        mixWithWhite(blue, whiteMix),
                        Mth.clamp(Math.round(sourceAlpha * (alpha / 255.0F)), 0, 255));
                return this;
            }

            @Override
            public void defaultColor(int red, int green, int blue, int sourceAlpha) {
                parent.defaultColor(mixWithWhite(red, whiteMix), mixWithWhite(green, whiteMix),
                        mixWithWhite(blue, whiteMix),
                        Mth.clamp(Math.round(sourceAlpha * (alpha / 255.0F)), 0, 255));
            }
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
