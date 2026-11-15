package dev.opticlient.fabric;

import dev.opticlient.core.HudEditor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Right Shift 로 여는 HUD 편집 화면 */
final class EditorScreen extends Screen {
    final HudEditor editor = new HudEditor();

    EditorScreen() {
        super(Component.literal("OptiClient"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        editor.render(new ModHudRenderer(g, font), MojGameInfo.INSTANCE, mouseX, mouseY, width, height);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // 흐림 효과 없이 게임 화면을 그대로 보여준다
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        editor.mousePressed((int) e.x(), (int) e.y(), MojInput.button(e.button()));
        if (editor.shouldClose()) onClose();
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        editor.mouseDragged((int) e.x(), (int) e.y());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        editor.mouseReleased();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        editor.mouseScrolled((int) mouseX, (int) mouseY, vertical);
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        int cp = e.codepoint();
        if (Character.isBmpCodePoint(cp) && editor.charTyped((char) cp)) return true;
        return super.charTyped(e);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == InputConstants.KEY_BACKSPACE && editor.backspace()) return true;
        if (e.key() == InputConstants.KEY_ESCAPE) {
            if (editor.escape()) onClose();
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public void removed() {
        editor.close();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
