package dev.aller.feature.store;

import dev.aller.AllerClient;
import dev.aller.feature.store.Modrinth.Category;
import dev.aller.feature.store.Modrinth.FileRef;
import dev.aller.feature.store.Modrinth.Project;
import dev.aller.feature.store.Modrinth.Version;
import dev.aller.platform.Mc;
import dev.aller.platform.Nav;
import dev.aller.ui.Toasts;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * What the store screen works on besides search results: the pages it has opened, the files
 * already in the folder (matched to Modrinth by hash, with any newer version), and downloads in
 * progress. Network and disk work runs on worker threads; everything handed out is only touched
 * on the client thread.
 *
 * <p>Files only ever come from Modrinth's own file host, are checked against the hash Modrinth
 * gave, and are written into the kind's folder under the name Modrinth lists.
 */
public final class Store {
    public enum State { IDLE, LOADING, READY, FAILED }

    /** Something that blocks and may fail, run off the client thread. */
    public interface Work<T> {
        T run() throws Exception;
    }

    /** A project's page as far as it has been fetched. */
    public static final class Details {
        public final Project project;
        public List<Version> versions = List.of();
        public State state = State.LOADING;
        public String error = "";

        Details(Project project) {
            this.project = project;
        }
    }

    /** A file in the folder. */
    public static final class Installed {
        public final Path file;
        public final String filename;
        public final long size;
        String sha1 = "";
        /** What Modrinth says this file is; null for a file it does not know. */
        public Version version;
        public Project project;
        /** A newer version for this game version, or null. */
        public Version update;

        Installed(Path file, long size) {
            this.file = file;
            this.filename = file.getFileName().toString();
            this.size = size;
        }

        public String title() {
            return project != null ? project.title : filename;
        }
    }

    public static final class Download {
        public final String title;
        /** 0 to 1; negative while the size is not known. */
        public volatile float progress;

        Download(String title) {
            this.title = title;
        }
    }

