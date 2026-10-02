package dev.aller.platform;

import dev.aller.AllerClient;
import org.lwjgl.glfw.GLFWNativeWin32;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

/**
 * The system webview, through the small native library built from {@code native/} (wry over
 * WebView2). A webview is a real window owned by the game window and laid over it: it is placed in
 * the game window's pixels and always sits above everything the game draws. Windows only for now.
 *
 * <p>Every call must come from the render thread. Nothing calls back into Java: what happened
 * since the last frame is collected with {@link #poll}.
 */
public final class WebNative {
    private static final String RESOURCE = "/natives/windows-x64/aller_webview.dll";
    public static final int NONE = 0, PENDING = 1, READY = 2, FAILED = -1;
    /** What {@link #action} can do. */
    public static final int BACK = 0, FORWARD = 1, RELOAD = 2, STOP = 3, FOCUS = 4, FOCUS_GAME = 5, SUSPEND = 6, RESUME = 7,
            CAPTURE = 8, MUTE = 9, UNMUTE = 10, CLEAR_DATA = 11;
    /** Kinds of {@link #blob}. */
    public static final int STILL = 0, FAVICON = 1;

    private static boolean tried, loaded;
    private static String problem;

    private WebNative() {}

    public static boolean supported() {
        String os = System.getProperty("os.name", "").toLowerCase(), arch = System.getProperty("os.arch", "");
        return os.contains("win") && (arch.equals("amd64") || arch.equals("x86_64"));
    }

    /** Why the browser cannot run here, or null if it can. */
    public static String problem() {
        if (!supported()) return "The browser needs 64-bit Windows for now.";
        return problem;
    }

    /** Unpacks and loads the library the first time; false if this build or system has none. */
    public static boolean load(Path dir) {
        if (tried) return loaded;
        tried = true;
        if (!supported()) return false;
        try (InputStream in = WebNative.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                problem = "This build of Aller Client was made without the browser's native library.";
                return false;
            }
            byte[] bytes = in.readAllBytes();
            CRC32 sum = new CRC32();
            sum.update(bytes);
            // Named by content: a file from an older build may still be loaded by another instance.
            Path file = dir.resolve("native").resolve("aller_webview-" + Long.toHexString(sum.getValue()) + ".dll");
            if (!Files.exists(file) || Files.size(file) != bytes.length) {
                Files.createDirectories(file.getParent());
                Files.write(file, bytes);
            }
            System.load(file.toAbsolutePath().toString());
            loaded = true;
        } catch (Throwable e) {
            problem = "The browser's native library could not be loaded.";
            AllerClient.LOG.warn("Could not load the webview library", e);
        }
        return loaded;
    }

    public static boolean loaded() {
        return loaded;
    }

    /** The game window, as the webviews' parent. */
    public static long window() {
        return GLFWNativeWin32.glfwGetWin32Window(Mc.window());
    }

    /** Starts the browser process for a data folder; {@link #state} says when it is ready. */
    public static native void start(String dataDir, String browserArguments);

    public static native int state();

    /** Creates a hidden webview loading {@code url}. Blocks for a moment. @return its id, or -1 */
    public static native int create(long parent, String url, String script, int width, int height, boolean isPrivate);

    public static native void destroy(int id);

    /** Position and size inside the game window, in window pixels. */
    public static native void place(int id, int x, int y, int width, int height);

    public static native void show(int id, boolean visible);

    public static native void navigate(int id, String url);

    public static native void eval(int id, String script);

    public static native void action(int id, int what);

    /** Key combinations kept from the page: a Windows virtual key with GLFW's modifier bits above bit 16. */
    public static native void keys(int[] combinations);

    /** Events since the last call, each {@code id \t kind \t payload}; null if there are none. */
    public static native String[] poll();

    /** A PNG announced by a {@code capture} or {@code favicon} event; null if it is gone. */
    public static native byte[] blob(int id, int kind);

    /** Whether a top-level window is the one in use. A page inside the game window having the keyboard still counts. */
    public static native boolean foreground(long window);

    public static native void shutdown();
}
