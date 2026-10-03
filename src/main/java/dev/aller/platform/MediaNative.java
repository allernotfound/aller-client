package dev.aller.platform;

/**
 * What the system is playing, from the same native library as the browser ({@link WebNative#load}
 * must have succeeded). Both calls wait on Windows and must not be made on the render thread.
 */
public final class MediaNative {
    public static final int PLAY_PAUSE = 0, NEXT = 1, PREVIOUS = 2;

    private MediaNative() {}

    /**
     * Seven lines: title, artist, playing (1 or 0), position, length, when that position was
     * true (all milliseconds, the last since 1970) and the app's id. Empty while nothing plays.
     */
    public static native String now();

    public static native void command(int what);
}
