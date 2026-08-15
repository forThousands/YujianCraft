package dev.swordflight.registry;

import dev.swordflight.Swordflight;
import dev.swordflight.item.FlyingSwordItem;
import dev.swordflight.material.FlyingSwordMaterial;
import dev.swordflight.visual.FlyingSwordSeries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Swordflight.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Swordflight.MOD_ID);

    private static final EnumMap<FlyingSwordMaterial, RegistryObject<Item>> MUTABLE_SWORDS =
            new EnumMap<>(FlyingSwordMaterial.class);
    private static final EnumMap<FlyingSwordMaterial, RegistryObject<Item>> MUTABLE_SPIRITFORGED_SWORDS =
            new EnumMap<>(FlyingSwordMaterial.class);
    public static final Map<FlyingSwordMaterial, RegistryObject<Item>> FLYING_SWORDS;
    public static final Map<FlyingSwordMaterial, RegistryObject<Item>> SPIRITFORGED_FLYING_SWORDS;
    public static final RegistryObject<Item> FLYING_SWORD_WORKBENCH = ITEMS.register("flying_sword_workbench",
            () -> new BlockItem(ModBlocks.FLYING_SWORD_WORKBENCH.get(), new Item.Properties()));

    static {
        for (FlyingSwordMaterial material : FlyingSwordMaterial.values()) {
            MUTABLE_SWORDS.put(material, ITEMS.register(material.itemId(),
                    () -> new FlyingSwordItem(material, FlyingSwordSeries.STANDARD,
                            new Item.Properties().stacksTo(1).durability(material.durability()))));
            MUTABLE_SPIRITFORGED_SWORDS.put(material,
                    ITEMS.register(FlyingSwordSeries.SPIRITFORGED.itemId(material),
                    () -> new FlyingSwordItem(material, FlyingSwordSeries.SPIRITFORGED,
                            new Item.Properties().stacksTo(1).durability(material.durability()))));
        }
        FLYING_SWORDS = Collections.unmodifiableMap(MUTABLE_SWORDS);
        SPIRITFORGED_FLYING_SWORDS = Collections.unmodifiableMap(MUTABLE_SPIRITFORGED_SWORDS);
    }

    public static final RegistryObject<Item> IRON_FLYING_SWORD = FLYING_SWORDS.get(FlyingSwordMaterial.IRON);

    public static final RegistryObject<CreativeModeTab> MAIN_TAB = CREATIVE_TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .title(Component.translatable("creativetab.swordflight.main"))
                    .icon(() -> getFlyingSword(FlyingSwordMaterial.IRON).getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        for (FlyingSwordMaterial material : FlyingSwordMaterial.values()) {
                            output.accept(getFlyingSword(material));
                        }
                        for (FlyingSwordMaterial material : FlyingSwordMaterial.values()) {
                            output.accept(getFlyingSword(material, FlyingSwordSeries.SPIRITFORGED));
                        }
                        output.accept(FLYING_SWORD_WORKBENCH.get());
                    })
                    .build()
    );

    private ModItems() {
    }

    public static Item getFlyingSword(FlyingSwordMaterial material) {
        return getFlyingSword(material, FlyingSwordSeries.STANDARD);
    }

    public static Item getFlyingSword(FlyingSwordMaterial material, FlyingSwordSeries series) {
        return (series == FlyingSwordSeries.SPIRITFORGED
                ? SPIRITFORGED_FLYING_SWORDS : FLYING_SWORDS).get(material).get();
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        CREATIVE_TABS.register(bus);
    }
}
