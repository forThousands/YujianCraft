package dev.swordflight.effect;

import dev.swordflight.config.EffectBalanceConfig;
import dev.swordflight.config.EffectParameter;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public final class SwordPoisonEffect extends MobEffect {
    public SwordPoisonEffect() {
        super(MobEffectCategory.HARMFUL, 0x65B84A);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        int level = Math.max(1, Math.min(3, amplifier + 1));
        double damage = EffectBalanceConfig.get(switch (level) {
            case 1 -> EffectParameter.POISON_DAMAGE_I;
            case 2 -> EffectParameter.POISON_DAMAGE_II;
            default -> EffectParameter.POISON_DAMAGE_III;
        });
        if (damage > 0.0D) entity.hurt(entity.damageSources().magic(), (float) damage);
        if (entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ENTITY_EFFECT, entity.getX(), entity.getY(0.6D), entity.getZ(),
                    4 + level, entity.getBbWidth() * 0.3D, entity.getBbHeight() * 0.25D,
                    entity.getBbWidth() * 0.3D, 0.02D);
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }
}
