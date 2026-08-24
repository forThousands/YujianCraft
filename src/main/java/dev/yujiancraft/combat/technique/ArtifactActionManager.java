package dev.yujiancraft.combat.technique;

import dev.yujiancraft.config.TechniqueConfig;
import dev.yujiancraft.entity.FlyingSwordEntity;
import dev.yujiancraft.item.FlyingSwordItem;
import dev.yujiancraft.wanxiang.WanxiangSwordData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;

import java.util.Comparator;
import java.util.List;

/** Validates the single contextual action key and delegates only to a ready formation sword. */
public final class ArtifactActionManager {
    private ArtifactActionManager() {
    }

    public static void handleAction(ServerPlayer player, BlockPos requestedPos,
                                    net.minecraft.core.Direction requestedFace) {
        ItemStack source = FlyingSwordItem.findFlyingSword(player);
        if (source.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.yujiancraft.no_sword"), true);
            return;
        }
        TechniqueMode technique = dev.yujiancraft.combat.SwordSettings.read(source).techniqueMode();
        if (technique != TechniqueMode.TOOL_USE && technique != TechniqueMode.SPIRIT_FISHING) {
            player.displayClientMessage(Component.translatable("message.yujiancraft.technique.no_context_action"), true);
            return;
        }
        double range = technique == TechniqueMode.TOOL_USE
                ? TechniqueConfig.toolRange() : TechniqueConfig.fishingRange();
        if (!validClientBlockHit(player, requestedPos, range)) {
            player.displayClientMessage(Component.translatable("message.yujiancraft.technique.no_block"), true);
            return;
        }
        List<FlyingSwordEntity> swords = FlyingSwordItem.ensureFormation(player, source);
        FlyingSwordEntity actor = swords.stream()
                .filter(sword -> sword.getFormationSlot() == 0 && sword.isReadyForArtifactAction())
                .min(Comparator.comparingInt(FlyingSwordEntity::getFormationSlot)).orElse(null);
        if (actor == null) {
            player.displayClientMessage(Component.translatable("message.yujiancraft.technique.not_ready"), true);
            return;
        }
        BlockPos pos = requestedPos;
        if (technique == TechniqueMode.TOOL_USE) {
            if (!canMine(player, source, pos) || !actor.beginToolAction(pos, requestedFace)) {
                player.displayClientMessage(Component.translatable("message.yujiancraft.technique.cannot_mine"), true);
            }
        } else {
            if (!player.serverLevel().getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)
                    && requestedFace != null) {
                BlockPos adjacent = pos.relative(requestedFace);
                if (player.serverLevel().getFluidState(adjacent).is(net.minecraft.tags.FluidTags.WATER)) pos = adjacent;
            }
            if (!player.serverLevel().getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)
                    || !actor.beginFishingAction(pos)) {
                player.displayClientMessage(Component.translatable("message.yujiancraft.technique.need_water"), true);
            }
        }
    }

    /**
     * The client sends its actual screen-centre block, which matters for the shoulder camera.
     * The server still owns reach and obstruction validation, so the packet cannot mine through
     * walls or address unloaded terrain.
     */
    private static boolean validClientBlockHit(ServerPlayer player, BlockPos pos, double range) {
        if (pos == null || !player.serverLevel().hasChunkAt(pos)) return false;
        Vec3 eye = player.getEyePosition();
        Vec3 centre = Vec3.atCenterOf(pos);
        if (eye.distanceToSqr(centre) > range * range) return false;
        net.minecraft.world.phys.BlockHitResult obstruction = player.serverLevel().clip(
                new net.minecraft.world.level.ClipContext(eye, centre,
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        return obstruction.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                || obstruction.getBlockPos().equals(pos);
    }

    public static int miningTicks(ServerPlayer player, ItemStack source, BlockPos pos) {
        BlockState state = player.serverLevel().getBlockState(pos);
        float hardness = state.getDestroySpeed(player.serverLevel(), pos);
        if (hardness < 0.0F) return TechniqueConfig.toolMaxWorkTicks();
        float speed = Math.max(0.1F, source.getDestroySpeed(state));
        double divisor = source.isCorrectToolForDrops(state) ? 30.0D : 100.0D;
        int ticks = (int) Math.ceil(Math.max(0.05D, hardness) * divisor / speed);
        return Math.max(5, Math.min(TechniqueConfig.toolMaxWorkTicks(), ticks));
    }

    public static boolean completeMining(ServerPlayer player, FlyingSwordEntity sword, BlockPos pos) {
        ItemStack source = FlyingSwordItem.findFlyingSword(player, sword.getSourceBindingId());
        if (!canMine(player, source, pos)) return false;
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(pos);
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, state, player);
        if (MinecraftForge.EVENT_BUS.post(event)) return false;
        var blockEntity = level.getBlockEntity(pos);
        Block block = state.getBlock();
        block.playerWillDestroy(level, pos, state, player);
        boolean removed = level.removeBlock(pos, false);
        if (!removed) return false;
        block.destroy(level, pos, state);
        block.playerDestroy(level, player, pos, state, blockEntity, source);
        level.levelEvent(2001, pos, Block.getId(state));
        if (event.getExpToDrop() > 0) state.getBlock().popExperience(level, pos, event.getExpToDrop());
        player.awardStat(Stats.BLOCK_MINED.get(state.getBlock()));
        player.causeFoodExhaustion(0.005F);
        sword.consumeSourceDurability(player, 1);
        return true;
    }

    private static boolean canMine(ServerPlayer player, ItemStack source, BlockPos pos) {
        if (source.isEmpty() || !WanxiangSwordData.isUsable(source)
                || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos))
                > TechniqueConfig.toolRange() * TechniqueConfig.toolRange()) return false;
        ServerLevel level = player.serverLevel();
        if (!level.hasChunkAt(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) return false;
        if (!source.isCorrectToolForDrops(state) && source.getDestroySpeed(state) <= 1.0F) return false;
        // Completion repeats every mutable check because the block or tool may change while the
        // implement is travelling. Forge's cancellable break event remains the final authority.
        return true;
    }

    public static void completeFishing(ServerPlayer player, FlyingSwordEntity sword, BlockPos waterPos) {
        if (!player.serverLevel().getFluidState(waterPos).is(net.minecraft.tags.FluidTags.WATER)) return;
        ItemStack source = FlyingSwordItem.findFlyingSword(player, sword.getSourceBindingId());
        if (source.isEmpty()) return;
        ServerLevel level = player.serverLevel();
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, net.minecraft.world.phys.Vec3.atCenterOf(waterPos))
                .withParameter(LootContextParams.TOOL, source)
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withLuck(player.getLuck() + net.minecraft.world.item.enchantment.EnchantmentHelper
                        .getItemEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.FISHING_LUCK,
                                source))
                .create(LootContextParamSets.FISHING);
        for (ItemStack loot : level.getServer().getLootData().getLootTable(BuiltInLootTables.FISHING)
                .getRandomItems(params)) {
            if (!player.getInventory().add(loot.copy())) {
                ItemEntity dropped = player.drop(loot.copy(), false);
                if (dropped != null) dropped.setNoPickUpDelay();
            }
        }
        sword.consumeSourceDurability(player, 1);
    }
}
