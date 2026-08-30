package dev.yujiancraft.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.entity.FlyingSwordEntity;
import dev.yujiancraft.entity.SwordArrayFieldEntity;
import dev.yujiancraft.material.FlyingSwordMaterial;
import dev.yujiancraft.wanxiang.WanxiangSwordData;
import dev.yujiancraft.wanxiang.WanxiangRenderPreset;
import dev.yujiancraft.visual.SwordArrayVisualStyle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import dev.yujiancraft.registry.ModEntities;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Procedural emissive geometry for the colossal seal and its heaven-to-ground finisher. */
public final class SwordArrayFieldRenderer extends EntityRenderer<SwordArrayFieldEntity> {
    /** Additive, depth-tested and colour-only: nested energy shells remain visible without z fighting. */
    private static final RenderType SPIRIT_LIGHT = SpiritRenderStates.create();
    /** The high celestial seal must remain readable above vanilla clouds. It deliberately ignores
     * world depth, while the beam and ground impact keep ordinary depth testing. */
    private static final RenderType CELESTIAL_LIGHT = SpiritRenderStates.createCelestial();
    private static final ResourceLocation SPIRIT_MIST = ResourceLocation.fromNamespaceAndPath(
            YujianCraft.MOD_ID, "textures/effect/sword_array/spirit_mist.png");
    private static final int CURTAIN_ATLAS_SIZE = 2048;
    private static final int CURTAIN_TILE_WIDTH = 682;
    private static final String[] TEXTURE_LAYERS = {"outer", "middle", "inner"};
    private FlyingSwordEntity visualSword;

    public SwordArrayFieldRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(SwordArrayFieldEntity field, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        float age = field.renderAge(partialTick);
        float finisherAge = age - field.finisherStartTick();
        float[] material = color(WanxiangSwordData.material(field.getDisplayStack()));
        SwordArrayVisualStyle style = field.visualStyle();
        float ringRadius = arrayRadius(field, age);

        renderTexturedCelestialSeal(field, style, material, age, ringRadius,
                poseStack, buffers);

        renderArraySwords(field, style, ringRadius, age, partialTick, poseStack, buffers);

        if (finisherAge >= 0.0F) {
            poseStack.pushPose();
            // Entity/item renderers above may have switched the shared BufferSource to another
            // vertex format. Always reacquire this consumer immediately before the colour-only
            // pass instead of retaining a stale BufferBuilder reference.
            VertexConsumer vertices = buffers.getBuffer(SPIRIT_LIGHT);
            renderWhiteFinisher(vertices, poseStack.last().pose(), field, style, ringRadius,
                    beamRadius(field, finisherAge) * style.beamWidth(), finisherAge,
                    beamFade(field, finisherAge), age);
            poseStack.popPose();
            renderDescendingSword(field, style, finisherAge, partialTick, poseStack, buffers);
        }
        super.render(field, yaw, partialTick, poseStack, buffers, packedLight);
    }

    /**
     * Three co-axial texture entities replace the old duplicated faces, support walls and white
     * procedural bars. All passes use the same entity transform, so target tracking remains owned
     * by the dispatcher and the existing post-effect signal channel stays camera-correct.
     */
    private static void renderTexturedCelestialSeal(SwordArrayFieldEntity field,
                                                     SwordArrayVisualStyle style,
                                                     float[] material, float age, float ringRadius,
                                                     PoseStack poseStack, MultiBufferSource buffers) {
        String variant = field.visualVariant() == 1 ? "gold" : "tricolor";
        float planeRadius = ringRadius * 1.12F;
        float pulse = 0.94F + 0.06F * Mth.sin(age * 0.12F);
        float brightness = Mth.clamp(style.brightness() * 1.22F, 0.35F, 1.0F);
        float brightnessProfile = ClientOptions.swordArrayBrightness().multiplier();
        int baseColour = Math.round(255.0F * Math.min(1.0F, brightnessProfile));
        float[] rotations = {
                age * 0.70F,
                -age * 1.15F + 17.0F,
                age * 1.85F - 11.0F
        };
        float[] heights = {-0.16F, 0.0F, 0.18F};

        for (int index = 0; index < TEXTURE_LAYERS.length; index++) {
            String layer = TEXTURE_LAYERS[index];
            poseStack.pushPose();
            poseStack.translate(0.0D, field.beamHeight() + heights[index], 0.0D);
            poseStack.mulPose(Axis.YP.rotationDegrees(rotations[index]));
            int baseAlpha = Math.round(238.0F * pulse * brightness);
            drawTexturedPlane(buffers.getBuffer(SpiritRenderStates.textured(
                            arrayTexture(variant, layer, "base"), false)),
                    poseStack.last(), planeRadius, baseColour, baseColour, baseColour, baseAlpha);

            if (brightnessProfile > 1.0F) {
                int emissiveAlpha = Math.round(112.0F * (brightnessProfile - 1.0F) / 0.48F);
                drawTexturedPlane(buffers.getBuffer(SpiritRenderStates.textured(
                                arrayTexture(variant, layer, "base"), true)),
                        poseStack.last(), planeRadius * 1.002F,
                        255, 255, 255, emissiveAlpha);
            }

            if (ClientOptions.swordArrayTextureGlow()) {
                int glowAlpha = Math.round((index == 0 ? 142.0F : 126.0F)
                        * pulse * style.haloStrength() * brightnessProfile);
                drawTexturedPlane(buffers.getBuffer(SpiritRenderStates.textured(
                                arrayTexture(variant, layer, "glow"), true)),
                        poseStack.last(), planeRadius * 1.035F,
                        255, 255, 255, glowAlpha);
            }
            poseStack.popPose();
        }

        if (ClientOptions.swordArraySpiritWisps()) {
            float[] wispColour = sealColour(material, style.outerTint(), style.brightness(), 0.86F);
            poseStack.pushPose();
            poseStack.translate(0.0D, field.beamHeight() + 0.13D, 0.0D);
            // Textured seal layers use NEW_ENTITY while fragments use POSITION_COLOR. The vanilla
            // BufferSource shares one builder between them, so the correct render type must be
            // requested again after all textured draws (Embeddium may hide this mistake).
            renderSpiritFragments(buffers.getBuffer(CELESTIAL_LIGHT), poseStack.last().pose(), ringRadius,
                    age, wispColour, Math.max(0.48F, style.fragmentStrength()));
            poseStack.popPose();
        }
        if (ClientOptions.swordArrayVolumeMist()) {
            renderVolumeMist(field, style, age, ringRadius, poseStack, buffers);
        }
    }

