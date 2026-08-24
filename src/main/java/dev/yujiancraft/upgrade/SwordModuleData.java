package dev.yujiancraft.upgrade;

import dev.yujiancraft.combat.SwordEffectEngine;
import dev.yujiancraft.wanxiang.WanxiangSwordData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public final class SwordModuleData {
    public static final String ROOT_TAG = "SwordModules";
    private static final String VIRTUAL_DURABILITY_TAG = "YujianCraftVirtualDurability";
    private static final FlyingSwordModule[] VISUAL_EFFECTS = {
            FlyingSwordModule.FLAME,
            FlyingSwordModule.LIGHTNING,
            FlyingSwordModule.POISON,
            FlyingSwordModule.EXPLOSION,
            FlyingSwordModule.ARROW_RAIN
    };

    private SwordModuleData() {
    }

    public static int getLevel(ItemStack sword, FlyingSwordModule module) {
        if (!sword.hasTag()) return 0;
        return getLevel(sword.getTag().getCompound(ROOT_TAG), module);
    }

    public static int getLevel(CompoundTag modules, FlyingSwordModule module) {
        return Math.max(0, Math.min(module.maxLevel(), modules.getInt(module.serializedName())));
    }

    public static void setLevel(ItemStack sword, FlyingSwordModule module, int level) {
        CompoundTag root = sword.getOrCreateTag();
        CompoundTag modules = root.getCompound(ROOT_TAG);
        int safeLevel = Math.max(0, Math.min(module.maxLevel(), level));
        if (safeLevel == 0) modules.remove(module.serializedName());
        else modules.putInt(module.serializedName(), safeLevel);
        if (modules.isEmpty()) root.remove(ROOT_TAG);
        else root.put(ROOT_TAG, modules);
        if (module == FlyingSwordModule.UNBREAKABLE) {
            if (safeLevel > 0) root.putBoolean("Unbreakable", true);
            else root.remove("Unbreakable");
        }
    }

    public static void setLevelPreservingDurability(ItemStack sword, FlyingSwordModule module, int level) {
        int oldModuleLevel = getLevel(sword, module);
        int oldMaximum = sword.getMaxDamage();
        int oldDamage = sword.getDamageValue();
        setLevel(sword, module, level);
        if (module == FlyingSwordModule.DURABILITY && WanxiangSwordData.isTempered(sword)) {
            int oldBonus = SwordEffectEngine.durabilityBonus(oldModuleLevel);
            int newBonus = SwordEffectEngine.durabilityBonus(getLevel(sword, module));
            int oldRemaining = sword.hasTag() && sword.getTag().contains(VIRTUAL_DURABILITY_TAG)
                    ? sword.getTag().getInt(VIRTUAL_DURABILITY_TAG) : oldBonus;
            if (newBonus <= 0) sword.getOrCreateTag().remove(VIRTUAL_DURABILITY_TAG);
            else {
                int newRemaining = oldBonus <= 0 ? newBonus
                        : (int) Math.round(oldRemaining * (double) newBonus / oldBonus);
                sword.getOrCreateTag().putInt(VIRTUAL_DURABILITY_TAG,
                        Math.max(0, Math.min(newBonus, newRemaining)));
            }
            return;
        }
        if (module == FlyingSwordModule.DURABILITY && oldMaximum > 0) {
            int newMaximum = sword.getMaxDamage();
            int scaledDamage = (int) Math.round(oldDamage * (double) newMaximum / oldMaximum);
            sword.setDamageValue(Math.max(0, Math.min(Math.max(0, newMaximum - 1), scaledDamage)));
        }
    }

    /** Returns the durability cost that still has to be applied to the underlying foreign item. */
    public static int consumeVirtualDurability(ItemStack sword, int requestedCost) {
        int cost = Math.max(0, requestedCost);
        if (cost == 0 || !WanxiangSwordData.isTempered(sword)) return cost;
        int maximum = SwordEffectEngine.durabilityBonus(getLevel(sword, FlyingSwordModule.DURABILITY));
        if (maximum <= 0) return cost;
        CompoundTag root = sword.getOrCreateTag();
        int remaining = root.contains(VIRTUAL_DURABILITY_TAG)
                ? Math.max(0, Math.min(maximum, root.getInt(VIRTUAL_DURABILITY_TAG))) : maximum;
        int absorbed = Math.min(remaining, cost);
        remaining -= absorbed;
        root.putInt(VIRTUAL_DURABILITY_TAG, remaining);
        return cost - absorbed;
    }

    public static int virtualDurabilityRemaining(ItemStack sword) {
        if (!WanxiangSwordData.isTempered(sword)) return 0;
        int maximum = SwordEffectEngine.durabilityBonus(getLevel(sword, FlyingSwordModule.DURABILITY));
        if (maximum <= 0) return 0;
        return sword.hasTag() && sword.getTag().contains(VIRTUAL_DURABILITY_TAG)
                ? Math.max(0, Math.min(maximum, sword.getTag().getInt(VIRTUAL_DURABILITY_TAG))) : maximum;
    }

    public static CompoundTag copyModules(ItemStack sword) {
        return sword.hasTag() ? sword.getTag().getCompound(ROOT_TAG).copy() : new CompoundTag();
    }

    /** Removes every installed Yujian core. Vanilla and third-party enchantments are untouched. */
    public static void clearAll(ItemStack sword) {
        if (!sword.hasTag()) return;
        int oldMaximum = sword.getMaxDamage();
        int oldDamage = sword.getDamageValue();
        CompoundTag root = sword.getTag();
        boolean moduleGrantedUnbreakable = getLevel(sword, FlyingSwordModule.UNBREAKABLE) > 0;
        root.remove(ROOT_TAG);
        root.remove(VIRTUAL_DURABILITY_TAG);
        if (moduleGrantedUnbreakable) root.remove("Unbreakable");
        if (root.isEmpty()) sword.setTag(null);
        int newMaximum = sword.getMaxDamage();
        if (oldMaximum > 0 && newMaximum > 0) {
            int scaledDamage = (int) Math.round(oldDamage * (double) newMaximum / oldMaximum);
            sword.setDamageValue(Math.max(0, Math.min(Math.max(0, newMaximum - 1), scaledDamage)));
        }
    }

    public static boolean hasAnyEffect(CompoundTag modules) {
        for (FlyingSwordModule module : FlyingSwordModule.values()) {
            if (module.category() == ModuleCategory.EFFECT && getLevel(modules, module) > 0) return true;
        }
        return false;
    }

    /** Packs the five effect-module levels into one synced integer (three bits per module). */
    public static int packVisualEffects(CompoundTag modules) {
        int packed = 0;
        for (int index = 0; index < VISUAL_EFFECTS.length; index++) {
            packed |= (getLevel(modules, VISUAL_EFFECTS[index]) & 0x7) << (index * 3);
        }
        return packed;
    }

    public static int visualEffectLevel(int packed, FlyingSwordModule module) {
        for (int index = 0; index < VISUAL_EFFECTS.length; index++) {
            if (VISUAL_EFFECTS[index] == module) return packed >> (index * 3) & 0x7;
        }
        return 0;
    }
}
