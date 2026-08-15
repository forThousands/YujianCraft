package dev.swordflight.material;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum FlyingSwordMaterial {
    WOODEN("wooden", Items.WOODEN_SWORD, Items.OAK_PLANKS, 384, 4.0D, 0.90D, 0xD49A55),
    STONE("stone", Items.STONE_SWORD, Items.COBBLESTONE, 768, 5.0D, 0.95D, 0xAEB8C4),
    IRON("iron", Items.IRON_SWORD, Items.IRON_INGOT, 1536, 6.0D, 1.00D, 0xE7F2FF),
    GOLDEN("golden", Items.GOLDEN_SWORD, Items.GOLD_INGOT, 512, 4.0D, 1.15D, 0xFFD34F),
    DIAMOND("diamond", Items.DIAMOND_SWORD, Items.DIAMOND, 3072, 7.0D, 1.05D, 0x43F2E6),
    NETHERITE("netherite", Items.NETHERITE_SWORD, Items.NETHERITE_INGOT, 4096, 8.0D, 1.10D, 0xAF70FF);

    private final String serializedName;
    private final Item vanillaSword;
    private final Item repairItem;
    private final int durability;
    private final double defaultDamage;
    private final double defaultFlightSpeed;
    private final int glowColor;

    FlyingSwordMaterial(String serializedName, Item vanillaSword, Item repairItem, int durability,
                        double defaultDamage, double defaultFlightSpeed, int glowColor) {
        this.serializedName = serializedName;
        this.vanillaSword = vanillaSword;
        this.repairItem = repairItem;
        this.durability = durability;
        this.defaultDamage = defaultDamage;
        this.defaultFlightSpeed = defaultFlightSpeed;
        this.glowColor = glowColor;
    }

    public String serializedName() { return serializedName; }
    public String itemId() { return serializedName + "_flying_sword"; }
    public Item vanillaSword() { return vanillaSword; }
    public Item repairItem() { return repairItem; }
    public int durability() { return durability; }
    public double defaultDamage() { return defaultDamage; }
    public double defaultFlightSpeed() { return defaultFlightSpeed; }
    public int glowColor() { return glowColor; }
    public String translationKey() { return "material.swordflight." + serializedName; }

    public boolean isRepairIngredient(ItemStack stack) {
        return this == WOODEN ? stack.is(ItemTags.PLANKS) : stack.is(repairItem);
    }

    public static FlyingSwordMaterial fromOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : IRON;
    }
}
