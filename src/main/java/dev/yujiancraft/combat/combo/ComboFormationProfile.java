package dev.yujiancraft.combat.combo;

import net.minecraft.world.phys.Vec3;

/** Persistent six-sword pose used between authored attacks. */
public enum ComboFormationProfile {
    REAR_GUARD {
        @Override
        public Vec3 position(Vec3 owner, Vec3 forward, int slot, double phase, double worldTick) {
            Vec3 horizontal = ComboMotionMath.horizontal(forward, new Vec3(0.0D, 0.0D, 1.0D));
            Vec3 right = new Vec3(-horizontal.z, 0.0D, horizontal.x);
            double angle = Math.PI * 2.0D * slot / 6.0D;
            return owner.add(0.0D, 1.2D, 0.0D)
                    .add(right.scale(Math.cos(angle) * 1.75D))
                    .add(horizontal.scale(Math.sin(angle) * 0.72D - 0.75D))
                    .add(0.0D, Math.sin(angle) * 1.15D, 0.0D);
        }
    },
    STAR_RING {
        @Override
        public Vec3 position(Vec3 owner, Vec3 forward, int slot, double phase, double worldTick) {
            return StarRingMotion.idlePosition(owner, forward, slot, phase, worldTick);
        }

        @Override
        public Vec3 direction(Vec3 owner, Vec3 forward, int slot, double phase,
                              double angularSpeed, double worldTick) {
            return StarRingMotion.idleDirection(owner, forward, slot, phase, angularSpeed, worldTick);
        }
    };

    public abstract Vec3 position(Vec3 owner, Vec3 forward, int slot, double phase, double worldTick);

    public Vec3 direction(Vec3 owner, Vec3 forward, int slot, double phase,
                          double angularSpeed, double worldTick) {
        return ComboMotionMath.horizontal(forward, new Vec3(0.0D, 0.0D, 1.0D));
    }
}
