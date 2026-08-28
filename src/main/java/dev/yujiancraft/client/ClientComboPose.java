package dev.yujiancraft.client;

import dev.yujiancraft.YujianCraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/** Render-only sword-seal stance. Inventory and server combat data are never mutated. */
@Mod.EventBusSubscriber(modid = YujianCraft.MOD_ID, value = Dist.CLIENT)
public final class ClientComboPose {
    private static final HumanoidModel.ArmPose SWORD_SEAL = HumanoidModel.ArmPose.create(
            "YUJIAN_SWORD_SEAL", false, (model, entity, arm) -> {
                float weight = entity instanceof Player player ? ClientComboState.poseWeight(player.getId()) : 0.0F;
                if (weight <= 0.0F || arm != HumanoidArm.RIGHT) return;
                // Only the right arm carries the command gesture. The left arm deliberately stays
                // on the vanilla lowered pose; forcing a behind-the-back pose with a one-joint
                // Minecraft arm always reads as an outward swing rather than a natural fold.
                model.rightArm.xRot = Mth.lerp(weight, model.rightArm.xRot, -1.48F);
                model.rightArm.yRot = Mth.lerp(weight, model.rightArm.yRot, -0.12F);
                model.rightArm.zRot = Mth.lerp(weight, model.rightArm.zRot, 0.02F);
            });
    private static final Map<Integer, HiddenItem> HIDDEN = new HashMap<>();

    private ClientComboPose() { }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void beforePlayer(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (!ClientComboState.shouldRenderPose(player.getId())) return;
        PlayerRenderer renderer = event.getRenderer();
        renderer.getModel().rightArmPose = SWORD_SEAL;
        int slot = player.getInventory().selected;
        ItemStack held = player.getInventory().getItem(slot);
        if (!held.isEmpty()) {
            HIDDEN.put(player.getId(), new HiddenItem(player, slot, held));
            player.getInventory().setItem(slot, ItemStack.EMPTY);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void afterPlayer(RenderPlayerEvent.Post event) {
        restore(event.getEntity().getId());
    }

    @SubscribeEvent
    public static void renderHand(RenderHandEvent event) {
        if (event.getHand() == InteractionHand.MAIN_HAND && ClientComboState.isLocalActive()) {
            event.setCanceled(true);
        }
    }

    private static void restore(int playerId) {
        HiddenItem hidden = HIDDEN.remove(playerId);
        if (hidden != null && hidden.player.getInventory().getItem(hidden.slot).isEmpty()) {
            hidden.player.getInventory().setItem(hidden.slot, hidden.stack);
        }
    }

    private record HiddenItem(Player player, int slot, ItemStack stack) { }
}
