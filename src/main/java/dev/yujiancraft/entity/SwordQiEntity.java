package dev.yujiancraft.entity;

import dev.yujiancraft.combat.SwordEffectEngine;
import dev.yujiancraft.combat.SwordTargetingRules;
import dev.yujiancraft.config.TechniqueConfig;
import dev.yujiancraft.item.FlyingSwordItem;
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

/** A short-lived, non-griefing spiritual slash. One wave is coordinated by formation slot zero. */
public final class SwordQiEntity extends Entity {
    private static final EntityDataAccessor<ItemStack> DATA_DISPLAY_STACK =
            SynchedEntityData.defineId(SwordQiEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(SwordQiEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private UUID ownerId;
    private UUID sourceBindingId;
    private ItemStack displayStack = ItemStack.EMPTY;
    private final Set<UUID> hitTargets = new HashSet<>();
    private int age;
    private boolean consumedDurability;

    public SwordQiEntity(EntityType<? extends SwordQiEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static void spawn(ServerLevel level, ServerPlayer owner, ItemStack source,
                             Vec3 position, Vec3 direction, UUID bindingId) {
        SwordQiEntity qi = ModEntities.SWORD_QI.get().create(level);
        if (qi == null) return;
        qi.ownerId = owner.getUUID();
        qi.sourceBindingId = bindingId;
        qi.displayStack = source.copy();
        qi.displayStack.setCount(1);
        qi.entityData.set(DATA_OWNER, Optional.of(qi.ownerId));
        qi.entityData.set(DATA_DISPLAY_STACK, qi.displayStack.copy());
        Vec3 motion = direction.normalize().scale(TechniqueConfig.qiSpeed());
        qi.setPos(position);
        qi.setDeltaMovement(motion);
        qi.faceMotion(motion);
        level.addFreshEntity(qi);
        level.playSound(null, owner.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 0.85F, 0.72F);
        level.playSound(null, owner.blockPosition(), SoundEvents.BEACON_POWER_SELECT,
                SoundSource.PLAYERS, 0.38F, 1.65F);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_DISPLAY_STACK, ItemStack.EMPTY);
        entityData.define(DATA_OWNER, Optional.empty());
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
            discard();
            return;
        }
        setPos(next);
        faceMotion(getDeltaMovement());
        AABB swept = new AABB(previous, next).inflate(TechniqueConfig.qiWidth());
        for (LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class, swept,
                candidate -> SwordTargetingRules.canActivelyTarget(owner, candidate))) {
            if (hitTargets.size() >= TechniqueConfig.qiTargetLimit() || !hitTargets.add(target.getUUID())) continue;
            hit(owner, target);
        }
        age++;
        int lifetime = Math.max(1, (int) Math.ceil(TechniqueConfig.qiRange() / TechniqueConfig.qiSpeed()));
        if (age >= lifetime) discard();
    }

    private void hit(ServerPlayer owner, LivingEntity target) {
        double base = WanxiangSwordData.isTempered(displayStack)
                ? WanxiangWeaponCatalog.damage(owner.server, displayStack)
                : WanxiangSwordData.pierceDamage(displayStack);
        double damage = FlyingSwordDamage.currentDamage(owner, displayStack,
                base + SwordEffectEngine.damageBonus(SwordModuleData.copyModules(displayStack)), target.getMobType())
                * TechniqueConfig.qiDamageScale();
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

    private ServerLevel serverLevel() {
        return (ServerLevel) level();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        sourceBindingId = tag.hasUUID("Binding") ? tag.getUUID("Binding") : null;
        displayStack = tag.contains("DisplayItem") ? ItemStack.of(tag.getCompound("DisplayItem")) : ItemStack.EMPTY;
        age = tag.getInt("Age");
        entityData.set(DATA_OWNER, Optional.ofNullable(ownerId));
        entityData.set(DATA_DISPLAY_STACK, displayStack.copy());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        if (sourceBindingId != null) tag.putUUID("Binding", sourceBindingId);
        if (!displayStack.isEmpty()) tag.put("DisplayItem", displayStack.save(new CompoundTag()));
        tag.putInt("Age", age);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
