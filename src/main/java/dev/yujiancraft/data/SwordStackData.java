package dev.yujiancraft.data;

import dev.yujiancraft.registry.ModDataComponents;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Copy-on-write access to YujianCraft's 1.21 item data component.
 *
 * <p>Callers must use {@link #update}; mutating the tag returned by {@link #copy}
 * alone intentionally does not change the stack.</p>
 */
public final class SwordStackData {
    private SwordStackData() {
    }

    public static CompoundTag copy(ItemStack stack) {
        CompoundTag value = stack.get(ModDataComponents.SWORD_DATA.get());
        return value == null ? new CompoundTag() : value.copy();
    }

    public static boolean has(ItemStack stack) {
        CompoundTag value = stack.get(ModDataComponents.SWORD_DATA.get());
        return value != null && !value.isEmpty();
    }

    public static void set(ItemStack stack, CompoundTag value) {
        if (value == null || value.isEmpty()) {
            stack.remove(ModDataComponents.SWORD_DATA.get());
        } else {
            stack.set(ModDataComponents.SWORD_DATA.get(), value.copy());
        }
    }

    public static void update(ItemStack stack, Consumer<CompoundTag> updater) {
        CompoundTag value = copy(stack);
        updater.accept(value);
        set(stack, value);
    }
}
