package dev.yujiancraft.mixin.client;

import dev.yujiancraft.client.OptimizedThirdPersonController;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the shoulder offset after vanilla 1.21.1 finishes rebuilding the camera position. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Inject(method = "setup", at = @At("RETURN"))
    private void yujiancraft$applyThirdPersonShoulder(BlockGetter level, Entity entity,
                                                      boolean detached, boolean thirdPersonReverse,
                                                      float partialTick, CallbackInfo callback) {
        OptimizedThirdPersonController.afterCameraSetup((Camera) (Object) this, entity, partialTick);
    }
}
