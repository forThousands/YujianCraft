package dev.yujiancraft.registry;

import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.effect.SwordBurnEffect;
import dev.yujiancraft.effect.SwordPoisonEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, YujianCraft.MOD_ID);

    public static final DeferredHolder<MobEffect, MobEffect> SWORD_BURN = EFFECTS.register("sword_burn", SwordBurnEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect> SWORD_POISON = EFFECTS.register("sword_poison", SwordPoisonEffect::new);

    private ModEffects() {
    }

    public static void register(IEventBus bus) {
        EFFECTS.register(bus);
    }
}
