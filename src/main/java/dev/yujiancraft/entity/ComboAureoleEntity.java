package dev.yujiancraft.entity;

import dev.yujiancraft.registry.ModEntities;
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
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived world entity for a combo aureole.  The server owns only lifetime and tracking; the
 * renderer resolves the owner's current interpolated body pose so network movement never leaves
 * the wheel behind in mid-air.
 */
public final class ComboAureoleEntity extends Entity {
    private static final Map<UUID, UUID> ACTIVE_BY_OWNER = new ConcurrentHashMap<>();
    private static final EntityDataAccessor<Integer> DATA_OWNER_ID =
            SynchedEntityData.defineId(ComboAureoleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFETIME =
            SynchedEntityData.defineId(ComboAureoleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(ComboAureoleEntity.class, EntityDataSerializers.FLOAT);

    private UUID ownerUuid;

    public ComboAureoleEntity(EntityType<? extends ComboAureoleEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        noCulling = true;
    }

    public static void spawn(ServerLevel level, ServerPlayer owner, int lifetimeTicks, float radius) {
        UUID previousId = ACTIVE_BY_OWNER.get(owner.getUUID());
        Entity previous = previousId == null ? null : level.getEntity(previousId);
        if (previous instanceof ComboAureoleEntity && previous.isAlive()) previous.discard();

        ComboAureoleEntity aureole = ModEntities.COMBO_AUREOLE.get().create(level);
        if (aureole == null) return;
        aureole.ownerUuid = owner.getUUID();
        aureole.entityData.set(DATA_OWNER_ID, owner.getId());
        aureole.entityData.set(DATA_LIFETIME, Mth.clamp(lifetimeTicks, 1, 200));
        aureole.entityData.set(DATA_RADIUS, Mth.clamp(radius, 0.5F, 4.0F));
        aureole.setPos(owner.position());
        if (level.addFreshEntity(aureole)) ACTIVE_BY_OWNER.put(owner.getUUID(), aureole.getUUID());
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_OWNER_ID, -1);
        entityData.define(DATA_LIFETIME, 40);
        entityData.define(DATA_RADIUS, 1.9F);
    }

    @Override
    public void tick() {
        super.tick();
        setNoGravity(true);
        noPhysics = true;
        if (level().isClientSide) return;
        Entity owner = ownerUuid == null ? null : ((ServerLevel) level()).getEntity(ownerUuid);
        if (owner == null || !owner.isAlive() || tickCount >= lifetimeTicks()) {
            discard();
            return;
        }
        setPos(owner.position());
    }

    public Entity owner() {
        int ownerId = entityData.get(DATA_OWNER_ID);
        return ownerId < 0 ? null : level().getEntity(ownerId);
    }

    public int lifetimeTicks() {
        return Math.max(1, entityData.get(DATA_LIFETIME));
    }

    public float radius() {
        return entityData.get(DATA_RADIUS);
    }

    public float renderAge(float partialTick) {
        return tickCount + partialTick;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && ownerUuid != null) {
            ACTIVE_BY_OWNER.remove(ownerUuid, getUUID());
        }
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        ownerUuid = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        entityData.set(DATA_OWNER_ID, tag.getInt("OwnerId"));
        entityData.set(DATA_LIFETIME, Math.max(1, tag.getInt("Lifetime")));
        entityData.set(DATA_RADIUS, tag.contains("Radius") ? tag.getFloat("Radius") : 1.9F);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUuid != null) tag.putUUID("Owner", ownerUuid);
        tag.putInt("OwnerId", entityData.get(DATA_OWNER_ID));
        tag.putInt("Lifetime", lifetimeTicks());
        tag.putFloat("Radius", radius());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
