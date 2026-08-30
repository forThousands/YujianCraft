package dev.yujiancraft.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.yujiancraft.YujianCraft;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@net.neoforged.fml.common.EventBusSubscriber(modid = YujianCraft.MOD_ID, value = Dist.CLIENT)
public final class OptimizedThirdPersonController {
    private static final double SHOULDER_OFFSET = 0.95D;
    private static final double VERTICAL_OFFSET = 0.12D;
    private static final double THIRD_PERSON_DISTANCE = 4.0D;
    private static final double CRAMPED_DISTANCE = 1.05D;
    private static final double BALLISTIC_CONVERGENCE_RANGE = 512.0D;
    private static final double MINIMUM_CONVERGENCE_DISTANCE = 2.0D;
    private static final double BALLISTIC_PARALLAX_RESPONSE = 0.18D;
    private static double cameraDistanceFactor = 1.0D;
    private static boolean pseudoFirstPerson;
    private static Entity highlightedEntity;
    private static boolean highlightedEntityWasGlowing;
    private static int aimedLivingEntityId = -1;
    private static double ballisticInverseDistance;
    private static boolean ballisticConvergenceActive;

    private OptimizedThirdPersonController() {
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = event.getCamera();
        if (!isActive(minecraft) || !(camera.getEntity() instanceof Player player)
                || minecraft.level == null) {
            resetVisualState();
            return;
        }

        Vec3 eye = player.getEyePosition((float) event.getPartialTick());
        Vec3 realLook = player.getViewVector((float) event.getPartialTick()).normalize();
        float playerYaw = Mth.rotLerp((float) event.getPartialTick(), player.yRotO, player.getYRot());
        // Derive the shoulder axis from the player rather than the camera. TACZ convergence toes
        // the camera inward, and feeding that adjusted camera axis back into its own position on
        // the next frame would otherwise cause a small lateral drift.
        Vec3 right = Vec3.directionFromRotation(0.0F, playerYaw + 90.0F).normalize();
        Vec3 desiredTrack = realLook.scale(-THIRD_PERSON_DISTANCE)
                .add(right.scale(SHOULDER_OFFSET)).add(0.0D, VERTICAL_OFFSET, 0.0D);
        double safeFactor = safeCameraDistanceFactor(player, eye, desiredTrack);
        cameraDistanceFactor = safeFactor < cameraDistanceFactor
                ? safeFactor : Mth.lerp(0.16D, cameraDistanceFactor, safeFactor);
        Vec3 cameraPosition = eye.add(desiredTrack.scale(cameraDistanceFactor));
        camera.setPosition(cameraPosition);
        pseudoFirstPerson = cameraPosition.distanceTo(eye) < CRAMPED_DISTANCE;

        Vec3 screenDirection = ClientModCompatibility.isEpicFightLoaded()
                ? Vec3.directionFromRotation(event.getPitch(), event.getYaw()).normalize()
                : screenDirection(player, eye, cameraPosition, realLook, true);
        if (usesBallisticShoulderConvergence(player)) {
            float ballisticYaw = (float) (Mth.atan2(-realLook.x, realLook.z) * Mth.RAD_TO_DEG);
            float ballisticPitch = (float) (Mth.atan2(-realLook.y, realLook.horizontalDistance())
                    * Mth.RAD_TO_DEG);
            float convergedYaw = (float) (Mth.atan2(-screenDirection.x, screenDirection.z) * Mth.RAD_TO_DEG);
            float convergedPitch = (float) (Mth.atan2(-screenDirection.y,
                    screenDirection.horizontalDistance()) * Mth.RAD_TO_DEG);
            // Add only the shoulder-parallax correction. TACZ and other camera handlers may
            // already have authored recoil/walk sway in the event; replacing the absolute angles
            // made those effects fight this controller during movement.
            event.setYaw(event.getYaw() + Mth.wrapDegrees(convergedYaw - ballisticYaw));
            event.setPitch(event.getPitch() + convergedPitch - ballisticPitch);
            screenDirection = Vec3.directionFromRotation(event.getPitch(), event.getYaw()).normalize();
        }
        updateScreenCenterAim(minecraft, player, camera, (float) event.getPartialTick(), screenDirection);
    }

