package dev.opticlient.fabric;

import dev.opticlient.core.GameInfo;
import dev.opticlient.core.OptiCore;
import dev.opticlient.core.Target;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Mojang 이름을 쓰는 버전(1.21.11, 26.x) 공통 게임 정보 */
final class MojGameInfo implements GameInfo {
    static final MojGameInfo INSTANCE = new MojGameInfo();
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    @Override
    public int fps() {
        return mc().getFps();
    }

    @Override
    public double x() {
        return mc().player.getX();
    }

    @Override
    public double y() {
        return mc().player.getY();
    }

    @Override
    public double z() {
        return mc().player.getZ();
    }

    @Override
    public float yaw() {
        return mc().player.getYRot();
    }

    @Override
    public String biome() {
        Minecraft mc = mc();
        if (mc.level == null) return "-";
        return mc.level.getBiome(mc.player.blockPosition()).unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
    }

    @Override
    public int ping() {
        ClientPacketListener c = mc().getConnection();
        if (c == null) return -1;
        PlayerInfo info = c.getPlayerInfo(mc().player.getUUID());
        return info == null ? -1 : info.getLatency();
    }

    @Override
    public double speed() {
        return OptiClientMod.speed;
    }

    @Override
    public boolean key(Key k) {
        Options o = mc().options;
        switch (k) {
            case FORWARD: return o.keyUp.isDown();
            case LEFT: return o.keyLeft.isDown();
            case BACK: return o.keyDown.isDown();
            case RIGHT: return o.keyRight.isDown();
            case JUMP: return o.keyJump.isDown();
            case SNEAK: return o.keyShift.isDown();
            case ATTACK: return o.keyAttack.isDown();
            case USE: return o.keyUse.isDown();
            default: return false;
        }
    }

    @Override
    public Object armor(int slot) {
        ItemStack s = mc().player.getItemBySlot(ARMOR[slot]);
        return s.isEmpty() ? null : s;
    }

    @Override
    public double aimDistance(double max) {
        Minecraft mc = mc();
        if (mc.player == null || mc.level == null) return -1;
        Entity self = mc.player;
        // 몸이 보는 방향 (퍼스펙티브로 카메라만 돌려도 공격은 몸 방향으로 나감)
        net.minecraft.world.phys.Vec3 eye = self.getEyePosition(1f);
        net.minecraft.world.phys.Vec3 look = self.getViewVector(1f);
        net.minecraft.world.phys.Vec3 end = eye.add(look.scale(max));
        net.minecraft.world.phys.AABB area = self.getBoundingBox().expandTowards(look.scale(max)).inflate(1.0);
        double best = -1;
        for (Entity e : mc.level.getEntities(self, area, x -> x instanceof LivingEntity && x.isAlive() && !x.isSpectator() && x.isPickable())) {
            net.minecraft.world.phys.AABB b = e.getBoundingBox().inflate(e.getPickRadius());
            double d;
            if (b.contains(eye)) {
                d = 0;
            } else {
                java.util.Optional<net.minecraft.world.phys.Vec3> hit = b.clip(eye, end);
                if (hit.isEmpty()) continue;
                d = eye.distanceTo(hit.get());
            }
            if (best < 0 || d < best) best = d;
        }
        if (best < 0) return -1;
        // 사이에 블록이 있으면 못 침
        net.minecraft.world.phys.BlockHitResult block = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, end,
                net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, self));
        if (block.getType() != net.minecraft.world.phys.HitResult.Type.MISS && eye.distanceTo(block.getLocation()) < best) return -1;
        return best;
    }

    @Override
    public Object slot(int i) {
        if (mc().player == null) return null;
        ItemStack s = mc().player.getInventory().getItem(i);
        return s.isEmpty() ? null : s;
    }

    @Override
    public Object mainHand() {
        ItemStack s = mc().player.getMainHandItem();
        return s.isEmpty() ? null : s;
    }

    @Override
    public int damage(Object stack) {
        return ((ItemStack) stack).getDamageValue();
    }

    @Override
    public int maxDamage(Object stack) {
        ItemStack s = (ItemStack) stack;
        return s.isDamageableItem() ? s.getMaxDamage() : 0;
    }

    @Override
    public int count(Object stack) {
        return ((ItemStack) stack).getCount();
    }

    @Override
    public List<String> effects() {
        List<String> out = new ArrayList<>();
        for (MobEffectInstance e : mc().player.getActiveEffects()) {
            String name = e.getEffect().value().getDisplayName().getString();
            out.add(OptiCore.effectLine(name, e.getAmplifier(), e.isInfiniteDuration() ? -1 : e.getDuration()));
        }
        return out;
    }

    // ---------------- PvP ----------------

    @Override
    public float health() {
        return mc().player.getHealth();
    }

    @Override
    public float maxHealth() {
        return mc().player.getMaxHealth();
    }

    @Override
    public int hurtTime() {
        return mc().player.hurtTime;
    }

    @Override
    public boolean entity(int id, Target out) {
        Minecraft mc = mc();
        if (mc.level == null) return false;
        Entity e = mc.level.getEntity(id);
        if (!(e instanceof LivingEntity le) || !le.isAlive()) return false;
        out.name = le.getName().getString();
        out.health = le.getHealth();
        out.maxHealth = le.getMaxHealth();
        out.hurtTime = le.hurtTime;
        out.distance = mc.player.distanceTo(le);
        out.player = le instanceof Player;
        return true;
    }

    private Map<String, Integer> invCache = new HashMap<>();
    private long invAt;

    @Override
    public Map<String, Integer> inventory() {
        long now = System.currentTimeMillis();
        if (now - invAt < 250) return invCache; // 매 프레임 세지 않도록 0.25초마다
        invAt = now;
        Map<String, Integer> m = new HashMap<>();
        Inventory inv = mc().player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            m.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).getPath(), s.getCount(), Integer::sum);
        }
        invCache = m;
        return m;
    }

    @Override
    public int food() {
        return mc().player.getFoodData().getFoodLevel();
    }

    @Override
    public float saturation() {
        return mc().player.getFoodData().getSaturationLevel();
    }

    @Override
    public String server() {
        ServerData sd = mc().getCurrentServer();
        return sd == null ? null : sd.ip;
    }

    @Override
    public boolean firstPerson() {
        return mc().options.getCameraType().isFirstPerson();
    }

    @Override
    public float attackCooldown() {
        return mc().player.getAttackStrengthScale(0f);
    }
}
