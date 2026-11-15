package dev.opticlient.fabric.mixin;

import dev.opticlient.core.OptiCore;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 줌: 월드 시야각만 줄인다 (손 시야각은 calculateHudFov 라 그대로) */
@Mixin(Camera.class)
public class FovMixin {
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void opticlient$zoom(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (OptiCore.zooming) cir.setReturnValue(OptiCore.zoomFov(cir.getReturnValue()));
    }
}
