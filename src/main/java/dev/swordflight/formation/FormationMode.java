package dev.swordflight.formation;

public enum FormationMode {
    FAN("fan"),
    RING("ring"),
    FAN_ALIGNED("fan_aligned");

    private final String serializedName;

    FormationMode(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public String translationKey() {
        return "formation.swordflight." + serializedName;
    }

    public FormationMode next() {
        return switch (this) {
            case FAN_ALIGNED -> RING;
            case RING -> FAN;
            case FAN -> FAN_ALIGNED;
        };
    }

    public boolean usesRingGeometry() {
        return this == RING;
    }

    public boolean usesLegacyVisualAxis() {
        return this == FAN;
    }

    public static FormationMode fromName(String name) {
        for (FormationMode mode : values()) {
            if (mode.serializedName.equals(name)) {
                return mode;
            }
        }
        return FAN_ALIGNED;
    }
}
