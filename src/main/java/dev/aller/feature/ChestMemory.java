package dev.aller.feature;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.aller.AllerClient;
import dev.aller.module.Modules;
import dev.aller.platform.Canvas;
import dev.aller.platform.Game;
import dev.aller.platform.Mc;
import dev.aller.ui.Colors;
import dev.aller.ui.Theme;
import dev.aller.ui.Toasts;
import dev.aller.ui.font.Fonts;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What was in each container the player opened, kept per world so an item can be found again.
 * Only what the player saw is known: a chest someone else has emptied since still reads as it did.
 * A container is tied to the block that was used just before its screen opened.
 */
public final class ChestMemory {
    public static final class Item {
        public String id = "", name = "";
        public int count;
    }

    public static final class Chest {
        public String dimension = "overworld", block = "";
        public int x, y, z;
        public long seen;
        public List<Item> items = new ArrayList<>();
    }

    private record Mark(Chest chest, int count) {}

    private static final Gson GSON = new GsonBuilder().create();
    private static final int MAX_CHESTS = 4000;
    private static List<Chest> chests = new ArrayList<>();
    private static String loadedKey;
    private static BlockPos used;
    private static long usedAt;
    private static Screen watching;
    private static Chest open;
    private static final List<Mark> marks = new ArrayList<>();
    private static String marked = "";
    private static long markedUntil;

    private ChestMemory() {}

