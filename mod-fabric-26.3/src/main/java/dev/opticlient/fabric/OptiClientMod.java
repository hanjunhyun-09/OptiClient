package dev.opticlient.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opticlient.core.OptiCore;
import dev.opticlient.fabric.mixin.OptionInstanceAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.resources.Identifier;

public final class OptiClientMod implements ClientModInitializer {
    private static KeyMapping menuKey;
    private static KeyMapping zoomKey;
    private static KeyMapping sprintKey;
    private static KeyMapping freelookKey;
    private static KeyMapping sneakKey;
    private static boolean freelookToggled;

    static double speed;
    private static double lastX = Double.NaN;
    private static double lastZ;
    private static Double savedGamma;

    @Override
    public void onInitializeClient() {
        OptiCore.init(FabricLoader.getInstance().getConfigDir().toFile());
        OptiCore.perf = new MojPerfBoost();

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(OptiCore.MOD_ID, "main"));
        menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.opticlient.menu", InputConstants.Type.KEYBOARD, InputConstants.KEY_RSHIFT, category));
        zoomKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.opticlient.zoom", InputConstants.Type.KEYBOARD, InputConstants.KEY_C, category));
        sprintKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.opticlient.sprint", InputConstants.Type.KEYBOARD, InputConstants.KEY_V, category));
        freelookKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.opticlient.freelook", InputConstants.Type.KEYBOARD, InputConstants.KEY_LALT, category));
        sneakKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.opticlient.sneak", InputConstants.Type.KEYBOARD, InputConstants.KEY_G, category));

        // OptiClient 크로스헤어를 켜면 기본 크로스헤어를 그리지 않는다
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, old -> (g, d) -> {
            if (!OptiCore.config.crosshair) old.extractRenderState(g, d);
        });

        // 채팅 바로 아래 층에 HUD 를 그린다
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(OptiCore.MOD_ID, "hud"), (g, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.gui.hud.isHidden() || mc.gui.hud.getDebugOverlay().showDebugScreen()) return;
            if (mc.gui.screen() instanceof EditorScreen) return;
            OptiCore.renderHud(new ModHudRenderer(g, mc.font), MojGameInfo.INSTANCE, g.guiWidth(), g.guiHeight());
        });

        ClientTickEvents.END_CLIENT_TICK.register(OptiClientMod::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(OptiClientMod::restoreGamma);

        // 개발용 자동 테스트 (배포 jar 에는 DevAutoTest 가 들어가지 않음)
        if (Boolean.getBoolean("opticlient.autotest")) {
            try {
                Class.forName("dev.opticlient.fabric.DevAutoTest").getDeclaredMethod("start").invoke(null);
            } catch (ReflectiveOperationException e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean screenOpen() {
        return Minecraft.getInstance().gui.screen() != null;
    }

    private static void tick(Minecraft mc) {
        while (menuKey.consumeClick()) {
            if (mc.player != null) mc.gui.setScreen(new EditorScreen());
        }
        while (sprintKey.consumeClick()) {
            OptiCore.sprintToggled = !OptiCore.sprintToggled;
            if (!OptiCore.sprintToggled) releaseKey(mc, mc.options.keySprint);
        }
        while (sneakKey.consumeClick()) {
            OptiCore.sneakToggled = !OptiCore.sneakToggled;
            if (!OptiCore.sneakToggled) releaseKey(mc, mc.options.keyShift);
        }
        if (OptiCore.sneakToggled && !OptiCore.config.toggleSneak) {
            OptiCore.sneakToggled = false;
            releaseKey(mc, mc.options.keyShift);
        }
        while (freelookKey.consumeClick()) freelookToggled = !freelookToggled;
        MojPvp.updateFreelook(mc, OptiCore.config.freelookToggle ? freelookToggled : freelookKey.isDown());
        if (!OptiCore.freelook) freelookToggled = freelookToggled && OptiCore.config.freelookToggle && OptiCore.config.freelook;
        OptiCore.zooming = OptiCore.config.zoomEnabled && zoomKey.isDown() && mc.gui.screen() == null;

        OptiCore.tickBaseline();
        if (mc.player != null) {
            OptiCore.tickBoost();
            if (OptiCore.config.toggleSprint && OptiCore.sprintToggled && mc.gui.screen() == null) {
                mc.options.keySprint.setDown(true);
            }
            if (OptiCore.config.toggleSneak && OptiCore.sneakToggled && mc.gui.screen() == null) {
                mc.options.keyShift.setDown(true);
            }
            OptiCore.PVP.tick(MojGameInfo.INSTANCE);
            MojPvp.applyHitboxes(mc);
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

    /** 토글을 끌 때, 실제로 그 키를 누르고 있지 않으면 눌림 상태를 해제한다. */
    private static void releaseKey(Minecraft mc, KeyMapping kb) {
        InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(kb);
        boolean physical = bound.getType() == InputConstants.Type.KEYBOARD
                && InputConstants.isKeyDown(bound.getValue());
        kb.setDown(physical);
    }

    private static void applyGamma(Minecraft mc) {
        OptionInstance<Double> gamma = mc.options.gamma();
        if (OptiCore.config.fullbright) {
            if (savedGamma == null) savedGamma = gamma.get();
            // 슬라이더 범위(0~1) 검사를 우회해 값을 직접 넣는다
            ((OptionInstanceAccessor) (Object) gamma).opticlient$setValue(15.0);
        } else {
            restoreGamma(mc);
        }
    }

    /** options.txt 에 비정상 밝기 값이 저장되지 않도록 원래 값으로 돌린다. */
    public static void restoreGamma(Minecraft mc) {
        if (savedGamma != null) {
            ((OptionInstanceAccessor) (Object) mc.options.gamma()).opticlient$setValue(savedGamma);
            savedGamma = null;
        }
    }
}
