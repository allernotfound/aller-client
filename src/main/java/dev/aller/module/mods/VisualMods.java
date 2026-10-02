package dev.aller.module.mods;

import dev.aller.feature.Combat;
import dev.aller.module.Category;
import dev.aller.module.Module;
import dev.aller.platform.Canvas;
import dev.aller.platform.Game;
import dev.aller.platform.Mc;
import dev.aller.setting.Settings;
import dev.aller.ui.Colors;
import dev.aller.ui.Theme;
import dev.aller.ui.anim.Motion;
import dev.aller.ui.anim.Spring;
import net.minecraft.client.CameraType;
import org.lwjgl.glfw.GLFW;

/** Mods that change how the game looks without changing what the player can do. */
public final class VisualMods {
    private VisualMods() {}

    public static final class Zoom extends Module {
        public final Settings.Num factor = num("factor", "Zoom", 4f, 1.5f, 20f, 0.5f).suffix("x");
        public final Settings.Bool scrollAdjust = bool("scroll", "Scroll to adjust", true);
        public final Settings.Bool rememberScroll = bool("remember_scroll", "Remember scrolled level", false)
                .describe("Start the next zoom where the last one ended")
                .visibleWhen(() -> this.scrollAdjust.get());
        public final Settings.Bool smooth = bool("smooth", "Animated zoom", true);
        public final Settings.Bool cinematic = bool("cinematic", "Smooth camera while zoomed", true)
                .describe("Glides the view like the cinematic camera, which steadies high zoom levels");
        public final Settings.Bool slowMouse = bool("slow_mouse", "Lower sensitivity while zoomed", true)
                .describe("Scales mouse speed with the zoom so aiming stays precise");
        private final Spring level = new Spring(1f, 240f, 26f);
        private float live;
        private boolean restoreSmooth, smoothed;

        public Zoom() {
            super("zoom", "Zoom", "Hold a key to zoom in, scroll to adjust", Category.VISUAL);
            keywords("optifine", "spyglass", "magnify");
            holdKey(GLFW.GLFW_KEY_C);
            onByDefault();
        }

        @Override
        protected void onHeldChanged(boolean down) {
            var options = Mc.mc().options;
            if (down) {
                if (!rememberScroll.get() || !scrollAdjust.get() || live < 1.5f) live = factor.get();
                if (cinematic.get()) {
                    restoreSmooth = options.smoothCamera;
                    options.smoothCamera = true;
                    smoothed = true;
                }
            } else if (smoothed) {
                // Tracked separately so switching the setting off mid-zoom cannot leave the camera gliding.
                options.smoothCamera = restoreSmooth;
                smoothed = false;
            }
        }

        public float apply(float fov) {
            float target = held() ? live : 1f;
            if (!smooth.get()) level.snap(target);
            float z = Math.max(1f, level.target(target).update());
            return fov / z;
        }

        /** Factor applied to mouse movement: turning slows in proportion to the magnification. */
        public float mouseScale() {
            if (!held() || !slowMouse.get()) return 1f;
            return 1f / Math.max(1f, level.get());
        }

        public boolean scroll(double amount) {
            if (!held() || !scrollAdjust.get()) return false;
            live = Math.clamp(live * (amount > 0 ? 1.15f : 1 / 1.15f), 1.5f, 40f);
            return true;
        }
    }

    public static final class Fullbright extends Module {
        public final Settings.Num brightness = num("brightness", "Brightness", 100f, 10f, 100f, 5f).suffix("%");

        public Fullbright() {
            super("fullbright", "Fullbright", "See clearly in the dark without torches", Category.VISUAL);
            keywords("gamma", "night vision", "brightness");
            restricted("Fullbright is not allowed on some competitive servers.");
        }

        public float gamma(float original) {
            return Math.max(original, 1f + 14f * brightness.get() / 100f);
        }
    }

