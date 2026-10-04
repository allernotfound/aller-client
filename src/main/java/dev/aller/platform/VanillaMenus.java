package dev.aller.platform;

import dev.aller.mixin.ButtonAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
//? if >=26.1 {
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
//?}

import java.util.ArrayList;
import java.util.List;

/**
 * Minecraft's own title and pause screens, built without being shown. Aller Client replaces both,
 * so what other mods add to them (through Fabric's screen events or their own mixins) would never
 * exist; laying one out out of sight makes it exist, and its widgets can then be pressed from
 * Aller Client's menus.
 */
public final class VanillaMenus {
    private VanillaMenus() {}

    /** Every widget on a freshly laid out title or pause screen, vanilla's own included. */
    public static List<AbstractWidget> widgets(boolean title) {
        var mc = Mc.mc();
        Screen screen = title ? new TitleScreen() : new PauseScreen(true);
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        //? if <26.1 {
        /*screen.init(mc, w, h);
        *///?} else {
        screen.init(w, h);
        //?}
        List<AbstractWidget> out = new ArrayList<>();
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget widget) out.add(widget);
        }
        return out;
    }

    /** The translation key of a widget's text, or null when it is plain text. */
    public static String key(AbstractWidget widget) {
        return widget.getMessage().getContents() instanceof TranslatableContents t ? t.getKey() : null;
    }

    /** The class of what a plain button runs when pressed, or null for any other widget. */
    public static Class<?> action(AbstractWidget widget) {
        if (!(widget instanceof Button button)) return null;
        Button.OnPress press = ((ButtonAccessor) button).aller$onPress();
        return press == null ? null : press.getClass();
    }

    /** A left click in the middle of the widget, as its own screen would deliver it. */
    public static void press(AbstractWidget widget) {
        double x = widget.getX() + widget.getWidth() / 2.0, y = widget.getY() + widget.getHeight() / 2.0;
        //? if <26.1 {
        /*widget.mouseClicked(x, y, 0);
        widget.mouseReleased(x, y, 0);
        *///?} else {
        MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0));
        widget.mouseClicked(event, false);
        widget.mouseReleased(event);
        //?}
    }

    /** For the harness: stand-in buttons on both screens, added the way another mod would add them. */
    public static void devButtons(Runnable pressed) {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof TitleScreen) && !(screen instanceof PauseScreen)) return;
            //? if <26.1 {
            /*var widgets = net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(screen);
            *///?} else {
            var widgets = net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen);
            //?}
            widgets.add(Button.builder(Component.literal("Open replays"), b -> pressed.run()).bounds(4, 4, 90, 20).build());
            widgets.add(Button.builder(Component.literal("Voice chat settings"), b -> pressed.run()).bounds(4, 28, 90, 20).build());
            widgets.add(Button.builder(Component.empty(), b -> pressed.run()).bounds(4, 52, 20, 20).build());
        });
    }
}
