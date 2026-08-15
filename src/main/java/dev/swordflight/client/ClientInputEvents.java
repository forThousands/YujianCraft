package dev.swordflight.client;

import dev.swordflight.Swordflight;
import dev.swordflight.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import dev.swordflight.item.FlyingSwordItem;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = Swordflight.MOD_ID, value = Dist.CLIENT)
public final class ClientInputEvents {
    private static final long SWORD_RIDING_DOUBLE_TAP_MS = 350L;
    private static int aimSyncCountdown;
    private static int manualAimSyncCountdown;
    private static boolean blockAttackHandledThisTick;
    private static long lastJumpPressMillis = -1L;
    private ClientInputEvents() {
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        while (ClientModEvents.SWITCH_FORMATION.consumeClick()) {
            ModNetwork.CHANNEL.sendToServer(new ModNetwork.ToggleFormationPacket());
        }
        while (ClientModEvents.OPEN_CONFIG.consumeClick()) {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(new SwordflightConfigScreen(minecraft.screen));
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getAction() == GLFW.GLFW_PRESS && minecraft.screen == null && minecraft.player != null
                && ClientOptions.swordRidingEnabled()
                && minecraft.player.getMainHandItem().getItem() instanceof FlyingSwordItem
                && minecraft.options.keyJump.matches(event.getKey(), event.getScanCode())) {
            long now = net.minecraft.Util.getMillis();
            if (lastJumpPressMillis >= 0L && now >= lastJumpPressMillis
                    && now - lastJumpPressMillis <= SWORD_RIDING_DOUBLE_TAP_MS) {
                ModNetwork.CHANNEL.sendToServer(new ModNetwork.ToggleSwordRidingPacket());
                lastJumpPressMillis = -1L;
            } else {
                lastJumpPressMillis = now;
            }
        }
    }

    @SubscribeEvent
    public static void onInteractionInput(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean optimizedAim = OptimizedThirdPersonController.refreshScreenCenterHit();
        if (event.isAttack() && minecraft.player != null
                && minecraft.player.getMainHandItem().getItem() instanceof FlyingSwordItem) {
            event.setCanceled(true);
            if (ClientSettingsState.get().targetingMode()
                    == dev.swordflight.combat.TargetingMode.MANUAL_GUIDANCE) {
                ModNetwork.CHANNEL.sendToServer(new ModNetwork.ManualLaunchPacket(
                        minecraft.player.getViewVector(1.0F)));
                return;
            }
            int targetId = ClientOptions.optimizedThirdPerson()
                    ? OptimizedThirdPersonController.getAimedLivingEntityId() : -1;
            if (targetId < 0 && minecraft.hitResult instanceof EntityHitResult entityHit) {
                targetId = entityHit.getEntity().getId();
            }
            ModNetwork.CHANNEL.sendToServer(new ModNetwork.LockCrosshairNowPacket(targetId));
        } else if (event.isUseItem() && minecraft.player != null
                && minecraft.player.getMainHandItem().getItem() instanceof FlyingSwordItem
                && !minecraft.player.isShiftKeyDown()
                && ClientSettingsState.get().targetingMode()
                == dev.swordflight.combat.TargetingMode.MANUAL_GUIDANCE
                && ClientManualGuidanceState.isGuiding()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            ModNetwork.CHANNEL.sendToServer(new ModNetwork.ManualLockPacket(findManualTargetId(minecraft)));
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
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            blockAttackHandledThisTick = false;
            return;
        }
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
        if (minecraft.player == null || !ClientOptions.optimizedThirdPerson()
                || minecraft.options.getCameraType() != net.minecraft.client.CameraType.THIRD_PERSON_BACK
                || ClientSettingsState.get().targetingMode()
                        != dev.swordflight.combat.TargetingMode.CROSSHAIR_LOCK
                || !hasFlyingSword(minecraft.player)) {
            aimSyncCountdown = 0;
        } else if (aimSyncCountdown-- <= 0) {
            aimSyncCountdown = 2;
            int targetId = OptimizedThirdPersonController.getAimedLivingEntityId();
            ModNetwork.CHANNEL.sendToServer(new ModNetwork.ClientAimTargetPacket(targetId));
        }

        if (minecraft.player != null && minecraft.screen == null && ClientManualGuidanceState.isGuiding()
                && minecraft.player.getMainHandItem().getItem() instanceof FlyingSwordItem) {
            if (manualAimSyncCountdown-- <= 0) {
                manualAimSyncCountdown = 1;
                ModNetwork.CHANNEL.sendToServer(new ModNetwork.ManualAimPacket(
                        minecraft.player.getViewVector(1.0F)));
            }
        } else {
            manualAimSyncCountdown = 0;
        }
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

    private static boolean hasFlyingSword(net.minecraft.world.entity.player.Player player) {
        if (player.getMainHandItem().getItem() instanceof FlyingSwordItem
                || player.getOffhandItem().getItem() instanceof FlyingSwordItem) return true;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).getItem() instanceof FlyingSwordItem) return true;
        }
        return false;
    }

    private static int findManualTargetId(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) return -1;
        if (ClientOptions.optimizedThirdPerson()
                && minecraft.options.getCameraType() == net.minecraft.client.CameraType.THIRD_PERSON_BACK) {
            OptimizedThirdPersonController.refreshScreenCenterHit();
            return OptimizedThirdPersonController.getAimedLivingEntityId();
        }
        if (minecraft.hitResult instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof Mob mob && mob.isAlive()) return mob.getId();

        Vec3 start = minecraft.player.getEyePosition(1.0F);
        Vec3 direction = minecraft.player.getViewVector(1.0F).normalize();
        Vec3 end = start.add(direction.scale(512.0D));
        BlockHitResult blockHit = minecraft.level.clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, minecraft.player));
        double maximumDistance = blockHit.getType() == HitResult.Type.MISS
                ? start.distanceToSqr(end) : start.distanceToSqr(blockHit.getLocation());
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(minecraft.player, start, end,
                new AABB(start, end).inflate(1.0D),
                entity -> entity instanceof Mob mob && mob.isAlive() && !mob.isSpectator(), maximumDistance);
        return entityHit == null ? -1 : entityHit.getEntity().getId();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientManualGuidanceState.setGuiding(false);
        ClientSwordRidingState.setActive(false);
        lastJumpPressMillis = -1L;
    }
}
