package dev.aller.feature;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.aller.AllerClient;
import dev.aller.config.Config;
import dev.aller.platform.Game;
import dev.aller.ui.Toasts;

import java.util.ArrayList;
import java.util.List;

/**
 * Context-aware profiles: rules that switch the whole mod and HUD setup automatically depending
 * on where the player is or what they are doing. The first matching rule wins; when none match,
 * the profile the player last chose by hand comes back.
 */
public final class AutoProfiles {
    public enum Kind {
        SERVER("On server", "Address contains"),
        SINGLEPLAYER("In singleplayer", null),
        DIMENSION("In dimension", "overworld, the_nether, the_end"),
        COMBAT("In combat", null);

        public final String label;
        /** Placeholder for the match field, or null if this kind takes no argument. */
        public final String hint;

        Kind(String label, String hint) {
            this.label = label;
            this.hint = hint;
        }
    }

    public static final class Rule {
        public Kind kind = Kind.SERVER;
        public String match = "";
        public String profile = Config.DEFAULT_PROFILE;
    }

    private static final List<Rule> rules = new ArrayList<>();
    private static boolean loaded;
    /** The profile to return to when no rule applies. */
    private static String base;
    private static Rule applied;
    private static int combatTicks;
    private static int lastHurt, lastCombo;
    private static int counter;

    private AutoProfiles() {}

    public static List<Rule> rules() {
        if (!loaded) {
            loaded = true;
            JsonElement saved = AllerClient.config().extra("profile_rules");
            if (saved != null && saved.isJsonArray()) {
                for (JsonElement e : saved.getAsJsonArray()) {
                    try {
                        JsonObject o = e.getAsJsonObject();
                        Rule r = new Rule();
                        r.kind = Kind.valueOf(o.get("kind").getAsString());
                        r.match = o.get("match").getAsString();
                        r.profile = o.get("profile").getAsString();
                        rules.add(r);
                    } catch (RuntimeException ignored) {
                        // skip a malformed rule rather than losing the rest
                    }
                }
            }
        }
        return rules;
    }

    public static void save() {
        JsonArray arr = new JsonArray();
        for (Rule r : rules()) {
            JsonObject o = new JsonObject();
            o.addProperty("kind", r.kind.name());
            o.addProperty("match", r.match);
            o.addProperty("profile", r.profile);
            arr.add(o);
        }
        AllerClient.config().setExtra("profile_rules", arr);
    }

    /** The user picked a profile by hand: that becomes the one to fall back to. */
    public static void manualSwitch(String profile) {
        base = null;
        applied = null;
        AllerClient.config().switchProfile(profile);
    }

    public static Rule applied() {
        return applied;
    }

    public static void tick() {
        var player = Game.player();
        if (player == null) {
            combatTicks = 0;
            if (applied != null) restore();
            return;
        }
        // "In combat" means having dealt or taken a hit in the last eight seconds.
        int combo = Combat.combo();
        if (player.hurtTime > lastHurt || combo > lastCombo) combatTicks = 160;
        else if (combatTicks > 0) combatTicks--;
        lastHurt = player.hurtTime;
        lastCombo = combo;

        if (++counter % 10 != 0) return;
        if (!AllerClient.options().autoProfiles.get()) {
            if (applied != null) restore();
            return;
        }
        Rule match = null;
        for (Rule r : rules()) {
            if (matches(r)) {
                match = r;
                break;
            }
        }
        if (match == applied) return;
        if (match == null) {
            restore();
            return;
        }
        Config config = AllerClient.config();
        if (applied == null) base = config.activeProfile();
        applied = match;
        if (!config.activeProfile().equals(match.profile)) {
            config.switchProfile(match.profile);
            Toasts.info("Profile: " + match.profile, match.kind.label + (match.match.isEmpty() ? "" : " " + match.match));
        }
    }

    private static void restore() {
        applied = null;
        if (base != null && !AllerClient.config().activeProfile().equals(base)) {
            AllerClient.config().switchProfile(base);
            if (Game.player() != null) Toasts.info("Profile: " + base, "Back to your usual setup");
        }
        base = null;
    }

    private static boolean matches(Rule r) {
        String address = Game.serverAddress();
        return switch (r.kind) {
            case SERVER -> address != null && !r.match.isBlank() && address.toLowerCase().contains(r.match.trim().toLowerCase());
            case SINGLEPLAYER -> address == null;
            case DIMENSION -> Game.dimensionId().equalsIgnoreCase(r.match.trim());
            case COMBAT -> combatTicks > 0;
        };
    }
}
