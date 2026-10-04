package dev.aller.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import dev.aller.AllerClient;
import dev.aller.platform.Tex;
import dev.aller.platform.VanillaMenus;
import dev.aller.ui.Toasts;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;

import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The buttons other mods put on Minecraft's title and pause screens (Flashback's replays, a voice
 * chat's settings), which Aller Client's menus would otherwise lose. Minecraft's own screen is laid
 * out out of sight ({@link VanillaMenus}); whatever on it is not vanilla's is offered in the menu's
 * drawer, and pressing a row presses the real button.
 *
 * <p>Nothing says which mod a button belongs to, so it is worked out: from the widget's class, then
 * the class of what it runs, then a mod id in its text's translation key. One nobody can be found
 * for is still listed, without a name.
 */
public final class ModButtons {
    private ModButtons() {}

    /**
     * @param id   stable between launches, for pinning and hiding
     * @param mod  the owner's name, or null when it could not be told
     * @param icon the owner's icon, or null
     */
    public record Entry(String id, String label, String mod, Tex icon, boolean active, Runnable press) {}

    /** Mods whose buttons Aller Client already offers in its own way. */
    private static final Set<String> COVERED = Set.of("modmenu", "essential", "essential-container", "essential-loader");
    /** Where vanilla's own button texts live; anything else in a vanilla-looking button is taken for a mod's. */
    private static final String[] VANILLA_KEYS = {"menu.", "gui.", "options.", "title.", "narrator.", "mco.", "selectWorld."};

    /** For the harness, whose stand-in buttons belong to Aller Client itself. */
    public static boolean dev;

    private static final Map<String, Optional<ModContainer>> OWNERS = new HashMap<>();
    private static final Map<String, Optional<Tex>> ICONS = new HashMap<>();
    private static boolean failed;

    /** What other mods added to the title screen or the pause menu, in the order they added it. */
    public static List<Entry> collect(boolean title) {
        List<Entry> out = new ArrayList<>();
        if (failed || !AllerClient.options().modButtons.get()) return out;
        List<AbstractWidget> widgets;
        try {
            widgets = VanillaMenus.widgets(title);
        } catch (Throwable e) {
            // Another mod's screen hook threw off screen: not worth trying again every time a menu opens.
            failed = true;
            AllerClient.LOG.warn("Minecraft's own menu could not be laid out; other mods' buttons are left out", e);
            return out;
        }
        Set<String> ids = new LinkedHashSet<>();
        for (AbstractWidget widget : widgets) {
            try {
                Entry entry = entry(title, widget);
                if (entry != null && ids.add(entry.id())) out.add(entry);
            } catch (Throwable e) {
                AllerClient.LOG.debug("Skipped a menu widget of {}", widget.getClass().getName(), e);
            }
        }
        return out;
    }

    private static Entry entry(boolean title, AbstractWidget widget) {
        if (!widget.visible) return null;
        String key = VanillaMenus.key(widget);
        ModContainer owner = mod(widget.getClass());
        if (owner == null) {
            // A vanilla text or spacer: only a button can be a mod's from here on.
            if (!(widget instanceof AbstractButton)) return null;
            Class<?> action = VanillaMenus.action(widget);
            if (action != null) owner = mod(action);
        }
        if (owner == null && key != null) owner = named(key);
        if (owner == null && key != null) {
            for (String prefix : VANILLA_KEYS) if (key.startsWith(prefix)) return null;
        }
        if (owner != null) {
            String id = owner.getMetadata().getId();
            if (COVERED.contains(id) || id.equals("aller") && !dev) return null;
        }
        if (widget.getClass().getName().startsWith("gg.essential.")) return null;

        String name = owner == null ? null : owner.getMetadata().getName();
        String label = widget.getMessage().getString().strip();
        if (label.isEmpty()) label = name != null ? name : "Mod button";
        String id = (title ? "title/" : "pause/") + (owner == null ? "?" : owner.getMetadata().getId()) + "/" + (key != null ? key : label);
        String shown = label;
        return new Entry(id, label, name, owner == null ? null : icon(owner), widget.active, () -> {
            try {
                VanillaMenus.press(widget);
            } catch (Throwable e) {
                AllerClient.LOG.warn("Another mod's menu button failed: {}", shown, e);
                Toasts.warn("That button did not work", name != null ? name + " may need Minecraft's own menu for it." : "It may need Minecraft's own menu.");
            }
        });
    }

