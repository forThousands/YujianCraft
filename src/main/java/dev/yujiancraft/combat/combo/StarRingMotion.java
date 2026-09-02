package dev.yujiancraft.combat.combo;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Deterministic orbit sampler shared by server collision and client rendering. */
public final class StarRingMotion {
    public static final double IDLE_RADIUS = 2.2D;
    public static final double BASE_ANGULAR_SPEED = Math.toRadians(18.0D);
    public static final double BOOST_ANGULAR_SPEED = Math.toRadians(40.0D);
    public static final int FINALE_RISE_TICKS = 8;
    public static final int FINALE_HOLD_END_TICK = 14;
    public static final int FINALE_IMPACT_TICK = 22;
    public static final double FINALE_LIFT = 4.5D;

    public enum Pattern {
        SWEEP,
        TILTED_SWEEP,
        DUAL_ORBIT,
        PRISON,
        COLLAPSE
    }

    private StarRingMotion() { }

    public static Vec3 idlePosition(Vec3 owner, Vec3 forward, int slot,
                                    double phase, double worldTick) {
        Vec3 centre = owner.add(0.0D, 1.08D, 0.0D);
        Basis basis = idleBasis(forward, worldTick);
        double angle = phase + Math.PI * 2.0D * slot / 6.0D;
        return centre.add(basis.u.scale(Math.cos(angle) * IDLE_RADIUS))
                .add(basis.v.scale(Math.sin(angle) * IDLE_RADIUS));
    }

    public static Vec3 idleDirection(Vec3 owner, Vec3 forward, int slot, double phase,
                                     double angularSpeed, double worldTick) {
        double epsilon = Math.copySign(0.025D, angularSpeed == 0.0D ? 1.0D : angularSpeed);
        Vec3 before = idlePosition(owner, forward, slot, phase - epsilon, worldTick);
        Vec3 after = idlePosition(owner, forward, slot, phase + epsilon, worldTick);
        return safeDirection(after.subtract(before), forward);
    }

    public static Vec3 position(Pattern pattern, ComboMotionFrame frame) {
        Vec3 playerCentre = frame.owner().add(0.0D, 1.08D, 0.0D);
        Vec3 targetDelta = frame.target().subtract(playerCentre);
        double targetDistance = Mth.clamp(targetDelta.length(), 0.75D, 14.0D);
        double progress = frame.progress();
        Vec3 centre = playerCentre;
        double radius = targetDistance;
        double planeOffset = 0.0D;
        double angle = frame.orbitPhase() + Math.PI * 2.0D * frame.slot() / 6.0D;

        if (pattern == Pattern.SWEEP) {
            radius = Mth.lerp(ComboMotionMath.smooth(frame.tick() / 4.0D),
                    IDLE_RADIUS, targetDistance);
        } else if (pattern == Pattern.TILTED_SWEEP) {
            planeOffset = Math.toRadians(42.0D) * Math.sin(Math.PI * progress);
        } else if (pattern == Pattern.DUAL_ORBIT) {
            double separation = Math.pow(Math.sin(Math.PI * progress), 2.0D);
            double sign = (frame.slot() & 1) == 0 ? 1.0D : -1.0D;
            planeOffset = sign * Math.toRadians(37.0D) * separation;
            angle += sign * Math.PI * 4.0D * progress;
        } else if (pattern == Pattern.PRISON) {
            double transfer = ComboMotionMath.smooth(Mth.clamp(frame.tick() / 8.0D, 0.0D, 1.0D));
            centre = playerCentre.lerp(frame.target(), transfer);
            radius = Mth.lerp(transfer, targetDistance, 3.4D);
        } else if (pattern == Pattern.COLLAPSE) {
            centre = finaleCentre(frame);
            double press = ComboMotionMath.smooth(Mth.clamp(
                    (frame.tick() - FINALE_HOLD_END_TICK)
                            / (double) (FINALE_IMPACT_TICK - FINALE_HOLD_END_TICK), 0.0D, 1.0D));
            radius = Mth.lerp(press, 3.4D, 3.02D);
            // The downward seal closes with one complete accelerating revolution. Ending on a
            // whole turn prevents a visible angular snap at impact.
            angle += Math.PI * 2.0D * press * press;
            Basis basis = finaleBasis(frame, Mth.clamp(frame.tick() / FINALE_RISE_TICKS, 0.0D, 1.0D));
            return centre.add(basis.u.scale(Math.cos(angle) * radius))
                    .add(basis.v.scale(Math.sin(angle) * radius));
        }

        Vec3 radialTarget = frame.target().subtract(centre);
        if (radialTarget.lengthSqr() < 1.0E-6D) radialTarget = frame.forward();
        Basis basis = attackBasis(frame.forward(), radialTarget, frame.worldTick(), planeOffset);
        return centre.add(basis.u.scale(Math.cos(angle) * radius))
                .add(basis.v.scale(Math.sin(angle) * radius));
    }

