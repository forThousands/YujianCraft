package dev.yujiancraft.registry;

import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.effect.SwordBurnEffect;
import dev.yujiancraft.effect.SwordPoisonEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, YujianCraft.MOD_ID);

    public static final RegistryObject<MobEffect> SWORD_BURN = EFFECTS.register("sword_burn", SwordBurnEffect::new);
    public static final RegistryObject<MobEffect> SWORD_POISON = EFFECTS.register("sword_poison", SwordPoisonEffect::new);

    private ModEffects() {
    }

    public static void register(IEventBus bus) {
        EFFECTS.register(bus);
    }
}
