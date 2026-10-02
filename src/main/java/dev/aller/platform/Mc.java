package dev.aller.platform;

import dev.aller.ui.AllerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/** Small accessors whose names differ between Minecraft versions. */
public final class Mc {
    private Mc() {}

    public static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static Screen screen() {
        //? if <26.1 {
        /*return mc().screen;
        *///?} else {
        return mc().gui.screen();
        //?}
    }

    public static void setScreen(Screen screen) {
        //? if <26.1 {
        /*mc().setScreen(screen);
        *///?} else {
        mc().gui.setScreen(screen);
        //?}
    }

    public static void open(AllerScreen screen) {
        setScreen(new ScreenHost(screen));
    }

    /** The Aller screen currently shown, or null. */
    public static AllerScreen current() {
        return screen() instanceof ScreenHost host ? host.screen : null;
    }

    public static com.mojang.blaze3d.pipeline.RenderTarget mainTarget() {
        //? if <26.1 {
        /*return mc().getMainRenderTarget();
        *///?} else {
        return mc().gameRenderer.mainRenderTarget();
        //?}
    }

    /** True while the resource-loading overlay covers the screen. */
    public static boolean loadingOverlay() {
        //? if <26.1 {
        /*return mc().getOverlay() != null;
        *///?} else {
        return mc().gui.overlay() != null;
        //?}
    }

    public static long window() {
        //? if <26.1 {
        /*return mc().getWindow().getWindow();
        *///?} else {
        return mc().getWindow().handle();
        //?}
    }

    public static boolean hudHidden() {
        //? if <26.1 {
        /*return mc().options.hideGui;
        *///?} else {
        return mc().gui.hud.isHidden();
        //?}
    }

    /** Mouse position in GUI pixels with sub-pixel precision (vanilla hands screens rounded ints). */
    public static float mouseX() {
        var w = mc().getWindow();
        return (float) (mc().mouseHandler.xpos() * w.getGuiScaledWidth() / Math.max(1, w.getScreenWidth()));
    }

    public static float mouseY() {
        var w = mc().getWindow();
        return (float) (mc().mouseHandler.ypos() * w.getGuiScaledHeight() / Math.max(1, w.getScreenHeight()));
    }

    /** @param code a GLFW key code, or {@code -2 - button} for a mouse button */
    public static boolean isDown(int code) {
        if (code == -1) return false;
        if (code <= -2) return GLFW.glfwGetMouseButton(window(), -2 - code) == GLFW.GLFW_PRESS;
        return GLFW.glfwGetKey(window(), code) == GLFW.GLFW_PRESS;
    }

    /** The key a vanilla binding is set to, in the same encoding as {@link #isDown}. */
    public static int boundCode(net.minecraft.client.KeyMapping mapping) {
        //? if <26.1 {
        /*var key = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(mapping);
        *///?} else {
        var key = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.getBoundKeyOf(mapping);
        //?}
        if (key.getValue() < 0) return -1;
        return key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.MOUSE ? -2 - key.getValue() : key.getValue();
    }

    /** Name of the vanilla control bound to this key ("Sprint"), or null if none is. */
    public static String vanillaUse(int code) {
        if (code == -1) return null;
        for (var mapping : mc().options.keyMappings) {
            if (boundCode(mapping) == code) return net.minecraft.network.chat.Component.translatable(mapping.getName()).getString();
        }
        return null;
    }

    public static boolean shiftDown() {
        return isDown(GLFW.GLFW_KEY_LEFT_SHIFT) || isDown(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    public static boolean ctrlDown() {
        return isDown(GLFW.GLFW_KEY_LEFT_CONTROL) || isDown(GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    public static String clipboard() {
        return mc().keyboardHandler.getClipboard();
    }

    public static void setClipboard(String text) {
        mc().keyboardHandler.setClipboard(text);
    }

    public static String keyName(int code) {
        if (code == -1) return "None";
        if (code <= -2) return "Mouse " + (-1 - code);
        String name = GLFW.glfwGetKeyName(code, 0);
        if (name != null) return name.toUpperCase();
        return switch (code) {
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "R Shift";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "L Shift";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "R Ctrl";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "L Ctrl";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "R Alt";
            case GLFW.GLFW_KEY_LEFT_ALT -> "L Alt";
            case GLFW.GLFW_KEY_SPACE -> "Space";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_ENTER -> "Enter";
            case GLFW.GLFW_KEY_BACKSPACE -> "Backspace";
            case GLFW.GLFW_KEY_CAPS_LOCK -> "Caps";
            case GLFW.GLFW_KEY_UP -> "Up";
            case GLFW.GLFW_KEY_DOWN -> "Down";
            case GLFW.GLFW_KEY_LEFT -> "Left";
            case GLFW.GLFW_KEY_RIGHT -> "Right";
            case GLFW.GLFW_KEY_INSERT -> "Insert";
            case GLFW.GLFW_KEY_DELETE -> "Delete";
            case GLFW.GLFW_KEY_HOME -> "Home";
            case GLFW.GLFW_KEY_END -> "End";
            case GLFW.GLFW_KEY_PAGE_UP -> "Page Up";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "Page Down";
            default -> code >= GLFW.GLFW_KEY_F1 && code <= GLFW.GLFW_KEY_F25
                    ? "F" + (code - GLFW.GLFW_KEY_F1 + 1) : "Key " + code;
        };
    }
}
