package dev.swordflight.client;

public final class ClientSwordRidingState {
    private static boolean active;

    private ClientSwordRidingState() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean value) {
        active = value;
    }
}
