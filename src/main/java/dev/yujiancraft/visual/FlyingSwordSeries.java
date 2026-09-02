package dev.yujiancraft.visual;

import dev.yujiancraft.material.FlyingSwordMaterial;

/** Extensible visual/crafting families that share the same material balance rules. */
public enum FlyingSwordSeries {
    STANDARD("standard", false, 1.0F, AuraStyle.STANDARD, false),
    SPIRITFORGED("spiritforged", true, 1.0F, AuraStyle.STANDARD, false),
    LUMINOUS("luminous", true, 1.18F, AuraStyle.NONE, true),
    CONDENSED("condensed", true, 1.18F, AuraStyle.TIGHT, false);

    private final String serializedName;
    private final boolean axialModel;
    private final float flightModelScale;
    private final AuraStyle auraStyle;
    private final boolean luminousBladeCore;

    FlyingSwordSeries(String serializedName, boolean axialModel, float flightModelScale,
                      AuraStyle auraStyle, boolean luminousBladeCore) {
        this.serializedName = serializedName;
        this.axialModel = axialModel;
        this.flightModelScale = flightModelScale;
        this.auraStyle = auraStyle;
        this.luminousBladeCore = luminousBladeCore;
    }

    public String serializedName() {
        return serializedName;
    }

    public boolean usesAxialModel() {
        return axialModel;
    }

    public boolean usesSlenderModel() {
        return this == LUMINOUS || this == CONDENSED;
    }

    public float flightModelScale() {
        return flightModelScale;
    }

    public AuraStyle auraStyle() {
        return auraStyle;
    }

    public boolean hasLuminousBladeCore() {
        return luminousBladeCore;
    }

    public String itemId(FlyingSwordMaterial material) {
        return this == STANDARD
                ? material.itemId()
                : material.serializedName() + "_" + serializedName + "_flying_sword";
    }

    public static FlyingSwordSeries fromOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : STANDARD;
    }

    public enum AuraStyle {
        STANDARD,
        NONE,
        TIGHT
    }
}
