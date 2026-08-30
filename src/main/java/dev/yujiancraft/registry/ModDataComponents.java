package dev.yujiancraft.registry;

import dev.yujiancraft.YujianCraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Persistent item state used by flying swords and tempered foreign tools. */
public final class ModDataComponents {
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, YujianCraft.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> SWORD_DATA =
            COMPONENTS.registerComponentType("sword_data", builder -> builder.persistent(CompoundTag.CODEC));

    private ModDataComponents() {
    }

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
    }
}
