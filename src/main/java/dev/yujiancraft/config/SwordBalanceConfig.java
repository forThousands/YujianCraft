package dev.yujiancraft.config;

import dev.yujiancraft.material.FlyingSwordMaterial;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.EnumMap;
import java.util.Map;

public final class SwordBalanceConfig {
    public static final double MIN_DAMAGE = 0.5D;
    public static final double MAX_DAMAGE = 100.0D;
    public static final double MIN_SPEED = 0.25D;
    public static final double MAX_SPEED = 3.0D;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final EnumMap<FlyingSwordMaterial, ModConfigSpec.DoubleValue> DAMAGE =
            new EnumMap<>(FlyingSwordMaterial.class);
    private static final EnumMap<FlyingSwordMaterial, ModConfigSpec.DoubleValue> SPEED =
            new EnumMap<>(FlyingSwordMaterial.class);

    public static final ModConfigSpec SPEC;

    static {
        for (FlyingSwordMaterial material : FlyingSwordMaterial.values()) {
            BUILDER.push(material.serializedName());
            DAMAGE.put(material, BUILDER.comment("Damage dealt by one successful flying sword pass")
                    .defineInRange("damage", material.defaultDamage(), MIN_DAMAGE, MAX_DAMAGE));
            SPEED.put(material, BUILDER.comment("Multiplier applied to flying movement speed")
                    .defineInRange("flightSpeed", material.defaultFlightSpeed(), MIN_SPEED, MAX_SPEED));
            BUILDER.pop();
        }
        SPEC = BUILDER.build();
    }

    private SwordBalanceConfig() {
    }

    public static Balance get(FlyingSwordMaterial material) {
        return new Balance(DAMAGE.get(material).get(), SPEED.get(material).get());
    }

    public static Map<FlyingSwordMaterial, Balance> snapshot() {
        EnumMap<FlyingSwordMaterial, Balance> result = new EnumMap<>(FlyingSwordMaterial.class);
        for (FlyingSwordMaterial material : FlyingSwordMaterial.values()) result.put(material, get(material));
        return result;
    }

    public static Balance update(FlyingSwordMaterial material, double damage, double speed) {
        double safeDamage = Mth.clamp(damage, MIN_DAMAGE, MAX_DAMAGE);
        double safeSpeed = Mth.clamp(speed, MIN_SPEED, MAX_SPEED);
        DAMAGE.get(material).set(safeDamage);
        SPEED.get(material).set(safeSpeed);
        DAMAGE.get(material).save();
        return new Balance(safeDamage, safeSpeed);
    }

    public static Balance reset(FlyingSwordMaterial material) {
        return update(material, material.defaultDamage(), material.defaultFlightSpeed());
    }

    public record Balance(double damage, double flightSpeed) {
    }
}
