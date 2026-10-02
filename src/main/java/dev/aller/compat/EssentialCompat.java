package dev.aller.compat;

import dev.aller.AllerClient;
import dev.aller.platform.Mc;
import dev.aller.ui.Icons;
import dev.aller.ui.Toasts;
import dev.aller.ui.widget.IconButton;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Essential adds its buttons (host or invite, social, wardrobe, pictures, settings, account) to the
 * vanilla title and pause screens. Aller replaces both, so they would be lost; this offers the same
 * destinations as icon buttons for Aller's menus.
 *
 * <p>Essential has no public API for any of it, so everything is reached by reflection against its
 * internals, one button at a time: a button whose target cannot be found in the installed version
 * is left out, and one that fails when pressed says so in a toast. Nothing here can stop the game.
 */
public final class EssentialCompat {
    private static final String GUI_UTIL = "gg.essential.util.GuiUtil";

    private EssentialCompat() {}

    public static boolean present() {
        return FabricLoader.getInstance().isModLoaded("essential") || FabricLoader.getInstance().isModLoaded("essential-container");
    }

    /**
     * The buttons for one of Aller's menus, in Essential's own order.
     *
     * @param mainMenu the title screen (which also gets the account switcher) rather than the pause menu
     * @param leave    hands the menu over to a screen opened by the given action
     */
    public static List<IconButton> buttons(boolean mainMenu, Consumer<Runnable> leave) {
        List<IconButton> out = new ArrayList<>();
        if (!present() || !enabled()) return out;
        try {
            Class<?> guiUtil = Class.forName(GUI_UTIL, false, EssentialCompat.class.getClassLoader());

            // On a server someone else hosts there is nothing to host, only friends to invite.
            boolean hosting = mainMenu || Mc.mc().hasSingleplayerServer();
            Class<?> sidebar = find("gg.essential.gui.menu.RightSideBarNew");
            if (sidebar != null) {
                Object companion = sidebar.getField("Companion").get(null);
                Method press = companion.getClass().getMethod("hostOrInviteButtonPressed");
                out.add(button(hosting ? Icons.HOST : Icons.INVITE, hosting ? "Host a world" : "Invite friends", () -> press.invoke(companion)));
            }

            Method open = openScreen(guiUtil);
            addScreen(out, leave, open, Icons.SOCIAL, "Social", "gg.essential.gui.friends.SocialMenu");
            addScreen(out, leave, open, Icons.COSMETICS, "Essential wardrobe", "gg.essential.gui.wardrobe.Wardrobe");
            addScreen(out, leave, open, Icons.PICTURES, "Pictures", "gg.essential.gui.screenshot.components.ScreenshotBrowser");

            Class<?> config = find("gg.essential.config.McEssentialConfig");
            if (config != null && open != null) {
                Object instance = config.getField("INSTANCE").get(null);
                Method gui = config.getMethod("gui");
                out.add(button(Icons.SLIDERS, "Essential settings",
                        () -> {
                            leave.accept(() -> open(open, gui.getReturnType(), () -> gui.invoke(instance)));
                            return null;
                        }));
            }

            if (mainMenu) {
                Class<?> modal = find("gg.essential.gui.menu.AccountManagerModal"), manager = find("gg.essential.gui.menu.AccountManager");
                Method push = method(guiUtil, "pushModal", 1);
                if (modal != null && manager != null && push != null) {
                    Object instance = guiUtil.getField("INSTANCE").get(null);
                    out.add(button(Icons.ACCOUNT, "Switch account", () -> push.invoke(instance, function(push.getParameterTypes()[0],
                            args -> construct(modal, args[0], manager.getConstructor().newInstance())))));
                }
            }
        } catch (Throwable e) {
            AllerClient.LOG.warn("Essential is installed but its menu could not be reached; its buttons are left out", e);
        }
        return out;
    }

    /** Essential's own switch for its menu buttons ("Essential menu layout: off"). */
    private static boolean enabled() {
        try {
            return (boolean) Class.forName("gg.essential.handlers.PauseMenuDisplay").getMethod("isEnabled").invoke(null);
        } catch (Throwable e) {
            return true;
        }
    }

