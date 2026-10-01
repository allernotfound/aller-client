package dev.aller.platform;

import dev.aller.AllerClient;
import dev.aller.ui.AllerScreen;
import dev.aller.ui.anim.Motion;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
//? if <26.1 {
/*import net.minecraft.client.gui.GuiGraphics;
*///?} else {
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?}

/** Adapts an {@link AllerScreen} to whichever Screen API the running Minecraft version has. */
public final class ScreenHost extends Screen {
    public final AllerScreen screen;
    private boolean opened;

    public ScreenHost(AllerScreen screen) {
        super(Component.literal(AllerClient.NAME));
        this.screen = screen;
    }

    @Override
    protected void init() {
        screen.resize(width, height);
        if (!opened) {
            opened = true;
            screen.opened();
        }
    }

    @Override
    public void tick() {
        screen.tick();
    }

    @Override
    public void removed() {
        screen.closed();
    }

    @Override
    public void onClose() {
        screen.close();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // AllerScreen handles Escape itself so the close animation can play
    }

    @Override
    public boolean isPauseScreen() {
        return screen.pausesGame();
    }

    private boolean wantsBlur() {
        return screen.blurBehind() && AllerClient.options().blur.get() && minecraft.level != null
                && minecraft.options.getMenuBackgroundBlurriness() >= 1;
    }

    //? if <26.1 {
    /*@Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (screen.vanillaBackground()) super.renderBackground(g, mouseX, mouseY, delta);
        else if (wantsBlur()) g.blurBeforeThisStratum();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        screen.frame(new Canvas(g), Mc.mouseX(), Mc.mouseY());
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return screen.mouseDown((float) x, (float) y, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return screen.mouseUp((float) x, (float) y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        return screen.mouseScroll((float) x, (float) y, (float) dy);
    }

    @Override
    public boolean keyPressed(int key, int scancode, int mods) {
        return screen.keyDown(key, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        return screen.charTyped(c);
    }
    *///?} else {
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (screen.vanillaBackground()) super.extractBackground(g, mouseX, mouseY, delta);
        else if (wantsBlur()) g.blurBeforeThisStratum();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        screen.frame(new Canvas(g), Mc.mouseX(), Mc.mouseY());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return screen.mouseDown((float) event.x(), (float) event.y(), event.button());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return screen.mouseUp((float) event.x(), (float) event.y(), event.button());
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        return screen.mouseScroll((float) x, (float) y, (float) dy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return screen.keyDown(event.key(), event.modifiers());
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return screen.charTyped(event.codepoint());
    }
    //?}
}
