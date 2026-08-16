package dev.swordflight.client;

import java.util.Locale;

/**
 * Independent client-side presentation profiles. DEFAULT is a sentinel that always selects the
 * untouched 0.9.6 renderer; its numeric values are documentation only and are never applied.
 */
public enum SwordGlowBrightness {
    SOFT("soft", 72, 0.55F, 0.60F, 0.52F, 0.0F),
    DIM("dim", 124, 0.78F, 0.80F, 0.76F, 0.0F),
    DEFAULT("default", 188, 1.0F, 1.0F, 1.0F, 0.0F),
    BRIGHT("bright", 218, 1.16F, 1.14F, 1.15F, 0.08F),
    RADIANT("radiant", 250, 1.34F, 1.27F, 1.30F, 0.16F);

    private final String serializedName;
    private final int bodyOverlayAlpha;
    private final float auraStrength;
    private final float trailStrength;
    private final float bloomStrength;
    private final float whiteMix;

    SwordGlowBrightness(String serializedName, int bodyOverlayAlpha, float auraStrength,
                        float trailStrength, float bloomStrength, float whiteMix) {
        this.serializedName = serializedName;
        this.bodyOverlayAlpha = bodyOverlayAlpha;
        this.auraStrength = auraStrength;
        this.trailStrength = trailStrength;
        this.bloomStrength = bloomStrength;
        this.whiteMix = whiteMix;
    }

    public String serializedName() { return serializedName; }
    public String translationKey() { return "glow_brightness.swordflight." + serializedName; }
    public int bodyOverlayAlpha() { return bodyOverlayAlpha; }
    public float auraStrength() { return auraStrength; }
    public float trailStrength() { return trailStrength; }
    public float bloomStrength() { return bloomStrength; }
    public float whiteMix() { return whiteMix; }
    public boolean usesLegacyRenderer() { return this == DEFAULT; }

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
