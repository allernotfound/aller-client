package dev.aller.hud.elements;

import dev.aller.hud.HudModule;
import dev.aller.platform.Canvas;
import dev.aller.platform.Game;
import dev.aller.platform.Mc;
import dev.aller.setting.Settings;
import dev.aller.ui.Colors;
import dev.aller.ui.Theme;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.font.Fonts;
import net.minecraft.util.StringUtil;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** HUD elements about the player's own condition and what they are looking at. */
public final class StatusHuds {
    private StatusHuds() {}

    public static final class Armor extends HudModule {
        public enum Show { DURABILITY, PERCENT, NONE }

        public final Settings.Choice<Show> show = choice("show", "Durability", Show.DURABILITY);
        public final Settings.Bool held = bool("held", "Include held item", true);
        public final Settings.Bool offhand = bool("offhand", "Include off-hand item", false);
        public final Settings.Bool horizontal = bool("horizontal", "Horizontal", false);

        private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        private final List<ItemStack> stacks = new ArrayList<>();
        private final List<String> labels = new ArrayList<>();
        private float labelW;

        public Armor() {
            super("armor", "Armour status", "Your armour pieces and how worn they are", AnchorH.RIGHT, AnchorV.BOTTOM, 6, 6);
            keywords("durability", "equipment", "armor");
            onByDefault();
        }

        @Override
        protected boolean measure(boolean editing) {
            stacks.clear();
            labels.clear();
            var p = Game.player();
            for (EquipmentSlot slot : SLOTS) add(p.getItemBySlot(slot));
            if (held.get()) add(p.getMainHandItem());
            if (offhand.get()) add(p.getOffhandItem());
            if (stacks.isEmpty() && editing) {
                add(new ItemStack(Items.DIAMOND_HELMET));
                add(new ItemStack(Items.DIAMOND_CHESTPLATE));
            }
            if (stacks.isEmpty()) return false;
            labelW = 0;
            for (String l : labels) labelW = Math.max(labelW, Fonts.MEDIUM.width(l, 7.5f));
            float cell = 16 + (labelW > 0 ? labelW + 4 : 0);
            if (horizontal.get()) {
                w = 6 + stacks.size() * (cell + 4);
                h = 22;
            } else {
                w = 8 + cell;
                h = 4 + stacks.size() * 18;
            }
            return true;
        }

        private void add(ItemStack stack) {
            if (stack.isEmpty()) return;
            stacks.add(stack);
            String label = "";
            if (stack.isDamageableItem() && show.get() != Show.NONE) {
                int left = stack.getMaxDamage() - stack.getDamageValue();
                label = show.get() == Show.PERCENT ? (left * 100 / stack.getMaxDamage()) + "%" : Integer.toString(left);
            } else if (!stack.isDamageableItem() && stack.getCount() > 1) {
                label = "x" + stack.getCount();
            }
            labels.add(label);
        }

        @Override
        protected void render(Canvas c, boolean editing) {
            chip(c, Theme.R_MD);
            float x = 4, y = 3;
            for (int i = 0; i < stacks.size(); i++) {
                ItemStack stack = stacks.get(i);
                c.item(stack, x, y);
                String label = labels.get(i);
                if (!label.isEmpty()) {
                    int color = Theme.TEXT;
                    if (stack.isDamageableItem()) {
                        float frac = 1f - stack.getDamageValue() / (float) stack.getMaxDamage();
                        color = frac < 0.15f ? Theme.DANGER : frac < 0.4f ? Theme.WARN : Theme.TEXT;
                    }
                    c.textMiddle(Fonts.MEDIUM, label, x + 19, y, 16, 7.5f, color);
                }
                if (horizontal.get()) x += 16 + (labelW > 0 ? labelW + 4 : 0) + 4;
                else y += 18;
            }
        }
    }

    public static final class Potions extends HudModule {
        public final Settings.Bool blink = bool("blink", "Blink when about to expire", true);
        private final List<MobEffectInstance> effects = new ArrayList<>();

        public Potions() {
            super("potions", "Potion effects", "Active status effects with time remaining", AnchorH.RIGHT, AnchorV.MIDDLE, 6, 0);
            keywords("effects", "status", "buffs");
            onByDefault();
        }

        @Override
        protected boolean measure(boolean editing) {
            effects.clear();
            effects.addAll(Game.player().getActiveEffects());
            effects.sort(Comparator.comparingInt(e -> e.isInfiniteDuration() ? Integer.MAX_VALUE : e.getDuration()));
            int rows = effects.isEmpty() && editing ? 2 : effects.size();
            if (rows == 0) return false;
            float widest = 60;
            for (MobEffectInstance e : effects) widest = Math.max(widest, Fonts.MEDIUM.width(name(e), 8f));
            w = widest + 22;
            h = 6 + rows * 20;
            return true;
        }

