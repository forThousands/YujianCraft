package dev.yujiancraft;

import dev.yujiancraft.registry.ModEntities;
import dev.yujiancraft.registry.ModItems;
import dev.yujiancraft.network.ModNetwork;
import dev.yujiancraft.config.SwordBalanceConfig;
import dev.yujiancraft.config.EffectBalanceConfig;
import dev.yujiancraft.config.TechniqueConfig;
import dev.yujiancraft.registry.ModBlockEntities;
import dev.yujiancraft.registry.ModBlocks;
import dev.yujiancraft.registry.ModEffects;
import dev.yujiancraft.registry.ModMenus;
import dev.yujiancraft.registry.ModDataComponents;
import dev.yujiancraft.registry.ModSounds;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

@Mod(YujianCraft.MOD_ID)
public final class YujianCraft {
    public static final String MOD_ID = "yujiancraft";

    public YujianCraft(IEventBus modBus, ModContainer modContainer) {
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModEntities.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenus.register(modBus);
        ModEffects.register(modBus);
        ModDataComponents.register(modBus);
        ModSounds.register(modBus);
        modBus.addListener(ModNetwork::register);
        modBus.addListener(YujianCraft::registerCapabilities);
        modContainer.registerConfig(ModConfig.Type.SERVER, SwordBalanceConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.SERVER, EffectBalanceConfig.SPEC,
                "yujiancraft-effects-server.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, TechniqueConfig.SPEC,
                "yujiancraft-techniques-server.toml");
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.FLYING_SWORD_WORKBENCH.get(), (blockEntity, side) -> blockEntity.inventory());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.SPIRIT_TEMPERING_TABLE.get(), (blockEntity, side) -> blockEntity.inventory());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.SPIRIT_REPLENISHING_TABLE.get(), (blockEntity, side) -> blockEntity.inventory());
    }
}
