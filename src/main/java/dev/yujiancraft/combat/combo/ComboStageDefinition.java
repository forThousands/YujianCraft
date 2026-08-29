package dev.yujiancraft.combat.combo;

public record ComboStageDefinition(int durationTicks, int commitTick, int resetGraceTicks,
                                   double damageScale, double damageRadius, double verticalRadius,
                                   int targetLimit, ComboChoreography choreography,
                                   ComboRootMotion rootMotion, ComboWarpProfile warp,
                                   ComboVfxProfile vfx) {
    public boolean damagingAttack() { return targetLimit > 0 && damageScale > 0.0D; }
    public boolean finisher() {
        return choreography == ComboChoreography.GIANT_ARRAY
                || choreography == ComboChoreography.HEAVY_GIANT_ARRAY;
    }
    public boolean warpOnly() { return warp != ComboWarpProfile.NONE && !damagingAttack() && !finisher(); }
}
