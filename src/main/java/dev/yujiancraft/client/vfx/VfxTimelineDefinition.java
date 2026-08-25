package dev.yujiancraft.client.vfx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.yujiancraft.YujianCraft;
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
 * Authored, resource-pack replaceable VFX timing data. Values are sampled in authored ticks;
 * {@link #mapRuntimeTick(float, int, int, int, int)} keeps each dramatic phase intact when a
 * server changes its configured phase lengths.
 */
public final class VfxTimelineDefinition {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation SWORD_ARRAY_FINISHER = ResourceLocation.fromNamespaceAndPath(
            YujianCraft.MOD_ID, "effects/sword_array/finisher.json");
    private static final VfxTimelineDefinition FALLBACK = fallback();

    private final float[] phaseEnds;
    private final Map<String, Curve> curves;

    private VfxTimelineDefinition(float[] phaseEnds, Map<String, Curve> curves) {
        this.phaseEnds = phaseEnds;
        this.curves = curves;
    }

    public static VfxTimelineDefinition loadSwordArrayFinisher(ResourceManager resources) {
        try (Reader reader = resources.getResourceOrThrow(SWORD_ARRAY_FINISHER).openAsReader()) {
            return parse(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception exception) {
            LOGGER.error("Unable to load {}; using the built-in safe timeline", SWORD_ARRAY_FINISHER,
                    exception);
            return FALLBACK;
        }
    }

    public float sample(String name, float authoredTick) {
        Curve curve = curves.get(name);
        return curve == null ? 0.0F : curve.sample(authoredTick);
    }

    public float durationTicks() {
        return phaseEnds[3];
    }

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
            if (runtimeTick <= runtimeEnds[phase] || phase == runtimeEnds.length - 1) {
                float phaseProgress = clamp01((runtimeTick - runtimeStart)
                        / Math.max(0.0001F, runtimeEnds[phase] - runtimeStart));
                return lerp(authoredStart, phaseEnds[phase], phaseProgress);
            }
            runtimeStart = runtimeEnds[phase];
            authoredStart = phaseEnds[phase];
        }
        return phaseEnds[3];
    }

    private static VfxTimelineDefinition parse(JsonObject root) {
        int schemaVersion = root.get("schemaVersion").getAsInt();
        if (schemaVersion != 1) throw new IllegalArgumentException("Unsupported schemaVersion " + schemaVersion);
        JsonObject phases = root.getAsJsonObject("phases");
        float charge = positive(phases, "charge");
        float hold = positive(phases, "hold");
        float expand = positive(phases, "expand");
        float sustain = positive(phases, "sustain");
        float[] phaseEnds = {charge, charge + hold, charge + hold + expand,
                charge + hold + expand + sustain};

        Map<String, Curve> curves = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("curves").entrySet()) {
            JsonArray array = entry.getValue().getAsJsonArray();
            List<Keyframe> keys = new ArrayList<>();
            for (JsonElement element : array) {
                JsonObject key = element.getAsJsonObject();
                float tick = key.get("tick").getAsFloat();
                float value = key.get("value").getAsFloat();
                Easing easing = key.has("easing")
                        ? Easing.fromName(key.get("easing").getAsString()) : Easing.LINEAR;
                keys.add(new Keyframe(tick, value, easing));
            }
            keys.sort(Comparator.comparingDouble(Keyframe::tick));
            if (keys.size() < 2) throw new IllegalArgumentException(
                    "Curve " + entry.getKey() + " requires at least two keyframes");
            curves.put(entry.getKey(), new Curve(List.copyOf(keys)));
        }
        for (String required : List.of("charge", "dark", "expansion", "white", "ink",
                "recovery", "distortion", "chroma")) {
            if (!curves.containsKey(required)) {
                throw new IllegalArgumentException("Missing required curve " + required);
            }
        }
        return new VfxTimelineDefinition(phaseEnds, Map.copyOf(curves));
    }

    private static float positive(JsonObject object, String name) {
        float value = object.get(name).getAsFloat();
        if (value <= 0.0F) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private static VfxTimelineDefinition fallback() {
        String json = """
                {"schemaVersion":1,"phases":{"charge":10,"hold":8,"expand":7,"sustain":32},
                "curves":{
                "charge":[{"tick":0,"value":0},{"tick":10,"value":1,"easing":"smoothstep"}],
                "dark":[{"tick":0,"value":0},{"tick":18,"value":0},{"tick":19.2,"value":1,"easing":"smoothstep"},{"tick":46,"value":1},{"tick":57,"value":0,"easing":"smoothstep"}],
                "expansion":[{"tick":0,"value":0},{"tick":18,"value":0},{"tick":19,"value":0.12},{"tick":22,"value":0.30,"easing":"smoothstep"},{"tick":25.5,"value":0.85,"easing":"easeIn"},{"tick":29,"value":1,"easing":"smoothstep"}],
                "white":[{"tick":0,"value":0},{"tick":25,"value":0},{"tick":27.3,"value":1,"easing":"easeIn"},{"tick":29,"value":1},{"tick":31,"value":0,"easing":"smoothstep"}],
                "ink":[{"tick":0,"value":0},{"tick":29,"value":0},{"tick":31,"value":1,"easing":"smoothstep"},{"tick":43,"value":1},{"tick":50,"value":0,"easing":"smoothstep"}],
                "recovery":[{"tick":0,"value":0},{"tick":42,"value":0},{"tick":57,"value":1,"easing":"smoothstep"}],
                "distortion":[{"tick":0,"value":0},{"tick":18,"value":0},{"tick":19,"value":0.72,"easing":"easeOut"},{"tick":22,"value":0.18,"easing":"smoothstep"},{"tick":25,"value":0.95,"easing":"easeInOut"},{"tick":31,"value":0.28,"easing":"smoothstep"},{"tick":50,"value":0}],
                "chroma":[{"tick":0,"value":0},{"tick":18,"value":0},{"tick":19.5,"value":0.86,"easing":"easeOut"},{"tick":26,"value":0.18,"easing":"smoothstep"},{"tick":44,"value":0.08},{"tick":57,"value":0}]
                }}
                """;
        return parse(JsonParser.parseString(json).getAsJsonObject());
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    private record Curve(List<Keyframe> keys) {
        float sample(float tick) {
            if (tick <= keys.get(0).tick) return keys.get(0).value;
            for (int index = 1; index < keys.size(); index++) {
                Keyframe right = keys.get(index);
                if (tick <= right.tick) {
                    Keyframe left = keys.get(index - 1);
                    float position = clamp01((tick - left.tick) / Math.max(0.0001F,
                            right.tick - left.tick));
                    return lerp(left.value, right.value, right.easing.apply(position));
                }
            }
            return keys.get(keys.size() - 1).value;
        }
    }

    private record Keyframe(float tick, float value, Easing easing) {
    }

    private enum Easing {
        LINEAR,
        HOLD,
        SMOOTHSTEP,
        EASE_IN,
        EASE_OUT,
        EASE_IN_OUT;

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
