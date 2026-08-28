package dev.yujiancraft.combat.combo;

import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.combat.SwordTargetingRules;
import dev.yujiancraft.entity.FlyingSwordEntity;
import dev.yujiancraft.entity.SwordArrayFieldEntity;
import dev.yujiancraft.item.FlyingSwordItem;
import dev.yujiancraft.network.ModNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-authoritative five-stage medium-range Yujian combo.
 *
 * <p>The manager owns sequencing, buffered input, target validation, damage groups and player
 * root motion. FlyingSwordEntity remains the visual/upgrade carrier and is only given authored
 * poses while a session is active.</p>
 */
@Mod.EventBusSubscriber(modid = YujianCraft.MOD_ID)
public final class SwordComboManager {
    public static final int MAX_STAGE = 5;
    public static final double TARGET_RANGE = 13.0D;
    private static final int RESET_GRACE_TICKS = 8;
    private static final int[] DURATIONS = {0, 9, 9, 13, 18, 32};
    private static final int[] COMMIT_TICKS = {0, 5, 5, 8, 10, 19};
    private static final double[] DAMAGE_SCALES = {0.0D, 0.85D, 0.95D, 1.25D, 1.55D, 3.25D};
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private SwordComboManager() { }

    public static boolean isActive(ServerPlayer player) {
        return player != null && SESSIONS.containsKey(player.getUUID());
    }

    public static void toggle(ServerPlayer player) {
        if (isActive(player)) {
            stop(player, true);
            return;
        }
        List<FlyingSwordEntity> swords = readyFormation(player, false);
        if (swords.size() != FlyingSwordItem.FORMATION_SIZE) {
            player.displayClientMessage(Component.translatable("message.yujiancraft.combo.need_six"), true);
            return;
        }
        swords.forEach(FlyingSwordEntity::enterComboControl);
        Session session = new Session(player.position(), swords);
        SESSIONS.put(player.getUUID(), session);
        ModNetwork.sendComboState(player, true, 0, player.level().getGameTime(), 0, -1,
                player.position(), player.position().add(0.0D, 1.0D, 0.0D));
        player.level().playSound(null, player.blockPosition(), SoundEvents.TRIDENT_RETURN,
                SoundSource.PLAYERS, 0.95F, 1.62F);
        player.displayClientMessage(Component.translatable("message.yujiancraft.combo.enter"), true);
    }

    /** Accepts one attack input. At most one future stage is buffered. */
    public static void attack(ServerPlayer player, int requestedTargetId, Vec3 clientLook) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        LivingEntity requested = validTarget(player, requestedTargetId);
        if (requested == null) requested = selectSoftTarget(player, session, clientLook);
        if (requested == null) {
            player.displayClientMessage(Component.translatable("message.yujiancraft.combo.no_target"), true);
            return;
        }
        if (session.stage == 0 || session.stageTick > DURATIONS[session.stage] + RESET_GRACE_TICKS) {
            startStage(player, session, 1, requested);
        } else if (session.stageTick >= 2) {
            session.buffered = true;
            session.bufferedTarget = requested.getUUID();
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide
                || !(event.player instanceof ServerPlayer player)) return;
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        if (!player.isAlive() || player.isSpectator() || player.level() != session.level) {
            stop(player, false);
            return;
        }
        session.swords.removeIf(sword -> !sword.isAlive() || !sword.isOwnedBy(player));
        if (session.swords.size() != FlyingSwordItem.FORMATION_SIZE) {
            stop(player, false);
            return;
        }
        if (session.stage == 0) {
            idlePose(player, session);
            return;
        }
        LivingEntity target = resolveTarget(player, session.targetId);
        if (target == null && session.stage < 5) {
            finishSequence(player, session);
            return;
        }
        if (session.stageTick < DURATIONS[session.stage]) tickStage(player, session, target);
        else idlePose(player, session);
        session.stageTick++;
        int duration = DURATIONS[session.stage];
        if (session.stageTick >= duration) {
            if (session.buffered) {
                int next = session.stage >= MAX_STAGE ? 1 : session.stage + 1;
                LivingEntity nextTarget = resolveTarget(player, session.bufferedTarget);
                if (nextTarget == null) nextTarget = selectSoftTarget(player, session, player.getLookAngle());
                session.buffered = false;
                session.bufferedTarget = null;
                if (nextTarget != null) startStage(player, session, next, nextTarget);
                else finishSequence(player, session);
            } else if (session.stageTick >= duration + RESET_GRACE_TICKS) {
                finishSequence(player, session);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stop(player, false);
    }

    private static void startStage(ServerPlayer player, Session session, int stage, LivingEntity target) {
        session.stage = Mth.clamp(stage, 1, MAX_STAGE);
        session.stageTick = 0;
        session.targetId = target.getUUID();
        session.hitTargets.clear();
        session.damageCommitted = false;
        session.anchor = player.position();
        session.targetAnchor = target.position();
        session.serial++;
        session.swords.forEach(FlyingSwordEntity::enterComboControl);
        ModNetwork.sendComboState(player, true, session.stage, player.level().getGameTime(),
                DURATIONS[session.stage], target.getId(), session.anchor,
                target.position().add(0.0D, target.getBbHeight() * 0.52D, 0.0D));
        player.level().playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW,
                SoundSource.PLAYERS, 0.72F + session.stage * 0.08F, 1.55F - session.stage * 0.08F);
    }

