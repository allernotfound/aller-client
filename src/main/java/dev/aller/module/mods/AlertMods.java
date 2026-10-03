package dev.aller.module.mods;

import dev.aller.module.Category;
import dev.aller.module.Module;
import dev.aller.platform.Canvas;
import dev.aller.platform.Game;
import dev.aller.platform.Sounds;
import dev.aller.setting.Settings;
import dev.aller.ui.Colors;
import dev.aller.ui.Theme;
import dev.aller.ui.Toasts;
import dev.aller.ui.anim.Motion;
import dev.aller.ui.anim.Spring;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/** Warnings that have no place of their own on the HUD: a toast, a sound, a tint at the screen's edge. */
public final class AlertMods {
    private AlertMods() {}

    public static final class Durability extends Module {
        public final Settings.Num threshold = num("threshold", "Warn at", 10f, 2f, 30f, 1f).suffix("% left");
        public final Settings.Bool armour = bool("armour", "Armour", true);
        public final Settings.Bool held = bool("held", "Tools and weapons in hand", true);
        public final Settings.Choice<Sounds.Alert> sound = choice("sound", "Sound", Sounds.Alert.BELL);
        public final Settings.Num volume = num("volume", "Volume", 60f, 0f, 100f, 5f).suffix("%");

        /** The item last warned about in each slot, so one piece is announced once. */
        private final Map<EquipmentSlot, Item> warned = new EnumMap<>(EquipmentSlot.class);

        public Durability() {
            super("durability_warnings", "Durability warnings", "A toast and a sound when a tool or a piece of armour is about to break", Category.HUD);
            keywords("break", "tool", "armour", "armor", "wear", "repair", "alert");
        }

        @Override
        public void tick() {
            var p = Game.player();
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                boolean hand = slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND;
                ItemStack stack = p.getItemBySlot(slot);
                if (!stack.isDamageableItem() || !(hand ? held.get() : armour.get())) {
                    warned.remove(slot);
                    continue;
                }
                int left = stack.getMaxDamage() - stack.getDamageValue();
                float percent = left * 100f / stack.getMaxDamage();
                if (percent > threshold.get() + 3) {
                    warned.remove(slot);
                } else if (percent <= threshold.get() && warned.put(slot, stack.getItem()) != stack.getItem()) {
                    Toasts.warn(stack.getHoverName().getString() + " is about to break", left + (left == 1 ? " use left" : " uses left"));
                    Sounds.alert(sound.get(), 0.8f, volume.get() / 100f);
                }
            }
        }
    }

    public static final class Vitals extends Module {
        public final Settings.Num health = num("health", "Low health at", 6f, 1f, 12f, 1f)
                .format(v -> v / 2 == Math.round(v / 2) ? Math.round(v / 2) + " hearts" : String.format("%.1f hearts", v / 2));
        public final Settings.Bool hunger = bool("hunger", "Also warn when hungry", true);
        public final Settings.Num food = num("food", "Hungry at", 6f, 1f, 12f, 1f).suffix(" points").visibleWhen(() -> this.hunger.get());
        public final Settings.Num strength = num("strength", "Strength", 60f, 20f, 100f, 5f).suffix("%");
        public final Settings.Bool pulse = bool("pulse", "Pulse", true).describe("Off, or with reduced motion, it is a steady tint");

        private final Spring red = Spring.smooth(0), amber = Spring.smooth(0);

        public Vitals() {
            super("vitals_warning", "Vitals warning", "The edges of the screen tint when your health or hunger runs low", Category.HUD);
            keywords("health", "hunger", "low", "vignette", "danger", "heart", "food");
        }

        /** Drawn under the HUD, over the world. */
        public void draw(Canvas c) {
            var p = Game.player();
            boolean alive = enabled() && p != null && p.isAlive() && !p.isCreative() && !p.isSpectator();
            float hurt = red.target(alive && p.getHealth() <= health.get() ? 1 : 0).update();
            float hungry = amber.target(alive && hunger.get() && p.getFoodData().getFoodLevel() <= food.get() ? 1 : 0).update();
            if (hurt < 0.02f && hungry < 0.02f) return;
            float beat = pulse.get() && !Motion.reduced() ? 0.72f + 0.28f * (float) Math.sin(Motion.time() * 5.2f) : 0.85f;
            float k = strength.get() / 100f;
            // Hunger is the quieter of the two, and gives way to a health warning.
            if (hungry > 0.02f) vignette(c, Theme.WARN, 0.3f * k * hungry * (1 - hurt));
            if (hurt > 0.02f) vignette(c, Theme.DANGER, 0.55f * k * hurt * beat);
        }

        private static void vignette(Canvas c, int color, float alpha) {
            if (alpha < 0.01f) return;
            float w = c.width(), h = c.height();
            float depth = Math.min(w, h) * 0.32f;
            int edge = Colors.withAlpha(color, alpha), clear = Colors.withAlpha(color, 0f);
            c.gradientV(0, 0, w, depth, 0, edge, clear);
            c.gradientV(0, h - depth, w, depth, 0, clear, edge);
            c.gradientH(0, 0, depth, h, 0, edge, clear);
            c.gradientH(w - depth, 0, depth, h, 0, clear, edge);
        }
    }
}
