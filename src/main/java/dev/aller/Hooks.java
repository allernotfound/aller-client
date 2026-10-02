package dev.aller;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.aller.feature.View;
import dev.aller.platform.Game;
import dev.aller.platform.Mc;
import net.minecraft.world.entity.Entity;
import dev.aller.module.Modules;
import dev.aller.platform.Canvas;
import dev.aller.screen.MenuSkin;
import net.minecraft.client.gui.screens.Screen;
import dev.aller.feature.Chat;
import dev.aller.feature.Pocket;
import net.minecraft.network.chat.Component;

/**
 * The single surface mixins call into. Keeping every injected call here means the mixin classes
 * stay trivial (and therefore easy to port), while the behaviour lives in ordinary module code.
 */
public final class Hooks {
    private Hooks() {}

    /** World field of view in degrees, after vanilla's own modifiers. */
    public static float fov(float original) {
        float fov = Modules.ZOOM.apply(original);
        View.fov = fov;
        return fov;
    }

    public static float gamma(float original) {
        if (Pocket.bright()) return Math.max(original, 15f);
        return Modules.FULLBRIGHT.enabled() ? Modules.FULLBRIGHT.gamma(original) : original;
    }

    public static boolean cancelHurtCam() {
        return Modules.NO_HURT_CAM.enabled();
    }

    /** How far to push the first-person fire overlay down, in overlay units. */
    public static float fireOffset() {
        return Modules.LOW_FIRE.enabled() ? Modules.LOW_FIRE.offset.get() : 0f;
    }

    public static long dayTime(long original) {
        return Modules.TIME_CHANGER.enabled() ? Modules.TIME_CHANGER.time(original) : original;
    }

    public static float rain(float original) {
        return Modules.WEATHER_CHANGER.enabled() ? Modules.WEATHER_CHANGER.rain() : original;
    }

    public static float thunder(float original) {
        return Modules.WEATHER_CHANGER.enabled() ? Modules.WEATHER_CHANGER.thunder() : original;
    }

    public static boolean cancelCrosshair() {
        return Modules.CROSSHAIR.enabled();
    }

    public static boolean hideScoreboard() {
        return Modules.SCOREBOARD.enabled();
    }

    public static boolean replaceTabList() {
        return Modules.PLAYER_LIST.enabled();
    }

    /** A message on its way into the chat window, as the chat mods want it shown; null to leave it out. */
    public static Chat.Incoming chat(Component message) {
        return Chat.incoming(message);
    }

    /** How many messages the chat window and the sent-message history keep (100 in vanilla). */
    public static int chatLimit(int original) {
        return Modules.CHAT_HISTORY.enabled() ? Modules.CHAT_HISTORY.limit(original) : original;
    }

    public static void chatSent(String text, boolean command) {
        Chat.sent(text, command);
    }

    /** Opacity of the chat window's background, 0 to 1. */
    public static float chatBackground(float original) {
        return Modules.CHAT_LOOK.enabled() ? Modules.CHAT_LOOK.background.get() / 100f : original;
    }

    /** Brackets the chat window's drawing. */
    public static void chatBegin(Canvas c) {
        Chat.begin(c);
    }

    public static void chatEnd(Canvas c) {
        Chat.end(c);
    }

    /** @return true if the mouse movement was consumed by freelook and must not turn the player */
    public static boolean freelookTurn(double dx, double dy) {
        return Modules.FREELOOK.turn(dx, dy);
    }

    /** Scales raw mouse movement before it turns the player (lower sensitivity while zoomed). */
    public static double turnScale() {
        return Modules.ZOOM.mouseScale();
    }

    public static float cameraYaw(float original) {
        return Modules.FREELOOK.active() ? Modules.FREELOOK.yaw() : original;
    }

    public static float cameraPitch(float original) {
        return Modules.FREELOOK.active() ? Modules.FREELOOK.pitch() : original;
    }

    /** @return true if the scroll was consumed (zoom adjustment) and must not change the hotbar slot */
    public static boolean scroll(double amount) {
        return Modules.ZOOM.scroll(amount);
    }

    /** Applied to the pose of the first-person hand and held item. */
    public static void viewmodel(PoseStack pose, boolean mainHand) {
        if (Modules.VIEWMODEL.enabled()) Modules.VIEWMODEL.apply(pose, mainHand);
    }

