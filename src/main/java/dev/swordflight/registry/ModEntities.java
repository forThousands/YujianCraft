package dev.swordflight.registry;

import dev.swordflight.Swordflight;
import dev.swordflight.entity.FlyingSwordEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Swordflight.MOD_ID);

    public static final RegistryObject<EntityType<FlyingSwordEntity>> FLYING_SWORD = ENTITIES.register(
            "flying_sword",
            () -> EntityType.Builder.<FlyingSwordEntity>of(FlyingSwordEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.2F)
                    .clientTrackingRange(512)
                    .updateInterval(1)
                    .build(Swordflight.MOD_ID + ":flying_sword")
    );

    private ModEntities() {
    }

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }
}
