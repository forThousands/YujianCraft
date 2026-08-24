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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** A short-lived, non-griefing spiritual slash released by the gathered formation coordinator. */
public final class SwordArrayQiEntity extends Entity {
    private static final EntityDataAccessor<ItemStack> DATA_DISPLAY_STACK =
            SynchedEntityData.defineId(SwordArrayQiEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(SwordArrayQiEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> DATA_FINISHER =
            SynchedEntityData.defineId(SwordArrayQiEntity.class, EntityDataSerializers.BOOLEAN);

    private UUID ownerId;
    private UUID sourceBindingId;
    private ItemStack displayStack = ItemStack.EMPTY;
    private final Set<UUID> hitTargets = new HashSet<>();
    private int age;
    private boolean consumedDurability;
    private boolean finisher;
    private boolean detonated;

    public SwordArrayQiEntity(EntityType<? extends SwordArrayQiEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static void spawn(ServerLevel level, ServerPlayer owner, ItemStack source,
                             Vec3 position, Vec3 direction, UUID bindingId) {
        spawn(level, owner, source, position, direction, bindingId, false);
    }

    public static void spawnFinisher(ServerLevel level, ServerPlayer owner, ItemStack source,
                                     Vec3 position, Vec3 direction, UUID bindingId) {
        spawn(level, owner, source, position, direction, bindingId, true);
    }

    private static void spawn(ServerLevel level, ServerPlayer owner, ItemStack source,
                              Vec3 position, Vec3 direction, UUID bindingId, boolean finisher) {
        SwordArrayQiEntity qi = ModEntities.SWORD_ARRAY.get().create(level);
        if (qi == null) return;
        qi.ownerId = owner.getUUID();
        qi.sourceBindingId = bindingId;
        qi.displayStack = source.copy();
        qi.displayStack.setCount(1);
        qi.entityData.set(DATA_OWNER, Optional.of(qi.ownerId));
        qi.entityData.set(DATA_DISPLAY_STACK, qi.displayStack.copy());
        qi.finisher = finisher;
        qi.entityData.set(DATA_FINISHER, finisher);
        Vec3 motion = direction.normalize().scale(TechniqueConfig.swordArraySpeed() * (finisher ? 1.22D : 1.0D));
        qi.setPos(position);
        qi.setDeltaMovement(motion);
        qi.faceMotion(motion);
        level.addFreshEntity(qi);
        net.minecraft.core.BlockPos soundPos = net.minecraft.core.BlockPos.containing(position);
        level.playSound(null, soundPos, SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, finisher ? 1.25F : 0.48F, finisher ? 0.48F : 0.92F);
        level.playSound(null, soundPos, SoundEvents.BEACON_POWER_SELECT,
                SoundSource.PLAYERS, finisher ? 0.62F : 0.16F, finisher ? 1.18F : 1.72F);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_DISPLAY_STACK, ItemStack.EMPTY);
        entityData.define(DATA_OWNER, Optional.empty());
        entityData.define(DATA_FINISHER, false);
    }

    @Override
    public void tick() {
        super.tick();
        setNoGravity(true);
        noPhysics = true;
        if (!(level() instanceof ServerLevel serverLevel)) return;
        ServerPlayer owner = ownerId == null ? null : serverLevel.getServer().getPlayerList().getPlayer(ownerId);
        if (owner == null || !owner.isAlive() || owner.level() != level()) {
            discard();
            return;
        }
        Vec3 previous = position();
        Vec3 next = previous.add(getDeltaMovement());
        HitResult blockHit = level().clip(new ClipContext(previous, next,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            if (finisher) detonate(owner, blockHit.getLocation());
            discard();
            return;
        }
        setPos(next);
        faceMotion(getDeltaMovement());
        if (finisher) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    next.x, next.y, next.z, 5, 0.32D, 0.42D, 0.32D, 0.04D);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                    next.x, next.y, next.z, 4, 0.42D, 0.5D, 0.42D, 0.08D);
        }
        AABB swept = new AABB(previous, next).inflate(TechniqueConfig.swordArrayWidth()
                * (finisher ? 1.65D : 1.0D));
        for (LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class, swept,
                candidate -> SwordTargetingRules.canActivelyTarget(owner, candidate))) {
            if (hitTargets.size() >= TechniqueConfig.swordArrayTargetLimit() || !hitTargets.add(target.getUUID())) continue;
            hit(owner, target, finisher
                    ? TechniqueConfig.swordArrayFinisherDamageScale()
                    : TechniqueConfig.swordArrayDamageScale());
        }
        age++;
        int lifetime = Math.max(1, (int) Math.ceil(TechniqueConfig.swordArrayRange() / TechniqueConfig.swordArraySpeed()));
        if (age >= lifetime) {
            if (finisher) detonate(owner, position());
            discard();
        }
    }

    private void hit(ServerPlayer owner, LivingEntity target, double damageScale) {
        double base = WanxiangSwordData.isTempered(displayStack)
                ? WanxiangWeaponCatalog.damage(owner.server, displayStack)
                : WanxiangSwordData.pierceDamage(displayStack);
        double damage = FlyingSwordDamage.currentDamage(owner, displayStack,
                base + SwordEffectEngine.damageBonus(SwordModuleData.copyModules(displayStack)), target.getMobType())
                * Math.max(0.0D, damageScale);
        boolean marked = ManualSpiritTrialManager.beginFlyingSwordDamage(owner, target, displayStack);
        boolean success;
        try {
            success = target.hurt(damageSources().playerAttack(owner), (float) Math.max(0.0D, damage));
        } finally {
            if (marked) ManualSpiritTrialManager.endFlyingSwordDamage(owner);
        }
        if (success) {
            SwordEffectEngine.applyOnHit(owner, target, SwordModuleData.copyModules(displayStack));
            consumeDurability(owner);
            serverLevel().playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT,
                    SoundSource.PLAYERS, 0.72F, 1.28F);
        }
        serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.FLASH,
                target.getX(), target.getY() + target.getBbHeight() * 0.55D, target.getZ(),
                1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    private void detonate(ServerPlayer owner, Vec3 impact) {
        if (detonated) return;
        detonated = true;
        ServerLevel level = serverLevel();
        double radius = TechniqueConfig.swordArrayFinisherRadius();
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                impact.x, impact.y, impact.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                impact.x, impact.y + 0.2D, impact.z, 42,
                radius * 0.35D, 0.7D, radius * 0.35D, 0.16D);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SONIC_BOOM,
                impact.x, impact.y + 0.35D, impact.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.playSound(null, net.minecraft.core.BlockPos.containing(impact), SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS, 1.35F, 0.72F);
        level.playSound(null, net.minecraft.core.BlockPos.containing(impact), SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.PLAYERS, 0.9F, 1.18F);
        AABB area = new AABB(impact, impact).inflate(radius, Math.max(2.0D, radius * 0.65D), radius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                candidate -> SwordTargetingRules.canActivelyTarget(owner, candidate))) {
            if (hitTargets.size() >= TechniqueConfig.swordArrayTargetLimit()
                    || !hitTargets.add(target.getUUID())) continue;
            hit(owner, target, TechniqueConfig.swordArrayFinisherDamageScale());
        }
        consumeDurability(owner);
        ModNetwork.sendSwordArrayFinisher(owner);
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

    private void faceMotion(Vec3 motion) {
        if (motion.lengthSqr() < 1.0E-6D) return;
        setYRot((float) (Mth.atan2(-motion.x, motion.z) * Mth.RAD_TO_DEG));
        setXRot((float) (Mth.atan2(-motion.y, motion.horizontalDistance()) * Mth.RAD_TO_DEG));
    }

    public ItemStack getDisplayStack() {
        ItemStack synced = entityData.get(DATA_DISPLAY_STACK);
        return synced.isEmpty() ? displayStack.copy() : synced.copy();
    }

    public boolean isFinisher() {
        return entityData.get(DATA_FINISHER);
    }

    private ServerLevel serverLevel() {
        return (ServerLevel) level();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        sourceBindingId = tag.hasUUID("Binding") ? tag.getUUID("Binding") : null;
        displayStack = tag.contains("DisplayItem") ? ItemStack.of(tag.getCompound("DisplayItem")) : ItemStack.EMPTY;
        age = tag.getInt("Age");
        finisher = tag.getBoolean("Finisher");
        entityData.set(DATA_OWNER, Optional.ofNullable(ownerId));
        entityData.set(DATA_DISPLAY_STACK, displayStack.copy());
        entityData.set(DATA_FINISHER, finisher);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        if (sourceBindingId != null) tag.putUUID("Binding", sourceBindingId);
        if (!displayStack.isEmpty()) tag.put("DisplayItem", displayStack.save(new CompoundTag()));
        tag.putInt("Age", age);
        tag.putBoolean("Finisher", finisher);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
