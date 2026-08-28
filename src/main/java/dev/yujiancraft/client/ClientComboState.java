package dev.yujiancraft.client;

import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.entity.FlyingSwordEntity;
import dev.yujiancraft.network.ModNetwork;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/** Client mirror used only for input interception, pose interpolation and restrained camera assist. */
@Mod.EventBusSubscriber(modid = YujianCraft.MOD_ID, value = Dist.CLIENT)
public final class ClientComboState {
    private static final Map<Integer, State> STATES = new HashMap<>();
    private static float lastPlayerYaw;
    private static float lastPlayerPitch;
    private static int assistedStage = -1;

    private ClientComboState() { }

    public static void accept(ModNetwork.ComboStatePacket packet) {
        State previous = STATES.get(packet.playerId());
        long now = Util.getMillis();
        if (!packet.active()) {
            if (previous != null) STATES.put(packet.playerId(), previous.end(now));
            assistedStage = -1;
            return;
        }
        STATES.put(packet.playerId(), new State(true, packet.stage(), packet.startGameTick(),
                packet.durationTicks(), packet.targetId(), packet.playerAnchor(), packet.targetAnchor(),
                previous == null || !previous.active ? now : previous.transitionAt, 0L));
        if (packet.stage() == 0) assistedStage = -1;
    }

