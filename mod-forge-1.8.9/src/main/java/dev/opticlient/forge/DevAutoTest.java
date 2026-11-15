package dev.opticlient.forge;

import dev.opticlient.core.HudConfig;
import dev.opticlient.core.OptiCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * 개발 환경 전용 자동 테스트 (-Dopticlient.autotest=true 일 때만 실행, 배포 jar 에서는 제외됨).
 * 크리에이티브 월드 생성 → 갑옷/효과 지급 → HUD·편집 화면·밝기 스크린샷 → 종료.
 */
public final class DevAutoTest {
    private int state;
    private int wait;
    private int total;
    private long fpsSum;
    private int fpsCount;
    private int fpsOff;
    private int fpsOn;
    private int titleWait;

    private static void log(String s) {
        System.out.println("[OptiClient AutoTest] " + s);
    }

    private static void shot(Minecraft mc, String name) {
        IChatComponent msg = ScreenShotHelper.saveScreenshot(mc.mcDataDir, "opticlient-" + name + ".png",
                mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        log(msg.getUnformattedText());
    }

    /** 1.8.9 채팅은 100자에서 잘리므로 긴 summon 명령은 내장 서버에서 바로 실행한다 */
    private static void cmd(Minecraft mc, final String c) {
        final net.minecraft.server.integrated.IntegratedServer srv = mc.getIntegratedServer();
        if (srv == null) {
            mc.thePlayer.sendChatMessage("/" + c);
            return;
        }
        final java.util.UUID id = mc.thePlayer.getUniqueID();
        srv.addScheduledTask(() -> {
            net.minecraft.entity.player.EntityPlayerMP p = srv.getConfigurationManager().getPlayerByUUID(id);
            if (p != null) srv.getCommandManager().executeCommand(p, c);
        });
    }

    private boolean rotate;
    private int crowdOff;

    // 1.8: 장비 순서 = 손, 발, 다리, 가슴, 머리 / 마법부여는 숫자 id (보호 0, 날카로움 16)
    private static final String EQUIP = "Equipment:[{id:\"diamond_sword\",Count:1b,tag:{ench:[{id:16s,lvl:5s}]}},"
            + "{id:\"diamond_boots\",Count:1b,tag:{ench:[{id:0s,lvl:4s}]}},{id:\"diamond_leggings\",Count:1b,tag:{ench:[{id:0s,lvl:4s}]}},"
            + "{id:\"diamond_chestplate\",Count:1b,tag:{ench:[{id:0s,lvl:4s}]}},{id:\"diamond_helmet\",Count:1b,tag:{ench:[{id:0s,lvl:4s}]}}]";

    /** PvP 처럼 무거운 장면: 마법부여 다이아 갑옷 좀비 12마리 (다른 버전과 같은 장면) */
    private void pvpScene(Minecraft mc) {
        cmd(mc, "gamerule doMobSpawning false");
        cmd(mc, "gamerule sendCommandFeedback false");
        cmd(mc, "difficulty 1");
        cmd(mc, "tp @p ~ ~ ~ 0 0");
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI * 2 / 12;
            cmd(mc, String.format(java.util.Locale.ROOT, "summon Zombie ~%.1f ~ ~%.1f {NoAI:1b,PersistenceRequired:1b,%s}", Math.sin(a) * 4, 3 + Math.cos(a) * 4, EQUIP));
        }
        log("PvP 장면 생성 (좀비 12마리 + 파티클)");
    }

    /** 사람 많은 서버처럼: 이름표 달고 돌아다니는 인챈트 갑옷 좀비 40마리 추가 */
    private void crowdScene(Minecraft mc) {
        for (int i = 0; i < 40; i++) {
            double a = i * Math.PI * 2 / 40;
            double r = 5 + (i % 4) * 2.5;
            cmd(mc, String.format(java.util.Locale.ROOT, "summon Zombie ~%.1f ~ ~%.1f {PersistenceRequired:1b,CustomName:\"Player%d\",CustomNameVisible:1b,%s}",
                    Math.sin(a) * r, Math.cos(a) * r, i, EQUIP));
        }
        rotate = true;
        log("복잡한 장면 생성 (돌아다니는 좀비 40마리 추가 + 화면 회전)");
    }

