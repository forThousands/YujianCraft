package dev.swordflight.client;

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
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DIRECTORY_NAME = "swordflight";
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
            optimizedThirdPerson = booleanValue("optimizedThirdPerson", false);
            swordRidingEnabled = booleanValue("swordRidingEnabled", true);
            flightSound = booleanValue("flightSound", true);
            swordTrail = booleanValue("swordTrail", true);
            swordBodyGlow = booleanValue("swordBodyGlow", true);
            swordEnergyHighlight = booleanValue("swordEnergyHighlight", false);
            swordOutline = booleanValue("swordOutline", false);
            inventoryGlint = booleanValue("inventoryGlint", true);
        } catch (Exception exception) {
            showDeveloperOptions = false;
            optimizedThirdPerson = false;
            swordRidingEnabled = true;
            flightSound = true;
            swordTrail = true;
            swordBodyGlow = true;
            swordEnergyHighlight = false;
            swordOutline = false;
            inventoryGlint = true;
            LOGGER.error("Could not load Swordflight client options from {}", path, exception);
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

    private static void setBoolean(String key, boolean enabled) {
        document.addProperty(key, enabled);
        try {
            Files.createDirectories(path().getParent());
            saveDocument(path());
        } catch (IOException exception) {
            LOGGER.error("Could not save Swordflight client options to {}", path(), exception);
        }
    }

    public static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(DIRECTORY_NAME).resolve(FILE_NAME);
    }

    private static boolean booleanValue(String key, boolean fallback) {
        return document.has(key) && document.get(key).isJsonPrimitive()
                ? document.get(key).getAsBoolean() : fallback;
    }

    private static JsonObject defaultsDocument() {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 10);
        root.addProperty("showDeveloperOptions", false);
        root.addProperty("optimizedThirdPerson", false);
        root.addProperty("swordRidingEnabled", true);
        root.addProperty("flightSound", true);
        root.addProperty("swordTrail", true);
        root.addProperty("swordBodyGlow", true);
        root.addProperty("swordEnergyHighlight", false);
        root.addProperty("swordOutline", false);
        root.addProperty("inventoryGlint", true);
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
        changed |= addBooleanIfMissing("showDeveloperOptions", false);
        changed |= addBooleanIfMissing("optimizedThirdPerson", false);
        changed |= addBooleanIfMissing("swordRidingEnabled", true);
        changed |= addBooleanIfMissing("flightSound", true);
        changed |= addBooleanIfMissing("swordTrail", true);
        changed |= addBooleanIfMissing("swordBodyGlow", true);
        changed |= addBooleanIfMissing("swordEnergyHighlight", false);
        changed |= addBooleanIfMissing("swordOutline", false);
        changed |= addBooleanIfMissing("inventoryGlint", true);
        return changed;
    }

    private static boolean addBooleanIfMissing(String key, boolean value) {
        if (document.has(key)) return false;
        document.addProperty(key, value);
        return true;
    }

    private static void saveDocument(Path path) throws IOException {
        Files.writeString(path, GSON.toJson(document) + System.lineSeparator(), StandardCharsets.UTF_8);
    }
}
