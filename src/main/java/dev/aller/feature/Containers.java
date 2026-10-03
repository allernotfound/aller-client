package dev.aller.feature;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import dev.aller.AllerClient;
import dev.aller.module.Modules;
import dev.aller.platform.Canvas;
import dev.aller.platform.Game;
import dev.aller.platform.Mc;
import dev.aller.ui.Colors;
import dev.aller.ui.Icons;
import dev.aller.ui.Theme;
import dev.aller.ui.Toasts;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.font.Fonts;
import dev.aller.ui.widget.TextField;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * What the item mods add to Minecraft's inventory and container screens, which stay vanilla: the
 * search that dims what does not match, the marks on locked slots, and the picture of a hovered
 * shulker box or map. The screen's mixin reports each frame's layout; keys and clicks come through
 * {@code Hooks}.
 */
public final class Containers {
    private static final float FIELD_H = 15;

    private static final TextField field = new TextField("Search items");
    private static final Spring open = Spring.snappy(0);
    private static Screen screen;
    private static int left, top, width;
    private static Slot hovered;
    private static int drawnFrame = -1, frame;
    private static float fieldX, fieldY, fieldW;
    private static Set<Integer> locks;
    private static long lockToastAt;

    static {
        field.textSize = 8f;
        field.maxLength = 40;
    }

    private Containers() {}

    // ---- per frame -----------------------------------------------------------------------------

    /** Once a frame, before anything is drawn. */
    public static void poll() {
        frame++;
        Screen now = Mc.screen();
        if (now != screen && !(now instanceof AbstractContainerScreen<?>)) {
            screen = null;
            hovered = null;
            field.focused = false;
            if (!Modules.INVENTORY_SEARCH.keep.get()) field.setText("");
        }
        ChestMemory.poll(now);
    }

    private static boolean live() {
        return screen != null && screen == Mc.screen() && drawnFrame >= frame - 1;
    }

    private static boolean searchable() {
        return Modules.INVENTORY_SEARCH.enabled() && !(screen instanceof CreativeModeInventoryScreen);
    }

    /** A container screen has drawn its slots: the search and the lock marks go over them, under any tooltip. */
    public static void drawn(Canvas c, AbstractContainerScreen<?> s, int leftPos, int topPos, int imageWidth, Slot hoveredSlot) {
        screen = s;
        left = leftPos;
        top = topPos;
        width = imageWidth;
        hovered = devHover >= 0 && devHover < s.getMenu().slots.size() ? s.getMenu().slots.get(devHover) : hoveredSlot;
        drawnFrame = frame;

        if (Modules.ITEM_LOCK.enabled()) {
            for (Slot slot : s.getMenu().slots) {
                if (!slot.isActive() || !locked(slot)) continue;
                float x = left + slot.x, y = top + slot.y;
                c.stroke(x - 0.5f, y - 0.5f, 17, 17, 2, 1, Colors.withAlpha(Theme.accent(), 0.9f));
                c.circle(x + 2.5f, y + 2.5f, 3.6f, 0xE6100E18);
                c.icon(Icons.LOCK, x + 2.5f, y + 2.5f, 5.5f, Theme.accent());
            }
        }
        if (!searchable()) return;

        String query = field.text.strip().toLowerCase(Locale.ROOT);
        if (!query.isEmpty()) {
            for (Slot slot : s.getMenu().slots) {
                if (!slot.isActive()) continue;
                ItemStack stack = slot.getItem();
                float x = left + slot.x, y = top + slot.y;
                if (stack.isEmpty() || !matches(stack, query, Modules.INVENTORY_SEARCH.inside.get())) c.rect(x - 1, y - 1, 18, 18, 0, 0xC8101014);
                else c.stroke(x - 1, y - 1, 18, 18, 2, 1, Theme.accent());
            }
        }

        // A search button above the top right corner, which opens into the field.
        boolean shown = field.focused || !field.text.isEmpty();
        float t = Math.clamp(open.target(shown ? 1 : 0).update(), 0f, 1f);
        fieldW = FIELD_H + (Math.min(120, width) - FIELD_H) * t;
        fieldX = left + width - fieldW;
        fieldY = top - FIELD_H - 3;
        if (fieldY < 2) fieldY = 2;
        Theme.chip(c, fieldX, fieldY, fieldW, FIELD_H, FIELD_H / 2.6f, 0xE6100E18);
        boolean over = Mc.mouseX() >= fieldX && Mc.mouseX() < fieldX + fieldW && Mc.mouseY() >= fieldY && Mc.mouseY() < fieldY + FIELD_H;
        c.icon(Icons.SEARCH, fieldX + FIELD_H / 2, fieldY + FIELD_H / 2, 8, field.focused || over ? Theme.accent() : Theme.TEXT_DIM);
        if (t > 0.5f) {
            field.bare = true;
            field.bounds(fieldX + FIELD_H, fieldY, fieldW - FIELD_H - 5, FIELD_H);
            c.pushAlpha((t - 0.5f) * 2);
            field.draw(c, Mc.mouseX(), Mc.mouseY());
            c.popAlpha();
        } else if (over) {
            String hint = Mc.keyName(Modules.INVENTORY_SEARCH.shortcut.get());
            float w = Fonts.MEDIUM.width(hint, 6.5f) + 10;
            Theme.chip(c, fieldX - w - 3, fieldY + 1, w, FIELD_H - 2, 4, 0xE6100E18);
            c.textMiddle(Fonts.MEDIUM, hint, fieldX - w + 2, fieldY + 1, FIELD_H - 2, 6.5f, Theme.TEXT_DIM);
        }
    }