    private static List<Chest> all() {
        String key = Game.worldKey();
        if (key.equals(loadedKey)) return chests;
        loadedKey = key;
        chests = new ArrayList<>();
        marks.clear();
        Path file = file(key);
        if (Files.exists(file)) {
            try {
                List<Chest> loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), new TypeToken<List<Chest>>() {}.getType());
                if (loaded != null) {
                    for (Chest c : loaded) {
                        if (c == null || c.dimension == null) continue;
                        if (c.items == null) c.items = new ArrayList<>();
                        c.items.removeIf(i -> i == null || i.id == null || i.name == null);
                        chests.add(c);
                    }
                }
            } catch (Exception e) {
                AllerClient.LOG.warn("Could not read the chest memory for {}", key, e);
            }
        }
        return chests;
    }

    private static Path file(String key) {
        return AllerClient.config().dir().resolve("chests").resolve(key + ".json");
    }

    private static void save() {
        if (loadedKey == null) return;
        try {
            Path file = file(loadedKey);
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(chests), StandardCharsets.UTF_8);
        } catch (Exception e) {
            AllerClient.LOG.warn("Could not save the chest memory", e);
        }
    }

    /** The player used a block: if a container screen follows, this is where it stands. */
    public static void used(BlockPos pos) {
        used = pos.immutable();
        usedAt = System.currentTimeMillis();
    }

    /** Once a frame, with the screen in front: notes a container opening and what it held when it shut. */
    static void poll(Screen now) {
        if (now == watching) return;
        if (open != null && watching instanceof AbstractContainerScreen<?> shut) {
            snapshot(shut, open);
            open = null;
            save();
        }
        watching = now;
        if (!Modules.CHEST_MEMORY.enabled() || !Game.inWorld() || !(now instanceof AbstractContainerScreen<?> screen)) return;
        if (used == null || System.currentTimeMillis() - usedAt > 3000) return;
        // The player's own inventory has no block; a screen with no slots of its own holds nothing.
        boolean own = false;
        for (Slot slot : screen.getMenu().slots) own |= !(slot.container instanceof Inventory);
        if (!own) return;
        String dim = Game.dimensionId();
        Chest chest = null;
        for (Chest c : all()) if (c.x == used.getX() && c.y == used.getY() && c.z == used.getZ() && c.dimension.equals(dim)) chest = c;
        if (chest == null) {
            chest = new Chest();
            chest.dimension = dim;
            chest.x = used.getX();
            chest.y = used.getY();
            chest.z = used.getZ();
            all().add(chest);
            while (chests.size() > MAX_CHESTS) chests.remove(0);
        }
        chest.block = Game.level().getBlockState(used).getBlock().getName().getString();
        open = chest;
        used = null;
        // Having walked up and opened it, the player has found it.
        marks.removeIf(m -> m.chest == open);
    }

    private static void snapshot(AbstractContainerScreen<?> screen, Chest into) {
        Map<String, Item> totals = new LinkedHashMap<>();
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            count(slot.getItem(), totals);
        }
        into.items = new ArrayList<>(totals.values());
        into.seen = System.currentTimeMillis();
    }

    private static void count(ItemStack stack, Map<String, Item> totals) {
        if (stack.isEmpty()) return;
        String name = stack.getHoverName().getString();
        Item item = totals.computeIfAbsent(Game.itemId(stack) + "|" + name, k -> new Item());
        item.id = Game.itemId(stack);
        item.name = name;
        item.count += stack.getCount();
        // What is packed in a shulker box counts too, so a search finds it.
        for (ItemStack inside : Containers.contents(stack)) count(inside, totals);
    }

    /** Points at every remembered container in this dimension holding something that matches, and says how many there are. */
    public static void find(String text) {
        String query = text.strip().toLowerCase(Locale.ROOT);
        marks.clear();
        if (query.isEmpty() || !Game.inWorld()) return;
        String dim = Game.dimensionId();
        int elsewhere = 0, total = 0;
        for (Chest chest : new ArrayList<>(all())) {
            int count = 0;
            for (Item item : chest.items) {
                if (item.name.toLowerCase(Locale.ROOT).contains(query) || item.id.contains(query.replace(' ', '_'))) count += item.count;
            }
            if (count == 0) continue;
            if (!chest.dimension.equals(dim)) {
                elsewhere++;
                continue;
            }
            // A container that has since been broken is forgotten once the place is in view again.
            BlockPos pos = new BlockPos(chest.x, chest.y, chest.z);
            if (Game.level().isLoaded(pos) && Game.level().getBlockState(pos).isAir()) {
                chests.remove(chest);
                continue;
            }
            marks.add(new Mark(chest, count));
            total += count;
        }
        marked = text.strip();
        markedUntil = System.currentTimeMillis() + (long) (Modules.CHEST_MEMORY.markTime.get() * 1000);
        if (marks.isEmpty()) {
            Toasts.info("Not in any chest you opened", elsewhere > 0 ? "But " + elsewhere + " in another dimension hold it" : marked);
        } else {
            Toasts.info(total + " in " + marks.size() + (marks.size() == 1 ? " container" : " containers"), "Marked for " + Math.round(Modules.CHEST_MEMORY.markTime.get()) + " seconds");
        }
    }

    public static boolean marking() {
        return !marks.isEmpty();
    }

    public static void clearMarks() {
        marks.clear();
    }

    /** How many containers are remembered in this world. */
    public static int known() {
        return Game.inWorld() ? all().size() : 0;
    }

    public static void forgetWorld() {
        all().clear();
        marks.clear();
        save();
    }

    /** The marks of the last search, projected onto the HUD like waypoints. */
    public static void draw(Canvas c) {
        if (marks.isEmpty()) return;
        if (System.currentTimeMillis() > markedUntil || !Modules.CHEST_MEMORY.enabled()) {
            marks.clear();
            return;
        }
        var p = Game.player();
        String dim = Game.dimensionId();
        float sw = c.width(), sh = c.height();
        int accent = Theme.accent();
        for (Mark mark : marks) {
            Chest chest = mark.chest;
            if (!chest.dimension.equals(dim)) continue;
            double dx = chest.x + 0.5 - p.getX(), dy = chest.y + 0.5 - p.getY(), dz = chest.z + 0.5 - p.getZ();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            float[] at = View.project(chest.x + 0.5, chest.y + 0.5, chest.z + 0.5, sw, sh);
            float x = at[0], y = at[1];
            boolean front = at[2] > 0.05f;
            float margin = 18;
            if (!front || x < margin || x > sw - margin || y < margin || y > sh - margin) {
                // Off screen: an arrowhead at the edge, pointing the way.
                float ox = x - sw / 2, oy = y - sh / 2;
                if (Math.abs(ox) < 0.001f && Math.abs(oy) < 0.001f) oy = 1;
                float k = Math.min((sw / 2 - margin) / Math.max(Math.abs(ox), 0.001f), (sh / 2 - margin) / Math.max(Math.abs(oy), 0.001f));
                x = sw / 2 + ox * k;
                y = sh / 2 + oy * k;
                c.push();
                c.rotate((float) (Math.atan2(oy, ox) + Math.PI / 2), x, y);
                c.polygon(x, y, 4.6f, 3, 0.7f, Colors.withAlpha(Colors.BLACK, 0.5f));
                c.polygon(x, y, 3.8f, 3, 0.6f, accent);
                c.pop();
                continue;
            }
            c.shadow(x - 4, y - 4, 8, 8, 4, 7, Colors.withAlpha(accent, 0.6f));
            c.polygon(x, y, 5.6f, 4, 1f, Colors.withAlpha(Colors.BLACK, 0.5f));
            c.polygon(x, y, 4.6f, 4, 0.9f, accent);
            String label = mark.count + " " + marked;
            if (Modules.CHEST_MEMORY.distance.get()) label += "  " + Waypoints.distanceText(dist);
            float tw = Fonts.MEDIUM.widthAny(label, 7.5f);
            c.rect(x - tw / 2 - 5, y + 9, tw + 10, 13.5f, 6.75f, Theme.GLASS_HUD);
            c.textAny(Fonts.MEDIUM, label, x - tw / 2, y + 9 + (13.5f - Fonts.MEDIUM.height(7.5f)) / 2, 7.5f, Theme.TEXT);
        }
    }
}