    private static ResourceLocation arrayTexture(String variant, String layer, String pass) {
        return ResourceLocation.fromNamespaceAndPath(YujianCraft.MOD_ID,
                "textures/effect/sword_array/" + variant + "_" + layer + "_" + pass + ".png");
    }

    /** Low-density textured slices give the otherwise planar seal a restrained volume. */
    private static void renderVolumeMist(SwordArrayFieldEntity field, SwordArrayVisualStyle style,
                                         float age, float radius, PoseStack poseStack,
                                         MultiBufferSource buffers) {
        VertexConsumer mist = buffers.getBuffer(SpiritRenderStates.textured(SPIRIT_MIST, false));
        SwordArrayVisualStyle.Colour tint = style.outerTint();
        for (int index = 0; index < 10; index++) {
            float seed = index * 2.399963F;
            float orbit = radius * (0.30F + 0.061F * (index % 6));
            float angle = seed + age * (index % 2 == 0 ? 0.0038F : -0.0029F);
            float x = Mth.cos(angle) * orbit;
            float z = Mth.sin(angle) * orbit;
            float vertical = ((index % 5) - 2) * 0.34F
                    + Mth.sin(age * 0.022F + seed) * 0.26F;
            float size = radius * (0.18F + (index % 3) * 0.035F);
            int alpha = Math.round(24.0F + 12.0F * (Mth.sin(age * 0.037F + seed) * 0.5F + 0.5F));
            poseStack.pushPose();
            poseStack.translate(x, field.beamHeight() + vertical, z);
            poseStack.mulPose(Axis.YP.rotationDegrees(index * 37.0F + age * 0.08F));
            drawTexturedPlane(mist, poseStack.last(), size,
                    Math.round(tint.red() * 255.0F), Math.round(tint.green() * 255.0F),
                    Math.round(tint.blue() * 255.0F), alpha);
            poseStack.popPose();
        }
    }

    /**
     * A deterministic spatial field rather than accessories attached to the fast-spinning seal.
     * Broad hero curtains and cropped secondary curtains share two atlases and no extra entities.
     */
    private void renderSpiritCurtainField(SwordArrayFieldEntity field, float age, float finisherAge,
                                          PoseStack poseStack, MultiBufferSource buffers) {
        SpiritCurtainDensity density = ClientOptions.spiritCurtainDensity();
        int count = density.curtainCount();
        int segments = density.segments();
        String variant = field.visualVariant() == 1 ? "gold" : "tricolor";
        ResourceLocation baseTexture = curtainTexture(variant, "base");
        ResourceLocation glyphTexture = curtainTexture(variant, "glyph");
        List<CurtainDraw> draws = new ArrayList<>(count);
        Vec3 camera = entityRenderDispatcher.camera.getPosition();
        Vec3 fieldOrigin = field.position();
        float spatialRadius = curtainFieldRadius(field, finisherAge);
        float brightness = ClientOptions.swordArrayBrightness().multiplier()
                * (field.visualVariant() == 0 ? 0.88F : 1.0F);
        float impactAge = finisherAge - field.chargeTicks() - field.holdTicks() - field.expandTicks();
        float impactPulse = impactAge >= 0.0F && impactAge < 12.0F
                ? (1.0F - impactAge / 12.0F) : 0.0F;
        float lifetimeFade = 1.0F - smoothStep(field.totalLifetimeTicks() - 22.0F,
                field.totalLifetimeTicks(), age);

        UUID fieldId = field.getUUID();
        for (int index = 0; index < count; index++) {
            float seed0 = curtainSeed(fieldId, index, 0);
            float seed1 = curtainSeed(fieldId, index, 1);
            float seed2 = curtainSeed(fieldId, index, 2);
            float seed3 = curtainSeed(fieldId, index, 3);
            boolean hero = index < Math.min(3, count);
            int tile = hero ? 0 : 1 + (index & 1);
            float baseAngle = (float) (Math.PI * 2.0D * (index + seed0 * 0.62F) / count);
            float degreesPerSecond = hero ? Mth.lerp(seed1, -0.35F, 0.65F)
                    : Mth.lerp(seed1, -0.8F, 2.4F);
            float angle = baseAngle + age / 20.0F * degreesPerSecond * (float) (Math.PI / 180.0D);
            float radiusFactor = hero ? Mth.lerp(seed2, 0.54F, 0.83F)
                    : Mth.lerp(seed2, 0.58F, 0.97F);
            float radius = spatialRadius * radiusFactor;
            float topGap = Mth.lerp(seed1, 1.45F, 3.25F) + (hero ? 0.0F : seed3 * 1.1F);
            float top = field.beamHeight() - topGap;
            float desiredLength = field.beamHeight() * (hero
                    ? Mth.lerp(seed3, 0.52F, 0.72F)
                    : Mth.lerp(seed3, 0.30F, 0.61F));
            float length = Math.max(3.5F, Math.min(desiredLength, top - 2.0F));
            float width = field.baseRadius() * (hero
                    ? Mth.lerp(seed0, 0.145F, 0.205F)
                    : Mth.lerp(seed0, 0.060F, 0.112F));
            width *= 0.90F + 0.10F * spatialRadius / Math.max(1.0F, field.baseRadius());

            float appearanceDelay = seed2 * 11.0F + (index % 4) * 1.7F;
            float appear = smoothStep(appearanceDelay, appearanceDelay + 11.0F, age);
            float breathWave = Mth.sin(age * (hero ? 0.022F : 0.034F) + seed0 * 18.0F);
            float breath = hero ? 0.84F + 0.16F * breathWave
                    : 0.28F + 0.72F * smoothStep(-0.62F, 0.58F, breathWave);
            float charge = finisherAge < 0.0F ? 0.0F
                    : smoothStep(0.0F, Math.max(1.0F, field.chargeTicks()), finisherAge);
            float opacity = appear * breath * lifetimeFade;
            float energy = brightness * (1.0F + charge * 0.12F + impactPulse * 0.42F);
            float phase = fractional(seed3 + age * Mth.lerp(seed1, 0.013F, 0.020F)
                    + impactPulse * 0.18F);
            float flow = ClientOptions.spiritCurtainFlow()
                    ? Mth.lerp(seed2, 0.58F, 0.94F) : 0.0F;

            float x = Mth.cos(angle) * radius;
            float z = Mth.sin(angle) * radius;
            Vec3 worldMid = fieldOrigin.add(x, top - length * 0.5F, z);
            Vec3 towardCamera = camera.subtract(worldMid);
            Vec3 horizontal = new Vec3(towardCamera.x, 0.0D, towardCamera.z);
            if (horizontal.lengthSqr() < 1.0E-6D) horizontal = new Vec3(0.0D, 0.0D, 1.0D);
            horizontal = horizontal.normalize();
            Vec3 right = new Vec3(horizontal.z, 0.0D, -horizontal.x);
            Vec3 tangent = new Vec3(-Mth.sin(angle), 0.0D, Mth.cos(angle));

            draws.add(new CurtainDraw(tile, segments, x, z, top, length, width, right, tangent,
                    age, seed0, phase, energy, flow, opacity, hero));
        }

        // Draw one complete render type at a time. Alternating two consumers from BufferSource
        // would make both references point at whichever shared BufferBuilder was activated last.
        if (ClientOptions.spiritCurtainMist()) {
            VertexConsumer baseVertices = buffers.getBuffer(SpiritRenderStates.curtain(baseTexture, false));
            for (CurtainDraw draw : draws) {
                drawCurtainRibbon(baseVertices, poseStack.last(), draw.tile, draw.segments,
                        draw.x, draw.z, draw.top, draw.length, draw.width, draw.right, draw.tangent,
                        draw.age, draw.seed, draw.phase, draw.energy, draw.flow * 0.28F,
                        draw.opacity * (draw.hero ? 0.54F : 0.40F));
            }
        }
        VertexConsumer glyphVertices = buffers.getBuffer(SpiritRenderStates.curtain(glyphTexture, true));
        for (CurtainDraw draw : draws) {
            drawCurtainRibbon(glyphVertices, poseStack.last(), draw.tile, draw.segments,
                    draw.x, draw.z, draw.top, draw.length, draw.width * 0.985F,
                    draw.right, draw.tangent, draw.age, draw.seed, draw.phase, draw.energy,
                    draw.flow, draw.opacity * (draw.hero ? 0.80F : 0.66F));
        }
    }