    /** Whether an item answers a search: by its name, its id, an enchantment on it, or (optionally) something inside it. */
    public static boolean matches(ItemStack stack, String query, boolean inside) {
        if (stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) return true;
        if (Game.itemId(stack).contains(query.replace(' ', '_'))) return true;
        if (enchanted(stack.get(DataComponents.ENCHANTMENTS), query) || enchanted(stack.get(DataComponents.STORED_ENCHANTMENTS), query)) return true;
        if (inside) for (ItemStack each : contents(stack)) if (!each.isEmpty() && matches(each, query, false)) return true;
        return false;
    }

    private static boolean enchanted(net.minecraft.world.item.enchantment.ItemEnchantments enchantments, String query) {
        if (enchantments == null) return false;
        for (var e : enchantments.entrySet()) {
            if (Enchantment.getFullname(e.getKey(), e.getIntValue()).getString().toLowerCase(Locale.ROOT).contains(query)) return true;
        }
        return false;
    }

    /** The items packed inside a stack (a shulker box), slot by slot; empty if it holds none. */
    public static NonNullList<ItemStack> contents(ItemStack stack) {
        var packed = stack.get(DataComponents.CONTAINER);
        if (packed == null) return NonNullList.create();
        NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
        packed.copyInto(items);
        return items;
    }

    // ---- the preview ---------------------------------------------------------------------------

    private static net.minecraft.client.renderer.state.MapRenderState mapState;

    /** Over everything a screen has drawn, its tooltip included: what the hovered item holds. */
    public static void preview(Canvas c, Screen current) {
        var mod = Modules.CONTAINER_PREVIEW;
        if (!mod.enabled() || !live() || current != screen || hovered == null || !hovered.hasItem()) return;
        if (!((AbstractContainerScreen<?>) screen).getMenu().getCarried().isEmpty()) return;
        ItemStack stack = hovered.getItem();
        float mx = Mc.mouseX(), my = Mc.mouseY();
        if (devHover >= 0) {
            mx = left + hovered.x + 8;
            my = top + hovered.y + 8;
        }

        if (mod.shulkers.get()) {
            NonNullList<ItemStack> items = contents(stack);
            boolean any = false;
            for (ItemStack each : items) any |= !each.isEmpty();
            if (any) {
                float w = 9 * 18 + 8, h = 3 * 18 + 8;
                float[] at = place(c, mx, my, w, h);
                c.layer();
                Theme.panel(c, at[0], at[1], w, h, Theme.R_MD);
                // Nothing is blurred behind it here, so the glass is made solid.
                c.rect(at[0], at[1], w, h, Theme.R_MD, 0xF2100E18);
                for (int i = 0; i < 27; i++) {
                    float x = at[0] + 4 + i % 9 * 18, y = at[1] + 4 + i / 9 * 18;
                    c.rect(x + 0.5f, y + 0.5f, 17, 17, 3, 0x12FFFFFF);
                    if (!items.get(i).isEmpty()) c.slotItem(items.get(i), x + 1, y + 1);
                }
                return;
            }
        }
        if (mod.maps.get() && Game.level() != null) {
            var id = stack.get(DataComponents.MAP_ID);
            var data = id == null ? null : net.minecraft.world.item.MapItem.getSavedData(id, Game.level());
            if (data == null) return;
            float size = 72, w = size + 8;
            float[] at = place(c, mx, my, w, w);
            if (mapState == null) mapState = new net.minecraft.client.renderer.state.MapRenderState();
            Mc.mc().getMapRenderer().extractRenderState(id, data, mapState);
            c.layer();
            Theme.panel(c, at[0], at[1], w, w, Theme.R_MD);
            c.rect(at[0], at[1], w, w, Theme.R_MD, 0xF2100E18);
            c.push();
            c.translate(at[0] + 4, at[1] + 4);
            c.raw().pose().scale(size / 128f, size / 128f);
            //? if <26.1 {
            /*c.raw().submitMapRenderState(mapState);
            *///?} else {
            c.raw().map(mapState);
            //?}
            c.pop();
        }
    }

