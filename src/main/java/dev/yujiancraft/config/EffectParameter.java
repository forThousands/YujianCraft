package dev.yujiancraft.config;

import java.util.Arrays;
import java.util.List;

public enum EffectParameter {
    GLOBAL_COOLDOWN(EffectConfigGroup.GLOBAL, "global_cooldown", 20, 0, 400, 5, true),

    FLAME_DURATION_I(EffectConfigGroup.FLAME, "flame_duration_i", 60, 20, 1200, 20, true),
    FLAME_DURATION_II(EffectConfigGroup.FLAME, "flame_duration_ii", 120, 20, 1200, 20, true),
    FLAME_DURATION_III(EffectConfigGroup.FLAME, "flame_duration_iii", 240, 20, 1200, 20, true),
    FLAME_DAMAGE_I(EffectConfigGroup.FLAME, "flame_damage_i", 1.0, 0, 50, 0.5, false),
    FLAME_DAMAGE_II(EffectConfigGroup.FLAME, "flame_damage_ii", 1.5, 0, 50, 0.5, false),
    FLAME_DAMAGE_III(EffectConfigGroup.FLAME, "flame_damage_iii", 2.0, 0, 50, 0.5, false),

    LIGHTNING_DAMAGE(EffectConfigGroup.LIGHTNING, "lightning_damage", 5.0, 0, 100, 0.5, false),

    POISON_DURATION_I(EffectConfigGroup.POISON, "poison_duration_i", 80, 20, 1200, 20, true),
    POISON_DURATION_II(EffectConfigGroup.POISON, "poison_duration_ii", 160, 20, 1200, 20, true),
    POISON_DURATION_III(EffectConfigGroup.POISON, "poison_duration_iii", 300, 20, 1200, 20, true),
    POISON_DAMAGE_I(EffectConfigGroup.POISON, "poison_damage_i", 0.5, 0, 50, 0.5, false),
    POISON_DAMAGE_II(EffectConfigGroup.POISON, "poison_damage_ii", 1.0, 0, 50, 0.5, false),
    POISON_DAMAGE_III(EffectConfigGroup.POISON, "poison_damage_iii", 1.5, 0, 50, 0.5, false),

    EXPLOSION_DAMAGE_I(EffectConfigGroup.EXPLOSION, "explosion_damage_i", 4.0, 0, 100, 0.5, false),
    EXPLOSION_DAMAGE_II(EffectConfigGroup.EXPLOSION, "explosion_damage_ii", 7.0, 0, 100, 0.5, false),
    EXPLOSION_DAMAGE_III(EffectConfigGroup.EXPLOSION, "explosion_damage_iii", 12.0, 0, 100, 0.5, false),

    ARROW_COUNT_I(EffectConfigGroup.ARROW_RAIN, "arrow_count_i", 2, 1, 24, 1, true),
    ARROW_COUNT_II(EffectConfigGroup.ARROW_RAIN, "arrow_count_ii", 4, 1, 24, 1, true),
    ARROW_COUNT_III(EffectConfigGroup.ARROW_RAIN, "arrow_count_iii", 7, 1, 24, 1, true),
    ARROW_DAMAGE(EffectConfigGroup.ARROW_RAIN, "arrow_damage", 2.5, 0, 50, 0.5, false),

    DAMAGE_BONUS_I(EffectConfigGroup.REFINEMENT, "damage_bonus_i", 1.0, 0, 50, 0.5, false),
    DAMAGE_BONUS_II(EffectConfigGroup.REFINEMENT, "damage_bonus_ii", 3.0, 0, 50, 0.5, false),
    DAMAGE_BONUS_III(EffectConfigGroup.REFINEMENT, "damage_bonus_iii", 7.0, 0, 50, 0.5, false),
    DURABILITY_BONUS_I(EffectConfigGroup.REFINEMENT, "durability_bonus_i", 500, 0, 50000, 100, true),
    DURABILITY_BONUS_II(EffectConfigGroup.REFINEMENT, "durability_bonus_ii", 2000, 0, 50000, 100, true),
    DURABILITY_BONUS_III(EffectConfigGroup.REFINEMENT, "durability_bonus_iii", 8000, 0, 50000, 100, true),

    SWORD_ARRAY_FINISHER_DAMAGE_SCALE(EffectConfigGroup.FINISHERS,
            "sword_array_finisher_damage_scale", 12.0, 0, 500, 1.0, false),
    COMBO_FINISHER_DAMAGE_SCALE(EffectConfigGroup.FINISHERS,
            "combo_finisher_damage_scale", 32.0, 0, 500, 1.0, false);

    private final EffectConfigGroup group;
    private final String serializedName;
    private final double defaultValue;
    private final double minimum;
    private final double maximum;
    private final double step;
    private final boolean integerDisplay;

    EffectParameter(EffectConfigGroup group, String serializedName, double defaultValue,
                    double minimum, double maximum, double step, boolean integerDisplay) {
        this.group = group;
        this.serializedName = serializedName;
        this.defaultValue = defaultValue;
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
        this.integerDisplay = integerDisplay;
    }

    public EffectConfigGroup group() { return group; }
    public String serializedName() { return serializedName; }
    public double defaultValue() { return defaultValue; }
    public double minimum() { return minimum; }
    public double maximum() { return maximum; }
    public double step() { return step; }
    public boolean integerDisplay() { return integerDisplay; }
    public String translationKey() { return "effect_parameter.yujiancraft." + serializedName; }

    public static List<EffectParameter> forGroup(EffectConfigGroup group) {
        return Arrays.stream(values()).filter(parameter -> parameter.group == group).toList();
    }
}
