package dev.yujiancraft.client;

import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.entity.SpiritTrialDummyEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class SpiritTrialDummyRenderer
        extends MobRenderer<SpiritTrialDummyEntity, HumanoidModel<SpiritTrialDummyEntity>> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(YujianCraft.MOD_ID, "spirit_trial_dummy"), "main");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            YujianCraft.MOD_ID, "textures/entity/spirit_trial_dummy.png");

    public SpiritTrialDummyRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(LAYER)), 0.35F);
    }

    @Override
    public ResourceLocation getTextureLocation(SpiritTrialDummyEntity entity) {
        return TEXTURE;
    }
}
