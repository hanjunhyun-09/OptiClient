package dev.opticlient.fabric.mixin;

import dev.opticlient.core.OptiCore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** CPS 계산용: 게임 화면에서 마우스 버튼을 누를 때마다 기록 */
@Mixin(Mouse.class)
public class MouseMixin {
    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void opticlient$onClick(long window, int button, int action, int mods, CallbackInfo ci) {
        if (action == 1 && MinecraftClient.getInstance().currentScreen == null) {
            OptiCore.CPS.click(button);
        }
    }
}
