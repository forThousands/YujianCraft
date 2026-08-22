package dev.yujiancraft.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Client-only preferences plus the intentionally file-gated developer menu switch. */
public final class ClientOptions {
    /** Package defaults edited by the visual development console. */
    public static final boolean DEFAULT_FLIGHT_SOUND = true;
    public static final boolean DEFAULT_SWORD_TRAIL = true;
    public static final boolean DEFAULT_SWORD_BODY_GLOW = true;
    public static final boolean DEFAULT_INVENTORY_GLINT = true;
    public static final boolean DEFAULT_SWORD_ENERGY_HIGHLIGHT = false;
    public static final boolean DEFAULT_SWORD_OUTLINE = false;
    public static final boolean DEFAULT_FLAME_MODULE_VISUAL = true;
    public static final boolean DEFAULT_LIGHTNING_MODULE_VISUAL = true;
    public static final boolean DEFAULT_POISON_MODULE_VISUAL = true;
    public static final boolean DEFAULT_EXPLOSION_MODULE_VISUAL = true;
    public static final boolean DEFAULT_ARROW_RAIN_MODULE_VISUAL = true;
    public static final boolean DEFAULT_HIT_IMPACT_VISUAL = true;
    public static final boolean DEFAULT_WORKBENCH_PREVIEW = true;
    public static final boolean DEFAULT_OPTIMIZED_THIRD_PERSON = true;
    public static final SwordGlowBrightness DEFAULT_GLOW_BRIGHTNESS = SwordGlowBrightness.DEFAULT;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DIRECTORY_NAME = "yujiancraft";
    private static final String FILE_NAME = "client-options.json";
    private static JsonObject document = defaultsDocument();
    private static boolean showDeveloperOptions;
    private static boolean optimizedThirdPerson;
    private static boolean swordRidingEnabled;
    private static boolean flightSound;
    private static boolean swordTrail;
    private static boolean swordBodyGlow;
    private static boolean swordEnergyHighlight;
    private static boolean swordOutline;
    private static boolean inventoryGlint;
    private static boolean flameModuleVisual;
    private static boolean lightningModuleVisual;
    private static boolean poisonModuleVisual;
    private static boolean explosionModuleVisual;
    private static boolean arrowRainModuleVisual;
    private static boolean hitImpactVisual;
    private static boolean workbenchPreview;
    private static SwordGlowBrightness glowBrightness = DEFAULT_GLOW_BRIGHTNESS;

    private ClientOptions() {
    }

