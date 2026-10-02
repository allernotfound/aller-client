package dev.aller.command;

import dev.aller.module.Module;
import dev.aller.screen.palette.Page;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One thing the launcher can do: open a screen, flip a switch, change a value, send a message.
 * Declared fluently in {@link Commands}; the launcher decides how each is drawn and run from the
 * fields set here.
 */
public final class Command {
    public enum Group {
        NAVIGATE("Go to"), GAME("Game"), OPTION("Minecraft settings"), ALLER("Aller Client"), CUSTOM("Custom"),
        MOD("Mods"), SETTING("Mod settings"), WAYPOINT("Waypoints"), RESULT("Results");

        public final String label;

        Group(String label) {
            this.label = label;
        }
    }

    /** Stable name used to remember recent, pinned and most used commands; null for one-off results. */
    public final String key;
    public final String name;
    public final Group group;
    public String detail = "";
    public String keywords = "";
    /** A short word that takes a value on the same line: "fov 90", "waypoint Base". */
    public String alias;
    public BooleanSupplier available = () -> true;
    /** What Enter does, given the screen the launcher was opened over (null in a world). */
    public Consumer<Screen> run = parent -> {};
    /** Asks for a value first; {@link #run} is not used. */
    public Step step;
    /** Runs once the launcher has closed: anything that opens a screen or must not have the launcher in view. */
    public boolean after;
    /** Leaves the launcher open afterwards (pinning, deleting an entry). */
    public boolean stay;
    /** Needs Enter twice. */
    public boolean danger;
    /** "Press Enter again to ...": what the second Enter does, in lower case. */
    public String confirm = "";
    /** Offered before anything is typed while in a world, or on the menus. */
    public boolean suggestWorld, suggestMenu;
    /** The palette already has its own entry for this. */
    public boolean hidePalette;
    /** For a mod: drawn with its status dot, switch and key. */
    public Module module;
    /** Drawn with a switch showing this state. */
    public BooleanSupplier state;
    /** The current value, shown at the right ("70", "Fancy"). */
    public Supplier<String> value;
    /** A page of the palette this command opens, so the palette can show it in place. */
    public Supplier<Page> page;
    /** Further things to do with this entry, listed by Tab. */
    public Supplier<List<Command>> menu;
    /** A marker colour to draw in place of the arrow (waypoints); 0 for none. */
    public int color;

    public Command(String key, String name, Group group) {
        this.key = key;
        this.name = name;
        this.group = group;
    }

    public Command detail(String text) {
        detail = text;
        return this;
    }

    public Command keywords(String words) {
        keywords = words;
        return this;
    }

    public Command alias(String word) {
        alias = word;
        return this;
    }

    public Command when(BooleanSupplier condition) {
        available = condition;
        return this;
    }

    public Command run(Consumer<Screen> action) {
        run = action;
        return this;
    }

    public Command run(Runnable action) {
        run = parent -> action.run();
        return this;
    }

    public Command step(Step s) {
        step = s;
        return this;
    }

    public Command after() {
        after = true;
        return this;
    }

    public Command stay() {
        stay = true;
        return this;
    }

    public Command danger(String confirmation) {
        danger = true;
        confirm = confirmation;
        return this;
    }

    public Command suggest(boolean inWorld, boolean onMenus) {
        suggestWorld = inWorld;
        suggestMenu = onMenus;
        return this;
    }

    public Command hidePalette() {
        hidePalette = true;
        return this;
    }

    public Command module(Module m) {
        module = m;
        return this;
    }

    public Command state(BooleanSupplier on) {
        state = on;
        return this;
    }

    public Command value(Supplier<String> text) {
        value = text;
        return this;
    }

    public Command page(Supplier<Page> p) {
        page = p;
        return this;
    }

    public Command menu(Supplier<List<Command>> more) {
        menu = more;
        return this;
    }

    public Command color(int argb) {
        color = argb;
        return this;
    }

    /**
     * This command with its value already given ("fov 90" becomes "Set FOV to 90"), or null if it
     * takes none or the text is not a value it accepts.
     */
    public Command withArg(String arg) {
        if (key == null || arg == null || arg.isBlank()) return null;
        if (step instanceof Step.Num n) {
            float typed;
            try {
                typed = Float.parseFloat(arg.trim().replace("%", ""));
            } catch (NumberFormatException e) {
                return null;
            }
            float v = n.clamp(typed);
            return new Command(key + "=" + n.plain(v), "Set " + lowerFirst(name) + " to " + n.format.apply(v), Group.RESULT)
                    .detail("Now " + n.format.apply(n.get.get()))
                    .when(available)
                    .run(() -> {
                        n.set.accept(v);
                        n.saved.run();
                    });
        }
        if (step instanceof Step.Text t && t.inline != null && t.valid.test(arg)) {
            String text = arg.trim();
            Command c = new Command(key + "=" + text, t.inline.apply(text), Group.RESULT).detail(detail).when(available)
                    .run(() -> t.submit.apply(text));
            c.after = after;
            return c;
        }
        return null;
    }

    /** "FOV" stays, "Render distance" becomes "render distance". */
    private static String lowerFirst(String s) {
        if (s.length() > 1 && Character.isUpperCase(s.charAt(1))) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
