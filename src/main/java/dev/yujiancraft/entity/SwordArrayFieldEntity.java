package dev.yujiancraft.entity;

import dev.yujiancraft.combat.SwordEffectEngine;
import dev.yujiancraft.combat.SwordTargetingRules;
import dev.yujiancraft.config.TechniqueConfig;
import dev.yujiancraft.item.FlyingSwordItem;
import dev.yujiancraft.network.ModNetwork;
import dev.yujiancraft.registry.ModEntities;
import dev.yujiancraft.upgrade.SwordModuleData;
import dev.yujiancraft.wanxiang.FlyingSwordDamage;
import dev.yujiancraft.wanxiang.ManualSpiritTrialManager;
import dev.yujiancraft.wanxiang.WanxiangSwordData;
import dev.yujiancraft.wanxiang.WanxiangWeaponCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One server-authoritative sword-array performance. A single entity replaces hundreds of ring
 * particles with client geometry, follows the target through the barrage, then freezes and
 * performs the sustained heaven-to-ground finisher.
 */
public final class SwordArrayFieldEntity extends Entity {
    /** Server-side ownership lock: one target may carry only one living sword-array field. */
    private static final Map<TargetKey, UUID> ACTIVE_BY_TARGET = new ConcurrentHashMap<>();
    private static final EntityDataAccessor<ItemStack> DATA_DISPLAY_STACK =
            SynchedEntityData.defineId(SwordArrayFieldEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(SwordArrayFieldEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Float> DATA_BASE_RADIUS =
            SynchedEntityData.defineId(SwordArrayFieldEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BEAM_HEIGHT =
            SynchedEntityData.defineId(SwordArrayFieldEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_FINISHER_START =
            SynchedEntityData.defineId(SwordArrayFieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CHARGE_TICKS =
            SynchedEntityData.defineId(SwordArrayFieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HOLD_TICKS =
            SynchedEntityData.defineId(SwordArrayFieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_EXPAND_TICKS =
            SynchedEntityData.defineId(SwordArrayFieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SUSTAIN_TICKS =
            SynchedEntityData.defineId(SwordArrayFieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_EXPANSION =
            SynchedEntityData.defineId(SwordArrayFieldEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BEAM_SCALE =
            SynchedEntityData.defineId(SwordArrayFieldEntity.class, EntityDataSerializers.FLOAT);

    private UUID ownerId;
    private UUID targetId;
    private UUID sourceBindingId;
    private ItemStack displayStack = ItemStack.EMPTY;
    private Vec3 lastTargetAnchor;
    private double targetHeight = 1.8D;
    private double targetWidth = 0.6D;
    private int age;
    private boolean finisherStarted;
    private boolean burstApplied;
    private boolean consumedDurability;
    private final Set<UUID> hitTargets = new HashSet<>();

    public SwordArrayFieldEntity(EntityType<? extends SwordArrayFieldEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        noCulling = true;
    }

    public static void spawn(ServerLevel level, ServerPlayer owner, ItemStack source, UUID bindingId,
                             UUID targetId, Vec3 targetAnchor, double targetHeight, double targetWidth) {
        if (targetId == null) return;
        TargetKey key = new TargetKey(level.dimension(), targetId);
        UUID activeId = ACTIVE_BY_TARGET.get(key);
        if (activeId != null) {
            Entity active = level.getEntity(activeId);
            if (active instanceof SwordArrayFieldEntity && active.isAlive()) return;
            ACTIVE_BY_TARGET.remove(key, activeId);
        }
        // Rebuild the lock after chunk/server reloads, where the in-memory map is intentionally empty.
        AABB nearby = new AABB(targetAnchor, targetAnchor).inflate(96.0D, 96.0D, 96.0D);
        for (SwordArrayFieldEntity existing : level.getEntitiesOfClass(SwordArrayFieldEntity.class, nearby,
                field -> targetId.equals(field.targetId) && field.isAlive())) {
            ACTIVE_BY_TARGET.put(key, existing.getUUID());
            return;
        }
        SwordArrayFieldEntity field = ModEntities.SWORD_ARRAY_FIELD.get().create(level);
        if (field == null) return;
        field.ownerId = owner.getUUID();
        field.targetId = targetId;
        field.sourceBindingId = bindingId;
        field.displayStack = source.copy();
        field.displayStack.setCount(1);
        field.lastTargetAnchor = targetAnchor;
        field.targetHeight = targetHeight;
        field.targetWidth = targetWidth;
        field.syncStaticData();
        field.updateAnchor(level, targetAnchor, targetHeight, targetWidth);
        if (level.addFreshEntity(field)) ACTIVE_BY_TARGET.put(key, field.getUUID());
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_DISPLAY_STACK, ItemStack.EMPTY);
        entityData.define(DATA_OWNER, Optional.empty());
        entityData.define(DATA_BASE_RADIUS, 18.0F);
        entityData.define(DATA_BEAM_HEIGHT, 28.0F);
        entityData.define(DATA_FINISHER_START, 72);
        entityData.define(DATA_CHARGE_TICKS, 10);
        entityData.define(DATA_HOLD_TICKS, 8);
        entityData.define(DATA_EXPAND_TICKS, 7);
        entityData.define(DATA_SUSTAIN_TICKS, 32);
        entityData.define(DATA_EXPANSION, 2.25F);
        entityData.define(DATA_BEAM_SCALE, 0.92F);
    }

    private void syncStaticData() {
        entityData.set(DATA_OWNER, Optional.ofNullable(ownerId));
        entityData.set(DATA_DISPLAY_STACK, displayStack.copy());
        entityData.set(DATA_FINISHER_START,
                TechniqueConfig.swordArrayHoldTicks() + TechniqueConfig.swordArrayBarrageTicks());
        entityData.set(DATA_CHARGE_TICKS, TechniqueConfig.swordArrayFinisherChargeTicks());
        entityData.set(DATA_HOLD_TICKS, TechniqueConfig.swordArrayFinisherHoldTicks());
        entityData.set(DATA_EXPAND_TICKS, TechniqueConfig.swordArrayFinisherExpandTicks());
        entityData.set(DATA_SUSTAIN_TICKS, TechniqueConfig.swordArrayFinisherSustainTicks());
        entityData.set(DATA_EXPANSION, (float) TechniqueConfig.swordArrayFinisherExpansion());
        entityData.set(DATA_BEAM_SCALE, (float) TechniqueConfig.swordArrayFinisherBeamScale());
    }

    @Override
    public void tick() {
        super.tick();
        setNoGravity(true);
        noPhysics = true;
        if (!(level() instanceof ServerLevel serverLevel)) return;
        if (!claimTarget(serverLevel)) {
            discard();
            return;
        }
        ServerPlayer owner = ownerId == null ? null : serverLevel.getServer().getPlayerList().getPlayer(ownerId);
        if (owner == null || !owner.isAlive() || owner.level() != level()) {
            discard();
            return;
        }

        int finisherStart = finisherStartTick();
        if (age < finisherStart) updateFromTarget(serverLevel);
        if (age >= TechniqueConfig.swordArrayHoldTicks() && age < finisherStart
                && (age - TechniqueConfig.swordArrayHoldTicks())
                % TechniqueConfig.swordArrayBarrageInterval() == 0) {
            releaseWave(serverLevel, owner);
        }
        if (!finisherStarted && age >= finisherStart) {
            finisherStarted = true;
            beginFinisher(serverLevel, owner);
        }
        int burstTick = finisherStart + chargeTicks() + holdTicks();
        if (!burstApplied && age >= burstTick) {
            burstApplied = true;
            applyBurst(serverLevel, owner);
        }
        if (finisherStarted && age % 3 == 0) emitBeamMotes(serverLevel, burstTick);

        age++;
        if (age >= totalLifetimeTicks()) discard();
    }

    private void updateFromTarget(ServerLevel level) {
        Entity raw = targetId == null ? null : level.getEntity(targetId);
        if (raw instanceof LivingEntity target && target.isAlive()) {
            lastTargetAnchor = target.position();
            targetHeight = target.getBbHeight();
            targetWidth = target.getBbWidth();
        }
        if (lastTargetAnchor != null) updateAnchor(level, lastTargetAnchor, targetHeight, targetWidth);
    }

    private void updateAnchor(ServerLevel level, Vec3 targetAnchor, double height, double width) {
        Vec3 ground = groundBelow(level, targetAnchor);
        setPos(ground.x, ground.y + 0.025D, ground.z);
        double topY = targetAnchor.y + height + TechniqueConfig.swordArrayHeight();
        entityData.set(DATA_BEAM_HEIGHT, (float) Math.max(2.0D, topY - getY()));
        entityData.set(DATA_BASE_RADIUS, (float) Math.max(width * 0.5D
                + TechniqueConfig.swordArrayRadiusPadding(), 18.0D));
    }

    private static Vec3 groundBelow(ServerLevel level, Vec3 targetAnchor) {
        Vec3 start = targetAnchor.add(0.0D, 0.6D, 0.0D);
        Vec3 end = new Vec3(targetAnchor.x, level.getMinBuildHeight() + 0.1D, targetAnchor.z);
        BlockHitResult hit = level.clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : targetAnchor;
    }

    private void releaseWave(ServerLevel level, ServerPlayer owner) {
        int volley = Math.max(0, (age - TechniqueConfig.swordArrayHoldTicks())
                / Math.max(1, TechniqueConfig.swordArrayBarrageInterval()));
        double angle = Math.PI * 2.0D * volley / Math.max(1, FlyingSwordItem.FORMATION_SIZE)
                + age * 0.026D;
        double radius = baseRadius() * 0.74D;
        Vec3 top = topCentre();
        Vec3 origin = top.add(Math.cos(angle) * radius, -0.2D, Math.sin(angle) * radius);
        Vec3 target = lastTargetAnchor == null
                ? position().add(0.0D, Math.min(1.0D, beamHeight() * 0.08D), 0.0D)
                : lastTargetAnchor.add(0.0D, targetHeight * 0.42D, 0.0D);
        SwordArrayQiEntity.spawn(level, owner, displayStack, origin,
                safeDirection(target.subtract(origin)), sourceBindingId);
    }

    private void beginFinisher(ServerLevel level, ServerPlayer owner) {
        Vec3 top = topCentre();
        level.playSound(null, BlockPos.containing(top), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 1.6F, 0.46F);
        level.playSound(null, blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE,
                SoundSource.PLAYERS, 1.15F, 0.62F);
        ModNetwork.sendSwordArrayFinisher(owner, position(), top, maximumBeamRadius(),
                chargeTicks(), holdTicks(), expandTicks(), sustainTicks());
    }

    private void applyBurst(ServerLevel level, ServerPlayer owner) {
        double damageRadius = TechniqueConfig.swordArrayFinisherRadius();
        double height = beamHeight();
        AABB column = new AABB(getX() - damageRadius, getY() - 0.5D, getZ() - damageRadius,
                getX() + damageRadius, getY() + height + 0.5D, getZ() + damageRadius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, column,
                candidate -> SwordTargetingRules.canActivelyTarget(owner, candidate))) {
            double extra = target.getBbWidth() * 0.5D;
            if (target.position().subtract(position()).horizontalDistanceSqr()
                    > (damageRadius + extra) * (damageRadius + extra)) continue;
            if (hitTargets.size() >= TechniqueConfig.swordArrayTargetLimit()
                    || !hitTargets.add(target.getUUID())) continue;
            hit(owner, target);
        }
        consumeDurability(owner);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.2D, getZ(),
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.4D, getZ(),
                96, damageRadius * 0.65D, 1.1D, damageRadius * 0.65D, 0.24D);
        level.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + 0.6D, getZ(),
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS, 2.0F, 0.54F);
        level.playSound(null, blockPosition(), SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.PLAYERS, 1.65F, 0.72F);
        level.playSound(null, BlockPos.containing(topCentre()), SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.PLAYERS, 1.2F, 1.65F);
    }

    private void hit(ServerPlayer owner, LivingEntity target) {
        double base = WanxiangSwordData.isTempered(displayStack)
                ? WanxiangWeaponCatalog.damage(owner.server, displayStack)
                : WanxiangSwordData.pierceDamage(displayStack);
        double damage = FlyingSwordDamage.currentDamage(owner, displayStack,
                base + SwordEffectEngine.damageBonus(SwordModuleData.copyModules(displayStack)), target.getMobType())
                * Math.max(0.0D, TechniqueConfig.swordArrayFinisherDamageScale());
        boolean marked = ManualSpiritTrialManager.beginFlyingSwordDamage(owner, target, displayStack);
        boolean success;
        try {
            success = target.hurt(damageSources().playerAttack(owner), (float) Math.max(0.0D, damage));
        } finally {
            if (marked) ManualSpiritTrialManager.endFlyingSwordDamage(owner);
        }
        if (success) SwordEffectEngine.applyOnHit(owner, target, SwordModuleData.copyModules(displayStack));
    }

    private void consumeDurability(ServerPlayer owner) {
        if (consumedDurability) return;
        consumedDurability = true;
        ItemStack source = FlyingSwordItem.findFlyingSword(owner, sourceBindingId);
        if (source.isEmpty() || source.getTag() != null && source.getTag().getBoolean("Unbreakable")) return;
        int cost = WanxiangSwordData.isTempered(source)
                ? WanxiangWeaponCatalog.durabilityCost(owner.server, source) : 1;
        cost = SwordModuleData.consumeVirtualDurability(source, Math.max(0, cost));
        if (cost > 0 && source.hurt(cost, owner.getRandom(), owner)) source.shrink(1);
    }

    private void emitBeamMotes(ServerLevel level, int burstTick) {
        double radius = age < burstTick ? 0.55D : Math.max(1.0D, maximumBeamRadius() * 0.55D);
        level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + beamHeight() * 0.5D, getZ(),
                age < burstTick ? 4 : 12, radius, beamHeight() * 0.36D, radius, 0.025D);
        if (age >= burstTick) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.25D, getZ(),
                    8, radius, 0.25D, radius, 0.12D);
        }
    }

    public ItemStack getDisplayStack() {
        ItemStack synced = entityData.get(DATA_DISPLAY_STACK);
        return synced.isEmpty() ? displayStack.copy() : synced.copy();
    }

    public float baseRadius() { return entityData.get(DATA_BASE_RADIUS); }
    public float beamHeight() { return entityData.get(DATA_BEAM_HEIGHT); }
    public int finisherStartTick() { return entityData.get(DATA_FINISHER_START); }
    public int chargeTicks() { return entityData.get(DATA_CHARGE_TICKS); }
    public int holdTicks() { return entityData.get(DATA_HOLD_TICKS); }
    public int expandTicks() { return entityData.get(DATA_EXPAND_TICKS); }
    public int sustainTicks() { return entityData.get(DATA_SUSTAIN_TICKS); }
    public float expansion() { return entityData.get(DATA_EXPANSION); }
    public float beamScale() { return entityData.get(DATA_BEAM_SCALE); }
    public float expandedArrayRadius() { return baseRadius() * expansion(); }
    public float maximumBeamRadius() { return expandedArrayRadius() * beamScale(); }
    public int burstTick() { return finisherStartTick() + chargeTicks() + holdTicks(); }
    public int totalLifetimeTicks() {
        return burstTick() + expandTicks() + sustainTicks();
    }
    public Vec3 topCentre() { return position().add(0.0D, beamHeight(), 0.0D); }

    private static Vec3 safeDirection(Vec3 direction) {
        return direction.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, -1.0D, 0.0D) : direction.normalize();
    }

    private boolean claimTarget(ServerLevel level) {
        if (targetId == null) return false;
        TargetKey key = new TargetKey(level.dimension(), targetId);
        UUID current = ACTIVE_BY_TARGET.putIfAbsent(key, getUUID());
        if (current == null || current.equals(getUUID())) return true;
        Entity active = level.getEntity(current);
        if (active instanceof SwordArrayFieldEntity && active.isAlive()) return false;
        return ACTIVE_BY_TARGET.replace(key, current, getUUID());
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && targetId != null) {
            ACTIVE_BY_TARGET.remove(new TargetKey(level().dimension(), targetId), getUUID());
        }
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        targetId = tag.hasUUID("Target") ? tag.getUUID("Target") : null;
        sourceBindingId = tag.hasUUID("Binding") ? tag.getUUID("Binding") : null;
        displayStack = tag.contains("DisplayItem") ? ItemStack.of(tag.getCompound("DisplayItem")) : ItemStack.EMPTY;
        lastTargetAnchor = tag.contains("AnchorX")
                ? new Vec3(tag.getDouble("AnchorX"), tag.getDouble("AnchorY"), tag.getDouble("AnchorZ")) : null;
        targetHeight = tag.getDouble("TargetHeight");
        targetWidth = tag.getDouble("TargetWidth");
        age = tag.getInt("Age");
        tickCount = age;
        finisherStarted = tag.getBoolean("FinisherStarted");
        burstApplied = tag.getBoolean("BurstApplied");
        consumedDurability = tag.getBoolean("ConsumedDurability");
        syncStaticData();
        if (tag.contains("BaseRadius")) entityData.set(DATA_BASE_RADIUS, tag.getFloat("BaseRadius"));
        if (tag.contains("BeamHeight")) entityData.set(DATA_BEAM_HEIGHT, tag.getFloat("BeamHeight"));
        if (tag.contains("FinisherStart")) entityData.set(DATA_FINISHER_START, tag.getInt("FinisherStart"));
        if (tag.contains("ChargeTicks")) entityData.set(DATA_CHARGE_TICKS, tag.getInt("ChargeTicks"));
        if (tag.contains("HoldTicks")) entityData.set(DATA_HOLD_TICKS, tag.getInt("HoldTicks"));
        if (tag.contains("ExpandTicks")) entityData.set(DATA_EXPAND_TICKS, tag.getInt("ExpandTicks"));
        if (tag.contains("SustainTicks")) entityData.set(DATA_SUSTAIN_TICKS, tag.getInt("SustainTicks"));
        if (tag.contains("Expansion")) entityData.set(DATA_EXPANSION, tag.getFloat("Expansion"));
        if (tag.contains("BeamScale")) entityData.set(DATA_BEAM_SCALE, tag.getFloat("BeamScale"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        if (targetId != null) tag.putUUID("Target", targetId);
        if (sourceBindingId != null) tag.putUUID("Binding", sourceBindingId);
        if (!displayStack.isEmpty()) tag.put("DisplayItem", displayStack.save(new CompoundTag()));
        if (lastTargetAnchor != null) {
            tag.putDouble("AnchorX", lastTargetAnchor.x);
            tag.putDouble("AnchorY", lastTargetAnchor.y);
            tag.putDouble("AnchorZ", lastTargetAnchor.z);
        }
        tag.putDouble("TargetHeight", targetHeight);
        tag.putDouble("TargetWidth", targetWidth);
        tag.putInt("Age", age);
        tag.putBoolean("FinisherStarted", finisherStarted);
        tag.putBoolean("BurstApplied", burstApplied);
        tag.putBoolean("ConsumedDurability", consumedDurability);
        tag.putFloat("BaseRadius", baseRadius());
        tag.putFloat("BeamHeight", beamHeight());
        tag.putInt("FinisherStart", finisherStartTick());
        tag.putInt("ChargeTicks", chargeTicks());
        tag.putInt("HoldTicks", holdTicks());
        tag.putInt("ExpandTicks", expandTicks());
        tag.putInt("SustainTicks", sustainTicks());
        tag.putFloat("Expansion", expansion());
        tag.putFloat("BeamScale", beamScale());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    private record TargetKey(ResourceKey<Level> dimension, UUID targetId) { }
}
