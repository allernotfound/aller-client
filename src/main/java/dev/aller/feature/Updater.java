package dev.aller.feature;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aller.AllerClient;
import dev.aller.platform.Mc;
import dev.aller.platform.Os;
import dev.aller.screen.UpdateScreen;
import dev.aller.ui.Toasts;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModOrigin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Keeps Aller Client up to date from its GitHub releases. Every ten minutes the release list is
 * read; a release newer than this one with a jar for this Minecraft version is offered in
 * {@link UpdateScreen}, and nothing is downloaded until the player says so.
 *
 * <p>The running jar cannot be replaced: the game reads classes from it for as long as it runs,
 * and Windows keeps it locked. So the new jar is put beside it, and the old one is deleted by a
 * small system program once the game has gone ({@link Os#deleteAfterExit}). Should that not happen,
 * Fabric loads the newer of the two and {@link #init} removes the older one at the next start.
 */
public final class Updater {
    public static final String REPO = "allernotfound/aller-client";
    public static final String SITE = "https://github.com/" + REPO;
    private static final String API = "https://api.github.com/repos/" + REPO + "/releases?per_page=30";
    /** The only place a jar is taken from. GitHub redirects from here to its own storage. */
    private static final String DOWNLOADS = SITE + "/releases/download/";
    private static final String AGENT = "AllerClient/" + AllerClient.VERSION + " (Minecraft mod)";
    private static final long EVERY = Duration.ofMinutes(10).toNanos();
    private static final String EXTRA = "updater";
    private static final Pattern NUMBERS = Pattern.compile("\\d+(\\.\\d+)*");
    private static final Pattern FILE = Pattern.compile("[A-Za-z0-9._+-]+\\.jar");

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Aller Client updater");
        t.setDaemon(true);
        return t;
    });
    /** What the worker has to say, run on the client thread by {@link #tick}. */
    private static final Queue<Runnable> INBOX = new ConcurrentLinkedQueue<>();

    public enum State { IDLE, AVAILABLE, DOWNLOADING, READY, FAILED }

    /** One release's notes: its description on GitHub, which is Markdown. */
    public record Note(String version, String date, String body) {}

    /** A release newer than this one, with the jar built for this Minecraft version. */
    public record Update(String version, String file, String url, long size, String sha256, List<Note> notes) {}

    private record Release(String version, String date, String body, String file, String url, long size, String sha256) {}

    private static State state = State.IDLE;
    private static Update update;
    private static String problem = "";
    private static volatile float progress;
    /** Put off until the next launch. */
    private static boolean later;
    private static String told;
    /** The version last seen running, while its notes are still owed: they are shown once the release list is in. */
    private static String owed;
    private static List<Note> news;

    // Only the worker touches these.
    private static String etag;
    private static List<Release> releases = List.of();

    /** The version releases are measured against, and the jar that is replaced: the self-test stands in others. */
    private static String running = AllerClient.VERSION;
    private static Path devJar;

    private static boolean checking;
    private static long next = System.nanoTime();

    private Updater() {}

    /** Where fabric.mod.json says which Minecraft version the jar was built for: the mod's own version does not. */
    private static final String BUILT_FOR = "aller:minecraft";

    /** The Minecraft version this jar was built for, as in its file name ("26.2"). */
    public static String target() {
        try {
            var value = FabricLoader.getInstance().getModContainer(AllerClient.ID).map(m -> m.getMetadata().getCustomValue(BUILT_FOR)).orElse(null);
            return value == null ? "" : value.getAsString();
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** The jar this is running from, or null in a development run, where there is nothing to replace. */
    private static Path jar() {
        if (devJar != null) return devJar;
        try {
            var mod = FabricLoader.getInstance().getModContainer(AllerClient.ID).orElse(null);
            if (mod == null || mod.getOrigin().getKind() != ModOrigin.Kind.PATH) return null;
            for (Path p : mod.getOrigin().getPaths()) {
                if (Files.isRegularFile(p) && p.getFileName().toString().endsWith(".jar")) return p.toAbsolutePath();
            }
        } catch (RuntimeException e) {
            AllerClient.LOG.debug("Could not tell which jar this is", e);
        }
        return null;
    }

    /** The version this is, as the updater sees it. */
    public static String running() {
        return running;
    }

    public static State state() {
        return state;
    }

    public static Update update() {
        return update;
    }

    public static String problem() {
        return problem;
    }

    /** 0 to 1 while downloading. */
    public static float progress() {
        return progress;
    }

    /** Whether the popup should come up by itself on the next menu. */
    public static boolean wanted() {
        return switch (state) {
            case READY -> true;
            case AVAILABLE, FAILED -> !later;
            default -> false;
        };
    }

    /** The notes of what was installed since the last launch, once: asking for them counts as showing them. */
    public static List<Note> takeNews() {
        List<Note> list = news;
        news = null;
        if (list != null) seen(AllerClient.VERSION);
        return list;
    }

    public static boolean hasNews() {
        return news != null;
    }

    private static boolean automatic() {
        return AllerClient.options().checkUpdates.get() && jar() != null && !target().isEmpty() && System.getProperty("aller.dev.shots") == null;
    }

    /** Once at startup: remembers which version ran last, and clears away what an update left behind. */
    public static void init() {
        JsonElement saved = AllerClient.config().extra(EXTRA);
        String seen = null;
        if (saved != null && saved.isJsonObject() && saved.getAsJsonObject().has("seen")) {
            try {
                seen = saved.getAsJsonObject().get("seen").getAsString();
            } catch (RuntimeException ignored) {
                // not a string: treated as never seen
            }
        }
        if (seen != null && compare(seen, AllerClient.VERSION) < 0) owed = seen;
        else if (!AllerClient.VERSION.equals(seen)) seen(AllerClient.VERSION);

        Path own = jar();
        if (own != null) WORKER.execute(() -> tidy(own));
    }

    private static void seen(String version) {
        JsonObject o = new JsonObject();
        o.addProperty("seen", version);
        AllerClient.config().setExtra(EXTRA, o);
    }

    public static void tick() {
        for (Runnable r; (r = INBOX.poll()) != null; ) r.run();
        if (!automatic()) return;
        if (!checking && System.nanoTime() - next >= 0) check(false);
        UpdateScreen.offer();
    }

    /** Asked for by the player: looks now, and says what it found either way. */
    public static void checkNow() {
        if (state == State.DOWNLOADING || state == State.READY) {
            UpdateScreen.show();
            return;
        }
        if (!checking) check(true);
    }

    private static void check(boolean asked) {
        checking = true;
        next = System.nanoTime() + EVERY;
        String current = running, target = target();
        WORKER.execute(() -> {
            boolean reached = fetch();
            Release best = null;
            for (Release r : releases) {
                if (r.url == null || compare(r.version, current) <= 0) continue;
                if (best == null || compare(r.version, best.version) > 0) best = r;
            }
            Update found = best == null ? null
                    : new Update(best.version, best.file, best.url, best.size, best.sha256, notes(current, best.version));
            List<Release> all = releases;
            INBOX.add(() -> result(found, all, reached, asked, target));
        });
    }

    private static void result(Update found, List<Release> all, boolean reached, boolean asked, String target) {
        checking = false;
        if (owed != null && reached) {
            List<Note> since = notes(all, owed, AllerClient.VERSION);
            owed = null;
            if (since.isEmpty()) seen(AllerClient.VERSION);
            else news = since;
        }
        if (state == State.IDLE || state == State.AVAILABLE || state == State.FAILED) {
            boolean fresh = found != null && (update == null || !update.version.equals(found.version));
            if (found != null && (state != State.FAILED || fresh)) {
                update = found;
                state = State.AVAILABLE;
            } else if (found == null && state == State.AVAILABLE && reached) {
                // The release was taken down again.
                update = null;
                state = State.IDLE;
            }
            if (fresh) AllerClient.LOG.info("{} {} is available for Minecraft {}", AllerClient.NAME, found.version, target);
        }
        if (asked) {
            if (update != null) {
                later = false;
                UpdateScreen.show();
            } else if (reached) {
                Toasts.info(AllerClient.NAME + " is up to date", "Version " + AllerClient.VERSION);
            } else {
                Toasts.warn("Could not check for updates", "GitHub did not answer.");
            }
        } else if (update != null && state == State.AVAILABLE && !later && !update.version.equals(told) && Mc.mc().level != null && Mc.screen() == null) {
            // Never a popup over the game: it waits for the pause menu.
            told = update.version;
            Toasts.info(AllerClient.NAME + " " + update.version + " is available", "Open the pause menu to update");
        }
    }

    /** "Later": nothing more is said until the game is started again. */
    public static void later() {
        later = true;
    }

    // ---- the release list ------------------------------------------------------------------------

    /** Reads the release list unless it is unchanged since last time. @return false if GitHub could not be reached */
    private static boolean fetch() {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(API)).timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/vnd.github+json").header("User-Agent", AGENT);
            // An unchanged list costs nothing of GitHub's hourly allowance.
            if (etag != null) request.header("If-None-Match", etag);
            HttpResponse<String> response = HTTP.send(request.GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 304) return true;
            if (response.statusCode() != 200) {
                AllerClient.LOG.debug("Update check: HTTP {}", response.statusCode());
                return false;
            }
            releases = parse(JsonParser.parseString(response.body()), target());
            etag = response.headers().firstValue("ETag").orElse(null);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            AllerClient.LOG.debug("Update check failed", e);
            return false;
        }
    }

    private static List<Release> parse(JsonElement root, String target) {
        List<Release> out = new ArrayList<>();
        if (!root.isJsonArray()) return out;
        for (JsonElement e : root.getAsJsonArray()) {
            try {
                JsonObject o = e.getAsJsonObject();
                if (flag(o, "draft") || flag(o, "prerelease")) continue;
                String version = str(o, "tag_name").replaceFirst("^[vV]", "");
                if (!NUMBERS.matcher(version).lookingAt()) continue;
                String file = null, url = null, sha = "";
                long size = 0;
                JsonArray assets = o.has("assets") && o.get("assets").isJsonArray() ? o.getAsJsonArray("assets") : new JsonArray();
                for (JsonElement a : assets) {
                    JsonObject asset = a.getAsJsonObject();
                    String name = str(asset, "name"), from = str(asset, "browser_download_url");
                    // "aller-1.0.2+26.2.jar": the part after the plus is the Minecraft version it was built for.
                    if (!name.startsWith(AllerClient.ID + "-") || !name.endsWith("+" + target + ".jar")) continue;
                    if (!FILE.matcher(name).matches() || !from.startsWith(DOWNLOADS)) continue;
                    file = name;
                    url = from;
                    size = asset.has("size") && asset.get("size").isJsonPrimitive() ? asset.get("size").getAsLong() : 0;
                    String digest = str(asset, "digest");
                    sha = digest.startsWith("sha256:") ? digest.substring(7) : "";
                    break;
                }
                String date = str(o, "published_at");
                out.add(new Release(version, date.length() >= 10 ? date.substring(0, 10) : "", str(o, "body"), file, url, size, sha));
            } catch (RuntimeException ex) {
                // one release that cannot be read does not hide the others
            }
        }
        return out;
    }

    private static boolean flag(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsBoolean();
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    private static List<Note> notes(String after, String upTo) {
        return notes(releases, after, upTo);
    }

    /** Every release after one version and up to another, newest first: an update may skip a few. */
    private static List<Note> notes(List<Release> all, String after, String upTo) {
        List<Note> out = new ArrayList<>();
        for (Release r : all) {
            if (compare(r.version, after) > 0 && compare(r.version, upTo) <= 0) out.add(new Note(r.version, r.date, r.body));
        }
        out.sort((a, b) -> compare(b.version, a.version));
        return out;
    }

    /** Compares the dotted numbers two versions start with ("1.0.10" is after "1.0.9"). */
    public static int compare(String a, String b) {
        int[] x = numbers(a), y = numbers(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? x[i] : 0, q = i < y.length ? y[i] : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    private static int[] numbers(String version) {
        Matcher m = NUMBERS.matcher(version == null ? "" : version);
        if (!m.lookingAt()) return new int[0];
        String[] parts = m.group().split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                out[i] = Integer.MAX_VALUE;
            }
        }
        return out;
    }

    // ---- downloading -----------------------------------------------------------------------------

    /** Fetches the new jar, checks it, and puts it beside the running one. Only ever called from the popup's button. */
    public static void download() {
        Update u = update;
        if (u == null || state == State.DOWNLOADING || state == State.READY) return;
        Path own = jar();
        if (own == null) {
            fail("This copy is not running from a jar in the mods folder, so it cannot update itself.");
            return;
        }
        state = State.DOWNLOADING;
        progress = 0;
        String wanted = u.version + "+" + target();
        if (target().isEmpty()) {
            fail("This copy does not say which Minecraft version it was built for.");
            return;
        }
        WORKER.execute(() -> {
            Path dir = own.getParent(), target = dir.resolve(u.file), part = dir.resolve(u.file + ".part");
            String failure = null;
            try {
                if (target.equals(own)) throw new IOException("the update has the running jar's name");
                HttpRequest request = HttpRequest.newBuilder(URI.create(u.url)).timeout(Duration.ofMinutes(2)).header("User-Agent", AGENT).GET().build();
                HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long done = 0;
                try (InputStream in = response.body()) {
                    if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
                    long total = u.size > 0 ? u.size : response.headers().firstValueAsLong("Content-Length").orElse(-1);
                    try (OutputStream out = Files.newOutputStream(part)) {
                        byte[] buffer = new byte[1 << 16];
                        for (int n; (n = in.read(buffer)) > 0; ) {
                            out.write(buffer, 0, n);
                            digest.update(buffer, 0, n);
                            done += n;
                            if (total > 0) progress = Math.min(1f, done / (float) total);
                        }
                    }
                }
                if (u.size > 0 && done != u.size || !u.sha256.isEmpty() && !u.sha256.equalsIgnoreCase(HexFormat.of().formatHex(digest.digest()))) {
                    failure = "The file did not match what GitHub lists, so it was thrown away.";
                } else if (!wanted.equals(versionOf(part))) {
                    failure = "The file was not " + AllerClient.NAME + " " + u.version + " for Minecraft " + target() + ", so it was thrown away.";
                } else {
                    Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failure = "The download did not finish.";
            } catch (Exception e) {
                AllerClient.LOG.warn("Download of {} failed", u.file, e);
                failure = e instanceof java.nio.file.FileSystemException ? "The mods folder could not be written to." : "The download did not finish.";
            }
            if (failure != null) {
                try {
                    Files.deleteIfExists(part);
                } catch (IOException ignored) {
                    // left behind; the game does not load a .part file, and the next start removes it
                }
            }
            String said = failure;
            INBOX.add(() -> {
                if (said != null) {
                    fail(said);
                } else {
                    state = State.READY;
                    problem = "";
                    AllerClient.LOG.info("{} {} is downloaded as {}; it takes over at the next start", AllerClient.NAME, u.version, target.getFileName());
                }
            });
        });
    }

    private static void fail(String message) {
        state = State.FAILED;
        problem = message;
        later = false;
    }

    /** The version a jar says it is in its fabric.mod.json and the Minecraft version it is for ("1.0.2+26.2"), if it is this mod at all. */
    private static String versionOf(Path file) {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            ZipEntry entry = zip.getEntry("fabric.mod.json");
            if (entry == null) return null;
            try (InputStreamReader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
                if (!AllerClient.ID.equals(str(o, "id"))) return null;
                JsonObject custom = o.has("custom") && o.get("custom").isJsonObject() ? o.getAsJsonObject("custom") : new JsonObject();
                String mc = str(custom, BUILT_FOR);
                if (mc.isEmpty()) {
                    // Releases from before the updater only say it in the file's name.
                    String name = file.getFileName().toString().replaceFirst("[.]jar([.]part)?$", "");
                    mc = name.substring(name.lastIndexOf('+') + 1);
                }
                return str(o, "version") + "+" + mc;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** As the game closes: with an update downloaded, the jar that was running is deleted once the game has gone. */
    public static void stopping() {
        Path own = jar();
        if (state == State.READY && own != null) Os.deleteAfterExit(own);
    }

    /**
     * Removes older copies of this mod for the same Minecraft version from the mods folder, and
     * downloads that never finished. An older copy is only there if it could not be deleted as the
     * game closed after an update.
     */
    private static void tidy(Path own) {
        String target = target();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(own.getParent())) {
            for (Path p : files) {
                String name = p.getFileName().toString();
                if (p.equals(own) || !Files.isRegularFile(p)) continue;
                if (name.startsWith(AllerClient.ID + "-") && name.endsWith(".jar.part")) {
                    Files.deleteIfExists(p);
                } else if (name.endsWith(".jar")) {
                    String version = versionOf(p);
                    if (version == null || !version.endsWith("+" + target) || compare(version, AllerClient.VERSION) >= 0) continue;
                    Files.deleteIfExists(p);
                    AllerClient.LOG.info("Removed {}, which this version replaces", name);
                }
            }
        } catch (IOException | RuntimeException e) {
            AllerClient.LOG.warn("Could not clear away the previous version", e);
        }
    }

    /** Stands in for a release in the self-test, which never asks GitHub. */
    public static void dev(String action) {
        String body = "A made-up release, for the self-test.\n\n### Added\n- An updater that asks before it downloads anything\n"
                + "- Release notes, **as written on GitHub**, with [links](" + SITE + ")\n\n### Fixed\n- `zoom` no longer sticks\n";
        List<Note> notes = List.of(new Note("9.9.9", "2026-10-03", body), new Note("9.9.8", "2026-09-28", "- Smaller things"));
        if (action.startsWith("real:")) {
            // The real thing against GitHub, as a version older than any release, with a stand-in for the running jar.
            try {
                running = "0.0.1";
                devJar = Path.of(action.substring(5)).toAbsolutePath().resolve("aller-0.0.1+" + target() + ".jar");
                Files.createDirectories(devJar.getParent());
                Files.writeString(devJar, "stands in for the running jar");
                state = State.IDLE;
                update = null;
                checkNow();
            } catch (IOException e) {
                AllerClient.LOG.error("UPDATE could not set up", e);
            }
            return;
        }
        switch (action) {
            case "available" -> {
                update = new Update("9.9.9", "aller-9.9.9+" + target() + ".jar", DOWNLOADS + "v9.9.9/aller-9.9.9+" + target() + ".jar", 4_321_000, "", notes);
                state = State.AVAILABLE;
                later = false;
            }
            case "downloading" -> {
                state = State.DOWNLOADING;
                progress = 0.42f;
            }
            case "ready" -> state = State.READY;
            case "failed" -> fail("The download did not finish.");
            case "news" -> {
                state = State.IDLE;
                update = null;
                news = notes;
            }
            default -> {
                state = State.IDLE;
                update = null;
                news = null;
            }
        }
    }
}
