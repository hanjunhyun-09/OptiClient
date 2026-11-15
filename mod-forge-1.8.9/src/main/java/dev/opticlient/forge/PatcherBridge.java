package dev.opticlient.forge;

import dev.opticlient.core.OptiCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;

/**
 * 1.8.9 는 함께 설치되는 PolyPatcher 의 최적화 기능을 켜서 쓴다 (PolyPatcher 가 없으면 아무것도 안 함).
 * - HUD 캐싱: HUD 를 매 프레임 그리지 않고 1초에 60번만 그려 둔 그림을 붙인다.
 *   키 입력 표시가 늦지 않게, 키·마우스 상태가 바뀌면 바로 다시 그린다.
 * - 반짝임 끄기: OptiClient 반짝임 끄기(또는 PvP 부스트) 가 켜지면 PolyPatcher 의 반짝임 끄기를 켠다.
 */
final class PatcherBridge {
    private static final int CACHE_FPS = 60;

    private static boolean looked;
    private static Field glint, hudCaching, cacheFps, hudDirty;

    private static boolean glintOn;      // 우리가 반짝임 끄기를 켜 둔 상태인지
    private static boolean userGlint;    // 켜기 전 사용자의 PolyPatcher 설정
    private static int lastInput = -1;

    private PatcherBridge() {
    }

    private static Field field(String cls, String name) {
        try {
            Field f = Class.forName(cls).getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private static void lookup() {
        if (looked) return;
        looked = true;
        String cfg = "club.sk1er.patcher.config.PatcherConfig";
        glint = field(cfg, "disableEnchantmentGlint");
        hudCaching = field(cfg, "hudCaching");
        cacheFps = field(cfg, "cacheFPS");
        hudDirty = field("club.sk1er.patcher.screen.render.caching.HUDCaching", "hudDirty");
        try {
            if (hudCaching != null && OptiCore.config.hudCache) {
                hudCaching.setBoolean(null, true);
                if (cacheFps != null && cacheFps.getInt(null) < CACHE_FPS) cacheFps.setInt(null, CACHE_FPS);
            }
        } catch (ReflectiveOperationException e) {
            hudCaching = null;
        }
        if (glint == null) System.out.println("[OptiClient] PolyPatcher 가 없어 반짝임 끄기를 쓸 수 없습니다");
    }

    /** 반짝임 끄기를 쓸 수 있는지 (PolyPatcher 가 있어야 함) */
    static boolean glintSupported() {
        lookup();
        return glint != null;
    }

    /** 매 프레임 그리기 전에 부른다 */
    static void update(Minecraft mc) {
        lookup();
        try {
            if (glint != null) {
                boolean want = OptiCore.glintOff();
                if (want != glintOn) {
                    if (want) {
                        userGlint = glint.getBoolean(null);
                        glint.setBoolean(null, true);
                    } else {
                        glint.setBoolean(null, userGlint);
                    }
                    glintOn = want;
                }
            }
            if (hudDirty != null && hudCaching != null && hudCaching.getBoolean(null)) {
                int input = input(mc.gameSettings);
                if (input != lastInput) {
                    lastInput = input;
                    hudDirty.setBoolean(null, true);
                }
            }
        } catch (ReflectiveOperationException e) {
            glint = null;
            hudDirty = null;
        }
    }

    /** 키 입력 표시에 나오는 키·마우스 상태를 비트로 묶는다 */
    private static int input(GameSettings s) {
        int m = 0;
        if (s.keyBindForward.isKeyDown()) m |= 1;
        if (s.keyBindBack.isKeyDown()) m |= 2;
        if (s.keyBindLeft.isKeyDown()) m |= 4;
        if (s.keyBindRight.isKeyDown()) m |= 8;
        if (s.keyBindJump.isKeyDown()) m |= 16;
        if (s.keyBindSneak.isKeyDown()) m |= 32;
        if (s.keyBindSprint.isKeyDown()) m |= 64;
        if (Mouse.isButtonDown(0)) m |= 128;
        if (Mouse.isButtonDown(1)) m |= 256;
        return m;
    }
}
