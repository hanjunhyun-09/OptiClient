package dev.opticlient.fabric;

import dev.opticlient.core.HudRenderer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

final class FabricHudRenderer implements HudRenderer {
    private final DrawContext ctx;
    private final TextRenderer tr;

    FabricHudRenderer(DrawContext ctx, TextRenderer tr) {
        this.ctx = ctx;
        this.tr = tr;
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int argb) {
        ctx.fill(x1, y1, x2, y2, argb);
    }

    @Override
    public void text(String s, int x, int y, int argb, boolean shadow) {
        ctx.drawText(tr, s, x, y, argb, shadow);
    }

    @Override
    public int width(String s) {
        return tr.getWidth(s);
    }

    @Override
    public int lineHeight() {
        return tr.fontHeight + 1;
    }

    @Override
    public void pushTransform(int x, int y, float scale) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(scale, scale, 1f);
    }

    @Override
    public void popTransform() {
        ctx.getMatrices().pop();
    }

    @Override
    public void scissor(int x1, int y1, int x2, int y2) {
        ctx.draw(); // 잘라내기 전에 모아 둔 것을 먼저 그린다
        ctx.enableScissor(x1, y1, x2, y2);
    }

    @Override
    public void endScissor() {
        ctx.draw();
        ctx.disableScissor();
    }

    @Override
    public void flush() {
        ctx.draw();
    }

    @Override
    public void nextLayer() {
        ctx.draw(); // 쌓인 글자/사각형을 먼저 그리고
        ctx.getMatrices().translate(0, 0, 200); // 글자보다 앞쪽 깊이로 옮겨 위에 덮이게 한다
    }

    @Override
    public void item(Object stack, int x, int y) {
        if (!(stack instanceof ItemStack s) || s.isEmpty()) return;
        ctx.draw(); // 아이템이 배경 아래로 가지 않게 먼저 그린다
        ctx.drawItem(s, x, y);
        ctx.drawItemInSlot(tr, s, x, y);
    }
}
