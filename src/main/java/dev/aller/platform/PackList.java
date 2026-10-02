package dev.aller.platform;

import dev.aller.feature.store.Store;
import dev.aller.mixin.PackSelectionScreenAccessor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.server.packs.repository.Pack;

import java.util.ArrayList;
import java.util.List;

/**
 * The game's own resource pack list, as far as the store reaches into it: reading the folder again
 * when the store closes, keeping what was just downloaded at the top of the available column, and
 * putting an updated pack where the file it replaced was switched on.
 */
public final class PackList {
    private PackList() {}

    private record Swap(int index, String oldId, String newId) {}

    private static final List<Swap> swaps = new ArrayList<>();

    /** A pack in the folder is known to the game as "file/" and its file name. */
    private static final String PREFIX = "file/";

    /** Whether this is the list of resource packs: a world's data packs are picked on the same screen. */
    public static boolean is(Screen screen) {
        return screen instanceof PackSelectionScreen
                && ((PackSelectionScreenAccessor) screen).aller$packDir().equals(Mc.mc().getResourcePackDirectory());
    }

    /** Has the list read its folder again now, rather than when it next notices. */
    public static void reload(Screen screen) {
        if (is(screen)) ((PackSelectionScreenAccessor) screen).aller$reload();
    }

    public static String filename(String packId) {
        return packId.startsWith(PREFIX) ? packId.substring(PREFIX.length()) : null;
    }

    /** The list is about to read the folder: note where the packs an update replaced are switched on. */
    public static void reading(Object repository, List<Pack> selected) {
        swaps.clear();
        if (repository != Mc.mc().getResourcePackRepository()) return;
        for (int i = 0; i < selected.size(); i++) {
            String name = filename(selected.get(i).getId());
            String next = name == null ? null : Store.replacement(name);
            if (next != null) swaps.add(new Swap(i, selected.get(i).getId(), PREFIX + next));
        }
    }

    /** The list has read the folder. */
    public static void read(Object repository, List<Pack> selected, List<Pack> unselected) {
        if (repository != Mc.mc().getResourcePackRepository()) return;
        for (Swap swap : swaps) {
            Pack next = find(unselected, swap.newId);
            if (next == null) continue;
            unselected.remove(next);
            // The old file stays put while the game has it open; it gives up its place all the same.
            Pack old = find(selected, swap.oldId);
            if (old != null) {
                selected.set(selected.indexOf(old), next);
                unselected.add(old);
            } else {
                selected.add(Math.min(swap.index, selected.size()), next);
            }
        }
        swaps.clear();
        List<String> fresh = Store.fresh();
        for (int i = fresh.size() - 1; i >= 0; i--) {
            Pack pack = find(unselected, PREFIX + fresh.get(i));
            if (pack != null) {
                unselected.remove(pack);
                unselected.add(0, pack);
            }
        }
    }

    private static Pack find(List<Pack> packs, String id) {
        for (Pack pack : packs) if (pack.getId().equals(id)) return pack;
        return null;
    }
}
