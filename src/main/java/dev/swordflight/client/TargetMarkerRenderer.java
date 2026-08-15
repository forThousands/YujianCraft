package dev.swordflight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.swordflight.Swordflight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = Swordflight.MOD_ID, value = Dist.CLIENT)
public final class TargetMarkerRenderer {
    private static final Component MARKER = Component.literal("▼");

    private TargetMarkerRenderer() {
    }

    @SubscribeEvent
    public static void renderMarker(RenderLivingEvent.Post<?, ?> event) {
        UUID lockedId = ClientTargetState.getLockedTargetId();
        LivingEntity entity = event.getEntity();
        if (lockedId == null || !lockedId.equals(entity.getUUID())) return;

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(0.0D, entity.getBbHeight() + 0.55D, 0.0D);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);
        float x = -font.width(MARKER) / 2.0F;
        font.drawInBatch(MARKER, x, 0.0F, 0xFFFF3030, false, poseStack.last().pose(),
                event.getMultiBufferSource(), Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientTargetState.setLockedTargetId(null);
    }
}
