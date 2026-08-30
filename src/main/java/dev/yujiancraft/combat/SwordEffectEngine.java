package dev.yujiancraft.combat;

import dev.yujiancraft.config.EffectBalanceConfig;
import dev.yujiancraft.config.EffectParameter;
import dev.yujiancraft.config.TechniqueConfig;
import dev.yujiancraft.registry.ModEffects;
import dev.yujiancraft.upgrade.FlyingSwordModule;
import dev.yujiancraft.upgrade.SwordModuleData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import org.joml.Vector3f;

public final class SwordEffectEngine {
    private static final String LAST_EFFECT_TICK = "YujianCraftLastModuleEffectTick";
    private static final String LAST_WORK_EFFECT_TICK = "YujianCraftLastWorkModuleEffectTick";

    private SwordEffectEngine() {
    }

    public static double damageBonus(CompoundTag modules) {
        int level = SwordModuleData.getLevel(modules, FlyingSwordModule.DAMAGE);
        if (level == 0) return 0.0D;
        return EffectBalanceConfig.get(switch (level) {
            case 1 -> EffectParameter.DAMAGE_BONUS_I;
            case 2 -> EffectParameter.DAMAGE_BONUS_II;
            default -> EffectParameter.DAMAGE_BONUS_III;
        });
    }

    public static int durabilityBonus(int level) {
        if (level <= 0) return 0;
        return EffectBalanceConfig.getInt(switch (Math.min(3, level)) {
            case 1 -> EffectParameter.DURABILITY_BONUS_I;
            case 2 -> EffectParameter.DURABILITY_BONUS_II;
            default -> EffectParameter.DURABILITY_BONUS_III;
        });
    }

