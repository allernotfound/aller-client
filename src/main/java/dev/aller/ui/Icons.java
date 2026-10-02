package dev.aller.ui;

import dev.aller.platform.Canvas;
import dev.aller.platform.Tex;
import dev.aller.ui.font.IconAtlas;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * The client's pictograms: Lucide icons (lucide.dev, ISC licence), bundled as SVG files in
 * {@code assets/aller/icons} and rasterised into an atlas at startup. To add one, copy its SVG
 * there and name it here.
 */
public enum Icons {
    CLOSE("x"), ROWS("list"), GRID("layout-grid"), REALMS("globe"), MODS("blocks"), QUIT("log-out"), ADVANCEMENTS("trophy"),
    LAN("network"), REPORT("flag"), FEEDBACK("message-square"), BUG("bug"), EDIT("pencil"), PLUS("plus"), SETTINGS("settings"),
    WARDROBE("shirt"), FOLDER("folder-open"), UPLOAD("upload"), BACK("arrow-left"), FORWARD("arrow-right"), RELOAD("rotate-cw"),
    STAR("star"), CLOCK("history"), SEARCH("search"), SOUND("volume-2"), MUTED("volume-x"), PRIVATE("hat-glasses"),
    CHECK("check"), CHEVRON_RIGHT("chevron-right"), CHEVRON_LEFT("chevron-left"), PIN("pin"),
    HOST("radio-tower"), INVITE("mail"), SOCIAL("users"), COSMETICS("sparkles"), PICTURES("images"), SLIDERS("sliders-horizontal"),
    ACCOUNT("circle-user-round"), CART("shopping-cart"), DOWNLOAD("download"), EXTERNAL("external-link"), IMAGE("image"),
    PACKAGE("package"), HEART("heart"), CALENDAR("calendar"), FILTER("funnel"), SORT("arrow-down-wide-narrow"), TAG("tag"),
    ARCHIVE("file-archive"), ALERT("circle-alert"), TRASH("trash-2"), LICENCE("scale");

    private final String file;

    Icons(String file) {
        this.file = file;
    }

    private static CompletableFuture<IconAtlas> pending;
    private static IconAtlas atlas;
    private static Tex smooth, pixel;

    /** Rasterises the atlas in the background, as the fonts are. */
    public static void preload() {
        pending = CompletableFuture.supplyAsync(Icons::rasterise);
    }

    private static IconAtlas rasterise() {
        return new IconAtlas(Arrays.stream(values()).map(i -> i.file).toList());
    }

    public static IconAtlas atlas() {
        if (atlas == null) {
            atlas = pending != null ? pending.join() : rasterise();
            pending = null;
        }
        return atlas;
    }

    /** Render thread only. @param pixelArt the hard 24 by 24 pictures, sampled without smoothing */
    public static Tex texture(boolean pixelArt) {
        if (pixelArt) {
            if (pixel == null) pixel = new Tex("aller-icons-pixel", IconAtlas.PIXEL_SIZE, IconAtlas.PIXEL_SIZE, atlas().pixelArt, false);
            return pixel;
        }
        if (smooth == null) smooth = new Tex("aller-icons", IconAtlas.SIZE, IconAtlas.SIZE, atlas().pixels, true);
        return smooth;
    }

    /** Draws the icon centred on (cx, cy) inside a square of the given size. */
    public void draw(Canvas c, float cx, float cy, float size, int color) {
        c.icon(this, cx, cy, size, color);
    }
}