    private static final String[] DIAG = {"기준", "HUD 숨김", "파티클 없음", "이름표 없음", "반짝임 없음(이후 계속)", "몹 안 그림"};
    private int diag;
    private boolean noParticles;
    private boolean noNames;
    private boolean noEntities;

    private static void stripGlint(Minecraft mc) {
        for (Object o : mc.theWorld.loadedEntityList) {
            if (!(o instanceof net.minecraft.entity.EntityLivingBase) || o == mc.thePlayer) continue;
            for (net.minecraft.item.ItemStack st : ((net.minecraft.entity.EntityLivingBase) o).getInventory()) {
                if (st != null && st.getTagCompound() != null) st.getTagCompound().removeTag("ench");
            }
        }
    }

    @SubscribeEvent
    public void onLiving(net.minecraftforge.client.event.RenderLivingEvent.Pre<?> e) {
        if (noEntities && e.entity != Minecraft.getMinecraft().thePlayer) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onName(net.minecraftforge.client.event.RenderLivingEvent.Specials.Pre<?> e) {
        if (noNames) e.setCanceled(true);
    }

    /** 5초(100틱) 동안 FPS 를 모은다. 다 모았으면 평균을, 아니면 -1 */
    private int measure(Minecraft mc) {
        if (fpsCount % 10 == 0 && !noParticles) cmd(mc, "particle crit ~ ~1 ~3 3 1 3 0.3 300 force");
        if (rotate) mc.thePlayer.rotationYaw += 3f;
        fpsSum += Minecraft.getDebugFPS();
        if (++fpsCount < 100) return -1;
        int avg = (int) (fpsSum / fpsCount);
        fpsSum = 0;
        fpsCount = 0;
        return avg;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        mc.gameSettings.pauseOnLostFocus = false;
        if (++total > 20 * 400) {
            log("시간 초과 (state=" + state + ")");
            mc.shutdown();
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        switch (state) {
            case 0:
                if (mc.currentScreen instanceof OptiTitleGui) {
                    if (titleWait++ < 50) break;
                    shot(mc, "title");
                    log("월드 생성");
                    WorldSettings ws = new WorldSettings(12345L, WorldSettings.GameType.CREATIVE, true, false, WorldType.DEFAULT);
                    ws.enableCommands();
                    mc.launchIntegratedServer("opticlient-test", "OptiClient Test", ws);
                    state = 1;
                }
                break;
            case 1:
                if (mc.thePlayer != null && mc.theWorld != null && mc.currentScreen == null) {
                    wait = 100;
                    state = 2;
                }
                break;
            case 2:
                cmd(mc, "time set 1000");
                cmd(mc, "replaceitem entity @p slot.armor.head diamond_helmet");
                cmd(mc, "replaceitem entity @p slot.armor.chest iron_chestplate");
                cmd(mc, "replaceitem entity @p slot.armor.legs golden_leggings");
                cmd(mc, "replaceitem entity @p slot.armor.feet diamond_boots");
                cmd(mc, "replaceitem entity @p slot.hotbar.0 diamond_sword");
                cmd(mc, "effect @p speed 300 1");
                cmd(mc, "effect @p strength 300 0");
                for (String id : new String[]{"speed", "clock"}) {
                    HudConfig.ModuleState s = OptiCore.config.modules.get(id);
                    if (s != null) s.enabled = true;
                }
                OptiCore.sprintToggled = true;
                for (int i = 0; i < 7; i++) OptiCore.CPS.click(0);
                for (int i = 0; i < 3; i++) OptiCore.CPS.click(1);
                wait = 40;
                state = 3;
                break;
            case 3:
                if (mc.currentScreen != null) { // 다른 화면이 떠 있으면 닫고 다시 시도
                    mc.displayGuiScreen(null);
                    wait = 10;
                    break;
                }
                shot(mc, "hud");
                mc.displayGuiScreen(new EditorGui());
                wait = 20;
                state = 4;
                break;
            case 4:
                shot(mc, "menu");
                if (mc.currentScreen instanceof EditorGui) ((EditorGui) mc.currentScreen).editor.selectTab(2);
                wait = 10;
                state = 5;
                break;
            case 5:
                shot(mc, "settings");
                if (mc.currentScreen instanceof EditorGui) { ((EditorGui) mc.currentScreen).editor.selectTab(1); ((EditorGui) mc.currentScreen).editor.selectForTest("keystrokes", 1.3f); }
                wait = 10;
                state = 6;
                break;
            case 6:
                shot(mc, "edit");
                mc.displayGuiScreen(null);
                OptiCore.config.pvpBoost = false;
                pvpScene(mc);
                wait = 200; // 청크가 다 만들어질 때까지 기다린 뒤 측정
                state = 7;
                break;
            case 7: {
                int v = measure(mc);
                if (v >= 0) {
                    fpsOff = v;
                    OptiCore.config.pvpBoost = true;
                    wait = 80;
                    state = 8;
                }
                break;
            }
            case 8: {
                int v = measure(mc);
                if (v >= 0) {
                    fpsOn = v;
                    shot(mc, "boost");
                    OptiCore.config.pvpBoost = false;
                    wait = 80;
                    state = 20;
                }
                break;
            }
            case 20: {
                int v = measure(mc);
                if (v >= 0) {
                    log("FPS 측정: 부스트 끔 평균 " + fpsOff + " / 부스트 켬 평균 " + fpsOn + " / 다시 끔 평균 " + v);
                    crowdScene(mc);
                    wait = 100;
                    state = 40;
                }
                break;
            }
            case 40: {
                int v = measure(mc);
                if (v >= 0) {
                    crowdOff = v;
                    OptiCore.config.pvpBoost = true;
                    wait = 80;
                    state = 41;
                }
                break;
            }
            case 41: {
                int v = measure(mc);
                if (v >= 0) {
                    shot(mc, "crowd");
                    OptiCore.config.pvpBoost = false;
                    rotate = false;
                    log("복잡한 장면 FPS 측정: 부스트 끔 평균 " + crowdOff + " / 부스트 켬 평균 " + v);
                    if (Boolean.getBoolean("opticlient.diag")) {
                        rotate = true;
                        diag = 0;
                        wait = 60;
                        state = 50;
                        break;
                    }
                    cmd(mc, "kill @e[type=Zombie]");
                    cmd(mc, "time set 18000");
                    wait = 60;
                    state = 9;
                }
                break;
            }
            case 50: { // 진단: 하나씩 끄고 잰다 (부스트 끔)
                int v = measure(mc);
                if (v < 0) break;
                log("진단 " + DIAG[diag] + ": " + v);
                // 바꾼 것 되돌리기
                mc.gameSettings.hideGUI = false;
                noParticles = false;
                noNames = false;
                diag++;
                if (diag >= DIAG.length) {
                    noEntities = false;
                    rotate = false;
                    cmd(mc, "kill @e[type=Zombie]");
                    cmd(mc, "time set 18000");
                    wait = 60;
                    state = 9;
                    break;
                }
                switch (diag) {
                    case 1: mc.gameSettings.hideGUI = true; break;
                    case 2: noParticles = true; break;
                    case 3: noNames = true; break;
                    case 4: stripGlint(mc); break;
                    case 5: noEntities = true; break;
                    default: break;
                }
                wait = 40;
                break;
            }
            case 9:
                shot(mc, "night");
                OptiCore.config.fullbright = true;
                wait = 40;
                state = 10;
                break;
            case 10:
                shot(mc, "fullbright");
                OptiCore.config.fullbright = false;
                log("완료");
                wait = 10;
                state = 11;
                break;
            case 11:
                mc.shutdown();
                state = 12;
                break;
            default:
                break;
        }
    }
}
