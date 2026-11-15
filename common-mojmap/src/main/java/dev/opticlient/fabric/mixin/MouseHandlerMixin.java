package dev.opticlient.fabric.mixin;

import dev.opticlient.core.OptiCore;
import dev.opticlient.fabric.MojInput;
import dev.opticlient.fabric.OptiClientMod;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** CPS 계산용: 게임 화면에서 마우스 버튼을 누를 때마다 기록 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"))
    private void opticlient$onClick(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (action == 1 && !OptiClientMod.screenOpen()) OptiCore.CPS.click(MojInput.button(info.button()));
    }
}
