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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, YujianCraft.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, YujianCraft.MOD_ID);

    private static final EnumMap<FlyingSwordSeries, Map<FlyingSwordMaterial, DeferredHolder<Item, Item>>> SWORD_SERIES =
            new EnumMap<>(FlyingSwordSeries.class);
    public static final Map<FlyingSwordMaterial, DeferredHolder<Item, Item>> FLYING_SWORDS;
    public static final Map<FlyingSwordMaterial, DeferredHolder<Item, Item>> SPIRITFORGED_FLYING_SWORDS;
    public static final Map<FlyingSwordMaterial, DeferredHolder<Item, Item>> LUMINOUS_FLYING_SWORDS;
    public static final Map<FlyingSwordMaterial, DeferredHolder<Item, Item>> CONDENSED_FLYING_SWORDS;
    /** Render-only creative-tab emblem; deliberately omitted from the tab contents and recipes. */
    public static final DeferredHolder<Item, Item> CREATIVE_TAB_ICON = ITEMS.register("creative_tab_icon",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> FLYING_SWORD_WORKBENCH = ITEMS.register("flying_sword_workbench",
            () -> new BlockItem(ModBlocks.FLYING_SWORD_WORKBENCH.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> SPIRIT_TEMPERING_TABLE = ITEMS.register("spirit_tempering_table",
            () -> new BlockItem(ModBlocks.SPIRIT_TEMPERING_TABLE.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> SPIRIT_REPLENISHING_TABLE = ITEMS.register("spirit_replenishing_table",
            () -> new BlockItem(ModBlocks.SPIRIT_REPLENISHING_TABLE.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> SPIRIT_ORE = ITEMS.register("spirit_ore",
            () -> new BlockItem(ModBlocks.SPIRIT_ORE.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> DEEPSLATE_SPIRIT_ORE = ITEMS.register("deepslate_spirit_ore",
            () -> new BlockItem(ModBlocks.DEEPSLATE_SPIRIT_ORE.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> SPIRIT_CRYSTAL = ITEMS.register("spirit_crystal",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> YUJIAN_GUIDE = ITEMS.register("yujian_guide",
            () -> new YujianGuideItem(new Item.Properties().stacksTo(1)));

    static {
        for (FlyingSwordSeries series : FlyingSwordSeries.values()) {
            EnumMap<FlyingSwordMaterial, DeferredHolder<Item, Item>> swords =
                    new EnumMap<>(FlyingSwordMaterial.class);
            for (FlyingSwordMaterial material : FlyingSwordMaterial.values()) {
                swords.put(material, ITEMS.register(series.itemId(material),
                        () -> new FlyingSwordItem(material, series,
                                new Item.Properties().stacksTo(1).durability(material.durability()))));
            }
            SWORD_SERIES.put(series, Collections.unmodifiableMap(swords));
        }
        FLYING_SWORDS = SWORD_SERIES.get(FlyingSwordSeries.STANDARD);
        SPIRITFORGED_FLYING_SWORDS = SWORD_SERIES.get(FlyingSwordSeries.SPIRITFORGED);
        LUMINOUS_FLYING_SWORDS = SWORD_SERIES.get(FlyingSwordSeries.LUMINOUS);
        CONDENSED_FLYING_SWORDS = SWORD_SERIES.get(FlyingSwordSeries.CONDENSED);
    }

    public static final DeferredHolder<Item, Item> IRON_FLYING_SWORD = FLYING_SWORDS.get(FlyingSwordMaterial.IRON);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = CREATIVE_TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .title(Component.translatable("creativetab.yujiancraft.main"))
                    .icon(() -> CREATIVE_TAB_ICON.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        for (FlyingSwordSeries series : FlyingSwordSeries.values()) {
                            for (FlyingSwordMaterial material : FlyingSwordMaterial.values()) {
                                output.accept(getFlyingSword(material, series));
                            }
                        }
                        output.accept(FLYING_SWORD_WORKBENCH.get());
                        output.accept(SPIRIT_TEMPERING_TABLE.get());
                        output.accept(SPIRIT_REPLENISHING_TABLE.get());
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
        Map<FlyingSwordMaterial, DeferredHolder<Item, Item>> swords = SWORD_SERIES.get(series);
        if (swords == null) swords = FLYING_SWORDS;
        return swords.get(material).get();
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        CREATIVE_TABS.register(bus);
    }
}
