package dev.opticlient.fabric.mixin;

import dev.opticlient.core.OptiCore;
import dev.opticlient.core.PvpTracker;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 리치·콤보: 공격하는 순간 상대와 거리를 기록 */
@Mixin(MultiPlayerGameMode.class)
public class AttackMixin {
    @Inject(method = "attack", at = @At("HEAD"))
    private void opticlient$attack(Player player, Entity target, CallbackInfo ci) {
        Vec3 e = player.getEyePosition();
        AABB b = target.getBoundingBox();
        OptiCore.PVP.onAttack(target.getId(), PvpTracker.reach(e.x, e.y, e.z, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ));
    }
}
