package dev.yujiancraft.blockentity;

import dev.yujiancraft.item.FlyingSwordItem;
import dev.yujiancraft.menu.SpiritReplenishingMenu;
import dev.yujiancraft.registry.ModBlockEntities;
import dev.yujiancraft.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SpiritReplenishingTableBlockEntity extends BlockEntity implements MenuProvider {
    private final ItemStackHandler inventory = new ItemStackHandler(2) {
        @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slot == 0 ? FlyingSwordItem.isUsableFlyingSword(stack) : stack.is(ModItems.SPIRIT_CRYSTAL.get());
        }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private LazyOptional<ItemStackHandler> capability = LazyOptional.of(() -> inventory);

    public SpiritReplenishingTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPIRIT_REPLENISHING_TABLE.get(), pos, state);
    }

    public ItemStackHandler inventory() { return inventory; }
    @Override public Component getDisplayName() {
        return Component.translatable("container.yujiancraft.spirit_replenishing_table");
    }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new SpiritReplenishingMenu(id, playerInventory, this);
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("Inventory"));
    }
    @NotNull @Override public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap,
                                                                @Nullable Direction side) {
        return cap == ForgeCapabilities.ITEM_HANDLER ? capability.cast() : super.getCapability(cap, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); capability.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); capability = LazyOptional.of(() -> inventory); }

    public void dropContents() {
        if (level == null || level.isClientSide()) return;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
                inventory.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
    }
}