    private static final String FILE_HOST = "cdn.modrinth.com";

    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "Aller Client store");
        t.setDaemon(true);
        return t;
    });

    private static final Map<Kind, List<Category>> categories = new EnumMap<>(Kind.class);
    private static final Set<Kind> categoriesAsked = new HashSet<>();
    private static final Map<String, Details> details = new HashMap<>();
    private static final Map<Kind, List<Installed>> installed = new EnumMap<>(Kind.class);
    private static final Map<Kind, State> scans = new EnumMap<>(Kind.class);
    private static final Map<String, String> hashes = new HashMap<>();
    private static final Map<String, Download> downloads = new HashMap<>();
    /** File names downloaded lately, newest first: these are pinned and marked where the game lists them. */
    private static final LinkedHashSet<String> fresh = new LinkedHashSet<>();
    /** Old file name to the one that replaced it, for whoever keeps a list of what is switched on. */
    private static final Map<String, String> replaced = new HashMap<>();
    /** Files that could not be deleted because the game had them open. */
    private static final Set<Path> doomed = new HashSet<>();
    private static int changes;

    private Store() {}

    public static <T> void async(Work<T> work, Consumer<T> done, Consumer<String> failed) {
        POOL.execute(() -> {
            try {
                T result = work.run();
                Mc.mc().execute(() -> done.accept(result));
            } catch (Exception e) {
                AllerClient.LOG.debug("Store request failed", e);
                String message = e instanceof IOException && e.getMessage() != null && e.getMessage().startsWith("Modrinth")
                        ? e.getMessage() : "Could not reach Modrinth";
                Mc.mc().execute(() -> failed.accept(message));
            }
        });
    }

    public static String gameVersion() {
        return Nav.minecraftVersion();
    }

    // ---- filters ---------------------------------------------------------------------------------

    /** Modrinth's filters for this kind; empty until they have arrived. */
    public static List<Category> categories(Kind kind) {
        if (categoriesAsked.add(kind)) {
            async(() -> Modrinth.categories(kind), list -> categories.put(kind, list), error -> categoriesAsked.remove(kind));
        }
        return categories.getOrDefault(kind, List.of());
    }

    // ---- pages -----------------------------------------------------------------------------------

    public static Details details(Kind kind, Project from) {
        Details d = details.get(from.id);
        if (d != null && d.state != State.FAILED) return d;
        Details fetched = new Details(from);
        details.put(from.id, fetched);
        record Fetched(Project page, List<Version> versions) {}
        Project page = new Project();
        page.id = from.id;
        page.author = from.author;
        page.banner = from.banner;
        async(() -> {
            Modrinth.project(page);
            return new Fetched(page, Modrinth.versions(kind, page.id));
        }, result -> {
            from.take(result.page);
            fetched.versions = result.versions;
            fetched.state = State.READY;
        }, error -> {
            fetched.state = State.FAILED;
            fetched.error = error;
        });
        return fetched;
    }

    /** The version a plain "Download" fetches: the newest one made for this game version, or null. */
    public static Version suggested(Details d) {
        String game = gameVersion();
        for (Version v : d.versions) if (v.gameVersions.contains(game)) return v;
        return null;
    }

    // ---- what is in the folder -------------------------------------------------------------------

    public static List<Installed> installed(Kind kind) {
        return installed.getOrDefault(kind, List.of());
    }

    public static State scanState(Kind kind) {
        return scans.getOrDefault(kind, State.IDLE);
    }

    /** The file in the folder that belongs to this project, or null. */
    public static Installed installed(Kind kind, String projectId) {
        for (Installed i : installed(kind)) if (i.project != null && i.project.id.equals(projectId)) return i;
        return null;
    }

    public static int updates(Kind kind) {
        int n = 0;
        for (Installed i : installed(kind)) if (i.update != null) n++;
        return n;
    }

    /** Goes up whenever the folder's contents change through the store. */
    public static int changes() {
        return changes;
    }

    /** Reads the folder again and asks Modrinth which of the files it knows, and whether any has a newer version. */
    public static void scan(Kind kind) {
        if (scans.get(kind) == State.LOADING) return;
        scans.put(kind, State.LOADING);
        Path dir = kind.dir();
        String game = gameVersion();
        Set<Path> skip = new HashSet<>(doomed);
        POOL.execute(() -> {
            List<Installed> found = new ArrayList<>();
            boolean reached = true;
            try {
                if (Files.isDirectory(dir)) {
                    try (Stream<Path> files = Files.list(dir)) {
                        for (Path file : (Iterable<Path>) files::iterator) {
                            if (!Files.isRegularFile(file) || !kind.holds(file.getFileName().toString())) continue;
                            if (skip.contains(file)) {
                                // Left over from an update while the game had it open; it may have let go by now.
                                try {
                                    Files.deleteIfExists(file);
                                } catch (IOException stillOpen) {
                                    // it goes when the game closes
                                }
                                continue;
                            }
                            Installed i = new Installed(file, Files.size(file));
                            i.sha1 = sha1(file, i.size);
                            found.add(i);
                        }
                    }
                }
                List<String> all = found.stream().map(i -> i.sha1).filter(h -> !h.isEmpty()).toList();
                if (!all.isEmpty()) {
                    Map<String, Version> known = Modrinth.identify(all);
                    Map<String, Version> newer = known.isEmpty() ? Map.of() : Modrinth.updates(kind, known.keySet(), game);
                    Map<String, Project> projects = Modrinth.projects(known.values().stream().map(v -> v.projectId).distinct().toList());
                    for (Installed i : found) {
                        i.version = known.get(i.sha1);
                        if (i.version == null) continue;
                        i.project = projects.get(i.version.projectId);
                        Version latest = newer.get(i.sha1);
                        if (latest != null && latest.file != null && !latest.id.equals(i.version.id) && latest.published != null
                                && i.version.published != null && latest.published.isAfter(i.version.published)) i.update = latest;
                    }
                }
            } catch (IOException | RuntimeException e) {
                AllerClient.LOG.debug("Could not check {} against Modrinth", dir, e);
                reached = false;
            }
            found.sort(Comparator.comparing((Installed i) -> i.update == null).thenComparing(i -> i.project == null)
                    .thenComparing(i -> i.title().toLowerCase(Locale.ROOT)));
            boolean ok = reached;
            Mc.mc().execute(() -> {
                installed.put(kind, found);
                scans.put(kind, ok ? State.READY : State.FAILED);
            });
        });
    }

    private static String sha1(Path file, long size) {
        try {
            String key = file + "|" + size + "|" + Files.getLastModifiedTime(file).toMillis();
            synchronized (hashes) {
                String cached = hashes.get(key);
                if (cached != null) return cached;
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[1 << 16];
                for (int n; (n = in.read(buffer)) > 0; ) digest.update(buffer, 0, n);
            }
            String hash = hex(digest.digest());
            synchronized (hashes) {
                hashes.put(key, hash);
            }
            return hash;
        } catch (Exception e) {
            return "";
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) out.append(String.format("%02x", b));
        return out.toString();
    }

    // ---- downloading -----------------------------------------------------------------------------

    /** The download under way for this project, or null. */
    public static Download download(String projectId) {
        return downloads.get(projectId);
    }

    public static boolean busy() {
        return !downloads.isEmpty();
    }

    /**
     * Fetches a version into the folder. If the folder already holds a file of the same project,
     * this one takes its place.
     */
    public static void install(Kind kind, Project project, Version version) {
        FileRef ref = version.file;
        if (ref == null || downloads.containsKey(project.id)) return;
        String name = Path.of(ref.filename().replace('\\', '/')).getFileName().toString();
        URI uri;
        try {
            uri = URI.create(ref.url());
        } catch (RuntimeException e) {
            uri = null;
        }
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || !FILE_HOST.equalsIgnoreCase(uri.getHost()) || !kind.holds(name)
                || name.startsWith(".")) {
            Toasts.warn("Not downloaded", "That file is not one the store can take.");
            return;
        }
        Download download = new Download(project.title);
        download.progress = -1;
        downloads.put(project.id, download);
        Installed old = installed(kind, project.id);
        Path dir = kind.dir();
        URI from = uri;
        POOL.execute(() -> {
            String problem = null;
            Path target = dir.resolve(name), part = dir.resolve(name + ".part");
            try {
                Files.createDirectories(dir);
                HttpRequest request = HttpRequest.newBuilder(from).timeout(Duration.ofMinutes(10)).header("User-Agent", Modrinth.AGENT).GET().build();
                HttpResponse<InputStream> response = Modrinth.HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
                MessageDigest digest = MessageDigest.getInstance(ref.sha512().isEmpty() ? "SHA-1" : "SHA-512");
                try (InputStream in = response.body()) {
                    if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
                    long total = ref.size() > 0 ? ref.size() : response.headers().firstValueAsLong("Content-Length").orElse(-1);
                    try (OutputStream out = Files.newOutputStream(part)) {
                        byte[] buffer = new byte[1 << 16];
                        long done = 0;
                        for (int n; (n = in.read(buffer)) > 0; ) {
                            out.write(buffer, 0, n);
                            digest.update(buffer, 0, n);
                            done += n;
                            if (total > 0) download.progress = Math.min(1f, done / (float) total);
                        }
                    }
                }
                String wanted = ref.sha512().isEmpty() ? ref.sha1() : ref.sha512();
                if (!wanted.isEmpty() && !wanted.equalsIgnoreCase(hex(digest.digest()))) {
                    problem = "The file did not match what Modrinth lists, so it was thrown away.";
                } else {
                    Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception e) {
                AllerClient.LOG.warn("Download of {} failed", name, e);
                problem = e instanceof java.nio.file.FileSystemException ? "The folder could not be written to." : "The download did not finish.";
            }
            if (problem != null) {
                try {
                    Files.deleteIfExists(part);
                } catch (IOException ignored) {
                    // left behind; it is not a pack as far as the game is concerned
                }
            }
            // The file this one replaces. The game keeps a pack that is switched on open, so it may refuse to go yet.
            boolean stuck = false;
            if (problem == null && old != null && !old.file.equals(target)) {
                try {
                    Files.deleteIfExists(old.file);
                } catch (IOException e) {
                    stuck = true;
                    old.file.toFile().deleteOnExit();
                }
            }
            String failed = problem;
            boolean kept = stuck;
            long size = ref.size();
            Mc.mc().execute(() -> {
                downloads.remove(project.id);
                if (failed != null) {
                    Toasts.warn("Not downloaded", failed);
                    return;
                }
                List<Installed> list = new ArrayList<>(installed(kind));
                list.removeIf(i -> i == old || i.file.equals(target));
                Installed now = new Installed(target, size);
                now.sha1 = ref.sha1();
                now.version = version;
                now.project = project;
                list.add(0, now);
                installed.put(kind, list);
                fresh.remove(name);
                fresh.add(name);
                if (old != null && !old.filename.equals(name)) {
                    replaced.put(old.filename, name);
                    fresh.remove(old.filename);
                    if (kept) doomed.add(old.file);
                }
                changes++;
                Toasts.info(old != null ? "Updated " + project.title : "Downloaded " + project.title,
                        old != null ? "Now " + version.number + "." : "It is at the top of your available " + kind.noun + "s.");
            });
        });
    }

    /** Deletes a file from the folder. */
    public static void remove(Kind kind, Installed entry) {
        List<Installed> list = new ArrayList<>(installed(kind));
        list.remove(entry);
        installed.put(kind, list);
        fresh.remove(entry.filename);
        changes++;
        POOL.execute(() -> {
            try {
                Files.deleteIfExists(entry.file);
            } catch (IOException e) {
                entry.file.toFile().deleteOnExit();
                Mc.mc().execute(() -> {
                    doomed.add(entry.file);
                    Toasts.warn("Still in use", entry.filename + " is switched on, so it goes when the game closes.");
                });
            }
        });
    }

    // ---- for the game's own list -----------------------------------------------------------------

    /** File names downloaded lately, newest first. */
    public static List<String> fresh() {
        List<String> out = new ArrayList<>(fresh);
        java.util.Collections.reverse(out);
        return out;
    }

    public static boolean fresh(String filename) {
        return fresh.contains(filename);
    }

    /** Stops marking downloads as new: the list they were pinned in has been closed. */
    public static void settle() {
        fresh.clear();
        replaced.clear();
    }

    /** The file that took this one's place in an update, or null. */
    public static String replacement(String filename) {
        String next = replaced.get(filename);
        // An update of an update.
        for (int i = 0; i < 8 && next != null && replaced.containsKey(next); i++) next = replaced.get(next);
        return next;
    }
}
