package dev.yujiancraft.combat.combo;

import java.util.Locale;

/**
 * Stable player-selectable combo sets. Each set is assembled from reusable motion, warp and VFX
 * profiles so variants do not grow the runtime manager into another stage switch.
 */
public enum ComboStyle {
    FLOWING_BALANCE("flowing_balance", "combo_style.yujiancraft.flowing_balance",
            flowingStages(false), false, false, false, false),
    FOURFOLD_BALANCE("fourfold_balance", "combo_style.yujiancraft.fourfold_balance",
            fourfoldStages(false), false, false, false, false),
    MOUNTAIN_STRIDE("mountain_stride", "combo_style.yujiancraft.mountain_stride",
            mountainStages(true, false, true), false, false, false, true),
    SWORD_SHADOW_SWIFT("sword_shadow_swift", "combo_style.yujiancraft.sword_shadow_swift",
            shadowStages(true), true, false, false, true);

    private final String id;
    private final String translationKey;
    private final ComboStageDefinition[] stages;
    private final boolean targetSuppression;
    private final boolean persistentAureole;
    private final boolean particlesOnlyWarp;
    private final boolean heavyFinisher;

    ComboStyle(String id, String translationKey, ComboStageDefinition[] stages,
               boolean targetSuppression, boolean persistentAureole,
               boolean particlesOnlyWarp, boolean heavyFinisher) {
        this.id = id;
        this.translationKey = translationKey;
        this.stages = stages;
        this.targetSuppression = targetSuppression;
        this.persistentAureole = persistentAureole;
        this.particlesOnlyWarp = particlesOnlyWarp;
        this.heavyFinisher = heavyFinisher;
    }

    public String id() { return id; }
    public String translationKey() { return translationKey; }
    public int maxStage() { return stages.length - 1; }
    public ComboStageDefinition stage(int stage) {
        return stages[Math.max(1, Math.min(maxStage(), stage))];
    }
    public ComboStyle next() { return values()[(ordinal() + 1) % values().length]; }
    public boolean targetSuppression() { return targetSuppression; }
    public boolean particlesOnlyWarp() { return particlesOnlyWarp; }
    public boolean heavyFinisher() { return heavyFinisher; }

    public boolean hasWarpStages() {
        for (int i = 1; i < stages.length; i++) {
            if (stages[i].warp() != ComboWarpProfile.NONE) return true;
        }
        return false;
    }

    /** Aureoles persist for enhanced sets and punctuate the two Sword-Shadow suppression beats. */
    public boolean showsAureoleAt(int stage) {
        return persistentAureole || stage == maxStage() || (targetSuppression && stage == 7);
    }

    public static ComboStyle byId(String id) {
        if (id != null) {
            String normalized = id.toLowerCase(Locale.ROOT);
            for (ComboStyle style : values()) if (style.id.equals(normalized)) return style;
            // Archived pre-release variants migrate to their closest retained choreography.
            return switch (normalized) {
                case "sword_shadow_escape", "sword_shadow_traceless" -> SWORD_SHADOW_SWIFT;
                case "flowing_radiance" -> FLOWING_BALANCE;
                case "fourfold_radiance" -> FOURFOLD_BALANCE;
                case "mountain_breaker", "mountain_quake", "mountain_rush" -> MOUNTAIN_STRIDE;
                default -> FLOWING_BALANCE;
            };
        }
        return FLOWING_BALANCE;
    }

    private static ComboStageDefinition[] flowingStages(boolean enhanced) {
        return new ComboStageDefinition[]{
                null,
                attack(9, 5, 0.85D, 1.9D, 2.0D, 4, ComboChoreography.CROSS_LEFT,
                        ComboRootMotion.NONE, flowVfx(enhanced, 0)),
                attack(9, 5, 0.95D, 1.9D, 2.0D, 4, ComboChoreography.CROSS_RIGHT,
                        ComboRootMotion.NONE, flowVfx(enhanced, 1)),
                attack(13, 8, 1.25D, 4.8D, 3.4D, 12, ComboChoreography.STILL_RING,
                        ComboRootMotion.NONE, flowVfx(enhanced, 2)),
                attack(18, 10, 1.55D, 2.8D, 2.2D, 4, ComboChoreography.STILL_SIX_RELEASE,
                        ComboRootMotion.NONE, flowVfx(enhanced, 3)),
                finisher(32, 7, ComboChoreography.GIANT_ARRAY,
                        enhanced ? flowVfx(true, 4) : new ComboVfxProfile(1.70F, 0.0F, 0.0F, 0.0F))
        };
    }