    public static boolean showOwnName(Entity entity) {
        return Modules.OWN_NAMETAG.enabled() && entity == Game.player() && !Mc.hudHidden()
                && !Mc.mc().options.getCameraType().isFirstPerson();
    }

    public static int blockOutlineColor(int original) {
        // Leave the black secondary pass of the high-contrast outline alone.
        if (!Modules.BLOCK_OUTLINE.enabled() || original == 0xFF000000) return original;
        return Modules.BLOCK_OUTLINE.tint();
    }

    /** A link Minecraft is about to hand to the system browser. @return true if Aller's browser took it */
    public static boolean openLink(java.net.URI uri) {
        return dev.aller.feature.Browser.chatLink(uri);
    }

    // The pocket dimension. Its mixins are optional, so none of these may be assumed to run.

    /** Puts the world a packet belongs to in place before it is handled; {@link #pocketPacketDone} follows it. */
    public static void pocketPacket(Object listener, Object packet) {
        Pocket.packet(listener, packet);
    }

    public static void pocketPacketDone() {
        Pocket.packetDone();
    }

    /** True while the world that is not on screen is in Minecraft's fields. */
    public static boolean pocketAway() {
        return dev.aller.platform.Worlds.away();
    }

    /** @return true to drop a screen opened by the world that is not on screen */
    public static boolean pocketScreen(Screen screen) {
        return Pocket.screen(screen);
    }

    /** @return true if the connection that dropped was the pocket's own */
    public static boolean pocketLost(Object listener) {
        return Pocket.lost(listener);
    }

    /** @return true if leaving the world was turned into leaving the pocket */
    public static boolean pocketQuit() {
        return Pocket.quit();
    }

    public static void pocketDisconnect() {
        Pocket.disconnecting();
    }

    /** @return true if chat typed in the pocket was sent on to the server instead */
    public static boolean pocketChat(Object listener, String text, boolean command) {
        return Pocket.chat(listener, text, command);
    }

    // Restyled vanilla menus. The boolean ones return true when Aller drew the piece and vanilla must not.

    /** Brackets every vanilla screen's drawing, for the ease-in after leaving one of Aller's menus. */
    public static void screenBegin(Canvas c, Screen screen) {
        dev.aller.screen.Entrance.begin(c, screen);
    }

    public static void screenEnd(Canvas c) {
        dev.aller.screen.Entrance.end(c);
    }

    /** The menu blur's radius, eased with the Aller screen that asked for it so it does not cut in or out. */
    public static int blurRadius(int original) {
        dev.aller.ui.AllerScreen screen = dev.aller.platform.Mc.current();
        if (screen == null || original < 1) return original;
        return Math.max(1, Math.round(original * screen.blurAmount()));
    }

    public static void menuBegin(Screen screen) {
        MenuSkin.begin(screen);
    }

    public static void menuEnd() {
        MenuSkin.end();
    }

    public static boolean menuBackdrop(Canvas c) {
        return MenuSkin.backdrop(c);
    }

    public static boolean menuSkipBlur() {
        return MenuSkin.skipBlur();
    }

    public static boolean menuSprite(Canvas c, String namespace, String path, int x, int y, int w, int h, int color) {
        return MenuSkin.sprite(c, namespace, path, x, y, w, h, color);
    }

    public static boolean menuTexture(Canvas c, String namespace, String path, int x0, int y0, int x1, int y1) {
        return MenuSkin.texture(c, namespace, path, x0, y0, x1, y1);
    }

    public static boolean menuFill(Canvas c, int x0, int y0, int x1, int y1, int color) {
        return MenuSkin.fill(c, x0, y0, x1, y1, color);
    }

    public static boolean menuBorder(Canvas c, int x0, int y0, int x1, int y1, int color) {
        return MenuSkin.border(c, x0, y0, x1, y1, color);
    }

    public static void menuSelection(boolean on) {
        MenuSkin.selection(on);
    }

    public static boolean menuButton(Canvas c, int x, int y, int w, int h, boolean hovered, boolean disabled) {
        if (!MenuSkin.drawing()) return false;
        MenuSkin.button(c, x, y, w, h, hovered, !disabled);
        return true;
    }

    public static boolean menuPanel(Canvas c, int x, int y, int w, int h) {
        return MenuSkin.panel(c, x, y, w, h);
    }
}
