package dev.yujiancraft.combat.technique;

import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.config.TechniqueConfig;
import dev.yujiancraft.entity.FlyingSwordEntity;
import dev.yujiancraft.formation.FormationGeometry;
import dev.yujiancraft.item.FlyingSwordItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.List;

/** Event-driven guard interception: no per-tick global raycasts and no client authority. */
@Mod.EventBusSubscriber(modid = YujianCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FormationTechniqueEvents {
    private FormationTechniqueEvents() {
    }

    @SubscribeEvent
    public static void onPlayerHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0.0F
                || event.getSource().is(DamageTypeTags.BYPASSES_SHIELD)) return;
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
    }
}
