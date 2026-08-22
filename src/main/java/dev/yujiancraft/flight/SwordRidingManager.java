package dev.yujiancraft.flight;

import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.entity.FlyingSwordEntity;
import dev.yujiancraft.item.FlyingSwordItem;
import dev.yujiancraft.network.ModNetwork;
import dev.yujiancraft.registry.ModEntities;
import dev.yujiancraft.upgrade.SwordModuleData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Grants collision-safe vanilla flight while an extra, non-combat sword supports the player. */
@Mod.EventBusSubscriber(modid = YujianCraft.MOD_ID)
public final class SwordRidingManager {
    private static final float RIDING_FLIGHT_SPEED = 0.075F;
    private static final String ACTIVE_TAG = "YujianCraftRidingActive";
    private static final String OLD_MAYFLY_TAG = "YujianCraftRidingOldMayfly";
    private static final String OLD_FLYING_TAG = "YujianCraftRidingOldFlying";
    private static final String OLD_SPEED_TAG = "YujianCraftRidingOldSpeed";
    private static final Map<UUID, RidingState> STATES = new HashMap<>();

    private SwordRidingManager() {
    }

    public static void toggle(ServerPlayer player) {
        if (STATES.containsKey(player.getUUID())) stop(player, true);
        else start(player);
    }

    public static boolean isRidingOn(ServerPlayer player, UUID supportSwordId) {
        RidingState state = STATES.get(player.getUUID());
        return state != null && state.supportSwordId.equals(supportSwordId);
    }

    private static void start(ServerPlayer player) {
        ItemStack stack = FlyingSwordItem.findFlyingSword(player);
        if (!(stack.getItem() instanceof FlyingSwordItem swordItem)) {
            player.displayClientMessage(Component.translatable("message.yujiancraft.riding_need_sword"), true);
            ModNetwork.sendSwordRidingState(player, false);
            return;
        }
        FlyingSwordEntity support = ModEntities.FLYING_SWORD.get().create(player.serverLevel());
        if (support == null) return;
        support.bindAsRideSupport(player, swordItem.getMaterialType(), swordItem.getSeries(),
                SwordModuleData.copyModules(stack));
        support.moveTo(player.getX(), player.getY() - 0.28D, player.getZ(), player.getYRot(), 0.0F);
        if (!player.serverLevel().addFreshEntity(support)) return;

        Abilities abilities = player.getAbilities();
        RidingState state = new RidingState(support.getUUID(), abilities.mayfly, abilities.flying,
                abilities.getFlyingSpeed());
        STATES.put(player.getUUID(), state);
        writeRecoveryData(player, state);
        abilities.mayfly = true;
        abilities.flying = true;
        abilities.setFlyingSpeed(RIDING_FLIGHT_SPEED);
        player.fallDistance = 0.0F;
        player.onUpdateAbilities();
        ModNetwork.sendSwordRidingState(player, true);
        player.displayClientMessage(Component.translatable("message.yujiancraft.riding_started"), true);
    }

    private static void stop(ServerPlayer player, boolean notify) {
        RidingState state = STATES.remove(player.getUUID());
        if (state == null) {
            restoreRecoveryData(player);
            ModNetwork.sendSwordRidingState(player, false);
            return;
        }
        Entity support = player.serverLevel().getEntity(state.supportSwordId);
        if (support != null) support.discard();
        restoreAbilities(player, state.oldMayfly, state.oldFlying, state.oldFlyingSpeed);
        clearRecoveryData(player);
        ModNetwork.sendSwordRidingState(player, false);
        if (notify) player.displayClientMessage(Component.translatable("message.yujiancraft.riding_stopped"), true);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()
                || !(event.player instanceof ServerPlayer player)) return;
        RidingState state = STATES.get(player.getUUID());
        if (state == null) return;
        if (!player.isAlive() || player.isSpectator()
                || FlyingSwordItem.findFlyingSword(player).isEmpty()
                || !(player.serverLevel().getEntity(state.supportSwordId) instanceof FlyingSwordEntity)) {
            stop(player, true);
            return;
        }
        Abilities abilities = player.getAbilities();
        boolean needsSync = !abilities.mayfly || !abilities.flying
                || Math.abs(abilities.getFlyingSpeed() - RIDING_FLIGHT_SPEED) > 1.0E-5F;
        abilities.mayfly = true;
        abilities.flying = true;
        abilities.setFlyingSpeed(RIDING_FLIGHT_SPEED);
        player.fallDistance = 0.0F;
        if (needsSync) player.onUpdateAbilities();
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stop(player, false);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stop(player, false);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.getPersistentData().getBoolean(ACTIVE_TAG)) {
            restoreRecoveryData(player);
            ModNetwork.sendSwordRidingState(player, false);
        }
    }

    private static void writeRecoveryData(ServerPlayer player, RidingState state) {
        CompoundTag data = player.getPersistentData();
        data.putBoolean(ACTIVE_TAG, true);
        data.putBoolean(OLD_MAYFLY_TAG, state.oldMayfly);
        data.putBoolean(OLD_FLYING_TAG, state.oldFlying);
        data.putFloat(OLD_SPEED_TAG, state.oldFlyingSpeed);
    }

    private static void restoreRecoveryData(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.getBoolean(ACTIVE_TAG)) return;
        restoreAbilities(player, data.getBoolean(OLD_MAYFLY_TAG), data.getBoolean(OLD_FLYING_TAG),
                data.contains(OLD_SPEED_TAG) ? data.getFloat(OLD_SPEED_TAG) : 0.05F);
        clearRecoveryData(player);
    }

    private static void restoreAbilities(ServerPlayer player, boolean mayfly, boolean flying, float speed) {
        Abilities abilities = player.getAbilities();
        abilities.mayfly = mayfly;
        abilities.flying = flying && mayfly;
        abilities.setFlyingSpeed(speed);
        player.fallDistance = 0.0F;
        player.onUpdateAbilities();
    }

    private static void clearRecoveryData(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        data.remove(ACTIVE_TAG);
        data.remove(OLD_MAYFLY_TAG);
        data.remove(OLD_FLYING_TAG);
        data.remove(OLD_SPEED_TAG);
    }

    private record RidingState(UUID supportSwordId, boolean oldMayfly, boolean oldFlying,
                               float oldFlyingSpeed) {
    }
}
