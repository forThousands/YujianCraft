package dev.yujiancraft.menu;

import dev.yujiancraft.blockentity.SpiritReplenishingTableBlockEntity;
import dev.yujiancraft.item.FlyingSwordItem;
import dev.yujiancraft.registry.ModBlocks;
import dev.yujiancraft.registry.ModItems;
import dev.yujiancraft.registry.ModMenus;
import dev.yujiancraft.upgrade.SwordModuleData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public final class SpiritReplenishingMenu extends AbstractContainerMenu {
    public static final int REPLENISH_BUTTON = 100;
    private final ContainerLevelAccess access;

    public SpiritReplenishingMenu(int id, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(id, playerInventory, clientInventory(playerInventory, buffer), ContainerLevelAccess.NULL);
    }

    public SpiritReplenishingMenu(int id, Inventory playerInventory, SpiritReplenishingTableBlockEntity table) {
        this(id, playerInventory, table.inventory(),
                ContainerLevelAccess.create(table.getLevel(), table.getBlockPos()));
    }

    private SpiritReplenishingMenu(int id, Inventory playerInventory, IItemHandler inventory,
                                   ContainerLevelAccess access) {
        super(ModMenus.SPIRIT_REPLENISHING_TABLE.get(), id);
        this.access = access;
        addSlot(new SlotItemHandler(inventory, 0, 56, 37) {
            @Override public boolean mayPlace(ItemStack stack) { return FlyingSwordItem.isUsableFlyingSword(stack); }
            @Override public int getMaxStackSize() { return 1; }
        });
        addSlot(new SlotItemHandler(inventory, 1, 56, 73) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.is(ModItems.SPIRIT_CRYSTAL.get()); }
        });
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column + row * 9 + 9, 35 + column * 18, 112 + row * 18));
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 35 + column * 18, 170));
        }
    }

    private static IItemHandler clientInventory(Inventory inventory, FriendlyByteBuf buffer) {
        return inventory.player.level().getBlockEntity(buffer.readBlockPos())
                instanceof SpiritReplenishingTableBlockEntity table ? table.inventory() : new ItemStackHandler(2);
    }

    public int maximumDurability() {
        ItemStack sword = getSlot(0).getItem();
        return sword.isEmpty() ? 0 : sword.getMaxDamage() + SwordModuleData.virtualDurabilityMaximum(sword);
    }

    public int remainingDurability() {
        ItemStack sword = getSlot(0).getItem();
        return sword.isEmpty() ? 0 : Math.max(0, sword.getMaxDamage() - sword.getDamageValue())
                + SwordModuleData.virtualDurabilityRemaining(sword);
    }

    public boolean canReplenish() {
        ItemStack sword = getSlot(0).getItem();
        return FlyingSwordItem.isUsableFlyingSword(sword) && maximumDurability() > remainingDurability()
                && getSlot(1).getItem().is(ModItems.SPIRIT_CRYSTAL.get());
    }

    @Override public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId != REPLENISH_BUTTON || !canReplenish()) return false;
        ItemStack sword = getSlot(0).getItem();
        int amount = Math.max(1, (int) Math.ceil(maximumDurability() * 0.25D));
        if (SwordModuleData.repairWithSpirit(sword, amount) <= 0) return false;
        getSlot(1).getItem().shrink(1);
        getSlot(0).setChanged();
        getSlot(1).setChanged();
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            java.util.UUID binding = dev.yujiancraft.wanxiang.WanxiangSwordData.binding(sword);
            FlyingSwordItem.getOwnedFormationSwords(serverPlayer).stream()
                    .filter(entity -> java.util.Objects.equals(binding, entity.getSourceBindingId()))
                    .forEach(Entity::discard);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS, 0.8F, 1.35F);
        broadcastChanges();
        return true;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return result;
        ItemStack source = slot.getItem();
        result = source.copy();
        if (index < 2) {
            if (!moveItemStackTo(source, 2, slots.size(), true)) return ItemStack.EMPTY;
        } else if (FlyingSwordItem.isUsableFlyingSword(source)) {
            if (!moveItemStackTo(source, 0, 1, false)) return ItemStack.EMPTY;
        } else if (source.is(ModItems.SPIRIT_CRYSTAL.get())) {
            if (!moveItemStackTo(source, 1, 2, false)) return ItemStack.EMPTY;
        } else return ItemStack.EMPTY;
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return result;
    }

    @Override public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.SPIRIT_REPLENISHING_TABLE.get());
    }
}
