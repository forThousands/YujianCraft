package dev.yujiancraft.client.vfx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.visual.SwordArrayVisualStyle;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resource-pack replaceable VFX project exported by the standalone Yujian Craft VFX Studio.
 * Only the stable schema is consumed at runtime; editor labels and layout metadata are ignored.
 */
public final class VfxTimelineDefinition {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation SWORD_ARRAY_FINISHER = ResourceLocation.fromNamespaceAndPath(
            YujianCraft.MOD_ID, "effects/sword_array/finisher.vfx.json");
    private static final VfxTimelineDefinition FALLBACK = fallback();

    private final float durationTicks;
    private final float[] phaseEnds;
    private final Map<String, Curve> tracks;
    private final Map<String, Module> modules;
    private final Map<String, Boolean> parameterEnabled;
    private final SwordArrayVisualStyle worldStyle;

    private VfxTimelineDefinition(float durationTicks, float[] phaseEnds,
                                  Map<String, Curve> tracks, Map<String, Module> modules,
                                  Map<String, Boolean> parameterEnabled, SwordArrayVisualStyle worldStyle) {
        this.durationTicks = durationTicks;
        this.phaseEnds = phaseEnds;
        this.tracks = tracks;
        this.modules = modules;
        this.parameterEnabled = parameterEnabled;
        this.worldStyle = worldStyle;
    }

