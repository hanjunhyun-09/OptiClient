package dev.opticlient.fabric.mixin;

import dev.opticlient.core.OptiCore;
import dev.opticlient.core.PvpTracker;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 리치·콤보: 공격하는 순간 상대와 거리를 기록 */
@Mixin(ClientPlayerInteractionManager.class)
public class AttackMixin {
    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void opticlient$attack(PlayerEntity player, Entity target, CallbackInfo ci) {
        Vec3d e = player.getEyePos();
        Box b = target.getBoundingBox();
        OptiCore.PVP.onAttack(target.getId(), PvpTracker.reach(e.x, e.y, e.z, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ));
    }
}
