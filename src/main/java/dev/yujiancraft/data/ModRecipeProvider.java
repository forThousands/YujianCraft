package dev.yujiancraft.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yujiancraft.YujianCraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Recipes are declared once in the visual control panel manifest and generated from that source. */
public final class ModRecipeProvider extends RecipeProvider {
    private static final Path DEFINITIONS = locateDefinitions();
    private static final char[] SYMBOLS = "ABCDEFGHI".toCharArray();

    public ModRecipeProvider(PackOutput output) {
        super(output);
    }

    private static Path locateDefinitions() {
        Path directory = Path.of("").toAbsolutePath().normalize();
        for (int depth = 0; directory != null && depth < 5; depth++, directory = directory.getParent()) {
            Path candidate = directory.resolve(Path.of("devtools", "control_panel", "recipes.json"));
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new IllegalStateException("Unable to locate devtools/control_panel/recipes.json from "
                + Path.of("").toAbsolutePath());
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> output) {
        try (Reader reader = Files.newBufferedReader(DEFINITIONS)) {
            JsonArray recipes = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("recipes");
            for (JsonElement element : recipes) generate(output, element.getAsJsonObject());
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Unable to generate Yujian Craft recipes from " + DEFINITIONS,
                    exception);
        }
    }

    private static void generate(Consumer<FinishedRecipe> output, JsonObject definition) {
        String recipeId = definition.get("id").getAsString();
        String type = definition.get("type").getAsString();
        RecipeCategory category = category(definition.get("category").getAsString());
        Item result = item(definition.get("result").getAsString());
        int count = definition.get("count").getAsInt();
        List<String> grid = new ArrayList<>(9);
        definition.getAsJsonArray("grid").forEach(element -> grid.add(element.getAsString()));
        Item unlockItem = item(grid.stream().filter(value -> !value.isBlank()).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(recipeId + " has no ingredients")));
        String criterion = "has_" + BuiltInRegistries.ITEM.getKey(unlockItem).getPath().replace('/', '_');
        ResourceLocation outputId = new ResourceLocation(YujianCraft.MOD_ID, recipeId);

        if ("shapeless".equals(type)) {
            ShapelessRecipeBuilder builder = ShapelessRecipeBuilder.shapeless(category, result, count);
            grid.stream().filter(value -> !value.isBlank()).map(ModRecipeProvider::item)
                    .forEach(builder::requires);
            builder.unlockedBy(criterion, has(unlockItem)).save(output, outputId);
            return;
        }

        List<List<String>> trimmed = trim(grid);
        Map<String, Character> symbols = new LinkedHashMap<>();
        for (List<String> row : trimmed) {
            for (String ingredient : row) {
                if (!ingredient.isBlank() && !symbols.containsKey(ingredient)) {
                    symbols.put(ingredient, SYMBOLS[symbols.size()]);
                }
            }
        }
        ShapedRecipeBuilder builder = ShapedRecipeBuilder.shaped(category, result, count);
        for (List<String> row : trimmed) {
            StringBuilder pattern = new StringBuilder();
            for (String ingredient : row) pattern.append(ingredient.isBlank() ? ' ' : symbols.get(ingredient));
            builder.pattern(pattern.toString());
        }
        symbols.forEach((ingredient, symbol) -> builder.define(symbol, item(ingredient)));
        builder.unlockedBy(criterion, has(unlockItem)).save(output, outputId);
    }

    private static List<List<String>> trim(List<String> grid) {
        int minRow = 3;
        int maxRow = -1;
        int minColumn = 3;
        int maxColumn = -1;
        for (int index = 0; index < grid.size(); index++) {
            if (grid.get(index).isBlank()) continue;
            int row = index / 3;
            int column = index % 3;
            minRow = Math.min(minRow, row);
            maxRow = Math.max(maxRow, row);
            minColumn = Math.min(minColumn, column);
            maxColumn = Math.max(maxColumn, column);
        }
        if (maxRow < 0) throw new IllegalArgumentException("A shaped recipe cannot be empty");
        List<List<String>> result = new ArrayList<>();
        for (int row = minRow; row <= maxRow; row++) {
            List<String> line = new ArrayList<>();
            for (int column = minColumn; column <= maxColumn; column++) line.add(grid.get(row * 3 + column));
            result.add(line);
        }
        return result;
    }

    private static RecipeCategory category(String value) {
        return switch (value) {
            case "combat" -> RecipeCategory.COMBAT;
            case "decorations" -> RecipeCategory.DECORATIONS;
            default -> RecipeCategory.MISC;
        };
    }

    private static Item item(String itemId) {
        ResourceLocation id = new ResourceLocation(itemId);
        return BuiltInRegistries.ITEM.getOptional(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown recipe item " + itemId));
    }
}
