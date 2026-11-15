package dev.opticlient.fabric;

import dev.opticlient.core.OptiCore;
import dev.opticlient.fabric.mixin.SimpleOptionAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class OptiClientMod implements ClientModInitializer {
    private static KeyBinding menuKey;
    private static KeyBinding zoomKey;
    private static KeyBinding sprintKey;
    private static KeyBinding freelookKey;
    private static KeyBinding sneakKey;
    private static Perspective savedPerspective;
    private static boolean freelookToggled;
    private static Boolean hitboxApplied;

    static double speed;
    private static double lastX = Double.NaN;
    private static double lastZ;
    private static Double savedGamma;

    @Override
    public void onInitializeClient() {
        OptiCore.init(FabricLoader.getInstance().getConfigDir().toFile());
        OptiCore.perf = new FabricPerfBoost();

        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.opticlient.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.opticlient"));
        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.opticlient.zoom", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, "category.opticlient"));
        sprintKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.opticlient.sprint", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.opticlient"));
        freelookKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.opticlient.freelook", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, "category.opticlient"));
        sneakKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.opticlient.sneak", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.opticlient"));

        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden || mc.getDebugHud().shouldShowDebugHud()) return;
            if (mc.currentScreen instanceof EditorScreen) return;
            // 사각형·글자를 하나씩 바로 그리지 않고 한 번에 묶어 그린다 (그리기 호출 수백 번 → 몇 번)
            ctx.draw(() -> OptiCore.renderHud(new FabricHudRenderer(ctx, mc.textRenderer), FabricGameInfo.INSTANCE,
                    ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight()));
        });

        ClientTickEvents.END_CLIENT_TICK.register(OptiClientMod::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> restoreGamma(mc));

        // 개발용 자동 테스트 (배포 jar 에는 DevAutoTest 가 들어가지 않음)
        if (Boolean.getBoolean("opticlient.autotest")) {
            try {
                Class.forName("dev.opticlient.fabric.DevAutoTest").getDeclaredMethod("start").invoke(null);
            } catch (ReflectiveOperationException e) {
                e.printStackTrace();
            }
        }
    }

    private static void tick(MinecraftClient mc) {
        while (menuKey.wasPressed()) {
            if (mc.player != null) mc.setScreen(new EditorScreen());
        }
        while (sprintKey.wasPressed()) {
            OptiCore.sprintToggled = !OptiCore.sprintToggled;
            if (!OptiCore.sprintToggled) releaseKey(mc, mc.options.sprintKey);
        }
        while (sneakKey.wasPressed()) {
            OptiCore.sneakToggled = !OptiCore.sneakToggled;
            if (!OptiCore.sneakToggled) releaseKey(mc, mc.options.sneakKey);
        }
        if (OptiCore.sneakToggled && !OptiCore.config.toggleSneak) {
            OptiCore.sneakToggled = false;
            releaseKey(mc, mc.options.sneakKey);
        }
        while (freelookKey.wasPressed()) freelookToggled = !freelookToggled;
        updateFreelook(mc);
        OptiCore.zooming = OptiCore.config.zoomEnabled && zoomKey.isPressed() && mc.currentScreen == null;

        OptiCore.tickBaseline();
        if (mc.player != null) {
            OptiCore.tickBoost();
            if (OptiCore.config.toggleSprint && OptiCore.sprintToggled && mc.currentScreen == null) {
                mc.options.sprintKey.setPressed(true);
            }
            if (OptiCore.config.toggleSneak && OptiCore.sneakToggled && mc.currentScreen == null) {
                mc.options.sneakKey.setPressed(true);
            }
            OptiCore.PVP.tick(FabricGameInfo.INSTANCE);
            applyHitboxes(mc);
            double x = mc.player.getX();
            double z = mc.player.getZ();
            if (!Double.isNaN(lastX)) speed = Math.sqrt((x - lastX) * (x - lastX) + (z - lastZ) * (z - lastZ)) * 20.0;
            lastX = x;
            lastZ = z;
        } else {
            lastX = Double.NaN;
            speed = 0;
            OptiCore.PVP.reset();
        }
        applyGamma(mc);
    }

    /** 퍼스펙티브: 키를 누르는 동안(또는 토글) 3인칭으로 바꾸고 카메라만 돌린다 */
    private static void updateFreelook(MinecraftClient mc) {
        boolean want = OptiCore.config.freelook && mc.player != null
                && (OptiCore.config.freelookToggle ? freelookToggled : freelookKey.isPressed());
        if (want && !OptiCore.freelook) {
            savedPerspective = mc.options.getPerspective();
            OptiCore.startFreelook(mc.player.getYaw(), mc.player.getPitch());
            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        } else if (!want && OptiCore.freelook) {
            OptiCore.freelook = false;
            freelookToggled = false;
            if (savedPerspective != null) mc.options.setPerspective(savedPerspective);
            savedPerspective = null;
        }
    }

    /** 히트박스 설정이 바뀌었을 때만 적용 (F3+B 로 직접 켠 상태는 건드리지 않음) */
    private static void applyHitboxes(MinecraftClient mc) {
        boolean want = OptiCore.config.hitboxes;
        if (hitboxApplied == null ? want : hitboxApplied != want) {
            mc.getEntityRenderDispatcher().setRenderHitboxes(want);
            hitboxApplied = want;
        }
    }

    /** 토글을 끌 때, 실제로 그 키를 누르고 있지 않으면 눌림 상태를 해제한다. */
    private static void releaseKey(MinecraftClient mc, KeyBinding kb) {
        InputUtil.Key bound = KeyBindingHelper.getBoundKeyOf(kb);
        boolean physical = bound.getCategory() == InputUtil.Type.KEYSYM
                && InputUtil.isKeyPressed(mc.getWindow().getHandle(), bound.getCode());
        kb.setPressed(physical);
    }

    @SuppressWarnings("unchecked")
    private static void applyGamma(MinecraftClient mc) {
        SimpleOption<Double> gamma = mc.options.getGamma();
        if (OptiCore.config.fullbright) {
            if (savedGamma == null) savedGamma = gamma.getValue();
            // 슬라이더 범위(0~1) 검사를 우회해 값을 직접 넣는다
            ((SimpleOptionAccessor) (Object) gamma).opticlient$setValue(15.0);
        } else {
            restoreGamma(mc);
        }
    }

    /** options.txt 에 비정상 밝기 값이 저장되지 않도록 원래 값으로 돌린다. */
    public static void restoreGamma(MinecraftClient mc) {
        if (savedGamma != null) {
            ((SimpleOptionAccessor) (Object) mc.options.getGamma()).opticlient$setValue(savedGamma);
            savedGamma = null;
        }
    }
}
