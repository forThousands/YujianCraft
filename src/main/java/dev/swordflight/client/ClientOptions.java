package dev.swordflight.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.swordflight.visual.FlyingSwordModelStyle;
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
    private static boolean flightSound;
    private static boolean swordTrail;
    private static boolean swordBodyGlow;
    private static boolean swordOutline;
    private static boolean inventoryGlint;
    private static FlyingSwordModelStyle swordModelStyle;

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
            flightSound = booleanValue("flightSound", true);
            swordTrail = booleanValue("swordTrail", true);
            swordBodyGlow = booleanValue("swordBodyGlow", true);
            swordOutline = booleanValue("swordOutline", false);
            inventoryGlint = booleanValue("inventoryGlint", true);
            swordModelStyle = FlyingSwordModelStyle.fromName(stringValue("swordModelStyle", "original"));
        } catch (Exception exception) {
            showDeveloperOptions = false;
            optimizedThirdPerson = false;
            flightSound = true;
            swordTrail = true;
            swordBodyGlow = true;
            swordOutline = false;
            inventoryGlint = true;
            swordModelStyle = FlyingSwordModelStyle.ORIGINAL;
            LOGGER.error("Could not load Swordflight client options from {}", path, exception);
        }
    }

    public static boolean showDeveloperOptions() {
        return showDeveloperOptions;
    }

    public static boolean optimizedThirdPerson() {
        return optimizedThirdPerson;
    }

    public static boolean flightSound() { return flightSound; }
    public static boolean swordTrail() { return swordTrail; }
    public static boolean swordBodyGlow() { return swordBodyGlow; }
    public static boolean swordOutline() { return swordOutline; }
    public static boolean inventoryGlint() { return inventoryGlint; }
    public static FlyingSwordModelStyle swordModelStyle() { return swordModelStyle; }

    public static synchronized void setOptimizedThirdPerson(boolean enabled) {
        optimizedThirdPerson = enabled;
        setBoolean("optimizedThirdPerson", enabled);
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

    public static synchronized void setSwordOutline(boolean enabled) {
        swordOutline = enabled;
        setBoolean("swordOutline", enabled);
    }

    public static synchronized void setInventoryGlint(boolean enabled) {
        inventoryGlint = enabled;
        setBoolean("inventoryGlint", enabled);
    }

    public static synchronized void setSwordModelStyle(FlyingSwordModelStyle style) {
        swordModelStyle = style;
        document.addProperty("swordModelStyle", style.serializedName());
        try {
            Files.createDirectories(path().getParent());
            saveDocument(path());
        } catch (IOException exception) {
            LOGGER.error("Could not save Swordflight client options to {}", path(), exception);
        }
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
        root.addProperty("schemaVersion", 5);
        root.addProperty("showDeveloperOptions", false);
        root.addProperty("optimizedThirdPerson", false);
        root.addProperty("flightSound", true);
        root.addProperty("swordTrail", true);
        root.addProperty("swordBodyGlow", true);
        root.addProperty("swordOutline", false);
        root.addProperty("inventoryGlint", true);
        root.addProperty("swordModelStyle", "original");
        root.addProperty("developerOptionsHint",
                "Set showDeveloperOptions to true and reopen the in-game config screen. OP permission is still required.");
        return root;
    }

    private static boolean migrateDocument() {
        boolean changed = false;
        int schemaVersion = document.has("schemaVersion") ? document.get("schemaVersion").getAsInt() : 0;
        if (schemaVersion < 5) {
            document.addProperty("schemaVersion", 5);
            // 0.7.4 returns to the vanilla silhouette by design. Migrate the 0.7.1-0.7.3
            // formal prototype default once; users can still select the archived experiment later.
            document.addProperty("swordModelStyle", "original");
            changed = true;
        }
        changed |= addBooleanIfMissing("showDeveloperOptions", false);
        changed |= addBooleanIfMissing("optimizedThirdPerson", false);
        changed |= addBooleanIfMissing("flightSound", true);
        changed |= addBooleanIfMissing("swordTrail", true);
        changed |= addBooleanIfMissing("swordBodyGlow", true);
        changed |= addBooleanIfMissing("swordOutline", false);
        changed |= addBooleanIfMissing("inventoryGlint", true);
        changed |= addStringIfMissing("swordModelStyle", "original");
        return changed;
    }

    private static boolean addBooleanIfMissing(String key, boolean value) {
        if (document.has(key)) return false;
        document.addProperty(key, value);
        return true;
    }

    private static String stringValue(String key, String fallback) {
        return document.has(key) && document.get(key).isJsonPrimitive()
                ? document.get(key).getAsString() : fallback;
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
