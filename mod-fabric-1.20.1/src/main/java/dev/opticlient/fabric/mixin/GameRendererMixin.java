package dev.opticlient.fabric.mixin;

import dev.opticlient.core.OptiCore;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 줌: 월드 시야각만 줄인다 (손은 그대로) */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void opticlient$zoom(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        if (changingFov && OptiCore.zooming) {
            cir.setReturnValue((double) OptiCore.zoomFov(cir.getReturnValue().floatValue()));
        }
    }

    /** 흔들림 끄기: 맞을 때 화면 기울기 효과를 건너뛴다 */
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true, require = 0)
    private void opticlient$noHurtCam(CallbackInfo ci) {
        if (OptiCore.config.noHurtCam) ci.cancel();
    }
}
