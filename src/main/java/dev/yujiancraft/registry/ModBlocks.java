package dev.yujiancraft.registry;

import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.block.FlyingSwordWorkbenchBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, YujianCraft.MOD_ID);

    public static final RegistryObject<Block> FLYING_SWORD_WORKBENCH = BLOCKS.register("flying_sword_workbench",
            () -> new FlyingSwordWorkbenchBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD).strength(3.5F).sound(SoundType.WOOD)));

    private ModBlocks() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
