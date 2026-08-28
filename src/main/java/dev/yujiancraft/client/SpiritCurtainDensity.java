package dev.yujiancraft.client;

import java.util.Locale;

public enum SpiritCurtainDensity {
    LOW("low", 8, 6),
    STANDARD("standard", 13, 7),
    HIGH("high", 18, 8);

    private final String serializedName;
    private final int curtainCount;
    private final int segments;

    SpiritCurtainDensity(String serializedName, int curtainCount, int segments) {
        this.serializedName = serializedName;
        this.curtainCount = curtainCount;
        this.segments = segments;
    }

    public String serializedName() { return serializedName; }
    public int curtainCount() { return curtainCount; }
    public int segments() { return segments; }
    public String translationKey() { return "spirit_curtain_density.yujiancraft." + serializedName; }
    public SpiritCurtainDensity next() { return values()[(ordinal() + 1) % values().length]; }

    public static SpiritCurtainDensity fromName(String name) {
        if (name != null) {
            String normalized = name.toLowerCase(Locale.ROOT);
            for (SpiritCurtainDensity value : values()) {
                if (value.serializedName.equals(normalized)) return value;
            }
        }
        return STANDARD;
    }
}
