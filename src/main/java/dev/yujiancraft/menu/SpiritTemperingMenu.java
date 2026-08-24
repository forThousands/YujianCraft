package dev.yujiancraft.menu;

import dev.yujiancraft.blockentity.SpiritTemperingTableBlockEntity;
import dev.yujiancraft.combat.technique.ArtifactRole;
import dev.yujiancraft.item.FlyingSwordItem;
import dev.yujiancraft.material.FlyingSwordMaterial;
import dev.yujiancraft.registry.ModBlocks;
import dev.yujiancraft.registry.ModMenus;
import dev.yujiancraft.wanxiang.ManualSpiritTrialManager;
import dev.yujiancraft.wanxiang.WanxiangGlowMode;
import dev.yujiancraft.wanxiang.WanxiangRenderPreset;
import dev.yujiancraft.wanxiang.WanxiangSwordData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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

public final class SpiritTemperingMenu extends AbstractContainerMenu {
    public static final int ROLE_BUTTON = 100;
    public static final int PRESET_BUTTON = 101;
    public static final int GLOW_BUTTON = 102;
    public static final int FLIP_BUTTON = 103;
    public static final int SCALE_DOWN_BUTTON = 104;
    public static final int SCALE_UP_BUTTON = 105;
    public static final int AURA_RADIUS_DOWN_BUTTON = 108;
    public static final int AURA_RADIUS_UP_BUTTON = 109;
    public static final int AURA_LENGTH_DOWN_BUTTON = 110;
    public static final int AURA_LENGTH_UP_BUTTON = 111;
    public static final int CONFIRM_SHAPE_BUTTON = 112;
    public static final int ENTER_TRIAL_BUTTON = 113;
    public static final int CONFIRM_ROLE_BUTTON = 114;

    private final ContainerLevelAccess access;
    private final DataSlot preset = DataSlot.standalone();
    private final DataSlot glowMode = DataSlot.standalone();
    private final DataSlot flip = DataSlot.standalone();
    private final DataSlot scalePercent = DataSlot.standalone();
    private final DataSlot auraRadiusPercent = DataSlot.standalone();
    private final DataSlot auraLengthPercent = DataSlot.standalone();
    private final DataSlot shapeConfirmed = DataSlot.standalone();
    private final DataSlot artifactRole = DataSlot.standalone();
    private final DataSlot roleConfirmed = DataSlot.standalone();
    private ItemStack lastSource = ItemStack.EMPTY;
    private ItemStack lastCore = ItemStack.EMPTY;
    private ItemStack confirmedSource = ItemStack.EMPTY;
    private ItemStack confirmedCore = ItemStack.EMPTY;

    public SpiritTemperingMenu(int id, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(id, playerInventory, clientInventory(playerInventory, buffer), ContainerLevelAccess.NULL);
    }

