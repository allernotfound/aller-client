package dev.aller.command;

import dev.aller.AllerClient;
import dev.aller.platform.Mc;
import dev.aller.platform.MenuKind;
import dev.aller.platform.ScreenHost;
import dev.aller.screen.LauncherScreen;
import dev.aller.setting.Settings;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.lwjgl.glfw.GLFW;

/**
 * Watches for the launcher shortcut. It is read once a frame rather than through a key handler, so
 * it works the same over a world, Aller's screens and Minecraft's menus without touching any of
 * their input code.
 */
public final class Launcher {
    private static boolean wasDown;

    private Launcher() {}

    public static void poll() {
        Commands.noteServer();
        int chord = AllerClient.options().launcherKey.get();
        boolean down = chord != Settings.Key.NONE && Mc.isDown(chord);
        if (down && !wasDown) pressed(chord);
        wasDown = down;
    }

    private static void pressed(int chord) {
        if (Mc.loadingOverlay()) return;
        // A bare key would fire while typing, so only a chord with Ctrl or Alt works away from the game itself.
        boolean modified = (Settings.Key.mods(chord) & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT)) != 0;
        if (Mc.current() instanceof LauncherScreen open) {
            if (modified) open.close();
            return;
        }
        Screen screen = Mc.screen();
        if (allowed(screen, modified)) Mc.setScreen(new ScreenHost(new LauncherScreen(screen)));
    }

    /** Whether a shortcut may open a panel over this screen. */
    public static boolean allowed(Screen screen, boolean modified) {
        if (screen == null) return Mc.mc().player != null;
        if (!modified) return false;
        if (screen instanceof ScreenHost host) return !host.screen.capturing() && !host.screen.isClosing();
        // Menus only: never over chat, inventories, books or signs, and not while a text box has the keyboard.
        if (!(screen instanceof TitleScreen) && MenuKind.of(screen) == null) return false;
        return !typing(screen);
    }

    private static boolean typing(Screen screen) {
        GuiEventListener focus = screen;
        for (int depth = 0; depth < 8 && focus instanceof ContainerEventHandler container; depth++) focus = container.getFocused();
        return focus instanceof EditBox;
    }
}
