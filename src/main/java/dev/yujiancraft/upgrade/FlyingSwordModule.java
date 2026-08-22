package dev.yujiancraft.upgrade;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum FlyingSwordModule {
    FLAME("flame", Items.BLAZE_POWDER, ModuleCategory.EFFECT, 1, 16, 64),
    LIGHTNING("lightning", Items.LIGHTNING_ROD, ModuleCategory.EFFECT, 1, 16, 64),
    POISON("poison", Items.POISONOUS_POTATO, ModuleCategory.EFFECT, 1, 16, 64),
    EXPLOSION("explosion", Items.GUNPOWDER, ModuleCategory.EFFECT, 1, 16, 64),
    ARROW_RAIN("arrow_rain", Items.ARROW, ModuleCategory.EFFECT, 1, 16, 64),
    DAMAGE("damage", Items.EMERALD, ModuleCategory.ATTRIBUTE, 1, 16, 64),
    DURABILITY("durability", Items.DIAMOND, ModuleCategory.ATTRIBUTE, 1, 16, 64),
    UNBREAKABLE("unbreakable", Items.NETHER_STAR, ModuleCategory.ATTRIBUTE, 1),
    WHITE_HOT("white_hot", Items.MAGMA_BLOCK, ModuleCategory.PRESENTATION, 1);

    private final String serializedName;
    private final Item ingredient;
    private final int maxLevel;
    private final ModuleCategory category;
    private final int[] levelCosts;

    FlyingSwordModule(String serializedName, Item ingredient, ModuleCategory category, int... levelCosts) {
        if (levelCosts.length == 0) throw new IllegalArgumentException("At least one module level is required");
        this.serializedName = serializedName;
        this.ingredient = ingredient;
        this.maxLevel = levelCosts.length;
        this.category = category;
        this.levelCosts = levelCosts.clone();
    }

    public String serializedName() { return serializedName; }
    public Item ingredient() { return ingredient; }
    public int maxLevel() { return maxLevel; }
    public ModuleCategory category() { return category; }
    public String translationKey() { return "module.yujiancraft." + serializedName; }
    public String descriptionKey() { return translationKey() + ".description"; }

    public boolean matches(ItemStack stack) {
        return stack.is(ingredient);
    }

    public int materialCountForLevel(int level) {
        int safeIndex = Math.max(0, Math.min(maxLevel - 1, level - 1));
        return levelCosts[safeIndex];
    }

    public int levelForAvailableCount(int count) {
        for (int index = levelCosts.length - 1; index >= 0; index--) {
            if (count >= levelCosts[index]) return index + 1;
        }
        return 0;
    }

    public static FlyingSwordModule fromOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : FLAME;
    }

    public static FlyingSwordModule fromIngredient(ItemStack stack) {
        for (FlyingSwordModule module : values()) if (module.matches(stack)) return module;
        return null;
    }
}
