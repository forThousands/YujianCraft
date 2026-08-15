package dev.swordflight.data;

import dev.swordflight.material.FlyingSwordMaterial;
import dev.swordflight.registry.ModItems;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;

import java.util.function.Consumer;

public final class ModRecipeProvider extends RecipeProvider {
    public ModRecipeProvider(PackOutput output) {
        super(output);
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> output) {
        for (FlyingSwordMaterial material : FlyingSwordMaterial.values()) {
            ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.getFlyingSword(material))
                    .pattern("DDD")
                    .pattern("DSD")
                    .pattern("DDD")
                    .define('D', Items.DIAMOND)
                    .define('S', material.vanillaSword())
                    .unlockedBy("has_" + material.serializedName() + "_sword", has(material.vanillaSword()))
                    .save(output);
        }
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, ModItems.FLYING_SWORD_WORKBENCH.get())
                .pattern("DDD")
                .pattern("DCD")
                .pattern("DDD")
                .define('D', Items.DIAMOND)
                .define('C', Items.CRAFTING_TABLE)
                .unlockedBy("has_crafting_table", has(Items.CRAFTING_TABLE))
                .save(output);
    }
}
