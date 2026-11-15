package dev.opticlient.fabric.mixin;

import dev.opticlient.core.OptiCore;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 반짝임 끄기: 인챈트 반짝임을 그리지 않는다 (갑옷·무기를 한 번 더 그리는 비용이 사라짐) */
@Mixin(ItemStack.class)
public class GlintMixin {
    @Inject(method = "hasFoil", at = @At("HEAD"), cancellable = true, require = 0)
    private void opticlient$noGlint(CallbackInfoReturnable<Boolean> cir) {
        if (OptiCore.glintOff()) cir.setReturnValue(false);
    }
}
