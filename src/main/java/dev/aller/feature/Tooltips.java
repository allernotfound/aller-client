package dev.aller.feature;

import dev.aller.module.Modules;
import dev.aller.platform.Game;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.List;
import java.util.Map;

/** Lines added to Minecraft's item tooltips: the numbers it leaves out, and what each enchantment does. */
public final class Tooltips {
    /** What the game's own enchantments do, by id. One from a data pack or a server simply gets no note. */
    private static final Map<String, String> NOTES = Map.ofEntries(
            Map.entry("protection", "Less damage from most sources"),
            Map.entry("fire_protection", "Less fire damage, and you burn for less long"),
            Map.entry("feather_falling", "Less fall damage"),
            Map.entry("blast_protection", "Less damage and knockback from explosions"),
            Map.entry("projectile_protection", "Less damage from arrows and other projectiles"),
            Map.entry("respiration", "Longer breath under water"),
            Map.entry("aqua_affinity", "Mine under water at normal speed"),
            Map.entry("thorns", "Hurts whoever hits you, at a cost in durability"),
            Map.entry("depth_strider", "Walk faster under water"),
            Map.entry("frost_walker", "Water freezes under your feet"),
            Map.entry("binding_curse", "Cannot be taken off"),
            Map.entry("soul_speed", "Faster on soul sand and soul soil"),
            Map.entry("swift_sneak", "Sneak faster"),
            Map.entry("sharpness", "More melee damage"),
            Map.entry("smite", "More damage to the undead"),
            Map.entry("bane_of_arthropods", "More damage to spiders, bees and silverfish"),
            Map.entry("knockback", "Hits push further"),
            Map.entry("fire_aspect", "Sets what you hit on fire"),
            Map.entry("looting", "Mobs drop more"),
            Map.entry("sweeping_edge", "Sweep attacks hit harder"),
            Map.entry("efficiency", "Mines faster"),
            Map.entry("silk_touch", "Blocks drop as themselves"),
            Map.entry("unbreaking", "Wears out more slowly"),
            Map.entry("fortune", "Ores and crops drop more"),
            Map.entry("power", "Arrows hit harder"),
            Map.entry("punch", "Arrows push further"),
            Map.entry("flame", "Arrows set what they hit on fire"),
            Map.entry("infinity", "Shooting uses no arrows, with one in the inventory"),
            Map.entry("luck_of_the_sea", "Better catches when fishing"),
            Map.entry("lure", "Fish bite sooner"),
            Map.entry("loyalty", "The trident comes back when thrown"),
            Map.entry("impaling", "More damage to anything in water or rain"),
            Map.entry("riptide", "Throws you with the trident, in water or rain"),
            Map.entry("channeling", "Calls lightning on what it hits in a thunderstorm"),
            Map.entry("multishot", "Fires three arrows for the price of one"),
            Map.entry("quick_charge", "Reloads faster"),
            Map.entry("piercing", "Arrows go through several mobs"),
            Map.entry("density", "More damage the further the mace falls"),
            Map.entry("breach", "Armour protects less against it"),
            Map.entry("wind_burst", "A hit from a fall throws you back up"),
            Map.entry("lunge", "Lunges you forward on a jab, at a cost in hunger"),
            Map.entry("mending", "Experience repairs it"),
            Map.entry("vanishing_curse", "Lost when you die"));

    private Tooltips() {}

    /** Called for every tooltip as it is built; never throws. */
    public static void append(ItemStack stack, boolean advanced, List<Component> lines) {
        try {
            if (Modules.ENCHANT_NOTES.enabled()) {
                notes(stack.get(DataComponents.ENCHANTMENTS), lines);
                notes(stack.get(DataComponents.STORED_ENCHANTMENTS), lines);
            }
            if (Modules.ITEM_DETAILS.enabled()) details(stack, advanced, lines);
        } catch (RuntimeException e) {
            // a tooltip without the extras is better than none
        }
    }

    private static void notes(ItemEnchantments enchantments, List<Component> lines) {
        if (enchantments == null || enchantments.isEmpty()) return;
        for (var e : enchantments.entrySet()) {
            Holder<Enchantment> holder = e.getKey();
            String note = holder.unwrapKey().map(key -> NOTES.get(Game.path(key))).orElse(null);
            if (note == null) continue;
            int max = holder.value().getMaxLevel();
            if (Modules.ENCHANT_NOTES.max.get() && max > 1) note += " (up to " + roman(max) + ")";
            Component line = Component.literal(" " + note).withStyle(ChatFormatting.DARK_GRAY);
            // Under the enchantment's own line, or at the end if a mod has reworded it.
            String name = Enchantment.getFullname(holder, e.getIntValue()).getString();
            int at = -1;
            for (int i = 0; i < lines.size() && at < 0; i++) if (lines.get(i).getString().equals(name)) at = i;
            if (at >= 0) lines.add(at + 1, line);
            else lines.add(line);
        }
    }

    private static String roman(int n) {
        return switch (n) {
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(n);
        };
    }

    private static void details(ItemStack stack, boolean advanced, List<Component> lines) {
        var mod = Modules.ITEM_DETAILS;
        // With advanced tooltips (F3+H) the game writes the durability itself.
        if (mod.durability.get() && stack.isDamageableItem() && !advanced) {
            add(lines, "Durability: " + (stack.getMaxDamage() - stack.getDamageValue()) + " / " + stack.getMaxDamage());
        }
        var eat = stack.get(DataComponents.FOOD);
        if (mod.food.get() && eat != null) {
            add(lines, "Restores " + trim(eat.nutrition() / 2f) + " hunger, " + trim(eat.saturation() / 2f) + " saturation");
        }
        if (mod.fuel.get() && Game.level() != null) {
            int ticks = Game.level().fuelValues().burnDuration(stack);
            if (ticks > 0) add(lines, "Fuel: smelts " + trim(ticks / 200f) + (ticks == 200 ? " item" : " items"));
        }
        Integer cost = stack.get(DataComponents.REPAIR_COST);
        if (mod.repair.get() && cost != null && cost > 0) add(lines, "Anvil cost so far: " + cost + (cost == 1 ? " level" : " levels"));
    }

    private static void add(List<Component> lines, String text) {
        lines.add(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }

    /** "4", "2.5", "0.25". */
    private static String trim(float v) {
        String s = String.format("%.2f", v);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