    private static void tickStage(ServerPlayer player, Session session, LivingEntity target) {
        Vec3 targetPoint = target == null ? session.targetAnchor.add(0.0D, 0.9D, 0.0D)
                : target.position().add(0.0D, target.getBbHeight() * 0.52D, 0.0D);
        session.targetAnchor = target == null ? session.targetAnchor : target.position();
        Vec3 forward = horizontal(targetPoint.subtract(player.position()), player.getLookAngle());
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        switch (session.stage) {
            case 1 -> tickCrossCut(session, player, targetPoint, forward, right, true);
            case 2 -> tickCrossCut(session, player, targetPoint, forward, right, false);
            case 3 -> tickRingLunge(session, player, targetPoint, forward, right);
            case 4 -> tickSixSwordRelease(session, player, targetPoint, forward, right);
            case 5 -> tickGiantSword(session, player, target, targetPoint, forward, right);
            default -> { }
        }
        if (!session.damageCommitted && session.stageTick >= COMMIT_TICKS[session.stage]) {
            session.damageCommitted = true;
            if (session.stage < 5) applyStageDamage(player, session, target, targetPoint);
        }
    }

    private static void tickCrossCut(Session s, ServerPlayer player, Vec3 target, Vec3 forward,
                                     Vec3 right, boolean leftToRight) {
        double p = smooth(s.stageTick / (double) DURATIONS[s.stage]);
        for (int i = 0; i < s.swords.size(); i++) {
            FlyingSwordEntity sword = s.swords.get(i);
            boolean active = leftToRight ? i < 3 : i >= 3;
            if (!active) {
                Vec3 hold = player.position().add(0.0D, 1.25D + (i % 3) * 0.24D, 0.0D)
                        .add(right.scale((i - 2.5D) * 0.36D)).subtract(forward.scale(0.7D));
                sword.applyComboPose(hold, forward);
                continue;
            }
            int lane = i % 3;
            double sign = leftToRight ? 1.0D : -1.0D;
            Vec3 start = target.add(right.scale(-sign * (3.2D + lane * 0.34D)))
                    .add(0.0D, leftToRight ? 3.0D + lane * 0.22D : -0.15D + lane * 0.16D, 0.0D)
                    .subtract(forward.scale(0.7D));
            Vec3 end = target.add(right.scale(sign * (3.2D + lane * 0.34D)))
                    .add(0.0D, leftToRight ? -0.2D + lane * 0.12D : 3.0D + lane * 0.22D, 0.0D)
                    .add(forward.scale(1.0D));
            Vec3 pos = start.lerp(end, p);
            sword.applyComboPose(pos, end.subtract(start));
        }
    }

    private static void tickRingLunge(Session s, ServerPlayer player, Vec3 target, Vec3 forward, Vec3 right) {
        double p = smooth(s.stageTick / (double) DURATIONS[3]);
        double lunge = Math.sin(Math.PI * Mth.clamp(p * 1.1D, 0.0D, 1.0D)) * 3.15D;
        movePlayerToward(player, s.anchor.add(forward.scale(lunge)), 0.78D);
        Vec3 centre = player.position().add(forward.scale(1.35D)).add(0.0D, 1.0D, 0.0D);
        for (int i = 0; i < s.swords.size(); i++) {
            double angle = Math.PI * 2.0D * i / 6.0D + p * Math.PI * 4.4D;
            Vec3 radial = right.scale(Math.cos(angle)).add(new Vec3(0, 1, 0).scale(Math.sin(angle)));
            Vec3 pos = centre.add(radial.scale(2.65D)).add(forward.scale(Math.sin(angle * 2.0D) * 0.3D));
            Vec3 tangent = right.scale(-Math.sin(angle)).add(new Vec3(0, 1, 0).scale(Math.cos(angle)));
            s.swords.get(i).applyComboPose(pos, tangent.add(forward.scale(0.32D)));
        }
    }

