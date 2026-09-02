package dev.yujiancraft.data;

import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.material.FlyingSwordMaterial;
import net.minecraft.data.PackOutput;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.client.model.generators.ItemModelBuilder;
import dev.yujiancraft.visual.FlyingSwordSeries;

public final class ModItemModelProvider extends ItemModelProvider {
    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, YujianCraft.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        for (FlyingSwordMaterial material : FlyingSwordMaterial.values()) {
            // Keep every flying sword on the exact same baked-model transform. Inheriting the
            // vanilla sword model adds another model layer and produced material-dependent FIXED
            // transforms in the entity renderer; only the texture is meant to vary here.
            singleTexture(material.itemId(), mcLoc("item/handheld"), "layer0",
                    mcLoc("item/" + material.serializedName() + "_sword"));
            for (FlyingSwordSeries series : FlyingSwordSeries.values()) {
                if (series == FlyingSwordSeries.STANDARD) continue;
                ItemModelBuilder model = withExistingParent(series.itemId(material), modLoc(
                        series.usesSlenderModel()
                                ? "item/flying_sword_style_slender"
                                : "item/flying_sword_style_formal"));
                applyMaterialTextures(model, material);
            }
        }
    }

    private void applyMaterialTextures(ItemModelBuilder model, FlyingSwordMaterial material) {
        if (material == FlyingSwordMaterial.IRON) {
            model.texture("blade_light", modLoc("item/formal_iron_blade"));
            model.texture("blade_dark", modLoc("item/formal_iron_blade"));
            model.texture("edge", modLoc("item/formal_iron_edge"));
            model.texture("guard", modLoc("item/formal_iron_guard"));
            model.texture("grip", modLoc("item/formal_iron_grip"));
            model.texture("accent", modLoc("item/formal_iron_accent"));
            model.texture("particle", modLoc("item/formal_iron_blade"));
            return;
        }
        String[] textures = switch (material) {
            case WOODEN -> new String[]{"oak_planks", "stripped_oak_log_top", "dark_oak_planks",
                    "spruce_planks", "gold_block"};
            case STONE -> new String[]{"stone", "smooth_stone", "deepslate_tiles",
                    "dark_oak_planks", "lapis_block"};
            case IRON -> new String[]{"iron_block", "quartz_block_side", "polished_deepslate",
                    "dark_oak_planks", "diamond_block"};
            case GOLDEN -> new String[]{"gold_block", "raw_gold_block", "polished_blackstone",
                    "dark_oak_planks", "redstone_block"};
            case DIAMOND -> new String[]{"diamond_block", "prismarine_bricks", "deepslate_tiles",
                    "warped_planks", "sea_lantern"};
            case NETHERITE -> new String[]{"netherite_block", "crying_obsidian", "polished_blackstone",
                    "crimson_planks", "amethyst_block"};
        };
        model.texture("blade_light", mcLoc("block/" + textures[0]));
        model.texture("blade_dark", mcLoc("block/" + textures[0]));
        model.texture("edge", mcLoc("block/" + textures[1]));
        model.texture("guard", mcLoc("block/" + textures[2]));
        model.texture("grip", mcLoc("block/" + textures[3]));
        model.texture("accent", mcLoc("block/" + textures[4]));
        model.texture("particle", mcLoc("block/" + textures[0]));
    }
}
