package dev.yujiancraft.registry;

import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.block.FlyingSwordWorkbenchBlock;
import dev.yujiancraft.block.SpiritTemperingTableBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.util.valueproviders.UniformInt;
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
    public static final RegistryObject<Block> SPIRIT_TEMPERING_TABLE = BLOCKS.register("spirit_tempering_table",
            () -> new SpiritTemperingTableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE).strength(4.0F).sound(SoundType.AMETHYST)));
    public static final RegistryObject<Block> SPIRIT_ORE = BLOCKS.register("spirit_ore",
            () -> new DropExperienceBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE).strength(3.2F, 3.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.STONE), UniformInt.of(2, 5)));
    public static final RegistryObject<Block> DEEPSLATE_SPIRIT_ORE = BLOCKS.register("deepslate_spirit_ore",
            () -> new DropExperienceBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE).strength(4.8F, 3.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE), UniformInt.of(2, 5)));

    private ModBlocks() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
