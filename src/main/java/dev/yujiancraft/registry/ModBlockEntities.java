package dev.yujiancraft.registry;

import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.blockentity.FlyingSwordWorkbenchBlockEntity;
import dev.yujiancraft.blockentity.SpiritTemperingTableBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, YujianCraft.MOD_ID);

    public static final RegistryObject<BlockEntityType<FlyingSwordWorkbenchBlockEntity>> FLYING_SWORD_WORKBENCH =
            BLOCK_ENTITIES.register("flying_sword_workbench",
                    () -> BlockEntityType.Builder.of(FlyingSwordWorkbenchBlockEntity::new,
                            ModBlocks.FLYING_SWORD_WORKBENCH.get()).build(null));
    public static final RegistryObject<BlockEntityType<SpiritTemperingTableBlockEntity>> SPIRIT_TEMPERING_TABLE =
            BLOCK_ENTITIES.register("spirit_tempering_table",
                    () -> BlockEntityType.Builder.of(SpiritTemperingTableBlockEntity::new,
                            ModBlocks.SPIRIT_TEMPERING_TABLE.get()).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
