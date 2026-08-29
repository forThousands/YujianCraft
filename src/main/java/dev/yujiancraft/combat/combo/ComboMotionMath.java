package dev.yujiancraft.combat.combo;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class ComboMotionMath {
    private ComboMotionMath() { }

    public static double smooth(double value) {
        double x = Mth.clamp(value, 0.0D, 1.0D);
        return x * x * (3.0D - 2.0D * x);
    }

    public static Vec3 horizontal(Vec3 vector, Vec3 fallback) {
        Vec3 result = new Vec3(vector.x, 0.0D, vector.z);
        if (result.lengthSqr() < 1.0E-6D) result = new Vec3(fallback.x, 0.0D, fallback.z);
        return result.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : result.normalize();
    }
}

