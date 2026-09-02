package dev.yujiancraft.combat.combo;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Reusable six-sword motion scripts. Adding a set composes these instead of expanding a manager switch. */
public enum ComboChoreography {
    HOLD_FORMATION { @Override public Vec3 position(ComboMotionFrame f) { return hold(f); } },
    CROSS_LEFT { @Override public Vec3 position(ComboMotionFrame f) { return cross(f, true, 3.2D); } },
    CROSS_RIGHT { @Override public Vec3 position(ComboMotionFrame f) { return cross(f, false, 3.2D); } },
    CROSS_RIGHT_HIGH { @Override public Vec3 position(ComboMotionFrame f) {
        return alternateCross(f, true, true, 3.2D); } },
    CROSS_LEFT_LOW { @Override public Vec3 position(ComboMotionFrame f) {
        return alternateCross(f, false, false, 3.2D); } },
    STILL_RING { @Override public Vec3 position(ComboMotionFrame f) { return ring(f, 2.65D, 4.4D); } },
    STILL_SIX_RELEASE { @Override public Vec3 position(ComboMotionFrame f) { return sixRelease(f, false); } },
    GIANT_ARRAY { @Override public Vec3 position(ComboMotionFrame f) { return giantStation(f, 9.5D, 14.0D, 7.0D); } },
    BREAKER_SWEEP_LEFT { @Override public Vec3 position(ComboMotionFrame f) { return breakerSweep(f, true); } },
    BREAKER_SWEEP_RIGHT { @Override public Vec3 position(ComboMotionFrame f) { return breakerSweep(f, false); } },
    BREAKER_LUNGE_RING { @Override public Vec3 position(ComboMotionFrame f) { return breakerLunge(f); } },
    BREAKER_APEX_RELEASE { @Override public Vec3 position(ComboMotionFrame f) { return sixRelease(f, true); } },
    HEAVY_GIANT_ARRAY { @Override public Vec3 position(ComboMotionFrame f) { return giantStation(f, 11.2D, 16.5D, 6.0D); } },
    STAR_RING_SWEEP {
        @Override public Vec3 position(ComboMotionFrame f) {
            return StarRingMotion.position(StarRingMotion.Pattern.SWEEP, f);
        }
        @Override public Vec3 direction(ComboMotionFrame f) {
            return StarRingMotion.direction(StarRingMotion.Pattern.SWEEP, f);
        }
    },
    STAR_RING_TILTED_SWEEP {
        @Override public Vec3 position(ComboMotionFrame f) {
            return StarRingMotion.position(StarRingMotion.Pattern.TILTED_SWEEP, f);
        }
        @Override public Vec3 direction(ComboMotionFrame f) {
            return StarRingMotion.direction(StarRingMotion.Pattern.TILTED_SWEEP, f);
        }
    },
    STAR_RING_DUAL_ORBIT {
        @Override public Vec3 position(ComboMotionFrame f) {
            return StarRingMotion.position(StarRingMotion.Pattern.DUAL_ORBIT, f);
        }
        @Override public Vec3 direction(ComboMotionFrame f) {
            return StarRingMotion.direction(StarRingMotion.Pattern.DUAL_ORBIT, f);
        }
    },
    STAR_RING_PRISON {
        @Override public Vec3 position(ComboMotionFrame f) {
            return StarRingMotion.position(StarRingMotion.Pattern.PRISON, f);
        }
        @Override public Vec3 direction(ComboMotionFrame f) {
            return StarRingMotion.direction(StarRingMotion.Pattern.PRISON, f);
        }
    },
    STAR_RING_COLLAPSE {
        @Override public Vec3 position(ComboMotionFrame f) {
            return StarRingMotion.position(StarRingMotion.Pattern.COLLAPSE, f);
        }
        @Override public Vec3 direction(ComboMotionFrame f) {
            return StarRingMotion.direction(StarRingMotion.Pattern.COLLAPSE, f);
        }
    };

    public abstract Vec3 position(ComboMotionFrame frame);