    public static synchronized void load() {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            if (Files.notExists(path)) {
                document = defaultsDocument();
                saveDocument(path);
            } else {
                document = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            }
            if (migrateDocument()) saveDocument(path);
            showDeveloperOptions = booleanValue("showDeveloperOptions", false);
            optimizedThirdPerson = booleanValue("optimizedThirdPerson", DEFAULT_OPTIMIZED_THIRD_PERSON);
            swordRidingEnabled = booleanValue("swordRidingEnabled", true);
            flightSound = booleanValue("flightSound", DEFAULT_FLIGHT_SOUND);
            swordTrail = booleanValue("swordTrail", DEFAULT_SWORD_TRAIL);
            swordBodyGlow = booleanValue("swordBodyGlow", DEFAULT_SWORD_BODY_GLOW);
            swordEnergyHighlight = booleanValue("swordEnergyHighlight", DEFAULT_SWORD_ENERGY_HIGHLIGHT);
            swordOutline = booleanValue("swordOutline", DEFAULT_SWORD_OUTLINE);
            inventoryGlint = booleanValue("inventoryGlint", DEFAULT_INVENTORY_GLINT);
            flameModuleVisual = booleanValue("flameModuleVisual", DEFAULT_FLAME_MODULE_VISUAL);
            lightningModuleVisual = booleanValue("lightningModuleVisual", DEFAULT_LIGHTNING_MODULE_VISUAL);
            poisonModuleVisual = booleanValue("poisonModuleVisual", DEFAULT_POISON_MODULE_VISUAL);
            explosionModuleVisual = booleanValue("explosionModuleVisual", DEFAULT_EXPLOSION_MODULE_VISUAL);
            arrowRainModuleVisual = booleanValue("arrowRainModuleVisual", DEFAULT_ARROW_RAIN_MODULE_VISUAL);
            hitImpactVisual = booleanValue("hitImpactVisual", DEFAULT_HIT_IMPACT_VISUAL);
            workbenchPreview = booleanValue("workbenchPreview", DEFAULT_WORKBENCH_PREVIEW);
            glowBrightness = SwordGlowBrightness.fromName(stringValue("glowBrightness",
                    DEFAULT_GLOW_BRIGHTNESS.serializedName()));
        } catch (Exception exception) {
            showDeveloperOptions = false;
            optimizedThirdPerson = DEFAULT_OPTIMIZED_THIRD_PERSON;
            swordRidingEnabled = true;
            flightSound = DEFAULT_FLIGHT_SOUND;
            swordTrail = DEFAULT_SWORD_TRAIL;
            swordBodyGlow = DEFAULT_SWORD_BODY_GLOW;
            swordEnergyHighlight = DEFAULT_SWORD_ENERGY_HIGHLIGHT;
            swordOutline = DEFAULT_SWORD_OUTLINE;
            inventoryGlint = DEFAULT_INVENTORY_GLINT;
            flameModuleVisual = DEFAULT_FLAME_MODULE_VISUAL;
            lightningModuleVisual = DEFAULT_LIGHTNING_MODULE_VISUAL;
            poisonModuleVisual = DEFAULT_POISON_MODULE_VISUAL;
            explosionModuleVisual = DEFAULT_EXPLOSION_MODULE_VISUAL;
            arrowRainModuleVisual = DEFAULT_ARROW_RAIN_MODULE_VISUAL;
            hitImpactVisual = DEFAULT_HIT_IMPACT_VISUAL;
            workbenchPreview = DEFAULT_WORKBENCH_PREVIEW;
            glowBrightness = DEFAULT_GLOW_BRIGHTNESS;
            LOGGER.error("Could not load YujianCraft client options from {}", path, exception);
        }
    }

    public static boolean showDeveloperOptions() {
        return showDeveloperOptions;
    }

    public static boolean optimizedThirdPerson() {
        return optimizedThirdPerson;
    }

    public static boolean swordRidingEnabled() { return swordRidingEnabled; }

    public static boolean flightSound() { return flightSound; }
    public static boolean swordTrail() { return swordTrail; }
    public static boolean swordBodyGlow() { return swordBodyGlow; }
    public static boolean swordEnergyHighlight() { return swordEnergyHighlight; }
    public static boolean swordOutline() { return swordOutline; }
    public static boolean inventoryGlint() { return inventoryGlint; }
    public static boolean flameModuleVisual() { return flameModuleVisual; }
    public static boolean lightningModuleVisual() { return lightningModuleVisual; }
    public static boolean poisonModuleVisual() { return poisonModuleVisual; }
    public static boolean explosionModuleVisual() { return explosionModuleVisual; }
    public static boolean arrowRainModuleVisual() { return arrowRainModuleVisual; }
    public static boolean hitImpactVisual() { return hitImpactVisual; }
    public static boolean workbenchPreview() { return workbenchPreview; }
    public static SwordGlowBrightness glowBrightness() { return glowBrightness; }

    public static synchronized void setOptimizedThirdPerson(boolean enabled) {
        optimizedThirdPerson = enabled;
        setBoolean("optimizedThirdPerson", enabled);
    }

    public static synchronized void setSwordRidingEnabled(boolean enabled) {
        swordRidingEnabled = enabled;
        setBoolean("swordRidingEnabled", enabled);
    }

    public static synchronized void setFlightSound(boolean enabled) {
        flightSound = enabled;
        setBoolean("flightSound", enabled);
    }

    public static synchronized void setSwordTrail(boolean enabled) {
        swordTrail = enabled;
        setBoolean("swordTrail", enabled);
    }

    public static synchronized void setSwordBodyGlow(boolean enabled) {
        swordBodyGlow = enabled;
        setBoolean("swordBodyGlow", enabled);
    }

    public static synchronized void setSwordEnergyHighlight(boolean enabled) {
        swordEnergyHighlight = enabled;
        setBoolean("swordEnergyHighlight", enabled);
    }

    public static synchronized void setSwordOutline(boolean enabled) {
        swordOutline = enabled;
        setBoolean("swordOutline", enabled);
    }

    public static synchronized void setInventoryGlint(boolean enabled) {
        inventoryGlint = enabled;
        setBoolean("inventoryGlint", enabled);
    }

    public static synchronized void setFlameModuleVisual(boolean enabled) {
        flameModuleVisual = enabled;
        setBoolean("flameModuleVisual", enabled);
    }

    public static synchronized void setLightningModuleVisual(boolean enabled) {
        lightningModuleVisual = enabled;
        setBoolean("lightningModuleVisual", enabled);
    }

    public static synchronized void setPoisonModuleVisual(boolean enabled) {
        poisonModuleVisual = enabled;
        setBoolean("poisonModuleVisual", enabled);
    }

    public static synchronized void setExplosionModuleVisual(boolean enabled) {
        explosionModuleVisual = enabled;
        setBoolean("explosionModuleVisual", enabled);
    }

    public static synchronized void setArrowRainModuleVisual(boolean enabled) {
        arrowRainModuleVisual = enabled;
        setBoolean("arrowRainModuleVisual", enabled);
    }

    public static synchronized void setHitImpactVisual(boolean enabled) {
        hitImpactVisual = enabled;
        setBoolean("hitImpactVisual", enabled);
    }

    public static synchronized void setWorkbenchPreview(boolean enabled) {
        workbenchPreview = enabled;
        setBoolean("workbenchPreview", enabled);
    }

    public static synchronized void setGlowBrightness(SwordGlowBrightness brightness) {
        glowBrightness = brightness == null ? DEFAULT_GLOW_BRIGHTNESS : brightness;
        setString("glowBrightness", glowBrightness.serializedName());
    }

    private static void setBoolean(String key, boolean enabled) {
        document.addProperty(key, enabled);
        saveOptionDocument();
    }

    private static void setString(String key, String value) {
        document.addProperty(key, value);
        saveOptionDocument();
    }

    private static void saveOptionDocument() {
        try {
            Files.createDirectories(path().getParent());
            saveDocument(path());
        } catch (IOException exception) {
            LOGGER.error("Could not save YujianCraft client options to {}", path(), exception);
        }
    }

    public static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(DIRECTORY_NAME).resolve(FILE_NAME);
    }

    private static boolean booleanValue(String key, boolean fallback) {
        return document.has(key) && document.get(key).isJsonPrimitive()
                ? document.get(key).getAsBoolean() : fallback;
    }

    private static String stringValue(String key, String fallback) {
        return document.has(key) && document.get(key).isJsonPrimitive()
                ? document.get(key).getAsString() : fallback;
    }

    private static JsonObject defaultsDocument() {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 13);
        root.addProperty("showDeveloperOptions", false);
        root.addProperty("optimizedThirdPerson", DEFAULT_OPTIMIZED_THIRD_PERSON);
        root.addProperty("swordRidingEnabled", true);
        root.addProperty("flightSound", DEFAULT_FLIGHT_SOUND);
        root.addProperty("swordTrail", DEFAULT_SWORD_TRAIL);
        root.addProperty("swordBodyGlow", DEFAULT_SWORD_BODY_GLOW);
        root.addProperty("swordEnergyHighlight", DEFAULT_SWORD_ENERGY_HIGHLIGHT);
        root.addProperty("swordOutline", DEFAULT_SWORD_OUTLINE);
        root.addProperty("inventoryGlint", DEFAULT_INVENTORY_GLINT);
        root.addProperty("flameModuleVisual", DEFAULT_FLAME_MODULE_VISUAL);
        root.addProperty("lightningModuleVisual", DEFAULT_LIGHTNING_MODULE_VISUAL);
        root.addProperty("poisonModuleVisual", DEFAULT_POISON_MODULE_VISUAL);
        root.addProperty("explosionModuleVisual", DEFAULT_EXPLOSION_MODULE_VISUAL);
        root.addProperty("arrowRainModuleVisual", DEFAULT_ARROW_RAIN_MODULE_VISUAL);
        root.addProperty("hitImpactVisual", DEFAULT_HIT_IMPACT_VISUAL);
        root.addProperty("workbenchPreview", DEFAULT_WORKBENCH_PREVIEW);
        root.addProperty("glowBrightness", DEFAULT_GLOW_BRIGHTNESS.serializedName());
        root.addProperty("developerOptionsHint",
                "Set showDeveloperOptions to true and reopen the in-game config screen. OP permission is still required.");
        return root;
    }

    private static boolean migrateDocument() {
        boolean changed = false;
        int schemaVersion = document.has("schemaVersion") ? document.get("schemaVersion").getAsInt() : 0;
        if (schemaVersion < 6) {
            changed = true;
        }
        if (schemaVersion < 7) {
            // The former client-wide model switch became a separately craftable sword series.
            document.remove("swordModelStyle");
            changed = true;
        }
        if (schemaVersion < 8) {
            changed = true;
        }
        if (document.has("swordWhiteHotHighlight")) {
            // White-hot is now an item module installed with a magma block, never a
            // client-global presentation toggle. Remove the obsolete key regardless of
            // the recorded schema so hand-edited and pre-release configs migrate too.
            document.remove("swordWhiteHotHighlight");
            changed = true;
        }
        if (schemaVersion < 9) {
            document.addProperty("schemaVersion", 9);
            changed = true;
        }
        if (schemaVersion < 10) {
            document.addProperty("schemaVersion", 10);
            changed = true;
        }
        if (schemaVersion < 11) {
            document.addProperty("schemaVersion", 11);
            changed = true;
        }
        if (schemaVersion < 12) {
            document.addProperty("schemaVersion", 12);
            changed = true;
        }
        if (schemaVersion < 13) {
            document.addProperty("schemaVersion", 13);
            changed = true;
        }
        changed |= addBooleanIfMissing("showDeveloperOptions", false);
        changed |= addBooleanIfMissing("optimizedThirdPerson", DEFAULT_OPTIMIZED_THIRD_PERSON);
        changed |= addBooleanIfMissing("swordRidingEnabled", true);
        changed |= addBooleanIfMissing("flightSound", DEFAULT_FLIGHT_SOUND);
        changed |= addBooleanIfMissing("swordTrail", DEFAULT_SWORD_TRAIL);
        changed |= addBooleanIfMissing("swordBodyGlow", DEFAULT_SWORD_BODY_GLOW);
        changed |= addBooleanIfMissing("swordEnergyHighlight", DEFAULT_SWORD_ENERGY_HIGHLIGHT);
        changed |= addBooleanIfMissing("swordOutline", DEFAULT_SWORD_OUTLINE);
        changed |= addBooleanIfMissing("inventoryGlint", DEFAULT_INVENTORY_GLINT);
        changed |= addBooleanIfMissing("flameModuleVisual", DEFAULT_FLAME_MODULE_VISUAL);
        changed |= addBooleanIfMissing("lightningModuleVisual", DEFAULT_LIGHTNING_MODULE_VISUAL);
        changed |= addBooleanIfMissing("poisonModuleVisual", DEFAULT_POISON_MODULE_VISUAL);
        changed |= addBooleanIfMissing("explosionModuleVisual", DEFAULT_EXPLOSION_MODULE_VISUAL);
        changed |= addBooleanIfMissing("arrowRainModuleVisual", DEFAULT_ARROW_RAIN_MODULE_VISUAL);
        changed |= addBooleanIfMissing("hitImpactVisual", DEFAULT_HIT_IMPACT_VISUAL);
        changed |= addBooleanIfMissing("workbenchPreview", DEFAULT_WORKBENCH_PREVIEW);
        changed |= addStringIfMissing("glowBrightness", DEFAULT_GLOW_BRIGHTNESS.serializedName());
        return changed;
    }

    private static boolean addBooleanIfMissing(String key, boolean value) {
        if (document.has(key)) return false;
        document.addProperty(key, value);
        return true;
    }

    private static boolean addStringIfMissing(String key, String value) {
        if (document.has(key)) return false;
        document.addProperty(key, value);
        return true;
    }

    private static void saveDocument(Path path) throws IOException {
        Files.writeString(path, GSON.toJson(document) + System.lineSeparator(), StandardCharsets.UTF_8);
    }
}
