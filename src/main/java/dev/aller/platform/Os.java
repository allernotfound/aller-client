package dev.aller.platform;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Things only the operating system can do with a file: put a picture on the clipboard, move
 * something to the recycle bin, point it out in the file manager. Minecraft runs Java headless, so
 * AWT's clipboard and desktop are not there; each of these starts a small system program instead.
 * They block for as long as that takes: call them from a worker thread.
 */
public final class Os {
    private static final String NAME = System.getProperty("os.name", "").toLowerCase();
    private static final boolean WINDOWS = NAME.contains("win"), MAC = NAME.contains("mac");
    /** The file is handed over in the environment, so no path ever has to be quoted into a script. */
    private static final String FILE = "ALLER_FILE";

    private Os() {}

    /** Copies a PNG to the clipboard as a picture, ready to paste into a chat program. */
    public static boolean copyImage(Path file) {
        if (WINDOWS) {
            return powershell(file, "Add-Type -AssemblyName System.Windows.Forms,System.Drawing; $i=[System.Drawing.Image]::FromFile($env:" + FILE
                    + "); [System.Windows.Forms.Clipboard]::SetImage($i); $i.Dispose()");
        }
        if (MAC) return run(file, "osascript", "-e", "set the clipboard to (read (POSIX file (system attribute \"" + FILE + "\")) as «class PNGf»)");
        String path = file.toAbsolutePath().toString();
        return run(file, "sh", "-c", "wl-copy --type image/png < \"$" + FILE + "\"") || run(file, "xclip", "-selection", "clipboard", "-t", "image/png", "-i", path);
    }

    /** Moves a file to the recycle bin, from where it can still be restored to where it was. */
    public static boolean recycle(Path file) {
        if (WINDOWS) {
            return powershell(file, "Add-Type -AssemblyName Microsoft.VisualBasic; [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile($env:" + FILE
                    + ",'OnlyErrorDialogs','SendToRecycleBin')");
        }
        if (MAC) return run(file, "osascript", "-e", "tell application \"Finder\" to delete (POSIX file (system attribute \"" + FILE + "\"))");
        return run(file, "gio", "trash", file.toAbsolutePath().toString());
    }

    /** Opens the file manager with the file picked out, or just its folder where that cannot be done. */
    public static void reveal(Path file) {
        String path = file.toAbsolutePath().toString();
        try {
            // Explorer answers with a failure code even when it worked, so nothing is read from these.
            if (WINDOWS) new ProcessBuilder("explorer.exe", "/select," + path).start();
            else if (MAC) new ProcessBuilder("open", "-R", path).start();
            else Mc.openFolder(file.toAbsolutePath().getParent());
        } catch (IOException e) {
            Mc.openFolder(file.toAbsolutePath().getParent());
        }
    }

    /**
     * Deletes a file once this game has gone, for one the game holds open for as long as it runs (its
     * own jar). Starts a program that outlives the game and waits for it; nothing is waited for here.
     */
    public static void deleteAfterExit(Path file) {
        long pid = ProcessHandle.current().pid();
        ProcessBuilder builder = WINDOWS
                ? new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command",
                        "Wait-Process -Id " + pid + " -ErrorAction SilentlyContinue; Start-Sleep -Milliseconds 500; Remove-Item -LiteralPath $env:" + FILE + " -Force")
                : new ProcessBuilder("sh", "-c", "while kill -0 " + pid + " 2>/dev/null; do sleep 1; done; rm -f \"$" + FILE + "\"");
        builder.environment().put(FILE, file.toAbsolutePath().toString());
        builder.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
        try {
            builder.start().getOutputStream().close();
        } catch (IOException e) {
            dev.aller.AllerClient.LOG.warn("Could not arrange for {} to be deleted", file.getFileName(), e);
        }
    }

    private static boolean powershell(Path file, String script) {
        return run(file, "powershell.exe", "-NoProfile", "-NonInteractive", "-STA", "-Command", "$ErrorActionPreference='Stop'; " + script);
    }

    private static boolean run(Path file, String... command) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
            builder.environment().put(FILE, file.toAbsolutePath().toString());
            Process process = builder.start();
            process.getOutputStream().close();
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
