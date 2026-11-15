package dev.opticlient.fabric.mixin;

import dev.opticlient.core.OptiCore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 낮은 불: 화면을 가리는 불꽃을 아래로 내린다 */
@Mixin(InGameOverlayRenderer.class)
public class FireOverlayMixin {
    @Unique
    private static boolean opticlient$pushed;

    @Inject(method = "renderFireOverlay", at = @At("HEAD"), require = 0)
    private static void opticlient$lowFireStart(MinecraftClient client, MatrixStack matrices, CallbackInfo ci) {
        opticlient$pushed = OptiCore.config.lowFire;
        if (opticlient$pushed) {
            matrices.push();
            matrices.translate(0.0, -0.3, 0.0);
        }
    }

    @Inject(method = "renderFireOverlay", at = @At("RETURN"), require = 0)
    private static void opticlient$lowFireEnd(MinecraftClient client, MatrixStack matrices, CallbackInfo ci) {
        if (opticlient$pushed) matrices.pop();
        opticlient$pushed = false;
    }
}
