package dev.yujiancraft.combat.combo;

/** Screen-space impact settings; only sampled during the short hit envelope. */
public record ComboVfxProfile(float cameraStrength, float thresholdAmount,
                              float radialBlurStrength, float chromaticStrength,
                              float thresholdHoldTicks) {
    public ComboVfxProfile(float cameraStrength, float thresholdAmount,
                           float radialBlurStrength, float chromaticStrength) {
        this(cameraStrength, thresholdAmount, radialBlurStrength, chromaticStrength, 0.0F);
    }
}