    private static void addScreen(List<IconButton> out, Consumer<Runnable> leave, Method open, Icons icon, String label, String className) {
        Class<?> type = find(className);
        if (type == null || open == null) return;
        try {
            var constructor = type.getConstructor();
            out.add(button(icon, label, () -> {
                leave.accept(() -> open(open, type, constructor::newInstance));
                return null;
            }));
        } catch (NoSuchMethodException e) {
            AllerClient.LOG.debug("Essential's {} cannot be opened without arguments", className);
        }
    }

    /** A call into Essential, which may throw anything. */
    private interface Call {
        Object run() throws Throwable;
    }

    private interface Body {
        Object run(Object[] args) throws Throwable;
    }

    private static IconButton button(Icons icon, String label, Call action) {
        return new IconButton(icon, label, () -> {
            try {
                action.run();
            } catch (Throwable e) {
                failed(label, e);
            }
        });
    }

    /** Through Essential's own opener, which first asks for its terms to be accepted where a screen needs that. */
    private static void open(Method open, Class<?> type, Call screen) {
        try {
            open.invoke(null, type, function(open.getParameterTypes()[1], args -> screen.run()));
        } catch (Throwable e) {
            failed(type.getSimpleName(), e);
        }
    }

    private static void failed(String what, Throwable e) {
        AllerClient.LOG.warn("Essential: {} failed", what, e);
        Toasts.warn("Essential did not open that", "This version of Essential may be too new for Aller Client's buttons.");
    }

    /** {@code GuiUtil.openScreen(Class, () -> Screen)}. */
    private static Method openScreen(Class<?> guiUtil) {
        for (Method m : guiUtil.getMethods()) {
            if (m.getName().equals("openScreen") && m.getParameterCount() == 2 && m.getParameterTypes()[0] == Class.class
                    && java.lang.reflect.Modifier.isStatic(m.getModifiers())) return m;
        }
        return null;
    }

    private static Method method(Class<?> type, String name, int parameters) {
        for (Method m : type.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == parameters && m.getParameterTypes()[parameters - 1].isInterface()) return m;
        }
        return null;
    }

    private static Class<?> find(String name) {
        try {
            // Found, not started: a class is initialised only when its button is pressed.
            return Class.forName(name, false, EssentialCompat.class.getClassLoader());
        } catch (Throwable e) {
            AllerClient.LOG.debug("Essential has no {}", name);
            return null;
        }
    }

    /** A Kotlin lambda (whichever {@code FunctionN} interface the method takes), standing in for one written in Kotlin. */
    private static Object function(Class<?> type, Body body) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> "Aller Client lambda";
                };
            }
            try {
                return body.run(args);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause() != null ? e.getCause() : e;
            }
        });
    }

    /**
     * Builds a Kotlin class from its first two arguments, leaving the rest to their defaults: through
     * the constructor Kotlin generates for that (a bit mask of the arguments left out, then a marker).
     */
    private static Object construct(Class<?> type, Object first, Object second) throws ReflectiveOperationException {
        for (var constructor : type.getConstructors()) {
            Class<?>[] p = constructor.getParameterTypes();
            if (p.length == 2) return constructor.newInstance(first, second);
            if (p.length < 4 || p[p.length - 2] != int.class || !p[p.length - 1].getName().endsWith("DefaultConstructorMarker")) continue;
            Object[] args = new Object[p.length];
            args[0] = first;
            args[1] = second;
            int mask = 0;
            for (int i = 2; i < p.length - 2; i++) {
                mask |= 1 << i;
                if (p[i] == boolean.class) args[i] = false;
                else if (p[i].isPrimitive()) args[i] = 0;
            }
            args[p.length - 2] = mask;
            return constructor.newInstance(args);
        }
        throw new NoSuchMethodException(type.getName());
    }

    /** Whether a screen is one of Essential's: they draw themselves, and are neither restyled nor eased in piece by piece. */
    public static boolean owns(Screen screen) {
        return screen.getClass().getName().startsWith("gg.essential.");
    }
}
