package dev.opticlient.fabric;

import dev.opticlient.core.HudRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

final class ModHudRenderer implements HudRenderer {
    private final GuiGraphicsExtractor g;
    private final Font font;

    ModHudRenderer(GuiGraphicsExtractor g, Font font) {
        this.g = g;
        this.font = font;
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int argb) {
        g.fill(x1, y1, x2, y2, argb);
    }

    @Override
    public void text(String s, int x, int y, int argb, boolean shadow) {
        g.text(font, s, x, y, argb, shadow);
    }

    @Override
    public int width(String s) {
        return font.width(s);
    }

    @Override
    public int lineHeight() {
        return font.lineHeight + 1;
    }

    @Override
    public void pushTransform(int x, int y, float scale) {
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().scale(scale, scale);
    }

    @Override
    public void popTransform() {
        g.pose().popMatrix();
    }

    @Override
    public void scissor(int x1, int y1, int x2, int y2) {
        g.enableScissor(x1, y1, x2, y2);
    }

    @Override
    public void endScissor() {
        g.disableScissor();
    }

    @Override
    public void nextLayer() {
        g.nextStratum();
    }

    @Override
    public void item(Object stack, int x, int y) {
        if (!(stack instanceof ItemStack s) || s.isEmpty()) return;
        g.item(s, x, y);
        g.itemDecorations(font, s, x, y);
    }
}
