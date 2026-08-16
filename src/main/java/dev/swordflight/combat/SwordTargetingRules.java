package dev.swordflight.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/** Server-authoritative eligibility shared by every active sword-lock mode. */
public final class SwordTargetingRules {
    private SwordTargetingRules() {
    }

    public static boolean canActivelyTarget(ServerPlayer owner, LivingEntity target) {
        if (target == owner || !target.isAlive() || target.isSpectator()) return false;
        if (target instanceof Player player) {
            // ServerPlayer.canHarmPlayer combines server PvP and scoreboard-team friendly-fire rules.
            return !player.getAbilities().invulnerable && owner.canHarmPlayer(player);
        }
        // Preserve the original active-lock behavior: passive and hostile mobs may both be selected.
        return target instanceof Mob;
    }
}
