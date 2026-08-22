package dev.swordflight.entity;

import dev.swordflight.formation.FormationGeometry;
import dev.swordflight.formation.FormationMode;
import dev.swordflight.combat.AttackMode;
import dev.swordflight.combat.SwordSettings;
import dev.swordflight.combat.TargetingMode;
import dev.swordflight.combat.TargetLockManager;
import dev.swordflight.combat.ManualGuidanceManager;
import dev.swordflight.flight.SwordRidingManager;
import dev.swordflight.registry.ModItems;
import dev.swordflight.material.FlyingSwordMaterial;
import dev.swordflight.config.SwordBalanceConfig;
import dev.swordflight.combat.SwordEffectEngine;
import dev.swordflight.combat.SwordTargetingRules;
import dev.swordflight.item.FlyingSwordItem;
import dev.swordflight.upgrade.SwordModuleData;
import dev.swordflight.upgrade.FlyingSwordModule;
import dev.swordflight.visual.FlyingSwordSeries;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.Comparator;
import java.util.Optional;
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
    private static final EntityDataAccessor<Boolean> DATA_RIDE_SUPPORT =
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
    private FlyingSwordMaterial material = FlyingSwordMaterial.IRON;
    private FlyingSwordSeries series = FlyingSwordSeries.STANDARD;
    private CompoundTag installedModules = new CompoundTag();
    private boolean rideSupport;
    private Vec3 manualLaunchDirection;
    private int manualLaunchTicks;

    public FlyingSwordEntity(EntityType<? extends FlyingSwordEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public void bindTo(ServerPlayer owner, int slot, FormationMode mode, SwordSettings settings,
                       FlyingSwordMaterial material, FlyingSwordSeries series, CompoundTag installedModules) {
        ownerId = owner.getUUID();
        entityData.set(DATA_OWNER_ID, Optional.of(ownerId));
        formationSlot = slot;
        formationMode = mode;
        applySettings(settings);
        this.material = material;
        this.series = series;
        this.installedModules = installedModules.copy();
        entityData.set(DATA_MATERIAL, material.ordinal());
        entityData.set(DATA_SERIES, series.ordinal());
        entityData.set(DATA_WHITE_HOT,
                SwordModuleData.getLevel(this.installedModules, FlyingSwordModule.WHITE_HOT) > 0);
        entityData.set(DATA_FORMATION_SLOT, slot);
        entityData.set(DATA_FORMATION_MODE, mode.ordinal());
        attackCooldown = 20 + slot * 7;
    }

    public void bindAsRideSupport(ServerPlayer owner, FlyingSwordMaterial material,
                                  FlyingSwordSeries series, CompoundTag installedModules) {
        ownerId = owner.getUUID();
        entityData.set(DATA_OWNER_ID, Optional.of(ownerId));
        this.material = material;
        this.series = series;
        this.installedModules = installedModules.copy();
        rideSupport = true;
        phase = FlightPhase.RIDE_SUPPORT;
        formationSlot = 0;
        entityData.set(DATA_MATERIAL, material.ordinal());
        entityData.set(DATA_SERIES, series.ordinal());
        entityData.set(DATA_FORMATION_SLOT, 0);
        entityData.set(DATA_DOCKED, false);
        entityData.set(DATA_RIDE_SUPPORT, true);
        entityData.set(DATA_WHITE_HOT,
                SwordModuleData.getLevel(this.installedModules, FlyingSwordModule.WHITE_HOT) > 0);
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
        return !rideSupport && phase == FlightPhase.DOCKED && attackCooldown <= 0;
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
        entityData.define(DATA_RIDE_SUPPORT, false);
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
        }
        entityData.set(DATA_DOCKED, phase == FlightPhase.DOCKED);
    }

    private void tickDocked(ServerPlayer owner) {
        Vec3 dock = FormationGeometry.dockPosition(owner, formationSlot, formationMode, tickCount);
        setPos(dock);
        setDeltaMovement(Vec3.ZERO);
        faceDirection(FormationGeometry.dockDirection(owner, dock, formationMode), 0.34D);

        if (attackCooldown <= 0) {
            findTarget(owner).ifPresent(target -> beginAttack(owner, target));
        }
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
            double damage = SwordBalanceConfig.get(material).damage()
                    + SwordEffectEngine.damageBonus(installedModules);
            boolean successfulHit = target.hurt(damageSources().playerAttack(owner), (float) damage);
            if (successfulHit) {
                SwordEffectEngine.applyOnHit(owner, target, installedModules);
                damageSourceSword(owner);
            }
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
        phase = FlightPhase.RETURN_RALLY;
    }

    private void damageSourceSword(ServerPlayer owner) {
        ItemStack stack = FlyingSwordItem.findFlyingSword(owner, material, series);
        if (stack.isEmpty()) {
            discard();
            return;
        }
        if (stack.getTag() != null && stack.getTag().getBoolean("Unbreakable")) return;
        if (stack.hurt(1, owner.getRandom(), owner)) {
            stack.shrink(1);
            FlyingSwordItem.getOwnedFormationSwords(owner).stream()
                    .filter(sword -> sword.material == material && sword.series == series)
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
            attackCooldown = minimumDockTicks;
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
        updateVelocity(destination, steering, maxSpeed * SwordBalanceConfig.get(material).flightSpeed());
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
        ItemStack display = new ItemStack(ModItems.getFlyingSword(getVisualMaterial(), getVisualSeries()));
        display.getOrCreateTag().putBoolean(FlyingSwordItem.ENTITY_DISPLAY_TAG, true);
        return display;
    }

    public FormationMode getVisualFormationMode() {
        int index = Mth.clamp(entityData.get(DATA_FORMATION_MODE), 0, FormationMode.values().length - 1);
        return FormationMode.values()[index];
    }

    public int getVisualFormationSlot() {
        return entityData.get(DATA_FORMATION_SLOT);
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

    public boolean isVisualRideSupport() {
        return entityData.get(DATA_RIDE_SUPPORT);
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
        material = tag.contains("Material")
                ? FlyingSwordMaterial.fromOrdinal(tag.getInt("Material")) : FlyingSwordMaterial.IRON;
        series = tag.contains("Series")
                ? FlyingSwordSeries.fromOrdinal(tag.getInt("Series")) : FlyingSwordSeries.STANDARD;
        installedModules = tag.contains(SwordModuleData.ROOT_TAG)
                ? tag.getCompound(SwordModuleData.ROOT_TAG).copy() : new CompoundTag();
        rideSupport = tag.getBoolean("RideSupport");
        int phaseIndex = Mth.clamp(tag.getInt("FlightPhase"), 0, FlightPhase.values().length - 1);
        phase = FlightPhase.values()[phaseIndex];
        entityData.set(DATA_FORMATION_SLOT, formationSlot);
        entityData.set(DATA_FORMATION_MODE, formationMode.ordinal());
        entityData.set(DATA_DOCKED, phase == FlightPhase.DOCKED);
        entityData.set(DATA_OWNER_ID, Optional.ofNullable(ownerId));
        entityData.set(DATA_MATERIAL, material.ordinal());
        entityData.set(DATA_SERIES, series.ordinal());
        entityData.set(DATA_WHITE_HOT,
                SwordModuleData.getLevel(installedModules, FlyingSwordModule.WHITE_HOT) > 0);
        entityData.set(DATA_RIDE_SUPPORT, rideSupport);
        if (tag.contains("ManualLaunchX")) {
            manualLaunchDirection = new Vec3(tag.getDouble("ManualLaunchX"), tag.getDouble("ManualLaunchY"),
                    tag.getDouble("ManualLaunchZ"));
            manualLaunchTicks = tag.getInt("ManualLaunchTicks");
        }
        if (tag.contains("WaypointX")) {
            fixedWaypoint = new Vec3(tag.getDouble("WaypointX"), tag.getDouble("WaypointY"), tag.getDouble("WaypointZ"));
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
        tag.putInt("Material", material.ordinal());
        tag.putInt("Series", series.ordinal());
        if (!installedModules.isEmpty()) tag.put(SwordModuleData.ROOT_TAG, installedModules.copy());
        tag.putBoolean("RideSupport", rideSupport);
        tag.putInt("FlightPhase", phase.ordinal());
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
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
