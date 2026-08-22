package dev.yujiancraft.effect;

import dev.yujiancraft.config.EffectBalanceConfig;
import dev.yujiancraft.config.EffectParameter;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public final class SwordBurnEffect extends MobEffect {
    public SwordBurnEffect() {
        super(MobEffectCategory.HARMFUL, 0xFF6A00);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        int level = Math.max(1, Math.min(3, amplifier + 1));
        double damage = EffectBalanceConfig.get(switch (level) {
            case 1 -> EffectParameter.FLAME_DAMAGE_I;
            case 2 -> EffectParameter.FLAME_DAMAGE_II;
            default -> EffectParameter.FLAME_DAMAGE_III;
        });
        if (damage > 0.0D) entity.hurt(entity.damageSources().magic(), (float) damage);
        if (entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.FLAME, entity.getX(), entity.getY(0.55D), entity.getZ(),
                    5 + level * 2, entity.getBbWidth() * 0.35D, entity.getBbHeight() * 0.25D,
                    entity.getBbWidth() * 0.35D, 0.015D);
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }
}