    public static void applyOnHit(ServerPlayer owner, LivingEntity target, CompoundTag modules) {
        if (!SwordModuleData.hasAnyEffect(modules) || !(target.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        CompoundTag targetData = target.getPersistentData();
        int cooldown = EffectBalanceConfig.getInt(EffectParameter.GLOBAL_COOLDOWN);
        if (targetData.contains(LAST_EFFECT_TICK) && now - targetData.getLong(LAST_EFFECT_TICK) < cooldown) return;
        targetData.putLong(LAST_EFFECT_TICK, now);

        applyFlame(target, modules);
        applyLightning(owner, target, modules, level);
        applyPoison(target, modules);
        applyExplosion(owner, target, modules, level);
        applyArrowRain(owner, target, modules, level);
    }

    /**
     * Low-frequency, non-damaging module echo used by mining and spirit fishing. It never changes
     * blocks and uses one owner-wide cooldown, so six simultaneous implements cannot particle-spam.
     */
    public static void applyWorkPulse(ServerPlayer owner, Vec3 position, CompoundTag modules) {
        if (!SwordModuleData.hasAnyEffect(modules) || !(owner.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        CompoundTag ownerData = owner.getPersistentData();
        int cooldown = TechniqueConfig.workEffectCooldown();
        if (ownerData.contains(LAST_WORK_EFFECT_TICK)
                && now - ownerData.getLong(LAST_WORK_EFFECT_TICK) < cooldown) return;
        ownerData.putLong(LAST_WORK_EFFECT_TICK, now);
        BlockPos soundPos = BlockPos.containing(position);

        int flame = SwordModuleData.getLevel(modules, FlyingSwordModule.FLAME);
        if (flame > 0) {
            level.sendParticles(ParticleTypes.FLAME, position.x, position.y, position.z,
                    4 + flame * 2, 0.18D, 0.18D, 0.18D, 0.025D);
        }
        int lightningLevel = SwordModuleData.getLevel(modules, FlyingSwordModule.LIGHTNING);
        if (lightningLevel > 0) {
            LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
            if (lightning != null) {
                lightning.moveTo(position.x, position.y, position.z);
                lightning.setVisualOnly(true);
                level.addFreshEntity(lightning);
            }
        }
        int poison = SwordModuleData.getLevel(modules, FlyingSwordModule.POISON);
        if (poison > 0) {
            DustParticleOptions green = new DustParticleOptions(new Vector3f(0.24F, 0.88F, 0.38F), 0.85F);
            level.sendParticles(green, position.x, position.y, position.z,
                    5 + poison * 2, 0.28D, 0.16D, 0.28D, 0.015D);
        }
        int explosion = SwordModuleData.getLevel(modules, FlyingSwordModule.EXPLOSION);
        if (explosion > 0) {
            level.sendParticles(ParticleTypes.EXPLOSION, position.x, position.y, position.z,
                    1, 0.08D, 0.08D, 0.08D, 0.0D);
            level.playSound(null, soundPos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS,
                    0.22F, 1.75F);
        }
        int arrowRain = SwordModuleData.getLevel(modules, FlyingSwordModule.ARROW_RAIN);
        if (arrowRain > 0) {
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, position.x, position.y, position.z,
                    1 + arrowRain, 0.2D, 0.15D, 0.2D, 0.0D);
        }
    }

    private static void applyFlame(LivingEntity target, CompoundTag modules) {
        int moduleLevel = SwordModuleData.getLevel(modules, FlyingSwordModule.FLAME);
        if (moduleLevel == 0) return;
        int duration = EffectBalanceConfig.getInt(switch (moduleLevel) {
            case 1 -> EffectParameter.FLAME_DURATION_I;
            case 2 -> EffectParameter.FLAME_DURATION_II;
            default -> EffectParameter.FLAME_DURATION_III;
        });
        target.addEffect(new MobEffectInstance(ModEffects.SWORD_BURN, duration, moduleLevel - 1,
                false, true, true));
    }

    private static void applyLightning(ServerPlayer owner, LivingEntity target, CompoundTag modules,
                                       ServerLevel level) {
        int moduleLevel = SwordModuleData.getLevel(modules, FlyingSwordModule.LIGHTNING);
        if (moduleLevel == 0) return;
        for (int index = 0; index < moduleLevel; index++) {
            LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
            if (lightning != null) {
                double angle = Math.PI * 2.0D * index / moduleLevel;
                lightning.moveTo(target.getX() + Math.cos(angle) * 0.35D, target.getY(),
                        target.getZ() + Math.sin(angle) * 0.35D);
                lightning.setVisualOnly(true);
                level.addFreshEntity(lightning);
            }
        }
        double damage = EffectBalanceConfig.get(EffectParameter.LIGHTNING_DAMAGE) * moduleLevel;
        if (damage > 0.0D) hurtIgnoringInvulnerability(target, target.damageSources().lightningBolt(), damage);
    }

    private static void applyPoison(LivingEntity target, CompoundTag modules) {
        int moduleLevel = SwordModuleData.getLevel(modules, FlyingSwordModule.POISON);
        if (moduleLevel == 0) return;
        int duration = EffectBalanceConfig.getInt(switch (moduleLevel) {
            case 1 -> EffectParameter.POISON_DURATION_I;
            case 2 -> EffectParameter.POISON_DURATION_II;
            default -> EffectParameter.POISON_DURATION_III;
        });
        target.addEffect(new MobEffectInstance(ModEffects.SWORD_POISON, duration, moduleLevel - 1,
                false, true, true));
    }

    private static void applyExplosion(ServerPlayer owner, LivingEntity target, CompoundTag modules,
                                       ServerLevel level) {
        int moduleLevel = SwordModuleData.getLevel(modules, FlyingSwordModule.EXPLOSION);
        if (moduleLevel == 0) return;
        level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY(0.55D), target.getZ(),
                2 + moduleLevel, 0.25D, 0.35D, 0.25D, 0.03D);
        level.playSound(null, target.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS,
                0.7F, 1.15F);
        double damage = EffectBalanceConfig.get(switch (moduleLevel) {
            case 1 -> EffectParameter.EXPLOSION_DAMAGE_I;
            case 2 -> EffectParameter.EXPLOSION_DAMAGE_II;
            default -> EffectParameter.EXPLOSION_DAMAGE_III;
        });
        if (damage > 0.0D) {
            hurtIgnoringInvulnerability(target, target.damageSources().explosion(owner, owner), damage);
        }
    }

    private static void applyArrowRain(ServerPlayer owner, LivingEntity target, CompoundTag modules,
                                       ServerLevel level) {
        int moduleLevel = SwordModuleData.getLevel(modules, FlyingSwordModule.ARROW_RAIN);
        if (moduleLevel == 0) return;
        int count = EffectBalanceConfig.getInt(switch (moduleLevel) {
            case 1 -> EffectParameter.ARROW_COUNT_I;
            case 2 -> EffectParameter.ARROW_COUNT_II;
            default -> EffectParameter.ARROW_COUNT_III;
        });
        double damage = EffectBalanceConfig.get(EffectParameter.ARROW_DAMAGE);
        for (int index = 0; index < count; index++) {
            double angle = Math.PI * 2.0D * index / count + target.getId();
            double radius = 0.35D + (index % 3) * 0.18D;
            Vec3 start = target.position().add(Math.cos(angle) * radius,
                    6.0D + (index % 3) * 0.45D, Math.sin(angle) * radius);
            Vec3 aim = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
            Vec3 velocity = aim.subtract(start).normalize();
            Arrow arrow = new Arrow(level, owner,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), null);
            arrow.setPos(start.x, start.y, start.z);
            arrow.setBaseDamage(damage);
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            arrow.shoot(velocity.x, velocity.y, velocity.z, 1.65F, 0.0F);
            level.addFreshEntity(arrow);
        }
    }

    private static void hurtIgnoringInvulnerability(LivingEntity target,
                                                    net.minecraft.world.damagesource.DamageSource source,
                                                    double damage) {
        target.invulnerableTime = 0;
        target.hurt(source, (float) damage);
    }
}
