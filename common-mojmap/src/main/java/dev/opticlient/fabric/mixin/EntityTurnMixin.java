package dev.opticlient.fabric.mixin;

import dev.opticlient.core.OptiCore;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 퍼스펙티브 중 마우스 움직임은 몸 대신 카메라를 돌린다 */
@Mixin(Entity.class)
public class EntityTurnMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void opticlient$freelook(double dx, double dy, CallbackInfo ci) {
        if (OptiCore.freelook && (Object) this == Minecraft.getInstance().player) {
            OptiCore.turnCamera(dx, dy);
            ci.cancel();
        }
    }
}
