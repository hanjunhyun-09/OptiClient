package dev.opticlient.forge;

import dev.opticlient.core.HudEditor;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;

/** Right Shift 로 여는 HUD 편집 화면 */
final class EditorGui extends GuiScreen {
    final HudEditor editor = new HudEditor();

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        editor.render(new ForgeHudRenderer(mc), ForgeGameInfo.INSTANCE, mouseX, mouseY, width, height);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        editor.mousePressed(mouseX, mouseY, button);
        if (editor.shouldClose()) mc.displayGuiScreen(null);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {
        editor.mouseDragged(mouseX, mouseY);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        editor.mouseReleased();
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int x = Mouse.getEventX() * width / mc.displayWidth;
            int y = height - Mouse.getEventY() * height / mc.displayHeight - 1;
            editor.mouseScrolled(x, y, wheel > 0 ? 1 : -1);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (editor.escape()) mc.displayGuiScreen(null);
            return;
        }
        if (keyCode == Keyboard.KEY_BACK) {
            editor.backspace();
            return;
        }
        editor.charTyped(typedChar);
    }

    @Override
    public void onGuiClosed() {
        editor.close();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
