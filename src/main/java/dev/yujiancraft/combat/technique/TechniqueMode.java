package dev.yujiancraft.combat.technique;

/** Behaviour performed by a deployed formation; independent from its A/B/C docking geometry. */
public enum TechniqueMode {
    PIERCE("pierce"),
    SWEEP("sweep"),
    SWORD_ARRAY("sword_array"),
    GUARD("guard"),
    TOOL_USE("tool_use"),
    SPIRIT_FISHING("spirit_fishing");

    private final String serializedName;

    TechniqueMode(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public String translationKey() {
        return "technique.yujiancraft." + serializedName;
    }

    public TechniqueMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public boolean isPassive() {
        return this == GUARD || this == TOOL_USE || this == SPIRIT_FISHING;
    }

    public static TechniqueMode fromOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : PIERCE;
    }

    public static TechniqueMode fromName(String name) {
        for (TechniqueMode mode : values()) {
            if (mode.serializedName.equals(name)) return mode;
        }
        return PIERCE;
    }
}