    public static final class NoHurtCam extends Module {
        public NoHurtCam() {
            super("no_hurt_cam", "No hurt shake", "Stop the camera tilting when you take damage", Category.VISUAL);
            keywords("hurtcam", "damage tilt", "shake");
        }
    }

    public static final class LowFire extends Module {
        public final Settings.Num offset = num("offset", "Lower by", 0.3f, 0.05f, 0.6f, 0.05f)
                .format(v -> Math.round(v * 100) + "%");

        public LowFire() {
            super("low_fire", "Low fire", "Lower the on-fire overlay so it blocks less of your view", Category.VISUAL);
            keywords("burning", "overlay");
        }
    }

    public static final class TimeChanger extends Module {
        public final Settings.Num hour = num("hour", "Time of day", 12f, 0f, 23.5f, 0.5f)
                .format(h -> String.format("%02d:%02d", (int) Math.floor(h), Math.round((h - (float) Math.floor(h)) * 60)));

        public TimeChanger() {
            super("time_changer", "Time changer", "Show the world at a fixed time of day (visual only)", Category.VISUAL);
            keywords("day", "night", "sunset");
        }

        /** Swaps the time of day and keeps the day itself, so the day counter and moon phase still advance. */
        public long time(long original) {
            // Minecraft's tick 0 is 06:00.
            long ofDay = Math.floorMod(Math.round((hour.get() - 6f) * 1000f), 24000);
            return original - Math.floorMod(original, 24000L) + ofDay;
        }
    }

    public static final class WeatherChanger extends Module {
        public enum Weather { CLEAR, RAIN, THUNDER }

        public final Settings.Choice<Weather> weather = choice("weather", "Weather", Weather.CLEAR);

        public WeatherChanger() {
            super("weather_changer", "Weather changer", "Show clear skies, rain or storms regardless of the server (visual only)", Category.VISUAL);
            keywords("rain", "snow", "storm", "clear");
        }

        public float rain() {
            return weather.get() == Weather.CLEAR ? 0f : 1f;
        }

        public float thunder() {
            return weather.get() == Weather.THUNDER ? 1f : 0f;
        }
    }

    public static final class BlockOutline extends Module {
        public final Settings.Bool followAccent = bool("follow_accent", "Use the accent colour", true);
        public final Settings.Color color = color("color", "Colour", 0xFF8B5CF6).visibleWhen(() -> !this.followAccent.get());

        public BlockOutline() {
            super("block_outline", "Block outline", "Recolour the outline of the block you are looking at", Category.VISUAL);
            keywords("selection", "highlight", "overlay");
        }

        public int tint() {
            return followAccent.get() ? Theme.accent() : color.get();
        }
    }

    public static final class Scoreboard extends Module {
        public Scoreboard() {
            super("scoreboard", "Hide scoreboard", "Hide the server scoreboard sidebar", Category.VISUAL);
            keywords("sidebar", "objective", "scoreboard");
        }
    }

    public static final class Crosshair extends Module {
        public enum Shape { CROSS, DOT, CIRCLE, CROSS_DOT, T_SHAPE }

        public final Settings.Choice<Shape> shape = choice("shape", "Shape", Shape.CROSS);
        public final Settings.Color color = color("color", "Colour", 0xFFFFFFFF);
        public final Settings.Num size = num("size", "Size", 4f, 1f, 12f, 0.5f);
        public final Settings.Num gap = num("gap", "Gap", 2f, 0f, 8f, 0.5f)
                .visibleWhen(() -> this.shape.get() != Shape.DOT && this.shape.get() != Shape.CIRCLE);
        public final Settings.Num thickness = num("thickness", "Thickness", 1f, 0.5f, 4f, 0.25f);
        public final Settings.Bool outline = bool("outline", "Dark outline", true);
        public final Settings.Bool dynamic = bool("dynamic", "Spread when moving", false);
        public final Settings.Bool hitMarker = bool("hit_marker", "Flash on hit", true);
        public final Settings.Color hitColor = color("hit_color", "Hit colour", 0xFFF2617A).visibleWhen(() -> this.hitMarker.get());
        public final Settings.Bool attackIndicator = bool("attack_indicator", "Attack indicator", true)
                .describe("A bar while your attack recharges, if Minecraft's indicator is set to Crosshair");
        public final Settings.Bool thirdPerson = bool("third_person", "Show in third person", false);
        private final Spring spread = Spring.snappy(0);
        private float hitAt = -10;
        private int lastCombo;

