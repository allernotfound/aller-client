package dev.aller.module.mods;

import dev.aller.module.Category;
import dev.aller.module.Module;
import dev.aller.platform.Game;
import dev.aller.setting.Settings;
import dev.aller.ui.Theme;

/**
 * Mods that work on the finished picture of the world (drawn by {@code feature/Effects}), plus the
 * fog and sky one. All of them leave the HUD and menus alone and cost nothing while switched off.
 */
public final class EffectMods {
    private EffectMods() {}

    /** How much work an effect may do: picture size and samples per pixel. */
    public enum Quality { LOW, MEDIUM, HIGH }

    private static final String PACK_NOTE = "Pauses while a shader pack is on";

    public static final class Bloom extends Module {
        public final Settings.Num strength = num("strength", "Strength", 40f, 5f, 100f, 5f).suffix("%");
        public final Settings.Num threshold = num("threshold", "Starts at brightness", 70f, 30f, 95f, 5f).suffix("%")
                .describe("Lower lets more of the picture glow");
        public final Settings.Num spread = num("spread", "Spread", 100f, 50f, 200f, 10f).suffix("%");
        public final Settings.Choice<Quality> quality = choice("quality", "Quality", Quality.MEDIUM)
                .describe("Low glows from a quarter-size picture, high adds a wider level");

        public Bloom() {
            super("bloom", "Bloom", "Bright things glow: sky, lava, torches in the dark", Category.VISUAL);
            keywords("glow", "shader", "light", "post");
        }
    }

    public static final class MotionBlur extends Module {
        public final Settings.Num strength = num("strength", "Shutter", 50f, 10f, 100f, 5f)
                .format(v -> "1/" + Math.round(3000f / v) + " s")
                .describe("How long the shutter stays open: longer blurs more. The same at any frame rate");
        public final Settings.Choice<Quality> quality = choice("quality", "Quality", Quality.MEDIUM).describe(PACK_NOTE);

        public MotionBlur() {
            super("motion_blur", "Motion blur", "Blur along the way the camera moves and turns, as a real shutter does", Category.VISUAL);
            keywords("blur", "smooth", "shutter", "camera", "shader");
        }
    }

    public static final class RimLight extends Module {
        public enum From { ALL_ROUND, ABOVE }

        public final Settings.Num strength = num("strength", "Strength", 40f, 10f, 100f, 5f).suffix("%");
        public final Settings.Bool followAccent = bool("follow_accent", "Use the accent colour", false);
        public final Settings.Color color = color("color", "Colour", 0xFFFFE9C4).visibleWhen(() -> !this.followAccent.get());
        public final Settings.Num width = num("width", "Width", 6f, 1f, 16f, 0.5f).suffix(" px");
        public final Settings.Num range = num("range", "Range", 48f, 8f, 128f, 8f).suffix(" blocks")
                .describe("Edges further away than this are left alone");
        public final Settings.Choice<From> from = choice("from", "Light from", From.ALL_ROUND);
        public final Settings.Bool heldItem = bool("held_item", "Light your hand and held item", true);
        public final Settings.Choice<Quality> quality = choice("quality", "Quality", Quality.MEDIUM).describe(PACK_NOTE);

        public RimLight() {
            super("rim_light", "Rim lighting", "A soft light along the outline of mobs, players, your hand and terrain", Category.VISUAL);
            keywords("outline", "edge", "silhouette", "fresnel", "shader");
        }

        public int tint() {
            return followAccent.get() ? Theme.accent() : color.get();
        }
    }

    public static final class DepthOfField extends Module {
        public final Settings.Num strength = num("strength", "Strength", 50f, 10f, 100f, 5f).suffix("%");
        public final Settings.Num speed = num("speed", "Focus speed", 5f, 1f, 10f, 1f);
        public final Settings.Bool near = bool("near", "Blur what is nearer than the focus", false)
                .describe("Off keeps everything between you and what you look at sharp");
        public final Settings.Choice<Quality> quality = choice("quality", "Quality", Quality.MEDIUM).describe(PACK_NOTE);
        private float target = 16f;

        public DepthOfField() {
            super("depth_of_field", "Depth of field", "Focus on what you look at and soften the rest", Category.VISUAL);
            keywords("dof", "focus", "blur", "bokeh", "shader");
        }

        @Override
        public void tick() {
            target = Game.lookDistance(96f);
        }

        /** Distance to whatever is under the crosshair, refreshed every tick. */
        public float target() {
            return target;
        }
    }

    public static final class ColourGrading extends Module {
        public enum Look {
            CUSTOM(0, 0, 0, 0, 0),
            VIVID(1.3f, 1.08f, 1f, 0f, 0.1f),
            WARM(1.1f, 1.04f, 1.02f, 0.5f, 0.15f),
            COOL(1.05f, 1.04f, 1f, -0.5f, 0.15f),
            CINEMATIC(0.9f, 1.15f, 0.97f, 0.15f, 0.35f),
            FADED(0.7f, 0.88f, 1.05f, 0.1f, 0.1f),
            NOIR(0f, 1.2f, 1f, 0f, 0.4f);

            final float saturation, contrast, brightness, temperature, vignette;

            Look(float saturation, float contrast, float brightness, float temperature, float vignette) {
                this.saturation = saturation;
                this.contrast = contrast;
                this.brightness = brightness;
                this.temperature = temperature;
                this.vignette = vignette;
            }
        }