    public Vec3 direction(ComboMotionFrame frame) {
        ComboMotionFrame next = new ComboMotionFrame(frame.owner(), frame.playerAnchor(), frame.target(),
                frame.forward(), frame.right(), frame.slot(), Math.min(frame.duration() - 0.001D,
                frame.tick() + 0.2D), frame.duration(), frame.worldTick() + 0.2D,
                frame.orbitPhase() + frame.orbitSpeed() * 0.2D, frame.orbitSpeed());
        Vec3 direction = position(next).subtract(position(frame));
        return direction.lengthSqr() < 1.0E-6D ? frame.forward() : direction.normalize();
    }

    private static Vec3 hold(ComboMotionFrame f) {
        return f.owner().add(0.0D, 1.25D + (f.slot() % 3) * 0.24D, 0.0D)
                .add(f.right().scale((f.slot() - 2.5D) * 0.36D)).subtract(f.forward().scale(0.7D));
    }

    private static Vec3 cross(ComboMotionFrame f, boolean leftToRight, double width) {
        boolean active = leftToRight ? f.slot() < 3 : f.slot() >= 3;
        if (!active) return hold(f);
        int lane = f.slot() % 3;
        double sign = leftToRight ? 1.0D : -1.0D;
        Vec3 start = f.target().add(f.right().scale(-sign * (width + lane * 0.34D)))
                .add(0.0D, leftToRight ? 3.0D + lane * 0.22D : -0.15D + lane * 0.16D, 0.0D)
                .subtract(f.forward().scale(0.7D));
        Vec3 end = f.target().add(f.right().scale(sign * (width + lane * 0.34D)))
                .add(0.0D, leftToRight ? -0.2D + lane * 0.12D : 3.0D + lane * 0.22D, 0.0D)
                .add(f.forward().scale(1.0D));
        return start.lerp(end, f.progress());
    }

    private static Vec3 alternateCross(ComboMotionFrame f, boolean startRight,
                                       boolean startHigh, double width) {
        boolean active = startRight ? f.slot() >= 3 : f.slot() < 3;
        if (!active) return hold(f);
        int lane = f.slot() % 3;
        double side = startRight ? 1.0D : -1.0D;
        double startY = startHigh ? 3.0D + lane * 0.22D : -0.2D + lane * 0.12D;
        double endY = startHigh ? -0.2D + lane * 0.12D : 3.0D + lane * 0.22D;
        Vec3 start = f.target().add(f.right().scale(side * (width + lane * 0.34D)))
                .add(0.0D, startY, 0.0D).subtract(f.forward().scale(0.7D));
        Vec3 end = f.target().subtract(f.right().scale(side * (width + lane * 0.34D)))
                .add(0.0D, endY, 0.0D).add(f.forward().scale(1.0D));
        return start.lerp(end, f.progress());
    }

    private static Vec3 ring(ComboMotionFrame f, double radius, double turns) {
        Vec3 centre = f.owner().add(f.forward().scale(1.35D)).add(0.0D, 1.0D, 0.0D);
        double angle = Math.PI * 2.0D * f.slot() / 6.0D + f.progress() * Math.PI * turns;
        Vec3 radial = f.right().scale(Math.cos(angle)).add(0.0D, Math.sin(angle), 0.0D);
        return centre.add(radial.scale(radius)).add(f.forward().scale(Math.sin(angle * 2.0D) * 0.3D));
    }

    private static Vec3 sixRelease(ComboMotionFrame f, boolean heavy) {
        double back = heavy ? 3.65D : 3.2D;
        double up = heavy ? 3.35D : 2.65D;
        Vec3 apex = f.playerAnchor().subtract(f.forward().scale(back)).add(0.0D, up, 0.0D);
        double angle = Math.PI * 2.0D * f.slot() / 6.0D + f.tick() * (heavy ? 0.18D : 0.12D);
        Vec3 ring = apex.add(f.right().scale(Math.cos(angle) * (heavy ? 2.7D : 2.25D)))
                .add(f.forward().scale(Math.sin(angle) * (heavy ? 1.35D : 1.15D)))
                .add(0.0D, Math.sin(angle) * (heavy ? 1.95D : 1.65D), 0.0D);
        double fireStart = heavy ? 8.0D : 6.0D;
        double fireDuration = heavy ? 5.0D : 5.0D;
        Vec3 end = f.target().add(f.forward().scale(heavy ? 2.7D : 2.2D))
                .add(f.right().scale((f.slot() - 2.5D) * 0.12D));
        return ring.lerp(end, ComboMotionMath.smooth(Mth.clamp((f.tick() - fireStart)
                / fireDuration, 0.0D, 1.0D)));
    }

