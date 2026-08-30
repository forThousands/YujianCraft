package dev.yujiancraft.blockentity;

import dev.yujiancraft.item.FlyingSwordItem;
import dev.yujiancraft.menu.SpiritTemperingMenu;
import dev.yujiancraft.registry.ModBlockEntities;
import dev.yujiancraft.wanxiang.WanxiangSwordData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SpiritTemperingTableBlockEntity extends BlockEntity implements MenuProvider {
    private final ItemStackHandler inventory = new ItemStackHandler(2) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slot == 0 ? WanxiangSwordData.canTemper(stack)
                    : stack.getItem() instanceof FlyingSwordItem;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    public SpiritTemperingTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPIRIT_TEMPERING_TABLE.get(), pos, state);
    }

    public ItemStackHandler inventory() { return inventory; }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.yujiancraft.spirit_tempering_table");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new SpiritTemperingMenu(id, inventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", inventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
    }

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
