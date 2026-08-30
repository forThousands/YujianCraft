package dev.yujiancraft.event;

import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.entity.SpiritTrialDummyEntity;
import dev.yujiancraft.registry.ModEntities;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@net.neoforged.fml.common.EventBusSubscriber(modid = YujianCraft.MOD_ID, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
public final class ModEntityAttributes {
    private ModEntityAttributes() {
    }

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.SPIRIT_TRIAL_DUMMY.get(), SpiritTrialDummyEntity.createAttributes().build());
    }
}
