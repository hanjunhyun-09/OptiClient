package dev.opticlient.forge;

import dev.opticlient.core.HudRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;
import net.minecraft.item.ItemStack;

final class ForgeHudRenderer implements HudRenderer {
    private final Minecraft mc;

    ForgeHudRenderer(Minecraft mc) {
        this.mc = mc;
    }

    private boolean batching;

    /** 사각형을 하나씩 그리지 않고 모았다가 한 번에 그린다 (글자·아이템 앞에서 flush) */
    @Override
    public void fill(int x1, int y1, int x2, int y2, int argb) {
        if (x2 <= x1 || y2 <= y1) return;
        WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
        if (!batching) {
            wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
            batching = true;
        }
        int a = (argb >>> 24) & 255, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        wr.pos(x1, y2, 0).color(r, g, b, a).endVertex();
        wr.pos(x2, y2, 0).color(r, g, b, a).endVertex();
        wr.pos(x2, y1, 0).color(r, g, b, a).endVertex();
        wr.pos(x1, y1, 0).color(r, g, b, a).endVertex();
    }

    @Override
    public void flush() {
        if (!batching) return;
        batching = false;
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        Tessellator.getInstance().draw();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    @Override
    public void text(String s, int x, int y, int argb, boolean shadow) {
        flush();
        mc.fontRendererObj.drawString(s, (float) x, (float) y, argb, shadow);
    }

    @Override
    public int width(String s) {
        return mc.fontRendererObj.getStringWidth(s);
    }

    @Override
    public int lineHeight() {
        return mc.fontRendererObj.FONT_HEIGHT + 1;
    }

    @Override
    public void pushTransform(int x, int y, float scale) {
        flush();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(scale, scale, 1f);
    }

    @Override
    public void popTransform() {
        flush();
        GlStateManager.popMatrix();
    }

    @Override
    public void scissor(int x1, int y1, int x2, int y2) {
        flush();
        int f = new ScaledResolution(mc).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x1 * f, mc.displayHeight - y2 * f, Math.max(0, (x2 - x1) * f), Math.max(0, (y2 - y1) * f));
    }

    @Override
    public void endScissor() {
        flush();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    @Override
    public void item(Object stack, int x, int y) {
        if (!(stack instanceof ItemStack)) return;
        flush();
        ItemStack s = (ItemStack) stack;
        GlStateManager.pushMatrix();
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableBlend();
        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().renderItemAndEffectIntoGUI(s, x, y);
        mc.getRenderItem().renderItemOverlays(mc.fontRendererObj, s, x, y);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();
    }
}
