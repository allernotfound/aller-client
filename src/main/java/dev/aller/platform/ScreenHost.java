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

    private float scale = 1f;

    /** Picks up a changed UI scale, but never mid-drag: the slider being dragged would move under the cursor. */
    private void syncScale() {
        float wanted = screen.scale();
        if (wanted != scale && org.lwjgl.glfw.GLFW.glfwGetMouseButton(Mc.window(), 0) != org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            scale = wanted;
            layout();
        }
    }

    private void layout() {
        screen.resize(width / scale, height / scale);
        AllerScreen under = screen.underlay();
        if (under != null) under.resize(width / under.scale(), height / under.scale());
    }

    private void background(Canvas c) {
        AllerScreen under = screen.underlay();
        if (under != null) {
            c.beginScale(under.scale());
            under.drawUnder(c);
            c.endScale();
            c.layer();
        }
        boolean something = under != null || minecraft.level != null;
        if (something && screen.blurBehind() && AllerClient.options().blur.get() && minecraft.options.getMenuBackgroundBlurriness() >= 1) {
            c.blurBehind();
        }
    }

    private void foreground(Canvas c) {
        syncScale();
        c.beginScale(scale);
        screen.frame(c, Mc.mouseX() / scale, Mc.mouseY() / scale);
        c.endScale();
    }

    @Override
    protected void init() {
        scale = screen.scale();
        layout();
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

    //? if <26.1 {
    /*@Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (screen.vanillaBackground()) super.renderBackground(g, mouseX, mouseY, delta);
        else background(new Canvas(g));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        foreground(new Canvas(g));
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return screen.mouseDown((float) x / scale, (float) y / scale, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return screen.mouseUp((float) x / scale, (float) y / scale, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        return screen.mouseScroll((float) x / scale, (float) y / scale, (float) dy);
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
        else background(new Canvas(g));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        foreground(new Canvas(g));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return screen.mouseDown((float) event.x() / scale, (float) event.y() / scale, event.button());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return screen.mouseUp((float) event.x() / scale, (float) event.y() / scale, event.button());
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        return screen.mouseScroll((float) x / scale, (float) y / scale, (float) dy);
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
