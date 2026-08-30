package dev.yujiancraft.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;

/**
 * Attackable heart of the Spirit Trial sword pedestal.  The historical class and registry name
 * are retained so old trial bookkeeping and target filters continue to recognise the entity.
 */
public final class SpiritTrialDummyEntity extends PathfinderMob {
    public static final float TOP_SLAB_Y = 5.75F;
    public static final float PEDESTAL_HEIGHT = TOP_SLAB_Y + 0.5F;
    private static final double ENERGY_EDGE_OFFSET = 1.34D;
    private long lastChimeTick = Long.MIN_VALUE;
    private int chimeIndex;

    public SpiritTrialDummyEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoAi(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1024.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D);
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) return;

        // Four straight spiritual streams connect matching base/cap corners. Keeping their X/Z
        // fixed preserves the silhouette of a contained sword altar instead of filling its heart
        // with diffuse visual noise.
        if (tickCount % 3 == 0) {
            for (int corner = 0; corner < 4; corner++) {
                double x = (corner & 1) == 0 ? -ENERGY_EDGE_OFFSET : ENERGY_EDGE_OFFSET;
                double z = (corner & 2) == 0 ? -ENERGY_EDGE_OFFSET : ENERGY_EDGE_OFFSET;
                double progress = Math.floorMod(tickCount * 19 + corner * 67 + getId() * 7, 520)
                        / 520.0D;
                double height = 0.58D + progress * (TOP_SLAB_Y - 0.70D);
                serverLevel.sendParticles(ParticleTypes.END_ROD,
                        getX() + x, getY() + height, getZ() + z,
                        1, 0.012D, 0.028D, 0.012D, 0.001D);
            }
        }
        if (tickCount % 7 == 0) {
            for (int corner = 0; corner < 4; corner++) {
                double x = (corner & 1) == 0 ? -ENERGY_EDGE_OFFSET : ENERGY_EDGE_OFFSET;
                double z = (corner & 2) == 0 ? -ENERGY_EDGE_OFFSET : ENERGY_EDGE_OFFSET;
                double progress = Math.floorMod(tickCount * 31 + corner * 101 + getId() * 13, 500)
                        / 500.0D;
                double height = 0.62D + progress * (TOP_SLAB_Y - 0.78D);
                serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        getX() + x, getY() + height, getZ() + z,
                        1, 0.018D, 0.075D, 0.018D, 0.012D);
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (!hurt || level().isClientSide) return hurt;

        long gameTime = level().getGameTime();
        if (gameTime - lastChimeTick >= 2L) {
            lastChimeTick = gameTime;
            float pitch = (chimeIndex++ & 1) == 0 ? 1.28F : 1.52F;
            level().playSound(null, blockPosition(), SoundEvents.BELL_BLOCK,
                    SoundSource.BLOCKS, 0.72F, pitch);
            level().playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.BLOCKS, 0.62F, pitch + 0.18F);
        }
        return true;
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }
}
