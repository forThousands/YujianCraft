package dev.yujiancraft.registry;

import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.block.FlyingSwordWorkbenchBlock;
import dev.yujiancraft.block.SpiritTemperingTableBlock;
import dev.yujiancraft.block.SpiritReplenishingTableBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, YujianCraft.MOD_ID);

    public static final DeferredHolder<Block, Block> FLYING_SWORD_WORKBENCH = BLOCKS.register("flying_sword_workbench",
            () -> new FlyingSwordWorkbenchBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD).strength(3.5F).sound(SoundType.WOOD)));
    public static final DeferredHolder<Block, Block> SPIRIT_TEMPERING_TABLE = BLOCKS.register("spirit_tempering_table",
            () -> new SpiritTemperingTableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE).strength(4.0F).sound(SoundType.AMETHYST)));
    public static final DeferredHolder<Block, Block> SPIRIT_REPLENISHING_TABLE = BLOCKS.register("spirit_replenishing_table",
            () -> new SpiritReplenishingTableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN).strength(4.0F).sound(SoundType.AMETHYST)));
    public static final DeferredHolder<Block, Block> SPIRIT_ORE = BLOCKS.register("spirit_ore",
            () -> new DropExperienceBlock(UniformInt.of(2, 5), BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE).strength(3.2F, 3.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)));
    public static final DeferredHolder<Block, Block> DEEPSLATE_SPIRIT_ORE = BLOCKS.register("deepslate_spirit_ore",
            () -> new DropExperienceBlock(UniformInt.of(2, 5), BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE).strength(4.8F, 3.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE)));

    private ModBlocks() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
