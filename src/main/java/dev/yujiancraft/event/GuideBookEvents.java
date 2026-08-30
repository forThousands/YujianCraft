package dev.yujiancraft.event;

import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@net.neoforged.fml.common.EventBusSubscriber(modid = YujianCraft.MOD_ID)
public final class GuideBookEvents {
    private static final String RECEIVED_GUIDE = "YujianCraftReceivedGuide";

    private GuideBookEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(RECEIVED_GUIDE)) return;
        ItemStack guide = ModItems.YUJIAN_GUIDE.get().getDefaultInstance();
        if (!player.getInventory().add(guide)) player.drop(guide, false);
        player.displayClientMessage(Component.translatable("message.yujiancraft.guide_received"), false);
        persisted.putBoolean(RECEIVED_GUIDE, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }
}