    public SpiritTemperingMenu(int id, Inventory playerInventory, SpiritTemperingTableBlockEntity blockEntity) {
        this(id, playerInventory, blockEntity.inventory(),
                ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()));
    }

    private SpiritTemperingMenu(int id, Inventory playerInventory, IItemHandler inventory,
                                ContainerLevelAccess access) {
        super(ModMenus.SPIRIT_TEMPERING_TABLE.get(), id);
        this.access = access;
        preset.set(WanxiangRenderPreset.VANILLA_FLAT.ordinal());
        glowMode.set(WanxiangGlowMode.FULL_BODY.ordinal());
        scalePercent.set(100);
        auraRadiusPercent.set(100);
        auraLengthPercent.set(100);
        artifactRole.set(ArtifactRole.GENERIC.ordinal());

        addSlot(new SlotItemHandler(inventory, 0, 103, 42) {
            @Override public boolean mayPlace(ItemStack stack) { return WanxiangSwordData.canTemper(stack); }
            @Override public int getMaxStackSize() { return 1; }
        });
        addSlot(new SlotItemHandler(inventory, 1, 103, 78) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.getItem() instanceof FlyingSwordItem; }
            @Override public int getMaxStackSize() { return 1; }
        });
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        99 + column * 18, 189 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 99 + column * 18, 249));
        }
        addDataSlot(preset);
        addDataSlot(glowMode);
        addDataSlot(flip);
        addDataSlot(scalePercent);
        addDataSlot(auraRadiusPercent);
        addDataSlot(auraLengthPercent);
        addDataSlot(shapeConfirmed);
        addDataSlot(artifactRole);
        addDataSlot(roleConfirmed);
        loadShapeFrom(inventory.getStackInSlot(0));
        lastSource = inventory.getStackInSlot(0).copy();
        lastCore = inventory.getStackInSlot(1).copy();
    }

    private static IItemHandler clientInventory(Inventory inventory, FriendlyByteBuf buffer) {
        return inventory.player.level().getBlockEntity(buffer.readBlockPos())
                instanceof SpiritTemperingTableBlockEntity blockEntity
                ? blockEntity.inventory() : new ItemStackHandler(2);
    }

    public WanxiangRenderPreset preset() { return WanxiangRenderPreset.fromOrdinal(preset.get()); }
    public WanxiangGlowMode glowMode() { return WanxiangGlowMode.fromOrdinal(glowMode.get()); }
    public boolean flipped() { return flip.get() != 0; }
    public int scalePercent() { return Math.max(50, Math.min(200, scalePercent.get())); }
    public int auraRadiusPercent() { return Math.max(50, Math.min(250, auraRadiusPercent.get())); }
    public int auraLengthPercent() { return Math.max(50, Math.min(250, auraLengthPercent.get())); }
    public boolean shapeConfirmed() { return shapeConfirmed.get() != 0; }
    public ArtifactRole artifactRole() { return ArtifactRole.fromOrdinal(artifactRole.get()); }
    public boolean roleConfirmed() { return roleConfirmed.get() != 0; }
    public int temperCount() { return WanxiangSwordData.temperCount(getSlot(0).getItem()); }
    public boolean hasRequiredItems() {
        return WanxiangSwordData.canTemperAgain(getSlot(0).getItem())
                && getSlot(1).getItem().getItem() instanceof FlyingSwordItem;
    }

    public FlyingSwordMaterial selectedCoreMaterial() {
        return getSlot(1).getItem().getItem() instanceof FlyingSwordItem sword
                ? sword.getMaterialType() : FlyingSwordMaterial.IRON;
    }

    public int experienceCost() { return WanxiangSwordData.experienceCost(selectedCoreMaterial()); }

    public ManualSpiritTrialManager.Shape shape() {
        return new ManualSpiritTrialManager.Shape(preset(), glowMode(), flipped(), scalePercent(),
                auraRadiusPercent(), auraLengthPercent(), artifactRole());
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        switch (buttonId) {
            case ROLE_BUTTON -> {
                artifactRole.set(artifactRole().next().ordinal());
                roleConfirmed.set(0);
                invalidateShape();
            }
            case PRESET_BUTTON -> { preset.set((preset.get() + 1) % WanxiangRenderPreset.values().length); invalidateShape(); }
            case GLOW_BUTTON -> { glowMode.set((glowMode.get() + 1) % WanxiangGlowMode.values().length); invalidateShape(); }
            case FLIP_BUTTON -> { flip.set(flip.get() == 0 ? 1 : 0); invalidateShape(); }
            case SCALE_DOWN_BUTTON -> { scalePercent.set(Math.max(50, scalePercent.get() - 5)); invalidateShape(); }
            case SCALE_UP_BUTTON -> { scalePercent.set(Math.min(200, scalePercent.get() + 5)); invalidateShape(); }
            case AURA_RADIUS_DOWN_BUTTON -> { auraRadiusPercent.set(Math.max(50, auraRadiusPercent.get() - 5)); invalidateShape(); }
            case AURA_RADIUS_UP_BUTTON -> { auraRadiusPercent.set(Math.min(250, auraRadiusPercent.get() + 5)); invalidateShape(); }
            case AURA_LENGTH_DOWN_BUTTON -> { auraLengthPercent.set(Math.max(50, auraLengthPercent.get() - 5)); invalidateShape(); }
            case AURA_LENGTH_UP_BUTTON -> { auraLengthPercent.set(Math.min(250, auraLengthPercent.get() + 5)); invalidateShape(); }
            case CONFIRM_SHAPE_BUTTON -> { return confirmShape(player); }
            case ENTER_TRIAL_BUTTON -> { return enterTrial(player); }
            case CONFIRM_ROLE_BUTTON -> {
                if (!hasRequiredItems()) return false;
                roleConfirmed.set(1);
                invalidateShape();
            }
            default -> { return false; }
        }
        broadcastChanges();
        return true;
    }

    private boolean confirmShape(Player player) {
        ItemStack source = getSlot(0).getItem();
        ItemStack core = getSlot(1).getItem();
        if (source.isEmpty() || core.isEmpty() || !roleConfirmed()) {
            player.displayClientMessage(Component.translatable("message.yujiancraft.tempering.confirm_role"), true);
            return false;
        }
        confirmedSource = source.copy();
        confirmedCore = core.copy();
        shapeConfirmed.set(1);
        player.displayClientMessage(Component.translatable("message.yujiancraft.tempering.shape_confirmed"), true);
        broadcastChanges();
        return true;
    }

    private boolean enterTrial(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        ItemStack source = getSlot(0).getItem();
        if (!WanxiangSwordData.canTemperAgain(source)) {
            player.displayClientMessage(Component.translatable("message.yujiancraft.tempering.limit"), true);
            return false;
        }
        if (!roleConfirmed() || !shapeConfirmed() || !same(source, confirmedSource)
                || !same(getSlot(1).getItem(), confirmedCore)) {
            player.displayClientMessage(Component.translatable("message.yujiancraft.tempering.confirm_shape"), true);
            return false;
        }
        return access.evaluate((level, pos) -> level.getBlockEntity(pos)
                instanceof SpiritTemperingTableBlockEntity table
                && ManualSpiritTrialManager.start(serverPlayer, table, shape()), false);
    }

    private void invalidateShape() {
        shapeConfirmed.set(0);
        confirmedSource = ItemStack.EMPTY;
        confirmedCore = ItemStack.EMPTY;
    }

    private void invalidateRitual() {
        roleConfirmed.set(0);
        invalidateShape();
    }

    private void loadShapeFrom(ItemStack source) {
        if (source.isEmpty()) return;
        preset.set(WanxiangSwordData.renderPreset(source).ordinal());
        glowMode.set(WanxiangSwordData.glowMode(source).ordinal());
        flip.set(WanxiangSwordData.flipAxis(source) ? 1 : 0);
        scalePercent.set(WanxiangSwordData.scalePercent(source));
        auraRadiusPercent.set(WanxiangSwordData.auraRadiusPercent(source));
        auraLengthPercent.set(WanxiangSwordData.auraLengthPercent(source));
        artifactRole.set(WanxiangSwordData.role(source).ordinal());
    }

    @Override
    public void broadcastChanges() {
        if (!same(getSlot(0).getItem(), lastSource)) {
            lastSource = getSlot(0).getItem().copy();
            loadShapeFrom(lastSource);
            invalidateRitual();
        }
        if (!same(getSlot(1).getItem(), lastCore)) {
            lastCore = getSlot(1).getItem().copy();
            invalidateRitual();
        }
        super.broadcastChanges();
    }

    private static boolean same(ItemStack first, ItemStack second) {
        return first.isEmpty() && second.isEmpty() || ItemStack.isSameItemSameTags(first, second);
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
            boolean moved = getSlot(1).getItem().isEmpty() && moveItemStackTo(source, 1, 2, false);
            if (!moved && !(getSlot(0).getItem().isEmpty() && moveItemStackTo(source, 0, 1, false))) {
                return ItemStack.EMPTY;
            }
        } else if (WanxiangSwordData.canTemper(source)) {
            if (!moveItemStackTo(source, 0, 1, false)) return ItemStack.EMPTY;
        } else return ItemStack.EMPTY;
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.SPIRIT_TEMPERING_TABLE.get());
    }
}