    private record CurtainDraw(int tile, int segments, float x, float z, float top, float length,
                               float width, Vec3 right, Vec3 tangent, float age, float seed,
                               float phase, float energy, float flow, float opacity, boolean hero) { }

    private static void drawCurtainRibbon(VertexConsumer vertices, PoseStack.Pose pose, int tile,
                                          int segments, float x, float z, float top, float length,
                                          float width, Vec3 right, Vec3 tangent, float age, float seed,
                                          float phase, float brightness, float flow, float opacity) {
        float u0 = (tile * CURTAIN_TILE_WIDTH + 2.0F) / CURTAIN_ATLAS_SIZE;
        float u1 = ((tile + 1) * CURTAIN_TILE_WIDTH - 2.0F) / CURTAIN_ATLAS_SIZE;
        for (int segment = 0; segment < segments; segment++) {
            float t0 = segment / (float) segments;
            float t1 = (segment + 1) / (float) segments;
            float y0 = top - length * t0;
            float y1 = top - length * t1;
            float sway0 = Mth.sin(age * 0.025F + seed * 23.0F + t0 * 4.1F)
                    * width * (0.07F + t0 * 0.08F);
            float sway1 = Mth.sin(age * 0.025F + seed * 23.0F + t1 * 4.1F)
                    * width * (0.07F + t1 * 0.08F);
            float breathingWidth0 = width * (0.90F + 0.10F * Mth.sin(seed * 31.0F + t0 * 5.3F));
            float breathingWidth1 = width * (0.90F + 0.10F * Mth.sin(seed * 31.0F + t1 * 5.3F));
            Vec3 center0 = new Vec3(x, y0, z).add(tangent.scale(sway0));
            Vec3 center1 = new Vec3(x, y1, z).add(tangent.scale(sway1));
            Vec3 left0 = center0.subtract(right.scale(breathingWidth0 * 0.5F));
            Vec3 right0 = center0.add(right.scale(breathingWidth0 * 0.5F));
            Vec3 left1 = center1.subtract(right.scale(breathingWidth1 * 0.5F));
            Vec3 right1 = center1.add(right.scale(breathingWidth1 * 0.5F));
            float alpha0 = opacity * curtainEdgeFade(t0);
            float alpha1 = opacity * curtainEdgeFade(t1);
            curtainVertex(vertices, pose, left0, u0, t0, phase, brightness, flow, alpha0);
            curtainVertex(vertices, pose, left1, u0, t1, phase, brightness, flow, alpha1);
            curtainVertex(vertices, pose, right1, u1, t1, phase, brightness, flow, alpha1);
            curtainVertex(vertices, pose, right0, u1, t0, phase, brightness, flow, alpha0);
        }
    }

