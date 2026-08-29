package dev.yujiancraft.combat.combo;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Absolute authored offsets. Collision is applied by the caller on both logical sides. */
public enum ComboRootMotion {
    NONE {
        @Override public Vec3 destination(ComboMotionFrame frame) { return frame.playerAnchor(); }
    },
    SIDE_LEFT {
        @Override public Vec3 destination(ComboMotionFrame frame) {
            double p = frame.progress();
            return frame.playerAnchor().add(frame.forward().scale(1.05D * p))
                    .subtract(frame.right().scale(1.75D * p));
        }
    },
    SIDE_RIGHT {
        @Override public Vec3 destination(ComboMotionFrame frame) {
            double p = frame.progress();
            return frame.playerAnchor().add(frame.forward().scale(0.75D * p))
                    .add(frame.right().scale(2.85D * p));
        }
    },
    FORWARD_LUNGE {
        @Override public Vec3 destination(ComboMotionFrame frame) {
            double distance = Mth.clamp(frame.target().subtract(frame.playerAnchor())
                    .horizontalDistance() - 1.55D, 2.2D, 6.4D);
            double p = ComboMotionMath.smooth(Mth.clamp(frame.tick() / 10.0D, 0.0D, 1.0D));
            return frame.playerAnchor().add(frame.forward().scale(distance * p));
        }
    },
    BACKWARD_APEX {
        @Override public Vec3 destination(ComboMotionFrame frame) {
            double tick = frame.tick();
            Vec3 apex = frame.playerAnchor().subtract(frame.forward().scale(3.65D)).add(0.0D, 3.35D, 0.0D);
            if (tick <= 5.0D) return frame.playerAnchor().lerp(apex,
                    ComboMotionMath.smooth(tick / 5.0D));
            if (tick <= 14.0D) return apex;
            return apex.lerp(frame.playerAnchor(), ComboMotionMath.smooth((tick - 14.0D)
                    / Math.max(1.0D, frame.duration() - 14.0D)));
        }
    },
    SIDE_LEFT_FAST {
        @Override public Vec3 destination(ComboMotionFrame frame) {
            double p = frame.progress();
            return frame.playerAnchor().add(frame.forward().scale(1.05D * p))
                    .subtract(frame.right().scale(1.75D * p));
        }
    },
    SIDE_RIGHT_FAST {
        @Override public Vec3 destination(ComboMotionFrame frame) {
            double p = frame.progress();
            return frame.playerAnchor().add(frame.forward().scale(0.75D * p))
                    .add(frame.right().scale(2.85D * p));
        }
    },
    FORWARD_LUNGE_FAST {
        @Override public Vec3 destination(ComboMotionFrame frame) {
            double distance = Mth.clamp(frame.target().subtract(frame.playerAnchor())
                    .horizontalDistance() - 1.55D, 2.2D, 6.4D);
            double p = ComboMotionMath.smooth(Mth.clamp(frame.tick() / 6.0D, 0.0D, 1.0D));
            return frame.playerAnchor().add(frame.forward().scale(distance * p));
        }
    },
    BACKWARD_APEX_FAST {
        @Override public Vec3 destination(ComboMotionFrame frame) {
            double tick = frame.tick();
            Vec3 apex = frame.playerAnchor().subtract(frame.forward().scale(3.65D)).add(0.0D, 3.35D, 0.0D);
            if (tick <= 3.0D) return frame.playerAnchor().lerp(apex,
                    ComboMotionMath.smooth(tick / 3.0D));
            if (tick <= 10.0D) return apex;
            return apex.lerp(frame.playerAnchor(), ComboMotionMath.smooth((tick - 10.0D)
                    / Math.max(1.0D, frame.duration() - 10.0D)));
        }
    },
    SIDE_LEFT_LONG {
        @Override public Vec3 destination(ComboMotionFrame frame) {
            double p = ComboMotionMath.smooth(Mth.clamp(frame.tick() / 5.0D, 0.0D, 1.0D));
            return frame.playerAnchor().add(frame.forward().scale(1.7D * p))
                    .subtract(frame.right().scale(5.2D * p));
        }
    },
    SIDE_RIGHT_LONG {
        @Override public Vec3 destination(ComboMotionFrame frame) {
            double p = ComboMotionMath.smooth(Mth.clamp(frame.tick() / 5.0D, 0.0D, 1.0D));
            return frame.playerAnchor().add(frame.forward().scale(1.4D * p))
                    .add(frame.right().scale(6.8D * p));
        }
    },
    FORWARD_LUNGE_LONG {
        @Override public Vec3 destination(ComboMotionFrame frame) {
            double distance = Mth.clamp(frame.target().subtract(frame.playerAnchor())
                    .horizontalDistance() - 1.45D, 4.0D, 10.5D);
            double p = ComboMotionMath.smooth(Mth.clamp(frame.tick() / 5.0D, 0.0D, 1.0D));
            return frame.playerAnchor().add(frame.forward().scale(distance * p));
        }
    },
    BACKWARD_APEX_LONG {
        @Override public Vec3 destination(ComboMotionFrame frame) {
            double tick = frame.tick();
            Vec3 apex = frame.playerAnchor().subtract(frame.forward().scale(7.8D))
                    .add(0.0D, 5.6D, 0.0D);
            if (tick <= 3.0D) return frame.playerAnchor().lerp(apex,
                    ComboMotionMath.smooth(tick / 3.0D));
            if (tick <= 9.0D) return apex;
            return apex.lerp(frame.playerAnchor(), ComboMotionMath.smooth((tick - 9.0D)
                    / Math.max(1.0D, frame.duration() - 9.0D)));
        }
    };

    public abstract Vec3 destination(ComboMotionFrame frame);
}
