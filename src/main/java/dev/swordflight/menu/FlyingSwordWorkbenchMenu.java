package dev.swordflight.menu;

import dev.swordflight.blockentity.FlyingSwordWorkbenchBlockEntity;
import dev.swordflight.entity.FlyingSwordEntity;
import dev.swordflight.item.FlyingSwordItem;
import dev.swordflight.registry.ModBlocks;
import dev.swordflight.registry.ModMenus;
import dev.swordflight.upgrade.FlyingSwordModule;
import dev.swordflight.upgrade.SwordModuleData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

public final class FlyingSwordWorkbenchMenu extends AbstractContainerMenu {
    public static final int INSTALL_BUTTON = 100;
    public static final int REMOVE_BUTTON = 101;
    private final IItemHandler workbenchInventory;
    private final ContainerLevelAccess access;
    private int selectedModule;

    public FlyingSwordWorkbenchMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(containerId, playerInventory, getClientInventory(playerInventory, buffer), ContainerLevelAccess.NULL);
    }

    public FlyingSwordWorkbenchMenu(int containerId, Inventory playerInventory,
                                    FlyingSwordWorkbenchBlockEntity blockEntity) {
        this(containerId, playerInventory, blockEntity.inventory(),
                ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()));
    }

    private FlyingSwordWorkbenchMenu(int containerId, Inventory playerInventory, IItemHandler handler,
                                     ContainerLevelAccess access) {
        super(ModMenus.FLYING_SWORD_WORKBENCH.get(), containerId);
        this.workbenchInventory = handler;
        this.access = access;

        addSlot(new SlotItemHandler(handler, 0, 113, 32) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof FlyingSwordItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new SlotItemHandler(handler, 1, 113, 68) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return FlyingSwordModule.fromIngredient(stack) != null;
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 35 + column * 18, 151 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 35 + column * 18, 209));
        }

        addDataSlot(new DataSlot() {
            @Override
            public int get() { return selectedModule; }

            @Override
            public void set(int value) { selectedModule = value; }
        });
    }

    private static IItemHandler getClientInventory(Inventory inventory, FriendlyByteBuf buffer) {
        return inventory.player.level().getBlockEntity(buffer.readBlockPos())
                instanceof FlyingSwordWorkbenchBlockEntity blockEntity
                ? blockEntity.inventory() : new ItemStackHandler(2);
    }

    public FlyingSwordModule selectedModule() {
        return FlyingSwordModule.fromOrdinal(selectedModule);
    }

    public int selectedLevel() {
        return SwordModuleData.getLevel(getSlot(0).getItem(), selectedModule());
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId >= 0 && buttonId < FlyingSwordModule.values().length) {
            selectedModule = buttonId;
            broadcastChanges();
            return true;
        }
        if (buttonId == INSTALL_BUTTON) return install(player);
        if (buttonId == REMOVE_BUTTON) return remove(player);
        return false;
    }

    private boolean install(Player player) {
        ItemStack sword = getSlot(0).getItem();
        ItemStack input = getSlot(1).getItem();
        if (!(sword.getItem() instanceof FlyingSwordItem) || input.isEmpty()) return false;
        FlyingSwordModule module = FlyingSwordModule.fromIngredient(input);
        if (module == null) return false;
        selectedModule = module.ordinal();
        int level = module.levelForAvailableCount(input.getCount());
        int cost = module.materialCountForLevel(level);
        refundInstalled(player, sword, module);
        input.shrink(cost);
        SwordModuleData.setLevelPreservingDurability(sword, module, level);
        recallActiveFormation(player, sword);
        getSlot(0).setChanged();
        getSlot(1).setChanged();
        broadcastChanges();
        return true;
    }

    private boolean remove(Player player) {
        ItemStack sword = getSlot(0).getItem();
        if (!(sword.getItem() instanceof FlyingSwordItem)) return false;
        FlyingSwordModule module = selectedModule();
        if (SwordModuleData.getLevel(sword, module) == 0) return false;
        refundInstalled(player, sword, module);
        SwordModuleData.setLevelPreservingDurability(sword, module, 0);
        recallActiveFormation(player, sword);
        getSlot(0).setChanged();
        broadcastChanges();
        return true;
    }

    private static void refundInstalled(Player player, ItemStack sword, FlyingSwordModule module) {
        int installedLevel = SwordModuleData.getLevel(sword, module);
        if (installedLevel == 0) return;
        ItemStack refund = new ItemStack(module.ingredient(), module.materialCountForLevel(installedLevel));
        if (!player.getInventory().add(refund)) player.drop(refund, false);
        SwordModuleData.setLevelPreservingDurability(sword, module, 0);
    }

    private static void recallActiveFormation(Player player, ItemStack sword) {
        if (!(sword.getItem() instanceof FlyingSwordItem swordItem)) return;
        player.level().getEntitiesOfClass(FlyingSwordEntity.class, player.getBoundingBox().inflate(64.0D),
                        entity -> entity.isOwnedBy(player)
                                && entity.getMaterialType() == swordItem.getMaterialType())
                .forEach(net.minecraft.world.entity.Entity::discard);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return result;
        ItemStack source = slot.getItem();
        result = source.copy();
        if (index < 2) {
            if (!moveItemStackTo(source, 2, slots.size(), true)) return ItemStack.EMPTY;
        } else if (source.getItem() instanceof FlyingSwordItem) {
            if (!moveItemStackTo(source, 0, 1, false)) return ItemStack.EMPTY;
        } else if (FlyingSwordModule.fromIngredient(source) != null) {
            if (!moveItemStackTo(source, 1, 2, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (source.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.FLYING_SWORD_WORKBENCH.get());
    }
}
