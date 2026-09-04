package dev.yujiancraft.client;

import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.network.ModNetwork;
import dev.yujiancraft.client.vfx.VfxLivePreviewBridge;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import dev.yujiancraft.item.FlyingSwordItem;
import dev.yujiancraft.entity.FlyingSwordEntity;
import dev.yujiancraft.combat.technique.TechniqueMode;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.lwjgl.glfw.GLFW;

@net.neoforged.fml.common.EventBusSubscriber(modid = YujianCraft.MOD_ID, value = Dist.CLIENT)
public final class ClientInputEvents {
    private static final long SWORD_RIDING_DOUBLE_TAP_MS = 350L;
    private static int manualAimSyncCountdown;
    private static boolean blockAttackHandledThisTick;
    private static boolean epicFightAttackHandledThisTick;
    private static boolean epicFightLockHandledThisTick;
    private static long lastJumpPressMillis = -1L;
    private static boolean heldFlyingSwordLastTick;
    private static boolean formationPresentLastTick;
    private static boolean formationSynced;
    private static boolean comboActiveLastTick;
    private static TechniqueMode techniqueLastTick;
    private static int pendingSwordArrayHintTicks = -1;
    private ClientInputEvents() {
    }

    /** Server-confirmed formation lifecycle; entity-list scans are frustum-dependent and unreliable. */
    public static void onFormationState(boolean deployed) {
        formationSynced = deployed;
        formationPresentLastTick = deployed;
        if (deployed) {
            ClientTechniqueOverlayState.showFormationControls(
                    ClientModEvents.OPEN_QUICK_SWITCH.getTranslatedKeyMessage(),
                    ClientModEvents.TOGGLE_COMBO.getTranslatedKeyMessage(),
                    ClientModEvents.SWITCH_TECHNIQUE.getTranslatedKeyMessage());
        } else {
            ClientTechniqueOverlayState.clearControlGuide();
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (VfxLivePreviewBridge.isAvailable()) {
            while (ClientModEvents.RELEASE_VFX_CURSOR.consumeClick()) {
                VfxLivePreviewBridge.toggleCursorCapture();
            }
        }
        while (ClientModEvents.SWITCH_FORMATION.consumeClick()) {
            ModNetwork.sendToServer(new ModNetwork.ToggleFormationPacket());
        }
        while (ClientModEvents.OPEN_CONFIG.consumeClick()) {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(new YujianCraftConfigScreen(minecraft.screen));
        }
        while (ClientModEvents.OPEN_QUICK_SWITCH.consumeClick()) {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(new YujianQuickSwitchScreen());
        }
        while (ClientModEvents.TOGGLE_SWORDS.consumeClick()) {
            ModNetwork.sendToServer(new ModNetwork.ToggleSummonedSwordsPacket());
        }
        while (ClientModEvents.ARTIFACT_ACTION.consumeClick()) {
            Minecraft minecraft = Minecraft.getInstance();
            OptimizedThirdPersonController.refreshScreenCenterHit();
            ModNetwork.sendToServer(contextualActionHit(minecraft));
        }
        while (ClientModEvents.SWITCH_TECHNIQUE.consumeClick()) {
            ModNetwork.sendToServer(new ModNetwork.CycleTechniquePacket());
        }
        while (ClientModEvents.ACTIVATE_SWORD_ARRAY.consumeClick()) {
            ModNetwork.sendToServer(new ModNetwork.ActivateSwordArrayPacket(
                    findSwordArrayTargetId(Minecraft.getInstance())));
        }
        while (ClientModEvents.SWITCH_SWORD_ARRAY_STYLE.consumeClick()) {
            ModNetwork.sendToServer(new ModNetwork.ToggleSwordArrayStylePacket());
        }
        while (ClientModEvents.CYCLE_COMBO_STYLE.consumeClick()) {
            ModNetwork.sendToServer(new ModNetwork.CycleComboStylePacket());
        }
        while (ClientModEvents.TOGGLE_COMBO.consumeClick()) {
            ModNetwork.sendToServer(new ModNetwork.ToggleComboPacket());
        }
        while (ClientModEvents.TOGGLE_TARGET_PROTECTION.consumeClick()) {
            ModNetwork.sendToServer(new ModNetwork.ToggleTargetProtectionPacket(
                    findManualTargetId(Minecraft.getInstance())));
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean jumpPressed = event.getAction() == GLFW.GLFW_PRESS && minecraft.screen == null
                && minecraft.player != null
                && minecraft.options.keyJump.matches(event.getKey(), event.getScanCode());
        boolean hasRidingSword = jumpPressed && hasFlyingSword(minecraft.player);
        if (jumpPressed && !hasRidingSword) {
            // Without a usable sword, leave the jump gesture and all flight state to vanilla.
            lastJumpPressMillis = -1L;
            return;
        }
        if (jumpPressed && ClientOptions.swordRidingEnabled() && hasRidingSword) {
            long now = net.minecraft.Util.getMillis();
            if (lastJumpPressMillis >= 0L && now >= lastJumpPressMillis
                    && now - lastJumpPressMillis <= SWORD_RIDING_DOUBLE_TAP_MS) {
                ModNetwork.sendToServer(new ModNetwork.ToggleSwordRidingPacket(
                        !ClientSwordRidingState.isActive()));
                lastJumpPressMillis = -1L;
            } else {
                lastJumpPressMillis = now;
            }
        }
    }

    /** G uses the configurable lock distance rather than vanilla's short block-pick distance. */
    private static ModNetwork.ArtifactActionPacket contextualActionHit(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) return ModNetwork.ArtifactActionPacket.miss();
        Vec3 eye = minecraft.player.getEyePosition(1.0F);
        Vec3 origin = minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 direction = minecraft.player.getViewVector(1.0F).normalize();
        double range = Math.max(2.0D, ClientSettingsState.get().crosshairLockRadius());
        Vec3 end = origin.add(direction.scale(range + origin.distanceTo(eye)));
        ClipContext.Fluid fluid = ClientSettingsState.get().techniqueMode()
                == dev.yujiancraft.combat.technique.TechniqueMode.SPIRIT_FISHING
                ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE;
        BlockHitResult hit = minecraft.level.clip(new ClipContext(origin, end,
                ClipContext.Block.OUTLINE, fluid, minecraft.player));
        return hit.getType() == HitResult.Type.BLOCK
                ? new ModNetwork.ArtifactActionPacket(true, hit.getBlockPos(), hit.getDirection())
                : ModNetwork.ArtifactActionPacket.miss();
    }

    /**
     * Epic Fight consumes the vanilla attack KeyMapping click while it is in battle mode, before
     * Forge can emit InteractionKeyMappingTriggered. Capture the physical attack edge first so
     * Yujian combo input remains independent of Epic Fight's combat/targeting pipeline.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onMouseButtonPre(InputEvent.MouseButton.Pre event) {
        if (!ClientModCompatibility.isEpicFightLoaded() || event.getAction() != GLFW.GLFW_PRESS) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null || minecraft.level == null
                || !minecraft.options.keyAttack.matchesMouse(event.getButton())) return;

        if (ClientComboState.isLocalActive()) {
            sendComboAttack(minecraft);
            epicFightAttackHandledThisTick = true;
            if (shouldShowHeldItemEmptySwing(minecraft)) {
                minecraft.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            }
            // Combo stance deliberately owns attack input. Prevent Epic Fight and vanilla from
            // applying a second attack for the same physical click. A non-flying held item may
            // still show its harmless empty-space swing above.
            event.setCanceled(true);
            return;
        }
        if (FlyingSwordItem.isUsableFlyingSword(minecraft.player.getMainHandItem())) {
            ModNetwork.sendToServer(new ModNetwork.LockCrosshairNowPacket(
                    findSwordArrayTargetId(minecraft)));
            epicFightLockHandledThisTick = true;
            // Do not cancel: outside combo stance Epic Fight's normal attack remains available.
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onInteractionInput(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean optimizedAim = OptimizedThirdPersonController.refreshScreenCenterHit();
        if (event.isAttack() && minecraft.player != null && ClientComboState.isLocalActive()) {
            event.setCanceled(true);
            event.setSwingHand(shouldShowHeldItemEmptySwing(minecraft));
            if (!epicFightAttackHandledThisTick) sendComboAttack(minecraft);
            return;
        }
        if (event.isAttack() && minecraft.player != null
                && FlyingSwordItem.isUsableFlyingSword(minecraft.player.getMainHandItem())) {
            boolean directLivingAttack = minecraft.hitResult instanceof EntityHitResult entityHit
                    && entityHit.getEntity() instanceof LivingEntity living && living.isAlive();
            if (ClientSettingsState.get().targetingMode()
                    == dev.yujiancraft.combat.TargetingMode.MANUAL_GUIDANCE
                    && ClientSettingsState.get().techniqueMode()
                    == dev.yujiancraft.combat.technique.TechniqueMode.PIERCE
                    && !directLivingAttack) {
                // Empty-space/block attack keeps the manual-guidance launch gesture. Clicking a
                // living entity always remains an ordinary vanilla melee attack.
                event.setCanceled(true);
                ModNetwork.sendToServer(new ModNetwork.ManualLaunchPacket(
                        minecraft.player.getViewVector(1.0F)));
                return;
            }
            int targetId = optimizedAim
                    ? OptimizedThirdPersonController.getAimedLivingEntityId() : -1;
            if (targetId < 0 && minecraft.hitResult instanceof EntityHitResult entityHit) {
                targetId = entityHit.getEntity().getId();
            }
            if (!epicFightLockHandledThisTick) {
                ModNetwork.sendToServer(new ModNetwork.LockCrosshairNowPacket(targetId));
            }
            // Do not cancel: the same click must still swing, attack an entity or mine a block.
        } else if (event.isUseItem() && minecraft.player != null
                && FlyingSwordItem.isUsableFlyingSword(minecraft.player.getMainHandItem())
                && !minecraft.player.isShiftKeyDown()
                && ClientSettingsState.get().targetingMode()
                == dev.yujiancraft.combat.TargetingMode.MANUAL_GUIDANCE
                && ClientSettingsState.get().techniqueMode()
                == dev.yujiancraft.combat.technique.TechniqueMode.PIERCE
                && ClientManualGuidanceState.isGuiding()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            ModNetwork.sendToServer(new ModNetwork.ManualLockPacket(findManualTargetId(minecraft)));
        } else if (event.isAttack() && optimizedAim
                && minecraft.hitResult instanceof BlockHitResult blockHit) {
            // startAttack reads hitResult after this event, but continueAttack captured the vanilla
            // player-ray block before firing it. Own both paths so they cannot diverge.
            event.setCanceled(true);
            event.setSwingHand(false);
            handleOptimizedBlockAttack(minecraft, blockHit);
            blockAttackHandledThisTick = true;
        }
    }

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        blockAttackHandledThisTick = false;
        epicFightAttackHandledThisTick = false;
        epicFightLockHandledThisTick = false;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        // continueAttack does not fire the Forge interaction event when the vanilla player ray
        // misses. Supply exactly one screen-ray mining update in that case.
        if (!blockAttackHandledThisTick && minecraft.player != null && minecraft.gameMode != null
                && minecraft.screen == null && minecraft.options.keyAttack.isDown()
                && !minecraft.player.isUsingItem()
                && OptimizedThirdPersonController.refreshScreenCenterHit()
                && minecraft.hitResult instanceof BlockHitResult blockHit) {
            handleOptimizedBlockAttack(minecraft, blockHit);
            blockAttackHandledThisTick = true;
        }
        if (minecraft.player != null && minecraft.screen == null && ClientManualGuidanceState.isGuiding()
                && FlyingSwordItem.isUsableFlyingSword(minecraft.player.getMainHandItem())
                && ClientSettingsState.get().techniqueMode()
                == dev.yujiancraft.combat.technique.TechniqueMode.PIERCE) {
            if (manualAimSyncCountdown-- <= 0) {
                manualAimSyncCountdown = 1;
                ModNetwork.sendToServer(new ModNetwork.ManualAimPacket(
                        minecraft.player.getViewVector(1.0F)));
            }
        } else {
            manualAimSyncCountdown = 0;
        }
        updateContextualGuidance(minecraft);
    }

    private static void handleOptimizedBlockAttack(Minecraft minecraft, BlockHitResult hit) {
        if (minecraft.player == null || minecraft.level == null || minecraft.gameMode == null
                || hit.getType() != HitResult.Type.BLOCK
                || minecraft.level.isEmptyBlock(hit.getBlockPos())) return;
        boolean showEffects;
        if (minecraft.gameMode.isDestroying()) {
            showEffects = minecraft.gameMode.continueDestroyBlock(hit.getBlockPos(), hit.getDirection());
        } else {
            showEffects = minecraft.gameMode.startDestroyBlock(hit.getBlockPos(), hit.getDirection());
        }
        if (showEffects) {
            minecraft.particleEngine.addBlockHitEffects(hit.getBlockPos(), hit);
            minecraft.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
    }

    /** Keeps ordinary held items visually responsive without releasing combo-owned attack input. */
    private static boolean shouldShowHeldItemEmptySwing(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.hitResult == null
                || minecraft.hitResult.getType() != HitResult.Type.MISS) return false;
        var held = minecraft.player.getMainHandItem();
        return !held.isEmpty() && !FlyingSwordItem.isUsableFlyingSword(held);
    }