        public Crosshair() {
            super("crosshair", "Custom crosshair", "Replace the crosshair with a crisp, configurable one", Category.VISUAL);
            keywords("reticle", "aim", "cursor");
        }

        public void draw(Canvas c) {
            if (Mc.mc().options.getCameraType() != CameraType.FIRST_PERSON && !thirdPerson.get()) return;
            var player = Game.player();
            float speed = (float) player.getDeltaMovement().horizontalDistance();
            float sp = spread.target(dynamic.get() ? Math.min(speed * 18f, 5f) : 0).update();

            // A landed hit kicks the marker, which then eases back. Timed rather than sprung, so it
            // still shows with reduce motion on.
            int combo = Combat.combo();
            float now = Motion.time();
            if (combo > lastCombo && hitMarker.get()) hitAt = now;
            lastCombo = combo;
            float h = Math.clamp(1f - (now - hitAt) / 0.28f, 0f, 1f);
            h *= h;

            float cx = c.width() / 2, cy = c.height() / 2;
            drawAt(c, cx, cy, sp, h);

            float charge = Game.attackCharge();
            if (attackIndicator.get() && charge < 1f) {
                float bw = 14, by = cy + Math.max(size.get() + gap.get(), 6) + sp + 5;
                c.rect(cx - bw / 2 - 0.6f, by - 0.6f, bw + 1.2f, 3.2f, 1.6f, Colors.withAlpha(Colors.BLACK, 0.5f));
                c.rect(cx - bw / 2, by, bw * charge, 2, 1, color.get());
            }
        }

        /** Draws the crosshair centred on a point, e.g. for the preview in its settings. */
        public void drawAt(Canvas c, float cx, float cy, float sp, float h) {
            float len = size.get(), g = gap.get() + sp + h * 1.5f, t = thickness.get();
            int col = Colors.mix(color.get(), hitColor.get(), Math.clamp(h * 1.4f, 0f, 1f));
            int dark = Colors.withAlpha(Colors.BLACK, 0.55f * Colors.alpha(col) / 255f);
            Shape s = shape.get();

            if (s == Shape.CIRCLE) {
                float r = len + sp;
                if (outline.get()) c.ring(cx, cy, r + 0.6f, t + 1.2f, dark);
                c.ring(cx, cy, r, t, col);
                return;
            }
            if (s == Shape.CROSS || s == Shape.CROSS_DOT || s == Shape.T_SHAPE) {
                bar(c, cx - g - len, cy - t / 2, len, t, col, dark);
                bar(c, cx + g, cy - t / 2, len, t, col, dark);
                bar(c, cx - t / 2, cy + g, t, len, col, dark);
                if (s != Shape.T_SHAPE) bar(c, cx - t / 2, cy - g - len, t, len, col, dark);
            }
            if (s == Shape.DOT || s == Shape.CROSS_DOT) {
                float r = s == Shape.DOT ? Math.max(t, len / 3f) : t * 0.75f;
                if (outline.get()) c.circle(cx, cy, r + 0.6f, dark);
                c.circle(cx, cy, r, col);
            }
        }

        private void bar(Canvas c, float x, float y, float w, float h, int col, int dark) {
            if (outline.get()) c.rect(x - 0.6f, y - 0.6f, w + 1.2f, h + 1.2f, 0.8f, dark);
            c.rect(x, y, w, h, 0.4f, col);
        }
    }
}
