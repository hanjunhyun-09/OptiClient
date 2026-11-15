package dev.opticlient.fabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.opticlient.core.OptiCore;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 낮은 불: 화면을 가리는 불꽃을 아래로 내린다 */
@Mixin(ScreenEffectRenderer.class)
public class FireOverlayMixin {
    @Unique
    private static boolean opticlient$pushed;

    @Inject(method = "submitFire", at = @At("HEAD"), require = 0)
    private static void opticlient$lowFireStart(PoseStack pose, SubmitNodeCollector buffers, TextureAtlasSprite sprite, CallbackInfo ci) {
        opticlient$pushed = OptiCore.config.lowFire;
        if (opticlient$pushed) {
            pose.pushPose();
            pose.translate(0f, -0.3f, 0f);
        }
    }

    @Inject(method = "submitFire", at = @At("RETURN"), require = 0)
    private static void opticlient$lowFireEnd(PoseStack pose, SubmitNodeCollector buffers, TextureAtlasSprite sprite, CallbackInfo ci) {
        if (opticlient$pushed) pose.popPose();
        opticlient$pushed = false;
    }
}
