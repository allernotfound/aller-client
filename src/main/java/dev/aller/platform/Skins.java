package dev.aller.platform;

import java.util.function.Supplier;
//? if <26.1 {
/*import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
*///?} else {
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
//?}

/** The local player's skin, usable outside a world (e.g. on the main menu). */
public final class Skins {
    private static Supplier<PlayerSkin> self;
    /** A skin put on from the wardrobe this session: the game itself only asks for its own skin at startup. */
    private static SkinTex worn;
    private static boolean wornSlim;
    private static PlayerSkin lastIn, lastOut;

    private Skins() {}

    private static PlayerSkin fetched() {
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

    private static PlayerSkin own() {
        return apply(fetched());
    }

    /** Shows a wardrobe skin on the local player from now on. */
    public static void wear(SkinTex skin, boolean slim) {
        worn = skin;
        wornSlim = slim;
        lastIn = null;
    }

    /** Called for the local player's skin every time it is looked up. */
    public static PlayerSkin apply(PlayerSkin skin) {
        if (worn == null) return skin;
        if (skin != lastIn) {
            lastIn = skin;
            //? if <26.1 {
            /*lastOut = new PlayerSkin(worn.id(), null, skin.capeTexture(), skin.elytraTexture(),
                    wornSlim ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE, skin.secure());
            *///?} else {
            lastOut = new PlayerSkin(new ClientAsset.ResourceTexture(worn.id(), worn.id()), skin.cape(), skin.elytra(),
                    wornSlim ? PlayerModelType.SLIM : PlayerModelType.WIDE, skin.secure());
            //?}
        }
        return lastOut;
    }

    /** The texture the game downloaded for the player at startup (the default skin until it arrives). */
    //? if <26.1 {
    /*static ResourceLocation fetchedTexture() {
        return fetched().texture();
    }
    *///?} else {
    static Identifier fetchedTexture() {
        return fetched().body().texturePath();
    }
    //?}

    /** Draws the player's face (with hat layer). The default skin is shown until the real one has downloaded. */
    public static void drawOwnFace(Canvas c, float x, float y, int size) {
        // Vanilla draws at whole pixels and full strength: move the origin instead so the face sits
        // exactly in its frame, and tint it so it fades with everything around it.
        int alpha = Math.round(c.alpha() * 255);
        if (alpha < 4) return;
        int tint = alpha << 24 | 0xFFFFFF;
        c.push();
        c.translate(x, y);
        //? if <26.1 {
        /*PlayerFaceRenderer.draw(c.raw(), own(), 0, 0, size, tint);
        *///?} else {
        PlayerFaceExtractor.extractRenderState(c.raw(), own(), 0, 0, size, tint);
        //?}
        c.pop();
    }

    public static boolean ownSlim() {
        return own().model().name().equals("SLIM");
    }

    /** "Slim" or "Classic", describing the arm width of the player's model. */
    public static String ownModel() {
        return ownSlim() ? "Slim arms" : "Classic arms";
    }
}
