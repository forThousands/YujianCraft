package dev.yujiancraft.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server-authoritative safety, performance and balance limits for Myriad techniques. */
public final class TechniqueConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.DoubleValue SWEEP_RADIUS;
    private static final ForgeConfigSpec.IntValue SWEEP_DURATION;
    private static final ForgeConfigSpec.IntValue SWEEP_TARGET_LIMIT;
    private static final ForgeConfigSpec.DoubleValue SWEEP_DAMAGE_SCALE;
    private static final ForgeConfigSpec.IntValue SWEEP_COOLDOWN;

    private static final ForgeConfigSpec.DoubleValue QI_RANGE;
    private static final ForgeConfigSpec.DoubleValue QI_WIDTH;
    private static final ForgeConfigSpec.DoubleValue QI_SPEED;
    private static final ForgeConfigSpec.IntValue QI_TARGET_LIMIT;
    private static final ForgeConfigSpec.DoubleValue QI_DAMAGE_SCALE;
    private static final ForgeConfigSpec.IntValue QI_COOLDOWN;

    private static final ForgeConfigSpec.DoubleValue GUARD_REDUCTION;
    private static final ForgeConfigSpec.IntValue GUARD_DURABILITY;
    private static final ForgeConfigSpec.IntValue GUARD_IMPACT_COOLDOWN;

    private static final ForgeConfigSpec.DoubleValue TOOL_RANGE;
    private static final ForgeConfigSpec.IntValue TOOL_MAX_WORK_TICKS;
    private static final ForgeConfigSpec.DoubleValue FISHING_RANGE;
    private static final ForgeConfigSpec.IntValue FISHING_MIN_WAIT;
    private static final ForgeConfigSpec.IntValue FISHING_MAX_WAIT;

    static {
        BUILDER.push("sweep");
        SWEEP_RADIUS = BUILDER.defineInRange("radius", 3.25D, 1.5D, 12.0D);
        SWEEP_DURATION = BUILDER.defineInRange("durationTicks", 18, 6, 80);
        SWEEP_TARGET_LIMIT = BUILDER.defineInRange("targetLimitPerSword", 8, 1, 64);
        SWEEP_DAMAGE_SCALE = BUILDER.defineInRange("damageScale", 1.0D, 0.0D, 20.0D);
        SWEEP_COOLDOWN = BUILDER.defineInRange("cooldownTicks", 30, 0, 400);
        BUILDER.pop();

        BUILDER.push("swordQi");
        QI_RANGE = BUILDER.defineInRange("range", 16.0D, 2.0D, 128.0D);
        QI_WIDTH = BUILDER.defineInRange("width", 1.5D, 0.25D, 8.0D);
        QI_SPEED = BUILDER.defineInRange("speedPerTick", 1.2D, 0.1D, 6.0D);
        QI_TARGET_LIMIT = BUILDER.defineInRange("targetLimit", 8, 1, 64);
        QI_DAMAGE_SCALE = BUILDER.defineInRange("damageScale", 0.8D, 0.0D, 20.0D);
        QI_COOLDOWN = BUILDER.defineInRange("cooldownTicks", 35, 0, 400);
        BUILDER.pop();

        BUILDER.push("guard");
        GUARD_REDUCTION = BUILDER.defineInRange("damageReduction", 1.0D, 0.0D, 1.0D);
        GUARD_DURABILITY = BUILDER.defineInRange("durabilityCostPerBlock", 1, 0, 100);
        GUARD_IMPACT_COOLDOWN = BUILDER.defineInRange("impactCooldownTicks", 6, 0, 100);
        BUILDER.pop();

        BUILDER.push("toolUse");
        TOOL_RANGE = BUILDER.defineInRange("maximumRange", 16.0D, 2.0D, 64.0D);
        TOOL_MAX_WORK_TICKS = BUILDER.defineInRange("maximumWorkTicks", 200, 10, 1200);
        BUILDER.pop();

        BUILDER.push("spiritFishing");
        FISHING_RANGE = BUILDER.defineInRange("maximumRange", 24.0D, 2.0D, 64.0D);
        FISHING_MIN_WAIT = BUILDER.defineInRange("minimumWaitTicks", 100, 20, 2400);
        FISHING_MAX_WAIT = BUILDER.defineInRange("maximumWaitTicks", 600, 20, 6000);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private TechniqueConfig() {
    }

    public static double sweepRadius() { return SWEEP_RADIUS.get(); }
    public static int sweepDuration() { return SWEEP_DURATION.get(); }
    public static int sweepTargetLimit() { return SWEEP_TARGET_LIMIT.get(); }
    public static double sweepDamageScale() { return SWEEP_DAMAGE_SCALE.get(); }
    public static int sweepCooldown() { return SWEEP_COOLDOWN.get(); }
    public static double qiRange() { return QI_RANGE.get(); }
    public static double qiWidth() { return QI_WIDTH.get(); }
    public static double qiSpeed() { return QI_SPEED.get(); }
    public static int qiTargetLimit() { return QI_TARGET_LIMIT.get(); }
    public static double qiDamageScale() { return QI_DAMAGE_SCALE.get(); }
    public static int qiCooldown() { return QI_COOLDOWN.get(); }
    public static double guardReduction() { return GUARD_REDUCTION.get(); }
    public static int guardDurability() { return GUARD_DURABILITY.get(); }
    public static int guardImpactCooldown() { return GUARD_IMPACT_COOLDOWN.get(); }
    public static double toolRange() { return TOOL_RANGE.get(); }
    public static int toolMaxWorkTicks() { return TOOL_MAX_WORK_TICKS.get(); }
    public static double fishingRange() { return FISHING_RANGE.get(); }
    public static int fishingMinWait() { return Math.min(FISHING_MIN_WAIT.get(), FISHING_MAX_WAIT.get()); }
    public static int fishingMaxWait() { return Math.max(FISHING_MIN_WAIT.get(), FISHING_MAX_WAIT.get()); }
}