    /** Stage four: retreat first, pause at the apex, then fire; descend only after impact. */
    private static void tickSixSwordRelease(Session s, ServerPlayer player, Vec3 target,
                                            Vec3 forward, Vec3 right) {
        int tick = s.stageTick;
        Vec3 apex = s.anchor.subtract(forward.scale(3.2D)).add(0.0D, 2.65D, 0.0D);
        if (tick <= 4) movePlayerToward(player, s.anchor.lerp(apex, smooth(tick / 4.0D)), 0.95D);
        else if (tick <= 11) movePlayerToward(player, apex, 0.9D);
        else movePlayerToward(player, apex.lerp(s.anchor, smooth((tick - 11) / 7.0D)), 0.44D);

        for (int i = 0; i < s.swords.size(); i++) {
            double angle = Math.PI * 2.0D * i / 6.0D + tick * 0.12D;
            Vec3 ring = apex.add(right.scale(Math.cos(angle) * 2.25D))
                    .add(forward.scale(Math.sin(angle) * 1.15D)).add(0.0D, Math.sin(angle) * 1.65D, 0.0D);
            Vec3 end = target.add(forward.scale(2.2D)).add(right.scale((i - 2.5D) * 0.12D));
            double fire = Mth.clamp((tick - 6) / 5.0D, 0.0D, 1.0D);
            Vec3 pos = ring.lerp(end, smooth(fire));
            s.swords.get(i).applyComboPose(pos, end.subtract(ring));
        }
    }

    private static void tickGiantSword(Session s, ServerPlayer player, LivingEntity target,
                                       Vec3 targetPoint, Vec3 forward, Vec3 right) {
        // Real swords gather into six visible array stations while the existing field renderer
        // carries the accelerated giant-sword performance and all installed visual modules.
        double p = smooth(Math.min(1.0D, s.stageTick / 7.0D));
        Vec3 centre = targetPoint.add(0.0D, 14.0D, 0.0D);
        for (int i = 0; i < s.swords.size(); i++) {
            double angle = Math.PI * 2.0D * i / 6.0D + s.stageTick * 0.09D;
            Vec3 station = centre.add(Math.cos(angle) * 9.5D, 0.0D, Math.sin(angle) * 9.5D);
            Vec3 start = player.position().add(0.0D, 1.2D, 0.0D)
                    .add(right.scale((i - 2.5D) * 0.45D)).subtract(forward.scale(0.5D));
            s.swords.get(i).applyComboPose(start.lerp(station, p), targetPoint.subtract(station));
        }
        if (!s.finisherSpawned && s.stageTick >= 7 && target != null) {
            s.finisherSpawned = true;
            FlyingSwordEntity source = s.swords.get(0);
            SwordArrayFieldEntity.spawnCombo((ServerLevel) player.level(), player,
                    source.getDisplayItem(), source.getSourceBindingId(), target.getUUID(),
                    target.position(), target.getBbHeight(), target.getBbWidth());
        }
    }

    private static void applyStageDamage(ServerPlayer player, Session s, LivingEntity primary, Vec3 centre) {
        double radius = switch (s.stage) { case 3 -> 4.8D; case 4 -> 2.8D; default -> 1.9D; };
        AABB area = new AABB(centre, centre).inflate(radius, Math.max(2.0D, radius * 0.7D), radius);
        List<LivingEntity> targets = player.level().getEntitiesOfClass(LivingEntity.class, area,
                candidate -> SwordTargetingRules.canActivelyTarget(player, candidate));
        if (primary != null && !targets.contains(primary)) targets.add(0, primary);
        int limit = s.stage == 3 ? 12 : 4;
        FlyingSwordEntity sword = s.swords.get(Math.min(s.swords.size() - 1, s.stage - 1));
        boolean damaged = false;
        for (LivingEntity target : targets) {
            if (s.hitTargets.size() >= limit || !s.hitTargets.add(target.getUUID())) continue;
            damaged |= sword.applyComboHit(player, target, DAMAGE_SCALES[s.stage], false);
        }
        if (damaged) sword.consumeSourceDurability(player, 1);
    }

