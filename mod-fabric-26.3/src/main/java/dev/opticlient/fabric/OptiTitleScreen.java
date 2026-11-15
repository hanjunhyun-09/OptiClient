package dev.opticlient.fabric;

import dev.opticlient.core.TitleMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** OptiClient 타이틀 화면 (기본 타이틀 화면 대신 열림) */
public final class OptiTitleScreen extends Screen {
    /** "기본 타이틀 화면" 을 눌러 연 기본 화면은 바꿔치기하지 않는다 */
    public static TitleScreen vanilla;
    private final TitleMenu menu = new TitleMenu("Minecraft 26.3");

    public OptiTitleScreen() {
        super(Component.literal("OptiClient"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        menu.render(new ModHudRenderer(g, font), mouseX, mouseY, width, height, minecraft.getFps());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (MojInput.button(e.button()) != 0) return false; // 좌클릭만 (26.x 는 SDL 번호라 직접 비교하면 안 됨)
        switch (menu.click((int) e.x(), (int) e.y())) {
            case TitleMenu.SINGLE -> minecraft.gui.setScreen(new SelectWorldScreen(this));
            case TitleMenu.MULTI -> minecraft.gui.setScreen(new JoinMultiplayerScreen(this));
            case TitleMenu.OPTIONS -> minecraft.gui.setScreen(new OptionsScreen(this, minecraft.options));
            case TitleMenu.QUIT -> minecraft.stop();
            case TitleMenu.VANILLA -> {
                vanilla = new TitleScreen();
                minecraft.gui.setScreen(vanilla);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
