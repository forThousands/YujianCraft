package dev.yujiancraft.entity;

import dev.yujiancraft.formation.FormationGeometry;
import dev.yujiancraft.formation.FormationMode;
import dev.yujiancraft.combat.technique.ArtifactActionManager;
import dev.yujiancraft.combat.technique.TechniqueMode;
import dev.yujiancraft.combat.AttackMode;
import dev.yujiancraft.combat.SwordSettings;
import dev.yujiancraft.combat.TargetingMode;
import dev.yujiancraft.combat.TargetLockManager;
import dev.yujiancraft.combat.ManualGuidanceManager;
import dev.yujiancraft.flight.SwordRidingManager;
import dev.yujiancraft.registry.ModItems;
import dev.yujiancraft.material.FlyingSwordMaterial;
import dev.yujiancraft.config.SwordBalanceConfig;
import dev.yujiancraft.config.TechniqueConfig;
import dev.yujiancraft.combat.SwordEffectEngine;
import dev.yujiancraft.combat.SwordTargetingRules;
import dev.yujiancraft.item.FlyingSwordItem;
import dev.yujiancraft.network.ModNetwork;
import dev.yujiancraft.upgrade.SwordModuleData;
import dev.yujiancraft.upgrade.FlyingSwordModule;
import dev.yujiancraft.visual.FlyingSwordSeries;
import dev.yujiancraft.wanxiang.WanxiangSwordData;
import dev.yujiancraft.wanxiang.WanxiangWeaponCatalog;
import dev.yujiancraft.wanxiang.FlyingSwordDamage;
import dev.yujiancraft.wanxiang.ManualSpiritTrialManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class FlyingSwordEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_FORMATION_MODE =
            SynchedEntityData.defineId(FlyingSwordEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FORMATION_SLOT =
            SynchedEntityData.defineId(FlyingSwordEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_DOCKED =
            SynchedEntityData.defineId(FlyingSwordEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER_ID =
            SynchedEntityData.defineId(FlyingSwordEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> DATA_MATERIAL =
            SynchedEntityData.defineId(FlyingSwordEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SERIES =
            SynchedEntityData.defineId(FlyingSwordEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_WHITE_HOT =
            SynchedEntityData.defineId(FlyingSwordEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_VISUAL_MODULES =
            SynchedEntityData.defineId(FlyingSwordEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_RIDE_SUPPORT =
            SynchedEntityData.defineId(FlyingSwordEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<ItemStack> DATA_DISPLAY_STACK =
            SynchedEntityData.defineId(FlyingSwordEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> DATA_TECHNIQUE =
            SynchedEntityData.defineId(FlyingSwordEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_COMBO_CONTROLLED =
            SynchedEntityData.defineId(FlyingSwordEntity.class, EntityDataSerializers.BOOLEAN);

    private UUID ownerId;
    private UUID targetId;
    private int formationSlot;
    private int attackCooldown;
    private FormationMode formationMode = FormationMode.FAN_ALIGNED;
    private FlightPhase phase = FlightPhase.DOCKED;
    private Vec3 fixedWaypoint;
    private int minimumDockTicks = SwordSettings.DEFAULT_MINIMUM_DOCK_TICKS;
    private double automaticTargetRadius = SwordSettings.DEFAULT_AUTOMATIC_RADIUS;
    private double crosshairLockRadius = SwordSettings.DEFAULT_LOCK_RADIUS;
    private TargetingMode targetingMode = TargetingMode.CROSSHAIR_LOCK;
    private AttackMode attackMode = AttackMode.SORTIE;
    private TechniqueMode techniqueMode = TechniqueMode.PIERCE;
    private FlyingSwordMaterial material = FlyingSwordMaterial.IRON;
    private FlyingSwordSeries series = FlyingSwordSeries.STANDARD;
    private CompoundTag installedModules = new CompoundTag();
    private boolean rideSupport;
    private Vec3 manualLaunchDirection;
    private int manualLaunchTicks;
    private boolean visualPreview;
    private UUID sourceBindingId;
    private ItemStack displayStack = ItemStack.EMPTY;
    private int techniqueTicks;
    private Vec3 swordArrayAnchor;
    private double swordArrayTargetHeight = 1.8D;
    private double swordArrayTargetWidth = 0.6D;
    private int postDockCooldown;
    private int guardImpactTicks;
    private BlockPos actionBlockPos;
    private Direction actionFace = Direction.UP;
    private int actionWorkTicks;
    private final Set<UUID> techniqueHits = new HashSet<>();
    private boolean techniqueDidDamage;

    public FlyingSwordEntity(EntityType<? extends FlyingSwordEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public void bindTo(ServerPlayer owner, int slot, FormationMode mode, SwordSettings settings,
                       ItemStack sourceStack) {
        ownerId = owner.getUUID();
        entityData.set(DATA_OWNER_ID, Optional.of(ownerId));
        formationSlot = slot;
        formationMode = mode;
        applySettings(settings);
        this.material = WanxiangSwordData.material(sourceStack);
        this.series = WanxiangSwordData.series(sourceStack);
        this.installedModules = SwordModuleData.copyModules(sourceStack);
        this.sourceBindingId = WanxiangSwordData.ensureBinding(sourceStack);
        this.displayStack = displayCopy(sourceStack);
        entityData.set(DATA_MATERIAL, material.ordinal());
        entityData.set(DATA_SERIES, series.ordinal());
        entityData.set(DATA_DISPLAY_STACK, displayStack.copy());
        entityData.set(DATA_TECHNIQUE, techniqueMode.ordinal());
        entityData.set(DATA_WHITE_HOT,
                SwordModuleData.getLevel(this.installedModules, FlyingSwordModule.WHITE_HOT) > 0);
        entityData.set(DATA_VISUAL_MODULES, SwordModuleData.packVisualEffects(this.installedModules));
        entityData.set(DATA_FORMATION_SLOT, slot);
        entityData.set(DATA_FORMATION_MODE, mode.ordinal());
        attackCooldown = 20 + slot * 7;
        if (techniqueMode.isPassive()) attackCooldown = 0;
        else if (techniqueMode != TechniqueMode.PIERCE) attackCooldown = 20;
    }

    public void bindAsRideSupport(ServerPlayer owner, ItemStack sourceStack) {
        ownerId = owner.getUUID();
        entityData.set(DATA_OWNER_ID, Optional.of(ownerId));
        this.material = WanxiangSwordData.material(sourceStack);
        this.series = WanxiangSwordData.series(sourceStack);
        this.installedModules = SwordModuleData.copyModules(sourceStack);
        this.sourceBindingId = WanxiangSwordData.ensureBinding(sourceStack);
        this.displayStack = displayCopy(sourceStack);
        rideSupport = true;
        phase = FlightPhase.RIDE_SUPPORT;
        formationSlot = 0;
        entityData.set(DATA_MATERIAL, material.ordinal());
        entityData.set(DATA_SERIES, series.ordinal());
        entityData.set(DATA_FORMATION_SLOT, 0);
        entityData.set(DATA_DOCKED, false);
        entityData.set(DATA_RIDE_SUPPORT, true);
        entityData.set(DATA_DISPLAY_STACK, displayStack.copy());
        entityData.set(DATA_WHITE_HOT,
                SwordModuleData.getLevel(this.installedModules, FlyingSwordModule.WHITE_HOT) > 0);
        entityData.set(DATA_VISUAL_MODULES, SwordModuleData.packVisualEffects(this.installedModules));
    }

    public void setFormationMode(FormationMode mode) {
        formationMode = mode;
        entityData.set(DATA_FORMATION_MODE, mode.ordinal());
    }

    public void applySettings(SwordSettings settings) {
        minimumDockTicks = settings.minimumDockTicks();
        automaticTargetRadius = settings.automaticTargetRadius();
        crosshairLockRadius = settings.crosshairLockRadius();
        targetingMode = settings.targetingMode();
        attackMode = settings.attackMode();
        TechniqueMode previousTechnique = techniqueMode;
        techniqueMode = settings.techniqueMode();
        entityData.set(DATA_TECHNIQUE, techniqueMode.ordinal());
        if (previousTechnique != techniqueMode && phase != FlightPhase.DOCKED && !rideSupport) {
            actionBlockPos = null;
            beginReturn();
        }
    }

    public boolean isOwnedBy(net.minecraft.world.entity.player.Player player) {
        return ownerId != null && ownerId.equals(player.getUUID());
    }

    public FlyingSwordMaterial getMaterialType() {
        return material;
    }

    public FlyingSwordSeries getSeriesType() {
        return series;
    }

    public int getFormationSlot() {
        return formationSlot;
    }

    public FormationMode getFormationModeType() {
        return formationMode;
    }

    public boolean isFormationSword() {
        return !rideSupport;
    }

    public boolean isReadyForManualLaunch() {
        return !rideSupport && techniqueMode == TechniqueMode.PIERCE
                && phase == FlightPhase.DOCKED && attackCooldown <= 0;
    }

    public TechniqueMode getTechniqueMode() {
        return techniqueMode;
    }

    public boolean isReadyForArtifactAction() {
        return !rideSupport && phase == FlightPhase.DOCKED && attackCooldown <= 0;
    }

    public boolean beginToolAction(BlockPos pos, Direction face) {
        if (!isReadyForArtifactAction() || techniqueMode != TechniqueMode.TOOL_USE || pos == null) return false;
        actionBlockPos = pos.immutable();
        actionFace = face == null ? Direction.UP : face;
        techniqueTicks = 0;
        phase = FlightPhase.TOOL_APPROACH;
        return true;
    }

    public boolean beginFishingAction(BlockPos pos) {
        if (!isReadyForArtifactAction() || techniqueMode != TechniqueMode.SPIRIT_FISHING || pos == null) return false;
        actionBlockPos = pos.immutable();
        techniqueTicks = 0;
        int lure = net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                net.minecraft.world.item.enchantment.Enchantments.FISHING_SPEED, displayStack);
        int minimum = Math.max(20, TechniqueConfig.fishingMinWait() - lure * 80);
        int maximum = Math.max(minimum, TechniqueConfig.fishingMaxWait() - lure * 80);
        actionWorkTicks = random.nextIntBetweenInclusive(minimum, maximum);
        phase = FlightPhase.FISHING_APPROACH;
        return true;
    }

    public boolean isGuarding() {
        return !rideSupport && techniqueMode == TechniqueMode.GUARD && phase == FlightPhase.DOCKED;
    }

    public void triggerGuardImpact() {
        guardImpactTicks = Math.max(guardImpactTicks, TechniqueConfig.guardImpactCooldown());
    }

    public void beginManualGuidance(Vec3 aimDirection, Vec3 launchDirection) {
        if (!isReadyForManualLaunch()) return;
        targetId = null;
        fixedWaypoint = null;
        manualLaunchDirection = safeDirection(launchDirection, aimDirection);
        manualLaunchTicks = 7;
        setDeltaMovement(manualLaunchDirection.scale(0.24D));
        phase = FlightPhase.MANUAL_GUIDANCE;
    }

    public void lockManualTarget(UUID target) {
        if (rideSupport || phase != FlightPhase.MANUAL_GUIDANCE || target == null) return;
        targetId = target;
        manualLaunchDirection = null;
        manualLaunchTicks = 0;
        phase = FlightPhase.HOMING;
    }

    public void cancelManualFlight() {
        if (phase == FlightPhase.MANUAL_GUIDANCE) beginReturn();
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_FORMATION_MODE, FormationMode.FAN_ALIGNED.ordinal());
        entityData.define(DATA_FORMATION_SLOT, 0);
        entityData.define(DATA_DOCKED, true);
        entityData.define(DATA_OWNER_ID, Optional.empty());
        entityData.define(DATA_MATERIAL, FlyingSwordMaterial.IRON.ordinal());
        entityData.define(DATA_SERIES, FlyingSwordSeries.STANDARD.ordinal());
        entityData.define(DATA_WHITE_HOT, false);
        entityData.define(DATA_VISUAL_MODULES, 0);
        entityData.define(DATA_RIDE_SUPPORT, false);
        entityData.define(DATA_DISPLAY_STACK, ItemStack.EMPTY);
        entityData.define(DATA_TECHNIQUE, TechniqueMode.PIERCE.ordinal());
        entityData.define(DATA_COMBO_CONTROLLED, false);
    }

    @Override
    public void tick() {
        super.tick();
        noPhysics = true;
        setNoGravity(true);

        if (!(level() instanceof ServerLevel serverLevel) || ownerId == null) {
            return;
        }

        ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(ownerId);
        if (owner == null || !owner.isAlive() || owner.level() != level()) {
            discard();
            return;
        }
        if (sourceBindingId != null && FlyingSwordItem.findFlyingSword(owner, sourceBindingId).isEmpty()) {
            discard();
            return;
        }
        if (WanxiangSwordData.isTempered(displayStack)
                && !WanxiangWeaponCatalog.enabled(serverLevel.getServer(), displayStack)) {
            discard();
            return;
        }

        if (rideSupport) {
            if (!SwordRidingManager.isRidingOn(owner, getUUID())) {
                discard();
                return;
            }
            tickRideSupport(owner);
            entityData.set(DATA_DOCKED, false);
            return;
        }

        if (phase == FlightPhase.DOCKED && attackCooldown > 0) {
            attackCooldown--;
        }

        switch (phase) {
            case DOCKED -> tickDocked(owner);
            case CLEAR_PLAYER -> tickClearPlayer(owner);
            case RISE -> tickRise();
            case HOMING -> tickHoming(owner, serverLevel);
            case FOLLOW_THROUGH -> tickFollowThrough(serverLevel);
            case RETURN_RALLY -> tickReturnRally(owner);
            case RETURN_APPROACH -> tickReturnApproach(owner);
            case DOCKING -> tickDocking(owner);
            case RELENTLESS_ARC -> tickRelentlessArc(serverLevel);
            case MANUAL_GUIDANCE -> tickManualGuidance(owner);
            case RIDE_SUPPORT -> discard();
            case SWEEP -> tickSweep(owner, serverLevel);
            case SWORD_ARRAY -> tickSwordArray(owner, serverLevel);
            case TOOL_APPROACH -> tickToolApproach(owner);
            case TOOL_WORK -> tickToolWork(owner, serverLevel);
            case FISHING_APPROACH -> tickFishingApproach(owner);
            case FISHING_WAIT -> tickFishingWait(owner, serverLevel);
            case COMBO -> tickComboControl();
        }
        entityData.set(DATA_DOCKED, phase == FlightPhase.DOCKED);
    }

    private void tickDocked(ServerPlayer owner) {
        if (techniqueMode == TechniqueMode.GUARD) {
            if (guardImpactTicks > 0) guardImpactTicks--;
            double impact = guardImpactTicks <= 0 ? 0.0D
                    : 0.28D * guardImpactTicks / Math.max(1.0D, TechniqueConfig.guardImpactCooldown());
            Vec3 guard = FormationGeometry.guardPosition(owner, formationSlot, impact);
            setPos(guard);
            setDeltaMovement(Vec3.ZERO);
            faceDirection(FormationGeometry.guardDirection(owner, formationSlot), 0.5D);
            return;
        }
        Vec3 dock = FormationGeometry.dockPosition(owner, formationSlot, formationMode, tickCount);
        setPos(dock);
        setDeltaMovement(Vec3.ZERO);
        faceDirection(FormationGeometry.dockDirection(owner, dock, formationMode), 0.34D);

        if (techniqueMode == TechniqueMode.SWORD_ARRAY) {
            // Sword Array is a deliberate formation command. Readiness is checked when the player
            // presses its activation key; a cooled formation must never auto-fire by itself.
            return;
        }
        if (attackCooldown <= 0 && !techniqueMode.isPassive()) {
            findTarget(owner).ifPresent(target -> beginAttack(owner, target));
        }
    }

    /** Sword-array is one formation action, not six independent attacks. The lowest-slot sword
     * coordinates an atomic release only after all six implements are docked and cooled down. */
    public static boolean activateCompleteSwordArray(ServerPlayer owner, LivingEntity target) {
        if (target == null || !target.isAlive() || !SwordTargetingRules.canActivelyTarget(owner, target)) return false;
        List<FlyingSwordEntity> formation = FlyingSwordItem.getOwnedFormationSwords(owner).stream()
                .filter(sword -> !sword.rideSupport && sword.techniqueMode == TechniqueMode.SWORD_ARRAY)
                .sorted(Comparator.comparingInt(FlyingSwordEntity::getFormationSlot))
                .toList();
        if (formation.size() != FlyingSwordItem.FORMATION_SIZE) return false;
        if (formation.stream().anyMatch(sword -> sword.phase != FlightPhase.DOCKED
                || sword.attackCooldown > 0)) return false;
        formation.forEach(sword -> sword.beginAttack(owner, target));
        return true;
    }

    private void tickRideSupport(ServerPlayer owner) {
        Vec3 forward = Vec3.directionFromRotation(0.0F, owner.getYRot()).normalize();
        Vec3 supportPosition = owner.position().add(forward.scale(0.10D)).add(0.0D, -0.28D, 0.0D);
        setPos(supportPosition);
        setDeltaMovement(owner.getDeltaMovement());
        faceDirection(forward, 1.0D);
    }

    private void tickManualGuidance(ServerPlayer owner) {
        Vec3 aimedDirection = ManualGuidanceManager.getAimDirection(owner, this);
        if (aimedDirection == null) {
            beginReturn();
            return;
        }
        Vec3 guidedDirection = aimedDirection;
        if (manualLaunchTicks > 0 && manualLaunchDirection != null) {
            double aimWeight = 1.0D - manualLaunchTicks / 7.0D;
            guidedDirection = safeDirection(manualLaunchDirection.scale(1.0D - aimWeight)
                    .add(aimedDirection.scale(aimWeight)), aimedDirection);
            manualLaunchTicks--;
        }
        flyToward(position().add(guidedDirection.scale(24.0D)), 0.16D, 1.02D);
    }

    private void beginAttack(ServerPlayer owner, LivingEntity target) {
        targetId = target.getUUID();
        if (techniqueMode == TechniqueMode.SWEEP) {
            techniqueTicks = 0;
            techniqueHits.clear();
            techniqueDidDamage = false;
            phase = FlightPhase.SWEEP;
            return;
        }
        if (techniqueMode == TechniqueMode.SWORD_ARRAY) {
            techniqueTicks = 0;
            techniqueHits.clear();
            swordArrayAnchor = target.position();
            swordArrayTargetHeight = target.getBbHeight();
            swordArrayTargetWidth = target.getBbWidth();
            phase = FlightPhase.SWORD_ARRAY;
            return;
        }
        if (!formationMode.usesRingGeometry()) {
            Vec3 dock = FormationGeometry.dockPosition(owner, formationSlot, formationMode, tickCount);
            fixedWaypoint = FormationGeometry.launchClearPoint(owner, formationSlot, tickCount);
            faceDirection(FormationGeometry.dockDirection(owner, dock, formationMode), 1.0D);
            phase = FlightPhase.CLEAR_PLAYER;
        } else {
            phase = FlightPhase.HOMING;
        }
    }

    private void tickClearPlayer(ServerPlayer owner) {
        if (fixedWaypoint == null) {
            fixedWaypoint = FormationGeometry.launchClearPoint(owner, formationSlot, tickCount);
        }
        flyToward(fixedWaypoint, 0.22D, 0.82D);
        if (closeTo(fixedWaypoint, 0.18D)) {
            Vec3 direction = safeDirection(getDeltaMovement(), new Vec3(0.0D, 1.0D, 0.0D));
            fixedWaypoint = FormationGeometry.risePoint(fixedWaypoint, direction);
            phase = FlightPhase.RISE;
        }
    }

    private void tickRise() {
        if (fixedWaypoint == null) {
            phase = FlightPhase.HOMING;
            return;
        }
        flyToward(fixedWaypoint, 0.2D, 0.88D);
        if (closeTo(fixedWaypoint, 0.22D)) {
            fixedWaypoint = null;
            phase = FlightPhase.HOMING;
        }
    }

    private void tickHoming(ServerPlayer owner, ServerLevel serverLevel) {
        if (targetingMode != TargetingMode.MANUAL_GUIDANCE) {
            LivingEntity locked = TargetLockManager.getLockedTarget(owner);
            if (locked != null) {
                targetId = locked.getUUID();
            } else if (targetingMode == TargetingMode.CROSSHAIR_LOCK) {
                    beginReturn();
                    return;
            }
        }

        Entity rawTarget = targetId == null ? null : serverLevel.getEntity(targetId);
        double baseRange = targetingMode == TargetingMode.CROSSHAIR_LOCK
                ? crosshairLockRadius : automaticTargetRadius;
        double maximumRange = targetingMode == TargetingMode.MANUAL_GUIDANCE
                ? Double.POSITIVE_INFINITY : baseRange + 12.0D;
        if (!(rawTarget instanceof LivingEntity target)
                || !SwordTargetingRules.canActivelyTarget(owner, target)
                || target.distanceToSqr(owner) > maximumRange * maximumRange) {
            beginReturn();
            return;
        }

        Vec3 aimPoint = target.position().add(0.0D, target.getBbHeight() * 0.55D, 0.0D);
        Vec3 previousPosition = position();
        flyToward(aimPoint, 0.25D, 1.18D);
        boolean crossedHitbox = target.getBoundingBox().inflate(0.3D).contains(position())
                || target.getBoundingBox().inflate(0.3D).clip(previousPosition, position()).isPresent();
        if (crossedHitbox || closeTo(aimPoint, 0.92D)) {
            damageLivingTarget(owner, target, 1.0D, true);
            Vec3 travel = safeDirection(getDeltaMovement(), new Vec3(0.0D, 1.0D, 0.0D));
            double followDistance = attackMode == AttackMode.RELENTLESS ? 3.2D : 1.15D;
            fixedWaypoint = position().add(travel.scale(followDistance)).add(0.0D, 0.25D, 0.0D);
            if (attackMode != AttackMode.RELENTLESS || !target.isAlive()) {
                targetId = null;
            }
            phase = FlightPhase.FOLLOW_THROUGH;
        }
    }

    private void tickFollowThrough(ServerLevel serverLevel) {
        if (fixedWaypoint == null) {
            beginReturn();
            return;
        }
        flyToward(fixedWaypoint, 0.14D, 0.96D);
        if (closeTo(fixedWaypoint, 0.2D)) {
            fixedWaypoint = null;
            Entity target = targetId == null ? null : serverLevel.getEntity(targetId);
            if (attackMode == AttackMode.RELENTLESS && target instanceof LivingEntity living && living.isAlive()) {
                Vec3 travel = safeDirection(getDeltaMovement(), new Vec3(0.0D, 0.0D, 1.0D));
                Vec3 side = travel.cross(new Vec3(0.0D, 1.0D, 0.0D));
                if (side.lengthSqr() < 1.0E-5D) side = new Vec3(1.0D, 0.0D, 0.0D);
                side = side.normalize().scale((formationSlot & 1) == 0 ? 2.35D : -2.35D);
                fixedWaypoint = living.position()
                        .add(0.0D, living.getBbHeight() * 0.75D + 0.7D, 0.0D)
                        .add(side)
                        .add(travel.scale(0.35D));
                phase = FlightPhase.RELENTLESS_ARC;
            } else {
                beginReturn();
            }
        }
    }

    private void tickRelentlessArc(ServerLevel serverLevel) {
        Entity target = targetId == null ? null : serverLevel.getEntity(targetId);
        if (!(target instanceof LivingEntity living) || !living.isAlive() || fixedWaypoint == null) {
            beginReturn();
            return;
        }
        flyToward(fixedWaypoint, 0.10D, 0.88D);
        if (closeTo(fixedWaypoint, 0.42D)) {
            fixedWaypoint = null;
            phase = FlightPhase.HOMING;
        }
    }

    private void tickSweep(ServerPlayer owner, ServerLevel serverLevel) {
        int duration = TechniqueConfig.sweepDuration();
        int approachTicks = 4;
        double progress = Mth.clamp((techniqueTicks - approachTicks) / (double) Math.max(1, duration), 0.0D, 1.0D);
        Vec3 forward = Vec3.directionFromRotation(0.0F, owner.getYRot()).normalize();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        double baseAngle = Math.PI * 2.0D * formationSlot / 6.0D;
        double angle = baseAngle - Math.PI / 3.0D
                + progress * Math.PI * 2.0D * TechniqueConfig.sweepRotations();
        Vec3 radial = forward.scale(Math.cos(angle)).add(right.scale(Math.sin(angle))).normalize();
        Vec3 desired = owner.position().add(0.0D, 0.95D + (formationSlot % 2) * 0.16D, 0.0D)
                .add(radial.scale(TechniqueConfig.sweepRadius()));
        Vec3 previous = position();
        Vec3 tangent = forward.scale(-Math.sin(angle)).add(right.scale(Math.cos(angle)));
        if (techniqueTicks < approachTicks) {
            flyToward(desired, 0.48D, 1.22D);
            faceDirection(tangent, 0.55D);
            techniqueTicks++;
            return;
        }
        Vec3 motion = desired.subtract(previous);
        setPos(desired);
        setDeltaMovement(motion);
        faceDirection(tangent, 0.75D);

        double hitWidth = TechniqueConfig.sweepHitWidth();
        AABB swept = new AABB(previous, desired).inflate(hitWidth);
        Vec3 sweepCentre = owner.position().add(0.0D, 1.0D, 0.0D);
        AABB spoke = new AABB(sweepCentre, desired).inflate(hitWidth * 0.72D,
                hitWidth * 0.55D, hitWidth * 0.72D);
        AABB query = swept.minmax(spoke);
        int limit = TechniqueConfig.sweepTargetLimit();
        for (LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class, query,
                candidate -> SwordTargetingRules.canActivelyTarget(owner, candidate)
                        && (swept.intersects(candidate.getBoundingBox())
                        || spoke.intersects(candidate.getBoundingBox())))) {
            if (techniqueHits.size() >= limit || !techniqueHits.add(target.getUUID())) continue;
            if (damageLivingTarget(owner, target, TechniqueConfig.sweepDamageScale(), false)) {
                techniqueDidDamage = true;
            }
        }
        techniqueTicks++;
        if (techniqueTicks > duration + approachTicks) {
            if (techniqueDidDamage) consumeSourceDurability(owner, 1);
            postDockCooldown = TechniqueConfig.sweepCooldown();
            beginReturn();
        }
    }

    private void tickSwordArray(ServerPlayer owner, ServerLevel serverLevel) {
        Entity raw = targetId == null ? null : serverLevel.getEntity(targetId);
        LivingEntity activeTarget = null;
        if (raw instanceof LivingEntity target && target.isAlive()
                && SwordTargetingRules.canActivelyTarget(owner, target)) {
            activeTarget = target;
            swordArrayAnchor = target.position();
            swordArrayTargetHeight = target.getBbHeight();
            swordArrayTargetWidth = target.getBbWidth();
        }
        if (swordArrayAnchor == null) {
            beginReturn();
            return;
        }
        Vec3 targetPoint = swordArrayAnchor.add(0.0D, swordArrayTargetHeight * 0.42D, 0.0D);
        Vec3 circleCentre = swordArrayAnchor.add(0.0D,
                swordArrayTargetHeight + TechniqueConfig.swordArrayHeight(), 0.0D);
        int gatherEnd = TechniqueConfig.swordArrayGatherTicks();
        int barrageStart = gatherEnd + TechniqueConfig.swordArrayHoldTicks();
        int barrageEnd = barrageStart + TechniqueConfig.swordArrayBarrageTicks();
        int burstStart = barrageEnd + TechniqueConfig.swordArrayFinisherChargeTicks()
                + TechniqueConfig.swordArrayFinisherHoldTicks();
        int expansionEnd = burstStart + TechniqueConfig.swordArrayFinisherExpandTicks();
        int finishLingerEnd = expansionEnd + TechniqueConfig.swordArrayFinisherSustainTicks();
        double baseRadius = Math.max(swordArrayTargetWidth * 0.5D
                + TechniqueConfig.swordArrayRadiusPadding(), 18.0D);
        double expansion = 1.0D;
        if (techniqueTicks >= burstStart) {
            double progress = Mth.clamp((techniqueTicks - burstStart)
                    / (double) Math.max(1, TechniqueConfig.swordArrayFinisherExpandTicks()), 0.0D, 1.0D);
            double explosive = 1.0D - Math.pow(1.0D - progress, 3.0D);
            expansion = Mth.lerp(explosive, 1.0D,
                    TechniqueConfig.swordArrayFinisherExpansion());
        } else if (techniqueTicks >= barrageStart) {
            expansion += 0.035D * Math.sin(techniqueTicks * 0.42D);
        }
        double radius = baseRadius * expansion;
        double angle = Math.PI * 2.0D * formationSlot / FlyingSwordItem.FORMATION_SIZE
                + techniqueTicks * 0.018D;
        Vec3 ringPosition = circleCentre.add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
        Vec3 desired = ringPosition;
        // At the first release all real implements plunge through the centre and rebound into
        // their slots. The field entity keeps the light-band seal continuous behind them.
        int plungeTicks = 16;
        boolean plunging = techniqueTicks >= barrageStart && techniqueTicks < barrageStart + plungeTicks;
        if (plunging) {
            double plungeProgress = (techniqueTicks - barrageStart) / (double) plungeTicks;
            double inward = Math.sin(Math.PI * plungeProgress);
            desired = ringPosition.lerp(targetPoint, inward * 0.94D);
        }
        Vec3 previous = position();
        flyToward(desired, plunging ? 0.68D : 0.44D, plunging ? 1.42D : 1.08D);
        faceDirection(targetPoint.subtract(position()), 0.82D);
        if (plunging && activeTarget != null && !techniqueHits.contains(activeTarget.getUUID())) {
            AABB hitbox = activeTarget.getBoundingBox().inflate(0.42D);
            if (hitbox.contains(position()) || hitbox.clip(previous, position()).isPresent()) {
                techniqueHits.add(activeTarget.getUUID());
                damageLivingTarget(owner, activeTarget, 1.0D, true);
            }
        }
        techniqueTicks++;
        if (techniqueTicks == gatherEnd && isSwordArrayCoordinator(owner)) {
            SwordArrayFieldEntity.spawn(serverLevel, owner, displayStack, sourceBindingId,
                    targetId, swordArrayAnchor, swordArrayTargetHeight, swordArrayTargetWidth);
        }
        if (techniqueTicks <= finishLingerEnd) return;
        postDockCooldown = TechniqueConfig.swordArrayCooldown();
        beginReturn();
    }

    private boolean isSwordArrayCoordinator(ServerPlayer owner) {
        return FlyingSwordItem.getOwnedFormationSwords(owner).stream()
                .filter(sword -> sword.phase == FlightPhase.SWORD_ARRAY
                        && java.util.Objects.equals(sword.targetId, targetId))
                .mapToInt(FlyingSwordEntity::getFormationSlot).min().orElse(formationSlot) == formationSlot;
    }

    private void tickToolApproach(ServerPlayer owner) {
        if (actionBlockPos == null) {
            beginReturn();
            return;
        }
        Vec3 normal = Vec3.atLowerCornerOf(actionFace.getNormal());
        Vec3 approach = Vec3.atCenterOf(actionBlockPos).add(normal.scale(0.92D));
        flyToward(approach, 0.24D, 0.82D);
        if (closeTo(approach, 0.25D)) {
            ItemStack source = FlyingSwordItem.findFlyingSword(owner, sourceBindingId);
            actionWorkTicks = ArtifactActionManager.miningTicks(owner, source, actionBlockPos);
            techniqueTicks = 0;
            phase = FlightPhase.TOOL_WORK;
        }
    }

    private void tickToolWork(ServerPlayer owner, ServerLevel serverLevel) {
        if (actionBlockPos == null || serverLevel.getBlockState(actionBlockPos).isAir()) {
            beginReturn();
            return;
        }
        Vec3 normal = Vec3.atLowerCornerOf(actionFace.getNormal());
        Vec3 swingUp = toolSwingUp(owner, normal);
        int swingPeriod = 8;
        double cycle = Math.floorMod(techniqueTicks, swingPeriod) / (double) swingPeriod;
        double stroke = 0.5D - 0.5D * Math.cos(cycle * Math.PI * 2.0D);
        // Ease the authored stroke so the implement accelerates into the block and spends more
        // of the return half visibly recovering, like an ordinary mining swing rather than a
        // repeated thrust along the face normal.
        double easedStroke = stroke * stroke * (3.0D - 2.0D * stroke);
        double swingAngle = 0.98D + (-0.14D - 0.98D) * easedStroke;
        Vec3 direction = normal.scale(-Math.cos(swingAngle))
                .add(swingUp.scale(Math.sin(swingAngle))).normalize();
        Vec3 base = Vec3.atCenterOf(actionBlockPos).add(normal.scale(0.92D));
        Vec3 swingPosition = base
                .add(swingUp.scale((1.0D - easedStroke) * 0.26D))
                .add(normal.scale(0.09D - easedStroke * 0.19D));
        Vec3 motion = swingPosition.subtract(position());
        setPos(swingPosition);
        setDeltaMovement(motion);
        faceDirection(direction, 1.0D);
        if (Math.floorMod(techniqueTicks, swingPeriod) == swingPeriod / 2) {
            serverLevel.sendParticles(ParticleTypes.CRIT, base.x, base.y, base.z, 3, 0.12D, 0.12D, 0.12D, 0.03D);
            net.minecraft.world.level.block.SoundType sound = serverLevel.getBlockState(actionBlockPos)
                    .getSoundType(serverLevel, actionBlockPos, owner);
            serverLevel.playSound(null, actionBlockPos, sound.getHitSound(), SoundSource.BLOCKS,
                    0.32F, sound.getPitch() * 0.88F);
        }
        techniqueTicks++;
        if (techniqueTicks >= actionWorkTicks) {
            if (ArtifactActionManager.completeMining(owner, this, actionBlockPos)) {
                SwordEffectEngine.applyWorkPulse(owner, Vec3.atCenterOf(actionBlockPos), installedModules);
            }
            actionBlockPos = null;
            postDockCooldown = 12;
            beginReturn();
        }
    }

    private static Vec3 toolSwingUp(ServerPlayer owner, Vec3 faceNormal) {
        if (Math.abs(faceNormal.y) < 0.85D) return new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 look = owner.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
        return horizontal.lengthSqr() < 1.0E-6D
                ? new Vec3(0.0D, 0.0D, 1.0D) : horizontal.normalize();
    }

    private void tickFishingApproach(ServerPlayer owner) {
        if (actionBlockPos == null) {
            beginReturn();
            return;
        }
        Vec3 hover = Vec3.atCenterOf(actionBlockPos).add(0.0D, 1.15D, 0.0D);
        flyToward(hover, 0.18D, 0.7D);
        if (closeTo(hover, 0.3D)) {
            techniqueTicks = 0;
            phase = FlightPhase.FISHING_WAIT;
        }
    }

    private void tickFishingWait(ServerPlayer owner, ServerLevel serverLevel) {
        if (actionBlockPos == null || !serverLevel.getFluidState(actionBlockPos)
                .is(net.minecraft.tags.FluidTags.WATER)) {
            beginReturn();
            return;
        }
        Vec3 hover = Vec3.atCenterOf(actionBlockPos)
                .add(0.0D, 1.12D + Math.sin(tickCount * 0.15D) * 0.07D, 0.0D);
        setPos(hover);
        setDeltaMovement(Vec3.ZERO);
        faceDirection(new Vec3(0.0D, -1.0D, 0.05D), 0.4D);
        if (techniqueTicks % 20 == 0) {
            serverLevel.sendParticles(ParticleTypes.BUBBLE, actionBlockPos.getX() + 0.5D,
                    actionBlockPos.getY() + 0.85D, actionBlockPos.getZ() + 0.5D,
                    4, 0.25D, 0.05D, 0.25D, 0.02D);
        }
        techniqueTicks++;
        if (techniqueTicks >= actionWorkTicks) {
            serverLevel.sendParticles(ParticleTypes.SPLASH, actionBlockPos.getX() + 0.5D,
                    actionBlockPos.getY() + 1.0D, actionBlockPos.getZ() + 0.5D,
                    14, 0.35D, 0.15D, 0.35D, 0.12D);
            serverLevel.playSound(null, actionBlockPos, SoundEvents.FISHING_BOBBER_SPLASH,
                    SoundSource.PLAYERS, 0.8F, 1.15F);
            ArtifactActionManager.completeFishing(owner, this, actionBlockPos);
            SwordEffectEngine.applyWorkPulse(owner, Vec3.atCenterOf(actionBlockPos).add(0.0D, 1.0D, 0.0D),
                    installedModules);
            actionBlockPos = null;
            postDockCooldown = 20;
            beginReturn();
        }
    }

    private void beginReturn() {
        if (targetingMode == TargetingMode.MANUAL_GUIDANCE && level() instanceof ServerLevel serverLevel
                && ownerId != null) {
            Player owner = serverLevel.getPlayerByUUID(ownerId);
            if (owner instanceof ServerPlayer serverPlayer) {
                ManualGuidanceManager.onSwordReturning(serverPlayer, this);
            }
        }
        targetId = null;
        fixedWaypoint = null;
        swordArrayAnchor = null;
        phase = FlightPhase.RETURN_RALLY;
    }

    private boolean damageLivingTarget(ServerPlayer owner, LivingEntity target,
                                       double damageScale, boolean consumeDurability) {
        if (!(level() instanceof ServerLevel serverLevel)) return false;
        double baseDamage = WanxiangSwordData.isTempered(displayStack)
                ? WanxiangWeaponCatalog.damage(serverLevel.getServer(), displayStack)
                : WanxiangSwordData.pierceDamage(displayStack);
        if (baseDamage <= 0.0D) baseDamage = SwordBalanceConfig.get(material).damage();
        double damage = FlyingSwordDamage.currentDamage(owner, displayStack,
                baseDamage + SwordEffectEngine.damageBonus(installedModules), target.getMobType())
                * Math.max(0.0D, damageScale);
        boolean markedTrialHit = ManualSpiritTrialManager.beginFlyingSwordDamage(owner, target, displayStack);
        boolean successfulHit;
        try {
            successfulHit = target.hurt(damageSources().playerAttack(owner), (float) damage);
        } finally {
            if (markedTrialHit) ManualSpiritTrialManager.endFlyingSwordDamage(owner);
        }
        ModNetwork.sendSwordImpact(this, target, safeDirection(getDeltaMovement(),
                new Vec3(0.0D, 1.0D, 0.0D)));
        if (successfulHit) {
            SwordEffectEngine.applyOnHit(owner, target, installedModules);
            if (consumeDurability) consumeSourceDurability(owner, 1);
        }
        return successfulHit;
    }

    /** Gives the player-level combo coordinator exclusive, server-authoritative control. */
    public void enterComboControl() {
        if (rideSupport) return;
        targetId = null;
        fixedWaypoint = null;
        swordArrayAnchor = null;
        techniqueHits.clear();
        setDeltaMovement(Vec3.ZERO);
        phase = FlightPhase.COMBO;
        entityData.set(DATA_COMBO_CONTROLLED, true);
    }

    /** Applies an authored combo pose without the inertia used by ordinary long-range flight. */
    public void applyComboPose(Vec3 desiredPosition, Vec3 facingDirection) {
        if (rideSupport || desiredPosition == null) return;
        if (phase != FlightPhase.COMBO) enterComboControl();
        Vec3 previous = position();
        setPos(desiredPosition);
        setDeltaMovement(desiredPosition.subtract(previous));
        faceDirection(safeDirection(facingDirection, getDeltaMovement().lengthSqr() > 1.0E-6D
                ? getDeltaMovement() : new Vec3(0.0D, 0.0D, 1.0D)), 1.0D);
    }

    /** Performs one logical combo hit while preserving this sword's modules and durability rules. */
    public boolean applyComboHit(ServerPlayer owner, LivingEntity target, double damageScale,
                                 boolean consumeDurability) {
        return target != null && target.isAlive()
                && damageLivingTarget(owner, target, damageScale, consumeDurability);
    }

    /** Returns to formation after combo ownership ends. */
    public void leaveComboControl(int cooldownTicks) {
        if (phase != FlightPhase.COMBO) return;
        entityData.set(DATA_COMBO_CONTROLLED, false);
        postDockCooldown = Math.max(0, cooldownTicks);
        beginReturn();
    }

    private void tickComboControl() {
        // Position and orientation are written by SwordComboManager once per server tick.
        // Deliberately do not integrate velocity here; this keeps six fast implements frame-tight.
        setDeltaMovement(Vec3.ZERO);
    }

    /**
     * Vanilla remote-entity interpolation chases each position packet for several ticks. That is
     * appropriate for mobs, but makes a sword authored relative to a moving owner look tethered.
     * Combo poses already arrive every tick, so one network step plus normal frame interpolation
     * keeps them attached without sacrificing sub-tick smoothness.
     */
    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch,
                       int increments, boolean teleport) {
        super.lerpTo(x, y, z, yaw, pitch,
                entityData.get(DATA_COMBO_CONTROLLED) ? 1 : increments, teleport);
    }

    public void consumeSourceDurability(ServerPlayer owner, int requestedCost) {
        ItemStack stack = FlyingSwordItem.findFlyingSword(owner, sourceBindingId);
        if (stack.isEmpty()) stack = FlyingSwordItem.findFlyingSword(owner, material, series);
        if (stack.isEmpty()) {
            discard();
            return;
        }
        if (stack.getTag() != null && stack.getTag().getBoolean("Unbreakable")) return;
        int durabilityCost = Math.max(0, requestedCost);
        if (WanxiangSwordData.isTempered(stack)) {
            durabilityCost *= WanxiangWeaponCatalog.durabilityCost(owner.server, stack);
        }
        durabilityCost = SwordModuleData.consumeVirtualDurability(stack, durabilityCost);
        if (durabilityCost <= 0) return;
        if (stack.hurt(durabilityCost, owner.getRandom(), owner)) {
            stack.shrink(1);
            FlyingSwordItem.getOwnedFormationSwords(owner).stream()
                    .filter(sword -> java.util.Objects.equals(sword.sourceBindingId, sourceBindingId))
                    .forEach(Entity::discard);
        }
    }

    private void tickReturnRally(ServerPlayer owner) {
        Vec3 rally = FormationGeometry.returnRallyPoint(owner, formationSlot);
        flyToward(rally, 0.19D, 1.02D);
        if (closeTo(rally, 0.5D)) {
            phase = FlightPhase.RETURN_APPROACH;
        }
    }

    private void tickReturnApproach(ServerPlayer owner) {
        Vec3 approach = FormationGeometry.returnApproachPoint(owner, formationSlot, formationMode, tickCount);
        flyToward(approach, 0.22D, 0.82D);
        if (closeTo(approach, 0.35D)) {
            phase = FlightPhase.DOCKING;
        }
    }

    private void tickDocking(ServerPlayer owner) {
        Vec3 dock = FormationGeometry.dockPosition(owner, formationSlot, formationMode, tickCount);
        flyToward(dock, 0.25D, 0.58D);
        if (closeTo(dock, 0.2D)) {
            setDeltaMovement(Vec3.ZERO);
            attackCooldown = Math.max(minimumDockTicks, postDockCooldown);
            postDockCooldown = 0;
            phase = FlightPhase.DOCKED;
        }
    }

    private Optional<LivingEntity> findTarget(ServerPlayer owner) {
        LivingEntity locked = TargetLockManager.getLockedTarget(owner);
        if (locked != null) return Optional.of(locked);
        if (targetingMode != TargetingMode.AUTOMATIC) return Optional.empty();
        AABB area = owner.getBoundingBox().inflate(automaticTargetRadius,
                Math.max(7.0D, automaticTargetRadius * 0.6D), automaticTargetRadius);
        return level().getEntitiesOfClass(LivingEntity.class, area,
                        entity -> entity instanceof Enemy && entity.isAlive() && owner.hasLineOfSight(entity))
                .stream()
                .min(Comparator.comparingDouble(owner::distanceToSqr));
    }

    private void flyToward(Vec3 destination, double steering, double maxSpeed) {
        double multiplier = SwordBalanceConfig.get(material).flightSpeed();
        if (WanxiangSwordData.isTempered(displayStack) && level().getServer() != null) {
            multiplier *= WanxiangWeaponCatalog.flightSpeedMultiplier(level().getServer(), displayStack);
        }
        updateVelocity(destination, steering, maxSpeed * multiplier);
        Vec3 motion = getDeltaMovement();
        setPos(position().add(motion));
        if (motion.lengthSqr() > 1.0E-5D) {
            faceDirection(motion, 0.28D);
        }
    }

    private void updateVelocity(Vec3 destination, double steering, double maxSpeed) {
        Vec3 offset = destination.subtract(position());
        Vec3 desired = offset.lengthSqr() < 1.0E-6D
                ? Vec3.ZERO
                : offset.normalize().scale(Math.min(maxSpeed, Math.max(0.08D, offset.length())));
        Vec3 motion = getDeltaMovement().scale(1.0D - steering).add(desired.scale(steering));
        if (motion.length() > maxSpeed) {
            motion = motion.normalize().scale(maxSpeed);
        }
        setDeltaMovement(motion);
    }

    private void faceDirection(Vec3 requestedDirection, double turnRate) {
        Vec3 current = Vec3.directionFromRotation(getXRot(), getYRot());
        Vec3 target = safeDirection(requestedDirection, current);
        Vec3 blended = safeDirection(current.scale(1.0D - turnRate).add(target.scale(turnRate)), target);
        setYRot((float) (Mth.atan2(-blended.x, blended.z) * Mth.RAD_TO_DEG));
        setXRot((float) (Mth.atan2(-blended.y, blended.horizontalDistance()) * Mth.RAD_TO_DEG));
    }

    private static Vec3 safeDirection(Vec3 direction, Vec3 fallback) {
        return direction.lengthSqr() < 1.0E-6D ? fallback.normalize() : direction.normalize();
    }

    private boolean closeTo(Vec3 point, double radius) {
        return position().distanceToSqr(point) <= radius * radius;
    }

    public ItemStack getDisplayItem() {
        ItemStack synced = entityData.get(DATA_DISPLAY_STACK);
        if (!synced.isEmpty()) return synced.copy();
        ItemStack fallback = new ItemStack(ModItems.getFlyingSword(getVisualMaterial(), getVisualSeries()));
        fallback.getOrCreateTag().putBoolean(FlyingSwordItem.ENTITY_DISPLAY_TAG, true);
        return fallback;
    }

    public FormationMode getVisualFormationMode() {
        int index = Mth.clamp(entityData.get(DATA_FORMATION_MODE), 0, FormationMode.values().length - 1);
        return FormationMode.values()[index];
    }

    public int getVisualFormationSlot() {
        return entityData.get(DATA_FORMATION_SLOT);
    }

    public TechniqueMode getVisualTechniqueMode() {
        return TechniqueMode.fromOrdinal(entityData.get(DATA_TECHNIQUE));
    }

    public boolean isVisuallyDocked() {
        return entityData.get(DATA_DOCKED);
    }

    public Player getVisualOwner() {
        return entityData.get(DATA_OWNER_ID).map(level()::getPlayerByUUID).orElse(null);
    }

    public FlyingSwordMaterial getVisualMaterial() {
        return FlyingSwordMaterial.fromOrdinal(entityData.get(DATA_MATERIAL));
    }

    public FlyingSwordSeries getVisualSeries() {
        return FlyingSwordSeries.fromOrdinal(entityData.get(DATA_SERIES));
    }

    public boolean hasVisualWhiteHotModule() {
        return entityData.get(DATA_WHITE_HOT);
    }

    public int getVisualModuleLevel(FlyingSwordModule module) {
        return SwordModuleData.visualEffectLevel(entityData.get(DATA_VISUAL_MODULES), module);
    }

    public int getVisualModuleMask() {
        return entityData.get(DATA_VISUAL_MODULES);
    }

    public boolean isVisualPreview() {
        return visualPreview;
    }

    /** Configures an unspawned client entity so the workbench renders the real sword pipeline. */
    public void configureVisualPreview(ItemStack stack) {
        if (!WanxiangSwordData.isUsable(stack)) return;
        material = WanxiangSwordData.material(stack);
        series = WanxiangSwordData.series(stack);
        installedModules = SwordModuleData.copyModules(stack);
        displayStack = displayCopy(stack);
        sourceBindingId = WanxiangSwordData.binding(stack);
        visualPreview = true;
        phase = FlightPhase.DOCKED;
        entityData.set(DATA_MATERIAL, material.ordinal());
        entityData.set(DATA_SERIES, series.ordinal());
        entityData.set(DATA_DOCKED, true);
        entityData.set(DATA_OWNER_ID, Optional.empty());
        entityData.set(DATA_WHITE_HOT,
                SwordModuleData.getLevel(installedModules, FlyingSwordModule.WHITE_HOT) > 0);
        entityData.set(DATA_VISUAL_MODULES, SwordModuleData.packVisualEffects(installedModules));
        entityData.set(DATA_RIDE_SUPPORT, false);
        entityData.set(DATA_DISPLAY_STACK, displayStack.copy());
    }

    /** Client-only presentation form used by the VFX field renderer. It deliberately carries the
     * complete item, glow mode and module mask so colossal copies use the exact flying-sword
     * rendering pipeline rather than an approximate item plus a generic shell. */
    public void configureTechniqueVisualPreview(ItemStack stack, int slot, float pitch, float yaw,
                                                int visualTick) {
        configureVisualPreview(stack);
        visualPreview = true;
        phase = FlightPhase.SWORD_ARRAY;
        formationSlot = Math.floorMod(slot, FlyingSwordItem.FORMATION_SIZE);
        tickCount = Math.max(0, visualTick);
        setXRot(pitch);
        xRotO = pitch;
        setYRot(yaw);
        yRotO = yaw;
        entityData.set(DATA_DOCKED, false);
        entityData.set(DATA_FORMATION_SLOT, formationSlot);
        entityData.set(DATA_TECHNIQUE, TechniqueMode.SWORD_ARRAY.ordinal());
    }

    public boolean isVisualRideSupport() {
        return entityData.get(DATA_RIDE_SUPPORT);
    }

    public boolean isVisualComboControlled() {
        return entityData.get(DATA_COMBO_CONTROLLED);
    }

    public UUID getSourceBindingId() {
        return sourceBindingId;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) ownerId = tag.getUUID("Owner");
        if (tag.hasUUID("Target")) targetId = tag.getUUID("Target");
        formationSlot = tag.getInt("FormationSlot");
        attackCooldown = tag.getInt("AttackCooldown");
        formationMode = FormationMode.fromName(tag.getString("FormationMode"));
        minimumDockTicks = tag.contains("MinimumDockTicks")
                ? tag.getInt("MinimumDockTicks") : SwordSettings.DEFAULT_MINIMUM_DOCK_TICKS;
        automaticTargetRadius = tag.contains("AutomaticTargetRadius")
                ? tag.getDouble("AutomaticTargetRadius") : SwordSettings.DEFAULT_AUTOMATIC_RADIUS;
        crosshairLockRadius = tag.contains("CrosshairLockRadius")
                ? tag.getDouble("CrosshairLockRadius") : SwordSettings.DEFAULT_LOCK_RADIUS;
        targetingMode = TargetingMode.fromOrdinal(tag.getInt("TargetingMode"));
        attackMode = AttackMode.fromOrdinal(tag.getInt("AttackMode"));
        techniqueMode = tag.contains("TechniqueMode")
                ? TechniqueMode.fromOrdinal(tag.getInt("TechniqueMode")) : TechniqueMode.PIERCE;
        material = tag.contains("Material")
                ? FlyingSwordMaterial.fromOrdinal(tag.getInt("Material")) : FlyingSwordMaterial.IRON;
        series = tag.contains("Series")
                ? FlyingSwordSeries.fromOrdinal(tag.getInt("Series")) : FlyingSwordSeries.STANDARD;
        installedModules = tag.contains(SwordModuleData.ROOT_TAG)
                ? tag.getCompound(SwordModuleData.ROOT_TAG).copy() : new CompoundTag();
        rideSupport = tag.getBoolean("RideSupport");
        sourceBindingId = tag.hasUUID("SourceBindingId") ? tag.getUUID("SourceBindingId") : null;
        displayStack = tag.contains("DisplayItem") ? ItemStack.of(tag.getCompound("DisplayItem")) : ItemStack.EMPTY;
        int phaseIndex = Mth.clamp(tag.getInt("FlightPhase"), 0, FlightPhase.values().length - 1);
        phase = FlightPhase.values()[phaseIndex];
        // Combo sessions intentionally are not persisted; after a restart each implement safely
        // resumes its ordinary return path instead of waiting for an absent player coordinator.
        if (phase == FlightPhase.COMBO) phase = FlightPhase.RETURN_RALLY;
        entityData.set(DATA_FORMATION_SLOT, formationSlot);
        entityData.set(DATA_FORMATION_MODE, formationMode.ordinal());
        entityData.set(DATA_DOCKED, phase == FlightPhase.DOCKED);
        entityData.set(DATA_OWNER_ID, Optional.ofNullable(ownerId));
        entityData.set(DATA_MATERIAL, material.ordinal());
        entityData.set(DATA_SERIES, series.ordinal());
        entityData.set(DATA_WHITE_HOT,
                SwordModuleData.getLevel(installedModules, FlyingSwordModule.WHITE_HOT) > 0);
        entityData.set(DATA_VISUAL_MODULES, SwordModuleData.packVisualEffects(installedModules));
        entityData.set(DATA_RIDE_SUPPORT, rideSupport);
        entityData.set(DATA_DISPLAY_STACK, displayStack.copy());
        entityData.set(DATA_TECHNIQUE, techniqueMode.ordinal());
        entityData.set(DATA_COMBO_CONTROLLED, false);
        if (tag.contains("ManualLaunchX")) {
            manualLaunchDirection = new Vec3(tag.getDouble("ManualLaunchX"), tag.getDouble("ManualLaunchY"),
                    tag.getDouble("ManualLaunchZ"));
            manualLaunchTicks = tag.getInt("ManualLaunchTicks");
        }
        if (tag.contains("WaypointX")) {
            fixedWaypoint = new Vec3(tag.getDouble("WaypointX"), tag.getDouble("WaypointY"), tag.getDouble("WaypointZ"));
        }
        techniqueTicks = tag.getInt("TechniqueTicks");
        if (tag.contains("SwordArrayAnchorX")) {
            swordArrayAnchor = new Vec3(tag.getDouble("SwordArrayAnchorX"),
                    tag.getDouble("SwordArrayAnchorY"), tag.getDouble("SwordArrayAnchorZ"));
            swordArrayTargetHeight = tag.getDouble("SwordArrayTargetHeight");
            swordArrayTargetWidth = tag.getDouble("SwordArrayTargetWidth");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        if (targetId != null) tag.putUUID("Target", targetId);
        tag.putInt("FormationSlot", formationSlot);
        tag.putInt("AttackCooldown", attackCooldown);
        tag.putString("FormationMode", formationMode.serializedName());
        tag.putInt("MinimumDockTicks", minimumDockTicks);
        tag.putDouble("AutomaticTargetRadius", automaticTargetRadius);
        tag.putDouble("CrosshairLockRadius", crosshairLockRadius);
        tag.putInt("TargetingMode", targetingMode.ordinal());
        tag.putInt("AttackMode", attackMode.ordinal());
        tag.putInt("TechniqueMode", techniqueMode.ordinal());
        tag.putInt("Material", material.ordinal());
        tag.putInt("Series", series.ordinal());
        if (!installedModules.isEmpty()) tag.put(SwordModuleData.ROOT_TAG, installedModules.copy());
        tag.putBoolean("RideSupport", rideSupport);
        if (sourceBindingId != null) tag.putUUID("SourceBindingId", sourceBindingId);
        if (!displayStack.isEmpty()) tag.put("DisplayItem", displayStack.save(new CompoundTag()));
        tag.putInt("FlightPhase", phase.ordinal());
        tag.putInt("TechniqueTicks", techniqueTicks);
        if (fixedWaypoint != null) {
            tag.putDouble("WaypointX", fixedWaypoint.x);
            tag.putDouble("WaypointY", fixedWaypoint.y);
            tag.putDouble("WaypointZ", fixedWaypoint.z);
        }
        if (manualLaunchDirection != null) {
            tag.putDouble("ManualLaunchX", manualLaunchDirection.x);
            tag.putDouble("ManualLaunchY", manualLaunchDirection.y);
            tag.putDouble("ManualLaunchZ", manualLaunchDirection.z);
            tag.putInt("ManualLaunchTicks", manualLaunchTicks);
        }
        if (swordArrayAnchor != null) {
            tag.putDouble("SwordArrayAnchorX", swordArrayAnchor.x);
            tag.putDouble("SwordArrayAnchorY", swordArrayAnchor.y);
            tag.putDouble("SwordArrayAnchorZ", swordArrayAnchor.z);
            tag.putDouble("SwordArrayTargetHeight", swordArrayTargetHeight);
            tag.putDouble("SwordArrayTargetWidth", swordArrayTargetWidth);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    private static ItemStack displayCopy(ItemStack source) {
        ItemStack display = source.copy();
        display.setCount(1);
        display.getOrCreateTag().putBoolean(FlyingSwordItem.ENTITY_DISPLAY_TAG, true);
        return display;
    }
}
