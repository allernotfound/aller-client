package dev.aller.platform;

import java.util.function.Supplier;
//? if <26.1 {
/*import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.PlayerSkin;
*///?} else {
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.world.entity.player.PlayerSkin;
//?}

/** The local player's skin, usable outside a world (e.g. on the main menu). */
public final class Skins {
    private static Supplier<PlayerSkin> self;

    private Skins() {}

    private static PlayerSkin own() {
        if (self == null) {
            var profile = Mc.mc().getGameProfile();
            //? if <26.1 {
            /*self = Mc.mc().getSkinManager().lookupInsecure(profile);
            *///?} else {
            self = Mc.mc().getSkinManager().createLookup(profile, false);
            //?}
        }
        return self.get();
    }

    /** Draws the player's face (with hat layer). The default skin is shown until the real one has downloaded. */
    public static void drawOwnFace(Canvas c, float x, float y, int size) {
        //? if <26.1 {
        /*PlayerFaceRenderer.draw(c.raw(), own(), Math.round(x), Math.round(y), size);
        *///?} else {
        PlayerFaceExtractor.extractRenderState(c.raw(), own(), Math.round(x), Math.round(y), size);
        //?}
    }

    /** "Slim" or "Classic", describing the arm width of the player's model. */
    public static String ownModel() {
        return own().model().name().equals("SLIM") ? "Slim arms" : "Classic arms";
    }
}
