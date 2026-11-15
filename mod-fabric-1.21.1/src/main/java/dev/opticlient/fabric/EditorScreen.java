package dev.opticlient.fabric;

import dev.opticlient.core.HudEditor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Right Shift 로 여는 HUD 편집 화면 */
final class EditorScreen extends Screen {
    final HudEditor editor = new HudEditor();

    EditorScreen() {
        super(Text.literal("OptiClient"));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.getMatrices().push();
        ctx.draw(() -> editor.render(new FabricHudRenderer(ctx, textRenderer), FabricGameInfo.INSTANCE, mouseX, mouseY, width, height));
        ctx.getMatrices().pop();
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // 흐림 효과 없이 게임 화면을 그대로 보여준다
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        editor.mousePressed((int) mouseX, (int) mouseY, button);
        if (editor.shouldClose()) close();
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        editor.mouseDragged((int) mouseX, (int) mouseY);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        editor.mouseReleased();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        editor.mouseScrolled((int) mouseX, (int) mouseY, vertical);
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return editor.charTyped(chr) || super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && editor.backspace()) return true;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (editor.escape()) close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        editor.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
