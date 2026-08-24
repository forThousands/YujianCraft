package dev.yujiancraft.wanxiang;

import net.minecraft.util.Mth;

/** Canonical model-axis presets offered by the spirit-tempering preview. */
public enum WanxiangRenderPreset {
    VANILLA_FLAT("vanilla_flat", -45.0F, 45.0F),
    AXIAL_3D("axial_3d", 0.0F, 0.0F),
    HORIZONTAL("horizontal", -90.0F, 90.0F);

    private final String serializedName;
    private final float flightAxisCorrection;
    private final float effectAxisCorrection;

    WanxiangRenderPreset(String serializedName, float flightAxisCorrection, float effectAxisCorrection) {
        this.serializedName = serializedName;
        this.flightAxisCorrection = flightAxisCorrection;
        this.effectAxisCorrection = effectAxisCorrection;
    }

    public String serializedName() { return serializedName; }
    public String translationKey() { return "render_preset.yujiancraft." + serializedName; }
    public float flightAxisCorrection() { return flightAxisCorrection; }
    public float effectAxisCorrection() { return effectAxisCorrection; }

    public static WanxiangRenderPreset fromOrdinal(int ordinal) {
        return values()[Mth.clamp(ordinal, 0, values().length - 1)];
    }
}
