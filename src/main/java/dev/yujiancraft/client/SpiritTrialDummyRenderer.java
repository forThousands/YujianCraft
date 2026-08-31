package dev.yujiancraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.yujiancraft.entity.FlyingSwordEntity;
import dev.yujiancraft.entity.SpiritTrialDummyEntity;
import dev.yujiancraft.material.FlyingSwordMaterial;
import dev.yujiancraft.registry.ModEntities;
import dev.yujiancraft.registry.ModItems;
import dev.yujiancraft.upgrade.FlyingSwordModule;
import dev.yujiancraft.upgrade.SwordModuleData;
import dev.yujiancraft.visual.FlyingSwordSeries;
import dev.yujiancraft.wanxiang.WanxiangGlowMode;
import dev.yujiancraft.wanxiang.WanxiangRenderPreset;
import dev.yujiancraft.wanxiang.WanxiangSwordData;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Renders the Spirit Trial target as a large gold-and-jade sword pedestal. */
public final class SpiritTrialDummyRenderer extends EntityRenderer<SpiritTrialDummyEntity> {
    private static final float SWORD_ORIGIN_Y = 4.24F;
    private static final float SWORD_SCALE = 2.05F;
    private final BlockRenderDispatcher blockRenderer;
    private final ItemStack swordStack;
    private FlyingSwordEntity visualSword;

    public SpiritTrialDummyRenderer(EntityRendererProvider.Context context) {
        super(context);
        blockRenderer = context.getBlockRenderDispatcher();
        shadowRadius = 1.5F;
        swordStack = createSwordStack();
    }

    @Override
    public void render(SpiritTrialDummyEntity pedestal, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();
        renderSlab(poseStack, buffers, packedLight, 0.0F, false);
        renderSlab(poseStack, buffers, packedLight, SpiritTrialDummyEntity.TOP_SLAB_Y, true);
        renderSuspendedSword(pedestal, partialTick, poseStack, buffers);
        poseStack.popPose();
        super.render(pedestal, yaw, partialTick, poseStack, buffers, packedLight);
    }

    private void renderSlab(PoseStack poseStack, MultiBufferSource buffers, int packedLight,
                            float y, boolean upper) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockState state;
                if (x == 0 && z == 0) state = Blocks.PRISMARINE_BRICKS.defaultBlockState();
                else if (Math.abs(x) + Math.abs(z) == 1) state = Blocks.GOLD_BLOCK.defaultBlockState();
                else state = Blocks.DARK_PRISMARINE.defaultBlockState();
                renderCell(state, poseStack, buffers, packedLight,
                        x - 0.5F, y, z - 0.5F, upper);
            }
        }
    }

    private void renderCell(BlockState state, PoseStack poseStack, MultiBufferSource buffers,
                            int packedLight, float x, float y, float z, boolean upper) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        if (upper) poseStack.translate(0.0F, 0.01F, 0.0F);
        poseStack.scale(1.0F, 0.5F, 1.0F);
        blockRenderer.renderSingleBlock(state, poseStack, buffers, packedLight,
                OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    private void renderSuspendedSword(SpiritTrialDummyEntity pedestal, float partialTick,
                                      PoseStack poseStack, MultiBufferSource buffers) {
        if (!ensureVisualSword(pedestal)) return;
        int visualTick = Math.round(pedestal.tickCount + partialTick);
        visualSword.configureTechniqueVisualPreview(swordStack, 0, 90.0F, 0.0F, visualTick);
        visualSword.setVisualAuraSuppressed(!ClientOptions.trialPedestalSwordAura());

        poseStack.pushPose();
        poseStack.translate(0.0F, SWORD_ORIGIN_Y, 0.0F);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(
                (pedestal.tickCount + partialTick) * 0.72F));
        float breathing = 1.0F + Mth.sin((pedestal.tickCount + partialTick) * 0.06F) * 0.018F;
        poseStack.scale(SWORD_SCALE * breathing, SWORD_SCALE * breathing, SWORD_SCALE * breathing);
        entityRenderDispatcher.getRenderer(visualSword).render(visualSword, 0.0F, partialTick,
                poseStack, buffers, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    private boolean ensureVisualSword(SpiritTrialDummyEntity pedestal) {
        if (visualSword != null && visualSword.level() == pedestal.level()) return true;
        visualSword = ModEntities.FLYING_SWORD.get().create(pedestal.level());
        return visualSword != null;
    }

    private static ItemStack createSwordStack() {
        ItemStack stack = new ItemStack(ModItems.getFlyingSword(
                FlyingSwordMaterial.GOLDEN, FlyingSwordSeries.SPIRITFORGED));
        WanxiangSwordData.applyShape(stack, WanxiangRenderPreset.AXIAL_3D,
                WanxiangGlowMode.FULL_BODY, false, 110, 130, 135);
        SwordModuleData.setLevel(stack, FlyingSwordModule.LIGHTNING, 1);
        return stack;
    }

    @Override
    public ResourceLocation getTextureLocation(SpiritTrialDummyEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
