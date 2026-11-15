package dev.opticlient.fabric;

import dev.opticlient.core.OptiCore;
import dev.opticlient.core.PerfBoost;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.option.SimpleOption;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** PvP FPS 부스트: 바꾼 설정은 기억해 두었다가 끌 때 되돌린다 */
final class FabricPerfBoost implements PerfBoost {
    private final List<Runnable> restore = new ArrayList<>();

    private <T> void set(SimpleOption<T> option, T value) {
        T old = option.getValue();
        if (Objects.equals(old, value)) return;
        restore.add(() -> option.setValue(old));
        option.setValue(value);
    }

    @Override
    public void apply(boolean on) {
        GameOptions o = MinecraftClient.getInstance().options;
        if (on) {
            if (!restore.isEmpty()) return;
            set(o.getGraphicsMode(), GraphicsMode.FAST);
            int rd = Math.max(2, OptiCore.config.boostRenderDistance);
            if (o.getViewDistance().getValue() > rd) set(o.getViewDistance(), rd);
            if (o.getSimulationDistance().getValue() > 5) set(o.getSimulationDistance(), 5);
            set(o.getParticles(), ParticlesMode.MINIMAL);
            set(o.getCloudRenderMode(), CloudRenderMode.OFF);
            set(o.getEntityShadows(), false);
            set(o.getAo(), false);
            set(o.getBiomeBlendRadius(), 0);
            set(o.getEnableVsync(), false);
            set(o.getMaxFps(), 260); // 260 = 무제한
            if (o.getEntityDistanceScaling().getValue() > 0.5) set(o.getEntityDistanceScaling(), 0.5); // 멀리 있는 엔티티는 안 그림
            set(o.getMipmapLevels(), 0);
        } else {
            for (int i = restore.size() - 1; i >= 0; i--) restore.get(i).run();
            restore.clear();
        }
        o.write();
    }

    @Override
    public void baseline() {
        GameOptions o = MinecraftClient.getInstance().options;
        o.getMaxFps().setValue(260); // 260 = 무제한
        o.getEnableVsync().setValue(false);
        o.write();
    }
}
