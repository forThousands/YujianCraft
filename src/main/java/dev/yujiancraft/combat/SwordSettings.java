package dev.yujiancraft.combat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public record SwordSettings(int minimumDockTicks, double automaticTargetRadius, double crosshairLockRadius,
                            TargetingMode targetingMode, AttackMode attackMode) {
    public static final int DEFAULT_MINIMUM_DOCK_TICKS = 45;
    public static final int MINIMUM_DOCK_TICKS = 0;
    public static final int MAXIMUM_DOCK_TICKS = 200;
    public static final double DEFAULT_AUTOMATIC_RADIUS = 12.0D;
    public static final double DEFAULT_LOCK_RADIUS = 32.0D;
    public static final double MINIMUM_AUTOMATIC_RADIUS = 4.0D;
    public static final double MAXIMUM_AUTOMATIC_RADIUS = 48.0D;
    public static final double MINIMUM_LOCK_RADIUS = 8.0D;
    public static final double MAXIMUM_LOCK_RADIUS = 64.0D;
    private static final String ROOT_TAG = "SwordSettings";

    public SwordSettings {
        minimumDockTicks = Mth.clamp(minimumDockTicks, MINIMUM_DOCK_TICKS, MAXIMUM_DOCK_TICKS);
        automaticTargetRadius = Mth.clamp(automaticTargetRadius, MINIMUM_AUTOMATIC_RADIUS, MAXIMUM_AUTOMATIC_RADIUS);
        crosshairLockRadius = Mth.clamp(crosshairLockRadius, MINIMUM_LOCK_RADIUS, MAXIMUM_LOCK_RADIUS);
        if (targetingMode == null) targetingMode = TargetingMode.CROSSHAIR_LOCK;
        if (attackMode == null) attackMode = AttackMode.SORTIE;
    }

    public static SwordSettings defaults() {
        return new SwordSettings(DEFAULT_MINIMUM_DOCK_TICKS, DEFAULT_AUTOMATIC_RADIUS, DEFAULT_LOCK_RADIUS,
                TargetingMode.CROSSHAIR_LOCK, AttackMode.SORTIE);
    }

    public static SwordSettings fromNetwork(int dockTicks, double automaticRadius, double lockRadius,
                                            int targetingOrdinal, int attackOrdinal) {
        return new SwordSettings(dockTicks, automaticRadius, lockRadius,
                TargetingMode.fromOrdinal(targetingOrdinal), AttackMode.fromOrdinal(attackOrdinal));
    }

    public static SwordSettings read(ItemStack stack) {
        if (!stack.hasTag() || !stack.getTag().contains(ROOT_TAG)) {
            return defaults();
        }
        CompoundTag tag = stack.getTag().getCompound(ROOT_TAG);
        double automaticRadius = tag.contains("AutomaticTargetRadius")
                ? tag.getDouble("AutomaticTargetRadius") : DEFAULT_AUTOMATIC_RADIUS;
        double lockRadius = tag.contains("CrosshairLockRadius")
                ? tag.getDouble("CrosshairLockRadius") : DEFAULT_LOCK_RADIUS;
        return fromNetwork(tag.getInt("MinimumDockTicks"), automaticRadius, lockRadius,
                tag.getInt("TargetingMode"), tag.getInt("AttackMode"));
    }

    public void write(ItemStack stack) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("MinimumDockTicks", minimumDockTicks);
        tag.putDouble("AutomaticTargetRadius", automaticTargetRadius);
        tag.putDouble("CrosshairLockRadius", crosshairLockRadius);
        tag.putInt("TargetingMode", targetingMode.ordinal());
        tag.putInt("AttackMode", attackMode.ordinal());
        stack.getOrCreateTag().put(ROOT_TAG, tag);
    }
}
