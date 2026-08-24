package dev.yujiancraft.wanxiang;

import dev.yujiancraft.combat.SwordEffectEngine;
import dev.yujiancraft.upgrade.SwordModuleData;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Keeps melee damage, tooltip damage and flying-sword piercing damage on one formula. */
public final class FlyingSwordDamage {
    /**
     * Vanilla recognises this UUID as the weapon's base attack contribution. Using a custom UUID
     * makes the tooltip describe the value as an extra "+N" bonus even though the combat result is
     * already correct.
     */
    // Item.BASE_ATTACK_DAMAGE_UUID is protected in Minecraft 1.20.1, so the shared vanilla UUID is
    // declared here for the generic item-attribute event (including non-SwordItem Wanxiang items).
    public static final UUID PIERCE_DAMAGE_MODIFIER_ID =
            UUID.fromString("fa233e1c-4180-4865-b01b-bcce9785aca3");
    private static final double LIMIT = 1.0E9D;

    private FlyingSwordDamage() {
    }

    public static double itemBaseDamage(ItemStack stack) {
        double base = WanxiangSwordData.pierceDamage(stack);
        double moduleBonus = SwordEffectEngine.damageBonus(SwordModuleData.copyModules(stack));
        // Vanilla applies Sharpness/Smite/Bane after the ATTACK_DAMAGE attribute. A tempered sword
        // stores the effective value observed with its current enchantments, so remove the saved
        // enchantment baseline here and let vanilla add the live enchantment value exactly once.
        return finiteNonNegative(base + moduleBonus - WanxiangSwordData.enchantmentDamageBaseline(stack));
    }

    public static double currentDamage(LivingEntity wielder, ItemStack stack) {
        return currentDamage(wielder, stack,
                WanxiangSwordData.pierceDamage(stack)
                        + SwordEffectEngine.damageBonus(SwordModuleData.copyModules(stack)),
                MobType.UNDEFINED);
    }

    /**
     * Calculates the attack value as though {@code stack} were in the main hand. Current-hand item
     * modifiers are replaced, while potion, accessory and other living-entity modifiers remain.
     */
    public static double currentDamage(LivingEntity wielder, ItemStack stack, double effectiveBaseDamage) {
        return currentDamage(wielder, stack, effectiveBaseDamage, MobType.UNDEFINED);
    }

    public static double currentDamage(LivingEntity wielder, ItemStack stack, double effectiveBaseDamage,
                                       MobType targetType) {
        double rawBaseDamage = finiteNonNegative(effectiveBaseDamage
                - WanxiangSwordData.enchantmentDamageBaseline(stack));
        double enchantmentDamage = enchantmentDamage(stack, targetType);
        if (wielder == null) return finiteNonNegative(rawBaseDamage + enchantmentDamage);
        AttributeInstance attack = wielder.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack == null) return finiteNonNegative(rawBaseDamage + enchantmentDamage);

        Set<UUID> equippedItemModifiers = new HashSet<>();
        for (AttributeModifier modifier : wielder.getMainHandItem()
                .getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE)) {
            equippedItemModifiers.add(modifier.getId());
        }

        double base = finite(attack.getBaseValue(), 1.0D);
        double addition = 0.0D;
        double multiplyBase = 0.0D;
        double multiplyTotal = 1.0D;
        for (AttributeModifier modifier : attack.getModifiers()) {
            if (equippedItemModifiers.contains(modifier.getId())) continue;
            double amount = finite(modifier.getAmount(), 0.0D);
            switch (modifier.getOperation()) {
                case ADDITION -> addition += amount;
                case MULTIPLY_BASE -> multiplyBase += amount;
                case MULTIPLY_TOTAL -> multiplyTotal *= 1.0D + amount;
            }
        }

        // The flying sword contributes an ADDITION modifier that turns the player's base 1 damage
        // into the configured per-pierce value. Other modifiers then follow vanilla's formula.
        addition += rawBaseDamage - 1.0D;
        double afterAddition = base + addition;
        return finiteNonNegative((afterAddition + afterAddition * multiplyBase) * multiplyTotal
                + enchantmentDamage);
    }

    public static double enchantmentDamage(ItemStack stack, MobType targetType) {
        return finiteNonNegative(EnchantmentHelper.getDamageBonus(stack,
                targetType == null ? MobType.UNDEFINED : targetType));
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? Math.max(-LIMIT, Math.min(LIMIT, value)) : fallback;
    }

    private static double finiteNonNegative(double value) {
        return Math.max(0.0D, finite(value, 0.0D));
    }
}
