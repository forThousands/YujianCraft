package dev.yujiancraft.combat.technique;

import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.config.TechniqueConfig;
import dev.yujiancraft.combat.SwordEffectEngine;
import dev.yujiancraft.combat.SwordTargetingRules;
import dev.yujiancraft.entity.FlyingSwordEntity;
import dev.yujiancraft.formation.FormationGeometry;
import dev.yujiancraft.item.FlyingSwordItem;
import dev.yujiancraft.upgrade.SwordModuleData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import java.util.Comparator;
import java.util.List;

/** Event-driven guard interception: no per-tick global raycasts and no client authority. */
@net.neoforged.fml.common.EventBusSubscriber(modid = YujianCraft.MOD_ID)
public final class FormationTechniqueEvents {
    private FormationTechniqueEvents() {
    }

    @SubscribeEvent
    public static void onPlayerHurt(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof LivingEntity victim
                && event.getSource().getDirectEntity() instanceof Projectile moduleProjectile
                && moduleProjectile.getPersistentData().hasUUID(SwordEffectEngine.PROJECTILE_OWNER_TAG)
                && victim.level() instanceof net.minecraft.server.level.ServerLevel serverLevel
                && serverLevel.getPlayerByUUID(moduleProjectile.getPersistentData().getUUID(
                        SwordEffectEngine.PROJECTILE_OWNER_TAG)) instanceof ServerPlayer swordOwner
                && !SwordTargetingRules.canActivelyTarget(swordOwner, victim)) {
            event.setCanceled(true);
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0.0F
                || event.getSource().is(DamageTypeTags.BYPASSES_SHIELD)
                || event.getSource().is(DamageTypes.THORNS)) return;
        List<FlyingSwordEntity> guards = FlyingSwordItem.getOwnedFormationSwords(player).stream()
                .filter(FlyingSwordEntity::isGuarding).toList();
        if (guards.isEmpty()) return;
        Vec3 sourcePosition = event.getSource().getSourcePosition();
        Entity direct = event.getSource().getDirectEntity();
        if (sourcePosition == null && direct != null) sourcePosition = direct.position();
        if (sourcePosition == null) return;
        Vec3 incoming = sourcePosition.subtract(player.position());
        if (incoming.lengthSqr() < 1.0E-6D) return;
        Vec3 horizontal = new Vec3(incoming.x, 0.0D, incoming.z);
        if (horizontal.lengthSqr() < 1.0E-6D) horizontal = incoming;
        Vec3 incomingDirection = horizontal.normalize();
        final Vec3 compareDirection = incomingDirection;
        FlyingSwordEntity guard = guards.stream().min(Comparator.comparingDouble(sword ->
                1.0D - FormationGeometry.guardDirection(player, sword.getFormationSlot())
                        .dot(compareDirection))).orElse(null);
        if (guard == null) return;

        float original = event.getAmount();
        event.setAmount((float) Math.max(0.0D, original * (1.0D - TechniqueConfig.guardReduction())));
        guard.triggerGuardImpact();
        int durabilityCost = TechniqueConfig.guardDurability()
                + (int) Math.ceil(original * TechniqueConfig.guardDurabilityPerDamage());
        guard.consumeSourceDurability(player, durabilityCost);
        Vec3 impact = FormationGeometry.guardPosition(player, guard.getFormationSlot(), 0.2D);
        player.serverLevel().sendParticles(ParticleTypes.FLASH, impact.x, impact.y, impact.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        player.serverLevel().sendParticles(ParticleTypes.CRIT, impact.x, impact.y, impact.z,
                8, 0.18D, 0.24D, 0.18D, 0.12D);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK,
                SoundSource.PLAYERS, 0.9F, 0.92F + player.getRandom().nextFloat() * 0.16F);

        if (direct instanceof Projectile projectile) {
            Vec3 motion = projectile.getDeltaMovement();
            if (motion.lengthSqr() > 1.0E-6D) projectile.setDeltaMovement(motion.scale(-0.35D));
        }

        Entity responsible = event.getSource().getEntity();
        if (responsible instanceof LivingEntity attacker && attacker != player && attacker.isAlive()
                && SwordTargetingRules.canActivelyTarget(player, attacker)) {
            float reflected = (float) Math.min(TechniqueConfig.guardReflectCap(),
                    original * TechniqueConfig.guardReflectPercent());
            if (reflected > 0.0F) {
                attacker.hurt(player.damageSources().thorns(player), reflected);
            }
            ItemStack sourceSword = FlyingSwordItem.findFlyingSword(player, guard.getSourceBindingId());
            if (!sourceSword.isEmpty() && attacker.isAlive()) {
                SwordEffectEngine.applyOnHit(player, attacker, SwordModuleData.copyModules(sourceSword));
            }
        }
    }
}
