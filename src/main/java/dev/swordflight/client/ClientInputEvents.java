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

@Mod.EventBusSubscriber(modid = Swordflight.MOD_ID, value = Dist.CLIENT)
public final class ClientInputEvents {
    private static int aimSyncCountdown;
    private static boolean blockAttackHandledThisTick;
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
    }

    @SubscribeEvent
    public static void onInteractionInput(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean optimizedAim = OptimizedThirdPersonController.refreshScreenCenterHit();
        if (event.isAttack() && minecraft.player != null
                && minecraft.player.getMainHandItem().getItem() instanceof FlyingSwordItem) {
            event.setCanceled(true);
            int targetId = ClientOptions.optimizedThirdPerson()
                    ? OptimizedThirdPersonController.getAimedLivingEntityId() : -1;
            if (targetId < 0 && minecraft.hitResult instanceof EntityHitResult entityHit) {
                targetId = entityHit.getEntity().getId();
            }
            ModNetwork.CHANNEL.sendToServer(new ModNetwork.LockCrosshairNowPacket(targetId));
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
            return;
        }
        if (aimSyncCountdown-- > 0) return;
        aimSyncCountdown = 2;
        int targetId = OptimizedThirdPersonController.getAimedLivingEntityId();
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ClientAimTargetPacket(targetId));
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
}
