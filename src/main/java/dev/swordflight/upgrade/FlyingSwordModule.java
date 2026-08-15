package dev.swordflight.upgrade;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum FlyingSwordModule {
    FLAME("flame", Items.BLAZE_POWDER, 3, ModuleCategory.EFFECT),
    LIGHTNING("lightning", Items.LIGHTNING_ROD, 3, ModuleCategory.EFFECT),
    POISON("poison", Items.POISONOUS_POTATO, 3, ModuleCategory.EFFECT),
    EXPLOSION("explosion", Items.GUNPOWDER, 3, ModuleCategory.EFFECT),
    ARROW_RAIN("arrow_rain", Items.ARROW, 3, ModuleCategory.EFFECT),
    DAMAGE("damage", Items.EMERALD, 3, ModuleCategory.ATTRIBUTE),
    DURABILITY("durability", Items.DIAMOND, 3, ModuleCategory.ATTRIBUTE),
    UNBREAKABLE("unbreakable", Items.NETHER_STAR, 1, ModuleCategory.ATTRIBUTE);

    private final String serializedName;
    private final Item ingredient;
    private final int maxLevel;
    private final ModuleCategory category;

    FlyingSwordModule(String serializedName, Item ingredient, int maxLevel, ModuleCategory category) {
        this.serializedName = serializedName;
        this.ingredient = ingredient;
        this.maxLevel = maxLevel;
        this.category = category;
    }

    public String serializedName() { return serializedName; }
    public Item ingredient() { return ingredient; }
    public int maxLevel() { return maxLevel; }
    public ModuleCategory category() { return category; }
    public String translationKey() { return "module.swordflight." + serializedName; }
    public String descriptionKey() { return translationKey() + ".description"; }

    public boolean matches(ItemStack stack) {
        return stack.is(ingredient);
    }

    public int materialCountForLevel(int level) {
        if (maxLevel == 1) return 1;
        return switch (Math.max(1, Math.min(3, level))) {
            case 1 -> 1;
            case 2 -> 16;
            default -> 64;
        };
    }

    public int levelForAvailableCount(int count) {
        if (count < 1) return 0;
        if (maxLevel == 1) return 1;
        if (count >= 64) return 3;
        if (count >= 16) return 2;
        return 1;
    }

    public static FlyingSwordModule fromOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : FLAME;
    }

    public static FlyingSwordModule fromIngredient(ItemStack stack) {
        for (FlyingSwordModule module : values()) if (module.matches(stack)) return module;
        return null;
    }
}