    private static Vec3 giantStation(ComboMotionFrame f, double radius, double height, double gatherTicks) {
        double p = ComboMotionMath.smooth(Math.min(1.0D, f.tick() / gatherTicks));
        Vec3 centre = f.target().add(0.0D, height, 0.0D);
        double angle = Math.PI * 2.0D * f.slot() / 6.0D + f.tick() * 0.11D;
        Vec3 station = centre.add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
        Vec3 start = f.owner().add(0.0D, 1.2D, 0.0D)
                .add(f.right().scale((f.slot() - 2.5D) * 0.45D)).subtract(f.forward().scale(0.5D));
        return start.lerp(station, p);
    }

    private static Vec3 breakerSweep(ComboMotionFrame f, boolean leftToRight) {
        boolean active = leftToRight ? f.slot() < 3 : f.slot() >= 3;
        if (!active) {
            double angle = Math.PI * 2.0D * f.slot() / 6.0D + f.tick() * 0.2D;
            return f.owner().subtract(f.forward().scale(0.9D)).add(0.0D, 1.3D, 0.0D)
                    .add(f.right().scale(Math.cos(angle) * 1.35D)).add(0.0D, Math.sin(angle) * 0.9D, 0.0D);
        }
        int lane = f.slot() % 3;
        double sign = leftToRight ? 1.0D : -1.0D;
        Vec3 start = f.target().add(f.right().scale(-sign * (4.4D + lane * 0.42D)))
                .add(0.0D, leftToRight ? 3.8D + lane * 0.28D : -0.7D + lane * 0.18D, 0.0D)
                .subtract(f.forward().scale(1.15D));
        Vec3 control = f.target().add(0.0D, 1.25D + lane * 0.1D, 0.0D)
                .subtract(f.forward().scale(0.25D));
        Vec3 end = f.target().add(f.right().scale(sign * (4.0D + lane * 0.38D)))
                .add(0.0D, leftToRight ? -0.55D + lane * 0.16D : 3.65D + lane * 0.25D, 0.0D)
                .add(f.forward().scale(1.55D));
        double p = f.progress();
        return start.lerp(control, p).lerp(control.lerp(end, p), p);
    }

    private static Vec3 breakerLunge(ComboMotionFrame f) {
        double p = f.progress();
        double gather = ComboMotionMath.smooth(Mth.clamp(f.tick() / 4.0D, 0.0D, 1.0D));
        double cut = ComboMotionMath.smooth(Mth.clamp((f.tick() - 3.0D) / 10.0D, 0.0D, 1.0D));
        double base = Math.PI * 2.0D * f.slot() / 6.0D;
        Vec3 rear = f.owner().subtract(f.forward().scale(2.15D + Math.cos(base) * 0.45D))
                .add(f.right().scale(Math.cos(base) * 1.8D)).add(0.0D, 1.15D + Math.sin(base) * 1.35D, 0.0D);
        double angle = base + cut * Math.PI * 2.7D;
        Vec3 slash = f.target().subtract(f.forward().scale(0.25D))
                .add(f.right().scale(Math.cos(angle) * 3.5D)).add(0.0D, 1.1D + Math.sin(angle) * 2.55D, 0.0D)
                .add(f.forward().scale(Math.sin(angle * 2.0D) * 0.7D));
        return hold(f).lerp(rear, gather).lerp(slash, cut * (0.84D + 0.16D * p));
    }
}
