package dev.swordflight.combat;

public enum TargetingMode {
    AUTOMATIC("automatic"),
    CROSSHAIR_LOCK("crosshair_lock");

    private final String name;

    TargetingMode(String name) {
        this.name = name;
    }

    public String translationKey() {
        return "targeting.swordflight." + name;
    }

    public TargetingMode next() {
        return this == AUTOMATIC ? CROSSHAIR_LOCK : AUTOMATIC;
    }

    public static TargetingMode fromOrdinal(int ordinal) {
        return ordinal == CROSSHAIR_LOCK.ordinal() ? CROSSHAIR_LOCK : AUTOMATIC;
    }
}
