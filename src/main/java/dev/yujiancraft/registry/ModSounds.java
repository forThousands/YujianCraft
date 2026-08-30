package dev.yujiancraft.registry;

import dev.yujiancraft.YujianCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Spatial sword-array sounds with an authored range independent of playback volume. */
public final class ModSounds {
    private static final float SWORD_ARRAY_CUE_RANGE = 64.0F;

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, YujianCraft.MOD_ID);

    public static final RegistryObject<SoundEvent> SWORD_ARRAY_BEACON_ACTIVATE =
            registerFixedRange("sword_array.beacon_activate", SWORD_ARRAY_CUE_RANGE);
    public static final RegistryObject<SoundEvent> SWORD_ARRAY_AMETHYST_RESONATE =
            registerFixedRange("sword_array.amethyst_resonate", SWORD_ARRAY_CUE_RANGE);
    public static final RegistryObject<SoundEvent> SWORD_ARRAY_RIPTIDE_DESCENT =
            registerFixedRange("sword_array.riptide_descent", SWORD_ARRAY_CUE_RANGE);
    public static final RegistryObject<SoundEvent> SWORD_ARRAY_BEACON_DESCENT =
            registerFixedRange("sword_array.beacon_descent", SWORD_ARRAY_CUE_RANGE);

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> registerFixedRange(String name, float range) {
        ResourceLocation id = new ResourceLocation(YujianCraft.MOD_ID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createFixedRangeEvent(id, range));
    }

    public static void register(IEventBus bus) {
        SOUND_EVENTS.register(bus);
    }
}
