package dev.yujiancraft.client;

import dev.yujiancraft.config.SwordBalanceConfig;
import dev.yujiancraft.config.EffectBalanceConfig;
import dev.yujiancraft.config.EffectParameter;
import dev.yujiancraft.material.FlyingSwordMaterial;
import dev.yujiancraft.network.ModNetwork;
import net.minecraft.client.Minecraft;

import java.util.EnumMap;
import java.util.Map;

public final class ClientBalanceState {
    private static final EnumMap<FlyingSwordMaterial, SwordBalanceConfig.Balance> BALANCES =
            new EnumMap<>(FlyingSwordMaterial.class);
    private static final EnumMap<EffectParameter, Double> EFFECT_VALUES = new EnumMap<>(EffectParameter.class);

    private ClientBalanceState() {
    }

    public static SwordBalanceConfig.Balance get(FlyingSwordMaterial material) {
        return BALANCES.getOrDefault(material,
                new SwordBalanceConfig.Balance(material.defaultDamage(), material.defaultFlightSpeed()));
    }

    public static void requestFromServer() {
        ModNetwork.sendToServer(new ModNetwork.RequestBalancePacket());
    }

    public static void update(FlyingSwordMaterial material, double damage, double speed) {
        double safeDamage = Math.max(SwordBalanceConfig.MIN_DAMAGE,
                Math.min(SwordBalanceConfig.MAX_DAMAGE, damage));
        double safeSpeed = Math.max(SwordBalanceConfig.MIN_SPEED,
                Math.min(SwordBalanceConfig.MAX_SPEED, speed));
        BALANCES.put(material, new SwordBalanceConfig.Balance(safeDamage, safeSpeed));
        ModNetwork.sendToServer(new ModNetwork.UpdateBalancePacket(
                material.ordinal(), safeDamage, safeSpeed, false));
    }

    public static void reset(FlyingSwordMaterial material) {
        ModNetwork.sendToServer(new ModNetwork.UpdateBalancePacket(material.ordinal(), 0.0D, 0.0D, true));
    }

    public static double get(EffectParameter parameter) {
        return EFFECT_VALUES.getOrDefault(parameter, parameter.defaultValue());
    }

    public static void update(EffectParameter parameter, double value) {
        double safe = Math.max(parameter.minimum(), Math.min(parameter.maximum(), value));
        if (parameter.integerDisplay()) safe = Math.rint(safe);
        EFFECT_VALUES.put(parameter, safe);
        ModNetwork.sendToServer(new ModNetwork.UpdateEffectBalancePacket(
                parameter.ordinal(), safe, false));
    }

    public static void reset(EffectParameter parameter) {
        ModNetwork.sendToServer(new ModNetwork.UpdateEffectBalancePacket(
                parameter.ordinal(), parameter.defaultValue(), true));
    }

    public static void acceptFromServer(Map<FlyingSwordMaterial, SwordBalanceConfig.Balance> balances,
                                        Map<EffectParameter, Double> effectValues) {
        BALANCES.clear();
        BALANCES.putAll(balances);
        EFFECT_VALUES.clear();
        EFFECT_VALUES.putAll(effectValues);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof AdminBalanceScreen screen) screen.onBalanceSynced();
    }
}