    public static VfxTimelineDefinition loadSwordArrayFinisher(ResourceManager resources) {
        try (Reader reader = resources.getResourceOrThrow(SWORD_ARRAY_FINISHER).openAsReader()) {
            return parse(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception exception) {
            LOGGER.error("Unable to load {}; using the built-in safe VFX project",
                    SWORD_ARRAY_FINISHER, exception);
            return FALLBACK;
        }
    }

    /** Used by the opt-in local studio bridge; never accepts data from a game server. */
    public static VfxTimelineDefinition parseProject(JsonObject root) {
        return parse(root);
    }

    public float sample(String track, float authoredTick, float fallback) {
        if (Boolean.FALSE.equals(parameterEnabled.get(track))) return fallback;
        Curve curve = tracks.get(track);
        return curve == null ? fallback : curve.sample(authoredTick);
    }

    public boolean moduleEnabled(String id) {
        Module module = modules.get(id);
        return module == null || module.enabled;
    }

    public String moduleAnchor(String id) {
        Module module = modules.get(id);
        return module == null ? "screen" : module.anchor;
    }

    public Center moduleCenter(String id, float fallbackX, float fallbackY) {
        Module module = modules.get(id);
        return module == null || module.center == null ? new Center(fallbackX, fallbackY) : module.center;
    }

    public float durationTicks() {
        return durationTicks;
    }

    public SwordArrayVisualStyle worldStyle() { return worldStyle; }

    public String moduleSetting(String id, String setting, String fallback) {
        Module module = modules.get(id);
        return module == null ? fallback : module.settings.getOrDefault(setting, fallback);
    }

    /** Maps server-configurable phase lengths onto the authored four-phase composition. */
    public float mapRuntimeTick(float runtimeTick, int chargeTicks, int holdTicks,
                                int expandTicks, int sustainTicks) {
        float[] runtimeEnds = {
                Math.max(1, chargeTicks),
                Math.max(1, chargeTicks) + Math.max(1, holdTicks),
                Math.max(1, chargeTicks) + Math.max(1, holdTicks) + Math.max(1, expandTicks),
                Math.max(1, chargeTicks) + Math.max(1, holdTicks) + Math.max(1, expandTicks)
                        + Math.max(1, sustainTicks)
        };
        float runtimeStart = 0.0F;
        float authoredStart = 0.0F;
        for (int phase = 0; phase < runtimeEnds.length; phase++) {
            float authoredEnd = phaseEnds[Math.min(phase, phaseEnds.length - 1)];
            if (runtimeTick <= runtimeEnds[phase] || phase == runtimeEnds.length - 1) {
                float progress = clamp01((runtimeTick - runtimeStart)
                        / Math.max(0.0001F, runtimeEnds[phase] - runtimeStart));
                return lerp(authoredStart, authoredEnd, progress);
            }
            runtimeStart = runtimeEnds[phase];
            authoredStart = authoredEnd;
        }
        return durationTicks;
    }

    private static VfxTimelineDefinition parse(JsonObject root) {
        int schemaVersion = root.get("schemaVersion").getAsInt();
        if (schemaVersion != 2) throw new IllegalArgumentException(
                "Unsupported VFX schemaVersion " + schemaVersion);
        float duration = positive(root, "durationTicks");
        JsonArray phaseArray = root.getAsJsonArray("phases");
        if (phaseArray == null || phaseArray.size() < 4) {
            throw new IllegalArgumentException("At least four authored phases are required");
        }
        float[] phaseEnds = new float[phaseArray.size()];
        float previousEnd = 0.0F;
        for (int index = 0; index < phaseArray.size(); index++) {
            JsonObject phase = phaseArray.get(index).getAsJsonObject();
            float start = phase.get("startTick").getAsFloat();
            float end = phase.get("endTick").getAsFloat();
            if (end <= start || start + 0.001F < previousEnd) {
                throw new IllegalArgumentException("Invalid or overlapping phase at index " + index);
            }
            phaseEnds[index] = end;
            previousEnd = end;
        }
        if (Math.abs(previousEnd - duration) > 0.01F) {
            throw new IllegalArgumentException("Last phase must end at durationTicks");
        }

        Map<String, Module> modules = new HashMap<>();
        Map<String, Boolean> parameterEnabled = new HashMap<>();
        JsonObject moduleRoot = root.getAsJsonObject("modules");
        if (moduleRoot != null) {
            for (Map.Entry<String, JsonElement> entry : moduleRoot.entrySet()) {
                JsonObject source = entry.getValue().getAsJsonObject();
                boolean enabled = !source.has("enabled") || source.get("enabled").getAsBoolean();
                String anchor = source.has("anchor") ? source.get("anchor").getAsString() : "screen";
                Center center = null;
                if (source.has("center")) {
                    JsonArray values = source.getAsJsonArray("center");
                    if (values.size() == 2) {
                        center = new Center(values.get(0).getAsFloat(), values.get(1).getAsFloat());
                    }
                }
                Map<String, String> settings = new HashMap<>();
                JsonObject settingRoot = source.getAsJsonObject("settings");
                if (settingRoot != null) settingRoot.entrySet().forEach(setting ->
                        settings.put(setting.getKey(), setting.getValue().getAsString()));
                JsonObject parameters = source.getAsJsonObject("parameters");
                if (parameters != null) for (Map.Entry<String, JsonElement> parameter : parameters.entrySet()) {
                    JsonObject definition = parameter.getValue().getAsJsonObject();
                    if (!definition.has("track")) continue;
                    boolean required = definition.has("required") && definition.get("required").getAsBoolean();
                    boolean parameterOn = required || !definition.has("enabled") || definition.get("enabled").getAsBoolean();
                    parameterEnabled.put(definition.get("track").getAsString(), parameterOn);
                }
                modules.put(entry.getKey(), new Module(enabled, anchor, center, Map.copyOf(settings)));
            }
        }

        Map<String, Curve> tracks = new HashMap<>();
        JsonObject trackRoot = root.getAsJsonObject("tracks");
        if (trackRoot == null) throw new IllegalArgumentException("Missing tracks object");
        for (Map.Entry<String, JsonElement> entry : trackRoot.entrySet()) {
            List<Keyframe> keys = new ArrayList<>();
            for (JsonElement element : entry.getValue().getAsJsonArray()) {
                JsonObject key = element.getAsJsonObject();
                Easing easing = key.has("easing")
                        ? Easing.fromName(key.get("easing").getAsString()) : Easing.LINEAR;
                keys.add(new Keyframe(key.get("tick").getAsFloat(),
                        key.get("value").getAsFloat(), easing));
            }
            keys.sort(Comparator.comparingDouble(Keyframe::tick));
            if (keys.isEmpty()) throw new IllegalArgumentException("Track " + entry.getKey() + " is empty");
            tracks.put(entry.getKey(), new Curve(List.copyOf(keys)));
        }
        for (String required : List.of("world.charge", "world.beamExpansion")) {
            if (!tracks.containsKey(required)) throw new IllegalArgumentException("Missing required track " + required);
        }
        SwordArrayVisualStyle worldStyle = SwordArrayVisualStyle.parse(root.getAsJsonObject("worldStyle"));
        return new VfxTimelineDefinition(duration, phaseEnds, Map.copyOf(tracks), Map.copyOf(modules),
                Map.copyOf(parameterEnabled), worldStyle);
    }

    private static float positive(JsonObject object, String name) {
        float value = object.get(name).getAsFloat();
        if (value <= 0.0F) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private static VfxTimelineDefinition fallback() {
        String json = """
                {"schemaVersion":2,"durationTicks":57,
                "phases":[
                  {"startTick":0,"endTick":10},{"startTick":10,"endTick":18},
                  {"startTick":18,"endTick":25},{"startTick":25,"endTick":57}],
                "modules":{},"tracks":{
                  "world.charge":[{"tick":0,"value":0},{"tick":10,"value":1,"easing":"smoothstep"}],
                  "world.beamExpansion":[{"tick":0,"value":0},{"tick":18,"value":0},{"tick":29,"value":1,"easing":"easeIn"}],
                  "post.distortion.strength":[{"tick":0,"value":0},{"tick":19,"value":0.7},{"tick":50,"value":0}],
                  "post.threshold.amount":[{"tick":0,"value":0},{"tick":20,"value":1},{"tick":53,"value":0}],
                  "post.threshold.level":[{"tick":0,"value":0.55},{"tick":57,"value":0.55}],
                  "post.threshold.softness":[{"tick":0,"value":0.04},{"tick":57,"value":0.04}],
                  "post.color.contrast":[{"tick":0,"value":1},{"tick":20,"value":1.7},{"tick":57,"value":1}],
                  "post.color.saturation":[{"tick":0,"value":1},{"tick":20,"value":0},{"tick":44,"value":0},{"tick":57,"value":1}]
                }}
                """;
        return parse(JsonParser.parseString(json).getAsJsonObject());
    }

    private static float clamp01(float value) { return Math.max(0.0F, Math.min(1.0F, value)); }
    private static float lerp(float from, float to, float amount) { return from + (to - from) * amount; }

    public record Center(float x, float y) { }
    private record Module(boolean enabled, String anchor, Center center, Map<String, String> settings) { }
    private record Keyframe(float tick, float value, Easing easing) { }

    private record Curve(List<Keyframe> keys) {
        float sample(float tick) {
            if (tick <= keys.get(0).tick) return keys.get(0).value;
            for (int index = 1; index < keys.size(); index++) {
                Keyframe right = keys.get(index);
                if (tick <= right.tick) {
                    Keyframe left = keys.get(index - 1);
                    float position = clamp01((tick - left.tick)
                            / Math.max(0.0001F, right.tick - left.tick));
                    return lerp(left.value, right.value, right.easing.apply(position));
                }
            }
            return keys.get(keys.size() - 1).value;
        }
    }

    private enum Easing {
        LINEAR, HOLD, SMOOTHSTEP, EASE_IN, EASE_OUT, EASE_IN_OUT;

        static Easing fromName(String name) {
            return switch (name) {
                case "linear" -> LINEAR;
                case "hold" -> HOLD;
                case "smoothstep" -> SMOOTHSTEP;
                case "easeIn" -> EASE_IN;
                case "easeOut" -> EASE_OUT;
                case "easeInOut" -> EASE_IN_OUT;
                default -> throw new IllegalArgumentException("Unknown easing " + name);
            };
        }

        float apply(float value) {
            return switch (this) {
                case LINEAR -> value;
                case HOLD -> value >= 1.0F ? 1.0F : 0.0F;
                case SMOOTHSTEP -> value * value * (3.0F - 2.0F * value);
                case EASE_IN -> value * value;
                case EASE_OUT -> 1.0F - (1.0F - value) * (1.0F - value);
                case EASE_IN_OUT -> value < 0.5F ? 2.0F * value * value
                        : 1.0F - (float) Math.pow(-2.0F * value + 2.0F, 2.0D) / 2.0F;
            };
        }
    }
}
