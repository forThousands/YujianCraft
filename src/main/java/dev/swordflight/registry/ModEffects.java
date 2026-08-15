package dev.swordflight.registry;

import dev.swordflight.Swordflight;
import dev.swordflight.effect.SwordBurnEffect;
import dev.swordflight.effect.SwordPoisonEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Swordflight.MOD_ID);

    public static final RegistryObject<MobEffect> SWORD_BURN = EFFECTS.register("sword_burn", SwordBurnEffect::new);
    public static final RegistryObject<MobEffect> SWORD_POISON = EFFECTS.register("sword_poison", SwordPoisonEffect::new);

    private ModEffects() {
    }

    public static void register(IEventBus bus) {
        EFFECTS.register(bus);
    }
}
