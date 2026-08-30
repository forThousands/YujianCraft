package dev.yujiancraft.client;

import net.neoforged.fml.ModList;

/** Lightweight client-side compatibility checks without optional compile-time dependencies. */
public final class ClientModCompatibility {
    private static final String EPIC_FIGHT_MOD_ID = "epicfight";
    private static final String SHOULDER_SURFING_MOD_ID = "shouldersurfing";

    private ClientModCompatibility() {
    }

    public static boolean isEpicFightLoaded() {
        return ModList.get().isLoaded(EPIC_FIGHT_MOD_ID);
    }

    public static boolean isShoulderSurfingLoaded() {
        return ModList.get().isLoaded(SHOULDER_SURFING_MOD_ID);
    }

    /** Shoulder Surfing owns the third-person camera whenever it is present. */
    public static boolean mayUseYujianThirdPersonCamera() {
        return !isShoulderSurfingLoaded();
    }
}
