package dev.yujiancraft.combat;

import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.item.FlyingSwordItem;
import dev.yujiancraft.entity.FlyingSwordEntity;
import dev.yujiancraft.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@net.neoforged.fml.common.EventBusSubscriber(modid = YujianCraft.MOD_ID)
public final class TargetLockManager {
    private static final Map<UUID, LockState> STATES = new HashMap<>();

    private TargetLockManager() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        LockState state = STATES.get(player.getUUID());
        if (state == null) return;
        // A player lock is independent from the formation. Keep it until the target dies/becomes
        // invalid or the player explicitly locks another target.
        if (resolve(player, state.lockedId) == null) clear(player);
    }

    public static LivingEntity getLockedTarget(ServerPlayer player) {
        LockState state = STATES.get(player.getUUID());
        return state == null ? null : resolve(player, state.lockedId);
    }

    public static boolean lockCrosshairNow(ServerPlayer player, int requestedEntityId) {
        if (!FlyingSwordItem.isUsableFlyingSword(player.getMainHandItem())) return false;
        SwordSettings settings = SwordSettings.read(player.getMainHandItem());
        LivingEntity aimed = validatedClientTarget(player, requestedEntityId, settings.crosshairLockRadius());
        if (aimed == null) aimed = findAimedEntity(player, settings.crosshairLockRadius()).orElse(null);
        if (aimed == null) return false;

        LockState state = STATES.computeIfAbsent(player.getUUID(), ignored -> new LockState());
        setLocked(player, state, aimed.getUUID());
        return true;
    }

    public static void lockTarget(ServerPlayer player, LivingEntity target) {
        if (!SwordTargetingRules.canActivelyTarget(player, target)) return;
        LockState state = STATES.computeIfAbsent(player.getUUID(), ignored -> new LockState());
        setLocked(player, state, target.getUUID());
    }

    private static LivingEntity validatedClientTarget(ServerPlayer player, int entityId, double lockRange) {
        if (entityId < 0 || !(player.serverLevel().getEntity(entityId) instanceof LivingEntity living)
                || !SwordTargetingRules.canActivelyTarget(player, living)) return null;
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
                        entity -> SwordTargetingRules.canActivelyTarget(player, entity)
                                && player.hasLineOfSight(entity))
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

    private static LivingEntity resolve(ServerPlayer owner, UUID id) {
        if (id == null) return null;
        return owner.serverLevel().getEntity(id) instanceof LivingEntity living
                && SwordTargetingRules.canActivelyTarget(owner, living) ? living : null;
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
    }
}