    private static double safeCameraDistanceFactor(Player player, Vec3 eye, Vec3 track) {
        if (isCameraPositionClear(player, eye, track, 1.0D)) return 1.0D;
        double lower = 0.0D;
        double upper = 1.0D;
        for (int step = 0; step < 10; step++) {
            double candidate = (lower + upper) * 0.5D;
            if (isCameraPositionClear(player, eye, track, candidate)) lower = candidate;
            else upper = candidate;
        }
        return lower;
    }

    private static boolean isCameraPositionClear(Player player, Vec3 eye, Vec3 track, double factor) {
        Vec3 position = eye.add(track.scale(factor));
        AABB cameraBox = new AABB(position.x - 0.10D, position.y - 0.10D, position.z - 0.10D,
                position.x + 0.10D, position.y + 0.10D, position.z + 0.10D);
        return player.level().noCollision(player, cameraBox)
                && unobstructed(player, eye, position);
    }

    private static boolean unobstructed(Player player, Vec3 start, Vec3 end) {
        BlockHitResult hit = player.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS
                || hit.getLocation().distanceTo(end) <= 0.12D;
    }

    private static void updateScreenCenterAim(Minecraft minecraft, Player player, Camera camera, float partialTick,
                                              Vec3 suppliedScreenDirection) {
        if (minecraft.player == null || minecraft.level == null || minecraft.gameMode == null) return;
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 screenDirection = suppliedScreenDirection.lengthSqr() < 1.0E-6D
                ? player.getViewVector(partialTick).normalize() : suppliedScreenDirection.normalize();
        Vec3 screenOrigin = camera.getPosition();
        double eyeToCamera = eye.distanceTo(screenOrigin);
        double interactionRange = Math.max(player.blockInteractionRange(), player.entityInteractionRange());
        double livingRange = ClientSettingsState.get().targetingMode()
                == dev.yujiancraft.combat.TargetingMode.MANUAL_GUIDANCE
                ? 512.0D : Math.max(interactionRange, ClientSettingsState.get().crosshairLockRadius());

        double interactionRayLength = interactionRange + eyeToCamera;
        Vec3 interactionEnd = screenOrigin.add(screenDirection.scale(interactionRayLength));
        BlockHitResult visualBlock = minecraft.level.clip(new ClipContext(screenOrigin, interactionEnd,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        double interactionMaximum = visualBlock.getType() == HitResult.Type.MISS
                ? interactionRayLength * interactionRayLength
                : screenOrigin.distanceToSqr(visualBlock.getLocation());
        EntityHitResult interactionEntity = ProjectileUtil.getEntityHitResult(player, screenOrigin, interactionEnd,
                new AABB(screenOrigin, interactionEnd).inflate(1.0D),
                entity -> isScreenRayCandidate(player, entity, interactionRange),
                interactionMaximum);
        if (interactionEntity != null && !isEntityHitReachable(player, eye, interactionEntity, interactionRange)) {
            interactionEntity = null;
        }

        BlockHitResult interactionBlock = isBlockHitReachable(player, eye, visualBlock, interactionRange)
                ? visualBlock : miss(interactionEnd, screenDirection);

        minecraft.crosshairPickEntity = interactionEntity == null ? null : interactionEntity.getEntity();
        minecraft.hitResult = interactionEntity == null ? interactionBlock : interactionEntity;

        double livingRayLength = livingRange + eyeToCamera;
        Vec3 livingEnd = screenOrigin.add(screenDirection.scale(livingRayLength));
        BlockHitResult occlusionBlock = minecraft.level.clip(new ClipContext(screenOrigin, livingEnd,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double livingMaximum = occlusionBlock.getType() == HitResult.Type.MISS
                ? livingRayLength * livingRayLength : screenOrigin.distanceToSqr(occlusionBlock.getLocation());
        EntityHitResult distantEntity = ProjectileUtil.getEntityHitResult(player, screenOrigin, livingEnd,
                new AABB(screenOrigin, livingEnd).inflate(1.0D),
                entity -> isScreenRayCandidate(player, entity, livingRange), livingMaximum);
        if (distantEntity != null && !isEntityHitReachable(player, eye, distantEntity, livingRange)) {
            distantEntity = null;
        }
        LivingEntity aimedLiving = distantEntity != null && distantEntity.getEntity() instanceof LivingEntity living
                && living.isAlive() ? living : null;
        aimedLivingEntityId = aimedLiving == null ? -1 : aimedLiving.getId();
        setHighlightedEntity(aimedLiving);
    }

    private static boolean isScreenRayCandidate(Player player, Entity entity, double playerRange) {
        double allowance = playerRange + Math.max(entity.getBbWidth(), entity.getBbHeight()) * 0.5D;
        return entity != player && !entity.isSpectator() && entity.isPickable()
                && entity.distanceToSqr(player) <= allowance * allowance;
    }

    private static boolean isEntityHitReachable(Player player, Vec3 eye, EntityHitResult hit, double playerRange) {
        return eye.distanceToSqr(hit.getLocation()) <= playerRange * playerRange
                && player.hasLineOfSight(hit.getEntity());
    }

    private static boolean isBlockHitReachable(Player player, Vec3 eye, BlockHitResult hit, double playerRange) {
        if (hit.getType() == HitResult.Type.MISS
                || eye.distanceToSqr(hit.getLocation()) > playerRange * playerRange) return false;
        BlockHitResult eyeTrace = player.level().clip(new ClipContext(eye, hit.getLocation(),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return eyeTrace.getType() == HitResult.Type.MISS
                || eyeTrace.getBlockPos().equals(hit.getBlockPos());
    }

    private static BlockHitResult miss(Vec3 end, Vec3 direction) {
        return BlockHitResult.miss(end, Direction.getNearest(direction.x, direction.y, direction.z),
                BlockPos.containing(end));
    }

    public static int getAimedLivingEntityId() {
        return aimedLivingEntityId;
    }

    /** Refreshes the same screen-center ray immediately before an input consumes hitResult. */
    public static boolean refreshScreenCenterHit() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isActive(minecraft) || minecraft.level == null || minecraft.gameMode == null
                || !(minecraft.gameRenderer.getMainCamera().getEntity() instanceof Player player)) return false;
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 cameraLook = Vec3.directionFromRotation(camera.getXRot(), camera.getYRot()).normalize();
        updateScreenCenterAim(minecraft, player, camera, 1.0F, cameraLook);
        return true;
    }

    /**
     * TACZ launches from the shooter using player yaw/pitch. A shoulder camera using the same
     * direction produces two parallel rays, so its centre reticle appears to the right of the
     * actual impact. Toe the camera toward the first point on the real ballistic ray instead of
     * mutating player rotation or TACZ state; this keeps recoil, spread and server authority intact.
     */
    private static Vec3 screenDirection(Player player, Vec3 eye, Vec3 cameraPosition, Vec3 playerLook,
                                        boolean advanceSmoothing) {
        if (!usesBallisticShoulderConvergence(player)) {
            ballisticConvergenceActive = false;
            ballisticInverseDistance = 0.0D;
            return playerLook;
        }
        Vec3 end = eye.add(playerLook.scale(BALLISTIC_CONVERGENCE_RANGE));
        BlockHitResult blockHit = player.level().clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double maximumDistance = blockHit.getType() == HitResult.Type.MISS
                ? eye.distanceToSqr(end) : eye.distanceToSqr(blockHit.getLocation());
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, end,
                new AABB(eye, end).inflate(1.0D),
                entity -> isScreenRayCandidate(player, entity, BALLISTIC_CONVERGENCE_RANGE),
                maximumDistance);
        Vec3 rawConvergence = entityHit != null ? entityHit.getLocation()
                : blockHit.getType() == HitResult.Type.BLOCK ? blockHit.getLocation() : end;
        double desiredDistance = Mth.clamp(eye.distanceTo(rawConvergence),
                MINIMUM_CONVERGENCE_DISTANCE, BALLISTIC_CONVERGENCE_RANGE);
        double desiredInverseDistance = 1.0D / desiredDistance;
        if (!ballisticConvergenceActive) {
            ballisticInverseDistance = desiredInverseDistance;
            ballisticConvergenceActive = true;
        } else if (advanceSmoothing) {
            // Smooth parallax rather than metres. Crossing the horizon changes the raw trace from
            // a nearby floor hit to the 512-block miss point in one frame; inverse-distance
            // smoothing removes that discontinuity while preserving close-range convergence.
            ballisticInverseDistance = Mth.lerp(BALLISTIC_PARALLAX_RESPONSE,
                    ballisticInverseDistance, desiredInverseDistance);
        }
        double convergenceDistance = 1.0D / Math.max(1.0D / BALLISTIC_CONVERGENCE_RANGE,
                ballisticInverseDistance);
        Vec3 convergence = eye.add(playerLook.scale(convergenceDistance));
        Vec3 converged = convergence.subtract(cameraPosition);
        return converged.lengthSqr() < 1.0E-6D ? playerLook : converged.normalize();
    }

    /** Optional TACZ detection without a compile-time dependency. */
    private static boolean usesBallisticShoulderConvergence(Player player) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return false;
        String namespace = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        String implementation = stack.getItem().getClass().getName();
        return "tacz".equals(namespace) || implementation.startsWith("com.tacz.");
    }

    private static void setHighlightedEntity(Entity entity) {
        // Epic Fight owns its target indicator/highlight state. Sharing vanilla's glowing flag
        // lets either mod accidentally clear the other one's marker, so keep Yujian's aim data
        // but relinquish the visual flag when Epic Fight is installed.
        if (ClientModCompatibility.isEpicFightLoaded()) {
            clearHighlightedEntity();
            return;
        }
        if (highlightedEntity == entity) return;
        clearHighlightedEntity();
        highlightedEntity = entity;
        if (entity != null) {
            highlightedEntityWasGlowing = entity.isCurrentlyGlowing();
            entity.setSharedFlag(6, true);
        }
    }

    private static void clearHighlightedEntity() {
        if (highlightedEntity != null && !highlightedEntityWasGlowing) {
            highlightedEntity.setSharedFlag(6, false);
        }
        highlightedEntity = null;
        highlightedEntityWasGlowing = false;
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (pseudoFirstPerson && event.getEntity() == minecraft.player) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onBlockHighlight(RenderHighlightEvent.Block event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isActive(minecraft) || minecraft.level == null) return;
        BlockPos pos = event.getTarget().getBlockPos();
        VoxelShape shape = minecraft.level.getBlockState(pos).getShape(minecraft.level, pos,
                CollisionContext.of(event.getCamera().getEntity()));
        Vec3 camera = event.getCamera().getPosition();
        VertexConsumer lines = event.getMultiBufferSource().getBuffer(RenderType.lines());
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> LevelRenderer.renderLineBox(
                event.getPoseStack(), lines,
                pos.getX() + minX - camera.x, pos.getY() + minY - camera.y, pos.getZ() + minZ - camera.z,
                pos.getX() + maxX - camera.x, pos.getY() + maxY - camera.y, pos.getZ() + maxZ - camera.z,
                1.0F, 1.0F, 1.0F, 0.9F));
    }

    public static void renderCrosshair(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isActive(minecraft) || minecraft.options.hideGui || minecraft.screen != null) return;
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        int x = screenWidth / 2;
        int y = screenHeight / 2;
        int color = aimedLivingEntityId >= 0
                || minecraft.hitResult != null && minecraft.hitResult.getType() != HitResult.Type.MISS
                ? 0xFFFFFFFF : 0xFFC9D2D8;
        graphics.fill(x - 5, y + 1, x - 1, y + 2, 0x90000000);
        graphics.fill(x + 2, y + 1, x + 6, y + 2, 0x90000000);
        graphics.fill(x + 1, y - 5, x + 2, y - 1, 0x90000000);
        graphics.fill(x + 1, y + 2, x + 2, y + 6, 0x90000000);
        graphics.fill(x - 5, y, x - 1, y + 1, color);
        graphics.fill(x + 2, y, x + 6, y + 1, color);
        graphics.fill(x, y - 5, x + 1, y - 1, color);
        graphics.fill(x, y + 2, x + 1, y + 6, color);
    }

    private static boolean isActive(Minecraft minecraft) {
        return ClientOptions.optimizedThirdPerson()
                && ClientModCompatibility.mayUseYujianThirdPersonCamera()
                && minecraft.options.getCameraType() == CameraType.THIRD_PERSON_BACK;
    }

    private static void resetVisualState() {
        cameraDistanceFactor = 1.0D;
        pseudoFirstPerson = false;
        aimedLivingEntityId = -1;
        ballisticConvergenceActive = false;
        ballisticInverseDistance = 0.0D;
        clearHighlightedEntity();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        resetVisualState();
    }
}
