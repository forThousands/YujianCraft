package dev.swordflight.visual;

public enum FlyingSwordModelStyle {
    ORIGINAL("original", 0.0F),
    FORMAL("formal", 0.2F);

    private final String serializedName;
    private final float predicateValue;

    FlyingSwordModelStyle(String serializedName, float predicateValue) {
        this.serializedName = serializedName;
        this.predicateValue = predicateValue;
    }

    public String serializedName() {
        return serializedName;
    }

    public float predicateValue() {
        return predicateValue;
    }

    public String translationKey() {
        return "model_style.swordflight." + serializedName;
    }

    public FlyingSwordModelStyle next() {
        FlyingSwordModelStyle[] styles = values();
        return styles[(ordinal() + 1) % styles.length];
    }

    public static FlyingSwordModelStyle fromName(String name) {
        for (FlyingSwordModelStyle style : values()) {
            if (style.serializedName.equals(name)) return style;
        }
        return ORIGINAL;
    }
}
