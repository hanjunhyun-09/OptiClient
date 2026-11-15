package dev.opticlient.fabric;

import dev.opticlient.core.OptiCore;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;

/** Mojang 이름 버전(1.21.11, 26.x) 공통 PvP 처리 */
final class MojPvp {
    private static CameraType savedCamera;
    private static Boolean hitboxApplied;

    private MojPvp() {
    }

    /** 퍼스펙티브: want 이면 3인칭으로 바꾸고 카메라만 돌린다. 꺼지면 원래 시점으로 */
    static void updateFreelook(Minecraft mc, boolean want) {
        want = want && OptiCore.config.freelook && mc.player != null;
        if (want && !OptiCore.freelook) {
            savedCamera = mc.options.getCameraType();
            OptiCore.startFreelook(mc.player.getYRot(), mc.player.getXRot());
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (!want && OptiCore.freelook) {
            OptiCore.freelook = false;
            if (savedCamera != null) mc.options.setCameraType(savedCamera);
            savedCamera = null;
        }
    }

    /** 히트박스 설정이 바뀌었을 때만 적용 (F3+B 로 직접 켠 상태는 건드리지 않음) */
    static void applyHitboxes(Minecraft mc) {
        boolean want = OptiCore.config.hitboxes;
        if (hitboxApplied == null ? want : hitboxApplied != want) {
            mc.debugEntries.setStatus(DebugScreenEntries.ENTITY_HITBOXES,
                    want ? DebugScreenEntryStatus.ALWAYS_ON : DebugScreenEntryStatus.NEVER);
            hitboxApplied = want;
        }
    }
}
