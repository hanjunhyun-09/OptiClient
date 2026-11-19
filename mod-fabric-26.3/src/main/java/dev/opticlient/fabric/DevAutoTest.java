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
        Screenshot.grab(mc.gameDirectory, "opticlient-" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1, t -> log(t.getString()));
    }

    private static void cmd(Minecraft mc, String c) {
        mc.player.connection.sendCommand(c);
    }

    private static final String GLINT = "components:{\"minecraft:enchantment_glint_override\":true}";
    private static boolean rotate;
    private static int pvpOff;
    private static int pvpOn;

    private static String gear() {
        return "equipment:{head:{id:\"diamond_helmet\"," + GLINT + "},chest:{id:\"diamond_chestplate\"," + GLINT + "},"
                + "legs:{id:\"diamond_leggings\"," + GLINT + "},feet:{id:\"diamond_boots\"," + GLINT + "},"
                + "mainhand:{id:\"diamond_sword\"," + GLINT + "}}";
    }

    /** PvP 장면: 인챈트 갑옷 좀비 12마리 (움직이지 않음) */
    private static void pvpScene(Minecraft mc) {
        cmd(mc, "gamerule send_command_feedback false");
        cmd(mc, "gamerule sendCommandFeedback false");
        cmd(mc, "gamerule spawn_mobs false");
        cmd(mc, "gamerule doMobSpawning false");
        cmd(mc, "difficulty easy");
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI * 2 / 12;
            cmd(mc, String.format(java.util.Locale.ROOT, "summon zombie ~%.1f ~ ~%.1f {NoAI:1b,PersistenceRequired:1b,%s}",
                    Math.sin(a) * 4, 3 + Math.cos(a) * 4, gear()));
        }
        log("PvP 장면 생성 (좀비 12마리 + 파티클)");
    }

    /** 사람 많은 서버처럼: 이름표 달고 돌아다니는 인챈트 갑옷 좀비 40마리 추가 */
    private static void crowdScene(Minecraft mc) {
        for (int i = 0; i < 40; i++) {
            double a = i * Math.PI * 2 / 40;
            double r = 5 + (i % 4) * 2.5;
            cmd(mc, String.format(java.util.Locale.ROOT,
                    "summon zombie ~%.1f ~ ~%.1f {PersistenceRequired:1b,CustomName:\"Player%d\",CustomNameVisible:1b,%s}",
                    Math.sin(a) * r, Math.cos(a) * r, i, gear()));
        }
        rotate = true;
        log("복잡한 장면 생성 (돌아다니는 좀비 40마리 추가 + 화면 회전)");
    }

    private static boolean measure(Minecraft mc) {
        if (fpsCount % 10 == 0) cmd(mc, "particle minecraft:crit ~ ~1 ~3 3 1 3 0.3 300 force");
        if (rotate) mc.player.setYRot(mc.player.getYRot() + 3f);
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
                if (mc.gui.screen() instanceof OptiTitleScreen) {
                    if (titleWait++ < 50) return;
                    shot(mc, "title");
                    CreateWorldScreen.openFresh(mc, () -> { });
                    state = 1;
                }
            }
            case 1 -> {
                if (mc.gui.screen() instanceof CreateWorldScreen cws) {
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
                if (mc.player != null && mc.level != null && mc.gui.screen() == null) {
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
                if (mc.gui.screen() != null) { // 다른 화면이 떠 있으면 닫고 다시 시도
                    mc.gui.setScreen(null);
                    wait = 10;
                    return;
                }
                shot(mc, "hud");
                mc.gui.setScreen(new EditorScreen());
                wait = 20;
                state = 5;
            }
            case 5 -> {
                shot(mc, "menu");
                if (mc.gui.screen() instanceof EditorScreen es) es.editor.selectTab(2);
                wait = 10;
                state = 6;
            }
            case 6 -> {
                shot(mc, "settings");
                if (mc.gui.screen() instanceof EditorScreen es) {
                    es.editor.selectTab(1);
                    es.editor.selectForTest("keystrokes", 1.3f); // 크기 조절·도구 막대 확인용
                }
                wait = 10;
                state = 7;
            }
            case 7 -> {
                shot(mc, "edit");
                mc.gui.setScreen(null);
                OptiCore.config.pvpBoost = false;
                pvpScene(mc);
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
                    crowdScene(mc);
                    wait = 100;
                    state = 40;
                }
            }
            case 40 -> {
                if (measure(mc)) {
                    pvpOff = average();
                    OptiCore.config.pvpBoost = true;
                    wait = 80;
                    state = 41;
                }
            }
            case 41 -> {
                if (measure(mc)) {
                    pvpOn = average();
                    shot(mc, "crowd");
                    OptiCore.config.pvpBoost = false;
                    rotate = false;
                    log("복잡한 장면 FPS 측정: 부스트 끔 평균 " + pvpOff + " / 부스트 켬 평균 " + pvpOn);
                    cmd(mc, "kill @e[type=zombie]");
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
                // 싱글플레이에서 나가면 OptiClient 타이틀 화면이 나와야 한다
                mc.disconnectFromWorld(net.minecraft.network.chat.Component.literal("OptiClient test"));
                wait = 100;
                state = 14;
            }
            case 14 -> {
                var sc = mc.gui.screen();
                log("나가기 후 화면: " + (sc == null ? "없음" : sc.getClass().getSimpleName()));
                mc.gui.setScreen(null); // 월드 없이 화면 닫기 → 게임이 타이틀 화면을 직접 만드는 경로
                wait = 20;
                state = 15;
            }
            case 15 -> {
                var sc = mc.gui.screen();
                log("화면 닫기 후 화면: " + (sc == null ? "없음" : sc.getClass().getSimpleName()));
                mc.stop();
                state = 13;
            }
            default -> {
            }
        }
    }
}
