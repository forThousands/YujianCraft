package dev.yujiancraft.wanxiang;

import com.mojang.brigadier.Command;
import dev.yujiancraft.YujianCraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = YujianCraft.MOD_ID)
public final class WanxiangServerEvents {
    private WanxiangServerEvents() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        WanxiangWeaponCatalog.load(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        WanxiangWeaponCatalog.unload();
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("yujiancraft")
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            WanxiangWeaponCatalog.load(context.getSource().getServer());
                            context.getSource().sendSuccess(
                                    () -> Component.translatable("command.yujiancraft.reload.success"), true);
                            return Command.SINGLE_SUCCESS;
                        })));
    }
}
