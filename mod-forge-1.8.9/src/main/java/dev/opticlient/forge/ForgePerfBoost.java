package dev.opticlient.forge;

import dev.opticlient.core.OptiCore;
import dev.opticlient.core.PerfBoost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import org.lwjgl.opengl.Display;

/** PvP FPS 부스트 (1.8.9): 바꾼 설정은 기억해 두었다가 끌 때 되돌린다 */
final class ForgePerfBoost implements PerfBoost {
    private boolean saved;
    private int renderDistance;
    private int particles;
    private int clouds;
    private boolean shadows;
    private boolean vsync;
    private int fpsLimit;
    private boolean fancy;
    private int ao;

    @Override
    public void apply(boolean on) {
        Minecraft mc = Minecraft.getMinecraft();
        GameSettings s = mc.gameSettings;
        if (on) {
            if (saved) return;
            saved = true;
            renderDistance = s.renderDistanceChunks;
            particles = s.particleSetting;
            clouds = s.clouds;
            shadows = s.entityShadows;
            vsync = s.enableVsync;
            fpsLimit = s.limitFramerate;
            fancy = s.fancyGraphics;
            ao = s.ambientOcclusion;

            s.renderDistanceChunks = Math.min(s.renderDistanceChunks, Math.max(2, OptiCore.config.boostRenderDistance));
            s.particleSetting = 2;      // 최소
            s.clouds = 0;               // 끔
            s.entityShadows = false;
            s.enableVsync = false;
            s.limitFramerate = 260;     // 무제한
            s.fancyGraphics = false;
            s.ambientOcclusion = 0;
        } else {
            if (!saved) return;
            saved = false;
            s.renderDistanceChunks = renderDistance;
            s.particleSetting = particles;
            s.clouds = clouds;
            s.entityShadows = shadows;
            s.enableVsync = vsync;
            s.limitFramerate = fpsLimit;
            s.fancyGraphics = fancy;
            s.ambientOcclusion = ao;
        }
        Display.setVSyncEnabled(s.enableVsync);
        if (mc.renderGlobal != null) mc.renderGlobal.loadRenderers();
        s.saveOptions();
    }

    @Override
    public void baseline() {
        GameSettings s = Minecraft.getMinecraft().gameSettings;
        s.limitFramerate = 260; // 무제한
        s.enableVsync = false;
        Display.setVSyncEnabled(false);
        s.saveOptions();
    }
}
