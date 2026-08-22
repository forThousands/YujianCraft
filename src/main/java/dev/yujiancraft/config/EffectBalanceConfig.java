package dev.yujiancraft.config;

import net.minecraft.util.Mth;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.EnumMap;
import java.util.Map;

public final class EffectBalanceConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final EnumMap<EffectParameter, ForgeConfigSpec.DoubleValue> VALUES =
            new EnumMap<>(EffectParameter.class);
    public static final ForgeConfigSpec SPEC;

    static {
        for (EffectConfigGroup group : EffectConfigGroup.values()) {
            BUILDER.push(group.serializedName());
            for (EffectParameter parameter : EffectParameter.forGroup(group)) {
                VALUES.put(parameter, BUILDER.defineInRange(parameter.serializedName(), parameter.defaultValue(),
                        parameter.minimum(), parameter.maximum()));
            }
            BUILDER.pop();
        }
        SPEC = BUILDER.build();
    }

    private EffectBalanceConfig() {
    }

    public static double get(EffectParameter parameter) {
        return VALUES.get(parameter).get();
    }

    public static int getInt(EffectParameter parameter) {
        return Mth.floor(get(parameter));
    }

    public static Map<EffectParameter, Double> snapshot() {
        EnumMap<EffectParameter, Double> result = new EnumMap<>(EffectParameter.class);
        for (EffectParameter parameter : EffectParameter.values()) result.put(parameter, get(parameter));
        return result;
    }

    /** Mirrors authoritative server values on a client without writing them to disk. */
    public static void acceptRemoteSnapshot(Map<EffectParameter, Double> values) {
        for (EffectParameter parameter : EffectParameter.values()) {
            Double value = values.get(parameter);
            if (value == null) continue;
            double safe = Mth.clamp(value, parameter.minimum(), parameter.maximum());
            if (parameter.integerDisplay()) safe = Math.rint(safe);
            VALUES.get(parameter).set(safe);
        }
    }

    public static double update(EffectParameter parameter, double value) {
        double safe = Mth.clamp(value, parameter.minimum(), parameter.maximum());
        if (parameter.integerDisplay()) safe = Math.rint(safe);
        VALUES.get(parameter).set(safe);
        VALUES.get(parameter).save();
        return safe;
    }

    public static double reset(EffectParameter parameter) {
        return update(parameter, parameter.defaultValue());
    }
}
