package dev.swordflight.combat;

import dev.swordflight.Swordflight;
import dev.swordflight.item.FlyingSwordItem;
import dev.swordflight.entity.FlyingSwordEntity;
import dev.swordflight.network.ModNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Swordflight.MOD_ID)
public final class TargetLockManager {
    private static final int SWITCH_HOLD_TICKS = 15;
    private static final Map<UUID, LockState> STATES = new HashMap<>();

    private TargetLockManager() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()
                || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        ItemStack sword = FlyingSwordItem.findFlyingSword(player);
        boolean hasActiveSwords = !player.level().getEntitiesOfClass(FlyingSwordEntity.class,
                player.getBoundingBox().inflate(64.0D),
                entity -> entity.isOwnedBy(player) && entity.isFormationSword()).isEmpty();
        if (sword.isEmpty() || !hasActiveSwords) {
            clear(player);
            return;
        }

        SwordSettings settings = SwordSettings.read(sword);
        LockState state = STATES.get(player.getUUID());
        if (settings.targetingMode() != TargetingMode.CROSSHAIR_LOCK) {
            if (state == null || !state.manualOverride) {
                clear(player);
                return;
            }
            if (resolve(player.serverLevel(), state.lockedId) == null) clear(player);
            return;
        }

        state = STATES.computeIfAbsent(player.getUUID(), ignored -> new LockState());
        state.manualOverride = false;
        LivingEntity locked = resolve(player.serverLevel(), state.lockedId);
        if (locked == null) {
            setLocked(player, state, null);
        }

        boolean hasFreshClientAim = state.clientAimExpiresAt >= player.serverLevel().getGameTime();
        LivingEntity aimed = hasFreshClientAim ? resolve(player.serverLevel(), state.clientAimedId)
                : findAimedEntity(player, settings.crosshairLockRadius()).orElse(null);
        if (aimed == null || aimed.getUUID().equals(state.lockedId)) {
            state.candidateId = null;
            state.candidateTicks = 0;
            return;
        }

        if (aimed.getUUID().equals(state.candidateId)) {
            state.candidateTicks++;
        } else {
            state.candidateId = aimed.getUUID();
            state.candidateTicks = 1;
        }

        if (state.candidateTicks >= SWITCH_HOLD_TICKS) {
            setLocked(player, state, aimed.getUUID());
            state.candidateId = null;
            state.candidateTicks = 0;
        }
    }

    public static LivingEntity getLockedTarget(ServerPlayer player) {
        LockState state = STATES.get(player.getUUID());
        return state == null ? null : resolve(player.serverLevel(), state.lockedId);
    }

    public static boolean lockCrosshairNow(ServerPlayer player, int requestedEntityId) {
        if (!(player.getMainHandItem().getItem() instanceof FlyingSwordItem)) return false;
        SwordSettings settings = SwordSettings.read(player.getMainHandItem());
        LivingEntity aimed = validatedClientTarget(player, requestedEntityId, settings.crosshairLockRadius());
        if (aimed == null) aimed = findAimedEntity(player, settings.crosshairLockRadius()).orElse(null);
        if (aimed == null) return false;

        LockState state = STATES.computeIfAbsent(player.getUUID(), ignored -> new LockState());
        state.manualOverride = settings.targetingMode() != TargetingMode.CROSSHAIR_LOCK;
        state.candidateId = null;
        state.candidateTicks = 0;
        setLocked(player, state, aimed.getUUID());
        return true;
    }

    public static void acceptClientAim(ServerPlayer player, int requestedEntityId) {
        ItemStack sword = FlyingSwordItem.findFlyingSword(player);
        if (sword.isEmpty()) return;
        SwordSettings settings = SwordSettings.read(sword);
        if (settings.targetingMode() != TargetingMode.CROSSHAIR_LOCK) return;
        LivingEntity aimed = validatedClientTarget(player, requestedEntityId, settings.crosshairLockRadius());
        LockState state = STATES.computeIfAbsent(player.getUUID(), ignored -> new LockState());
        state.clientAimedId = aimed == null ? null : aimed.getUUID();
        state.clientAimExpiresAt = player.serverLevel().getGameTime() + 5L;
    }

    private static LivingEntity validatedClientTarget(ServerPlayer player, int entityId, double lockRange) {
        if (entityId < 0 || !(player.serverLevel().getEntity(entityId) instanceof LivingEntity living)
                || !(living instanceof Mob) || !living.isAlive() || living.isSpectator()) return null;
        if (living.distanceToSqr(player) > lockRange * lockRange || !player.hasLineOfSight(living)) return null;
        return living;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    private static Optional<LivingEntity> findAimedEntity(ServerPlayer player, double lockRange) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 end = eye.add(look.scale(lockRange));
        AABB search = new AABB(eye, end).inflate(3.0D);

        return player.level().getEntitiesOfClass(LivingEntity.class, search,
                        entity -> entity instanceof Mob && entity.isAlive() && entity != player
                                && !entity.isSpectator() && player.hasLineOfSight(entity))
                .stream()
                .filter(entity -> isInsideCrosshairCone(eye, look, entity, lockRange))
                .min(Comparator.comparingDouble(entity -> aimScore(eye, look, entity)));
    }

    private static boolean isInsideCrosshairCone(Vec3 eye, Vec3 look, LivingEntity entity, double lockRange) {
        Vec3 target = entity.position().add(0.0D, entity.getBbHeight() * 0.55D, 0.0D).subtract(eye);
        double forward = target.dot(look);
        if (forward <= 0.0D || forward > lockRange) return false;
        double perpendicularSquared = Math.max(0.0D, target.lengthSqr() - forward * forward);
        double allowance = Math.max(0.75D, entity.getBbWidth() * 0.75D + forward * 0.035D);
        return perpendicularSquared <= allowance * allowance;
    }

    private static double aimScore(Vec3 eye, Vec3 look, LivingEntity entity) {
        Vec3 target = entity.position().add(0.0D, entity.getBbHeight() * 0.55D, 0.0D).subtract(eye);
        double forward = target.dot(look);
        double perpendicularSquared = Math.max(0.0D, target.lengthSqr() - forward * forward);
        return perpendicularSquared / Math.max(1.0D, forward * forward) + forward * 1.0E-5D;
    }

    private static LivingEntity resolve(ServerLevel level, UUID id) {
        if (id == null) return null;
        return level.getEntity(id) instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    private static void clear(ServerPlayer player) {
        LockState removed = STATES.remove(player.getUUID());
        if (removed != null && removed.lockedId != null) {
            ModNetwork.sendLockedTarget(player, null);
        }
    }

    private static void setLocked(ServerPlayer player, LockState state, UUID targetId) {
        if (java.util.Objects.equals(state.lockedId, targetId)) return;
        state.lockedId = targetId;
        ModNetwork.sendLockedTarget(player, targetId);
    }

    private static final class LockState {
        private UUID lockedId;
        private UUID candidateId;
        private int candidateTicks;
        private boolean manualOverride;
        private UUID clientAimedId;
        private long clientAimExpiresAt = Long.MIN_VALUE;
    }
}
