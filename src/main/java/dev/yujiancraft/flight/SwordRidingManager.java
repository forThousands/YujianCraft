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
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Grants collision-safe vanilla flight while an extra, non-combat sword supports the player. */
@net.neoforged.fml.common.EventBusSubscriber(modid = YujianCraft.MOD_ID)
public final class SwordRidingManager {
    private static final float RIDING_FLIGHT_SPEED = 0.075F;
    private static final String ACTIVE_TAG = "YujianCraftRidingActive";
    private static final String OLD_MAYFLY_TAG = "YujianCraftRidingOldMayfly";
    private static final String OLD_FLYING_TAG = "YujianCraftRidingOldFlying";
    private static final String OLD_SPEED_TAG = "YujianCraftRidingOldSpeed";
    private static final int RESTORE_GUARD_TICKS = 10;
    private static final Map<UUID, RidingState> STATES = new HashMap<>();
    private static final Map<UUID, AbilityRestoreGuard> RESTORE_GUARDS = new HashMap<>();

    private SwordRidingManager() {
    }

    public static void setRiding(ServerPlayer player, boolean active) {
        if (active) {
            if (!STATES.containsKey(player.getUUID())) start(player);
        } else if (STATES.containsKey(player.getUUID())
                || player.getPersistentData().getBoolean(ACTIVE_TAG)) {
            stop(player, true);
        } else {
            ModNetwork.sendSwordRidingState(player, false);
        }
    }

    public static boolean isRidingOn(ServerPlayer player, UUID supportSwordId) {
        RidingState state = STATES.get(player.getUUID());
        return state != null && state.supportSwordId.equals(supportSwordId);
    }

    private static void start(ServerPlayer player) {
        RESTORE_GUARDS.remove(player.getUUID());
        ItemStack stack = FlyingSwordItem.findFlyingSword(player);
        if (!FlyingSwordItem.isUsableFlyingSword(stack)) {
            // A stale/racing request must not overwrite vanilla Creative flight after its
            // double-jump has already toggled the local flying flag.
            if (!player.isCreative()) {
                player.displayClientMessage(Component.translatable("message.yujiancraft.riding_need_sword"), true);
                ModNetwork.sendSwordRidingState(player, false);
            }
            return;
        }
        FlyingSwordEntity support = ModEntities.FLYING_SWORD.get().create(player.serverLevel());
        if (support == null) return;
        support.bindAsRideSupport(player, stack);
        support.moveTo(player.getX(), player.getY() - 0.28D, player.getZ(), player.getYRot(), 0.0F);
        if (!player.serverLevel().addFreshEntity(support)) return;

        Abilities abilities = player.getAbilities();
        RidingState state = new RidingState(support.getUUID(), abilities.mayfly, abilities.flying,
                abilities.getFlyingSpeed(), player.gameMode.getGameModeForPlayer());
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
        if (player.isCreative() || player.isSpectator()) {
            RESTORE_GUARDS.remove(player.getUUID());
        } else {
            RESTORE_GUARDS.put(player.getUUID(), new AbilityRestoreGuard(
                    state.oldMayfly, state.oldFlying && state.oldMayfly, state.oldFlyingSpeed,
                    state.gameMode, RESTORE_GUARD_TICKS));
        }
        clearRecoveryData(player);
        ModNetwork.sendSwordRidingState(player, false);
        if (notify) player.displayClientMessage(Component.translatable("message.yujiancraft.riding_stopped"), true);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()
                || !(event.getEntity() instanceof ServerPlayer player)) return;
        RidingState state = STATES.get(player.getUUID());
        if (state == null) {
            enforceRestoredAbilities(player);
            return;
        }
        if (player.gameMode.getGameModeForPlayer() != state.gameMode) {
            stopAfterGameModeChange(player);
            return;
        }
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
        if (event.getEntity() instanceof ServerPlayer player) {
            stop(player, false);
            RESTORE_GUARDS.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stop(player, false);
            RESTORE_GUARDS.remove(player.getUUID());
        }
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

    /**
     * Vanilla has already installed the new game mode's abilities by the end-of-tick callback.
     * Remove only Yujian's support state here; restoring the previous mode's snapshot would leak
     * Creative flight into Survival (or strip Spectator flight).
     */
    private static void stopAfterGameModeChange(ServerPlayer player) {
        RidingState state = STATES.remove(player.getUUID());
        if (state != null) {
            Entity support = player.serverLevel().getEntity(state.supportSwordId);
            if (support != null) support.discard();
        }
        RESTORE_GUARDS.remove(player.getUUID());
        clearRecoveryData(player);
        ModNetwork.sendSwordRidingState(player, false);
    }

    private static void enforceRestoredAbilities(ServerPlayer player) {
        AbilityRestoreGuard guard = RESTORE_GUARDS.get(player.getUUID());
        if (guard == null) return;
        if (player.gameMode.getGameModeForPlayer() != guard.gameMode) {
            RESTORE_GUARDS.remove(player.getUUID());
            return;
        }
        Abilities abilities = player.getAbilities();
        boolean preserveVanillaFlying = player.isCreative();
        boolean changed = abilities.mayfly != guard.mayfly
                || !preserveVanillaFlying && abilities.flying != guard.flying
                || Math.abs(abilities.getFlyingSpeed() - guard.flyingSpeed) > 1.0E-5F;
        abilities.mayfly = guard.mayfly;
        if (!preserveVanillaFlying) abilities.flying = guard.flying && guard.mayfly;
        abilities.setFlyingSpeed(guard.flyingSpeed);
        player.fallDistance = 0.0F;
        if (changed) player.onUpdateAbilities();
        if (guard.remainingTicks <= 1) RESTORE_GUARDS.remove(player.getUUID());
        else RESTORE_GUARDS.put(player.getUUID(), guard.tick());
    }

    private static void clearRecoveryData(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        data.remove(ACTIVE_TAG);
        data.remove(OLD_MAYFLY_TAG);
        data.remove(OLD_FLYING_TAG);
        data.remove(OLD_SPEED_TAG);
    }

    private record RidingState(UUID supportSwordId, boolean oldMayfly, boolean oldFlying,
                               float oldFlyingSpeed, GameType gameMode) {
    }

    private record AbilityRestoreGuard(boolean mayfly, boolean flying, float flyingSpeed,
                                       GameType gameMode, int remainingTicks) {
        private AbilityRestoreGuard tick() {
            return new AbilityRestoreGuard(mayfly, flying, flyingSpeed, gameMode, remainingTicks - 1);
        }
    }
}
