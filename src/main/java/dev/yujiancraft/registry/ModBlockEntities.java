package dev.yujiancraft.registry;

import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.blockentity.FlyingSwordWorkbenchBlockEntity;
import dev.yujiancraft.blockentity.SpiritTemperingTableBlockEntity;
import dev.yujiancraft.blockentity.SpiritReplenishingTableBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, YujianCraft.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FlyingSwordWorkbenchBlockEntity>> FLYING_SWORD_WORKBENCH =
            BLOCK_ENTITIES.register("flying_sword_workbench",
                    () -> BlockEntityType.Builder.of(FlyingSwordWorkbenchBlockEntity::new,
                            ModBlocks.FLYING_SWORD_WORKBENCH.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpiritTemperingTableBlockEntity>> SPIRIT_TEMPERING_TABLE =
            BLOCK_ENTITIES.register("spirit_tempering_table",
                    () -> BlockEntityType.Builder.of(SpiritTemperingTableBlockEntity::new,
                            ModBlocks.SPIRIT_TEMPERING_TABLE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpiritReplenishingTableBlockEntity>> SPIRIT_REPLENISHING_TABLE =
            BLOCK_ENTITIES.register("spirit_replenishing_table",
                    () -> BlockEntityType.Builder.of(SpiritReplenishingTableBlockEntity::new,
                            ModBlocks.SPIRIT_REPLENISHING_TABLE.get()).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
