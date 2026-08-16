package dev.swordflight.client;

import java.util.Locale;

/** Client-local glow intensity presets. DEFAULT preserves the established renderer exactly. */
public enum SwordGlowBrightness {
    SOFT("soft", 0.45F, 0.72F, 0.0F),
    DIM("dim", 0.70F, 0.86F, 0.0F),
    DEFAULT("default", 1.0F, 1.0F, 0.0F),
    BRIGHT("bright", 1.25F, 1.0F, 0.12F),
    RADIANT("radiant", 1.55F, 1.0F, 0.26F);

    private final String serializedName;
    private final float alphaMultiplier;
    private final float colorMultiplier;
    private final float whiteMix;

    SwordGlowBrightness(String serializedName, float alphaMultiplier,
                        float colorMultiplier, float whiteMix) {
        this.serializedName = serializedName;
        this.alphaMultiplier = alphaMultiplier;
        this.colorMultiplier = colorMultiplier;
        this.whiteMix = whiteMix;
    }

    public String serializedName() {
        return serializedName;
    }

    public String translationKey() {
        return "glow_brightness.swordflight." + serializedName;
    }

    public float alphaMultiplier() {
        return alphaMultiplier;
    }

    public float colorMultiplier() {
        return colorMultiplier;
    }

    public float whiteMix() {
        return whiteMix;
    }

    public SwordGlowBrightness next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static SwordGlowBrightness fromName(String name) {
        if (name != null) {
            String normalized = name.toLowerCase(Locale.ROOT);
            for (SwordGlowBrightness value : values()) {
                if (value.serializedName.equals(normalized)) return value;
            }
        }
        return DEFAULT;
    }
}
