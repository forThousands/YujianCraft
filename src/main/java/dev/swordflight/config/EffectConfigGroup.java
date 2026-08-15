package dev.swordflight.config;

public enum EffectConfigGroup {
    GLOBAL("global"),
    FLAME("flame"),
    LIGHTNING("lightning"),
    POISON("poison"),
    EXPLOSION("explosion"),
    ARROW_RAIN("arrow_rain"),
    REFINEMENT("refinement");

    private final String serializedName;

    EffectConfigGroup(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() { return serializedName; }
    public String translationKey() { return "config_group.swordflight." + serializedName; }
}
