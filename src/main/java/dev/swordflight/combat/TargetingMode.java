package dev.swordflight.combat;

public enum TargetingMode {
    AUTOMATIC("automatic"),
    CROSSHAIR_LOCK("crosshair_lock"),
    MANUAL_GUIDANCE("manual_guidance");

    private final String name;

    TargetingMode(String name) {
        this.name = name;
    }

    public String translationKey() {
        return "targeting.swordflight." + name;
    }

    public TargetingMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static TargetingMode fromOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : AUTOMATIC;
    }
}
