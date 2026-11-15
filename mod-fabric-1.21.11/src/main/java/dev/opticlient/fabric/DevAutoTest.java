package dev.opticlient.fabric;

import dev.opticlient.core.HudConfig;
import dev.opticlient.core.OptiCore;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

import java.lang.reflect.Method;

/**
 * 개발 환경 전용 자동 테스트 (-Dopticlient.autotest=true 일 때만 실행, 배포 jar 에서는 제외됨).
 * 크리에이티브 월드 생성 → 갑옷/효과 지급 → HUD·메뉴 스크린샷 → FPS 부스트 전후 측정 → 밤/밝기 스크린샷 → 종료.
 */
final class DevAutoTest {
    private static int state;
    private static int wait;
    private static int total;
    private static long fpsSum;
    private static int fpsCount;
    private static int fpsOff;
    private static int fpsOn;
    private static int titleWait;

    static void start() {
        log("시작");
        ClientTickEvents.END_CLIENT_TICK.register(DevAutoTest::tick);
    }

    private static void log(String s) {
        System.out.println("[OptiClient AutoTest] " + s);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "opticlient-" + name + ".png", mc.getMainRenderTarget(), 1, t -> log(t.getString()));
    }

    private static void cmd(Minecraft mc, String c) {
        mc.player.connection.sendCommand(c);
    }

    private static boolean measure(Minecraft mc) {
        fpsSum += mc.getFps();
        fpsCount++;
        return fpsCount >= 100; // 5초
    }

    private static int average() {
        int avg = fpsCount == 0 ? 0 : (int) (fpsSum / fpsCount);
        fpsSum = 0;
        fpsCount = 0;
        return avg;
    }

    private static void tick(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        if (++total > 20 * 400) {
            log("시간 초과 (state=" + state + ")");
            mc.stop();
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        switch (state) {
            case 0 -> {
                if (mc.screen instanceof OptiTitleScreen) {
                    if (titleWait++ < 50) return;
                    shot(mc, "title");
                    CreateWorldScreen.openFresh(mc, () -> { });
                    state = 1;
                }
            }
            case 1 -> {
                if (mc.screen instanceof CreateWorldScreen cws) {
                    cws.getUiState().setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
                    cws.getUiState().setAllowCommands(true);
                    cws.getUiState().setSeed("12345"); // 측정 비교를 위해 같은 월드
                    try {
                        Method m = CreateWorldScreen.class.getDeclaredMethod("onCreate");
                        m.setAccessible(true);
                        m.invoke(cws);
                        log("월드 생성");
                    } catch (ReflectiveOperationException e) {
                        e.printStackTrace();
                    }
                    state = 2;
                }
            }
            case 2 -> {
                if (mc.player != null && mc.level != null && mc.screen == null) {
                    wait = 100;
                    state = 3;
                }
            }
            case 3 -> {
                cmd(mc, "time set day");
                cmd(mc, "item replace entity @s armor.head with diamond_helmet");
                cmd(mc, "item replace entity @s armor.chest with iron_chestplate");
                cmd(mc, "item replace entity @s armor.legs with golden_leggings");
                cmd(mc, "item replace entity @s armor.feet with diamond_boots");
                cmd(mc, "item replace entity @s weapon.mainhand with diamond_sword");
                cmd(mc, "effect give @s speed 300 1");
                cmd(mc, "effect give @s strength 300 0");
                for (String id : new String[]{"speed", "clock"}) {
                    HudConfig.ModuleState s = OptiCore.config.modules.get(id);
                    if (s != null) s.enabled = true;
                }
                OptiCore.sprintToggled = true;
                for (int i = 0; i < 7; i++) OptiCore.CPS.click(0);
                for (int i = 0; i < 3; i++) OptiCore.CPS.click(1);
                wait = 40;
                state = 4;
            }
            case 4 -> {
                if (mc.screen != null) { // 다른 화면이 떠 있으면 닫고 다시 시도
                    mc.setScreen(null);
                    wait = 10;
                    return;
                }
                shot(mc, "hud");
                mc.setScreen(new EditorScreen());
                wait = 20;
                state = 5;
            }
            case 5 -> {
                shot(mc, "menu");
                if (mc.screen instanceof EditorScreen es) es.editor.selectTab(2);
                wait = 10;
                state = 6;
            }
            case 6 -> {
                shot(mc, "settings");
                if (mc.screen instanceof EditorScreen es) {
                    es.editor.selectTab(1);
                    es.editor.selectForTest("keystrokes", 1.3f); // 크기 조절·도구 막대 확인용
                }
                wait = 10;
                state = 7;
            }
            case 7 -> {
                shot(mc, "edit");
                mc.setScreen(null);
                OptiCore.config.pvpBoost = false;
                wait = 200; // 청크가 다 만들어질 때까지 기다린 뒤 측정
                state = 8;
            }
            case 8 -> {
                if (measure(mc)) {
                    fpsOff = average();
                    OptiCore.config.pvpBoost = true;
                    wait = 80;
                    state = 9;
                }
            }
            case 9 -> {
                if (measure(mc)) {
                    fpsOn = average();
                    shot(mc, "boost");
                    OptiCore.config.pvpBoost = false;
                    wait = 80;
                    state = 20;
                }
            }
            case 20 -> {
                if (measure(mc)) {
                    log("FPS 측정: 부스트 끔 평균 " + fpsOff + " / 부스트 켬 평균 " + fpsOn + " / 다시 끔 평균 " + average());
                    cmd(mc, "time set midnight");
                    wait = 60;
                    state = 10;
                }
            }
            case 10 -> {
                shot(mc, "night");
                OptiCore.config.fullbright = true;
                wait = 40;
                state = 11;
            }
            case 11 -> {
                shot(mc, "fullbright");
                OptiCore.config.fullbright = false;
                log("완료");
                wait = 10;
                state = 12;
            }
            case 12 -> {
                mc.stop();
                state = 13;
            }
            default -> {
            }
        }
    }
}
