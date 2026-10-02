package dev.aller.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** The concrete setting types. Each maps to one widget in the settings panel. */
public final class Settings {
    private Settings() {}

    public static final class Bool extends Setting<Boolean> {
        public Bool(String id, String name, boolean def) {
            super(id, name, def);
        }

        public void toggle() {
            set(!value);
        }

        @Override
        public JsonElement save() {
            return new JsonPrimitive(value);
        }

        @Override
        public void load(JsonElement json) {
            if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean()) set(json.getAsBoolean());
        }
    }

    public static final class Num extends Setting<Float> {
        public final float min, max, step;
        public String suffix = "";
        private java.util.function.Function<Float, String> format;

        public Num(String id, String name, float def, float min, float max, float step) {
            super(id, name, def);
            this.min = min;
            this.max = max;
            this.step = step;
        }

        public Num suffix(String s) {
            suffix = s;
            return this;
        }

        /** Replaces the plain number shown beside the slider ("12:30" for a time, "Never" for zero). */
        public Num format(java.util.function.Function<Float, String> f) {
            format = f;
            return this;
        }

        @Override
        public void set(Float v) {
            if (v.isNaN()) return;
            float snapped = step > 0 ? Math.round(v / step) * step : v;
            // Trim float noise so 0.3 is saved as 0.3 rather than 0.3000001.
            super.set(Math.clamp(Math.round(snapped * 10000f) / 10000f, min, max));
        }

        public int asInt() {
            return Math.round(value);
        }

        public String display() {
            if (format != null) return format.apply(value);
            String num = step >= 1 ? Integer.toString(asInt()) : String.format(step >= 0.1f ? "%.1f" : "%.2f", value);
            return num + suffix;
        }

        @Override
        public JsonElement save() {
            return new JsonPrimitive(value);
        }

        @Override
        public void load(JsonElement json) {
            if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) set(json.getAsFloat());
        }
    }

    /** ARGB colour. */
    public static final class Color extends Setting<Integer> {
        public final boolean alpha;

        public Color(String id, String name, int argb, boolean allowAlpha) {
            super(id, name, argb);
            this.alpha = allowAlpha;
        }

        @Override
        public JsonElement save() {
            return new JsonPrimitive(String.format("#%08X", value));
        }

        @Override
        public void load(JsonElement json) {
            try {
                String hex = json.getAsString().replace("#", "").trim();
                int argb = (int) Long.parseLong(hex, 16);
                // "#RRGGBB" written by hand has no alpha digits; treat it as opaque rather than invisible.
                if (hex.length() <= 6 || !alpha) argb |= 0xFF000000;
                set(argb);
            } catch (RuntimeException ignored) {
                // keep current value
            }
        }
    }

    public static final class Choice<E extends Enum<E>> extends Setting<E> {
        public final E[] options;

        public Choice(String id, String name, E def) {
            super(id, name, def);
            this.options = def.getDeclaringClass().getEnumConstants();
        }

        public void cycle(int direction) {
            set(options[Math.floorMod(value.ordinal() + direction, options.length)]);
        }

        /** "TOP_LEFT" -> "Top left". */
        public static String label(Enum<?> e) {
            String s = e.name().replace('_', ' ').toLowerCase();
            return Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }

        @Override
        public JsonElement save() {
            return new JsonPrimitive(value.name());
        }

        @Override
        public void load(JsonElement json) {
            try {
                String name = json.getAsString();
                for (E o : options) if (o.name().equals(name)) set(o);
            } catch (RuntimeException ignored) {
                // keep current value
            }
        }
    }

    /** A GLFW key code, or -1 for unbound. Negative values below -1 encode mouse buttons as {@code -2 - button}. */
    public static final class Key extends Setting<Integer> {
        public static final int NONE = -1;

        public Key(String id, String name, int def) {
            super(id, name, def);
        }

        @Override
        public JsonElement save() {
            return new JsonPrimitive(value);
        }

        @Override
        public void load(JsonElement json) {
            if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) set(json.getAsInt());
        }
    }

    public static final class Text extends Setting<String> {
        public final int maxLength;

        public Text(String id, String name, String def, int maxLength) {
            super(id, name, def);
            this.maxLength = maxLength;
        }

        @Override
        public JsonElement save() {
            return new JsonPrimitive(value);
        }

        @Override
        public void load(JsonElement json) {
            if (json.isJsonPrimitive()) {
                String s = json.getAsString();
                set(s.length() > maxLength ? s.substring(0, maxLength) : s);
            }
        }
    }
}
