package dev.yujiancraft.block;

import com.mojang.serialization.MapCodec;

import dev.yujiancraft.blockentity.SpiritReplenishingTableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class SpiritReplenishingTableBlock extends BaseEntityBlock {
    public static final MapCodec<SpiritReplenishingTableBlock> CODEC = simpleCodec(SpiritReplenishingTableBlock::new);
    public SpiritReplenishingTableBlock(Properties properties) { super(properties); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpiritReplenishingTableBlockEntity(pos, state);
    }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof MenuProvider provider) {
            serverPlayer.openMenu(provider, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void onRemove(BlockState oldState, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!oldState.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof SpiritReplenishingTableBlockEntity table) {
            table.dropContents();
        }
        super.onRemove(oldState, level, pos, newState, moving);
    }
}