    private static void idlePose(ServerPlayer player, Session session) {
        Vec3 forward = horizontal(player.getLookAngle(), new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        for (int i = 0; i < session.swords.size(); i++) {
            double angle = Math.PI * 2.0D * i / 6.0D;
            Vec3 pos = player.position().add(0.0D, 1.2D, 0.0D)
                    .add(right.scale(Math.cos(angle) * 1.75D))
                    .add(forward.scale(Math.sin(angle) * 0.72D - 0.75D))
                    .add(0.0D, Math.sin(angle) * 1.15D, 0.0D);
            session.swords.get(i).applyComboPose(pos, forward);
        }
    }

    private static LivingEntity validTarget(ServerPlayer player, int entityId) {
        Entity raw = entityId < 0 ? null : player.level().getEntity(entityId);
        if (!(raw instanceof LivingEntity target) || !SwordTargetingRules.canActivelyTarget(player, target)) return null;
        double range = TARGET_RANGE + target.getBbWidth();
        return player.distanceToSqr(target) <= range * range && player.hasLineOfSight(target) ? target : null;
    }

    private static LivingEntity selectSoftTarget(ServerPlayer player, Session session, Vec3 suppliedLook) {
        Vec3 look = suppliedLook == null || suppliedLook.lengthSqr() < 0.5D
                ? player.getLookAngle() : suppliedLook.normalize();
        LivingEntity sticky = resolveTarget(player, session.targetId);
        List<LivingEntity> candidates = player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(TARGET_RANGE, TARGET_RANGE * 0.75D, TARGET_RANGE),
                target -> SwordTargetingRules.canActivelyTarget(player, target) && player.hasLineOfSight(target));
        return candidates.stream().min(Comparator.comparingDouble(target -> {
            Vec3 direction = target.getEyePosition().subtract(player.getEyePosition()).normalize();
            double anglePenalty = (1.0D - Mth.clamp(direction.dot(look), -1.0D, 1.0D)) * 24.0D;
            double distancePenalty = player.distanceTo(target) * 0.22D;
            double stickyBonus = target == sticky ? -2.4D : 0.0D;
            return anglePenalty + distancePenalty + stickyBonus;
        })).orElse(null);
    }

    private static LivingEntity resolveTarget(ServerPlayer player, UUID id) {
        if (id == null) return null;
        Entity raw = ((ServerLevel) player.level()).getEntity(id);
        return raw instanceof LivingEntity living && SwordTargetingRules.canActivelyTarget(player, living)
                ? living : null;
    }

    private static List<FlyingSwordEntity> readyFormation(ServerPlayer player, boolean requireCooldown) {
        return FlyingSwordItem.getOwnedFormationSwords(player).stream()
                .filter(FlyingSwordEntity::isAlive)
                .sorted(Comparator.comparingInt(FlyingSwordEntity::getFormationSlot))
                .limit(FlyingSwordItem.FORMATION_SIZE).toList();
    }

    private static void finishSequence(ServerPlayer player, Session session) {
        session.stage = 0;
        session.stageTick = 0;
        session.targetId = null;
        session.buffered = false;
        session.bufferedTarget = null;
        session.finisherSpawned = false;
        session.anchor = player.position();
        ModNetwork.sendComboState(player, true, 0, player.level().getGameTime(), 0, -1,
                player.position(), player.position().add(0.0D, 1.0D, 0.0D));
    }

    private static void stop(ServerPlayer player, boolean notify) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null) return;
        session.swords.forEach(sword -> sword.leaveComboControl(6));
        ModNetwork.sendComboState(player, false, 0, player.level().getGameTime(), 0, -1,
                player.position(), player.position());
        if (notify) {
            player.displayClientMessage(Component.translatable("message.yujiancraft.combo.exit"), true);
            player.level().playSound(null, player.blockPosition(), SoundEvents.TRIDENT_RETURN,
                    SoundSource.PLAYERS, 0.85F, 0.86F);
        }
    }

    private static void movePlayerToward(ServerPlayer player, Vec3 destination, double response) {
        Vec3 delta = destination.subtract(player.position()).scale(Mth.clamp(response, 0.0D, 1.0D));
        player.move(MoverType.SELF, delta);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
    }

    private static Vec3 horizontal(Vec3 vector, Vec3 fallback) {
        Vec3 result = new Vec3(vector.x, 0.0D, vector.z);
        if (result.lengthSqr() < 1.0E-6D) result = new Vec3(fallback.x, 0.0D, fallback.z);
        return result.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : result.normalize();
    }

    private static double smooth(double value) {
        double x = Mth.clamp(value, 0.0D, 1.0D);
        return x * x * (3.0D - 2.0D * x);
    }

    private static final class Session {
        private final ServerLevel level;
        private final List<FlyingSwordEntity> swords;
        private final Set<UUID> hitTargets = new HashSet<>();
        private Vec3 anchor;
        private Vec3 targetAnchor;
        private UUID targetId;
        private UUID bufferedTarget;
        private int stage;
        private int stageTick;
        private int serial;
        private boolean buffered;
        private boolean damageCommitted;
        private boolean finisherSpawned;

        private Session(Vec3 anchor, List<FlyingSwordEntity> swords) {
            this.level = (ServerLevel) swords.get(0).level();
            this.anchor = anchor;
            this.targetAnchor = anchor;
            this.swords = new ArrayList<>(swords);
        }
    }
}