    /**
     * Where a panel goes so it is clear of the tooltip, which starts beside the pointer and hangs
     * down from just above it: over the pointer if there is room, otherwise to its left.
     */
    private static float[] place(Canvas c, float mx, float my, float w, float h) {
        float x = Math.clamp(mx + 8, 4, Math.max(4, c.width() - w - 4));
        float y = my - 20 - h;
        if (y < 4) {
            x = mx - 10 - w;
            y = Math.clamp(my - h / 2, 4, Math.max(4, c.height() - h - 4));
            if (x < 4) x = 4;
        }
        return new float[] {x, y};
    }

    /** For the dev harness, which has no pointer: "hover" a slot by index (-1 for none), "lock" one, or "search:text". */
    public static void dev(String action, int slot) {
        if (action.equals("hover")) devHover = slot;
        else if (action.equals("lock") && !locks().remove(slot)) locks.add(slot);
        else if (action.startsWith("search:")) field.setText(action.substring(7));
    }

    private static int devHover = -1;

    // ---- input ---------------------------------------------------------------------------------

    /** A key on its way to the game. @return true if the search or the lock took it */
    public static boolean key(int key, int mods, boolean repeat) {
        if (!live()) return false;
        if (searchable()) {
            if (field.focused) {
                if (key == GLFW.GLFW_KEY_ESCAPE) {
                    field.focused = false;
                    field.setText("");
                } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                    field.focused = false;
                } else {
                    field.keyDown(key, mods);
                }
                // Everything typed belongs to the field: E must not close the inventory under it.
                return true;
            }
            int shortcut = Modules.INVENTORY_SEARCH.shortcut.get();
            int held = mods & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_ALT);
            if (!repeat && shortcut != -1 && key == dev.aller.setting.Settings.Key.code(shortcut) && held == dev.aller.setting.Settings.Key.mods(shortcut)) {
                field.focused = true;
                field.selectAll();
                return true;
            }
        }
        var lock = Modules.ITEM_LOCK;
        if (!repeat && lock.enabled() && key == lock.lockKey.get() && hovered != null && hovered.container instanceof Inventory) {
            int index = hovered.getContainerSlot();
            if (!locks().remove(index)) locks.add(index);
            saveLocks();
            dev.aller.platform.Sounds.toggle(locks.contains(index));
            return true;
        }
        return false;
    }

    /** A typed character. @return true if it went into the search */
    public static boolean typed(int codepoint) {
        if (!live() || !field.focused || !searchable()) return false;
        field.charTyped(codepoint);
        return true;
    }

    /** A left click. @return true if it landed on the search */
    public static boolean click() {
        if (!live() || !searchable()) return false;
        float mx = Mc.mouseX(), my = Mc.mouseY();
        boolean over = mx >= fieldX && mx < fieldX + fieldW && my >= fieldY && my < fieldY + FIELD_H;
        if (!over) {
            field.focused = false;
            return false;
        }
        if (!field.focused) {
            field.focused = true;
            field.selectAll();
        } else {
            field.mouseDown(mx, my, 0);
        }
        return true;
    }

    // ---- locks ---------------------------------------------------------------------------------

    private static Set<Integer> locks() {
        if (locks == null) {
            locks = new HashSet<>();
            try {
                JsonElement saved = AllerClient.config().extra("item_locks");
                if (saved != null && saved.isJsonArray()) for (JsonElement e : saved.getAsJsonArray()) locks.add(e.getAsInt());
            } catch (RuntimeException e) {
                AllerClient.LOG.warn("Ignoring unreadable item locks", e);
            }
        }
        return locks;
    }

    private static void saveLocks() {
        JsonArray array = new JsonArray();
        for (int index : locks) array.add(index);
        AllerClient.config().setExtra("item_locks", array);
    }

    private static boolean locked(Slot slot) {
        return slot.container instanceof Inventory && locks().contains(slot.getContainerSlot());
    }

    /** @return true if the drop key is about to throw what is in a locked hotbar slot */
    public static boolean blockDrop() {
        var p = Game.player();
        if (!Modules.ITEM_LOCK.enabled() || p == null || !locks().contains(p.getInventory().getSelectedSlot())) return false;
        refused();
        return true;
    }

    /** @return true if a click in a container would throw the contents of a locked slot */
    public static boolean blockThrow(Slot slot) {
        if (!Modules.ITEM_LOCK.enabled() || slot == null || !locked(slot)) return false;
        refused();
        return true;
    }

    private static void refused() {
        long now = System.currentTimeMillis();
        if (now - lockToastAt < 3000) return;
        lockToastAt = now;
        Toasts.show("Slot locked", "Unlock it in your inventory with " + Mc.keyName(Modules.ITEM_LOCK.lockKey.get()), false);
    }
}