    public static Vec3 direction(Pattern pattern, ComboMotionFrame frame) {
        double travelSign = 1.0D;
        if (pattern == Pattern.DUAL_ORBIT) {
            double epsilon = 0.08D;
            double groupSign = (frame.slot() & 1) == 0 ? 1.0D : -1.0D;
            double now = frame.progress();
            double next = ComboMotionMath.smooth(Math.min(frame.duration() - 0.001D,
                    frame.tick() + epsilon) / Math.max(1.0D, frame.duration()));
            double delta = frame.orbitSpeed() * epsilon
                    + groupSign * Math.PI * 4.0D * (next - now);
            travelSign = delta < 0.0D ? -1.0D : 1.0D;
        }
        ComboMotionFrame beforeFrame = new ComboMotionFrame(frame.owner(), frame.playerAnchor(), frame.target(),
                frame.forward(), frame.right(), frame.slot(), frame.tick(), frame.duration(),
                frame.worldTick(), frame.orbitPhase() - travelSign * 0.025D,
                frame.orbitSpeed());
        ComboMotionFrame afterFrame = new ComboMotionFrame(frame.owner(), frame.playerAnchor(), frame.target(),
                frame.forward(), frame.right(), frame.slot(), frame.tick(), frame.duration(),
                frame.worldTick(), frame.orbitPhase() + travelSign * 0.025D,
                frame.orbitSpeed());
        return safeDirection(position(pattern, afterFrame).subtract(position(pattern, beforeFrame)),
                frame.forward());
    }

    private static Vec3 finaleCentre(ComboMotionFrame frame) {
        double lift;
        if (frame.tick() < FINALE_RISE_TICKS) {
            lift = Mth.lerp(ComboMotionMath.smooth(frame.tick() / FINALE_RISE_TICKS),
                    0.0D, FINALE_LIFT);
        } else if (frame.tick() < FINALE_HOLD_END_TICK) {
            lift = FINALE_LIFT;
        } else {
            double press = ComboMotionMath.smooth(Mth.clamp(
                    (frame.tick() - FINALE_HOLD_END_TICK)
                            / (double) (FINALE_IMPACT_TICK - FINALE_HOLD_END_TICK), 0.0D, 1.0D));
            lift = Mth.lerp(press, FINALE_LIFT, 0.15D);
        }
        return frame.target().add(0.0D, lift, 0.0D);
    }

    private static Basis finaleBasis(ComboMotionFrame frame, double flatten) {
        Basis inclined = attackBasis(frame.forward(), frame.forward(), frame.worldTick(), 0.0D);
        Vec3 forward = ComboMotionMath.horizontal(frame.forward(), new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x).normalize();
        Vec3 normal = inclined.normal.lerp(new Vec3(0.0D, 1.0D, 0.0D),
                ComboMotionMath.smooth(flatten)).normalize();
        Vec3 u = inclined.u.lerp(right, ComboMotionMath.smooth(flatten));
        u = safeDirection(u.subtract(normal.scale(u.dot(normal))), right);
        Vec3 v = normal.cross(u).normalize();
        return new Basis(u, v, normal);
    }

    private static Basis idleBasis(Vec3 forward, double worldTick) {
        Vec3 horizontal = ComboMotionMath.horizontal(forward, new Vec3(0.0D, 0.0D, 1.0D));
        double precession = worldTick * Math.toRadians(0.7D);
        Vec3 precessedForward = rotateAroundAxis(horizontal, new Vec3(0.0D, 1.0D, 0.0D), precession);
        Vec3 right = new Vec3(-precessedForward.z, 0.0D, precessedForward.x).normalize();
        double tilt = Math.toRadians(45.0D + 6.0D * Math.sin(worldTick * Math.PI * 2.0D / 60.0D));
        Vec3 normal = new Vec3(0.0D, Math.cos(tilt), 0.0D)
                .add(precessedForward.scale(Math.sin(tilt))).normalize();
        Vec3 v = normal.cross(right).normalize();
        return new Basis(right, v, normal);
    }

    private static Basis attackBasis(Vec3 forward, Vec3 radialTarget,
                                     double worldTick, double planeOffset) {
        Basis idle = idleBasis(forward, worldTick);
        Vec3 u = safeDirection(radialTarget, forward);
        Vec3 projectedNormal = idle.normal.subtract(u.scale(idle.normal.dot(u)));
        if (projectedNormal.lengthSqr() < 1.0E-6D) {
            projectedNormal = new Vec3(-u.z, 0.0D, u.x);
        }
        Vec3 normal = projectedNormal.normalize();
        if (Math.abs(planeOffset) > 1.0E-6D) {
            normal = rotateAroundAxis(normal, u, planeOffset).normalize();
        }
        Vec3 v = normal.cross(u).normalize();
        return new Basis(u, v, normal);
    }

    private static Vec3 rotateAroundAxis(Vec3 vector, Vec3 axis, double angle) {
        Vec3 n = safeDirection(axis, new Vec3(0.0D, 1.0D, 0.0D));
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return vector.scale(cos)
                .add(n.cross(vector).scale(sin))
                .add(n.scale(n.dot(vector) * (1.0D - cos)));
    }

    private static Vec3 safeDirection(Vec3 value, Vec3 fallback) {
        if (value.lengthSqr() >= 1.0E-8D) return value.normalize();
        return fallback.lengthSqr() >= 1.0E-8D ? fallback.normalize() : new Vec3(0.0D, 0.0D, 1.0D);
    }

    private record Basis(Vec3 u, Vec3 v, Vec3 normal) { }
}
