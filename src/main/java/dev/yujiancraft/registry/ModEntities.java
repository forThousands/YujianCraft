package dev.yujiancraft.registry;

import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.entity.FlyingSwordEntity;
import dev.yujiancraft.entity.SpiritTrialDummyEntity;
import dev.yujiancraft.entity.SwordArrayQiEntity;
import dev.yujiancraft.entity.SwordArrayFieldEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, YujianCraft.MOD_ID);

    public static final RegistryObject<EntityType<FlyingSwordEntity>> FLYING_SWORD = ENTITIES.register(
            "flying_sword",
            () -> EntityType.Builder.<FlyingSwordEntity>of(FlyingSwordEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.2F)
                    .clientTrackingRange(512)
                    .updateInterval(1)
                    .build(YujianCraft.MOD_ID + ":flying_sword")
    );
    public static final RegistryObject<EntityType<SpiritTrialDummyEntity>> SPIRIT_TRIAL_DUMMY = ENTITIES.register(
            "spirit_trial_dummy",
            () -> EntityType.Builder.<SpiritTrialDummyEntity>of(SpiritTrialDummyEntity::new, MobCategory.MISC)
                    .sized(0.7F, 1.95F)
                    .clientTrackingRange(32)
                    .updateInterval(2)
                    .build(YujianCraft.MOD_ID + ":spirit_trial_dummy")
    );
    public static final RegistryObject<EntityType<SwordArrayQiEntity>> SWORD_ARRAY = ENTITIES.register(
            "sword_array",
            () -> EntityType.Builder.<SwordArrayQiEntity>of(SwordArrayQiEntity::new, MobCategory.MISC)
                    .sized(2.8F, 2.8F)
                    .clientTrackingRange(96)
                    .updateInterval(1)
                    .build(YujianCraft.MOD_ID + ":sword_array")
    );
    public static final RegistryObject<EntityType<SwordArrayFieldEntity>> SWORD_ARRAY_FIELD = ENTITIES.register(
            "sword_array_field",
            () -> EntityType.Builder.<SwordArrayFieldEntity>of(SwordArrayFieldEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .build(YujianCraft.MOD_ID + ":sword_array_field")
    );
    private ModEntities() {
    }

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }
}
