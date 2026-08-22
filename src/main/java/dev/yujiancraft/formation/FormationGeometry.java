package dev.yujiancraft.formation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** All editable formation geometry and return-lane waypoints live here. */
public final class FormationGeometry {
    private static final int FORMATION_SIZE = 6;
    private static final double RING_CENTER_HEIGHT = 1.32D;
    private static final double RING_VERTICAL_RADIUS = 1.28D;
    private static final double RING_HORIZONTAL_RADIUS = 1.70D;
    private static final double RING_BACK_OFFSET = 0.82D;
    private static final double RING_ANGLE_OFFSET = -Math.PI / 2.0D;

    private FormationGeometry() {
    }

    public static Vec3 dockPosition(ServerPlayer owner, int slot, FormationMode mode, int tickCount) {
        return dockPosition(owner.position(), owner.getYRot(), slot, mode);
    }

    public static Vec3 dockPosition(Vec3 ownerPosition, float ownerYaw, int slot, FormationMode mode) {
        Basis basis = basis(ownerYaw);
        if (mode.usesRingGeometry()) {
            // Preserve the original six-slot layout and centre. Only the horizontal and vertical
            // radii are enlarged, so the formation keeps its established visual rhythm.
            double angle = Math.PI * 2.0D * slot / FORMATION_SIZE + RING_ANGLE_OFFSET;
            return ownerPosition
                    .add(0.0D, RING_CENTER_HEIGHT + Math.sin(angle) * RING_VERTICAL_RADIUS, 0.0D)
                    .add(basis.forward.scale(-RING_BACK_OFFSET))
                    .add(basis.right.scale(Math.cos(angle) * RING_HORIZONTAL_RADIUS));
        }

        double horizontal = (slot - 2.5D) * 0.56D;
        double vertical = 1.08D + Math.abs(slot - 2.5D) * 0.18D;
        return ownerPosition
                .add(0.0D, vertical, 0.0D)
                .add(basis.forward.scale(-1.28D))
                .add(basis.right.scale(horizontal));
    }

    public static Vec3 dockDirection(ServerPlayer owner, Vec3 dockPosition, FormationMode mode) {
        return dockDirection(owner.position(), owner.getYRot(), dockPosition, mode);
    }

    public static Vec3 dockDirection(Vec3 ownerPosition, float ownerYaw, Vec3 dockPosition, FormationMode mode) {
        Basis basis = basis(ownerYaw);
        if (mode.usesRingGeometry()) {
            return basis.forward;
        }
        Vec3 shoulderCenter = ownerPosition.add(0.0D, 1.15D, 0.0D);
        Vec3 radial = dockPosition.subtract(shoulderCenter);
        return radial.lengthSqr() < 1.0E-6D ? basis.forward.scale(-1.0D) : radial.normalize();
    }

    public static Vec3 launchClearPoint(ServerPlayer owner, int slot, int tickCount) {
        Vec3 dock = dockPosition(owner, slot, FormationMode.FAN, tickCount);
        return dock.add(dockDirection(owner, dock, FormationMode.FAN).scale(1.45D));
    }

    public static Vec3 risePoint(Vec3 clearPoint, Vec3 launchDirection) {
        return clearPoint.add(launchDirection.scale(0.3D)).add(0.0D, 1.45D, 0.0D);
    }

    /** A high, behind-player corridor prevents returning swords from cutting through the owner. */
    public static Vec3 returnRallyPoint(ServerPlayer owner, int slot) {
        Basis basis = basis(owner);
        return owner.position()
                .add(0.0D, 3.05D + (slot % 2) * 0.12D, 0.0D)
                .add(basis.forward.scale(-2.0D))
                .add(basis.right.scale((slot - 2.5D) * 0.16D));
    }

    public static Vec3 returnApproachPoint(ServerPlayer owner, int slot, FormationMode mode, int tickCount) {
        Vec3 dock = dockPosition(owner, slot, mode, tickCount);
        Vec3 outward = dockDirection(owner, dock, mode);
        if (mode.usesRingGeometry()) {
            Vec3 center = owner.position().add(0.0D, RING_CENTER_HEIGHT, 0.0D)
                    .add(basis(owner).forward.scale(-RING_BACK_OFFSET));
            outward = dock.subtract(center).normalize();
        }
        return dock.add(outward.scale(0.82D)).add(0.0D, 0.28D, 0.0D);
    }

    private static Basis basis(ServerPlayer owner) {
        return basis(owner.getYRot());
    }

    private static Basis basis(float yaw) {
        Vec3 forward = Vec3.directionFromRotation(0.0F, yaw).normalize();
        return new Basis(forward, new Vec3(-forward.z, 0.0D, forward.x));
    }

    private record Basis(Vec3 forward, Vec3 right) {
    }
}