    public static boolean isLocalActive() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && isActive(minecraft.player.getId());
    }

    public static boolean isActive(int playerId) {
        State state = STATES.get(playerId);
        return state != null && state.active;
    }

    public static float poseWeight(int playerId) {
        State state = STATES.get(playerId);
        if (state == null) return 0.0F;
        long now = Util.getMillis();
        if (state.active) return Mth.clamp((now - state.transitionAt) / 250.0F, 0.0F, 1.0F);
        return 1.0F - Mth.clamp((now - state.endedAt) / 250.0F, 0.0F, 1.0F);
    }

    public static boolean shouldRenderPose(int playerId) {
        return poseWeight(playerId) > 0.001F;
    }

    /**
     * Rebuilds the authored combo path from the same timeline used by the server. The server still
     * owns combat and entity state; this is only a render position. Keeping both the owner and the
     * sword on the client's current interpolation frame avoids the one-packet "towed behind" look
     * that appears whenever a locally predicted player moves faster than entity updates arrive.
     */
    public static Vec3 visualSwordPosition(FlyingSwordEntity sword, float partialTick) {
        if (!sword.isVisualComboControlled()) return null;
        var owner = sword.getVisualOwner();
        if (owner == null || owner.level() == null) return null;
        State state = STATES.get(owner.getId());
        if (state == null || !state.active) return null;

        Vec3 ownerPosition = interpolatedPosition(owner, partialTick);
        float ownerYaw = Mth.rotLerp(partialTick, owner.yRotO, owner.getYRot());
        Vec3 ownerLook = Vec3.directionFromRotation(0.0F, ownerYaw);
        int slot = Math.floorMod(sword.getVisualFormationSlot(), 6);
        if (state.stage <= 0) return idlePosition(ownerPosition, ownerLook, slot);

        Minecraft minecraft = Minecraft.getInstance();
        float age = minecraft.level == null ? 0.0F
                : minecraft.level.getGameTime() + partialTick - state.startTick;
        double tick = Mth.clamp(age, 0.0F, Math.max(0, state.durationTicks - 0.001F));
        Vec3 targetPoint = state.targetAnchor;
        if (minecraft.level != null && state.targetId >= 0) {
            Entity raw = minecraft.level.getEntity(state.targetId);
            if (raw instanceof LivingEntity target && target.isAlive()) {
                targetPoint = interpolatedPosition(target, partialTick)
                        .add(0.0D, target.getBbHeight() * 0.52D, 0.0D);
            }
        }
        Vec3 forward = horizontal(targetPoint.subtract(ownerPosition), ownerLook);
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        return switch (state.stage) {
            case 1 -> crossCutPosition(ownerPosition, targetPoint, forward, right, slot, tick,
                    state.durationTicks, true);
            case 2 -> crossCutPosition(ownerPosition, targetPoint, forward, right, slot, tick,
                    state.durationTicks, false);
            case 3 -> ringLungePosition(ownerPosition, forward, right, slot, tick, state.durationTicks);
            case 4 -> sixSwordPosition(state.playerAnchor, targetPoint, forward, right, slot, tick);
            case 5 -> giantSwordStation(ownerPosition, targetPoint, forward, right, slot, tick);
            default -> idlePosition(ownerPosition, ownerLook, slot);
        };
    }

    private static Vec3 crossCutPosition(Vec3 owner, Vec3 target, Vec3 forward, Vec3 right,
                                         int slot, double tick, int duration, boolean leftToRight) {
        boolean active = leftToRight ? slot < 3 : slot >= 3;
        if (!active) {
            return owner.add(0.0D, 1.25D + (slot % 3) * 0.24D, 0.0D)
                    .add(right.scale((slot - 2.5D) * 0.36D)).subtract(forward.scale(0.7D));
        }
        int lane = slot % 3;
        double sign = leftToRight ? 1.0D : -1.0D;
        Vec3 start = target.add(right.scale(-sign * (3.2D + lane * 0.34D)))
                .add(0.0D, leftToRight ? 3.0D + lane * 0.22D : -0.15D + lane * 0.16D, 0.0D)
                .subtract(forward.scale(0.7D));
        Vec3 end = target.add(right.scale(sign * (3.2D + lane * 0.34D)))
                .add(0.0D, leftToRight ? -0.2D + lane * 0.12D : 3.0D + lane * 0.22D, 0.0D)
                .add(forward.scale(1.0D));
        return start.lerp(end, smooth(tick / Math.max(1.0D, duration)));
    }

    private static Vec3 ringLungePosition(Vec3 owner, Vec3 forward, Vec3 right, int slot,
                                          double tick, int duration) {
        double progress = smooth(tick / Math.max(1.0D, duration));
        Vec3 centre = owner.add(forward.scale(1.35D)).add(0.0D, 1.0D, 0.0D);
        double angle = Math.PI * 2.0D * slot / 6.0D + progress * Math.PI * 4.4D;
        Vec3 radial = right.scale(Math.cos(angle)).add(0.0D, Math.sin(angle), 0.0D);
        return centre.add(radial.scale(2.65D)).add(forward.scale(Math.sin(angle * 2.0D) * 0.3D));
    }

    private static Vec3 sixSwordPosition(Vec3 playerAnchor, Vec3 target, Vec3 forward, Vec3 right,
                                         int slot, double tick) {
        Vec3 apex = playerAnchor.subtract(forward.scale(3.2D)).add(0.0D, 2.65D, 0.0D);
        double angle = Math.PI * 2.0D * slot / 6.0D + tick * 0.12D;
        Vec3 ring = apex.add(right.scale(Math.cos(angle) * 2.25D))
                .add(forward.scale(Math.sin(angle) * 1.15D)).add(0.0D, Math.sin(angle) * 1.65D, 0.0D);
        Vec3 end = target.add(forward.scale(2.2D)).add(right.scale((slot - 2.5D) * 0.12D));
        return ring.lerp(end, smooth(Mth.clamp((tick - 6.0D) / 5.0D, 0.0D, 1.0D)));
    }

    private static Vec3 giantSwordStation(Vec3 owner, Vec3 target, Vec3 forward, Vec3 right,
                                          int slot, double tick) {
        double progress = smooth(Math.min(1.0D, tick / 7.0D));
        Vec3 centre = target.add(0.0D, 14.0D, 0.0D);
        double angle = Math.PI * 2.0D * slot / 6.0D + tick * 0.09D;
        Vec3 station = centre.add(Math.cos(angle) * 9.5D, 0.0D, Math.sin(angle) * 9.5D);
        Vec3 start = owner.add(0.0D, 1.2D, 0.0D)
                .add(right.scale((slot - 2.5D) * 0.45D)).subtract(forward.scale(0.5D));
        return start.lerp(station, progress);
    }

    private static Vec3 idlePosition(Vec3 owner, Vec3 look, int slot) {
        Vec3 forward = horizontal(look, new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        double angle = Math.PI * 2.0D * slot / 6.0D;
        return owner.add(0.0D, 1.2D, 0.0D)
                .add(right.scale(Math.cos(angle) * 1.75D))
                .add(forward.scale(Math.sin(angle) * 0.72D - 0.75D))
                .add(0.0D, Math.sin(angle) * 1.15D, 0.0D);
    }

    private static Vec3 interpolatedPosition(Entity entity, float partialTick) {
        return new Vec3(Mth.lerp(partialTick, entity.xo, entity.getX()),
                Mth.lerp(partialTick, entity.yo, entity.getY()),
                Mth.lerp(partialTick, entity.zo, entity.getZ()));
    }

    private static Vec3 horizontal(Vec3 vector, Vec3 fallback) {
        Vec3 result = new Vec3(vector.x, 0.0D, vector.z);
        if (result.lengthSqr() < 1.0E-6D) result = new Vec3(fallback.x, 0.0D, fallback.z);
        return result.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : result.normalize();
    }

    private static double smooth(double value) {
        double x = Mth.clamp(value, 0.0D, 1.0D);
        return x * x * (3.0D - 2.0D * x);
    }

    public static Impact impact(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return null;
        State state = STATES.get(minecraft.player.getId());
        if (state == null || !state.active || state.stage <= 0) return null;
        float age = minecraft.level.getGameTime() + partialTick - state.startTick;
        int impactTick = switch (state.stage) { case 1, 2 -> 5; case 3 -> 8; case 4 -> 10; default -> 19; };
        float distance = Math.abs(age - impactTick);
        if (distance > 2.4F) return null;
        float envelope = 1.0F - distance / 2.4F;
        float strength = switch (state.stage) { case 1 -> 0.52F; case 2 -> 0.62F;
            case 3 -> 0.9F; case 4 -> 1.15F; default -> 1.7F; };
        return new Impact(envelope * strength, state.targetAnchor);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) return;
        State state = STATES.get(minecraft.player.getId());
        STATES.entrySet().removeIf(entry -> !entry.getValue().active
                && Util.getMillis() - entry.getValue().endedAt > 300L);
        if (state == null || !state.active || state.stage <= 0 || state.targetId < 0) return;
        float age = minecraft.level.getGameTime() - state.startTick;
        if (state.stage != assistedStage) {
            assistedStage = state.stage;
            lastPlayerYaw = minecraft.player.getYRot();
            lastPlayerPitch = minecraft.player.getXRot();
        }
        if (age < 0 || age > 3) return;
        // Any deliberate mouse movement immediately wins over assistance.
        float manualYaw = Math.abs(Mth.wrapDegrees(minecraft.player.getYRot() - lastPlayerYaw));
        float manualPitch = Math.abs(minecraft.player.getXRot() - lastPlayerPitch);
        if (manualYaw > 2.2F || manualPitch > 2.2F) return;
        Entity raw = minecraft.level.getEntity(state.targetId);
        if (!(raw instanceof LivingEntity target)) return;
        Vec3 delta = target.getEyePosition().subtract(minecraft.player.getEyePosition());
        float desiredYaw = (float) (Mth.atan2(-delta.x, delta.z) * Mth.RAD_TO_DEG);
        float desiredPitch = (float) (Mth.atan2(-delta.y, delta.horizontalDistance()) * Mth.RAD_TO_DEG);
        float yawStep = Mth.clamp(Mth.wrapDegrees(desiredYaw - minecraft.player.getYRot()), -3.5F, 3.5F);
        float pitchStep = Mth.clamp(desiredPitch - minecraft.player.getXRot(), -2.6F, 2.6F);
        minecraft.player.setYRot(minecraft.player.getYRot() + yawStep);
        minecraft.player.setXRot(minecraft.player.getXRot() + pitchStep);
        lastPlayerYaw = minecraft.player.getYRot();
        lastPlayerPitch = minecraft.player.getXRot();
    }

    public static void clear() {
        STATES.clear();
        assistedStage = -1;
    }

    public record Impact(float strength, Vec3 centre) { }

    private record State(boolean active, int stage, long startTick, int durationTicks, int targetId,
                         Vec3 playerAnchor, Vec3 targetAnchor, long transitionAt, long endedAt) {
        private State end(long now) {
            return new State(false, 0, startTick, durationTicks, -1, playerAnchor, targetAnchor,
                    transitionAt, now);
        }
    }
}
