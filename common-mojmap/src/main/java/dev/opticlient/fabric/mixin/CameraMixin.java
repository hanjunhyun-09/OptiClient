package dev.opticlient.fabric.mixin;

import dev.opticlient.core.OptiCore;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 퍼스펙티브: 카메라 방향만 따로 돌린다 (몸 방향은 그대로) */
@Mixin(Camera.class)
public class CameraMixin {
    @ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float opticlient$yaw(float yaw) {
        return OptiCore.freelook ? OptiCore.camYaw : yaw;
    }

    @ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private float opticlient$pitch(float pitch) {
        return OptiCore.freelook ? OptiCore.camPitch : pitch;
    }
}
