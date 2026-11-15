package dev.opticlient.forge;

import dev.opticlient.core.TitleMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;

import java.io.IOException;

/** OptiClient 타이틀 화면 (기본 타이틀 화면 대신 열림) */
public final class OptiTitleGui extends GuiScreen {
    /** "기본 타이틀 화면" 을 눌러 연 기본 화면은 바꿔치기하지 않는다 */
    static GuiMainMenu vanilla;
    private final TitleMenu menu = new TitleMenu("Minecraft 1.8.9");

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        menu.render(new ForgeHudRenderer(mc), mouseX, mouseY, width, height, Minecraft.getDebugFPS());
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (button != 0) return;
        switch (menu.click(mouseX, mouseY)) {
            case TitleMenu.SINGLE:
                mc.displayGuiScreen(new GuiSelectWorld(this));
                break;
            case TitleMenu.MULTI:
                mc.displayGuiScreen(new GuiMultiplayer(this));
                break;
            case TitleMenu.OPTIONS:
                mc.displayGuiScreen(new GuiOptions(this, mc.gameSettings));
                break;
            case TitleMenu.QUIT:
                mc.shutdown();
                break;
            case TitleMenu.VANILLA:
                vanilla = new GuiMainMenu();
                mc.displayGuiScreen(vanilla);
                break;
            default:
                break;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        // ESC 로 닫히지 않게 한다
    }
}
