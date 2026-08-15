package dev.swordflight.registry;

import dev.swordflight.Swordflight;
import dev.swordflight.menu.FlyingSwordWorkbenchMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, Swordflight.MOD_ID);

    public static final RegistryObject<MenuType<FlyingSwordWorkbenchMenu>> FLYING_SWORD_WORKBENCH =
            MENUS.register("flying_sword_workbench", () -> IForgeMenuType.create(FlyingSwordWorkbenchMenu::new));

    private ModMenus() {
    }

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
