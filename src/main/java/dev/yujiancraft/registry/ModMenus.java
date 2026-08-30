package dev.yujiancraft.registry;

import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.menu.FlyingSwordWorkbenchMenu;
import dev.yujiancraft.menu.SpiritTemperingMenu;
import dev.yujiancraft.menu.SpiritReplenishingMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, YujianCraft.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<FlyingSwordWorkbenchMenu>> FLYING_SWORD_WORKBENCH =
            MENUS.register("flying_sword_workbench", () -> IMenuTypeExtension.create(FlyingSwordWorkbenchMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<SpiritTemperingMenu>> SPIRIT_TEMPERING_TABLE =
            MENUS.register("spirit_tempering_table", () -> IMenuTypeExtension.create(SpiritTemperingMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<SpiritReplenishingMenu>> SPIRIT_REPLENISHING_TABLE =
            MENUS.register("spirit_replenishing_table", () -> IMenuTypeExtension.create(SpiritReplenishingMenu::new));

    private ModMenus() {
    }

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
