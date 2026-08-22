package dev.yujiancraft.visual;

import dev.yujiancraft.material.FlyingSwordMaterial;

/** Extensible visual/crafting families that share the same material balance rules. */
public enum FlyingSwordSeries {
    STANDARD("standard"),
    SPIRITFORGED("spiritforged");

    private final String serializedName;

    FlyingSwordSeries(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public String itemId(FlyingSwordMaterial material) {
        return this == STANDARD
                ? material.itemId()
                : material.serializedName() + "_" + serializedName + "_flying_sword";
    }

    public static FlyingSwordSeries fromOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : STANDARD;
    }
}