    private static boolean hasFlyingSword(net.minecraft.world.entity.player.Player player) {
        if (FlyingSwordItem.isUsableFlyingSword(player.getMainHandItem())
                || FlyingSwordItem.isUsableFlyingSword(player.getOffhandItem())) return true;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (FlyingSwordItem.isUsableFlyingSword(player.getInventory().getItem(slot))) return true;
        }
        return false;
    }

    private static int findManualTargetId(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) return -1;
        if (ClientOptions.optimizedThirdPerson()
                && minecraft.options.getCameraType() == net.minecraft.client.CameraType.THIRD_PERSON_BACK
                && OptimizedThirdPersonController.refreshScreenCenterHit()) {
            return OptimizedThirdPersonController.getAimedLivingEntityId();
        }
        if (minecraft.hitResult instanceof EntityHitResult entityHit
                && isPotentialSwordTarget(minecraft.player, entityHit.getEntity())) {
            return entityHit.getEntity().getId();
        }

        Vec3 start = minecraft.player.getEyePosition(1.0F);
        Vec3 direction = minecraft.player.getViewVector(1.0F).normalize();
        Vec3 end = start.add(direction.scale(512.0D));
        BlockHitResult blockHit = minecraft.level.clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, minecraft.player));
        double maximumDistance = blockHit.getType() == HitResult.Type.MISS
                ? start.distanceToSqr(end) : start.distanceToSqr(blockHit.getLocation());
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(minecraft.player, start, end,
                new AABB(start, end).inflate(1.0D),
                entity -> isPotentialSwordTarget(minecraft.player, entity), maximumDistance);
        return entityHit == null ? -1 : entityHit.getEntity().getId();
    }

    /** H is a direct aim-and-cast gesture: it never mutates the persistent target lock. */
    private static int findSwordArrayTargetId(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) return -1;
        double range = Math.max(2.0D, ClientSettingsState.get().crosshairLockRadius());
        if (ClientOptions.optimizedThirdPerson()
                && minecraft.options.getCameraType() == net.minecraft.client.CameraType.THIRD_PERSON_BACK
                && OptimizedThirdPersonController.refreshScreenCenterHit()) {
            int targetId = OptimizedThirdPersonController.getAimedLivingEntityId();
            Entity aimed = minecraft.level.getEntity(targetId);
            if (aimed != null && isPotentialSwordTarget(minecraft.player, aimed)
                    && minecraft.player.distanceToSqr(aimed) <= Math.pow(range + aimed.getBbWidth(), 2.0D)) {
                return targetId;
            }
        }
        Vec3 start = minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 direction = currentAimDirection(minecraft);
        Vec3 end = start.add(direction.scale(range));
        BlockHitResult blockHit = minecraft.level.clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, minecraft.player));
        double maximumDistance = blockHit.getType() == HitResult.Type.MISS
                ? start.distanceToSqr(end) : start.distanceToSqr(blockHit.getLocation());
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(minecraft.player, start, end,
                new AABB(start, end).inflate(1.0D),
                entity -> isPotentialSwordTarget(minecraft.player, entity), maximumDistance);
        return entityHit == null ? -1 : entityHit.getEntity().getId();
    }

    private static void sendComboAttack(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) return;
        Vec3 look = currentAimDirection(minecraft);
        ModNetwork.sendToServer(new ModNetwork.ComboAttackPacket(
                findSwordArrayTargetId(minecraft), look));
    }

    /** Camera direction remains correct for Epic Fight's decoupled camera and shoulder cameras. */
    private static Vec3 currentAimDirection(Minecraft minecraft) {
        net.minecraft.client.Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 direction = Vec3.directionFromRotation(camera.getXRot(), camera.getYRot());
        if (direction.lengthSqr() < 1.0E-6D && minecraft.player != null) {
            direction = minecraft.player.getViewVector(1.0F);
        }
        return direction.normalize();
    }

    private static void updateContextualGuidance(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) return;
        boolean holding = FlyingSwordItem.isUsableFlyingSword(minecraft.player.getMainHandItem());
        // Formation presence comes from an explicit server acknowledgement. Scanning
        // entitiesForRendering() is frustum-dependent and can miss swords even while deployed.
        boolean formation = formationSynced;
        TechniqueMode technique = ClientSettingsState.get().techniqueMode();
        boolean comboActive = ClientComboState.isLocalActive();
        if (holding && !heldFlyingSwordLastTick) {
            minecraft.player.displayClientMessage(Component.translatable(
                    "message.yujiancraft.guide.toggle_formation",
                    ClientModEvents.TOGGLE_SWORDS.getTranslatedKeyMessage()), true);
        }
        if (formation && !formationPresentLastTick) {
            ClientTechniqueOverlayState.showFormationControls(
                    ClientModEvents.OPEN_QUICK_SWITCH.getTranslatedKeyMessage(),
                    ClientModEvents.TOGGLE_COMBO.getTranslatedKeyMessage(),
                    ClientModEvents.SWITCH_TECHNIQUE.getTranslatedKeyMessage());
            pendingSwordArrayHintTicks = technique == TechniqueMode.SWORD_ARRAY ? 70 : -1;
        }
        if (comboActive && !comboActiveLastTick) {
            ClientTechniqueOverlayState.showComboControls(
                    ClientModEvents.OPEN_QUICK_SWITCH.getTranslatedKeyMessage(),
                    ClientModEvents.CYCLE_COMBO_STYLE.getTranslatedKeyMessage());
        } else if (formation && technique == TechniqueMode.SWORD_ARRAY
                && techniqueLastTick != TechniqueMode.SWORD_ARRAY) {
            minecraft.player.displayClientMessage(Component.translatable(
                    "message.yujiancraft.guide.sword_array_cast",
                    ClientModEvents.ACTIVATE_SWORD_ARRAY.getTranslatedKeyMessage()), true);
            pendingSwordArrayHintTicks = -1;
        } else if (formation && technique == TechniqueMode.SWORD_ARRAY
                && pendingSwordArrayHintTicks > 0 && --pendingSwordArrayHintTicks == 0) {
            minecraft.player.displayClientMessage(Component.translatable(
                    "message.yujiancraft.guide.sword_array_cast",
                    ClientModEvents.ACTIVATE_SWORD_ARRAY.getTranslatedKeyMessage()), true);
            pendingSwordArrayHintTicks = -1;
        }
        if (!formation) {
            pendingSwordArrayHintTicks = -1;
            ClientTechniqueOverlayState.clearControlGuide();
        }
        heldFlyingSwordLastTick = holding;
        formationPresentLastTick = formation;
        comboActiveLastTick = comboActive;
        techniqueLastTick = technique;
    }

    private static boolean isPotentialSwordTarget(Player owner, Entity entity) {
        if (!(entity instanceof LivingEntity living) || living == owner
                || !living.isAlive() || living.isSpectator()) return false;
        // The server performs the authoritative PvP/team-friendly-fire validation.
        return living instanceof Mob || living instanceof Player;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientManualGuidanceState.setGuiding(false);
        ClientSwordRidingState.setActive(false);
        ClientComboState.clear();
        ClientQuickSwitchState.clear();
        lastJumpPressMillis = -1L;
        heldFlyingSwordLastTick = false;
        formationPresentLastTick = false;
        formationSynced = false;
        comboActiveLastTick = false;
        techniqueLastTick = null;
        pendingSwordArrayHintTicks = -1;
    }
}