    private static ComboStageDefinition[] fourfoldStages(boolean enhanced) {
        return new ComboStageDefinition[]{
                null,
                attack(9, 5, 0.85D, 1.9D, 2.0D, 4, ComboChoreography.CROSS_LEFT,
                        ComboRootMotion.NONE, flowVfx(enhanced, 0)),
                attack(9, 5, 0.95D, 1.9D, 2.0D, 4, ComboChoreography.CROSS_RIGHT,
                        ComboRootMotion.NONE, flowVfx(enhanced, 1)),
                attack(10, 5, 1.05D, 2.1D, 2.2D, 4, ComboChoreography.CROSS_RIGHT_HIGH,
                        ComboRootMotion.NONE, flowVfx(enhanced, 2)),
                attack(10, 5, 1.15D, 2.1D, 2.2D, 4, ComboChoreography.CROSS_LEFT_LOW,
                        ComboRootMotion.NONE, flowVfx(enhanced, 3)),
                attack(13, 8, 1.35D, 4.8D, 3.4D, 12, ComboChoreography.STILL_RING,
                        ComboRootMotion.NONE, flowVfx(enhanced, 4)),
                attack(18, 10, 1.65D, 2.8D, 2.2D, 4, ComboChoreography.STILL_SIX_RELEASE,
                        ComboRootMotion.NONE, flowVfx(enhanced, 5)),
                finisher(32, 7, ComboChoreography.GIANT_ARRAY,
                        enhanced ? flowVfx(true, 6) : new ComboVfxProfile(1.76F, 0.0F, 0.0F, 0.0F))
        };
    }

    private static ComboVfxProfile flowVfx(boolean enhanced, int beat) {
        float[] restrained = {0.52F, 0.62F, 0.90F, 1.15F, 1.35F, 1.48F, 1.70F};
        float base = restrained[Math.min(beat, restrained.length - 1)];
        if (!enhanced) return new ComboVfxProfile(base, 0.0F, 0.0F, 0.0F);
        float scale = 1.0F + beat * 0.13F;
        return new ComboVfxProfile(1.08F * scale, Math.min(0.92F, 0.72F + beat * 0.06F),
                0.0105F * scale, 0.0042F * scale, 4.2F + beat * 0.7F);
    }

    private static ComboStageDefinition[] mountainStages(boolean fast, boolean enhanced,
                                                          boolean expandedMotion) {
        ComboRootMotion left = expandedMotion ? ComboRootMotion.SIDE_LEFT_LONG
                : fast ? ComboRootMotion.SIDE_LEFT_FAST : ComboRootMotion.SIDE_LEFT;
        ComboRootMotion right = expandedMotion ? ComboRootMotion.SIDE_RIGHT_LONG
                : fast ? ComboRootMotion.SIDE_RIGHT_FAST : ComboRootMotion.SIDE_RIGHT;
        ComboRootMotion lunge = expandedMotion ? ComboRootMotion.FORWARD_LUNGE_LONG
                : fast ? ComboRootMotion.FORWARD_LUNGE_FAST : ComboRootMotion.FORWARD_LUNGE;
        ComboRootMotion apex = expandedMotion ? ComboRootMotion.BACKWARD_APEX_LONG
                : fast ? ComboRootMotion.BACKWARD_APEX_FAST : ComboRootMotion.BACKWARD_APEX;
        int[] duration = expandedMotion ? new int[]{8, 8, 11, 16, 30}
                : fast ? new int[]{8, 8, 12, 17, 30} : new int[]{11, 11, 16, 23, 36};
        int[] commit = expandedMotion ? new int[]{4, 4, 6, 8, 6}
                : fast ? new int[]{4, 4, 6, 9, 6} : new int[]{6, 6, 9, 13, 6};
        return new ComboStageDefinition[]{
                null,
                attack(duration[0], commit[0], 1.25D, 2.7D, 2.5D, 7,
                        ComboChoreography.BREAKER_SWEEP_LEFT, left, mountainVfx(enhanced, 0)),
                attack(duration[1], commit[1], 1.45D, 2.9D, 2.7D, 7,
                        ComboChoreography.BREAKER_SWEEP_RIGHT, right, mountainVfx(enhanced, 1)),
                attack(duration[2], commit[2], 1.90D, 4.6D, 3.2D, 14,
                        ComboChoreography.BREAKER_LUNGE_RING, lunge, mountainVfx(enhanced, 2)),
                attack(duration[3], commit[3], 2.35D, 3.8D, 3.0D, 10,
                        ComboChoreography.BREAKER_APEX_RELEASE, apex, mountainVfx(enhanced, 3)),
                finisher(duration[4], commit[4], ComboChoreography.HEAVY_GIANT_ARRAY,
                        mountainVfx(enhanced, 4))
        };
    }

    private static ComboVfxProfile mountainVfx(boolean enhanced, int beat) {
        float[] camera = {0.95F, 1.08F, 1.35F, 1.62F, 2.05F};
        float[] threshold = {0.48F, 0.56F, 0.68F, 0.78F, 0.90F};
        float[] radial = {0.0075F, 0.0090F, 0.0120F, 0.0150F, 0.0190F};
        float[] chroma = {0.0030F, 0.0038F, 0.0050F, 0.0065F, 0.0080F};
        if (!enhanced) return new ComboVfxProfile(camera[beat], threshold[beat], radial[beat], chroma[beat]);
        float scale = 1.13F + beat * 0.055F;
        return new ComboVfxProfile(camera[beat] * scale, Math.min(0.97F, threshold[beat] * 1.12F),
                radial[beat] * scale, chroma[beat] * scale, 3.8F + beat * 0.8F);
    }

