package dev.aller.module.mods;

import dev.aller.module.Category;
import dev.aller.module.Module;
import dev.aller.setting.Settings;
import org.lwjgl.glfw.GLFW;

/** Mods for items and the screens that hold them. {@code feature/Tooltips}, {@code Containers} and {@code ChestMemory} do the work. */
public final class ItemMods {
    private ItemMods() {}

    public static final class ContainerPreview extends Module {
        public final Settings.Bool shulkers = bool("shulkers", "Shulker boxes and other filled containers", true);
        public final Settings.Bool maps = bool("maps", "Maps", true);

        public ContainerPreview() {
            super("container_preview", "Container preview", "Hover a shulker box or a map to see what is in it", Category.UTILITY);
            keywords("shulker", "box", "map", "tooltip", "peek", "contents");
            onByDefault();
        }
    }

    public static final class ItemDetails extends Module {
        public final Settings.Bool durability = bool("durability", "Durability in numbers", true);
        public final Settings.Bool food = bool("food", "Hunger and saturation restored", true);
        public final Settings.Bool fuel = bool("fuel", "How much a fuel smelts", true);
        public final Settings.Bool repair = bool("repair", "Anvil cost so far", true)
                .describe("Each anvil use makes the next one dearer; past 39 levels it is too expensive");

        public ItemDetails() {
            super("item_details", "Item details", "Adds the numbers Minecraft leaves out of an item's tooltip", Category.UTILITY);
            keywords("tooltip", "durability", "food", "saturation", "fuel", "anvil", "repair cost");
        }
    }

    public static final class EnchantNotes extends Module {
        public final Settings.Bool max = bool("max", "Show the highest level", true);

        public EnchantNotes() {
            super("enchant_notes", "Enchantment notes", "A line under each enchantment saying what it does", Category.UTILITY);
            keywords("enchant", "tooltip", "description", "book", "max level");
        }
    }

    public static final class InventorySearch extends Module {
        public final Settings.Key shortcut = key("shortcut", "Search shortcut", Settings.Key.pack(GLFW.GLFW_KEY_F, GLFW.GLFW_MOD_CONTROL)).chord();
        public final Settings.Bool inside = bool("inside", "Look inside shulker boxes", true);
        public final Settings.Bool keep = bool("keep", "Keep the search between containers", false);

        public InventorySearch() {
            super("inventory_search", "Inventory search", "Type in any chest or inventory and everything else dims", Category.UTILITY);
            keywords("find", "filter", "chest", "highlight", "items");
            onByDefault();
        }
    }

    public static final class ItemLock extends Module {
        public final Settings.Key lockKey = key("lock_key", "Lock the slot under the pointer", GLFW.GLFW_KEY_L)
                .describe("Press it over a slot of your own inventory, with the inventory open");

        public ItemLock() {
            super("item_lock", "Item lock", "Lock slots of your inventory so the drop key cannot throw what is in them", Category.UTILITY);
            keywords("drop", "protect", "slot", "tool", "safe", "throw");
        }
    }

    public static final class ChestMemory extends Module {
        public final Settings.Num markTime = num("mark_time", "Point at chests for", 60f, 10f, 300f, 10f).suffix("s");
        public final Settings.Bool distance = bool("distance", "Show how far each chest is", true);

        public ChestMemory() {
            super("chest_memory", "Chest memory", "Remembers what was in the containers you opened, and finds an item again from the launcher", Category.UTILITY);
            keywords("find", "where", "storage", "tracker", "container", "locate", "items");
            restricted("Remembering chest contents only uses what you have seen yourself, but some servers do not allow storage trackers.");
        }
    }
}