        private static String name(MobEffectInstance e) {
            String n = e.getEffect().value().getDisplayName().getString();
            return e.getAmplifier() > 0 ? n + " " + roman(e.getAmplifier() + 1) : n;
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

        @Override
        protected void render(Canvas c, boolean editing) {
            chip(c, Theme.R_MD);
            float y = 3;
            if (effects.isEmpty()) {
                row(c, y, "Speed II", "1:30", 0xFF7CAFC6, 1f);
                row(c, y + 20, "Regeneration", "0:12", 0xFFCD5CAB, 1f);
                return;
            }
            for (MobEffectInstance e : effects) {
                String time = e.isInfiniteDuration() ? "•••" : StringUtil.formatTickDuration(e.getDuration(), 20f);
                float a = 1f;
                if (blink.get() && !e.isInfiniteDuration() && e.getDuration() < 200) {
                    a = 0.55f + 0.45f * (float) Math.sin(e.getDuration() * 0.5f);
                }
                row(c, y, name(e), time, e.getEffect().value().getColor() | 0xFF000000, a);
                y += 20;
            }
        }

        private void row(Canvas c, float y, String name, String time, int color, float alpha) {
            c.pushAlpha(alpha);
            c.rect(6, y + 3, 3, 14, 1.5f, color);
            c.text(Fonts.MEDIUM, name, 14, y + 1.5f, 8f, Theme.TEXT);
            c.text(Fonts.REGULAR, time, 14, y + 11f, 7f, Theme.TEXT_DIM);
            c.popAlpha();
        }
    }

    public static final class Target extends HudModule {
        public final Settings.Bool playersOnly = bool("players_only", "Players only", false);
        public final Settings.Num lingerTime = num("linger", "Stay after looking away", 1.5f, 0f, 5f, 0.5f).suffix("s");
        private final Spring health = new Spring(1, 180f, 26f);
        private final Spring damageTrail = new Spring(1, 40f, 12f);
        private LivingEntity target;
        private int linger;
        private String name = "";

        public Target() {
            super("target", "Target info", "Name and health of the entity you are looking at", AnchorH.CENTER, AnchorV.MIDDLE, 0, -46);
            keywords("health", "enemy", "mob", "entity", "pvp");
        }

        @Override
        public void tick() {
            if (Mc.mc().crosshairPickEntity instanceof LivingEntity living && living.isAlive()
                    && (!playersOnly.get() || living instanceof net.minecraft.world.entity.player.Player)) {
                if (living != target) {
                    target = living;
                    float f = fraction();
                    health.snap(f);
                    damageTrail.snap(f);
                }
                linger = Math.round(lingerTime.get() * 20);
                if (linger == 0) linger = 1;
            } else if (linger > 0 && --linger == 0) {
                target = null;
            }
        }

        private float fraction() {
            return target == null ? 1 : Math.clamp(target.getHealth() / Math.max(1f, target.getMaxHealth()), 0f, 1f);
        }

        @Override
        protected boolean measure(boolean editing) {
            if (target == null && !editing) return false;
            name = target == null ? "Zombie" : target.getDisplayName().getString();
            w = Math.max(96, Fonts.SEMIBOLD.width(name, 8.5f) + 56);
            h = 30;
            return true;
        }

        @Override
        protected void render(Canvas c, boolean editing) {
            chip(c, Theme.R_MD);
            float f = target == null ? 0.65f : fraction();
            float shown = Math.clamp(health.target(f).update(), 0f, 1f);
            float trail = Math.clamp(damageTrail.target(f).update(), 0f, 1f);
            String hp = target == null ? "13.0" : String.format("%.1f", target.getHealth());
            c.text(Fonts.SEMIBOLD, Fonts.SEMIBOLD.truncate(name, 8.5f, w - 44), 7, 5, 8.5f, Theme.TEXT);
            c.textRight(Fonts.MEDIUM, hp + " HP", w - 7, 5.5f, 7.5f, Theme.TEXT_DIM);

            float bx = 7, by = 20, bw = w - 14, bh = 4;
            c.rect(bx, by, bw, bh, 2, 0x33FFFFFF);
            // A pale bar lags behind the real one so recent damage stays visible for a moment.
            if (trail > shown) c.rect(bx, by, bw * trail, bh, 2, 0x99FFFFFF);
            int color = Colors.mix(Theme.DANGER, Theme.SUCCESS, shown);
            if (shown > 0.01f) c.rect(bx, by, Math.max(bh, bw * shown), bh, 2, color);
        }
    }
}
