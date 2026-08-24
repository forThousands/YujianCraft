package dev.yujiancraft.wanxiang;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Server-authoritative, lazily populated catalogue of items that completed spirit tempering. */
public final class WanxiangWeaponCatalog {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "yujiancraft-wanxiang-weapons.json";
    private static final double SAFE_NUMERIC_LIMIT = 1.0E9D;
    private static Catalogue catalogue = new Catalogue();
    private static Path loadedPath;

    private WanxiangWeaponCatalog() {
    }

    public static synchronized void load(MinecraftServer server) {
        Path path = path(server);
        loadedPath = path;
        try {
            Files.createDirectories(path.getParent());
            if (Files.notExists(path)) {
                catalogue = new Catalogue();
                save(server);
                return;
            }
            Catalogue loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Catalogue.class);
            catalogue = loaded == null ? new Catalogue() : loaded;
            catalogue.sanitize();
        } catch (IOException | JsonParseException exception) {
            LOGGER.error("Could not load Myriad Flying Sword catalogue from {}", path, exception);
            catalogue = new Catalogue();
        }
    }

    public static synchronized void unload() {
        catalogue = new Catalogue();
        loadedPath = null;
    }

    public static synchronized Entry register(MinecraftServer server, ItemStack stack) {
        ensureLoaded(server);
        String id = itemId(stack);
        Entry existing = catalogue.weapons.get(id);
        if (existing != null) return existing;
        Entry created = new Entry();
        catalogue.weapons.put(id, created);
        save(server);
        return created;
    }

    public static synchronized double damage(MinecraftServer server, ItemStack stack) {
        ensureLoaded(server);
        Entry entry = catalogue.weapons.get(itemId(stack));
        if (entry == null) entry = register(server, stack);
        if (!entry.enabled) return 0.0D;
        double base = entry.damageOverride == null
                ? WanxiangSwordData.pierceDamage(stack) : entry.damageOverride;
        return finiteNonNegative(base * finite(entry.damageMultiplier, 1.0D));
    }

    public static synchronized double flightSpeedMultiplier(MinecraftServer server, ItemStack stack) {
        ensureLoaded(server);
        Entry entry = catalogue.weapons.get(itemId(stack));
        if (entry == null) entry = register(server, stack);
        return entry.enabled ? Math.max(0.01D, finite(entry.flightSpeedMultiplier, 1.0D)) : 0.0D;
    }

    public static synchronized int durabilityCost(MinecraftServer server, ItemStack stack) {
        ensureLoaded(server);
        Entry entry = catalogue.weapons.get(itemId(stack));
        if (entry == null) entry = register(server, stack);
        return entry.enabled ? Math.max(0, entry.durabilityCost) : 0;
    }

    public static synchronized boolean enabled(MinecraftServer server, ItemStack stack) {
        ensureLoaded(server);
        Entry entry = catalogue.weapons.get(itemId(stack));
        return entry == null || entry.enabled;
    }

    public static synchronized void save(MinecraftServer server) {
        Path path = path(server);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(catalogue), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            LOGGER.error("Could not save Myriad Flying Sword catalogue to {}", path, exception);
        }
    }

    public static Path path(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve(FILE_NAME);
    }

    private static void ensureLoaded(MinecraftServer server) {
        Path current = path(server);
        if (loadedPath == null || !loadedPath.equals(current)) load(server);
    }

    private static String itemId(ItemStack stack) {
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? "minecraft:air" : id.toString();
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? Math.max(-SAFE_NUMERIC_LIMIT, Math.min(SAFE_NUMERIC_LIMIT, value)) : fallback;
    }

    private static double finiteNonNegative(double value) {
        return Math.max(0.0D, finite(value, 0.0D));
    }

    public static final class Catalogue {
        public int schemaVersion = 2;
        public Map<String, Entry> weapons = new LinkedHashMap<>();

        private void sanitize() {
            if (weapons == null) weapons = new LinkedHashMap<>();
            weapons.values().forEach(Entry::sanitize);
            schemaVersion = 2;
        }
    }

    public static final class Entry {
        public boolean enabled = true;
        public Double damageOverride;
        public double damageMultiplier = 1.0D;
        public double flightSpeedMultiplier = 1.0D;
        public int durabilityCost = 1;

        private void sanitize() {
            if (damageOverride != null) damageOverride = finiteNonNegative(damageOverride);
            damageMultiplier = finite(damageMultiplier, 1.0D);
            flightSpeedMultiplier = Math.max(0.01D, finite(flightSpeedMultiplier, 1.0D));
            durabilityCost = Math.max(0, durabilityCost);
        }
    }
}
