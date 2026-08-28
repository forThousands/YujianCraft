package dev.yujiancraft.client;

import java.util.Locale;

/** Client-side sword-array luminance profiles. Alpha stays independent so dimming never makes
 * the seal transparent. Profiles above DEFAULT add a restrained emissive copy. */
public enum SwordArrayBrightness {
    LOW("low", 0.62F),
    SOFT("soft", 0.80F),
    DEFAULT("default", 1.00F),
    BRIGHT("bright", 1.24F),
    RADIANT("radiant", 1.48F);

    private final String serializedName;
    private final float multiplier;

    SwordArrayBrightness(String serializedName, float multiplier) {
        this.serializedName = serializedName;
        this.multiplier = multiplier;
    }

    public String serializedName() { return serializedName; }
    public float multiplier() { return multiplier; }
    public String translationKey() { return "sword_array_brightness.yujiancraft." + serializedName; }
    public SwordArrayBrightness next() { return values()[(ordinal() + 1) % values().length]; }

    public static SwordArrayBrightness fromName(String name) {
        if (name != null) {
            String normalized = name.toLowerCase(Locale.ROOT);
            for (SwordArrayBrightness value : values()) {
                if (value.serializedName.equals(normalized)) return value;
            }
        }
        return DEFAULT;
    }
}
