package dev.swordflight.client;

public final class ClientManualGuidanceState {
    private static boolean guiding;

    private ClientManualGuidanceState() {
    }

    public static boolean isGuiding() {
        return guiding;
    }

    public static void setGuiding(boolean value) {
        guiding = value;
    }
}
