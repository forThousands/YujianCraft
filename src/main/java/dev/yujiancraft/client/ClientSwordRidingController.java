package dev.yujiancraft.client;

import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.entity.FlyingSwordEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client movement and presentation that are specific to standing on a flying sword. */
@Mod.EventBusSubscriber(modid = YujianCraft.MOD_ID, value = Dist.CLIENT)
public final class ClientSwordRidingController {
    private static final double ASCEND_SPEED = 0.32D;
    private static final double DESCEND_SPEED = -0.28D;
    private static final HumanoidModel.ArmPose ARMS_DOWN = HumanoidModel.ArmPose.create(
            "YUJIAN_RIDING_ARMS_DOWN", false, (model, entity, arm) -> {
                var part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
                part.xRot = 0.0F;
                part.yRot = 0.0F;
                part.zRot = 0.0F;
            });
    private static final Map<UUID, Float> SAVED_WALK_SPEEDS = new HashMap<>();
    private static final Map<UUID, Float> SAVED_ATTACK_TIMES = new HashMap<>();

    private ClientSwordRidingController() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!ClientSwordRidingState.isActive() || minecraft.player == null
                || minecraft.screen != null || minecraft.isPaused()) return;

        boolean ascend = minecraft.options.keyJump.isDown();
        boolean descend = minecraft.options.keyShift.isDown();
        Vec3 movement = minecraft.player.getDeltaMovement();
        double vertical = ascend == descend ? 0.0D : ascend ? ASCEND_SPEED : DESCEND_SPEED;
        minecraft.player.setDeltaMovement(movement.x, vertical, movement.z);
        minecraft.player.fallDistance = 0.0F;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (event.isCanceled() || !isSwordRiding(event.getEntity())) return;
        Player player = event.getEntity();
        SAVED_WALK_SPEEDS.put(player.getUUID(), player.walkAnimation.speed());
        // Both interpolated endpoints must be zero, otherwise the legs retain part of the
        // previous stride for one render frame.
        player.walkAnimation.setSpeed(0.0F);
        player.walkAnimation.update(0.0F, 1.0F);
        PlayerRenderer renderer = event.getRenderer();
        SAVED_ATTACK_TIMES.put(player.getUUID(), renderer.getModel().attackTime);
        renderer.getModel().attackTime = 0.0F;
        renderer.getModel().rightArmPose = ARMS_DOWN;
        renderer.getModel().leftArmPose = ARMS_DOWN;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        Float previousSpeed = SAVED_WALK_SPEEDS.remove(event.getEntity().getUUID());
        if (previousSpeed != null) event.getEntity().walkAnimation.setSpeed(previousSpeed);
        Float previousAttackTime = SAVED_ATTACK_TIMES.remove(event.getEntity().getUUID());
        if (previousAttackTime != null) event.getRenderer().getModel().attackTime = previousAttackTime;
    }

    private static boolean isSwordRiding(Player player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (player == minecraft.player && ClientSwordRidingState.isActive()) return true;
        return player.level().getEntitiesOfClass(FlyingSwordEntity.class,
                        player.getBoundingBox().inflate(2.5D), FlyingSwordEntity::isVisualRideSupport)
                .stream().anyMatch(sword -> sword.getVisualOwner() == player);
    }
}
