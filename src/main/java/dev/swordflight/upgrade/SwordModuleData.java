package dev.swordflight.upgrade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public final class SwordModuleData {
    public static final String ROOT_TAG = "SwordModules";

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
        int oldMaximum = sword.getMaxDamage();
        int oldDamage = sword.getDamageValue();
        setLevel(sword, module, level);
        if (module == FlyingSwordModule.DURABILITY && oldMaximum > 0) {
            int newMaximum = sword.getMaxDamage();
            int scaledDamage = (int) Math.round(oldDamage * (double) newMaximum / oldMaximum);
            sword.setDamageValue(Math.max(0, Math.min(Math.max(0, newMaximum - 1), scaledDamage)));
        }
    }

    public static CompoundTag copyModules(ItemStack sword) {
        return sword.hasTag() ? sword.getTag().getCompound(ROOT_TAG).copy() : new CompoundTag();
    }

    public static boolean hasAnyEffect(CompoundTag modules) {
        for (FlyingSwordModule module : FlyingSwordModule.values()) {
            if (module.category() == ModuleCategory.EFFECT && getLevel(modules, module) > 0) return true;
        }
        return false;
    }
}
