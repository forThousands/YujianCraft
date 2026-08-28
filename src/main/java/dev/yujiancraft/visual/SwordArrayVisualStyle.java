package dev.yujiancraft.visual;

import com.google.gson.JsonObject;

/**
 * Data-only description of the sword-array world geometry. It deliberately contains no client
 * classes, so the same schema can later be synchronized for public alternate forms.
 */
public record SwordArrayVisualStyle(
        String preset, float brightness,
        int upperLayers, float upperRadiusStep, float upperHeightStep, float upperThickness,
        float haloStrength, float fragmentStrength, float orbitSwordScale,
        int groundLayers, float groundRadiusStep, float groundThickness,
        boolean shellEnabled, int shellLayers, float shellSpacing, float shellOpacityFalloff,
        float shellBrightnessFalloff, float expandedScale,
        BeamShape beamShape, float beamWidth, float giantSwordScale, float giantSwordDescentTicks,
        Colour innerTint, Colour middleTint, Colour outerTint,
        Colour groundTint, Colour shellTint) {

    public static final SwordArrayVisualStyle DEFAULT = new SwordArrayVisualStyle(
            "default", 0.72F, 3, 0.14F, 2.8F, 0.10F, 0.68F, 0.58F, 4.8F,
            2, 0.18F, 0.06F,
            false, 2, 0.12F, 0.48F, 0.78F, 1.0F,
            BeamShape.COLUMN, 0.72F, 22.5F, 7.0F,
            Colour.fromHex("#ffe7a0"), Colour.fromHex("#bcefc9"), Colour.fromHex("#54e2c8"),
            Colour.fromHex("#72cfc9"), Colour.fromHex("#b8e7ee"));

    public static SwordArrayVisualStyle parse(JsonObject root) {
        if (root == null) return DEFAULT;
        return new SwordArrayVisualStyle(
                text(root, "preset", DEFAULT.preset),
                number(root, "brightness", DEFAULT.brightness, 0.15F, 1.5F),
                integer(root, "upperLayers", DEFAULT.upperLayers, 1, 5),
                number(root, "upperRadiusStep", DEFAULT.upperRadiusStep, 0.0F, 0.4F),
                number(root, "upperHeightStep", DEFAULT.upperHeightStep, 0.1F, 12.0F),
                number(root, "upperThickness", DEFAULT.upperThickness, 0.01F, 0.4F),
                number(root, "haloStrength", DEFAULT.haloStrength, 0.0F, 1.5F),
                number(root, "fragmentStrength", DEFAULT.fragmentStrength, 0.0F, 1.5F),
                number(root, "orbitSwordScale", DEFAULT.orbitSwordScale, 1.0F, 64.0F),
                integer(root, "groundLayers", DEFAULT.groundLayers, 0, 5),
                number(root, "groundRadiusStep", DEFAULT.groundRadiusStep, 0.0F, 0.4F),
                number(root, "groundThickness", DEFAULT.groundThickness, 0.01F, 0.4F),
                bool(root, "shellEnabled", DEFAULT.shellEnabled),
                integer(root, "shellLayers", DEFAULT.shellLayers, 1, 6),
                number(root, "shellSpacing", DEFAULT.shellSpacing, 0.03F, 0.5F),
                number(root, "shellOpacityFalloff", DEFAULT.shellOpacityFalloff, 0.2F, 1.0F),
                number(root, "shellBrightnessFalloff", DEFAULT.shellBrightnessFalloff, 0.2F, 1.0F),
                number(root, "expandedScale", DEFAULT.expandedScale, 0.6F, 1.6F),
                BeamShape.fromName(text(root, "beamShape", "column")),
                number(root, "beamWidth", DEFAULT.beamWidth, 0.2F, 2.0F),
                number(root, "giantSwordScale", DEFAULT.giantSwordScale, 3.0F, 10000.0F),
                number(root, "giantSwordDescentTicks", DEFAULT.giantSwordDescentTicks, 2.0F, 16.0F),
                Colour.fromHex(text(root, "innerTint", "#ffe7a0")),
                Colour.fromHex(text(root, "middleTint", "#bcefc9")),
                Colour.fromHex(text(root, "outerTint", text(root, "upperTint", "#54e2c8"))),
                Colour.fromHex(text(root, "groundTint", "#72bfd8")),
                Colour.fromHex(text(root, "shellTint", "#b8e7ee")));
    }

    private static int integer(JsonObject root, String name, int fallback, int min, int max) {
        try { return Math.max(min, Math.min(max, root.get(name).getAsInt())); }
        catch (Exception ignored) { return fallback; }
    }

    private static float number(JsonObject root, String name, float fallback, float min, float max) {
        try { return Math.max(min, Math.min(max, root.get(name).getAsFloat())); }
        catch (Exception ignored) { return fallback; }
    }

    private static String text(JsonObject root, String name, String fallback) {
        try { return root.get(name).getAsString(); }
        catch (Exception ignored) { return fallback; }
    }

    private static boolean bool(JsonObject root, String name, boolean fallback) {
        try { return root.get(name).getAsBoolean(); }
        catch (Exception ignored) { return fallback; }
    }

    public enum BeamShape {
        COLUMN, CONE, BLADE;
        public static BeamShape fromName(String value) {
            return switch (value == null ? "" : value.toLowerCase()) {
                case "cone" -> CONE;
                case "blade" -> BLADE;
                default -> COLUMN;
            };
        }
    }

    public record Colour(float red, float green, float blue) {
        public static Colour fromHex(String value) {
            try {
                String clean = value == null ? "" : value.strip().replace("#", "");
                if (clean.length() != 6) return new Colour(1.0F, 1.0F, 1.0F);
                int rgb = Integer.parseInt(clean, 16);
                return new Colour(((rgb >> 16) & 255) / 255.0F,
                        ((rgb >> 8) & 255) / 255.0F, (rgb & 255) / 255.0F);
            } catch (RuntimeException ignored) {
                return new Colour(1.0F, 1.0F, 1.0F);
            }
        }
    }
}
