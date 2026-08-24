package dev.yujiancraft.event;

import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.entity.SpiritTrialDummyEntity;
import dev.yujiancraft.registry.ModEntities;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = YujianCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEntityAttributes {
    private ModEntityAttributes() {
    }

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.SPIRIT_TRIAL_DUMMY.get(), SpiritTrialDummyEntity.createAttributes().build());
    }
}
