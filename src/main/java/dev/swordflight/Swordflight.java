package dev.swordflight;

import dev.swordflight.registry.ModEntities;
import dev.swordflight.registry.ModItems;
import dev.swordflight.network.ModNetwork;
import dev.swordflight.config.SwordBalanceConfig;
import dev.swordflight.config.EffectBalanceConfig;
import dev.swordflight.registry.ModBlockEntities;
import dev.swordflight.registry.ModBlocks;
import dev.swordflight.registry.ModEffects;
import dev.swordflight.registry.ModMenus;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Swordflight.MOD_ID)
public final class Swordflight {
    public static final String MOD_ID = "swordflight";

    public Swordflight(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModEntities.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenus.register(modBus);
        ModEffects.register(modBus);
        ModNetwork.register();
        context.registerConfig(ModConfig.Type.SERVER, SwordBalanceConfig.SPEC);
        context.registerConfig(ModConfig.Type.SERVER, EffectBalanceConfig.SPEC, "swordflight-effects-server.toml");
    }
}
