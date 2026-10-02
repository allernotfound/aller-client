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
        for (AllerScreen under = screen.underlay(); under != null; under = under.underlay()) {
            under.resize(width / under.scale(), height / under.scale());
        }
        Screen vanilla = screen.vanillaUnderlay();
        if (vanilla != null && (vanilla.width != width || vanilla.height != height)) Mc.resize(vanilla, width, height);
    }

    /** Set if the menu underneath could not be drawn while it is not the current screen; it is left out from then on. */
    private boolean vanillaFailed;

    /** Draws a stack of underlays bottom-up: the menu a palette was opened from, then the palette. */
    private static void drawUnder(Canvas c, AllerScreen under) {
        AllerScreen deeper = under.underlay();
        if (deeper != null) drawUnder(c, deeper);
        c.beginScale(under.scale());
        under.drawUnder(c);
        c.endScale();
    }

    private void background(Canvas c, float delta) {
        Screen vanilla = screen.vanillaUnderlay();
        if (vanilla != null && !vanillaFailed) {
            // The menu paints its own background and blur; blurring again in the same frame is not allowed.
            try {
                //? if <26.1 {
                /*vanilla.renderWithTooltip(c.raw(), -1, -1, delta);
                *///?} else {
                vanilla.extractRenderStateWithTooltipAndSubtitles(c.raw(), -1, -1, delta);
                //?}
            } catch (RuntimeException e) {
                vanillaFailed = true;
                AllerClient.LOG.warn("Could not draw {} beneath an Aller Client screen", vanilla.getClass().getName(), e);
            }
            c.layer();
            return;
        }
        AllerScreen under = screen.underlay();
        if (under != null) {
            drawUnder(c, under);
            c.layer();
        }
        boolean something = under != null || minecraft.level != null;
        boolean wanted = AllerClient.options().background.get() == dev.aller.ClientOptions.Background.BLUR;
        if (something && screen.blurBehind() && wanted && screen.blurAmount() > 0.05f && minecraft.options.getMenuBackgroundBlurriness() >= 1) {
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
    public void added() {
        super.added();
        if (opened) screen.reshown();
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
    public void onFilesDrop(java.util.List<java.nio.file.Path> files) {
        screen.filesDropped(files);
    }

    @Override
    public boolean isPauseScreen() {
        return screen.pausesGame();
    }

    //? if <26.1 {
    /*@Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (screen.vanillaBackground()) super.renderBackground(g, mouseX, mouseY, delta);
        else background(new Canvas(g), delta);
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
        else background(new Canvas(g), delta);
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