    private static ComboStageDefinition[] shadowStages(boolean fast) {
        ComboWarpProfile right = fast ? ComboWarpProfile.RIGHT_SHIFT_FAST : ComboWarpProfile.RIGHT_SHIFT;
        ComboWarpProfile forward = fast ? ComboWarpProfile.FORWARD_SHIFT_FAST : ComboWarpProfile.FORWARD_SHIFT;
        ComboWarpProfile mirror = fast ? ComboWarpProfile.MIRROR_ACROSS_TARGET_FAST
                : ComboWarpProfile.MIRROR_ACROSS_TARGET;
        ComboWarpProfile rear = fast ? ComboWarpProfile.DISTANT_REAR_APEX_FAST
                : ComboWarpProfile.DISTANT_REAR_APEX;
        int[] duration = fast ? new int[]{16, 13, 16, 14, 22, 15, 26, 17, 40}
                : new int[]{20, 18, 20, 20, 28, 22, 32, 24, 48};
        int[] commit = fast ? new int[]{7, 0, 7, 0, 10, 0, 13, 0, 10}
                : new int[]{10, 0, 10, 0, 14, 0, 18, 0, 12};
        return new ComboStageDefinition[]{
                null,
                slowAttack(duration[0], commit[0], 1.40D, 3.0D, 2.7D, 8,
                        ComboChoreography.BREAKER_SWEEP_LEFT, shadowVfx(0)),
                warpOnly(duration[1], right),
                slowAttack(duration[2], commit[2], 1.62D, 3.2D, 2.9D, 8,
                        ComboChoreography.BREAKER_SWEEP_RIGHT, shadowVfx(1)),
                warpOnly(duration[3], forward),
                slowAttack(duration[4], commit[4], 2.15D, 5.1D, 3.7D, 14,
                        ComboChoreography.STILL_RING, shadowVfx(2)),
                warpOnly(duration[5], mirror),
                slowAttack(duration[6], commit[6], 2.82D, 4.4D, 3.4D, 12,
                        ComboChoreography.BREAKER_APEX_RELEASE, shadowVfx(3)),
                warpOnly(duration[7], rear),
                finisher(duration[8], commit[8], ComboChoreography.HEAVY_GIANT_ARRAY, shadowVfx(4))
        };
    }

    private static ComboVfxProfile shadowVfx(int beat) {
        float[] camera = {1.34F, 1.48F, 1.72F, 1.96F, 2.36F};
        float[] threshold = {0.78F, 0.84F, 0.90F, 0.94F, 0.96F};
        float[] radial = {0.0130F, 0.0145F, 0.0170F, 0.0200F, 0.0230F};
        float[] chroma = {0.0052F, 0.0058F, 0.0068F, 0.0080F, 0.0092F};
        float[] hold = {5.5F, 6.0F, 7.0F, 7.5F, 8.0F};
        return new ComboVfxProfile(camera[beat], threshold[beat], radial[beat], chroma[beat], hold[beat]);
    }

    private static ComboStageDefinition attack(int duration, int commit, double damageScale,
                                                double radius, double verticalRadius, int targetLimit,
                                                ComboChoreography choreography, ComboRootMotion rootMotion,
                                                ComboVfxProfile vfx) {
        return new ComboStageDefinition(duration, commit, 8, damageScale, radius, verticalRadius,
                targetLimit, choreography, rootMotion, ComboWarpProfile.NONE, vfx);
    }

    private static ComboStageDefinition slowAttack(int duration, int commit, double damageScale,
                                                    double radius, double verticalRadius, int targetLimit,
                                                    ComboChoreography choreography, ComboVfxProfile vfx) {
        return new ComboStageDefinition(duration, commit, 8, damageScale, radius, verticalRadius,
                targetLimit, choreography, ComboRootMotion.NONE, ComboWarpProfile.NONE, vfx);
    }

    private static ComboStageDefinition warpOnly(int duration, ComboWarpProfile warp) {
        return new ComboStageDefinition(duration, 0, 8, 0.0D, 0.0D, 0.0D,
                0, ComboChoreography.HOLD_FORMATION, ComboRootMotion.NONE, warp,
                new ComboVfxProfile(warp.vfxStrength(), 0.0F, 0.0F, 0.0F, 0.0F));
    }

    private static ComboStageDefinition finisher(int duration, int commit,
                                                  ComboChoreography choreography,
                                                  ComboVfxProfile vfx) {
        return new ComboStageDefinition(duration, commit, 8, 0.0D, 0.0D, 0.0D,
                0, choreography, ComboRootMotion.NONE, ComboWarpProfile.NONE, vfx);
    }
}
