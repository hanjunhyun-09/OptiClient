package dev.opticlient.fabric;

import dev.opticlient.core.HudConfig;
import dev.opticlient.core.OptiCore;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;

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

    private static void shot(MinecraftClient mc, String name) {
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, "opticlient-" + name + ".png", mc.getFramebuffer(), t -> log(t.getString()));
    }

    private static void cmd(MinecraftClient mc, String c) {
        mc.player.networkHandler.sendChatCommand(c);
    }

    private static boolean rotate;
    private static int crowdOff;
    private static int crowdOn;

    private static String item(String id, String ench, int lvl) {
        return "{id:\"minecraft:" + id + "\",count:1,components:{\"minecraft:enchantments\":{levels:{\"minecraft:" + ench + "\":" + lvl + "}}}}";
    }

    private static String armor() {
        return "ArmorItems:[" + item("diamond_boots", "protection", 4) + "," + item("diamond_leggings", "protection", 4) + ","
                + item("diamond_chestplate", "protection", 4) + "," + item("diamond_helmet", "protection", 4) + "],"
                + "HandItems:[" + item("diamond_sword", "sharpness", 5) + ",{}]";
    }

    /** PvP 처럼 무거운 장면: 마법부여 다이아 갑옷 좀비 12마리 (1.20.1·26.3 과 같은 장면) */
    private static void pvpScene(MinecraftClient mc) {
        cmd(mc, "gamerule doMobSpawning false");
        cmd(mc, "gamerule sendCommandFeedback false");
        cmd(mc, "difficulty easy");
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI * 2 / 12;
            cmd(mc, String.format(java.util.Locale.ROOT, "summon zombie ~%.1f ~ ~%.1f {NoAI:1b,PersistenceRequired:1b,%s}", Math.sin(a) * 4, 3 + Math.cos(a) * 4, armor()));
        }
        log("PvP 장면 생성 (좀비 12마리 + 파티클)");
    }

    /** 사람 많은 서버처럼: 이름표 달고 돌아다니는 인챈트 갑옷 좀비 40마리 추가 */
    private static void crowdScene(MinecraftClient mc) {
        for (int i = 0; i < 40; i++) {
            double a = i * Math.PI * 2 / 40;
            double r = 5 + (i % 4) * 2.5;
            cmd(mc, String.format(java.util.Locale.ROOT,
                    "summon zombie ~%.1f ~ ~%.1f {PersistenceRequired:1b,CustomName:'\"Player%d\"',CustomNameVisible:1b,%s}",
                    Math.sin(a) * r, Math.cos(a) * r, i, armor()));
        }
        rotate = true;
        log("복잡한 장면 생성 (돌아다니는 좀비 40마리 추가 + 화면 회전)");
    }

    private static boolean measure(MinecraftClient mc) {
        if (fpsCount % 10 == 0) cmd(mc, "particle minecraft:crit ~ ~1 ~3 3 1 3 0.3 300 force");
        if (rotate) mc.player.setYaw(mc.player.getYaw() + 3f);
        fpsSum += mc.getCurrentFps();
        fpsCount++;
        return fpsCount >= 100; // 5초
    }

    private static int average() {
        int avg = fpsCount == 0 ? 0 : (int) (fpsSum / fpsCount);
        fpsSum = 0;
        fpsCount = 0;
        return avg;
    }

    private static void tick(MinecraftClient mc) {
        mc.options.pauseOnLostFocus = false;
        if (++total > 20 * 400) {
            log("시간 초과 (state=" + state + ")");
            mc.scheduleStop();
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        switch (state) {
            case 0 -> {
                if (mc.currentScreen instanceof OptiTitleScreen) {
                    if (titleWait++ < 50) return;
                    shot(mc, "title");
                    CreateWorldScreen.create(mc, mc.currentScreen);
                    state = 1;
                }
            }
            case 1 -> {
                if (mc.currentScreen instanceof CreateWorldScreen cws) {
                    cws.getWorldCreator().setGameMode(WorldCreator.Mode.CREATIVE);
                    cws.getWorldCreator().setCheatsEnabled(true);
                    cws.getWorldCreator().setSeed("12345"); // 측정 비교를 위해 같은 월드
                    String label = Text.translatable("selectWorld.create").getString();
                    for (Element e : cws.children()) {
                        if (e instanceof ButtonWidget b && b.getMessage().getString().equals(label)) {
                            log("월드 생성");
                            b.onPress();
                            state = 2;
                            break;
                        }
                    }
                }
            }
            case 2 -> {
                if (mc.player != null && mc.world != null && mc.currentScreen == null) {
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
                if (mc.currentScreen != null) { // 다른 화면이 떠 있으면 닫고 다시 시도
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
                if (mc.currentScreen instanceof EditorScreen es) es.editor.selectTab(2);
                wait = 10;
                state = 6;
            }
            case 6 -> {
                shot(mc, "settings");
                if (mc.currentScreen instanceof EditorScreen es) {
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
                cmd(mc, "tp @s ~ ~ ~ 0 0");
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
                    crowdOff = average();
                    OptiCore.config.pvpBoost = true;
                    wait = 80;
                    state = 41;
                }
            }
            case 41 -> {
                if (measure(mc)) {
                    crowdOn = average();
                    shot(mc, "crowd");
                    OptiCore.config.pvpBoost = false;
                    rotate = false;
                    log("복잡한 장면 FPS 측정: 부스트 끔 평균 " + crowdOff + " / 부스트 켬 평균 " + crowdOn);
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
                mc.scheduleStop();
                state = 13;
            }
            default -> {
            }
        }
    }
}
