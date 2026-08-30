package dev.yujiancraft.entity;

import dev.yujiancraft.combat.SwordEffectEngine;
import dev.yujiancraft.combat.SwordTargetingRules;
import dev.yujiancraft.config.TechniqueConfig;
import dev.yujiancraft.config.EffectBalanceConfig;
import dev.yujiancraft.config.EffectParameter;
import dev.yujiancraft.item.FlyingSwordItem;
import dev.yujiancraft.network.ModNetwork;
import dev.yujiancraft.registry.ModEntities;
import dev.yujiancraft.registry.ModSounds;
import dev.yujiancraft.upgrade.SwordModuleData;
import dev.yujiancraft.wanxiang.FlyingSwordDamage;
import dev.yujiancraft.wanxiang.ManualSpiritTrialManager;
import dev.yujiancraft.wanxiang.WanxiangSwordData;
import dev.yujiancraft.wanxiang.WanxiangWeaponCatalog;
import dev.yujiancraft.visual.SwordArrayVisualStyle;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
 * particles with client geometry, fixes a weighty world-space anchor, suppresses the target, then
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
    private static final EntityDataAccessor<Integer> DATA_VISUAL_VARIANT =
            SynchedEntityData.defineId(SwordArrayFieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_COMBO_FINISHER =
            SynchedEntityData.defineId(SwordArrayFieldEntity.class, EntityDataSerializers.BOOLEAN);

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
    private int visualVariant;
    private boolean comboFinisher;
    private float comboPower = 1.0F;
    private float clientPreviewAge = -1.0F;
    private SwordArrayVisualStyle clientPreviewStyle = SwordArrayVisualStyle.DEFAULT;
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
        field.visualVariant = Mth.clamp(owner.getPersistentData()
                .getInt("YujianCraftSwordArrayStyle"), 0, 1);
        field.syncStaticData();
        field.updateAnchor(level, targetAnchor, targetHeight, targetWidth);
        if (level.addFreshEntity(field)) ACTIVE_BY_TARGET.put(key, field.getUUID());
    }

    /** Accelerated, slightly smaller field used by the fifth hit of Yujian Combo Stance. */
    public static void spawnCombo(ServerLevel level, ServerPlayer owner, ItemStack source, UUID bindingId,
                                  UUID targetId, Vec3 targetAnchor, double targetHeight, double targetWidth,
                                  boolean heavy) {
        if (targetId == null) return;
        TargetKey key = new TargetKey(level.dimension(), targetId);
        UUID activeId = ACTIVE_BY_TARGET.get(key);
        Entity active = activeId == null ? null : level.getEntity(activeId);
        if (active instanceof SwordArrayFieldEntity && active.isAlive()) return;
        if (activeId != null) ACTIVE_BY_TARGET.remove(key, activeId);
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
        field.comboFinisher = true;
        field.comboPower = heavy ? 1.45F : 1.0F;
        field.visualVariant = Mth.clamp(owner.getPersistentData()
                .getInt("YujianCraftSwordArrayStyle"), 0, 1);
        field.syncStaticData();
        field.updateAnchor(level, targetAnchor, targetHeight, targetWidth);
        // Keep the proven renderer/signal channel, but compress the performance for a combo beat.
        field.entityData.set(DATA_BASE_RADIUS, heavy ? 13.25F : 11.5F);
        field.entityData.set(DATA_FINISHER_START, 2);
        field.entityData.set(DATA_CHARGE_TICKS, heavy ? 5 : 4);
        field.entityData.set(DATA_HOLD_TICKS, heavy ? 4 : 3);
        field.entityData.set(DATA_EXPAND_TICKS, heavy ? 5 : 4);
        field.entityData.set(DATA_SUSTAIN_TICKS, heavy ? 16 : 14);
        field.entityData.set(DATA_EXPANSION, heavy ? 1.74F : 1.62F);
        field.entityData.set(DATA_BEAM_SCALE, heavy ? 0.92F : 0.76F);
        field.entityData.set(DATA_COMBO_FINISHER, true);
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
        entityData.define(DATA_VISUAL_VARIANT, 0);
        entityData.define(DATA_COMBO_FINISHER, false);
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
        entityData.set(DATA_VISUAL_VARIANT, Mth.clamp(visualVariant, 0, 1));
        entityData.set(DATA_COMBO_FINISHER, comboFinisher);
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
        suppressTarget(serverLevel);
        if (lastTargetAnchor != null) updateAnchor(serverLevel, lastTargetAnchor, targetHeight, targetWidth);
        if (!comboFinisher && age >= TechniqueConfig.swordArrayHoldTicks() && age < finisherStart
                && (age - TechniqueConfig.swordArrayHoldTicks())
                % TechniqueConfig.swordArrayBarrageInterval() == 0) {
            releaseWave(serverLevel, owner);
        }
        if (!finisherStarted && age >= finisherStart) {
            finisherStarted = true;
            beginFinisher(serverLevel, owner);
        }
        int descentTick = finisherStart + chargeTicks() + holdTicks();
        if (age == descentTick) {
            level().playSound(null, BlockPos.containing(topCentre()), ModSounds.SWORD_ARRAY_RIPTIDE_DESCENT.get(),
                    SoundSource.PLAYERS, 1.8F, 0.48F);
            level().playSound(null, BlockPos.containing(topCentre()), ModSounds.SWORD_ARRAY_BEACON_DESCENT.get(),
                    SoundSource.PLAYERS, 1.4F, 0.62F);
        }
        int impactTick = descentTick + expandTicks();
        if (!burstApplied && age >= impactTick) {
            burstApplied = true;
            applyBurst(serverLevel, owner);
        }
        if (finisherStarted && age % 3 == 0) emitBeamMotes(serverLevel, impactTick);

        age++;
        if (age >= totalLifetimeTicks()) discard();
    }

    /**
     * Sword-array suppression is authoritative rather than a potion effect. This also handles
     * bosses that ignore movement effects and players whose movement is client-predicted. The
     * field itself never follows correction jitter: its anchor remains the cast-time position.
     */
    private void suppressTarget(ServerLevel level) {
        Entity raw = targetId == null ? null : level.getEntity(targetId);
        if (!(raw instanceof LivingEntity target) || !target.isAlive() || lastTargetAnchor == null) return;
        if (target instanceof Mob mob) mob.getNavigation().stop();
        target.setDeltaMovement(Vec3.ZERO);
        target.hasImpulse = true;
        if (target.position().distanceToSqr(lastTargetAnchor) > 0.0025D) {
            if (target instanceof ServerPlayer player) {
                player.connection.teleport(lastTargetAnchor.x, lastTargetAnchor.y, lastTargetAnchor.z,
                        player.getYRot(), player.getXRot());
            } else {
                target.teleportTo(lastTargetAnchor.x, lastTargetAnchor.y, lastTargetAnchor.z);
            }
        }
        if (age % 6 == 0) {
            level.sendParticles(ParticleTypes.ENCHANT,
                    lastTargetAnchor.x, lastTargetAnchor.y + targetHeight * 0.55D, lastTargetAnchor.z,
                    5, targetWidth * 0.42D, targetHeight * 0.34D, targetWidth * 0.42D, 0.015D);
        }
    }

    private void updateAnchor(ServerLevel level, Vec3 targetAnchor, double height, double width) {
        Vec3 ground = groundBelow(level, targetAnchor);
        setPos(ground.x, ground.y + 0.025D, ground.z);
        double topY = targetAnchor.y + height
                + (comboFinisher ? 19.0D : TechniqueConfig.swordArrayHeight());
        entityData.set(DATA_BEAM_HEIGHT, (float) Math.max(2.0D, topY - getY()));
        entityData.set(DATA_BASE_RADIUS, (float) Math.max(width * 0.5D
                + TechniqueConfig.swordArrayRadiusPadding(), comboFinisher
                        ? (comboPower > 1.01F ? 13.25D : 11.5D) : 18.0D));
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
        level.playSound(null, BlockPos.containing(top), ModSounds.SWORD_ARRAY_BEACON_ACTIVATE.get(),
                SoundSource.PLAYERS, 1.6F, 0.46F);
        level.playSound(null, blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE,
                SoundSource.PLAYERS, 1.15F, 0.62F);
        level.playSound(null, BlockPos.containing(top), ModSounds.SWORD_ARRAY_AMETHYST_RESONATE.get(),
                SoundSource.PLAYERS, 1.35F, 0.52F);
        ModNetwork.sendSwordArrayFinisher(owner, level.getGameTime(), position(), top,
                maximumBeamRadius(),
                chargeTicks(), holdTicks(), expandTicks(), sustainTicks());
    }

    private void applyBurst(ServerLevel level, ServerPlayer owner) {
        double damageRadius = TechniqueConfig.swordArrayFinisherRadius() * (comboFinisher ? 1.45D : 1.0D);
        double height = beamHeight();
        AABB column = new AABB(getX() - damageRadius, getY() - 0.5D, getZ() - damageRadius,
                getX() + damageRadius, getY() + height + 0.5D, getZ() + damageRadius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, column,
                candidate -> SwordTargetingRules.canActivelyTarget(owner, candidate))) {
            double extra = target.getBbWidth() * 0.5D;
            if (target.position().subtract(position()).horizontalDistanceSqr()
                    > (damageRadius + extra) * (damageRadius + extra)) continue;
            if (hitTargets.size() >= TechniqueConfig.swordArrayFinisherTargetLimit()
                    || !hitTargets.add(target.getUUID())) continue;
            Vec3 targetCentre = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
            Vec3 impactCentre = position().add(0.0D, Math.min(1.0D, target.getBbHeight() * 0.35D), 0.0D);
            double normalizedDistance = Mth.clamp(targetCentre.distanceTo(impactCentre)
                    / Math.max(0.001D, damageRadius + extra), 0.0D, 1.0D);
            double radialScale = 0.35D + 0.65D * (1.0D - normalizedDistance);
            hit(owner, target, radialScale);
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
        // Low pitches stretch the vanilla transients into a weighty metal-and-bronze impact.
        // Layering keeps the sound original to Minecraft while avoiding a short, tinny anvil hit.
        level.playSound(null, blockPosition(), SoundEvents.ANVIL_LAND,
                SoundSource.PLAYERS, 2.8F, 0.46F);
        level.playSound(null, blockPosition(), SoundEvents.BELL_BLOCK,
                SoundSource.PLAYERS, 2.2F, 0.52F);
        level.playSound(null, blockPosition(), SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.PLAYERS, 1.65F, 0.72F);
        level.playSound(null, BlockPos.containing(topCentre()), SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.PLAYERS, 1.2F, 1.65F);
    }

    private void hit(ServerPlayer owner, LivingEntity target, double radialScale) {
        double base = WanxiangSwordData.isTempered(displayStack)
                ? WanxiangWeaponCatalog.damage(owner.server, displayStack)
                : WanxiangSwordData.pierceDamage(displayStack);
        double damage = FlyingSwordDamage.currentDamage(owner, displayStack,
                base + SwordEffectEngine.damageBonus(SwordModuleData.copyModules(displayStack)), target.getMobType())
                * Math.max(0.0D, EffectBalanceConfig.get(comboFinisher
                        ? EffectParameter.COMBO_FINISHER_DAMAGE_SCALE
                        : EffectParameter.SWORD_ARRAY_FINISHER_DAMAGE_SCALE))
                * (comboFinisher ? comboPower : 1.0F)
                * Mth.clamp(radialScale, 0.0D, 1.0D);
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
    /** 0 = threefold gold/jade/cyan, 1 = all-gold. */
    public int visualVariant() { return Mth.clamp(entityData.get(DATA_VISUAL_VARIANT), 0, 1); }
    public float expandedArrayRadius() { return baseRadius() * expansion(); }
    public float maximumBeamRadius() { return expandedArrayRadius() * beamScale(); }
    public int burstTick() { return finisherStartTick() + chargeTicks() + holdTicks(); }
    public int totalLifetimeTicks() {
        return burstTick() + expandTicks() + sustainTicks()
                + TechniqueConfig.swordArrayFinisherLingerTicks();
    }
    public Vec3 topCentre() { return position().add(0.0D, beamHeight(), 0.0D); }

    /** Configures the opt-in, client-only VFX Studio preview entity. */
    public void configureClientPreview(Vec3 ground, ItemStack stack, float authoredTick,
                                       SwordArrayVisualStyle style) {
        if (!level().isClientSide) return;
        setPos(ground);
        entityData.set(DATA_DISPLAY_STACK, stack.copy());
        entityData.set(DATA_BASE_RADIUS, 18.0F);
        entityData.set(DATA_BEAM_HEIGHT, 28.0F);
        entityData.set(DATA_FINISHER_START, 72);
        entityData.set(DATA_CHARGE_TICKS, 10);
        entityData.set(DATA_HOLD_TICKS, 8);
        entityData.set(DATA_EXPAND_TICKS, 7);
        entityData.set(DATA_SUSTAIN_TICKS, 32);
        entityData.set(DATA_EXPANSION, 2.25F * style.expandedScale());
        entityData.set(DATA_BEAM_SCALE, 0.92F);
        clientPreviewStyle = style;
        clientPreviewAge = finisherStartTick() + Math.max(0.0F, authoredTick);
    }

    public void configureClientPreview(Vec3 ground, ItemStack stack, float authoredTick) {
        configureClientPreview(ground, stack, authoredTick, SwordArrayVisualStyle.DEFAULT);
    }

    public SwordArrayVisualStyle visualStyle() {
        return clientPreviewAge >= 0.0F ? clientPreviewStyle : SwordArrayVisualStyle.DEFAULT;
    }

    public float renderAge(float partialTick) {
        return clientPreviewAge >= 0.0F ? clientPreviewAge : tickCount + partialTick;
    }

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
        visualVariant = Mth.clamp(tag.getInt("VisualVariant"), 0, 1);
        comboFinisher = tag.getBoolean("ComboFinisher");
        comboPower = tag.contains("ComboPower") ? Math.max(1.0F, tag.getFloat("ComboPower")) : 1.0F;
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
        if (tag.contains("VisualVariant")) entityData.set(DATA_VISUAL_VARIANT,
                Mth.clamp(tag.getInt("VisualVariant"), 0, 1));
        entityData.set(DATA_COMBO_FINISHER, comboFinisher);
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
        tag.putInt("VisualVariant", visualVariant());
        tag.putBoolean("ComboFinisher", comboFinisher);
        tag.putFloat("ComboPower", comboPower);
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
