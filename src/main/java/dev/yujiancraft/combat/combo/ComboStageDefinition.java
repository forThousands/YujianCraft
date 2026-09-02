package dev.yujiancraft.combat.combo;

public record ComboStageDefinition(int durationTicks, int commitTick, int resetGraceTicks,
                                   double damageScale, double damageRadius, double verticalRadius,
                                   int targetLimit, ComboChoreography choreography,
                                   ComboRootMotion rootMotion, ComboWarpProfile warp,
                                   ComboVfxProfile vfx, ComboHitProfile hitProfile,
                                   boolean targetSuppression) {
    public boolean damagingAttack() {
        return hitProfile != ComboHitProfile.NONE && targetLimit > 0 && damageScale > 0.0D;
    }
    public boolean finisher() {
        return choreography == ComboChoreography.GIANT_ARRAY
                || choreography == ComboChoreography.HEAVY_GIANT_ARRAY
                || choreography == ComboChoreography.STAR_RING_COLLAPSE;
    }
    public boolean giantArrayFinisher() {
        return choreography == ComboChoreography.GIANT_ARRAY
                || choreography == ComboChoreography.HEAVY_GIANT_ARRAY;
    }
    public boolean orbitSweep() { return hitProfile == ComboHitProfile.ORBIT_SWEEP; }
    public boolean warpOnly() { return warp != ComboWarpProfile.NONE && !damagingAttack() && !finisher(); }
}
