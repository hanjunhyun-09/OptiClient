package dev.opticlient.fabric;

import dev.opticlient.core.OptiCore;
import dev.opticlient.core.PerfBoost;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.server.level.ParticleStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** PvP FPS 부스트 (1.21.11 / 26.x): 바꾼 설정은 기억해 두었다가 끌 때 되돌린다 */
final class MojPerfBoost implements PerfBoost {
    private final List<Runnable> restore = new ArrayList<>();

    private <T> void set(OptionInstance<T> option, T value) {
        T old = option.get();
        if (Objects.equals(old, value)) return;
        restore.add(() -> option.set(old));
        option.set(value);
    }

    @Override
    public void apply(boolean on) {
        Options o = Minecraft.getInstance().options;
        if (on) {
            if (!restore.isEmpty()) return;
            set(o.graphicsPreset(), GraphicsPreset.FAST);
            int rd = Math.max(2, OptiCore.config.boostRenderDistance);
            if (o.renderDistance().get() > rd) set(o.renderDistance(), rd);
            if (o.simulationDistance().get() > 5) set(o.simulationDistance(), 5);
            set(o.particles(), ParticleStatus.MINIMAL);
            set(o.cloudStatus(), CloudStatus.OFF);
            set(o.entityShadows(), false);
            set(o.ambientOcclusion(), false);
            set(o.cutoutLeaves(), false);
            set(o.improvedTransparency(), false);
            set(o.biomeBlendRadius(), 0);
            set(o.chunkSectionFadeInTime(), 0.0);
            set(o.enableVsync(), false);
            set(o.framerateLimit(), 260); // 260 = 무제한
            set(o.inactivityFpsLimit(), InactivityFpsLimit.MINIMIZED); // 잠깐 가만히 있어도 FPS 를 낮추지 않음
            if (o.entityDistanceScaling().get() > 0.5) set(o.entityDistanceScaling(), 0.5); // 멀리 있는 엔티티는 안 그림
            set(o.mipmapLevels(), 0);
        } else {
            for (int i = restore.size() - 1; i >= 0; i--) restore.get(i).run();
            restore.clear();
        }
        o.save();
    }

    @Override
    public void baseline() {
        Options o = Minecraft.getInstance().options;
        o.framerateLimit().set(260); // 260 = 무제한
        o.enableVsync().set(false);
        o.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
        o.save();
    }
}
