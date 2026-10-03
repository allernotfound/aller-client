package dev.aller.hud.elements;

import dev.aller.feature.Session;
import dev.aller.hud.HudModule;
import dev.aller.platform.Canvas;
import dev.aller.platform.Game;
import dev.aller.platform.Mc;
import dev.aller.platform.Picture;
import dev.aller.setting.Settings;
import dev.aller.ui.Colors;
import dev.aller.ui.Theme;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.font.Fonts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** HUD elements about what is in the player's hands, pockets and crosshair. */
public final class GearHuds {
    private GearHuds() {}

    /** A small caption with its value under it; returns the width used. */
    private static float cell(Canvas c, float x, float y, String label, String value, int color) {
        c.text(Fonts.MEDIUM, label, x, y, 6f, Theme.accent());
        c.text(Fonts.SEMIBOLD, value, x, y + 8, 8.5f, color);
        return cellWidth(label, value);
    }

    private static float cellWidth(String label, String value) {
        return Math.max(Fonts.MEDIUM.width(label, 6f), Fonts.SEMIBOLD.width(value, 8.5f));
    }

    /** A thin bar filled from the left. */
    private static void bar(Canvas c, float x, float y, float w, float fraction, int color) {
        c.rect(x, y, w, 3, 1.5f, 0x33FFFFFF);
        float f = Math.clamp(fraction, 0f, 1f);
        if (f > 0.01f) c.rect(x, y, Math.max(3, w * f), 3, 1.5f, color);
    }

    private static int wear(float fraction) {
        return fraction < 0.15f ? Theme.DANGER : fraction < 0.4f ? Theme.WARN : Theme.SUCCESS;
    }

    public static final class LookingAt extends HudModule {
        public final Settings.Bool entities = bool("entities", "Also name mobs and players", true);
        public final Settings.Bool icon = bool("icon", "Show the block's picture", true);
        public final Settings.Bool tool = bool("tool", "Say which tool mines it", true);

        private final Spring breaking = new Spring(0, 260f, 30f);
        private String title = "", detail = "";
        private ItemStack stack = ItemStack.EMPTY;
        private float progress;

        public LookingAt() {
            super("looking_at", "Looking at", "The block or mob under your crosshair: its name, the tool for it, how far it has grown or broken",
                    AnchorH.CENTER, AnchorV.TOP, 0, 36);
            keywords("waila", "block", "what", "crosshair", "crop", "growth", "redstone", "power", "target");
        }

        private void read() {
            title = "";
            detail = "";
            stack = ItemStack.EMPTY;
            progress = 0;
            HitResult hit = Mc.mc().hitResult;
            if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = block.getBlockPos();
                BlockState state = Game.level().getBlockState(pos);
                if (state.isAir()) return;
                title = state.getBlock().getName().getString();
                stack = new ItemStack(state.getBlock().asItem());
                List<String> parts = new ArrayList<>();
                if (tool.get()) {
                    String with = state.is(BlockTags.MINEABLE_WITH_PICKAXE) ? "Pickaxe" : state.is(BlockTags.MINEABLE_WITH_AXE) ? "Axe"
                            : state.is(BlockTags.MINEABLE_WITH_SHOVEL) ? "Shovel" : state.is(BlockTags.MINEABLE_WITH_HOE) ? "Hoe" : null;
                    if (with != null) parts.add(with);
                }
                for (Property<?> property : state.getProperties()) {
                    if (!(property instanceof IntegerProperty number)) continue;
                    int value = state.getValue(number), max = Collections.max(number.getPossibleValues());
                    if (property.getName().equals("age") && grows(state)) parts.add(value >= max ? "Fully grown" : "Grown " + value * 100 / max + "%");
                    else if (property.getName().equals("power")) parts.add("Power " + value);
                    else if (property.getName().equals("level") && max == 8) parts.add("Level " + value);
                    else if (property.getName().equals("honey_level")) parts.add("Honey " + value + "/" + max);
                }
                detail = String.join("  ·  ", parts);
                var mode = Mc.mc().gameMode;
                if (mode != null && mode.isDestroying() && mode.getDestroyStage() >= 0) progress = (mode.getDestroyStage() + 1) / 10f;
            } else if (entities.get() && hit instanceof EntityHitResult entity) {
                var e = entity.getEntity();
                title = e.getDisplayName().getString();
                String kind = e.getType().getDescription().getString();
                List<String> parts = new ArrayList<>();
                if (!kind.equals(title)) parts.add(kind);
                if (e instanceof LivingEntity living) parts.add(String.format("%.0f / %.0f HP", Math.ceil(living.getHealth()), living.getMaxHealth()));
                detail = String.join("  ·  ", parts);
            }
        }