        public final Settings.Choice<Look> look = choice("look", "Look", Look.VIVID);
        public final Settings.Num saturation = num("saturation", "Saturation", 110f, 0f, 200f, 5f).suffix("%").visibleWhen(this::custom);
        public final Settings.Num contrast = num("contrast", "Contrast", 105f, 50f, 150f, 5f).suffix("%").visibleWhen(this::custom);
        public final Settings.Num brightness = num("brightness", "Brightness", 100f, 50f, 150f, 5f).suffix("%").visibleWhen(this::custom);
        public final Settings.Num temperature = num("temperature", "Temperature", 0f, -100f, 100f, 10f)
                .format(v -> v == 0 ? "Neutral" : Math.round(Math.abs(v)) + (v > 0 ? "% warmer" : "% cooler"))
                .visibleWhen(this::custom);
        public final Settings.Bool tinted = bool("tinted", "Colour filter", false);
        public final Settings.Color tint = color("tint", "Filter colour", 0xFFFFB070).visibleWhen(() -> this.tinted.get());
        public final Settings.Num tintAmount = num("tint_amount", "Filter amount", 30f, 5f, 100f, 5f).suffix("%").visibleWhen(() -> this.tinted.get());
        public final Settings.Num vignette;
        public final Settings.Num vignetteSize;

        public ColourGrading() {
            super("colour_grading", "Colour grading", "Saturation, contrast, warmth, a colour filter and a vignette", Category.VISUAL);
            keywords("color", "saturation", "contrast", "vignette", "filter", "lut", "tone", "shader");
            section("Vignette");
            vignette = num("vignette", "Darken the edges", 25f, 0f, 100f, 5f)
                    .format(v -> v == 0 ? "Off" : Math.round(v) + "%")
                    .visibleWhen(this::custom);
            vignetteSize = num("vignette_size", "Clear area", 60f, 20f, 100f, 5f).suffix("%");
        }

        private boolean custom() {
            return look.get() == Look.CUSTOM;
        }

        public float saturation() {
            return custom() ? saturation.get() / 100f : look.get().saturation;
        }

        public float contrast() {
            return custom() ? contrast.get() / 100f : look.get().contrast;
        }

        public float brightness() {
            return custom() ? brightness.get() / 100f : look.get().brightness;
        }

        public float temperature() {
            return custom() ? temperature.get() / 100f : look.get().temperature;
        }

        public float vignette() {
            return custom() ? vignette.get() / 100f : look.get().vignette;
        }
    }

    public static final class Sharpen extends Module {
        public final Settings.Num amount = num("amount", "Amount", 50f, 10f, 100f, 5f).suffix("%");

        public Sharpen() {
            super("sharpen", "Sharpen", "Crisper textures and distant terrain, without halos", Category.VISUAL);
            keywords("cas", "sharpness", "crisp", "clarity", "shader");
        }
    }

    public static final class Atmosphere extends Module {
        public final Settings.Num distance = num("distance", "Fog distance", 150f, 25f, 400f, 25f)
                .format(v -> v == 100 ? "Normal" : Math.round(v) + "%")
                .describe("Above normal clears the haze, below thickens it. Water, lava and blindness are never changed");
        public final Settings.Bool fogTinted = bool("fog_tinted", "Colour the fog", false);
        public final Settings.Color fogTint = color("fog_tint", "Fog colour", 0xFFB8C8E8).visibleWhen(() -> this.fogTinted.get());
        public final Settings.Num fogAmount = num("fog_amount", "Fog colour amount", 50f, 5f, 100f, 5f).suffix("%").visibleWhen(() -> this.fogTinted.get());
        public final Settings.Bool skyTinted;
        public final Settings.Color skyTint;
        public final Settings.Num skyAmount;

        public Atmosphere() {
            super("atmosphere", "Fog and sky", "Push the fog back or pull it in, and tint the fog and the sky", Category.VISUAL);
            keywords("fog", "haze", "sky", "clear", "atmosphere", "horizon");
            section("Sky");
            skyTinted = bool("sky_tinted", "Colour the sky", false).describe(PACK_NOTE);
            skyTint = color("sky_tint", "Sky colour", 0xFF9B8CFF).visibleWhen(() -> this.skyTinted.get());
            skyAmount = num("sky_amount", "Sky colour amount", 40f, 5f, 100f, 5f).suffix("%").visibleWhen(() -> this.skyTinted.get());
        }

        /**
         * One of the fog distances on its way to the shaders. Only ordinary air fog is touched: seeing
         * further through water, lava or a blindness effect would be an advantage, not a look.
         */
        public float fog(int which, float value, float renderEnd) {
            float m = distance.get() / 100f;
            if (m == 1f || value > 1e30f || !Game.clearView()) return value;
            // 0 and 1: where the haze starts and ends. 2: where the fade at the edge of loaded chunks starts.
            if (which < 2) return value * m;
            return m < 1f ? value * m : renderEnd - (renderEnd - value) / m;
        }

        public void color(org.joml.Vector4f c) {
            if (!fogTinted.get() || !Game.clearView()) return;
            int t = fogTint.get();
            float a = fogAmount.get() / 100f;
            c.x += (((t >> 16) & 0xFF) / 255f - c.x) * a;
            c.y += (((t >> 8) & 0xFF) / 255f - c.y) * a;
            c.z += ((t & 0xFF) / 255f - c.z) * a;
        }
    }
}
