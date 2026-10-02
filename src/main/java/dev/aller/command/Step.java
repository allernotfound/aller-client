package dev.aller.command;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * What the launcher's bar turns into when a command needs more than Enter: a number to dial in,
 * a line of text, or a choice from a list.
 */
public abstract class Step {
    /** Shown in the bar in place of the search icon ("FOV"). */
    public final String title;

    protected Step(String title) {
        this.title = title;
    }

    /** A value on a range. Changes apply as they are made, so the game behind shows the result; Escape puts the old value back. */
    public static final class Num extends Step {
        public final float min, max, step;
        public final Supplier<Float> get;
        public final Consumer<Float> set;
        public final Function<Float, String> format;
        /** Run when the value is kept (to save the config). */
        public Runnable saved = () -> {};

        public Num(String title, float min, float max, float step, Supplier<Float> get, Consumer<Float> set, Function<Float, String> format) {
            super(title);
            this.min = min;
            this.max = max;
            this.step = step;
            this.get = get;
            this.set = set;
            this.format = format;
        }

        public Num saved(Runnable r) {
            saved = r;
            return this;
        }

        public float clamp(float v) {
            float snapped = step > 0 ? Math.round(v / step) * step : v;
            return Math.clamp(Math.round(snapped * 10000f) / 10000f, min, max);
        }

        /** The value as it is typed and stored in the history: "90", "0.5". */
        public String plain(float v) {
            return step >= 1 ? Integer.toString(Math.round(v)) : Float.toString(Math.round(v * 100f) / 100f);
        }
    }

    /** One line of text. */
    public static final class Text extends Step {
        public final String placeholder, hint;
        public int maxLength = 64;
        public Supplier<String> initial = () -> "";
        /** Takes the text and returns the next step of a longer flow, or null when done. */
        public final Function<String, Step> submit;
        /** Names the one-line form ("Add waypoint Base here"); null if the text can only be given as a step. */
        public Function<String, String> inline;
        /** Rejects text before it is submitted (not a colour, say). */
        public java.util.function.Predicate<String> valid = s -> !s.isBlank();

        public Text(String title, String placeholder, String hint, Function<String, Step> submit) {
            super(title);
            this.placeholder = placeholder;
            this.hint = hint;
            this.submit = submit;
        }

        public Text max(int length) {
            maxLength = length;
            return this;
        }

        public Text initial(Supplier<String> text) {
            initial = text;
            return this;
        }

        public Text inline(Function<String, String> name) {
            inline = name;
            return this;
        }

        public Text valid(java.util.function.Predicate<String> test) {
            valid = test;
            return this;
        }
    }

    /** A list to choose from, filtered by typing. */
    public static final class Pick extends Step {
        public final Supplier<List<Command>> options;
        public final String empty;

        /** @param empty what to say when there is nothing to choose from */
        public Pick(String title, String empty, Supplier<List<Command>> options) {
            super(title);
            this.options = options;
            this.empty = empty;
        }
    }
}
