package dev.opticlient.fabric;

import dev.opticlient.core.GameInfo;
import dev.opticlient.core.OptiCore;
import dev.opticlient.core.Target;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.registry.Registries;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class FabricGameInfo implements GameInfo {
    static final FabricGameInfo INSTANCE = new FabricGameInfo();

    private static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }

    @Override
    public int fps() {
        return mc().getCurrentFps();
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
        return mc().player.getYaw();
    }

    @Override
    public String biome() {
        MinecraftClient mc = mc();
        if (mc.world == null) return "-";
        return mc.world.getBiome(mc.player.getBlockPos()).getKey().map(k -> k.getValue().getPath()).orElse("?");
    }

    @Override
    public int ping() {
        ClientPlayNetworkHandler nh = mc().getNetworkHandler();
        if (nh == null) return -1;
        PlayerListEntry e = nh.getPlayerListEntry(mc().player.getUuid());
        return e == null ? -1 : e.getLatency();
    }

    @Override
    public double speed() {
        return OptiClientMod.speed;
    }

    @Override
    public boolean key(Key k) {
        GameOptions o = mc().options;
        switch (k) {
            case FORWARD: return o.forwardKey.isPressed();
            case LEFT: return o.leftKey.isPressed();
            case BACK: return o.backKey.isPressed();
            case RIGHT: return o.rightKey.isPressed();
            case JUMP: return o.jumpKey.isPressed();
            case SNEAK: return o.sneakKey.isPressed();
            case ATTACK: return o.attackKey.isPressed();
            case USE: return o.useKey.isPressed();
            default: return false;
        }
    }

    @Override
    public Object armor(int slot) {
        // 인벤토리 armor 리스트는 0=부츠 ... 3=투구
        ItemStack s = mc().player.getInventory().armor.get(3 - slot);
        return s.isEmpty() ? null : s;
    }

    @Override
    public Object slot(int i) {
        if (mc().player == null) return null;
        ItemStack s = mc().player.getInventory().getStack(i);
        return s.isEmpty() ? null : s;
    }

    @Override
    public Object mainHand() {
        ItemStack s = mc().player.getMainHandStack();
        return s.isEmpty() ? null : s;
    }

    @Override
    public int damage(Object stack) {
        return ((ItemStack) stack).getDamage();
    }

    @Override
    public int maxDamage(Object stack) {
        ItemStack s = (ItemStack) stack;
        return s.isDamageable() ? s.getMaxDamage() : 0;
    }

    @Override
    public int count(Object stack) {
        return ((ItemStack) stack).getCount();
    }

    @Override
    public List<String> effects() {
        List<String> out = new ArrayList<>();
        for (StatusEffectInstance e : mc().player.getStatusEffects()) {
            String name = e.getEffectType().getName().getString();
            out.add(OptiCore.effectLine(name, e.getAmplifier(), e.isInfinite() ? -1 : e.getDuration()));
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
        MinecraftClient mc = mc();
        if (mc.world == null) return false;
        Entity e = mc.world.getEntityById(id);
        if (!(e instanceof LivingEntity le) || !le.isAlive()) return false;
        out.name = le.getName().getString();
        out.health = le.getHealth();
        out.maxHealth = le.getMaxHealth();
        out.hurtTime = le.hurtTime;
        out.distance = mc.player.distanceTo(le);
        out.player = le instanceof PlayerEntity;
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
        PlayerInventory inv = mc().player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            m.merge(Registries.ITEM.getId(s.getItem()).getPath(), s.getCount(), Integer::sum);
        }
        invCache = m;
        return m;
    }

    @Override
    public int food() {
        return mc().player.getHungerManager().getFoodLevel();
    }

    @Override
    public float saturation() {
        return mc().player.getHungerManager().getSaturationLevel();
    }

    @Override
    public String server() {
        ServerInfo si = mc().getCurrentServerEntry();
        return si == null ? null : si.address;
    }

    @Override
    public boolean firstPerson() {
        return mc().options.getPerspective().isFirstPerson();
    }

    @Override
    public float attackCooldown() {
        return mc().player.getAttackCooldownProgress(0f);
    }
}
