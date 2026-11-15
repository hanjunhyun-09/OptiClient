package dev.opticlient.fabric.mixin;

import dev.opticlient.fabric.OptiClientMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 설정 저장 직전에 밝기를 원래 값으로 돌려 options.txt 가 깨지지 않게 한다 (다음 틱에 다시 적용됨) */
@Mixin(GameOptions.class)
public class GameOptionsMixin {
    @Inject(method = "write", at = @At("HEAD"))
    private void opticlient$beforeWrite(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.options != null) OptiClientMod.restoreGamma(mc);
    }
}
