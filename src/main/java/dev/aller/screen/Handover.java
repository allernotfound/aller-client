package dev.aller.screen;

import dev.aller.AllerClient;
import dev.aller.platform.Mc;
import dev.aller.ui.AllerScreen;
import dev.aller.ui.anim.Spring;

/**
 * Hands a menu over to a Minecraft screen without a cut: the menu's content fades out, the next
 * screen is shown (and eases in, see {@link Entrance}), and the content fades back in when the
 * player returns.
 */
final class Handover {
    private final Spring leave = Spring.smooth(0);
    private Runnable action;
    private boolean away;

    /** Fade out, then run the navigation. */
    void go(Runnable next) {
        if (action != null) return;
        action = next;
        leave.target(1);
    }

    boolean leaving() {
        return action != null;
    }

    /** Advances the fade; call once a frame. @return how far the content has left, 0 to 1 */
    float update(AllerScreen owner) {
        float v = leave.update();
        if (action != null && v > 0.97f) {
            Runnable next = action;
            action = null;
            AllerClient.defer(() -> {
                Entrance.start();
                next.run();
                // Nothing opened after all (Realms unavailable, say): come back.
                away = Mc.current() != owner;
                if (!away) leave.target(0);
            });
        }
        return Math.clamp(v, 0f, 1f);
    }

    void reset() {
        leave.snap(0);
        action = null;
        away = false;
    }

    /** The menu is on show again after the screen it handed over to closed. */
    void back() {
        // Shown again from under a panel of its own, it never left.
        if (away) leave.snap(1).target(0);
        away = false;
        action = null;
    }
}
