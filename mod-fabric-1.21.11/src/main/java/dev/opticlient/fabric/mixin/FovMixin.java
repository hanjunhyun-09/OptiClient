package dev.opticlient.fabric.mixin;

import dev.opticlient.core.OptiCore;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 줌: 월드 시야각만 줄인다 (손은 그대로) */
@Mixin(GameRenderer.class)
public class FovMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void opticlient$zoom(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        if (useFovSetting && OptiCore.zooming) cir.setReturnValue(OptiCore.zoomFov(cir.getReturnValue()));
    }
}
