package dev.yujiancraft;

import dev.yujiancraft.registry.ModEntities;
import dev.yujiancraft.registry.ModItems;
import dev.yujiancraft.network.ModNetwork;
import dev.yujiancraft.config.SwordBalanceConfig;
import dev.yujiancraft.config.EffectBalanceConfig;
import dev.yujiancraft.registry.ModBlockEntities;
import dev.yujiancraft.registry.ModBlocks;
import dev.yujiancraft.registry.ModEffects;
import dev.yujiancraft.registry.ModMenus;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(YujianCraft.MOD_ID)
public final class YujianCraft {
    public static final String MOD_ID = "yujiancraft";

    public YujianCraft(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModEntities.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenus.register(modBus);
        ModEffects.register(modBus);
        ModNetwork.register();
        context.registerConfig(ModConfig.Type.SERVER, SwordBalanceConfig.SPEC);
        context.registerConfig(ModConfig.Type.SERVER, EffectBalanceConfig.SPEC, "yujiancraft-effects-server.toml");
    }
}
