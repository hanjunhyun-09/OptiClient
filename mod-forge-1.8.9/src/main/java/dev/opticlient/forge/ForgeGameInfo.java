package dev.opticlient.forge;

import dev.opticlient.core.GameInfo;
import dev.opticlient.core.OptiCore;
import dev.opticlient.core.Target;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ForgeGameInfo implements GameInfo {
    static final ForgeGameInfo INSTANCE = new ForgeGameInfo();

    private static Minecraft mc() {
        return Minecraft.getMinecraft();
    }

    @Override
    public int fps() {
        return Minecraft.getDebugFPS();
    }

    @Override
    public double x() {
        return mc().thePlayer.posX;
    }

    @Override
    public double y() {
        return mc().thePlayer.posY;
    }

    @Override
    public double z() {
        return mc().thePlayer.posZ;
    }

    @Override
    public float yaw() {
        return mc().thePlayer.rotationYaw;
    }

    @Override
    public String biome() {
        Minecraft mc = mc();
        if (mc.theWorld == null) return "-";
        return mc.theWorld.getBiomeGenForCoords(new BlockPos(mc.thePlayer)).biomeName;
    }

    @Override
    public int ping() {
        NetHandlerPlayClient nh = mc().getNetHandler();
        if (nh == null) return -1;
        NetworkPlayerInfo info = nh.getPlayerInfo(mc().thePlayer.getUniqueID());
        return info == null ? -1 : info.getResponseTime();
    }

    @Override
    public double speed() {
        return OptiClientForge.speed;
    }

    @Override
    public boolean key(Key k) {
        GameSettings o = mc().gameSettings;
        switch (k) {
            case FORWARD: return o.keyBindForward.isKeyDown();
            case LEFT: return o.keyBindLeft.isKeyDown();
            case BACK: return o.keyBindBack.isKeyDown();
            case RIGHT: return o.keyBindRight.isKeyDown();
            case JUMP: return o.keyBindJump.isKeyDown();
            case SNEAK: return o.keyBindSneak.isKeyDown();
            case ATTACK: return o.keyBindAttack.isKeyDown();
            case USE: return o.keyBindUseItem.isKeyDown();
            default: return false;
        }
    }

    @Override
    public Object armor(int slot) {
        // armorInventory: 0=부츠 ... 3=투구
        return mc().thePlayer.inventory.armorInventory[3 - slot];
    }

    @Override
    public double aimDistance(double max) {
        Minecraft mc = mc();
        if (mc.thePlayer == null || mc.theWorld == null) return -1;
        net.minecraft.entity.Entity self = mc.thePlayer;
        net.minecraft.util.Vec3 eye = self.getPositionEyes(1f);
        net.minecraft.util.Vec3 look = self.getLook(1f);
        net.minecraft.util.Vec3 end = eye.addVector(look.xCoord * max, look.yCoord * max, look.zCoord * max);
        net.minecraft.util.AxisAlignedBB area = self.getEntityBoundingBox()
                .addCoord(look.xCoord * max, look.yCoord * max, look.zCoord * max).expand(1.0, 1.0, 1.0);
        double best = -1;
        for (Object o : mc.theWorld.getEntitiesWithinAABBExcludingEntity(self, area)) {
            if (!(o instanceof EntityLivingBase)) continue;
            EntityLivingBase e = (EntityLivingBase) o;
            if (e.isDead || !e.canBeCollidedWith()) continue;
            float m = e.getCollisionBorderSize();
            net.minecraft.util.AxisAlignedBB b = e.getEntityBoundingBox().expand(m, m, m);
            double d;
            if (b.isVecInside(eye)) {
                d = 0;
            } else {
                net.minecraft.util.MovingObjectPosition hit = b.calculateIntercept(eye, end);
                if (hit == null) continue;
                d = eye.distanceTo(hit.hitVec);
            }
            if (best < 0 || d < best) best = d;
        }
        if (best < 0) return -1;
        // 사이에 블록이 있으면 못 침
        net.minecraft.util.MovingObjectPosition block = mc.theWorld.rayTraceBlocks(eye, end, false, false, false);
        if (block != null && block.hitVec != null && eye.distanceTo(block.hitVec) < best) return -1;
        return best;
    }

    @Override
    public Object slot(int i) {
        if (mc().thePlayer == null) return null;
        return mc().thePlayer.inventory.mainInventory[i];
    }

    @Override
    public Object mainHand() {
        return mc().thePlayer.getHeldItem();
    }

    @Override
    public int damage(Object stack) {
        return ((ItemStack) stack).getItemDamage();
    }

    @Override
    public int maxDamage(Object stack) {
        ItemStack s = (ItemStack) stack;
        return s.isItemStackDamageable() ? s.getMaxDamage() : 0;
    }

    @Override
    public int count(Object stack) {
        return ((ItemStack) stack).stackSize;
    }

    @Override
    public List<String> effects() {
        List<String> out = new ArrayList<String>();
        for (PotionEffect pe : mc().thePlayer.getActivePotionEffects()) {
            out.add(OptiCore.effectLine(I18n.format(pe.getEffectName()), pe.getAmplifier(), pe.getDuration()));
        }
        return out;
    }

    // ---------------- PvP ----------------

    @Override
    public float health() {
        return mc().thePlayer.getHealth();
    }

    @Override
    public float maxHealth() {
        return mc().thePlayer.getMaxHealth();
    }

    @Override
    public int hurtTime() {
        // 흔들림 끄기가 화면을 그리는 동안 0 으로 바꿔 두므로 원래 값을 쓴다
        return OptiClientForge.realHurtTime(mc().thePlayer);
    }

    @Override
    public boolean entity(int id, Target out) {
        Minecraft mc = mc();
        if (mc.theWorld == null) return false;
        Entity e = mc.theWorld.getEntityByID(id);
        if (!(e instanceof EntityLivingBase) || e.isDead) return false;
        EntityLivingBase le = (EntityLivingBase) e;
        if (le.getHealth() <= 0) return false;
        out.name = le.getName();
        out.health = le.getHealth();
        out.maxHealth = le.getMaxHealth();
        out.hurtTime = le.hurtTime;
        out.distance = mc.thePlayer.getDistanceToEntity(le);
        out.player = le instanceof EntityPlayer;
        return true;
    }

    private Map<String, Integer> invCache = new HashMap<String, Integer>();
    private long invAt;

    @Override
    public Map<String, Integer> inventory() {
        long now = System.currentTimeMillis();
        if (now - invAt < 250) return invCache; // 매 프레임 세지 않도록 0.25초마다
        invAt = now;
        Map<String, Integer> m = new HashMap<String, Integer>();
        for (ItemStack s : mc().thePlayer.inventory.mainInventory) {
            if (s == null || s.getItem() == null) continue;
            String key;
            if (s.getItem() instanceof ItemPotion) {
                key = ItemPotion.isSplash(s.getMetadata()) ? "splash_potion" : "potion";
            } else {
                ResourceLocation id = (ResourceLocation) Item.itemRegistry.getNameForObject(s.getItem());
                if (id == null) continue;
                key = id.getResourcePath();
            }
            Integer old = m.get(key);
            m.put(key, (old == null ? 0 : old) + s.stackSize);
        }
        invCache = m;
        return m;
    }

    @Override
    public int food() {
        return mc().thePlayer.getFoodStats().getFoodLevel();
    }

    @Override
    public float saturation() {
        return mc().thePlayer.getFoodStats().getSaturationLevel();
    }

    @Override
    public String server() {
        ServerData sd = mc().getCurrentServerData();
        return sd == null ? null : sd.serverIP;
    }

    @Override
    public boolean firstPerson() {
        return mc().gameSettings.thirdPersonView == 0;
    }
}