    /** The mod a class came from, or null for Minecraft's own and anything unplaced. */
    private static ModContainer mod(Class<?> type) {
        String name = type.getName();
        // A lambda's class is named after the class it was written in.
        int lambda = name.indexOf("$$Lambda");
        if (lambda > 0) name = name.substring(0, lambda);
        if (name.startsWith("net.minecraft.") || name.startsWith("com.mojang.") || name.startsWith("java.")) return null;
        return OWNERS.computeIfAbsent(name, n -> {
            String file = n.replace('.', '/') + ".class";
            // A development run keeps Aller Client's classes outside the mod's own folder.
            if (dev && n.startsWith("dev.aller.")) return FabricLoader.getInstance().getModContainer("aller");
            for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
                String id = mod.getMetadata().getId();
                if (id.equals("minecraft") || id.equals("java")) continue;
                if (mod.findPath(file).isPresent()) return Optional.of(outermost(mod));
            }
            return Optional.empty();
        }).orElse(null);
    }

    /** A library bundled inside a mod counts as that mod. */
    private static ModContainer outermost(ModContainer mod) {
        for (int i = 0; i < 8 && mod.getContainingMod().isPresent(); i++) mod = mod.getContainingMod().get();
        return mod;
    }

    /** A mod whose id is one of the first parts of a translation key ("flashback.open", "gui.flashback.open"). */
    private static ModContainer named(String key) {
        String[] parts = key.split("\\.");
        for (int i = 0; i < Math.min(3, parts.length - 1); i++) {
            if (parts[i].equals("minecraft") || parts[i].equals("java")) continue;
            Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(parts[i]);
            if (mod.isPresent()) return outermost(mod.get());
        }
        return null;
    }

    /** Render thread only. Kept for the session: there are only ever a few. */
    private static Tex icon(ModContainer mod) {
        return ICONS.computeIfAbsent(mod.getMetadata().getId(), id -> {
            try {
                var path = mod.getMetadata().getIconPath(64).flatMap(mod::findPath);
                if (path.isEmpty()) return Optional.empty();
                try (InputStream in = Files.newInputStream(path.get())) {
                    return Optional.of(new Tex("aller-mod-icon-" + id, NativeImage.read(in)));
                }
            } catch (Throwable e) {
                AllerClient.LOG.debug("No icon for {}", id, e);
                return Optional.empty();
            }
        }).orElse(null);
    }

    // ---- pinned and hidden, kept in client.json ---------------------------------------------------

    private static Set<String> pinned, hidden;

    private static void load() {
        if (pinned != null) return;
        pinned = new LinkedHashSet<>();
        hidden = new LinkedHashSet<>();
        try {
            JsonElement saved = AllerClient.config().extra("mod_buttons");
            if (saved == null || !saved.isJsonObject()) return;
            read(saved.getAsJsonObject(), "pinned", pinned);
            read(saved.getAsJsonObject(), "hidden", hidden);
        } catch (RuntimeException e) {
            AllerClient.LOG.warn("Could not read the pinned and hidden mod buttons", e);
        }
    }

    private static void read(JsonObject from, String name, Set<String> into) {
        if (!from.has(name) || !from.get(name).isJsonArray()) return;
        for (JsonElement e : from.getAsJsonArray(name)) {
            if (e.isJsonPrimitive()) into.add(e.getAsString());
        }
    }

    private static void save() {
        JsonObject o = new JsonObject();
        JsonArray p = new JsonArray(), h = new JsonArray();
        pinned.forEach(p::add);
        hidden.forEach(h::add);
        o.add("pinned", p);
        o.add("hidden", h);
        AllerClient.config().setExtra("mod_buttons", o);
    }

    public static boolean pinned(Entry e) {
        load();
        return pinned.contains(e.id());
    }

    public static boolean hidden(Entry e) {
        load();
        return hidden.contains(e.id());
    }

    /** Pinned, a button sits in the strip by itself instead of in the drawer. */
    public static void pin(Entry e, boolean on) {
        load();
        if (on ? pinned.add(e.id()) : pinned.remove(e.id())) save();
    }

    public static void hide(Entry e, boolean on) {
        load();
        boolean changed = on ? hidden.add(e.id()) : hidden.remove(e.id());
        if (on) changed |= pinned.remove(e.id());
        if (changed) save();
    }

    public static boolean anyHidden() {
        load();
        return !hidden.isEmpty();
    }

    /** Brings every hidden button back, on both menus. */
    public static void showAll() {
        load();
        if (hidden.isEmpty()) return;
        hidden.clear();
        save();
    }
}
