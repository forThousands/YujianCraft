package dev.yujiancraft.registry;

import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.item.FlyingSwordItem;
import dev.yujiancraft.item.YujianGuideItem;
import dev.yujiancraft.material.FlyingSwordMaterial;
import dev.yujiancraft.visual.FlyingSwordSeries;
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
            DeferredRegister.create(ForgeRegistries.ITEMS, YujianCraft.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, YujianCraft.MOD_ID);

    private static final EnumMap<FlyingSwordMaterial, RegistryObject<Item>> MUTABLE_SWORDS =
            new EnumMap<>(FlyingSwordMaterial.class);
    private static final EnumMap<FlyingSwordMaterial, RegistryObject<Item>> MUTABLE_SPIRITFORGED_SWORDS =
            new EnumMap<>(FlyingSwordMaterial.class);
    public static final Map<FlyingSwordMaterial, RegistryObject<Item>> FLYING_SWORDS;
    public static final Map<FlyingSwordMaterial, RegistryObject<Item>> SPIRITFORGED_FLYING_SWORDS;
    /** Render-only creative-tab emblem; deliberately omitted from the tab contents and recipes. */
    public static final RegistryObject<Item> CREATIVE_TAB_ICON = ITEMS.register("creative_tab_icon",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> FLYING_SWORD_WORKBENCH = ITEMS.register("flying_sword_workbench",
            () -> new BlockItem(ModBlocks.FLYING_SWORD_WORKBENCH.get(), new Item.Properties()));
    public static final RegistryObject<Item> SPIRIT_TEMPERING_TABLE = ITEMS.register("spirit_tempering_table",
            () -> new BlockItem(ModBlocks.SPIRIT_TEMPERING_TABLE.get(), new Item.Properties()));
    public static final RegistryObject<Item> SPIRIT_ORE = ITEMS.register("spirit_ore",
            () -> new BlockItem(ModBlocks.SPIRIT_ORE.get(), new Item.Properties()));
    public static final RegistryObject<Item> DEEPSLATE_SPIRIT_ORE = ITEMS.register("deepslate_spirit_ore",
            () -> new BlockItem(ModBlocks.DEEPSLATE_SPIRIT_ORE.get(), new Item.Properties()));
    public static final RegistryObject<Item> SPIRIT_CRYSTAL = ITEMS.register("spirit_crystal",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> YUJIAN_GUIDE = ITEMS.register("yujian_guide",
            () -> new YujianGuideItem(new Item.Properties().stacksTo(1)));

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
                    .title(Component.translatable("creativetab.yujiancraft.main"))
                    .icon(() -> CREATIVE_TAB_ICON.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        for (FlyingSwordMaterial material : FlyingSwordMaterial.values()) {
                            output.accept(getFlyingSword(material));
                        }
                        for (FlyingSwordMaterial material : FlyingSwordMaterial.values()) {
                            output.accept(getFlyingSword(material, FlyingSwordSeries.SPIRITFORGED));
                        }
                        output.accept(FLYING_SWORD_WORKBENCH.get());
                        output.accept(SPIRIT_TEMPERING_TABLE.get());
                        output.accept(SPIRIT_ORE.get());
                        output.accept(DEEPSLATE_SPIRIT_ORE.get());
                        output.accept(SPIRIT_CRYSTAL.get());
                        output.accept(YUJIAN_GUIDE.get());
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
