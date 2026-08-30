package dev.yujiancraft.combat.technique;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

/** A semantic description of an item. It recommends behaviour but never hard-locks player choice. */
public enum ArtifactRole {
    BLADE("blade"),
    HEAVY("heavy"),
    RANGED("ranged"),
    SHIELD("shield"),
    TOOL("tool"),
    FISHING("fishing"),
    GENERIC("generic");

    private final String serializedName;

    ArtifactRole(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public String translationKey() {
        return "artifact_role.yujiancraft." + serializedName;
    }

    public ArtifactRole next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public TechniqueMode recommendedTechnique() {
        return switch (this) {
            case SHIELD -> TechniqueMode.GUARD;
            case TOOL -> TechniqueMode.TOOL_USE;
            case FISHING -> TechniqueMode.SPIRIT_FISHING;
            case HEAVY -> TechniqueMode.SWEEP;
            case RANGED -> TechniqueMode.SWORD_ARRAY;
            case BLADE, GENERIC -> TechniqueMode.PIERCE;
        };
    }

    public static ArtifactRole detect(ItemStack stack) {
        if (stack.isEmpty()) return GENERIC;
        if (stack.getItem() instanceof ShieldItem) return SHIELD;
        if (stack.getItem() instanceof FishingRodItem) return FISHING;
        if (stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem
                || stack.getItem() instanceof ProjectileWeaponItem || stack.getItem() instanceof TridentItem) {
            return RANGED;
        }
        if (stack.getItem() instanceof SwordItem) return BLADE;
        if (stack.getItem() instanceof AxeItem) return HEAVY;
        if (stack.getItem() instanceof DiggerItem) return TOOL;
        boolean[] hasAttackDamage = {false};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ATTACK_DAMAGE)) hasAttackDamage[0] = true;
        });
        if (hasAttackDamage[0]) return BLADE;
        return GENERIC;
    }

    public static ArtifactRole fromOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : GENERIC;
    }

    public static ArtifactRole fromName(String name) {
        for (ArtifactRole role : values()) {
            if (role.serializedName.equals(name)) return role;
        }
        return GENERIC;
    }
}
