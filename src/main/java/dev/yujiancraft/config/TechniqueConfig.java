package dev.yujiancraft.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server-authoritative safety, performance and balance limits for Myriad techniques. */
public final class TechniqueConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.DoubleValue SWEEP_RADIUS;
    private static final ForgeConfigSpec.IntValue SWEEP_DURATION;
    private static final ForgeConfigSpec.DoubleValue SWEEP_ROTATIONS;
    private static final ForgeConfigSpec.DoubleValue SWEEP_HIT_WIDTH;
    private static final ForgeConfigSpec.IntValue SWEEP_TARGET_LIMIT;
    private static final ForgeConfigSpec.DoubleValue SWEEP_DAMAGE_SCALE;
    private static final ForgeConfigSpec.IntValue SWEEP_COOLDOWN;

    private static final ForgeConfigSpec.DoubleValue SWORD_ARRAY_RANGE;
    private static final ForgeConfigSpec.DoubleValue SWORD_ARRAY_WIDTH;
    private static final ForgeConfigSpec.DoubleValue SWORD_ARRAY_SPEED;
    private static final ForgeConfigSpec.IntValue SWORD_ARRAY_TARGET_LIMIT;
    private static final ForgeConfigSpec.DoubleValue SWORD_ARRAY_DAMAGE_SCALE;
    private static final ForgeConfigSpec.IntValue SWORD_ARRAY_COOLDOWN;
    private static final ForgeConfigSpec.IntValue SWORD_ARRAY_GATHER_TICKS;
    private static final ForgeConfigSpec.IntValue SWORD_ARRAY_HOLD_TICKS;
    private static final ForgeConfigSpec.DoubleValue SWORD_ARRAY_HEIGHT;
    private static final ForgeConfigSpec.DoubleValue SWORD_ARRAY_RADIUS_PADDING;
    private static final ForgeConfigSpec.IntValue SWORD_ARRAY_BARRAGE_TICKS;
    private static final ForgeConfigSpec.IntValue SWORD_ARRAY_BARRAGE_INTERVAL;
    private static final ForgeConfigSpec.IntValue SWORD_ARRAY_FINISHER_CHARGE_TICKS;
    private static final ForgeConfigSpec.IntValue SWORD_ARRAY_FINISHER_HOLD_TICKS;
    private static final ForgeConfigSpec.IntValue SWORD_ARRAY_FINISHER_EXPAND_TICKS;
    private static final ForgeConfigSpec.IntValue SWORD_ARRAY_FINISHER_SUSTAIN_TICKS;
    private static final ForgeConfigSpec.DoubleValue SWORD_ARRAY_FINISHER_EXPANSION;
    private static final ForgeConfigSpec.DoubleValue SWORD_ARRAY_FINISHER_BEAM_SCALE;
    private static final ForgeConfigSpec.DoubleValue SWORD_ARRAY_FINISHER_RADIUS;
    private static final ForgeConfigSpec.DoubleValue SWORD_ARRAY_FINISHER_DAMAGE_SCALE;

    private static final ForgeConfigSpec.DoubleValue GUARD_REDUCTION;
    private static final ForgeConfigSpec.IntValue GUARD_DURABILITY;
    private static final ForgeConfigSpec.DoubleValue GUARD_DURABILITY_PER_DAMAGE;
    private static final ForgeConfigSpec.IntValue GUARD_IMPACT_COOLDOWN;
    private static final ForgeConfigSpec.DoubleValue GUARD_REFLECT_PERCENT;
    private static final ForgeConfigSpec.DoubleValue GUARD_REFLECT_CAP;

    private static final ForgeConfigSpec.IntValue TOOL_MAX_WORK_TICKS;
    private static final ForgeConfigSpec.IntValue FISHING_MIN_WAIT;
    private static final ForgeConfigSpec.IntValue FISHING_MAX_WAIT;
    private static final ForgeConfigSpec.IntValue WORK_EFFECT_COOLDOWN;

    static {
        BUILDER.push("sweep");
        SWEEP_RADIUS = BUILDER.defineInRange("radius", 3.25D, 1.5D, 12.0D);
        SWEEP_DURATION = BUILDER.defineInRange("durationTicks", 24, 6, 80);
        SWEEP_ROTATIONS = BUILDER.defineInRange("rotations", 2.5D, 0.5D, 8.0D);
        SWEEP_HIT_WIDTH = BUILDER.defineInRange("hitWidth", 1.55D, 0.25D, 4.0D);
        SWEEP_TARGET_LIMIT = BUILDER.defineInRange("targetLimitPerSword", 8, 1, 64);
        SWEEP_DAMAGE_SCALE = BUILDER.defineInRange("damageScale", 1.0D, 0.0D, 20.0D);
        SWEEP_COOLDOWN = BUILDER.defineInRange("cooldownTicks", 30, 0, 400);
        BUILDER.pop();

        BUILDER.push("swordArray");
        SWORD_ARRAY_RANGE = BUILDER.defineInRange("range", 16.0D, 2.0D, 128.0D);
        SWORD_ARRAY_WIDTH = BUILDER.defineInRange("width", 1.5D, 0.25D, 8.0D);
        SWORD_ARRAY_SPEED = BUILDER.defineInRange("speedPerTick", 1.2D, 0.1D, 6.0D);
        SWORD_ARRAY_TARGET_LIMIT = BUILDER.defineInRange("targetLimit", 8, 1, 64);
        SWORD_ARRAY_DAMAGE_SCALE = BUILDER.defineInRange("damageScale", 0.8D, 0.0D, 20.0D);
        SWORD_ARRAY_COOLDOWN = BUILDER.defineInRange("cooldownTicks", 35, 0, 400);
        SWORD_ARRAY_GATHER_TICKS = BUILDER.defineInRange("gatherTicks", 12, 4, 80);
        SWORD_ARRAY_HOLD_TICKS = BUILDER.defineInRange("holdTicks", 12, 2, 80);
        // The array is a battlefield-scale art. These lower bounds also migrate old 0.13.3
        // server configs whose smaller values would otherwise silently defeat the new staging.
        SWORD_ARRAY_HEIGHT = BUILDER.defineInRange("formationHeight", 12.0D, 12.0D, 32.0D);
        SWORD_ARRAY_RADIUS_PADDING = BUILDER.defineInRange("radiusPadding", 8.0D, 8.0D, 24.0D);
        SWORD_ARRAY_BARRAGE_TICKS = BUILDER.defineInRange("barrageTicks", 64, 16, 240);
        SWORD_ARRAY_BARRAGE_INTERVAL = BUILDER.defineInRange("barrageIntervalTicks", 8, 3, 40);
        SWORD_ARRAY_FINISHER_CHARGE_TICKS = BUILDER.defineInRange("finisherChargeTicks", 10, 4, 80);
        SWORD_ARRAY_FINISHER_HOLD_TICKS = BUILDER.defineInRange("finisherHoldTicks", 8, 2, 60);
        SWORD_ARRAY_FINISHER_EXPAND_TICKS = BUILDER.defineInRange("finisherExpandTicks", 7, 2, 40);
        SWORD_ARRAY_FINISHER_SUSTAIN_TICKS = BUILDER.defineInRange("finisherSustainTicks", 32, 8, 120);
        SWORD_ARRAY_FINISHER_EXPANSION = BUILDER.defineInRange("finisherExpansion", 1.75D, 1.75D, 3.5D);
        SWORD_ARRAY_FINISHER_BEAM_SCALE = BUILDER.defineInRange("finisherBeamRadiusScale", 0.88D, 0.2D, 1.2D);
        SWORD_ARRAY_FINISHER_RADIUS = BUILDER.defineInRange("finisherExplosionRadius", 5.5D, 0.5D, 16.0D);
        SWORD_ARRAY_FINISHER_DAMAGE_SCALE = BUILDER.defineInRange("finisherDamageScale", 2.4D, 0.0D, 40.0D);
        BUILDER.pop();

        BUILDER.push("guard");
        GUARD_REDUCTION = BUILDER.defineInRange("damageReduction", 1.0D, 0.0D, 1.0D);
        GUARD_DURABILITY = BUILDER.defineInRange("durabilityCostPerBlock", 6, 0, 100);
        GUARD_DURABILITY_PER_DAMAGE = BUILDER.defineInRange("additionalDurabilityPerDamage", 0.5D, 0.0D, 10.0D);
        GUARD_IMPACT_COOLDOWN = BUILDER.defineInRange("impactCooldownTicks", 6, 0, 100);
        GUARD_REFLECT_PERCENT = BUILDER.defineInRange("reflectedDamagePercent", 0.25D, 0.0D, 5.0D);
        GUARD_REFLECT_CAP = BUILDER.defineInRange("reflectedDamageCap", 16.0D, 0.0D, 100000.0D);
        BUILDER.pop();

        BUILDER.push("toolUse");
        TOOL_MAX_WORK_TICKS = BUILDER.defineInRange("maximumWorkTicks", 200, 10, 1200);
        BUILDER.pop();

        BUILDER.push("spiritFishing");
        FISHING_MIN_WAIT = BUILDER.defineInRange("minimumWaitTicks", 100, 20, 2400);
        FISHING_MAX_WAIT = BUILDER.defineInRange("maximumWaitTicks", 600, 20, 6000);
        BUILDER.pop();

        BUILDER.push("workEffects");
        WORK_EFFECT_COOLDOWN = BUILDER.defineInRange("cooldownTicks", 40, 0, 1200);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private TechniqueConfig() {
    }

    public static double sweepRadius() { return SWEEP_RADIUS.get(); }
    public static int sweepDuration() { return SWEEP_DURATION.get(); }
    public static double sweepRotations() { return SWEEP_ROTATIONS.get(); }
    public static double sweepHitWidth() { return SWEEP_HIT_WIDTH.get(); }
    public static int sweepTargetLimit() { return SWEEP_TARGET_LIMIT.get(); }
    public static double sweepDamageScale() { return SWEEP_DAMAGE_SCALE.get(); }
    public static int sweepCooldown() { return SWEEP_COOLDOWN.get(); }
    public static double swordArrayRange() { return SWORD_ARRAY_RANGE.get(); }
    public static double swordArrayWidth() { return SWORD_ARRAY_WIDTH.get(); }
    public static double swordArraySpeed() { return SWORD_ARRAY_SPEED.get(); }
    public static int swordArrayTargetLimit() { return SWORD_ARRAY_TARGET_LIMIT.get(); }
    public static double swordArrayDamageScale() { return SWORD_ARRAY_DAMAGE_SCALE.get(); }
    public static int swordArrayCooldown() { return SWORD_ARRAY_COOLDOWN.get(); }
    public static int swordArrayGatherTicks() { return SWORD_ARRAY_GATHER_TICKS.get(); }
    public static int swordArrayHoldTicks() { return SWORD_ARRAY_HOLD_TICKS.get(); }
    public static double swordArrayHeight() { return SWORD_ARRAY_HEIGHT.get(); }
    public static double swordArrayRadiusPadding() { return SWORD_ARRAY_RADIUS_PADDING.get(); }
    public static int swordArrayBarrageTicks() { return SWORD_ARRAY_BARRAGE_TICKS.get(); }
    public static int swordArrayBarrageInterval() { return SWORD_ARRAY_BARRAGE_INTERVAL.get(); }
    public static int swordArrayFinisherChargeTicks() { return SWORD_ARRAY_FINISHER_CHARGE_TICKS.get(); }
    public static int swordArrayFinisherHoldTicks() { return SWORD_ARRAY_FINISHER_HOLD_TICKS.get(); }
    public static int swordArrayFinisherExpandTicks() { return SWORD_ARRAY_FINISHER_EXPAND_TICKS.get(); }
    public static int swordArrayFinisherSustainTicks() { return SWORD_ARRAY_FINISHER_SUSTAIN_TICKS.get(); }
    public static double swordArrayFinisherExpansion() { return SWORD_ARRAY_FINISHER_EXPANSION.get(); }
    public static double swordArrayFinisherBeamScale() { return SWORD_ARRAY_FINISHER_BEAM_SCALE.get(); }
    public static double swordArrayFinisherRadius() { return SWORD_ARRAY_FINISHER_RADIUS.get(); }
    public static double swordArrayFinisherDamageScale() { return SWORD_ARRAY_FINISHER_DAMAGE_SCALE.get(); }
    public static double guardReduction() { return GUARD_REDUCTION.get(); }
    public static int guardDurability() { return GUARD_DURABILITY.get(); }
    public static double guardDurabilityPerDamage() { return GUARD_DURABILITY_PER_DAMAGE.get(); }
    public static int guardImpactCooldown() { return GUARD_IMPACT_COOLDOWN.get(); }
    public static double guardReflectPercent() { return GUARD_REFLECT_PERCENT.get(); }
    public static double guardReflectCap() { return GUARD_REFLECT_CAP.get(); }
    public static int toolMaxWorkTicks() { return TOOL_MAX_WORK_TICKS.get(); }
    public static int fishingMinWait() { return Math.min(FISHING_MIN_WAIT.get(), FISHING_MAX_WAIT.get()); }
    public static int fishingMaxWait() { return Math.max(FISHING_MIN_WAIT.get(), FISHING_MAX_WAIT.get()); }
    public static int workEffectCooldown() { return WORK_EFFECT_COOLDOWN.get(); }
}
