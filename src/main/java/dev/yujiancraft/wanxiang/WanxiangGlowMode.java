package dev.yujiancraft.wanxiang;

import net.minecraft.util.Mth;

/** Rendering safety levels for unknown third-party item models. */
public enum WanxiangGlowMode {
    FULL_BODY("full_body"),
    AURA_ONLY("aura_only"),
    ORIGINAL("original");

    private final String serializedName;

    WanxiangGlowMode(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() { return serializedName; }
    public String translationKey() { return "glow_mode.yujiancraft." + serializedName; }

    public static WanxiangGlowMode fromOrdinal(int ordinal) {
        return values()[Mth.clamp(ordinal, 0, values().length - 1)];
    }
}
