package dev.yujiancraft.client;

import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.combat.combo.ComboMotionFrame;
import dev.yujiancraft.combat.combo.ComboMotionMath;
import dev.yujiancraft.combat.combo.ComboRootMotion;
import dev.yujiancraft.combat.combo.ComboStageDefinition;
import dev.yujiancraft.combat.combo.ComboStyle;
import dev.yujiancraft.combat.combo.ComboVfxProfile;
import dev.yujiancraft.combat.combo.ComboWarpProfile;
import dev.yujiancraft.combat.combo.StarRingMotion;
import dev.yujiancraft.entity.FlyingSwordEntity;
import dev.yujiancraft.network.ModNetwork;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Client mirror for input, pose interpolation, shared sword reconstruction and local root prediction. */
@net.neoforged.fml.common.EventBusSubscriber(modid = YujianCraft.MOD_ID, value = Dist.CLIENT)
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
        ComboStyle style = ComboStyle.byId(packet.styleId());
        Minecraft minecraft = Minecraft.getInstance();
        STATES.put(packet.playerId(), new State(true, style, packet.stage(), packet.startGameTick(),
                packet.durationTicks(), packet.targetId(), packet.playerAnchor(), packet.targetAnchor(),
                packet.warpDestination(), packet.warpYaw(),
                packet.orbitPhase(), packet.orbitBoostTick(),
                previous == null || !previous.active ? now : previous.transitionAt, 0L));
        if (packet.stage() == 0) assistedStage = -1;
        if (minecraft.player != null && packet.playerId() == minecraft.player.getId()
                && (previous == null || previous.style != style)) {
            ClientTechniqueOverlayState.showComboStyle(style.translationKey());
        }
    }

    public static boolean isLocalActive() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && isActive(minecraft.player.getId());
    }

    public static boolean isActive(int playerId) {
        State state = STATES.get(playerId);
        return state != null && state.active;
    }

    public static Vec3 visualSwordPosition(FlyingSwordEntity sword, float partialTick) {
        VisualSwordPose pose = visualSwordPose(sword, partialTick);
        return pose == null ? null : pose.position();
    }

    /** Uses the same partial-tick reconstruction as position so a fast orbit cannot visibly outrun
     * the server-synchronised Euler angles and make the blade look radial or stutter. */
    public static Vec3 visualSwordDirection(FlyingSwordEntity sword, float partialTick) {
        VisualSwordPose pose = visualSwordPose(sword, partialTick);
        return pose == null ? null : pose.direction();
    }

    private static VisualSwordPose visualSwordPose(FlyingSwordEntity sword, float partialTick) {
        if (!sword.isVisualComboControlled()) return null;
        var owner = sword.getVisualOwner();
        if (owner == null || owner.level() == null) return null;
        State state = STATES.get(owner.getId());
        if (state == null || !state.active) return null;

        Vec3 ownerPosition = interpolatedPosition(owner, partialTick);
        float ownerYaw = Mth.rotLerp(partialTick, owner.yRotO, owner.getYRot());
        Vec3 ownerLook = Vec3.directionFromRotation(0.0F, ownerYaw);
        int slot = Math.floorMod(sword.getVisualFormationSlot(), 6);
        Minecraft minecraft = Minecraft.getInstance();
        double worldTick = minecraft.level == null ? state.startTick
                : minecraft.level.getGameTime() + partialTick;
        double phase = orbitPhase(state, worldTick);
        double speed = orbitSpeed(state);
        if (state.stage <= 0) {
            return new VisualSwordPose(
                    state.style.formation().position(ownerPosition, ownerLook, slot, phase, worldTick),
                    state.style.formation().direction(ownerPosition, ownerLook, slot, phase, speed, worldTick));
        }

        float age = (float) (worldTick - state.startTick);
        double tick = Mth.clamp(age, 0.0F, Math.max(0, state.durationTicks - 0.001F));
        Vec3 targetPoint = resolveTargetPoint(minecraft, state, partialTick);
        Vec3 forward = ComboMotionMath.horizontal(state.targetAnchor.subtract(state.playerAnchor), ownerLook);
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        ComboStageDefinition definition = state.style.stage(state.stage);
        ComboMotionFrame frame = new ComboMotionFrame(ownerPosition, state.playerAnchor,
                targetPoint, forward, right, slot, tick, state.durationTicks, worldTick, phase, speed);
        return new VisualSwordPose(definition.choreography().position(frame),
                definition.choreography().direction(frame));
    }

    private record VisualSwordPose(Vec3 position, Vec3 direction) { }

    private static Vec3 resolveTargetPoint(Minecraft minecraft, State state, float partialTick) {
        Vec3 targetPoint = state.targetAnchor;
        if (minecraft.level != null && state.targetId >= 0) {
            Entity raw = minecraft.level.getEntity(state.targetId);
            if (raw instanceof LivingEntity target && target.isAlive()) {
                targetPoint = interpolatedPosition(target, partialTick)
                        .add(0.0D, target.getBbHeight() * 0.52D, 0.0D);
            }
        }
        return targetPoint;
    }

    private static Vec3 interpolatedPosition(Entity entity, float partialTick) {
        return new Vec3(Mth.lerp(partialTick, entity.xo, entity.getX()),
                Mth.lerp(partialTick, entity.yo, entity.getY()),
                Mth.lerp(partialTick, entity.zo, entity.getZ()));
    }

    public static Impact impact(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !ClientOptions.hitImpactVisual()) return null;
        State state = STATES.get(minecraft.player.getId());
        if (state == null || !state.active || state.stage <= 0) return null;
        ComboStageDefinition definition = state.style.stage(state.stage);
        float age = minecraft.level.getGameTime() + partialTick - state.startTick;
        ComboWarpProfile warp = definition.warp();
        if (warp != ComboWarpProfile.NONE) {
            if (state.style.particlesOnlyWarp()) return null;
            float execute = warp.executeTick();
            float blackout = ClientOptions.comboWarpBlackout()
                    ? triangular(age, execute - 0.32F, execute, execute + 0.38F) : 0.0F;
            float arrival = triangular(age, execute - 0.6F, execute + 0.3F, execute + 2.4F);
            // The black frame conceals the discontinuous spatial/camera change. The monochrome
            // impact deliberately begins afterwards, so the arrival reads as a second heavy beat.
            float delayedFlash = heldEnvelope(age, execute + 2.2F, 3.2F, 2.8F);
            if (blackout > 0.001F || arrival > 0.001F || delayedFlash > 0.001F) {
                float power = warp.vfxStrength();
                return new Impact(arrival * power * 1.28F,
                        Math.min(0.96F, delayedFlash * (0.78F + power * 0.10F)),
                        (arrival * 0.0135F + delayedFlash * 0.0090F) * power,
                        (arrival * 0.0052F + delayedFlash * 0.0032F) * power,
                        blackout, Mth.clamp((age - (execute - 0.6F)) / 3.0F, 0.0F, 1.0F),
                        state.warpDestination, state.style, state.stage);
            }
        }
        if (!definition.damagingAttack()) return null;
        ComboVfxProfile vfx = definition.vfx();
        if (state.style.starRing() && state.orbitBoostTick < state.startTick) return null;
        float impactTick = definition.commitTick();
        if (state.style.starRing() && state.orbitBoostTick >= state.startTick) {
            impactTick = state.orbitBoostTick - state.startTick;
        }
        float envelope;
        if (vfx.thresholdHoldTicks() > 0.0F) {
            envelope = heldEnvelope(age, impactTick, vfx.thresholdHoldTicks(), 3.2F);
        } else {
            float width = definition.vfx().thresholdAmount() <= 0.001F ? 2.4F : 1.65F;
            float distance = Math.abs(age - impactTick);
            if (distance > width) return null;
            envelope = 1.0F - distance / width;
        }
        if (envelope <= 0.001F) return null;
        float cameraEnvelope = vfx.thresholdHoldTicks() > 0.0F
                ? triangular(age, impactTick - 0.45F,
                impactTick + 0.35F, impactTick + 3.8F)
                : envelope;
        float shakePhase = Mth.clamp((age - (impactTick - 0.45F)) / 4.25F,
                0.0F, 1.0F);
        return new Impact(cameraEnvelope * vfx.cameraStrength(), envelope * vfx.thresholdAmount(),
                envelope * vfx.radialBlurStrength(), envelope * vfx.chromaticStrength(),
                0.0F, shakePhase, resolveTargetPoint(minecraft, state, partialTick),
                state.style, state.stage);
    }

    private static float heldEnvelope(float age, float impact, float hold, float release) {
        float lead = 1.8F;
        if (age < impact - lead || age > impact + hold + release) return 0.0F;
        if (age < impact) return Mth.clamp((age - (impact - lead)) / lead, 0.0F, 1.0F);
        if (age <= impact + hold) return 1.0F;
        return 1.0F - Mth.clamp((age - impact - hold) / release, 0.0F, 1.0F);
    }

    private static float triangular(float age, float start, float peak, float end) {
        if (age <= start || age >= end) return 0.0F;
        if (age <= peak) return Mth.clamp((age - start) / Math.max(0.001F, peak - start), 0.0F, 1.0F);
        return 1.0F - Mth.clamp((age - peak) / Math.max(0.001F, end - peak), 0.0F, 1.0F);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;
        State state = STATES.get(minecraft.player.getId());
        STATES.entrySet().removeIf(entry -> !entry.getValue().active
                && Util.getMillis() - entry.getValue().endedAt > 300L);
        if (state == null || !state.active || state.stage <= 0) return;

        applyLocalMotion(minecraft, state);
        if (minecraft.screen != null || state.targetId < 0) return;
        // Warp sets never continuously steer the reticle. The one authored 180-degree turn is
        // applied atomically with its mirror transition instead of dragging aim between stages.
        if (state.style.hasWarpStages()) return;
        float age = minecraft.level.getGameTime() - state.startTick;
        if (state.stage != assistedStage) {
            assistedStage = state.stage;
            lastPlayerYaw = minecraft.player.getYRot();
            lastPlayerPitch = minecraft.player.getXRot();
        }
        if (age < 0 || age > 6) return;
        float manualYaw = Math.abs(Mth.wrapDegrees(minecraft.player.getYRot() - lastPlayerYaw));
        float manualPitch = Math.abs(minecraft.player.getXRot() - lastPlayerPitch);
        if (manualYaw > 0.75F || manualPitch > 0.75F) {
            lastPlayerYaw = minecraft.player.getYRot();
            lastPlayerPitch = minecraft.player.getXRot();
            return;
        }
        Entity raw = minecraft.level.getEntity(state.targetId);
        if (!(raw instanceof LivingEntity target)) return;
        Vec3 delta = target.getEyePosition().subtract(minecraft.player.getEyePosition());
        float desiredYaw = (float) (Mth.atan2(-delta.x, delta.z) * Mth.RAD_TO_DEG);
        float desiredPitch = (float) (Mth.atan2(-delta.y, delta.horizontalDistance()) * Mth.RAD_TO_DEG);
        float yawError = Mth.wrapDegrees(desiredYaw - minecraft.player.getYRot());
        float pitchError = desiredPitch - minecraft.player.getXRot();
        if (ClientOptions.comboPreciseCameraAssist()) {
            minecraft.player.setYRot(minecraft.player.getYRot()
                    + Mth.clamp(yawError, -3.5F, 3.5F));
            minecraft.player.setXRot(minecraft.player.getXRot()
                    + Mth.clamp(pitchError, -2.6F, 2.6F));
        } else {
            // A large dead zone leaves ordinary aim entirely player-controlled. Outside it only
            // the excess angle is damped, so target selection never implies reticle ownership.
            float yawExcess = Math.copySign(Math.max(0.0F, Math.abs(yawError) - 46.0F), yawError);
            float pitchExcess = Math.copySign(Math.max(0.0F, Math.abs(pitchError) - 30.0F), pitchError);
            minecraft.player.setYRot(minecraft.player.getYRot()
                    + Mth.clamp(yawExcess * 0.035F, -0.55F, 0.55F));
            minecraft.player.setXRot(minecraft.player.getXRot()
                    + Mth.clamp(pitchExcess * 0.028F, -0.34F, 0.34F));
        }
        lastPlayerYaw = minecraft.player.getYRot();
        lastPlayerPitch = minecraft.player.getXRot();
    }

    private static boolean applyLocalMotion(Minecraft minecraft, State state) {
        ComboStageDefinition definition = state.style.stage(state.stage);
        double tick = Mth.clamp(minecraft.level.getGameTime() - state.startTick, 0.0D,
                Math.max(0, state.durationTicks - 0.001D));
        ComboWarpProfile warp = definition.warp();
        if (warp != ComboWarpProfile.NONE) {
            if (tick < warp.executeTick()) {
                minecraft.player.move(MoverType.SELF,
                        state.playerAnchor.subtract(minecraft.player.position()));
                minecraft.player.setDeltaMovement(Vec3.ZERO);
                return false;
            }
            if (!state.localWarpApplied) {
                state.localWarpApplied = true;
                minecraft.player.setPos(state.warpDestination.x, state.warpDestination.y,
                        state.warpDestination.z);
                minecraft.player.xo = state.warpDestination.x;
                minecraft.player.yo = state.warpDestination.y;
                minecraft.player.zo = state.warpDestination.z;
                if (warp.reverseFacing()) {
                    minecraft.player.setYRot(state.warpYaw);
                    minecraft.player.yRotO = minecraft.player.getYRot();
                }
                minecraft.player.setDeltaMovement(Vec3.ZERO);
                minecraft.player.fallDistance = 0.0F;
            }
            return tick <= warp.executeTick() + 4.0D;
        }
        if (definition.rootMotion() == ComboRootMotion.NONE) return false;
        Vec3 fallback = Vec3.directionFromRotation(0.0F, minecraft.player.getYRot());
        Vec3 forward = ComboMotionMath.horizontal(state.targetAnchor.subtract(state.playerAnchor), fallback);
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        double worldTick = minecraft.level.getGameTime();
        ComboMotionFrame frame = new ComboMotionFrame(minecraft.player.position(), state.playerAnchor,
                state.targetAnchor, forward, right, 0, tick, state.durationTicks, worldTick,
                orbitPhase(state, worldTick), orbitSpeed(state));
        Vec3 destination = definition.rootMotion().destination(frame);
        minecraft.player.move(MoverType.SELF, destination.subtract(minecraft.player.position()).scale(0.88D));
        minecraft.player.setDeltaMovement(Vec3.ZERO);
        minecraft.player.fallDistance = 0.0F;
        return false;
    }

    private static double orbitPhase(State state, double worldTick) {
        double elapsed = Math.max(0.0D, worldTick - state.startTick);
        double boosted = state.orbitBoostTick < state.startTick ? 0.0D
                : Math.max(0.0D, worldTick - state.orbitBoostTick);
        return state.orbitPhase + StarRingMotion.BASE_ANGULAR_SPEED * elapsed
                + (StarRingMotion.BOOST_ANGULAR_SPEED - StarRingMotion.BASE_ANGULAR_SPEED) * boosted;
    }

    private static double orbitSpeed(State state) {
        return state.orbitBoostTick < state.startTick
                ? StarRingMotion.BASE_ANGULAR_SPEED : StarRingMotion.BOOST_ANGULAR_SPEED;
    }

    public static void clear() {
        STATES.clear();
        assistedStage = -1;
    }

    /** Final GUI pass makes the authored full-black interval truly cover the whole frame. */
    public static void renderBlackout(net.minecraft.client.gui.GuiGraphics graphics,
                                      DeltaTracker deltaTracker) {
        if (!ClientOptions.swordArrayPostEffect()) return;
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
        Impact current = impact(partialTick);
        if (current == null || current.blackout() <= 0.001F) return;
        int alpha = Mth.clamp(Math.round(current.blackout() * 255.0F), 0, 255);
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24);
    }

    public static boolean shouldHidePlayer(int playerId, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        State state = STATES.get(playerId);
        if (minecraft.level == null || state == null || !state.active || state.stage <= 0) return false;
        ComboWarpProfile warp = state.style.stage(state.stage).warp();
        if (warp == ComboWarpProfile.NONE || state.style.particlesOnlyWarp()) return false;
        float age = minecraft.level.getGameTime() + partialTick - state.startTick;
        return age >= warp.executeTick() - 2.25F && age <= warp.executeTick() + 2.25F;
    }

    public static List<WarpVisual> warpVisuals(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        List<WarpVisual> visuals = new ArrayList<>();
        if (minecraft.level == null) return visuals;
        for (Map.Entry<Integer, State> entry : STATES.entrySet()) {
            State state = entry.getValue();
            if (!state.active || state.stage <= 0) continue;
            ComboWarpProfile warp = state.style.stage(state.stage).warp();
            boolean transitionVisible = warp != ComboWarpProfile.NONE && !state.style.particlesOnlyWarp();
            if (!transitionVisible) continue;
            float age = minecraft.level.getGameTime() + partialTick - state.startTick;
            if (age < -0.5F || age > state.durationTicks + 1.0F) continue;
            Entity entity = minecraft.level.getEntity(entry.getKey());
            Vec3 current = entity == null ? state.warpDestination : interpolatedPosition(entity, partialTick);
            visuals.add(new WarpVisual(entry.getKey(), state.playerAnchor, state.warpDestination,
                    current, age, warp.executeTick(), warp == ComboWarpProfile.NONE ? 1.0F : warp.vfxStrength(),
                    true, state.stage));
        }
        return visuals;
    }

    public record Impact(float strength, float threshold, float radialBlur, float chromatic,
                         float blackout, float shakePhase, Vec3 centre,
                         ComboStyle style, int stage) { }

    public record WarpVisual(int playerId, Vec3 origin, Vec3 destination, Vec3 current,
                             float age, int executeTick, float strength,
                             boolean transition, int stage) { }

    private static final class State {
        private final boolean active;
        private final ComboStyle style;
        private final int stage;
        private final long startTick;
        private final int durationTicks;
        private final int targetId;
        private final Vec3 playerAnchor;
        private final Vec3 targetAnchor;
        private final Vec3 warpDestination;
        private final float warpYaw;
        private final double orbitPhase;
        private final long orbitBoostTick;
        private final long transitionAt;
        private final long endedAt;
        private boolean localWarpApplied;

        private State(boolean active, ComboStyle style, int stage, long startTick, int durationTicks,
                      int targetId, Vec3 playerAnchor, Vec3 targetAnchor, Vec3 warpDestination,
                      float warpYaw, double orbitPhase, long orbitBoostTick,
                      long transitionAt, long endedAt) {
            this.active = active;
            this.style = style;
            this.stage = stage;
            this.startTick = startTick;
            this.durationTicks = durationTicks;
            this.targetId = targetId;
            this.playerAnchor = playerAnchor;
            this.targetAnchor = targetAnchor;
            this.warpDestination = warpDestination;
            this.warpYaw = warpYaw;
            this.orbitPhase = orbitPhase;
            this.orbitBoostTick = orbitBoostTick;
            this.transitionAt = transitionAt;
            this.endedAt = endedAt;
            this.localWarpApplied = stage <= 0 || style.stage(Math.max(1, stage)).warp() == ComboWarpProfile.NONE;
        }

        private State end(long now) {
            return new State(false, style, 0, startTick, durationTicks, -1, playerAnchor,
                    targetAnchor, warpDestination, warpYaw, orbitPhase, orbitBoostTick,
                    transitionAt, now);
        }
    }
}
