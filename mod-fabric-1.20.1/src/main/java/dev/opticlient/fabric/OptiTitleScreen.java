package dev.opticlient.fabric;

import dev.opticlient.core.TitleMenu;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.text.Text;

/** OptiClient 타이틀 화면 (기본 타이틀 화면 대신 열림) */
public final class OptiTitleScreen extends Screen {
    /** "기본 타이틀 화면" 을 눌러 연 기본 화면은 바꿔치기하지 않는다 */
    public static TitleScreen vanilla;
    private final TitleMenu menu = new TitleMenu("Minecraft 1.20.1");

    public OptiTitleScreen() {
        super(Text.literal("OptiClient"));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.draw(() -> menu.render(new FabricHudRenderer(ctx, textRenderer), mouseX, mouseY, width, height, client.getCurrentFps()));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        switch (menu.click((int) mouseX, (int) mouseY)) {
            case TitleMenu.SINGLE -> client.setScreen(new SelectWorldScreen(this));
            case TitleMenu.MULTI -> client.setScreen(new MultiplayerScreen(this));
            case TitleMenu.OPTIONS -> client.setScreen(new OptionsScreen(this, client.options));
            case TitleMenu.QUIT -> client.scheduleStop();
            case TitleMenu.VANILLA -> {
                vanilla = new TitleScreen();
                client.setScreen(vanilla);
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
