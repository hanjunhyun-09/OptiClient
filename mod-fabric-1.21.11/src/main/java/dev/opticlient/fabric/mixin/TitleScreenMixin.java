package dev.opticlient.fabric.mixin;

import dev.opticlient.core.OptiCore;
import dev.opticlient.fabric.OptiTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 기본 타이틀 화면이 열리려 하면 OptiClient 타이틀 화면으로 바꾼다 */
@Mixin(Minecraft.class)
public class TitleScreenMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void opticlient$title(Screen screen, CallbackInfo ci) {
        // 월드가 없을 때 화면을 닫으면(null) 게임이 이 안에서 기본 타이틀 화면을 직접 만든다 → 그것도 바꾼다
        boolean closingToTitle = screen == null && ((Minecraft) (Object) this).level == null;
        if ((closingToTitle || screen instanceof TitleScreen && screen != OptiTitleScreen.vanilla) && OptiCore.config.customTitle) {
            ci.cancel();
            ((Minecraft) (Object) this).setScreen(new OptiTitleScreen());
        }
    }
}
