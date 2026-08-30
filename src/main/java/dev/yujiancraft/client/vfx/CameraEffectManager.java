package dev.yujiancraft.client.vfx;

import dev.yujiancraft.YujianCraft;
import dev.yujiancraft.client.ClientTechniqueOverlayState;
import dev.yujiancraft.client.ClientComboState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/** Applies timeline-authored camera impulses through Forge's real camera hooks. */
@net.neoforged.fml.common.EventBusSubscriber(modid = YujianCraft.MOD_ID, value = Dist.CLIENT)
public final class CameraEffectManager {
    private static final float TAU = (float) (Math.PI * 2.0D);

    private CameraEffectManager() { }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        ClientTechniqueOverlayState.FinisherFrame frame =
                ClientTechniqueOverlayState.sampleFinisher((float) event.getPartialTick());
        ClientComboState.Impact combo = ClientComboState.impact((float) event.getPartialTick());
        if (combo != null) {
            float strength = combo.strength();
            if (dev.yujiancraft.client.ClientOptions.comboHighFrequencyShake()) {
                float time = net.minecraft.Util.getMillis() / 1000.0F;
                event.setYaw(event.getYaw() + layeredWave(time, 13.0F, 0.7F, 1.9F) * strength * 1.6F);
                event.setPitch(event.getPitch() + layeredWave(time, 15.0F, 2.1F, 0.4F) * strength * 1.15F);
                event.setRoll(event.getRoll() + layeredWave(time, 9.0F, 1.1F, 2.7F) * strength * 0.75F);
            } else {
                // One broad impulse lobe: kick, weight and return, without the reciprocating
                // vibration that made a sword impact read like a powered saw.
                float pulse = (float) Math.sin(Math.PI * combo.shakePhase());
                event.setYaw(event.getYaw() + pulse * strength * 0.72F);
                event.setPitch(event.getPitch() - pulse * strength * 1.42F);
                event.setRoll(event.getRoll() + pulse * strength * 0.58F);
            }
        }
        if (frame == null || !frame.enabled("camera")) return;
        float frequency = Math.max(0.0F, frame.value("camera.frequency", 18.0F));
        float time = frame.ageSeconds();
        float yawNoise = layeredWave(time, frequency, 1.17F, 0.73F);
        float pitchNoise = layeredWave(time, frequency * 0.91F, 2.71F, 1.31F);
        float rollNoise = layeredWave(time, frequency * 0.67F, 0.43F, 2.03F);
        event.setYaw(event.getYaw() + yawNoise * frame.value("camera.yaw", 0.0F));
        event.setPitch(event.getPitch() + pitchNoise * frame.value("camera.pitch", 0.0F));
        event.setRoll(event.getRoll() + rollNoise * frame.value("camera.roll", 0.0F));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFov(ViewportEvent.ComputeFov event) {
        ClientTechniqueOverlayState.FinisherFrame frame =
                ClientTechniqueOverlayState.sampleFinisher((float) event.getPartialTick());
        ClientComboState.Impact combo = ClientComboState.impact((float) event.getPartialTick());
        if (combo != null) event.setFOV(Math.max(18.0D, event.getFOV() - combo.strength() * 2.8D));
        if (frame == null || !frame.enabled("camera")) return;
        event.setFOV(Math.max(18.0D, event.getFOV() + frame.value("camera.fov", 0.0F)));
    }

    /** Multi-frequency waves avoid the mechanical left-right cadence of a single sine. */
    private static float layeredWave(float time, float frequency, float phaseA, float phaseB) {
        float primary = (float) Math.sin(time * frequency * TAU + phaseA);
        float secondary = (float) Math.sin(time * frequency * 0.613F * TAU + phaseB);
        float tertiary = (float) Math.sin(time * frequency * 1.731F * TAU + phaseA * 0.37F);
        return primary * 0.57F + secondary * 0.28F + tertiary * 0.15F;
    }
}
