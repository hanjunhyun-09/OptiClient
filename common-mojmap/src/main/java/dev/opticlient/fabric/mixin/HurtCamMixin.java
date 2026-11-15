package dev.opticlient.fabric.mixin;

import dev.opticlient.core.OptiCore;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 흔들림 끄기: 맞을 때 화면 기울기 효과를 건너뛴다 */
@Mixin(GameRenderer.class)
public class HurtCamMixin {
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true, require = 0)
    private void opticlient$noHurtCam(CallbackInfo ci) {
        if (OptiCore.config.noHurtCam) ci.cancel();
    }
}
