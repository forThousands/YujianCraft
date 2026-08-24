package dev.yujiancraft.client;

import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import dev.yujiancraft.item.FlyingSwordItem;
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
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = YujianCraft.MOD_ID, value = Dist.CLIENT)
public final class ClientInputEvents {
    private static final long SWORD_RIDING_DOUBLE_TAP_MS = 350L;
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
            minecraft.setScreen(new YujianCraftConfigScreen(minecraft.screen));
        }
        while (ClientModEvents.TOGGLE_SWORDS.consumeClick()) {
            ModNetwork.CHANNEL.sendToServer(new ModNetwork.ToggleSummonedSwordsPacket());
        }
        while (ClientModEvents.ARTIFACT_ACTION.consumeClick()) {
            Minecraft minecraft = Minecraft.getInstance();
            OptimizedThirdPersonController.refreshScreenCenterHit();
            ModNetwork.ArtifactActionPacket packet = minecraft.hitResult instanceof BlockHitResult hit
                    && hit.getType() == HitResult.Type.BLOCK
                    ? new ModNetwork.ArtifactActionPacket(true, hit.getBlockPos(), hit.getDirection())
                    : ModNetwork.ArtifactActionPacket.miss();
            ModNetwork.CHANNEL.sendToServer(packet);
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getAction() == GLFW.GLFW_PRESS && minecraft.screen == null && minecraft.player != null
                && ClientOptions.swordRidingEnabled()
                && hasFlyingSword(minecraft.player)
                && minecraft.options.keyJump.matches(event.getKey(), event.getScanCode())) {
            long now = net.minecraft.Util.getMillis();
            if (lastJumpPressMillis >= 0L && now >= lastJumpPressMillis
                    && now - lastJumpPressMillis <= SWORD_RIDING_DOUBLE_TAP_MS) {
                ModNetwork.CHANNEL.sendToServer(new ModNetwork.ToggleSwordRidingPacket(
                        !ClientSwordRidingState.isActive()));
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
        if (minecraft.player != null && minecraft.screen == null && ClientManualGuidanceState.isGuiding()
                && FlyingSwordItem.isUsableFlyingSword(minecraft.player.getMainHandItem())
                && ClientSettingsState.get().techniqueMode()
                == dev.yujiancraft.combat.technique.TechniqueMode.PIERCE) {
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
                && minecraft.options.getCameraType() == net.minecraft.client.CameraType.THIRD_PERSON_BACK) {
            OptimizedThirdPersonController.refreshScreenCenterHit();
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
        lastJumpPressMillis = -1L;
    }
}
