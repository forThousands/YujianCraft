package dev.yujiancraft.combat.combo;

import net.minecraft.world.phys.Vec3;

/** Authored discontinuous relocations. The server resolves collision and sends the final point. */
public enum ComboWarpProfile {
    NONE(0, 0.0F, false, false),
    RIGHT_SHIFT(7, 1.05F, false, false) {
        @Override public Vec3 desired(Vec3 anchor, Vec3 target, Vec3 forward, Vec3 right) {
            return anchor.add(right.scale(6.4D));
        }
    },
    RIGHT_SHIFT_FAST(4, 1.05F, false, false) {
        @Override public Vec3 desired(Vec3 anchor, Vec3 target, Vec3 forward, Vec3 right) {
            return anchor.add(right.scale(6.4D));
        }
    },
    FORWARD_SHIFT(8, 1.14F, false, false) {
        @Override public Vec3 desired(Vec3 anchor, Vec3 target, Vec3 forward, Vec3 right) {
            return anchor.add(forward.scale(5.6D));
        }
    },
    FORWARD_SHIFT_FAST(4, 1.14F, false, false) {
        @Override public Vec3 desired(Vec3 anchor, Vec3 target, Vec3 forward, Vec3 right) {
            return anchor.add(forward.scale(5.6D));
        }
    },
    MIRROR_ACROSS_TARGET(9, 1.38F, true, true) {
        @Override public Vec3 desired(Vec3 anchor, Vec3 target, Vec3 forward, Vec3 right) {
            Vec3 normal = new Vec3(forward.x, 0.0D, forward.z).normalize();
            double signedDistance = anchor.subtract(target).dot(normal);
            Vec3 reflected = anchor.subtract(normal.scale(2.0D * signedDistance));
            return new Vec3(reflected.x, anchor.y, reflected.z);
        }
    },
    MIRROR_ACROSS_TARGET_FAST(5, 1.38F, true, true) {
        @Override public Vec3 desired(Vec3 anchor, Vec3 target, Vec3 forward, Vec3 right) {
            Vec3 normal = new Vec3(forward.x, 0.0D, forward.z).normalize();
            double signedDistance = anchor.subtract(target).dot(normal);
            Vec3 reflected = anchor.subtract(normal.scale(2.0D * signedDistance));
            return new Vec3(reflected.x, anchor.y, reflected.z);
        }
    },
    DISTANT_REAR_APEX(9, 1.58F, true, false) {
        @Override public Vec3 desired(Vec3 anchor, Vec3 target, Vec3 forward, Vec3 right) {
            return anchor.subtract(forward.scale(11.5D)).add(0.0D, 7.2D, 0.0D);
        }
    },
    DISTANT_REAR_APEX_FAST(5, 1.58F, true, false) {
        @Override public Vec3 desired(Vec3 anchor, Vec3 target, Vec3 forward, Vec3 right) {
            return anchor.subtract(forward.scale(11.5D)).add(0.0D, 7.2D, 0.0D);
        }
    };

    private final int executeTick;
    private final float vfxStrength;
    private final boolean halo;
    private final boolean reverseFacing;

    ComboWarpProfile(int executeTick, float vfxStrength, boolean halo, boolean reverseFacing) {
        this.executeTick = executeTick;
        this.vfxStrength = vfxStrength;
        this.halo = halo;
        this.reverseFacing = reverseFacing;
    }

    public int executeTick() { return executeTick; }
    public float vfxStrength() { return vfxStrength; }
    public boolean halo() { return halo; }
    public boolean reverseFacing() { return reverseFacing; }
    public Vec3 desired(Vec3 anchor, Vec3 target, Vec3 forward, Vec3 right) { return anchor; }
}