        /** Whether a block's age is growth the player waits for, rather than the ticking of cactus, kelp or fire. */
        private static boolean grows(BlockState state) {
            var block = state.getBlock();
            return block instanceof net.minecraft.world.level.block.CropBlock || block instanceof net.minecraft.world.level.block.StemBlock
                    || block instanceof net.minecraft.world.level.block.NetherWartBlock || block instanceof net.minecraft.world.level.block.CocoaBlock
                    || block instanceof net.minecraft.world.level.block.SweetBerryBushBlock;
        }

        @Override
        protected boolean measure(boolean editing) {
            read();
            if (title.isEmpty()) {
                if (!editing) return false;
                title = "Wheat Crops";
                detail = "Grown 57%";
                stack = new ItemStack(Items.WHEAT);
            }
            float left = icon.get() && !stack.isEmpty() ? 24 : 7;
            w = left + Math.max(Fonts.SEMIBOLD.widthAny(title, 8.5f), Fonts.MEDIUM.widthAny(detail, 7f)) + 8;
            h = detail.isEmpty() ? 22 : 27;
            return true;
        }

        @Override
        protected void render(Canvas c, boolean editing) {
            chip(c, Theme.R_MD);
            float left = 7;
            if (icon.get() && !stack.isEmpty()) {
                c.item(stack, 4, (h - 16) / 2);
                left = 24;
            }
            if (detail.isEmpty()) {
                c.textAny(Fonts.SEMIBOLD, title, left, (h - Fonts.SEMIBOLD.height(8.5f)) / 2, 8.5f, Theme.TEXT);
            } else {
                c.textAny(Fonts.SEMIBOLD, title, left, 4.5f, 8.5f, Theme.TEXT);
                c.textAny(Fonts.MEDIUM, detail, left, 15.5f, 7f, Theme.TEXT_DIM);
            }
            float p = breaking.target(progress).update();
            if (p > 0.02f) c.rect(3, h - 2.5f, (w - 6) * Math.clamp(p, 0f, 1f), 1.5f, 0.75f, Theme.accent());
        }
    }

    public static final class Elytra extends HudModule {
        public final Settings.Bool always = bool("always", "Show while not flying", false);
        private double lastX, lastY, lastZ;
        private float speed;

        public Elytra() {
            super("elytra", "Elytra flight", "Speed, height, pitch, wear and rockets left while you glide", AnchorH.CENTER, AnchorV.BOTTOM, 0, 130);
            keywords("glide", "fly", "rocket", "firework", "altitude", "wings");
        }

        @Override
        public void tick() {
            var p = Game.player();
            double dx = p.getX() - lastX, dy = p.getY() - lastY, dz = p.getZ() - lastZ;
            double perSecond = Math.sqrt(dx * dx + dy * dy + dz * dz) * 20;
            if (perSecond < 400) speed += ((float) perSecond - speed) * 0.35f;
            lastX = p.getX();
            lastY = p.getY();
            lastZ = p.getZ();
        }

        private static final String[] LABELS = {"SPEED", "HEIGHT", "PITCH", "ROCKETS"};
        private final String[] values = new String[4];
        private float wear = -1;

        @Override
        protected boolean measure(boolean editing) {
            var p = Game.player();
            if (!editing && !always.get() && !p.isFallFlying()) return false;
            values[0] = String.format("%.1f b/s", speed);
            values[1] = Integer.toString((int) Math.floor(p.getY()));
            values[2] = String.format("%.0f°", -p.getXRot());
            values[3] = Integer.toString(p.getInventory().countItem(Items.FIREWORK_ROCKET));
            ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
            wear = chest.is(Items.ELYTRA) && chest.isDamageableItem() ? 1f - chest.getDamageValue() / (float) chest.getMaxDamage() : -1;
            w = 10;
            for (int i = 0; i < 4; i++) w += Math.max(30, cellWidth(LABELS[i], values[i])) + 8;
            h = wear >= 0 ? 31 : 25;
            return true;
        }

        @Override
        protected void render(Canvas c, boolean editing) {
            chip(c, Theme.R_MD);
            float x = 8;
            for (int i = 0; i < 4; i++) {
                int color = i == 3 && values[3].equals("0") ? Theme.WARN : Theme.TEXT;
                x += Math.max(30, cell(c, x, 4, LABELS[i], values[i], color)) + 8;
            }
            if (wear >= 0) bar(c, 8, h - 6.5f, w - 16, wear, wear(wear));
        }
    }

    public static final class Cooldowns extends HudModule {
        private final List<ItemStack> stacks = new ArrayList<>();
        private final List<Float> left = new ArrayList<>();

        public Cooldowns() {
            super("cooldowns", "Cooldowns", "Items you cannot use again yet: ender pearls, shields, chorus fruit, wind charges", AnchorH.CENTER, AnchorV.BOTTOM, 0, 58);
            keywords("pearl", "shield", "timer", "wait", "chorus", "wind charge");
        }

        @Override
        protected boolean measure(boolean editing) {
            stacks.clear();
            left.clear();
            var p = Game.player();
            var cooldowns = p.getCooldowns();
            float partial = Game.partialTick();
            Set<Object> seen = new HashSet<>();
            var inventory = p.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.isEmpty()) continue;
                float f = cooldowns.getCooldownPercent(stack, partial);
                if (f <= 0 || !seen.add(cooldowns.getCooldownGroup(stack))) continue;
                stacks.add(stack);
                left.add(f);
            }
            if (stacks.isEmpty()) {
                if (!editing) return false;
                stacks.add(new ItemStack(Items.ENDER_PEARL));
                left.add(0.6f);
            }
            w = 4 + stacks.size() * 20;
            h = 26;
            return true;
        }

        @Override
        protected void render(Canvas c, boolean editing) {
            chip(c, Theme.R_MD);
            for (int i = 0; i < stacks.size(); i++) {
                float x = 4 + i * 20;
                c.item(stacks.get(i), x, 3);
                bar(c, x, 20.5f, 16, 1f - left.get(i), Theme.accent());
            }
        }
    }

    public static final class InventoryView extends HudModule {
        public final Settings.Bool hideEmpty = bool("hide_empty", "Hide while it is empty", true);
        public final Settings.Bool cells = bool("cells", "Mark the slots", true);
        private static final float CELL = 18f;

        public InventoryView() {
            super("inventory_view", "Inventory view", "The three rows of your inventory, always in sight", AnchorH.RIGHT, AnchorV.BOTTOM, 52, 6);
            keywords("items", "backpack", "slots", "preview");
        }

        @Override
        protected boolean measure(boolean editing) {
            if (!editing && hideEmpty.get()) {
                boolean any = false;
                var inventory = Game.player().getInventory();
                for (int i = 9; i < 36 && !any; i++) any = !inventory.getItem(i).isEmpty();
                if (!any) return false;
            }
            w = 9 * CELL + 6;
            h = 3 * CELL + 6;
            return true;
        }

        @Override
        protected void render(Canvas c, boolean editing) {
            chip(c, Theme.R_MD);
            var inventory = Game.player().getInventory();
            for (int i = 0; i < 27; i++) {
                float x = 3 + i % 9 * CELL, y = 3 + i / 9 * CELL;
                if (cells.get()) c.rect(x + 0.5f, y + 0.5f, CELL - 1, CELL - 1, 3, 0x12FFFFFF);
                ItemStack stack = inventory.getItem(9 + i);
                if (!stack.isEmpty()) c.slotItem(stack, x + 1, y + 1);
            }
        }
    }

    public static final class HeldItem extends HudModule {
        public final Settings.Bool enchantments = bool("enchantments", "List enchantments", true);
        public final Settings.Bool onlyTools = bool("only_tools", "Only for things that wear out", false);
        private ItemStack stack = ItemStack.EMPTY;
        private String name = "", enchants = "", uses = "";
        private float wear;

        public HeldItem() {
            super("held_item", "Held item", "What is in your hand: how worn it is and what is on it", AnchorH.CENTER, AnchorV.BOTTOM, 0, 90);
            keywords("durability", "tool", "sword", "enchant", "hand");
        }

        @Override
        protected boolean measure(boolean editing) {
            stack = Game.player().getMainHandItem();
            if (stack.isEmpty() || onlyTools.get() && !stack.isDamageableItem()) {
                if (!editing) return false;
                stack = new ItemStack(Items.DIAMOND_PICKAXE);
            }
            name = stack.getHoverName().getString();
            enchants = "";
            if (enchantments.get()) {
                List<String> names = new ArrayList<>();
                for (var e : stack.getEnchantments().entrySet()) names.add(Enchantment.getFullname(e.getKey(), e.getIntValue()).getString());
                if (names.size() > 3) {
                    int more = names.size() - 3;
                    names = new ArrayList<>(names.subList(0, 3));
                    names.add("+" + more);
                }
                enchants = String.join(", ", names);
            }
            wear = -1;
            uses = "";
            if (stack.isDamageableItem()) {
                int remaining = stack.getMaxDamage() - stack.getDamageValue();
                wear = remaining / (float) stack.getMaxDamage();
                uses = remaining + " / " + stack.getMaxDamage();
            }
            float text = Math.max(Fonts.SEMIBOLD.widthAny(name, 8.5f) + (uses.isEmpty() ? 0 : Fonts.MEDIUM.width(uses, 7f) + 10), Fonts.MEDIUM.widthAny(enchants, 6.8f));
            w = Math.max(90, 26 + text + 7);
            h = 14 + (enchants.isEmpty() ? 0 : 10) + (wear >= 0 ? 8 : 0) + 4;
            if (h < 24) h = 24;
            return true;
        }

        @Override
        protected void render(Canvas c, boolean editing) {
            chip(c, Theme.R_MD);
            c.item(stack, 5, 4);
            float y = enchants.isEmpty() && wear < 0 ? (h - Fonts.SEMIBOLD.height(8.5f)) / 2 : 4.5f;
            c.textAny(Fonts.SEMIBOLD, name, 26, y, 8.5f, Theme.TEXT);
            if (!uses.isEmpty()) c.textRight(Fonts.MEDIUM, uses, w - 7, y + 1, 7f, Colors.mix(wear(wear), Theme.TEXT, 0.4f));
            y += 11.5f;
            if (!enchants.isEmpty()) {
                c.textAny(Fonts.MEDIUM, enchants, 26, y, 6.8f, Theme.TEXT_DIM);
                y += 10;
            }
            if (wear >= 0) bar(c, 26, y + 1, w - 33, wear, wear(wear));
        }
    }

    public static final class Mount extends HudModule {
        private final List<String> labels = new ArrayList<>(), values = new ArrayList<>();
        private String name = "";
        private float health = 1;

        public Mount() {
            super("mount", "Mount stats", "What you are riding: its health, speed and how high it jumps", AnchorH.LEFT, AnchorV.BOTTOM, 6, 90);
            keywords("horse", "donkey", "mule", "camel", "ride", "saddle", "jump");
        }

        @Override
        protected boolean measure(boolean editing) {
            labels.clear();
            values.clear();
            if (Game.player().getVehicle() instanceof LivingEntity mount) {
                name = mount.getDisplayName().getString();
                health = Math.clamp(mount.getHealth() / Math.max(1f, mount.getMaxHealth()), 0f, 1f);
                add("HEALTH", String.format("%.0f / %.0f", Math.ceil(mount.getHealth()), mount.getMaxHealth()));
                // A horse covers 43.17 blocks a second for each point of its speed attribute.
                add("SPEED", String.format("%.1f b/s", mount.getAttributeValue(Attributes.MOVEMENT_SPEED) * 43.17));
                String kind = BuiltInRegistries.ENTITY_TYPE.getKey(mount.getType()).getPath();
                if (mount.getAttributes().hasAttribute(Attributes.JUMP_STRENGTH) && (kind.contains("horse") || kind.equals("donkey") || kind.equals("mule"))) {
                    double s = mount.getAttributeValue(Attributes.JUMP_STRENGTH);
                    double blocks = -0.1817584952 * s * s * s + 3.689713992 * s * s + 2.128599134 * s - 0.343930367;
                    add("JUMP", String.format("%.1f blocks", blocks));
                }
            } else if (editing) {
                name = "Horse";
                health = 0.8f;
                add("HEALTH", "24 / 30");
                add("SPEED", "11.2 b/s");
                add("JUMP", "3.4 blocks");
            } else {
                return false;
            }
            w = 8;
            for (int i = 0; i < labels.size(); i++) w += Math.max(34, cellWidth(labels.get(i), values.get(i))) + 8;
            w = Math.max(w, Fonts.SEMIBOLD.widthAny(name, 8f) + 16);
            h = 42;
            return true;
        }

        private void add(String label, String value) {
            labels.add(label);
            values.add(value);
        }

        @Override
        protected void render(Canvas c, boolean editing) {
            chip(c, Theme.R_MD);
            c.textAny(Fonts.SEMIBOLD, name, 8, 4.5f, 8f, Theme.TEXT);
            float x = 8;
            for (int i = 0; i < labels.size(); i++) x += Math.max(34, cell(c, x, 16, labels.get(i), values.get(i), Theme.TEXT)) + 8;
            bar(c, 8, h - 6.5f, w - 16, health, Colors.mix(Theme.DANGER, Theme.SUCCESS, health));
        }
    }

    public static final class SessionStats extends HudModule {
        public final Settings.Bool kills = bool("kills", "Kills", true);
        public final Settings.Bool deaths = bool("deaths", "Deaths", true);
        public final Settings.Bool hits = bool("hits", "Hits landed", false);
        public final Settings.Bool distance = bool("distance", "Distance travelled", true);
        private final List<String> labels = new ArrayList<>(), values = new ArrayList<>();

        public SessionStats() {
            super("session_stats", "Session stats", "Kills, deaths and distance since you joined", AnchorH.RIGHT, AnchorV.TOP, 6, 180);
            keywords("kills", "deaths", "kd", "distance", "travelled", "score");
        }

        @Override
        protected boolean measure(boolean editing) {
            labels.clear();
            values.clear();
            var live = Session.current();
            if (kills.get()) add("KILLS", Integer.toString(live.kills));
            if (deaths.get()) add("DEATHS", Integer.toString(live.deaths));
            if (hits.get()) add("HITS", Integer.toString(live.hits));
            if (distance.get()) add("DISTANCE", live.distance >= 1000 ? String.format("%.1f km", live.distance / 1000) : Math.round(live.distance) + " m");
            if (labels.isEmpty()) return false;
            w = 8;
            for (int i = 0; i < labels.size(); i++) w += Math.max(26, cellWidth(labels.get(i), values.get(i))) + 8;
            h = 25;
            return true;
        }

        private void add(String label, String value) {
            labels.add(label);
            values.add(value);
        }

        @Override
        protected void render(Canvas c, boolean editing) {
            chip(c, Theme.R_MD);
            float x = 8;
            for (int i = 0; i < labels.size(); i++) x += Math.max(26, cell(c, x, 4, labels.get(i), values.get(i), Theme.TEXT)) + 8;
        }
    }

    public static final class PackDisplay extends HudModule {
        public final Settings.Bool picture = bool("picture", "Show the pack's picture", true);
        private String shownId, title = "", more = "";
        private Picture icon;

        public PackDisplay() {
            super("pack_display", "Pack display", "The resource pack on top of your list", AnchorH.RIGHT, AnchorV.TOP, 6, 156);
            keywords("resource", "texture", "pack");
        }

        /** Reads the pack list; the picture is only fetched again when the top pack changes. */
        private void read() {
            net.minecraft.server.packs.repository.Pack top = null;
            int count = 0;
            for (var pack : Mc.mc().getResourcePackRepository().getSelectedPacks()) {
                if (!pack.getId().startsWith("file/")) continue;
                top = pack;
                count++;
            }
            String id = top == null ? "" : top.getId();
            more = count > 1 ? "+" + (count - 1) + " more" : "";
            if (id.equals(shownId)) return;
            shownId = id;
            if (icon != null) icon.close();
            icon = null;
            title = top == null ? "Default textures" : top.getTitle().getString();
            if (top == null) return;
            try (var resources = top.open()) {
                var supplier = resources.getRootResource("pack.png");
                if (supplier == null) return;
                try (var in = supplier.get()) {
                    icon = new Picture(com.mojang.blaze3d.platform.NativeImage.read(in));
                }
            } catch (Exception e) {
                // a pack without a readable picture is shown by name alone
            }
        }

        @Override
        protected boolean measure(boolean editing) {
            read();
            float left = picture.get() && icon != null ? 26 : 7;
            w = left + Math.max(Fonts.SEMIBOLD.widthAny(title, 8f), Fonts.MEDIUM.width(more, 6.5f)) + 8;
            h = picture.get() && icon != null || !more.isEmpty() ? 26 : 20;
            return true;
        }

        @Override
        protected void render(Canvas c, boolean editing) {
            chip(c, Theme.R_MD);
            float left = 7;
            if (picture.get() && icon != null) {
                icon.draw(c, 4, 4, 18, 18);
                left = 26;
            }
            if (more.isEmpty()) {
                c.textAny(Fonts.SEMIBOLD, title, left, (h - Fonts.SEMIBOLD.height(8f)) / 2, 8f, Theme.TEXT);
            } else {
                c.textAny(Fonts.SEMIBOLD, title, left, 4.5f, 8f, Theme.TEXT);
                c.text(Fonts.MEDIUM, more, left, 15, 6.5f, Theme.TEXT_DIM);
            }
        }
    }

    public static final class NowPlaying extends HudModule {
        public final Settings.Bool hidePaused = bool("hide_paused", "Hide while paused", false);
        public final Settings.Bool progress = bool("progress", "Show how far the track is", true);
        public final Settings.Num width = num("width", "Widest", 150f, 90f, 260f, 10f).suffix("px");
        public final Settings.Key playPause = key("play_pause", "Play or pause", Settings.Key.NONE);
        public final Settings.Key next = key("next", "Next track", Settings.Key.NONE);
        public final Settings.Key previous = key("previous", "Previous track", Settings.Key.NONE);
        private final boolean[] wasDown = new boolean[3];
        private String title = "", artist = "", time = "";
        private boolean playing;
        private float fraction = -1;

        public NowPlaying() {
            super("now_playing", "Now playing", "The song your computer is playing, from Spotify, a browser or any player, with keys to pause and skip",
                    AnchorH.LEFT, AnchorV.BOTTOM, 76, 6);
            keywords("music", "spotify", "song", "media", "track", "skip", "pause", "audio");
        }

        @Override
        public void tick() {
            Settings.Key[] keys = {playPause, next, previous};
            for (int i = 0; i < 3; i++) {
                boolean down = Mc.screen() == null && Mc.isDown(keys[i].get());
                if (down && !wasDown[i]) dev.aller.feature.Media.command(i);
                wasDown[i] = down;
            }
        }

        private static String clock(long millis) {
            long s = millis / 1000;
            return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
        }

        @Override
        protected boolean measure(boolean editing) {
            var now = dev.aller.feature.Media.now();
            if (now != null && !(hidePaused.get() && !now.playing() && !editing)) {
                title = now.title().isBlank() ? "Unknown track" : now.title();
                artist = now.artist();
                playing = now.playing();
                fraction = now.length() > 0 ? now.at() / (float) now.length() : -1;
                time = now.length() > 0 ? clock(now.at()) + " / " + clock(now.length()) : "";
            } else if (editing) {
                title = "Nothing playing";
                artist = dev.aller.feature.Media.usable() ? "Start a song in any player" : "Needs 64-bit Windows";
                playing = false;
                fraction = -1;
                time = "";
            } else {
                return false;
            }
            boolean bar = progress.get() && fraction >= 0;
            float room = width.get() - 29;
            title = Fonts.SEMIBOLD.truncateAny(title, 8f, room);
            artist = Fonts.MEDIUM.truncateAny(artist, 7f, room - (bar && !time.isEmpty() ? Fonts.MEDIUM.width(time, 6.5f) + 8 : 0));
            w = Math.max(80, 22 + Math.max(Fonts.SEMIBOLD.widthAny(title, 8f),
                    Fonts.MEDIUM.widthAny(artist, 7f) + (bar && !time.isEmpty() ? Fonts.MEDIUM.width(time, 6.5f) + 8 : 0)) + 7);
            h = (artist.isEmpty() && !bar ? 18 : 26) + (bar ? 5 : 0);
            return true;
        }

        @Override
        protected void render(Canvas c, boolean editing) {
            chip(c, Theme.R_MD);
            boolean bar = progress.get() && fraction >= 0;
            float mid = (h - (bar ? 5 : 0)) / 2;
            // Two bars while it plays, a triangle while it waits.
            if (playing) {
                c.rect(8, mid - 4, 2.4f, 8, 1, Theme.accent());
                c.rect(12.6f, mid - 4, 2.4f, 8, 1, Theme.accent());
            } else {
                c.push();
                c.rotate((float) (Math.PI / 2), 11.5f, mid);
                c.polygon(11.5f, mid, 5, 3, 0.8f, Theme.TEXT_DIM);
                c.pop();
            }
            if (artist.isEmpty() && !bar) {
                c.textAny(Fonts.SEMIBOLD, title, 22, (h - Fonts.SEMIBOLD.height(8f)) / 2, 8f, Theme.TEXT);
                return;
            }
            c.textAny(Fonts.SEMIBOLD, title, 22, 4.5f, 8f, Theme.TEXT);
            c.textAny(Fonts.MEDIUM, artist, 22, 15f, 7f, Theme.TEXT_DIM);
            if (bar) {
                if (!time.isEmpty()) c.textRight(Fonts.MEDIUM, time, w - 7, 15.5f, 6.5f, Theme.TEXT_MUTED);
                bar(c, 7, h - 6.5f, w - 14, fraction, Theme.accent());
            }
        }
    }
}
