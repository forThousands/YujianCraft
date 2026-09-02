package dev.yujiancraft.combat.combo;

import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.combat.SwordTargetingRules;
import dev.yujiancraft.entity.FlyingSwordEntity;
import dev.yujiancraft.entity.SwordArrayFieldEntity;
import dev.yujiancraft.config.EffectBalanceConfig;
import dev.yujiancraft.config.EffectParameter;
import dev.yujiancraft.item.FlyingSwordItem;
import dev.yujiancraft.network.ModNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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

/** Server-authoritative runtime shared by all data-defined Yujian combo sets. */
@Mod.EventBusSubscriber(modid = YujianCraft.MOD_ID)
public final class SwordComboManager {
    public static final double TARGET_RANGE = 13.0D;
    private static final String STYLE_TAG = "YujianCraftComboStyle";
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
        List<FlyingSwordEntity> swords = readyFormation(player);
        if (swords.size() != FlyingSwordItem.FORMATION_SIZE) {
            player.displayClientMessage(Component.translatable("message.yujiancraft.combo.need_six"), true);
            return;
        }
        ComboStyle style = selectedStyle(player);
        swords.forEach(FlyingSwordEntity::enterComboControl);
        Session session = new Session(player.position(), swords, style, player.level().getGameTime());
        SESSIONS.put(player.getUUID(), session);
        sendState(player, session, 0, -1, player.position().add(0.0D, 1.0D, 0.0D));
        player.level().playSound(null, player.blockPosition(), SoundEvents.TRIDENT_RETURN,
                SoundSource.PLAYERS, 0.95F, 1.62F);
        player.displayClientMessage(Component.translatable("message.yujiancraft.combo.enter_style",
                Component.translatable(style.translationKey())), true);
    }

    public static void cycleStyle(ServerPlayer player) {
        ComboStyle next = selectedStyle(player).next();
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && session.stage > 0) {
            session.pendingStyle = next;
            player.displayClientMessage(Component.translatable("message.yujiancraft.combo.style_pending",
                    Component.translatable(next.translationKey())), true);
            return;
        }
        saveStyle(player, next);
        if (session != null) {
            resetOrbitEpoch(session, player.level().getGameTime());
            session.style = next;
            sendState(player, session, 0, -1, player.position().add(0.0D, 1.0D, 0.0D));
        }
        player.displayClientMessage(Component.translatable("message.yujiancraft.combo.style_selected",
                Component.translatable(next.translationKey())), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 0.9F, next.heavyFinisher() ? 0.72F : 1.15F);
    }

    public static void attack(ServerPlayer player, int requestedTargetId, Vec3 clientLook) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        LivingEntity requested = validTarget(player, requestedTargetId);
        // Authored spatial transitions may intentionally leave the caster outside the ordinary
        // acquisition radius. Keep the already-authorised living target for sequence continuity;
        // fresh targets still go through normal range/LOS selection below.
        if (requested == null && session.stage > 0) requested = resolveTarget(player, session.targetId);
        if (requested == null) requested = selectSoftTarget(player, session, clientLook);
        if (requested == null) {
            player.displayClientMessage(Component.translatable("message.yujiancraft.combo.no_target"), true);
            return;
        }
        ComboStageDefinition current = session.stage <= 0 ? null : session.style.stage(session.stage);
        if (session.stage == 0 || session.stageTick > current.durationTicks() + inputGraceTicks()) {
            startStage(player, session, 1, requested);
        } else {
            session.bufferedInputs = Math.min(bufferedInputDepth(), session.bufferedInputs + 1);
            session.bufferedTarget = requested.getUUID();
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide
                || !(event.player instanceof ServerPlayer player)) return;
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        if (!player.isAlive() || player.isSpectator() || player.level() != session.level || player.isPassenger()) {
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
        if (target == null && session.stage < session.style.maxStage()) {
            finishSequence(player, session);
            return;
        }
        ComboStageDefinition definition = session.style.stage(session.stage);
        if (session.stageTick < definition.durationTicks()) tickStage(player, session, target, definition);
        else idlePose(player, session);
        session.stageTick++;
        if (session.stageTick >= definition.durationTicks()) {
            if (session.bufferedInputs > 0) {
                int next = session.stage >= session.style.maxStage() ? 1 : session.stage + 1;
                LivingEntity nextTarget = resolveTarget(player, session.bufferedTarget);
                if (nextTarget == null) nextTarget = selectSoftTarget(player, session, player.getLookAngle());
                session.bufferedInputs--;
                if (session.bufferedInputs == 0) session.bufferedTarget = null;
                if (nextTarget != null) startStage(player, session, next, nextTarget);
                else finishSequence(player, session);
            } else if (session.stageTick >= definition.durationTicks() + inputGraceTicks()) {
                finishSequence(player, session);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stop(player, false);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        String style = event.getOriginal().getPersistentData().getString(STYLE_TAG);
        if (!style.isBlank()) event.getEntity().getPersistentData().putString(STYLE_TAG, style);
    }

    private static void startStage(ServerPlayer player, Session session, int stage, LivingEntity target) {
        resetOrbitEpoch(session, player.level().getGameTime());
        session.stage = Mth.clamp(stage, 1, session.style.maxStage());
        session.stageTick = 0;
        session.targetId = target.getUUID();
        session.hitTargets.clear();
        session.orbitHits.clear();
        session.orbitTargets.clear();
        session.damageCommitted = false;
        session.durabilityCommitted = false;
        session.finisherSpawned = false;
        session.anchor = player.position();
        session.targetAnchor = target.position();
        Vec3 targetPoint = target.position().add(0.0D, target.getBbHeight() * 0.52D, 0.0D);
        Vec3 viewForward = ComboMotionMath.horizontal(player.getLookAngle(),
                ComboMotionMath.horizontal(targetPoint.subtract(session.anchor), new Vec3(0.0D, 0.0D, 1.0D)));
        session.stageForward = ComboMotionMath.horizontal(targetPoint.subtract(session.anchor), viewForward);
        session.stageRight = new Vec3(-session.stageForward.z, 0.0D, session.stageForward.x);
        Vec3 viewRight = new Vec3(-viewForward.z, 0.0D, viewForward.x);
        Vec3 desiredWarp = session.style.stage(session.stage).warp().desired(session.anchor,
                session.targetAnchor, viewForward, viewRight);
        session.warpDestination = resolveSafeWarp(player, desiredWarp);
        session.warpYaw = player.getYRot()
                + (session.style.stage(session.stage).warp().reverseFacing() ? 180.0F : 0.0F);
        session.warpApplied = session.style.stage(session.stage).warp() == ComboWarpProfile.NONE;
        session.swords.forEach(FlyingSwordEntity::enterComboControl);
        sendState(player, session, target.getId(), targetPoint);
        boolean forceful = session.style.stage(session.stage).vfx().thresholdAmount() > 0.001F;
        float pitch = forceful
                ? 1.15F - session.stage * 0.055F : 1.55F - session.stage * 0.08F;
        player.level().playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW,
                SoundSource.PLAYERS, 0.76F + session.stage * 0.10F, pitch);
    }

    private static void tickStage(ServerPlayer player, Session session, LivingEntity target,
                                  ComboStageDefinition definition) {
        Vec3 targetPoint = target == null ? session.targetAnchor.add(0.0D, 0.9D, 0.0D)
                : target.position().add(0.0D, target.getBbHeight() * 0.52D, 0.0D);
        applyWarp(player, session, definition);
        if ((session.style.targetSuppression() || definition.targetSuppression()) && target != null
                && EffectBalanceConfig.get(EffectParameter.COMBO_TARGET_SUPPRESSION_ENABLED) >= 0.5D) {
            suppressTarget(target, session.targetAnchor);
        }
        ComboMotionFrame rootFrame = frame(session, player.position(), targetPoint, 0, definition);
        if (definition.rootMotion() != ComboRootMotion.NONE) {
            movePlayerToward(player, definition.rootMotion().destination(rootFrame), 0.88D);
        }
        ComboMotionFrame base = frame(session, player.position(), targetPoint, 0, definition);
        boolean orbitDamageActive = definition.orbitSweep()
                && session.stageTick >= definition.commitTick();
        List<SwordSweep> sweeps = orbitDamageActive
                ? new ArrayList<>(session.swords.size()) : List.of();
        for (int i = 0; i < session.swords.size(); i++) {
            ComboMotionFrame swordFrame = new ComboMotionFrame(base.owner(), base.playerAnchor(), base.target(),
                    base.forward(), base.right(), i, base.tick(), base.duration(), base.worldTick(),
                    base.orbitPhase(), base.orbitSpeed());
            Vec3 position = definition.choreography().position(swordFrame);
            FlyingSwordEntity sword = session.swords.get(i);
            Vec3 previous = sword.position();
            sword.applyComboPose(position, definition.choreography().direction(swordFrame));
            if (orbitDamageActive) sweeps.add(new SwordSweep(i, sword, previous, position));
        }
        if (orbitDamageActive) {
            applyOrbitSweepDamage(player, session, target, targetPoint, definition, sweeps);
        }
        if (definition.finisher() && !session.finisherSpawned
                && session.stageTick >= finisherSpawnTick(definition)
                && target != null) {
            session.finisherSpawned = true;
            FlyingSwordEntity source = session.swords.get(0);
            if (definition.choreography() == ComboChoreography.STAR_RING_COLLAPSE) {
                SwordArrayFieldEntity.spawnStarRingSeal((ServerLevel) player.level(), player,
                        source.getDisplayItem(), source.getSourceBindingId(), target.getUUID(),
                        target.position(), target.getBbHeight(), target.getBbWidth());
            } else if (definition.giantArrayFinisher()) {
                SwordArrayFieldEntity.spawnCombo((ServerLevel) player.level(), player,
                        source.getDisplayItem(), source.getSourceBindingId(), target.getUUID(),
                        target.position(), target.getBbHeight(), target.getBbWidth(),
                        session.style.heavyFinisher());
            }
        }
        if (definition.hitProfile() == ComboHitProfile.COMMIT_AREA && !session.damageCommitted
                && session.stageTick >= definition.commitTick()) {
            session.damageCommitted = true;
            applyStageDamage(player, session, target, targetPoint, definition);
        }
    }

    private static int finisherSpawnTick(ComboStageDefinition definition) {
        if (definition.choreography() == ComboChoreography.STAR_RING_COLLAPSE) {
            return Math.max(0, StarRingMotion.FINALE_RISE_TICKS - 2);
        }
        return definition.commitTick();
    }

    private static int inputGraceTicks() {
        return Math.max(0, EffectBalanceConfig.getInt(EffectParameter.COMBO_INPUT_GRACE_TICKS));
    }

    private static int bufferedInputDepth() {
        return Mth.clamp(EffectBalanceConfig.getInt(EffectParameter.COMBO_BUFFERED_INPUT_DEPTH), 1, 4);
    }

    private static ComboMotionFrame frame(Session session, Vec3 owner, Vec3 targetPoint, int slot,
                                          ComboStageDefinition definition) {
        double worldTick = session.level.getGameTime();
        return new ComboMotionFrame(owner, session.anchor, targetPoint, session.stageForward,
                session.stageRight, slot, session.stageTick, definition.durationTicks(), worldTick,
                orbitPhase(session, worldTick), orbitSpeed(session));
    }

    private static void applyStageDamage(ServerPlayer player, Session session, LivingEntity primary,
                                         Vec3 centre, ComboStageDefinition definition) {
        AABB area = (definition.rootMotion() == ComboRootMotion.FORWARD_LUNGE
                || definition.rootMotion() == ComboRootMotion.FORWARD_LUNGE_FAST
                || definition.rootMotion() == ComboRootMotion.FORWARD_LUNGE_LONG)
                ? new AABB(session.anchor.add(0.0D, 1.0D, 0.0D), centre).inflate(
                        definition.damageRadius(), definition.verticalRadius(), definition.damageRadius())
                : new AABB(centre, centre).inflate(definition.damageRadius(),
                        definition.verticalRadius(), definition.damageRadius());
        List<LivingEntity> targets = player.level().getEntitiesOfClass(LivingEntity.class, area,
                candidate -> SwordTargetingRules.canActivelyTarget(player, candidate));
        if (primary != null && !targets.contains(primary)) targets.add(0, primary);
        FlyingSwordEntity sword = session.swords.get(Math.min(session.swords.size() - 1, session.stage - 1));
        boolean damaged = false;
        for (LivingEntity target : targets) {
            if (session.hitTargets.size() >= definition.targetLimit()
                    || !session.hitTargets.add(target.getUUID())) continue;
            damaged |= sword.applyComboHit(player, target, definition.damageScale(), false);
        }
        if (damaged) {
            sword.consumeSourceDurability(player, 1);
            if (session.style.starRing()) markOrbitContact(player, session, centre);
            boolean forceful = definition.vfx().thresholdAmount() > 0.001F;
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG,
                    SoundSource.PLAYERS, forceful ? 1.35F : 0.9F,
                    forceful ? 0.72F : 1.0F);
        }
    }

    private static void applyOrbitSweepDamage(ServerPlayer player, Session session, LivingEntity primary,
                                              Vec3 targetPoint, ComboStageDefinition definition,
                                              List<SwordSweep> sweeps) {
        boolean damaged = false;
        for (SwordSweep sweep : sweeps) {
            // High angular velocity at the outer edge can legitimately move a blade well over
            // seven blocks per tick. Only reject discontinuities large enough to be a teleport.
            if (sweep.from.distanceToSqr(sweep.to) > 400.0D) continue;
            AABB sweptArea = new AABB(sweep.from, sweep.to).inflate(0.72D);
            List<LivingEntity> candidates = player.level().getEntitiesOfClass(LivingEntity.class,
                    sweptArea, candidate -> SwordTargetingRules.canActivelyTarget(player, candidate));
            candidates.sort(Comparator.comparingInt(candidate -> candidate == primary ? 0 : 1));
            for (LivingEntity candidate : candidates) {
                OrbitHitKey key = new OrbitHitKey(sweep.slot, candidate.getUUID());
                if (session.orbitHits.contains(key)) continue;
                if (!session.orbitTargets.contains(candidate.getUUID())
                        && session.orbitTargets.size() >= definition.targetLimit()) continue;
                AABB hitBox = candidate.getBoundingBox().inflate(0.38D);
                if (!hitBox.contains(sweep.from) && !hitBox.contains(sweep.to)
                        && hitBox.clip(sweep.from, sweep.to).isEmpty()) continue;
                int previousInvulnerability = candidate.invulnerableTime;
                candidate.invulnerableTime = 0;
                boolean hit = sweep.sword.applyComboHit(player, candidate,
                        definition.damageScale(), false);
                if (!hit) {
                    candidate.invulnerableTime = previousInvulnerability;
                    continue;
                }
                candidate.invulnerableTime = 0;
                session.orbitHits.add(key);
                session.orbitTargets.add(candidate.getUUID());
                damaged = true;
                if (session.orbitBoostTick < 0L) {
                    markOrbitContact(player, session, targetPoint);
                }
            }
        }
        if (damaged && !session.durabilityCommitted) {
            session.durabilityCommitted = true;
            session.swords.get(0).consumeSourceDurability(player, 1);
        }
    }

    private static void markOrbitContact(ServerPlayer player, Session session, Vec3 targetPoint) {
        if (!session.style.starRing() || session.orbitBoostTick >= 0L) return;
        session.orbitBoostTick = player.level().getGameTime();
        LivingEntity target = resolveTarget(player, session.targetId);
        int targetId = target == null ? -1 : target.getId();
        sendState(player, session, targetId, targetPoint);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_CLUSTER_HIT,
                SoundSource.PLAYERS, 1.15F, 0.82F + session.stage * 0.07F);
    }

    private static double orbitPhase(Session session, double worldTick) {
        double elapsed = Math.max(0.0D, worldTick - session.orbitEpochTick);
        double boosted = session.orbitBoostTick < 0L ? 0.0D
                : Math.max(0.0D, worldTick - session.orbitBoostTick);
        return session.orbitPhaseAtEpoch + StarRingMotion.BASE_ANGULAR_SPEED * elapsed
                + (StarRingMotion.BOOST_ANGULAR_SPEED - StarRingMotion.BASE_ANGULAR_SPEED) * boosted;
    }

    private static double orbitSpeed(Session session) {
        return session.orbitBoostTick < 0L
                ? StarRingMotion.BASE_ANGULAR_SPEED : StarRingMotion.BOOST_ANGULAR_SPEED;
    }

    private static void resetOrbitEpoch(Session session, long worldTick) {
        session.orbitPhaseAtEpoch = orbitPhase(session, worldTick);
        session.orbitEpochTick = worldTick;
        session.orbitBoostTick = -1L;
    }

    private static void idlePose(ServerPlayer player, Session session) {
        Vec3 forward = ComboMotionMath.horizontal(Vec3.directionFromRotation(0.0F, player.getYRot()),
                new Vec3(0.0D, 0.0D, 1.0D));
        double worldTick = player.level().getGameTime();
        double phase = orbitPhase(session, worldTick);
        double speed = orbitSpeed(session);
        for (int i = 0; i < session.swords.size(); i++) {
            Vec3 pos = session.style.formation().position(player.position(), forward, i, phase, worldTick);
            Vec3 direction = session.style.formation().direction(player.position(), forward, i,
                    phase, speed, worldTick);
            session.swords.get(i).applyComboPose(pos, direction);
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
            return anglePenalty + player.distanceTo(target) * 0.22D + (target == sticky ? -2.4D : 0.0D);
        })).orElse(null);
    }

    private static LivingEntity resolveTarget(ServerPlayer player, UUID id) {
        if (id == null) return null;
        Entity raw = ((ServerLevel) player.level()).getEntity(id);
        return raw instanceof LivingEntity living && SwordTargetingRules.canActivelyTarget(player, living)
                ? living : null;
    }

    private static List<FlyingSwordEntity> readyFormation(ServerPlayer player) {
        return FlyingSwordItem.getOwnedFormationSwords(player).stream()
                .filter(FlyingSwordEntity::isAlive)
                .sorted(Comparator.comparingInt(FlyingSwordEntity::getFormationSlot))
                .limit(FlyingSwordItem.FORMATION_SIZE).toList();
    }

    private static void finishSequence(ServerPlayer player, Session session) {
        resetOrbitEpoch(session, player.level().getGameTime());
        if (session.pendingStyle != null) {
            session.style = session.pendingStyle;
            session.pendingStyle = null;
            saveStyle(player, session.style);
            player.displayClientMessage(Component.translatable("message.yujiancraft.combo.style_selected",
                    Component.translatable(session.style.translationKey())), true);
        }
        session.stage = 0;
        session.stageTick = 0;
        session.targetId = null;
        session.bufferedInputs = 0;
        session.bufferedTarget = null;
        session.finisherSpawned = false;
        session.orbitHits.clear();
        session.orbitTargets.clear();
        session.warpApplied = true;
        session.anchor = player.position();
        sendState(player, session, 0, -1, player.position().add(0.0D, 1.0D, 0.0D));
    }

    private static void stop(ServerPlayer player, boolean notify) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null) return;
        session.swords.forEach(sword -> sword.leaveComboControl(6));
        ModNetwork.sendComboState(player, false, session.style.id(), 0, player.level().getGameTime(),
                0, -1, player.position(), player.position(), player.position(), player.getYRot(),
                orbitPhase(session, player.level().getGameTime()), -1L);
        if (notify) {
            player.displayClientMessage(Component.translatable("message.yujiancraft.combo.exit"), true);
            player.level().playSound(null, player.blockPosition(), SoundEvents.TRIDENT_RETURN,
                    SoundSource.PLAYERS, 0.85F, 0.86F);
        }
    }

    private static void sendState(ServerPlayer player, Session session, int targetId, Vec3 targetPoint) {
        sendState(player, session, session.stage, targetId, targetPoint);
    }

    private static void sendState(ServerPlayer player, Session session, int stage, int targetId,
                                  Vec3 targetPoint) {
        int duration = stage <= 0 ? 0 : session.style.stage(stage).durationTicks();
        ModNetwork.sendComboState(player, true, session.style.id(), stage, session.orbitEpochTick,
                duration, targetId, session.anchor, targetPoint, session.warpDestination, session.warpYaw,
                session.orbitPhaseAtEpoch, session.orbitBoostTick);
    }

    private static void applyWarp(ServerPlayer player, Session session, ComboStageDefinition definition) {
        ComboWarpProfile warp = definition.warp();
        if (warp == ComboWarpProfile.NONE || session.warpApplied) return;
        if (session.stageTick < warp.executeTick()) {
            movePlayerToward(player, session.anchor, 1.0D);
            return;
        }
        session.warpApplied = true;
        Vec3 origin = player.position();
        Vec3 destination = session.warpDestination;
        player.connection.teleport(destination.x, destination.y, destination.z,
                session.warpYaw, player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(ParticleTypes.ENCHANT, origin.x, origin.y + 1.0D, origin.z,
                22, 0.48D, 0.92D, 0.48D, 0.04D);
        level.sendParticles(ParticleTypes.END_ROD, destination.x, destination.y + 1.0D, destination.z,
                28, 0.62D, 1.05D, 0.62D, 0.065D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, destination.x, destination.y + 0.9D, destination.z,
                16, 0.74D, 0.82D, 0.74D, 0.12D);
        level.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE,
                SoundSource.PLAYERS, 1.25F, 0.72F + session.stage * 0.045F);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 1.0F, 1.35F);
    }

    private static void suppressTarget(LivingEntity target, Vec3 anchor) {
        if (target instanceof Mob mob) mob.getNavigation().stop();
        target.setDeltaMovement(Vec3.ZERO);
        target.hasImpulse = true;
        if (target.position().distanceToSqr(anchor) > 0.0025D) {
            if (target instanceof ServerPlayer player) {
                player.connection.teleport(anchor.x, anchor.y, anchor.z, player.getYRot(), player.getXRot());
            } else {
                target.teleportTo(anchor.x, anchor.y, anchor.z);
            }
        }
    }

    private static Vec3 resolveSafeWarp(ServerPlayer player, Vec3 desired) {
        Vec3 forward = ComboMotionMath.horizontal(desired.subtract(player.position()), player.getLookAngle());
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        Vec3[] candidates = {desired, desired.add(0.0D, 1.0D, 0.0D), desired.add(0.0D, 2.0D, 0.0D),
                desired.add(right), desired.subtract(right), desired.subtract(forward.scale(1.25D))};
        for (Vec3 candidate : candidates) {
            AABB moved = player.getBoundingBox().move(candidate.subtract(player.position())).deflate(0.02D);
            if (player.level().noCollision(player, moved)) return candidate;
        }
        return player.position();
    }

    private static void movePlayerToward(ServerPlayer player, Vec3 destination, double response) {
        Vec3 delta = destination.subtract(player.position()).scale(Mth.clamp(response, 0.0D, 1.0D));
        player.move(MoverType.SELF, delta);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
    }

    private static ComboStyle selectedStyle(ServerPlayer player) {
        return ComboStyle.byId(player.getPersistentData().getString(STYLE_TAG));
    }

    private static void saveStyle(ServerPlayer player, ComboStyle style) {
        player.getPersistentData().putString(STYLE_TAG, style.id());
    }

    private static final class Session {
        private final ServerLevel level;
        private final List<FlyingSwordEntity> swords;
        private final Set<UUID> hitTargets = new HashSet<>();
        private final Set<OrbitHitKey> orbitHits = new HashSet<>();
        private final Set<UUID> orbitTargets = new HashSet<>();
        private ComboStyle style;
        private ComboStyle pendingStyle;
        private Vec3 anchor;
        private Vec3 targetAnchor;
        private Vec3 stageForward = new Vec3(0.0D, 0.0D, 1.0D);
        private Vec3 stageRight = new Vec3(-1.0D, 0.0D, 0.0D);
        private Vec3 warpDestination;
        private float warpYaw;
        private UUID targetId;
        private UUID bufferedTarget;
        private int stage;
        private int stageTick;
        private int bufferedInputs;
        private long orbitEpochTick;
        private long orbitBoostTick = -1L;
        private double orbitPhaseAtEpoch;
        private boolean damageCommitted;
        private boolean durabilityCommitted;
        private boolean finisherSpawned;
        private boolean warpApplied = true;

        private Session(Vec3 anchor, List<FlyingSwordEntity> swords, ComboStyle style, long worldTick) {
            this.level = (ServerLevel) swords.get(0).level();
            this.anchor = anchor;
            this.targetAnchor = anchor;
            this.warpDestination = anchor;
            this.warpYaw = 0.0F;
            this.orbitEpochTick = worldTick;
            this.orbitPhaseAtEpoch = worldTick * StarRingMotion.BASE_ANGULAR_SPEED;
            this.swords = new ArrayList<>(swords);
            this.style = style;
        }
    }

    private record SwordSweep(int slot, FlyingSwordEntity sword, Vec3 from, Vec3 to) { }

    private record OrbitHitKey(int slot, UUID target) { }
}
