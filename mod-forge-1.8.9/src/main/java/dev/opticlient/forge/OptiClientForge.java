package dev.opticlient.forge;

import dev.opticlient.core.OptiCore;
import dev.opticlient.core.PvpTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderBlockOverlayEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import java.lang.reflect.Method;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

@Mod(modid = OptiCore.MOD_ID, name = "OptiClient HUD", version = "1.0.0",
        clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class OptiClientForge {
    private KeyBinding menuKey;
    private KeyBinding zoomKey;
    private KeyBinding sprintKey;
    private KeyBinding freelookKey;
    private KeyBinding sneakKey;
    private boolean freelookToggled;
    private int savedView = -1;
    private Boolean hitboxApplied;

    // 화면을 그리는 동안 바꿔 둔 값 (그리기가 끝나면 되돌림)
    private static boolean renderSwapped;
    private static float realYaw, realPitch, realPrevYaw, realPrevPitch;
    private static int savedHurtTime = -1;

    static double speed;
    private double lastX = Double.NaN;
    private double lastZ;
    private float savedFov = -1;
    private float savedGamma = -1;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        OptiCore.init(e.getModConfigurationDirectory());
        OptiCore.perf = new ForgePerfBoost();
        // 1.8.9 는 마인크래프트 자체 그리기가 느려서 PvP 부스트를 기본으로 켠다 (처음 한 번만, 끄면 끈 채로 유지)
        if (!OptiCore.config.boostDefaultApplied) {
            OptiCore.config.boostDefaultApplied = true;
            OptiCore.config.pvpBoost = true;
            OptiCore.save();
        }
        OptiCore.glintSupported = false; // PolyPatcher 가 있는지는 게임이 뜬 뒤 확인 (PatcherBridge)
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        menuKey = new KeyBinding("key.opticlient.menu", Keyboard.KEY_RCONTROL, // PolyPatcher(OneConfig) 가 Right Shift 를 써서 겹치지 않게
                "key.categories.opticlient");
        zoomKey = new KeyBinding("key.opticlient.zoom", Keyboard.KEY_C, "key.categories.opticlient");
        sprintKey = new KeyBinding("key.opticlient.sprint", Keyboard.KEY_V, "key.categories.opticlient");
        ClientRegistry.registerKeyBinding(menuKey);
        ClientRegistry.registerKeyBinding(zoomKey);
        ClientRegistry.registerKeyBinding(sprintKey);
        freelookKey = new KeyBinding("key.opticlient.freelook", Keyboard.KEY_LMENU, "key.categories.opticlient");
        sneakKey = new KeyBinding("key.opticlient.sneak", Keyboard.KEY_G, "key.categories.opticlient");
        ClientRegistry.registerKeyBinding(freelookKey);
        ClientRegistry.registerKeyBinding(sneakKey);

        MinecraftForge.EVENT_BUS.register(this);
        if (FMLCommonHandler.instance().bus() != MinecraftForge.EVENT_BUS) {
            FMLCommonHandler.instance().bus().register(this);
        }

        // 개발용 자동 테스트 (배포 jar 에는 DevAutoTest 가 들어가지 않음)
        if (Boolean.getBoolean("opticlient.autotest")) {
            try {
                Object test = Class.forName("dev.opticlient.forge.DevAutoTest").newInstance();
                MinecraftForge.EVENT_BUS.register(test);
                if (FMLCommonHandler.instance().bus() != MinecraftForge.EVENT_BUS) FMLCommonHandler.instance().bus().register(test);
            } catch (ReflectiveOperationException ex) {
                ex.printStackTrace();
            }
        }
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post e) {
        if (e.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.gameSettings.showDebugInfo || mc.currentScreen instanceof EditorGui) return;
        ScaledResolution sr = e.resolution;
        OptiCore.renderHud(new ForgeHudRenderer(mc), ForgeGameInfo.INSTANCE, sr.getScaledWidth(), sr.getScaledHeight());
    }

    /** OptiClient 크로스헤어를 켜면 기본 크로스헤어를 그리지 않는다 */
    @SubscribeEvent
    public void onOverlayPre(RenderGameOverlayEvent.Pre e) {
        if (e.type == RenderGameOverlayEvent.ElementType.CROSSHAIRS && OptiCore.config.crosshair) e.setCanceled(true);
    }

    /** 리치·콤보: 공격하는 순간 상대와 거리를 기록 */
    @SubscribeEvent
    public void onAttack(AttackEntityEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (e.entityPlayer != mc.thePlayer || e.target == null) return;
        Vec3 eye = mc.thePlayer.getPositionEyes(1f);
        AxisAlignedBB b = e.target.getEntityBoundingBox();
        OptiCore.PVP.onAttack(e.target.getEntityId(),
                PvpTracker.reach(eye.xCoord, eye.yCoord, eye.zCoord, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ));
    }

    private static Method fireMethod;
    private static boolean fireLookup;

    /** 낮은 불: 기본 불꽃 대신 아래로 내린 불꽃을 그린다 */
    @SubscribeEvent
    public void onBlockOverlay(RenderBlockOverlayEvent e) {
        if (e.overlayType != RenderBlockOverlayEvent.OverlayType.FIRE || !OptiCore.config.lowFire) return;
        if (!fireLookup) {
            fireLookup = true;
            try {
                fireMethod = ReflectionHelper.findMethod(ItemRenderer.class, null,
                        new String[]{"renderFireInFirstPerson", "func_78442_d"}, float.class);
            } catch (RuntimeException ex) {
                System.err.println("[OptiClient] 낮은 불을 쓸 수 없습니다: " + ex);
            }
        }
        if (fireMethod == null) return;
        e.setCanceled(true);
        GlStateManager.pushMatrix();
        GlStateManager.translate(0f, -0.3f, 0f);
        try {
            fireMethod.invoke(Minecraft.getMinecraft().getItemRenderer(), e.renderPartialTicks);
        } catch (ReflectiveOperationException ex) {
            fireMethod = null;
        } finally {
            GlStateManager.popMatrix();
        }
    }

    /**
     * 화면을 그리기 직전/직후.
     * - 퍼스펙티브: 그리는 동안만 플레이어 방향을 카메라 방향으로 바꾸고, 끝나면 원래 방향으로 되돌린다.
     *   (그리는 동안 들어온 마우스 움직임은 카메라에만 남는다)
     * - 흔들림 끄기: 그리는 동안만 피격 시간을 0 으로 둔다.
     */
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.thePlayer;
        if (e.phase == TickEvent.Phase.START) {
            renderSwapped = false;
            PatcherBridge.update(mc);
            OptiCore.glintSupported = PatcherBridge.glintSupported();
            if (p == null) return;
            if (OptiCore.freelook) {
                realYaw = p.rotationYaw;
                realPitch = p.rotationPitch;
                realPrevYaw = p.prevRotationYaw;
                realPrevPitch = p.prevRotationPitch;
                p.rotationYaw = p.prevRotationYaw = OptiCore.camYaw;
                p.rotationPitch = p.prevRotationPitch = OptiCore.camPitch;
                renderSwapped = true;
            }
            if (OptiCore.config.noHurtCam && p.hurtTime > 0) {
                savedHurtTime = p.hurtTime;
                p.hurtTime = 0;
            }
        } else {
            if (p == null) {
                savedHurtTime = -1;
                return;
            }
            if (savedHurtTime >= 0) {
                p.hurtTime = savedHurtTime;
                savedHurtTime = -1;
            }
            if (renderSwapped) {
                OptiCore.camYaw = p.rotationYaw;
                OptiCore.camPitch = p.rotationPitch;
                p.rotationYaw = realYaw;
                p.rotationPitch = realPitch;
                p.prevRotationYaw = realPrevYaw;
                p.prevRotationPitch = realPrevPitch;
                renderSwapped = false;
                // 조준 대상은 몸이 보는 방향으로 다시 계산 (카메라 방향으로 때리지 않게)
                if (mc.entityRenderer != null) mc.entityRenderer.getMouseOver(e.renderTickTime);
            }
        }
    }

    /** 흔들림 끄기 때문에 잠깐 0 이 된 동안에도 원래 피격 시간을 돌려준다 */
    static int realHurtTime(EntityLivingBase p) {
        return savedHurtTime >= 0 ? savedHurtTime : p.hurtTime;
    }

    /** 기본 타이틀 화면이 열리려 하면 OptiClient 타이틀 화면으로 바꾼다 */
    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent e) {
        if (e.gui instanceof GuiMainMenu && e.gui != OptiTitleGui.vanilla && OptiCore.config.customTitle) {
            e.gui = new OptiTitleGui();
        }
    }

    @SubscribeEvent
    public void onMouse(MouseEvent e) {
        if (e.buttonstate && (e.button == 0 || e.button == 1)) OptiCore.CPS.click(e.button);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();

        while (menuKey.isPressed()) {
            if (mc.thePlayer != null) mc.displayGuiScreen(new EditorGui());
        }
        while (sprintKey.isPressed()) {
            OptiCore.sprintToggled = !OptiCore.sprintToggled;
            if (!OptiCore.sprintToggled) {
                int code = mc.gameSettings.keyBindSprint.getKeyCode();
                KeyBinding.setKeyBindState(code, code > 0 && Keyboard.isKeyDown(code));
            }
        }

        while (sneakKey.isPressed()) {
            OptiCore.sneakToggled = !OptiCore.sneakToggled;
            if (!OptiCore.sneakToggled) releaseKey(mc.gameSettings.keyBindSneak);
        }
        if (OptiCore.sneakToggled && !OptiCore.config.toggleSneak) {
            OptiCore.sneakToggled = false;
            releaseKey(mc.gameSettings.keyBindSneak);
        }
        while (freelookKey.isPressed()) freelookToggled = !freelookToggled;
        updateFreelook(mc);

        // 줌: 누르고 있는 동안 시야각을 줄이고 부드러운 카메라를 켠다
        boolean zoom = OptiCore.config.zoomEnabled && zoomKey.isKeyDown() && mc.currentScreen == null;
        if (zoom && savedFov < 0) {
            savedFov = mc.gameSettings.fovSetting;
            mc.gameSettings.smoothCamera = true;
        } else if (!zoom && savedFov >= 0) {
            mc.gameSettings.fovSetting = savedFov;
            mc.gameSettings.smoothCamera = false;
            savedFov = -1;
        }
        OptiCore.zooming = zoom;
        if (zoom) mc.gameSettings.fovSetting = (float) (savedFov / Math.max(1.0, OptiCore.config.zoomFactor));

        // 밝기 최대
        if (OptiCore.config.fullbright) {
            if (savedGamma < 0) savedGamma = mc.gameSettings.gammaSetting;
            mc.gameSettings.gammaSetting = 100f;
        } else if (savedGamma >= 0) {
            mc.gameSettings.gammaSetting = savedGamma;
            savedGamma = -1;
        }

        OptiCore.tickBaseline();
        if (mc.thePlayer != null) {
            OptiCore.tickBoost();
            if (OptiCore.config.toggleSprint && OptiCore.sprintToggled && mc.currentScreen == null) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
            }
            if (OptiCore.config.toggleSneak && OptiCore.sneakToggled && mc.currentScreen == null) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
            }
            OptiCore.PVP.tick(ForgeGameInfo.INSTANCE);
            boolean hb = OptiCore.config.hitboxes;
            if (hitboxApplied == null ? hb : hitboxApplied != hb) {
                mc.getRenderManager().setDebugBoundingBox(hb);
                hitboxApplied = hb;
            }
            double x = mc.thePlayer.posX;
            double z = mc.thePlayer.posZ;
            if (!Double.isNaN(lastX)) speed = Math.sqrt((x - lastX) * (x - lastX) + (z - lastZ) * (z - lastZ)) * 20.0;
            lastX = x;
            lastZ = z;
        } else {
            lastX = Double.NaN;
            speed = 0;
            OptiCore.PVP.reset();
        }
    }

    /** 퍼스펙티브: 키를 누르는 동안(또는 토글) 3인칭으로 바꾸고 카메라만 돌린다 */
    private void updateFreelook(Minecraft mc) {
        boolean want = OptiCore.config.freelook && mc.thePlayer != null
                && (OptiCore.config.freelookToggle ? freelookToggled : freelookKey.isKeyDown());
        if (want && !OptiCore.freelook) {
            savedView = mc.gameSettings.thirdPersonView;
            OptiCore.startFreelook(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
            mc.gameSettings.thirdPersonView = 1;
        } else if (!want && OptiCore.freelook) {
            OptiCore.freelook = false;
            freelookToggled = false;
            if (savedView >= 0) mc.gameSettings.thirdPersonView = savedView;
            savedView = -1;
        }
    }

    /** 토글을 끌 때, 실제로 그 키를 누르고 있지 않으면 눌림 상태를 해제한다 */
    private static void releaseKey(KeyBinding kb) {
        int code = kb.getKeyCode();
        KeyBinding.setKeyBindState(code, code > 0 && Keyboard.isKeyDown(code));
    }
}
