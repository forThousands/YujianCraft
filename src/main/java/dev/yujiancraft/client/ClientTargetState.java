package dev.yujiancraft.client;

import java.util.UUID;

public final class ClientTargetState {
    private static UUID lockedTargetId;

    private ClientTargetState() {
    }

    public static UUID getLockedTargetId() {
        return lockedTargetId;
    }

    public static void setLockedTargetId(UUID id) {
        lockedTargetId = id;
    }
}
