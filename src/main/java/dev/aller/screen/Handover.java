package dev.aller.screen;

import dev.aller.AllerClient;
import dev.aller.platform.Mc;
import dev.aller.ui.AllerScreen;
import dev.aller.ui.anim.Spring;

/**
 * Hands a menu over to a Minecraft screen without a cut. The next screen is shown at once and eases
 * in (see {@link Entrance}) while this menu's content, drawn over it, slides away; the content
 * fades back in when the player returns.
 */
final class Handover {
    private final AllerScreen owner;
    private final Spring leave = Spring.smooth(0);
    private boolean going, away;

    Handover(AllerScreen owner) {
        this.owner = owner;
    }

    /** Start leaving and run the navigation. */
    void go(Runnable next) {
        if (going || away) return;
        going = true;
        leave.target(1);
        // Between frames, not in the middle of drawing this one.
        AllerClient.defer(() -> {
            going = false;
            Entrance.from(owner);
            try {
                next.run();
            } finally {
                Entrance.from(null);
            }
            // Nothing opened after all (Realms unavailable, say): come back.
            away = Mc.current() != owner;
            if (!away) leave.target(0);
        });
    }

    /** True from the click until the menu is back, so it takes no input meanwhile. */
    boolean leaving() {
        return going || away;
    }

    /** Advances the fade; call once a frame. @return how far the content has left, 0 to 1 */
    float update() {
        return Math.clamp(leave.update(), 0f, 1f);
    }

    void reset() {
        leave.snap(0);
        going = away = false;
    }

    /** The menu is on show again after the screen it handed over to closed. */
    void back() {
        // Shown again from under a panel of its own, it never left.
        if (away) leave.snap(1).target(0);
        away = going = false;
    }
}
