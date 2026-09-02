package dev.yujiancraft.combat.combo;

import net.minecraft.world.phys.Vec3;

/** Immutable input shared by server sword placement, client reconstruction and root prediction. */
public record ComboMotionFrame(Vec3 owner, Vec3 playerAnchor, Vec3 target, Vec3 forward, Vec3 right,
                               int slot, double tick, int duration, double worldTick,
                               double orbitPhase, double orbitSpeed) {
    public double progress() { return ComboMotionMath.smooth(tick / Math.max(1.0D, duration)); }
}