    private static void curtainVertex(VertexConsumer vertices, PoseStack.Pose pose, Vec3 point,
                                      float u, float v, float phase, float brightness,
                                      float flow, float alpha) {
        int phaseChannel = Math.round(Mth.clamp(phase, 0.0F, 1.0F) * 255.0F);
        int brightnessChannel = Math.round(Mth.clamp(brightness / 1.55F, 0.0F, 1.0F) * 255.0F);
        int flowChannel = Math.round(Mth.clamp(flow, 0.0F, 1.0F) * 255.0F);
        vertices.addVertex(pose.pose(), (float) point.x, (float) point.y, (float) point.z)
                .setColor(phaseChannel, brightnessChannel, flowChannel,
                        Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F))
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F)
                ;
    }

    private static ResourceLocation curtainTexture(String variant, String pass) {
        return ResourceLocation.fromNamespaceAndPath(YujianCraft.MOD_ID,
                "textures/effect/sword_array/curtain/" + variant + "_" + pass + ".png");
    }

    private static float curtainFieldRadius(SwordArrayFieldEntity field, float finisherAge) {
        float base = field.baseRadius();
        if (!ClientOptions.swordArrayExpansion()) return base;
        float expansionStart = field.chargeTicks() + field.holdTicks() + 2.0F;
        float progress = smoothStep(expansionStart,
                expansionStart + Math.max(1.0F, field.expandTicks() * 1.65F), finisherAge);
        float delayed = 1.0F - (float) Math.pow(1.0F - progress, 3.0D);
        return base * Mth.lerp(delayed, 1.0F,
                1.0F + (field.expansion() - 1.0F) * 0.68F);
    }

    private static float curtainEdgeFade(float value) {
        return smoothStep(0.0F, 0.075F, value)
                * (1.0F - smoothStep(0.80F, 1.0F, value));
    }

    private static float curtainSeed(UUID id, int index, int salt) {
        long value = id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 17)
                ^ (index + 1L) * 0x9E3779B97F4A7C15L
                ^ (salt + 3L) * 0xC2B2AE3D27D4EB4FL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (float) ((value >>> 40) & 0xFFFFFFL) / 16777215.0F;
    }

    private static float smoothStep(float edge0, float edge1, float value) {
        float t = Mth.clamp((value - edge0) / Math.max(1.0E-5F, edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static void drawTexturedPlane(VertexConsumer vertices, PoseStack.Pose pose, float radius,
                                          int red, int green, int blue, int alpha) {
        texturedVertex(vertices, pose, -radius, 0.0F, -radius, 0.0F, 0.0F, red, green, blue, alpha);
        texturedVertex(vertices, pose, -radius, 0.0F, radius, 0.0F, 1.0F, red, green, blue, alpha);
        texturedVertex(vertices, pose, radius, 0.0F, radius, 1.0F, 1.0F, red, green, blue, alpha);
        texturedVertex(vertices, pose, radius, 0.0F, -radius, 1.0F, 0.0F, red, green, blue, alpha);
    }

    private static void texturedVertex(VertexConsumer vertices, PoseStack.Pose pose,
                                       float x, float y, float z, float u, float v,
                                       int red, int green, int blue, int alpha) {
        vertices.addVertex(pose.pose(), x, y, z)
                .setColor(red, green, blue, Mth.clamp(alpha, 0, 255))
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F)
                ;
    }

    /** Kept inside the entity renderer so the dispatcher owns camera-relative positioning. */
    private static void renderCelestialSeal(SwordArrayFieldEntity field, SwordArrayVisualStyle style,
                                            float[] material, float age, float finisherAge,
                                            float ringRadius, PoseStack poseStack,
                                            VertexConsumer vertices) {
        for (int layer = 0; layer < style.upperLayers(); layer++) {
            float centre = (style.upperLayers() - 1) * 0.5F;
            float distanceFromCentre = Math.abs(layer - centre);
            float radiusScale = Math.max(0.42F,
                    1.0F - (centre - distanceFromCentre) * style.upperRadiusStep());
            float vertical = (layer - centre) * style.upperHeightStep();
            float layerPhase = layer * 0.71F;
            for (int face = 0; face < 2; face++) {
                poseStack.pushPose();
                poseStack.translate(0.0D, field.beamHeight() + vertical
                        + (face == 0 ? -style.upperThickness() * 0.5F
                        : style.upperThickness() * 0.5F), 0.0D);
                renderSeal(vertices, poseStack.last().pose(), ringRadius * radiusScale, material, style,
                        age + layer * 13.0F, layerPhase, finisherAge >= 0.0F);
                poseStack.popPose();
            }
            float[] wallTint = sealColour(material, style.outerTint(), style.brightness(), 0.82F);
            poseStack.pushPose();
            poseStack.translate(0.0D, field.beamHeight() + vertical, 0.0D);
            drawRingWall(vertices, poseStack.last().pose(), ringRadius * radiusScale,
                    -style.upperThickness() * 0.5F, style.upperThickness() * 0.5F,
                    wallTint[0], wallTint[1], wallTint[2], 96, 80);
            poseStack.popPose();
        }
    }

    /** Six enlarged outward-facing implements make the array positions readable at colossal scale.
     * They share the same preview entity as the giant sword, so body glow, aura and installed core
     * accents all travel through the normal flying-sword renderer. */
    private void renderArraySwords(SwordArrayFieldEntity field, SwordArrayVisualStyle style,
                                   float ringRadius, float age, float partialTick, PoseStack poseStack,
                                   MultiBufferSource buffers) {
        if (!ensureVisualSword(field)) return;
        float orbit = age * 0.036F;
        float radius = ringRadius * 0.81F;
        for (int slot = 0; slot < 6; slot++) {
            double angle = Math.PI * 2.0D * slot / 6.0D + orbit;
            float x = (float) Math.cos(angle) * radius;
            float z = (float) Math.sin(angle) * radius;
            float outwardYaw = (float) Math.toDegrees(Math.atan2(-Math.cos(angle), Math.sin(angle)));
            visualSword.configureTechniqueVisualPreview(field.getDisplayStack(), slot, 0.0F,
                    outwardYaw, Math.round(age));
            poseStack.pushPose();
            poseStack.translate(x, field.beamHeight(), z);
            poseStack.scale(style.orbitSwordScale(), style.orbitSwordScale(), style.orbitSwordScale());
            entityRenderDispatcher.getRenderer(visualSword).render(visualSword, outwardYaw, partialTick,
                    poseStack, buffers, LightTexture.FULL_BRIGHT);
            poseStack.popPose();
        }
    }

    private boolean ensureVisualSword(SwordArrayFieldEntity field) {
        if (visualSword == null || visualSword.level() != field.level()) {
            visualSword = ModEntities.FLYING_SWORD.get().create(field.level());
        }
        return visualSword != null;
    }

    private static void renderSeal(VertexConsumer vertices, Matrix4f matrix, float radius,
                                   float[] material, SwordArrayVisualStyle style, float age,
                                   float layerPhase, boolean monochrome) {
        float pulse = 0.92F + 0.08F * Mth.sin(age * 0.18F);
        float[] inner = sealColour(material, style.innerTint(), style.brightness(), 0.88F);
        float[] middle = sealColour(material, style.middleTint(), style.brightness(), 0.82F);
        float[] outer = sealColour(material, style.outerTint(), style.brightness(), 0.78F);
        if (monochrome) {
            inner = whiten(inner, 0.16F);
            middle = whiten(middle, 0.12F);
            outer = whiten(outer, 0.10F);
        }
        float innerSpin = -age * 0.031F - layerPhase;
        float middleSpin = age * 0.019F + layerPhase * 0.57F;
        float outerSpin = age * 0.009F - layerPhase * 0.33F;

        // Wide low-alpha duplicates form a restrained additive halo. They bridge the hard geometry
        // into the surrounding air without spawning networked particles or hiding the rune detail.
        int haloAlpha = Math.round(34.0F * style.haloStrength() * pulse);
        drawBrokenAnnulus(vertices, matrix, radius * 0.925F, radius * 1.025F, -0.025F,
                outer[0], outer[1], outer[2], haloAlpha, 112, 11, 2, outerSpin);
        drawBrokenAnnulus(vertices, matrix, radius * 0.61F, radius * 0.715F, 0.055F,
                middle[0], middle[1], middle[2], Math.round(haloAlpha * 0.72F), 88, 7, 2, middleSpin);

        drawBrokenAnnulus(vertices, matrix, radius * 0.945F, radius, 0.0F,
                outer[0], outer[1], outer[2], Math.round(225.0F * pulse), 112, 11, 2, outerSpin);
        drawBrokenAnnulus(vertices, matrix, radius * 0.815F, radius * 0.842F, -0.11F,
                outer[0] * 0.88F, outer[1] * 0.92F, outer[2], Math.round(172.0F * pulse), 96, 8, 1, -outerSpin * 1.31F);
        drawBrokenAnnulus(vertices, matrix, radius * 0.655F, radius * 0.682F, 0.09F,
                middle[0], middle[1], middle[2], Math.round(208.0F * pulse), 88, 7, 2, middleSpin);
        drawBrokenAnnulus(vertices, matrix, radius * 0.375F, radius * 0.415F, -0.05F,
                inner[0], inner[1], inner[2], Math.round(186.0F * pulse), 72, 6, 1, innerSpin);

        // An octagonal spiritual core replaces the former double-circle "eye" silhouette.
        drawPolygonRing(vertices, matrix, 8, radius * 0.125F, radius * 0.235F, 0.045F,
                innerSpin, inner[0], inner[1], inner[2], Math.round(216.0F * pulse));
        drawPolygonRing(vertices, matrix, 6, radius * 0.255F, radius * 0.275F, 0.015F,
                -innerSpin * 0.72F, middle[0], middle[1], middle[2], Math.round(142.0F * pulse));

        for (int index = 0; index < 16; index++) {
            double angle = Math.PI * 2.0D * index / 16.0D + middleSpin * 0.72D;
            drawSpoke(vertices, matrix, radius * 0.25F, radius * 0.925F, angle,
                    radius * (index % 2 == 0 ? 0.0075F : 0.0045F),
                    middle[0], middle[1], middle[2], index % 2 == 0 ? 128 : 82);
        }
        renderTrigramBand(vertices, matrix, radius, middleSpin, middle, pulse);
        for (int index = 0; index < 32; index++) {
            double angle = Math.PI * 2.0D * index / 32.0D - outerSpin * 1.4D;
            drawChevron(vertices, matrix, radius * (index % 2 == 0 ? 0.73F : 0.77F), angle,
                    radius * 0.055F, radius * 0.022F,
                    outer[0], outer[1], outer[2], index % 2 == 0 ? 168 : 112);
        }
        for (int index = 0; index < 12; index++) {
            double angle = Math.PI * 2.0D * index / 12.0D + outerSpin * 0.64D;
            float length = radius * (0.07F + 0.025F * (index % 3));
            drawCurtain(vertices, matrix, radius * 0.91F, angle, radius * 0.018F, length,
                    outer[0], outer[1], outer[2], 92);
        }
        renderSpiritFragments(vertices, matrix, radius, age + layerPhase * 17.0F,
                outer, style.fragmentStrength());
    }

    private static void renderTrigramBand(VertexConsumer vertices, Matrix4f matrix, float radius,
                                          float rotation, float[] colour, float pulse) {
        int[] trigrams = {0b111, 0b110, 0b101, 0b100, 0b011, 0b010, 0b001, 0b000};
        for (int sector = 0; sector < 8; sector++) {
            double angle = Math.PI * 2.0D * sector / 8.0D + rotation;
            for (int line = 0; line < 3; line++) {
                float radial = radius * (0.49F + line * 0.046F);
                boolean solid = (trigrams[sector] & (1 << line)) != 0;
                float halfLength = radius * 0.055F;
                float halfWidth = radius * 0.009F;
                if (solid) {
                    drawTangentialBar(vertices, matrix, radial, angle, halfLength, halfWidth,
                            colour[0], colour[1], colour[2], Math.round(176.0F * pulse));
                } else {
                    float part = halfLength * 0.41F;
                    drawTangentialBar(vertices, matrix, radial, angle, part, halfWidth,
                            -halfLength * 0.57F, colour[0], colour[1], colour[2], Math.round(162.0F * pulse));
                    drawTangentialBar(vertices, matrix, radial, angle, part, halfWidth,
                            halfLength * 0.57F, colour[0], colour[1], colour[2], Math.round(162.0F * pulse));
                }
            }
        }
    }

    private static void renderSpiritFragments(VertexConsumer vertices, Matrix4f matrix, float radius,
                                               float age, float[] colour, float strength) {
        if (strength <= 0.001F) return;
        int count = 28;
        for (int index = 0; index < count; index++) {
            float seed = index * 12.9898F;
            float phase = age * (0.016F + (index % 5) * 0.0025F) + seed;
            float visibility = Math.max(0.0F, Mth.sin(phase) * 0.5F + 0.5F);
            if (visibility < 0.28F) continue;
            double angle = Math.PI * 2.0D * index / count + Mth.sin(seed) * 0.19D - age * 0.004D;
            float orbit = radius * (0.88F + 0.17F * fractional(Mth.sin(seed * 0.37F) * 437.58F));
            float length = radius * (0.010F + 0.018F * visibility);
            float width = length * (0.22F + 0.18F * (index % 3));
            drawShard(vertices, matrix, orbit, angle, length, width,
                    0.10F + Mth.sin(phase * 0.7F) * radius * 0.002F,
                    colour[0], colour[1], colour[2],
                    Math.round(110.0F * visibility * strength));
        }
    }

    private static void renderWhiteFinisher(VertexConsumer vertices, Matrix4f matrix,
                                            SwordArrayFieldEntity field, SwordArrayVisualStyle style, float ringRadius,
                                            float beamRadius, float finisherAge, float fade, float age) {
        float burst = field.chargeTicks() + field.holdTicks();
        float impact = finisherAge < burst ? 0.0F : Mth.clamp(
                (finisherAge - burst) / Math.max(1.0F, field.expandTicks()), 0.0F, 1.0F);
        SwordArrayVisualStyle.Colour shell = style.shellTint();
        int shellCount = style.shellEnabled() ? Math.max(1, style.shellLayers()) : 0;
        for (int layer = 0; layer < shellCount; layer++) {
            float t = shellCount == 1 ? 1.0F : layer / (float) (shellCount - 1);
            float radius = Math.max(0.16F, beamRadius * (0.34F + 0.66F * t)
                    * (1.0F + t * style.shellSpacing()));
            float opacity = (float) Math.pow(style.shellOpacityFalloff(), layer);
            float brightness = (float) Math.pow(style.shellBrightnessFalloff(), layer);
            int alpha = Math.round(Mth.lerp(impact, 236.0F, 202.0F) * fade * opacity);
            drawTurbulentColumn(vertices, matrix, radius, field.beamHeight(), age + layer * 17.0F,
                    shell.red() * brightness, shell.green() * brightness,
                    shell.blue() * brightness, alpha, 24, 6, 0.035F + t * 0.045F);
        }
        float coreFade = finisherAge < burst ? 1.0F : Mth.clamp(1.0F - (finisherAge - burst) / 2.5F, 0.0F, 1.0F);
        if (coreFade > 0.01F) drawTurbulentColumn(vertices, matrix, Math.max(0.12F, beamRadius * 0.28F),
                field.beamHeight(), age + 43.0F, 1.0F, 1.0F, 1.0F,
                Math.round(228.0F * fade * coreFade), 20, 6, 0.018F);

        float topHub = Math.min(ringRadius * 0.34F,
                Math.max(beamRadius * 1.22F, field.baseRadius() * 0.16F));
        float flareDepth = Math.min(field.beamHeight() * 0.22F, Math.max(3.0F, topHub * 0.46F));
        if (coreFade > 0.01F) drawTurbulentFrustum(vertices, matrix, beamRadius * 0.70F, topHub,
                field.beamHeight() - flareDepth, field.beamHeight(), age + 61.0F,
                0.91F, 0.95F, 1.0F, Math.round(186.0F * fade * coreFade), 36, 5, 0.045F);
        drawAnnulus(vertices, matrix, topHub * 0.42F, topHub, field.beamHeight() - 0.08F,
                0.96F, 0.98F, 1.0F, Math.round(224.0F * fade), 72);
        renderImpactBands(vertices, matrix, field, style, finisherAge, fade, age);
    }

    private static void renderImpactBands(VertexConsumer vertices, Matrix4f matrix,
                                          SwordArrayFieldEntity field, SwordArrayVisualStyle style, float finisherAge,
                                          float fade, float age) {
        float burst = field.chargeTicks() + field.holdTicks();
        if (finisherAge < burst) return;
        float progress = Mth.clamp((finisherAge - burst) / Math.max(1.0F, field.expandTicks()), 0.0F, 1.0F);
        float radius = field.baseRadius() * (0.34F + progress * 0.28F);
        SwordArrayVisualStyle.Colour ground = style.groundTint();
        float unified = style.brightness();
        for (int layer = 0; layer < style.groundLayers(); layer++) {
            float layerRadius = radius * (1.0F + layer * style.groundRadiusStep());
            float y = 0.04F + layer * style.groundThickness();
            float brightness = (float) Math.pow(0.86F, layer);
            drawBrokenAnnulus(vertices, matrix, layerRadius * 0.77F, layerRadius, y,
                    ground.red() * brightness * unified, ground.green() * brightness * unified, ground.blue() * brightness * unified,
                    Math.round(206.0F * fade * (float) Math.pow(0.76F, layer)), 96, 9, 2,
                    age * (layer % 2 == 0 ? 0.035F : -0.029F));
            drawRingWall(vertices, matrix, layerRadius, y, y + style.groundThickness(),
                    ground.red() * brightness * unified, ground.green() * brightness * unified,
                    ground.blue() * brightness * unified, Math.round(126.0F * fade), 72);
        }
        drawBrokenAnnulus(vertices, matrix, radius * 0.43F, radius * 0.57F, 0.12F,
                ground.red()*unified, ground.green()*unified, ground.blue()*unified, Math.round(216.0F * fade), 80, 7, 1, -age * 0.046F);
        for (int index = 0; index < 18; index++) {
            double angle = Math.PI * 2.0D * index / 18.0D + age * 0.017D;
            drawSpoke(vertices, matrix, radius * 0.24F, radius * 1.08F, angle,
                    radius * 0.006F, ground.red()*unified, ground.green()*unified, ground.blue()*unified, Math.round(118.0F * fade));
        }
    }

    private void renderDescendingSword(SwordArrayFieldEntity field, SwordArrayVisualStyle style,
                                       float finisherAge, float partialTick, PoseStack poseStack,
                                       MultiBufferSource buffers) {
        float start = field.chargeTicks() + field.holdTicks();
        if (finisherAge < start) return;
        float progress = Mth.clamp((finisherAge - start) / style.giantSwordDescentTicks(), 0.0F, 1.0F);
        float eased = progress * progress * (3.0F - 2.0F * progress);
        float scale = style.giantSwordScale();
        var display = field.getDisplayStack();
        boolean formal = WanxiangSwordData.renderPreset(display) != WanxiangRenderPreset.VANILLA_FLAT;
        float localAuraTip = formal ? 1.33F : 1.20F;
        float profileScale = WanxiangSwordData.scalePercent(display) / 100.0F;
        float auraLengthScale = WanxiangSwordData.auraLengthPercent(display) / 100.0F;
        // FlyingSwordRenderer applies its own 1.25 profile scale before drawing the aura. Matching
        // that exact transform makes the visible sword tip settle on the ground instead of using a
        // hand-tuned offset that embeds one model and leaves another hovering.
        float tipOffset = scale * 1.25F * profileScale * auraLengthScale * localAuraTip;
        // The aura extends visibly beyond the opaque model tip. In play tests the perceived blade
        // therefore stopped 9-10 blocks above the ground at the preferred 20-25x scale. Apply the
        // correction proportionally so custom scales retain the same grounded silhouette.
        float landedY = Math.max(0.1F, tipOffset - scale * 0.42F + 0.04F);
        float y = Mth.lerp(eased, field.beamHeight() + landedY, landedY);
        if (!ensureVisualSword(field)) return;
        visualSword.configureTechniqueVisualPreview(field.getDisplayStack(), 0, 90.0F, 0.0F,
                Math.round(field.renderAge(partialTick)));
        poseStack.pushPose();
        poseStack.translate(0.0D, y, 0.0D);
        poseStack.scale(scale, scale, scale);
        entityRenderDispatcher.getRenderer(visualSword).render(visualSword, 0.0F, partialTick,
                poseStack, buffers, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    private static void drawStyledCore(VertexConsumer vertices, Matrix4f matrix,
                                       SwordArrayVisualStyle style, float radius, float height,
                                       float phase, int alpha) {
        switch (style.beamShape()) {
            case CONE -> drawTurbulentFrustum(vertices, matrix, radius * 1.35F, radius * 0.28F,
                    0.0F, height, phase, 1.0F, 1.0F, 1.0F, alpha, 24, 8, 0.025F);
            case BLADE -> drawBladeColumn(vertices, matrix, radius * 1.45F, height, phase, alpha);
            case COLUMN -> drawTurbulentColumn(vertices, matrix, radius, height, phase,
                    1.0F, 1.0F, 1.0F, alpha, 24, 7, 0.025F);
        }
    }

    /** A tapered octagonal prism: broad in X, thin in Z, pointed at both ends like a vertical blade. */
    private static void drawBladeColumn(VertexConsumer vertices, Matrix4f matrix, float radius,
                                        float height, float phase, int alpha) {
        int segments = 8;
        int layers = 10;
        for (int layer = 0; layer < layers; layer++) {
            float t0 = layer / (float) layers;
            float t1 = (layer + 1) / (float) layers;
            float profile0 = 0.22F + 0.78F * Mth.sin((float) Math.PI * t0);
            float profile1 = 0.22F + 0.78F * Mth.sin((float) Math.PI * t1);
            for (int index = 0; index < segments; index++) {
                double a0 = Math.PI * 2.0D * index / segments;
                double a1 = Math.PI * 2.0D * (index + 1) / segments;
                float wobble00 = irregularRadius(a0, t0, phase, 0.035F);
                float wobble01 = irregularRadius(a1, t0, phase, 0.035F);
                float wobble11 = irregularRadius(a1, t1, phase, 0.035F);
                float wobble10 = irregularRadius(a0, t1, phase, 0.035F);
                vertex(vertices, matrix, (float)Math.cos(a0)*radius*profile0*wobble00,
                        height*t0, (float)Math.sin(a0)*radius*0.28F*profile0*wobble00, 1,1,1,alpha);
                vertex(vertices, matrix, (float)Math.cos(a1)*radius*profile0*wobble01,
                        height*t0, (float)Math.sin(a1)*radius*0.28F*profile0*wobble01, 1,1,1,alpha);
                vertex(vertices, matrix, (float)Math.cos(a1)*radius*profile1*wobble11,
                        height*t1, (float)Math.sin(a1)*radius*0.28F*profile1*wobble11, 1,1,1,alpha);
                vertex(vertices, matrix, (float)Math.cos(a0)*radius*profile1*wobble10,
                        height*t1, (float)Math.sin(a0)*radius*0.28F*profile1*wobble10, 1,1,1,alpha);
            }
        }
    }

    private static float arrayRadius(SwordArrayFieldEntity field, float age) {
        float finisherAge = age - field.finisherStartTick();
        float base = field.baseRadius();
        if (!ClientOptions.swordArrayExpansion()) {
            return base * (1.0F + 0.025F * Mth.sin(age * 0.14F));
        }
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
        return thin * (1.0F - progress * 0.22F);
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

    private static void drawPolygonRing(VertexConsumer vertices, Matrix4f matrix, int sides,
                                        float inner, float outer, float y, float rotation,
                                        float red, float green, float blue, int alpha) {
        for (int index = 0; index < sides; index++) {
            double a0 = Math.PI * 2.0D * index / sides + rotation;
            double a1 = Math.PI * 2.0D * (index + 1) / sides + rotation;
            annulusSegment(vertices, matrix, inner, outer, y, red, green, blue, alpha, a0, a1);
        }
    }

    private static void drawTangentialBar(VertexConsumer vertices, Matrix4f matrix, float radius,
                                           double angle, float halfLength, float halfWidth,
                                           float red, float green, float blue, int alpha) {
        drawTangentialBar(vertices, matrix, radius, angle, halfLength, halfWidth, 0.0F,
                red, green, blue, alpha);
    }

    private static void drawTangentialBar(VertexConsumer vertices, Matrix4f matrix, float radius,
                                           double angle, float halfLength, float halfWidth, float tangentOffset,
                                           float red, float green, float blue, int alpha) {
        float radialX = (float) Math.cos(angle);
        float radialZ = (float) Math.sin(angle);
        float tangentX = -radialZ;
        float tangentZ = radialX;
        float centerX = radialX * radius + tangentX * tangentOffset;
        float centerZ = radialZ * radius + tangentZ * tangentOffset;
        vertex(vertices, matrix, centerX - tangentX * halfLength - radialX * halfWidth, 0.085F,
                centerZ - tangentZ * halfLength - radialZ * halfWidth, red, green, blue, alpha);
        vertex(vertices, matrix, centerX + tangentX * halfLength - radialX * halfWidth, 0.085F,
                centerZ + tangentZ * halfLength - radialZ * halfWidth, red, green, blue, alpha);
        vertex(vertices, matrix, centerX + tangentX * halfLength + radialX * halfWidth, 0.085F,
                centerZ + tangentZ * halfLength + radialZ * halfWidth, red, green, blue, alpha);
        vertex(vertices, matrix, centerX - tangentX * halfLength + radialX * halfWidth, 0.085F,
                centerZ - tangentZ * halfLength + radialZ * halfWidth, red, green, blue, alpha);
    }

    private static void drawShard(VertexConsumer vertices, Matrix4f matrix, float radius, double angle,
                                  float radialLength, float tangentWidth, float y,
                                  float red, float green, float blue, int alpha) {
        float radialX = (float) Math.cos(angle);
        float radialZ = (float) Math.sin(angle);
        float tangentX = -radialZ;
        float tangentZ = radialX;
        float x = radialX * radius;
        float z = radialZ * radius;
        vertex(vertices, matrix, x + radialX * radialLength, y, z + radialZ * radialLength,
                red, green, blue, 0);
        vertex(vertices, matrix, x + tangentX * tangentWidth, y + radialLength * 0.28F,
                z + tangentZ * tangentWidth, red, green, blue, alpha);
        vertex(vertices, matrix, x - radialX * radialLength * 0.55F, y,
                z - radialZ * radialLength * 0.55F, red, green, blue, Math.round(alpha * 0.36F));
        vertex(vertices, matrix, x - tangentX * tangentWidth, y - radialLength * 0.22F,
                z - tangentZ * tangentWidth, red, green, blue, alpha);
    }

    private static void drawRingWall(VertexConsumer vertices, Matrix4f matrix, float radius,
                                     float bottomY, float topY, float red, float green, float blue,
                                     int alpha, int segments) {
        for (int index = 0; index < segments; index++) {
            double a0 = Math.PI * 2.0D * index / segments;
            double a1 = Math.PI * 2.0D * (index + 1) / segments;
            vertex(vertices, matrix, (float)Math.cos(a0)*radius, bottomY, (float)Math.sin(a0)*radius,
                    red, green, blue, alpha);
            vertex(vertices, matrix, (float)Math.cos(a1)*radius, bottomY, (float)Math.sin(a1)*radius,
                    red, green, blue, alpha);
            vertex(vertices, matrix, (float)Math.cos(a1)*radius, topY, (float)Math.sin(a1)*radius,
                    red, green, blue, alpha);
            vertex(vertices, matrix, (float)Math.cos(a0)*radius, topY, (float)Math.sin(a0)*radius,
                    red, green, blue, alpha);
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
        consumer.addVertex(matrix, x, y, z).setColor(red, green, blue,
                Mth.clamp(alpha, 0, 255) / 255.0F);
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

    private static float[] tinted(float[] material, SwordArrayVisualStyle.Colour tint, float amount) {
        return new float[]{Mth.lerp(amount, material[0], tint.red()),
                Mth.lerp(amount, material[1], tint.green()),
                Mth.lerp(amount, material[2], tint.blue())};
    }

    private static float[] scaled(float[] colour, float brightness) {
        return new float[]{Mth.clamp(colour[0] * brightness, 0.0F, 1.0F),
                Mth.clamp(colour[1] * brightness, 0.0F, 1.0F),
                Mth.clamp(colour[2] * brightness, 0.0F, 1.0F)};
    }

    private static float[] sealColour(float[] material, SwordArrayVisualStyle.Colour tint,
                                      float brightness, float tintAmount) {
        return scaled(tinted(material, tint, tintAmount), brightness);
    }

    private static float[] whiten(float[] colour, float amount) {
        return new float[]{Mth.lerp(amount, colour[0], 1.0F),
                Mth.lerp(amount, colour[1], 1.0F), Mth.lerp(amount, colour[2], 1.0F)};
    }

    private static float fractional(float value) {
        return value - Mth.floor(value);
    }

    @Override
    public ResourceLocation getTextureLocation(SwordArrayFieldEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    private static final class SpiritRenderStates extends RenderStateShard {
        private static final Map<ResourceLocation, RenderType> TEXTURED = new ConcurrentHashMap<>();
        private static final Map<ResourceLocation, RenderType> ADDITIVE_TEXTURED = new ConcurrentHashMap<>();
        private static final Map<ResourceLocation, RenderType> CURTAIN_TEXTURED = new ConcurrentHashMap<>();
        private static final Map<ResourceLocation, RenderType> CURTAIN_ADDITIVE = new ConcurrentHashMap<>();

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

        private static RenderType createCelestial() {
            return RenderType.create("yujiancraft_celestial_spirit_light", DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS, RenderType.SMALL_BUFFER_SIZE, false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                            .setTransparencyState(ADDITIVE_TRANSPARENCY)
                            .setDepthTestState(NO_DEPTH_TEST)
                            .setCullState(NO_CULL)
                            .setLightmapState(NO_LIGHTMAP)
                            .setOverlayState(NO_OVERLAY)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false));
        }

        private static RenderType textured(ResourceLocation texture, boolean additive) {
            Map<ResourceLocation, RenderType> cache = additive ? ADDITIVE_TEXTURED : TEXTURED;
            return cache.computeIfAbsent(texture, location -> RenderType.create(
                    "yujiancraft_sword_array_" + (additive ? "additive_" : "texture_")
                            + location.getPath().replace('/', '_'),
                    DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                    RenderType.SMALL_BUFFER_SIZE, false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                            .setTextureState(new TextureStateShard(location, false, true))
                            .setTransparencyState(additive ? ADDITIVE_TRANSPARENCY : TRANSLUCENT_TRANSPARENCY)
                            .setDepthTestState(NO_DEPTH_TEST)
                            .setCullState(NO_CULL)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(true)));
        }

        private static RenderType curtain(ResourceLocation texture, boolean additive) {
            Map<ResourceLocation, RenderType> cache = additive ? CURTAIN_ADDITIVE : CURTAIN_TEXTURED;
            return cache.computeIfAbsent(texture, location -> RenderType.create(
                    "yujiancraft_spirit_curtain_" + (additive ? "additive_" : "texture_")
                            + location.getPath().replace('/', '_'),
                    DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                    RenderType.SMALL_BUFFER_SIZE, false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(new ShaderStateShard(() -> ClientModEvents.spiritCurtainShader() == null
                                    ? net.minecraft.client.renderer.GameRenderer
                                            .getRendertypeEntityTranslucentEmissiveShader()
                                    : ClientModEvents.spiritCurtainShader()))
                            .setTextureState(new TextureStateShard(location, false, true))
                            .setTransparencyState(additive ? ADDITIVE_TRANSPARENCY : TRANSLUCENT_TRANSPARENCY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setCullState(NO_CULL)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(true)));
        }
    }
}
