package dev.yujiancraft.wanxiang;

import dev.yujiancraft.combat.SwordEffectEngine;
import dev.yujiancraft.upgrade.SwordModuleData;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Set;

/** Keeps melee damage, tooltip damage and flying-sword piercing damage on one formula. */
public final class FlyingSwordDamage {
    /**
     * Vanilla recognises this UUID as the weapon's base attack contribution. Using a custom UUID
     * makes the tooltip describe the value as an extra "+N" bonus even though the combat result is
     * already correct.
     */
    // Item.BASE_ATTACK_DAMAGE_UUID is protected in Minecraft 1.20.1, so the shared vanilla UUID is
    // declared here for the generic item-attribute event (including non-SwordItem Wanxiang items).
    public static final ResourceLocation PIERCE_DAMAGE_MODIFIER_ID = Item.BASE_ATTACK_DAMAGE_ID;
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
                        + SwordEffectEngine.damageBonus(SwordModuleData.copyModules(stack)), null);
    }

    /**
     * Calculates the attack value as though {@code stack} were in the main hand. Current-hand item
     * modifiers are replaced, while potion, accessory and other living-entity modifiers remain.
     */
    public static double currentDamage(LivingEntity wielder, ItemStack stack, double effectiveBaseDamage) {
        return currentDamage(wielder, stack, effectiveBaseDamage, null);
    }

    public static double currentDamage(LivingEntity wielder, ItemStack stack, double effectiveBaseDamage,
                                       LivingEntity target) {
        double rawBaseDamage = finiteNonNegative(effectiveBaseDamage
                - WanxiangSwordData.enchantmentDamageBaseline(stack));
        if (wielder == null) return rawBaseDamage;
        AttributeInstance attack = wielder.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack == null) return applyEnchantments(wielder, target, stack, rawBaseDamage);

        Set<ResourceLocation> equippedItemModifiers = new HashSet<>();
        wielder.getMainHandItem().forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ATTACK_DAMAGE)) equippedItemModifiers.add(modifier.id());
        });

        double base = finite(attack.getBaseValue(), 1.0D);
        double addition = 0.0D;
        double multiplyBase = 0.0D;
        double multiplyTotal = 1.0D;
        for (AttributeModifier modifier : attack.getModifiers()) {
            if (equippedItemModifiers.contains(modifier.id())) continue;
            double amount = finite(modifier.amount(), 0.0D);
            switch (modifier.operation()) {
                case ADD_VALUE -> addition += amount;
                case ADD_MULTIPLIED_BASE -> multiplyBase += amount;
                case ADD_MULTIPLIED_TOTAL -> multiplyTotal *= 1.0D + amount;
            }
        }

        // The flying sword contributes an ADDITION modifier that turns the player's base 1 damage
        // into the configured per-pierce value. Other modifiers then follow vanilla's formula.
        addition += rawBaseDamage - 1.0D;
        double afterAddition = base + addition;
        return applyEnchantments(wielder, target, stack,
                (afterAddition + afterAddition * multiplyBase) * multiplyTotal);
    }

    private static double applyEnchantments(LivingEntity wielder, LivingEntity target,
                                            ItemStack stack, double damage) {
        if (target == null || !(target.level() instanceof ServerLevel level)) return finiteNonNegative(damage);
        var source = wielder instanceof Player player
                ? target.damageSources().playerAttack(player) : target.damageSources().mobAttack(wielder);
        return finiteNonNegative(EnchantmentHelper.modifyDamage(level, stack, target, source, (float) damage));
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? Math.max(-LIMIT, Math.min(LIMIT, value)) : fallback;
    }

    private static double finiteNonNegative(double value) {
        return Math.max(0.0D, finite(value, 0.0D));
    }
}
