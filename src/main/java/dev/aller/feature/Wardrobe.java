package dev.aller.feature;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.aller.AllerClient;
import dev.aller.platform.Mc;
import dev.aller.platform.SkinTex;
import dev.aller.platform.Skins;
import dev.aller.ui.Toasts;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * The skins behind the wardrobe screen: a library of PNG files in {@code aller-skins}, the skin
 * being worn (asked of Minecraft services), and the ones worn before, which Mojang keeps no record
 * of, so they come from laby.net's public texture history. Changing skin is the same request the
 * launcher makes, signed with the session the game was started with.
 *
 * <p>Network and disk work runs on worker threads; everything in the lists is only touched on the
 * client thread.
 */
public final class Wardrobe {
    public enum Source { CURRENT, SAVED, HISTORY }

    public enum Lookup { IDLE, LOADING, DONE, EMPTY, FAILED }

    public static final class Outfit {
        public final Source source;
        public String name;
        /** Small print: when it was worn, or where it came from. */
        public String detail = "";
        public boolean slim;
        /** The file as it would be uploaded; null if there is none to send (an offline account's skin). */
        public byte[] png;
        /** Identifies the picture itself, whatever file it came in. */
        public String hash;
        /** Null until the image has been read. */
        public SkinTex tex;
        public Path file;

        Outfit(Source source, String name) {
            this.source = source;
            this.name = name;
        }
    }

    private record Decoded(int[] pixels, boolean slim, String hash) {}

    private static final String PROFILE = "https://api.minecraftservices.com/minecraft/profile";
    private static final String HISTORY = "https://laby.net/api/v3/user/%s/textures";
    private static final String HISTORY_IMAGE = "https://texture.laby.net/%s.png";
    private static final String AGENT = "AllerClient/" + AllerClient.VERSION + " (Minecraft mod)";
    private static final int MAX_FILE = 1 << 20, MAX_HISTORY = 60;
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.UK);

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "Aller wardrobe");
        t.setDaemon(true);
        return t;
    });

    private static final List<Outfit> saved = new ArrayList<>();
    private static final List<Outfit> history = new ArrayList<>();
    private static final Map<String, SkinTex> textures = new HashMap<>();
    private static Outfit current;
    private static Outfit fresh;
    private static Lookup lookup = Lookup.IDLE;
    private static String busy;
    private static boolean offline;
    private static float refreshedAt = -1000;

    private Wardrobe() {}

    public static Path dir() {
        return Mc.mc().gameDirectory.toPath().resolve("aller-skins");
    }

    public static List<Outfit> saved() {
        return saved;
    }

    public static List<Outfit> history() {
        return history;
    }

    /** What the player is wearing; never null once {@link #refresh} has been called. */
    public static Outfit current() {
        return current;
    }

    public static Lookup lookup() {
        return lookup;
    }

    /** What is in progress ("Changing skin"), or null. */
    public static String busy() {
        return busy;
    }

    /** True when the game was not started signed in, so skins can be looked at but not changed. */
    public static boolean offline() {
        return offline;
    }

    /** An outfit that has just been added and should be shown; handed out once. */
    public static Outfit takeFresh() {
        Outfit o = fresh;
        fresh = null;
        return o;
    }

    public static boolean wearing(Outfit o) {
        return o != null && current != null && (o == current || o.hash != null && o.hash.equals(current.hash));
    }

    /** Reads the library again and asks the two services what is worn now and what was worn before. */
    public static void refresh() {
        if (current == null) {
            current = new Outfit(Source.CURRENT, "Current skin");
            current.tex = SkinTex.fetched();
            current.slim = Skins.ownSlim();
        }
        float now = dev.aller.ui.anim.Motion.time();
        boolean recent = now - refreshedAt < 60;
        refreshedAt = now;
        loadLibrary();
        if (recent && lookup != Lookup.FAILED) return;
        fetchCurrent();
        fetchHistory();
    }

    // ---- the library ---------------------------------------------------------------------------

    private static void loadLibrary() {
        POOL.execute(() -> {
            record Found(Path file, byte[] png, Decoded image, long modified) {}
            List<Found> found = new ArrayList<>();
            Path dir = dir();
            if (Files.isDirectory(dir)) {
                try (Stream<Path> files = Files.list(dir)) {
                    for (Path file : (Iterable<Path>) files::iterator) {
                        if (!file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png") || !Files.isRegularFile(file)) continue;
                        try {
                            if (Files.size(file) > MAX_FILE) continue;
                            byte[] png = Files.readAllBytes(file);
                            Decoded image = decode(png);
                            if (image != null) found.add(new Found(file, png, image, Files.getLastModifiedTime(file).toMillis()));
                        } catch (IOException ignored) {
                            // a file that cannot be read is not a skin
                        }
                    }
                } catch (IOException | RuntimeException e) {
                    AllerClient.LOG.warn("Could not list {}", dir, e);
                }
            }
            found.sort(Comparator.comparingLong(Found::modified).reversed());
            main(() -> {
                saved.clear();
                for (Found f : found) saved.add(savedOutfit(f.file, f.png, f.image));
            });
        });
    }

    private static Outfit savedOutfit(Path file, byte[] png, Decoded image) {
        String name = file.getFileName().toString();
        Outfit o = new Outfit(Source.SAVED, name.substring(0, name.length() - 4));
        o.file = file;
        o.detail = "In your library";
        fill(o, png, image);
        Boolean arms = armsChoice(image.hash);
        if (arms != null) o.slim = arms;
        return o;
    }

    private static void fill(Outfit o, byte[] png, Decoded image) {
        o.png = png;
        o.hash = image.hash;
        o.slim = image.slim;
        o.tex = textures.computeIfAbsent(image.hash, h -> new SkinTex(h, image.pixels));
    }

    /** Adds a PNG from anywhere on disk to the library. */
    public static void importFile(Path file) {
        POOL.execute(() -> {
            byte[] png;
            try {
                png = Files.size(file) <= MAX_FILE ? Files.readAllBytes(file) : null;
            } catch (IOException | RuntimeException e) {
                png = null;
            }
            String name = file.getFileName().toString();
            int dot = name.lastIndexOf('.');
            add(dot > 0 ? name.substring(0, dot) : name, png, null);
        });
    }

    /** Copies what another player is wearing into the library. */
    public static void importName(String player) {
        String name = player.trim();
        if (!name.matches("[A-Za-z0-9_]{2,16}")) {
            Toasts.warn("Not a player name", "Names are 2 to 16 letters, digits or underscores.");
            return;
        }
        busy = "Looking up " + name;
        POOL.execute(() -> {
            String problem = "Could not reach Minecraft services.";
            try {
                HttpResponse<byte[]> found = get("https://api.mojang.com/users/profiles/minecraft/" + name, null);
                if (found.statusCode() == 404 || found.statusCode() == 204) {
                    problem = "Nobody is called " + name + ".";
                } else if (found.statusCode() == 200) {
                    JsonObject who = json(found.body());
                    HttpResponse<byte[]> profile = get("https://sessionserver.mojang.com/session/minecraft/profile/" + who.get("id").getAsString(), null);
                    if (profile.statusCode() == 200) {
                        String url = null;
                        boolean slim = false;
                        for (JsonElement p : json(profile.body()).getAsJsonArray("properties")) {
                            JsonObject property = p.getAsJsonObject();
                            if (!property.get("name").getAsString().equals("textures")) continue;
                            JsonObject textures = json(Base64.getDecoder().decode(property.get("value").getAsString())).getAsJsonObject("textures");
                            if (textures == null || !textures.has("SKIN")) continue;
                            JsonObject skin = textures.getAsJsonObject("SKIN");
                            url = skin.get("url").getAsString().replace("http://", "https://");
                            slim = skin.has("metadata") && "slim".equals(skin.getAsJsonObject("metadata").get("model").getAsString());
                        }
                        if (url == null) {
                            problem = who.get("name").getAsString() + " wears a default skin.";
                        } else {
                            HttpResponse<byte[]> image = get(url, null);
                            if (image.statusCode() == 200) {
                                add(who.get("name").getAsString(), image.body(), slim);
                                main(() -> busy = null);
                                return;
                            }
                        }
                    } else if (profile.statusCode() == 429) {
                        problem = "Too many lookups. Try again in a minute.";
                    }
                } else if (found.statusCode() == 429) {
                    problem = "Too many lookups. Try again in a minute.";
                }
            } catch (Exception e) {
                AllerClient.LOG.warn("Skin lookup for {} failed", name, e);
            }
            String message = problem;
            main(() -> {
                busy = null;
                Toasts.warn("No skin copied", message);
            });
        });
    }

    /** Writes a checked image into the library and shows it. Runs on a worker thread. */
    private static void add(String name, byte[] png, Boolean slim) {
        Decoded image = decode(png);
        if (image == null) {
            main(() -> Toasts.warn("Not a skin", "Skins are PNG images of 64×64 pixels (or 64×32)."));
            return;
        }
        try {
            Path dir = dir();
            Files.createDirectories(dir);
            String base = name.replaceAll("[^A-Za-z0-9 _-]", "").trim();
            if (base.isEmpty()) base = "skin";
            if (base.length() > 32) base = base.substring(0, 32);
            Path file = dir.resolve(base + ".png");
            for (int n = 2; Files.exists(file); n++) {
                // The same picture under the same name is already there: show that one.
                if (java.util.Arrays.equals(Files.readAllBytes(file), png)) break;
                file = dir.resolve(base + " " + n + ".png");
            }
            if (!Files.exists(file)) Files.write(file, png);
            Path written = file;
            main(() -> {
                if (slim != null) rememberArms(image.hash, slim);
                Outfit o = null;
                for (Outfit s : saved) if (s.file.equals(written)) o = s;
                if (o == null) {
                    o = savedOutfit(written, png, image);
                    saved.add(0, o);
                }
                fresh = o;
            });
        } catch (IOException | RuntimeException e) {
            AllerClient.LOG.warn("Could not save skin {}", name, e);
            main(() -> Toasts.warn("Could not save skin", "The aller-skins folder cannot be written to."));
        }
    }

    /** Keeps a copy of a worn or past skin in the library. */
    public static void save(Outfit o) {
        if (o.png == null) return;
        byte[] png = o.png;
        boolean slim = o.slim;
        String name = o.source == Source.HISTORY ? "Past skin " + o.name : "My skin";
        POOL.execute(() -> add(name, png, slim));
    }

    public static void remove(Outfit o) {
        if (o.source != Source.SAVED) return;
        saved.remove(o);
        Path file = o.file;
        POOL.execute(() -> {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                AllerClient.LOG.warn("Could not delete {}", file, e);
            }
        });
    }

    // ---- which arms a picture goes with ----------------------------------------------------------

    private static Boolean armsChoice(String hash) {
        try {
            JsonElement all = AllerClient.config().extra("wardrobe_arms");
            JsonElement one = all != null && all.isJsonObject() ? all.getAsJsonObject().get(hash) : null;
            return one != null ? one.getAsBoolean() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The picture alone does not say which arms it was painted for, so a choice made once is kept. */
    public static void rememberArms(String hash, boolean slim) {
        if (hash == null) return;
        JsonElement all = AllerClient.config().extra("wardrobe_arms");
        JsonObject map = all != null && all.isJsonObject() ? all.getAsJsonObject() : new JsonObject();
        map.addProperty(hash, slim);
        AllerClient.config().setExtra("wardrobe_arms", map);
    }

    // ---- what is worn now ------------------------------------------------------------------------

    private static String token() {
        String token = Mc.mc().getUser().getAccessToken();
        // A development or offline launch has a placeholder here.
        return token != null && token.length() >= 32 ? token : null;
    }

    private static void fetchCurrent() {
        String token = token();
        if (token == null) {
            offline = true;
            return;
        }
        POOL.execute(() -> {
            try {
                HttpResponse<byte[]> response = get(PROFILE, token);
                if (response.statusCode() != 200) {
                    boolean refused = response.statusCode() == 401 || response.statusCode() == 403 || response.statusCode() == 404;
                    main(() -> offline = refused);
                    return;
                }
                JsonObject active = activeSkin(json(response.body()));
                if (active == null) {
                    main(() -> offline = false);
                    return;
                }
                boolean slim = "SLIM".equalsIgnoreCase(active.get("variant").getAsString());
                byte[] png = get(active.get("url").getAsString().replace("http://", "https://"), null).body();
                Decoded image = decode(png);
                main(() -> {
                    offline = false;
                    if (image != null) worn(png, image, slim);
                });
            } catch (Exception e) {
                AllerClient.LOG.warn("Could not ask Minecraft services for the current skin", e);
            }
        });
    }

    private static JsonObject activeSkin(JsonObject profile) {
        if (!profile.has("skins")) return null;
        for (JsonElement s : profile.getAsJsonArray("skins")) {
            JsonObject skin = s.getAsJsonObject();
            if ("ACTIVE".equals(skin.get("state").getAsString())) return skin;
        }
        return null;
    }

    private static void worn(byte[] png, Decoded image, boolean slim) {
        Outfit o = new Outfit(Source.CURRENT, "Current skin");
        fill(o, png, image);
        o.slim = slim;
        current = o;
    }

    /**
     * Puts a skin on. Other players see it the next time the player joins a world or server; the
     * player's own view changes straight away.
     *
     * @param slim the arm width to wear it with
     */
    public static void wear(Outfit o, boolean slim) {
        String token = token();
        if (o.png == null || o.tex == null || busy != null) return;
        if (token == null) {
            Toasts.warn("Skin not changed", "This game was not started signed in to a Minecraft account.");
            return;
        }
        busy = "Changing skin";
        byte[] png = o.png;
        POOL.execute(() -> {
            String problem;
            try {
                String boundary = "----AllerSkin" + Long.toHexString(System.nanoTime());
                byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"variant\"\r\n\r\n" + (slim ? "slim" : "classic")
                        + "\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\nContent-Type: image/png\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8);
                byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
                HttpRequest request = HttpRequest.newBuilder(URI.create(PROFILE + "/skins")).timeout(Duration.ofSeconds(30))
                        .header("Authorization", "Bearer " + token).header("User-Agent", AGENT)
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArrays(List.of(head, png, tail))).build();
                int status = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray()).statusCode();
                problem = switch (status) {
                    case 200, 204 -> null;
                    case 401, 403 -> "The sign-in has run out. Restart the game from your launcher.";
                    case 429 -> "Too many changes. Try again in a minute.";
                    case 400 -> "Minecraft services did not accept that image.";
                    default -> "Minecraft services answered " + status + ".";
                };
            } catch (Exception e) {
                AllerClient.LOG.warn("Skin change failed", e);
                problem = "Could not reach Minecraft services.";
            }
            String failed = problem;
            main(() -> {
                busy = null;
                if (failed != null) {
                    Toasts.warn("Skin not changed", failed);
                    return;
                }
                Outfit now = new Outfit(Source.CURRENT, "Current skin");
                now.png = png;
                now.hash = o.hash;
                now.tex = o.tex;
                now.slim = slim;
                current = now;
                o.slim = slim;
                rememberArms(o.hash, slim);
                Skins.wear(o.tex, slim);
                Toasts.info("Skin changed", Mc.mc().level != null ? "Others see it once you rejoin." : "It is on for your next world or server.");
            });
        });
    }

    // ---- what was worn before --------------------------------------------------------------------

    private static void fetchHistory() {
        String id = System.getProperty("aller.dev.skinUuid", Mc.mc().getUser().getProfileId().toString()).replace("-", "");
        lookup = Lookup.LOADING;
        POOL.execute(() -> {
            try {
                HttpResponse<byte[]> response = get(String.format(HISTORY, id), null);
                if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
                JsonObject root = json(response.body());
                JsonArray skins = root.has("SKIN") && root.get("SKIN").isJsonArray() ? root.getAsJsonArray("SKIN") : new JsonArray();
                record Past(String image, boolean slim, boolean active, OffsetDateTime first, OffsetDateTime last) {}
                List<Past> past = new ArrayList<>();
                for (JsonElement e : skins) {
                    try {
                        JsonObject s = e.getAsJsonObject();
                        String image = s.get("image_hash").getAsString();
                        if (!image.matches("[0-9a-f]{16,64}")) continue;
                        past.add(new Past(image, flag(s, "slim_skin"), flag(s, "active"), date(s, "first_seen_at"), date(s, "last_seen_at")));
                    } catch (RuntimeException ignored) {
                        // skip an entry in a shape we do not know
                    }
                }
                past.sort(Comparator.comparing((Past p) -> !p.active)
                        .thenComparing(p -> p.last != null ? p.last : OffsetDateTime.MIN, Comparator.reverseOrder()));
                List<Outfit> found = new ArrayList<>();
                List<String> images = new ArrayList<>();
                for (Past p : past.subList(0, Math.min(past.size(), MAX_HISTORY))) {
                    Outfit o = new Outfit(Source.HISTORY, p.active ? "Latest" : p.last != null ? p.last.format(MONTH) : "Earlier");
                    o.slim = p.slim;
                    o.detail = p.active ? (p.first != null ? "Since " + p.first.format(MONTH) : "Worn now")
                            : p.first == null || p.last == null ? "Worn before"
                            : p.first.format(MONTH).equals(p.last.format(MONTH)) ? "Worn before"
                            : p.first.format(MONTH) + " to " + p.last.format(MONTH);
                    found.add(o);
                    images.add(p.image);
                }
                main(() -> {
                    history.clear();
                    history.addAll(found);
                    lookup = found.isEmpty() ? Lookup.EMPTY : Lookup.DONE;
                });
                for (int i = 0; i < found.size(); i++) {
                    Outfit o = found.get(i);
                    String image = images.get(i);
                    POOL.execute(() -> pastImage(o, image));
                }
            } catch (Exception e) {
                AllerClient.LOG.warn("Could not read skin history from laby.net", e);
                main(() -> lookup = Lookup.FAILED);
            }
        });
    }

    private static boolean flag(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsBoolean();
    }

    private static OffsetDateTime date(JsonObject o, String key) {
        try {
            return o.has(key) && !o.get(key).isJsonNull() ? OffsetDateTime.parse(o.get(key).getAsString()) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Fetches one past skin, keeping a copy on disk so it is only downloaded once. */
    private static void pastImage(Outfit o, String image) {
        Path cached = dir().resolve(".cache").resolve(image + ".png");
        byte[] png = null;
        try {
            if (Files.isRegularFile(cached)) png = Files.readAllBytes(cached);
        } catch (IOException ignored) {
            // download it again
        }
        Decoded decoded = decode(png);
        if (decoded == null) {
            try {
                HttpResponse<byte[]> response = get(String.format(HISTORY_IMAGE, image), null);
                if (response.statusCode() == 200) {
                    png = response.body();
                    decoded = decode(png);
                    if (decoded != null) {
                        Files.createDirectories(cached.getParent());
                        Files.write(cached, png);
                    }
                }
            } catch (Exception e) {
                AllerClient.LOG.debug("Could not fetch past skin {}", image, e);
            }
        }
        byte[] bytes = png;
        Decoded result = decoded;
        main(() -> {
            if (result == null) {
                history.remove(o);
                if (history.isEmpty() && lookup == Lookup.DONE) lookup = Lookup.EMPTY;
                return;
            }
            // laby.net records the arms each one was worn with.
            boolean slim = o.slim;
            fill(o, bytes, result);
            o.slim = slim;
        });
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private static void main(Runnable task) {
        Mc.mc().execute(task);
    }

    private static HttpResponse<byte[]> get(String url, String bearer) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("User-Agent", AGENT);
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        return HTTP.send(request.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static JsonObject json(byte[] body) {
        return JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /**
     * Reads a skin file into the 64 by 64 layout the model uses, the way the game does for a
     * downloaded skin: an old 64 by 32 skin gets its left limbs mirrored from the right ones, and
     * the base layer is made opaque. Null if the bytes are not a skin.
     */
    private static Decoded decode(byte[] png) {
        if (png == null || png.length == 0 || png.length > MAX_FILE) return null;
        try (NativeImage image = NativeImage.read(png)) {
            int w = image.getWidth(), h = image.getHeight();
            if (w != 64 || h != 64 && h != 32) return null;
            int[] p = new int[64 * 64];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < 64; x++) p[y * 64 + x] = image.getPixel(x, y);
            }
            boolean slim = false;
            if (h == 32) {
                copy(p, 4, 16, 16, 32, 4, 4);
                copy(p, 8, 16, 16, 32, 4, 4);
                copy(p, 0, 20, 24, 32, 4, 12);
                copy(p, 4, 20, 16, 32, 4, 12);
                copy(p, 8, 20, 8, 32, 4, 12);
                copy(p, 12, 20, 16, 32, 4, 12);
                copy(p, 44, 16, -8, 32, 4, 4);
                copy(p, 48, 16, -8, 32, 4, 4);
                copy(p, 40, 20, 0, 32, 4, 12);
                copy(p, 44, 20, -8, 32, 4, 12);
                copy(p, 48, 20, -16, 32, 4, 12);
                copy(p, 52, 20, -8, 32, 4, 12);
                // A hat layer with no transparency at all is a background colour, not a hat.
                boolean solid = true;
                for (int y = 0; y < 32 && solid; y++) {
                    for (int x = 32; x < 64; x++) solid &= p[y * 64 + x] >>> 24 >= 128;
                }
                if (solid) {
                    for (int y = 0; y < 32; y++) {
                        for (int x = 32; x < 64; x++) p[y * 64 + x] &= 0xFFFFFF;
                    }
                }
            } else {
                // Slim arms are three pixels wide, which leaves the last columns of the arm strip unused.
                slim = true;
                for (int y = 20; y < 32 && slim; y++) slim = p[y * 64 + 54] >>> 24 == 0 && p[y * 64 + 55] >>> 24 == 0;
            }
            opaque(p, 0, 0, 32, 16);
            opaque(p, 0, 16, 64, 32);
            opaque(p, 16, 48, 48, 64);
            for (int i = 0; i < p.length; i++) if (p[i] >>> 24 == 0) p[i] = 0;

            ByteBuffer bytes = ByteBuffer.allocate(p.length * 4);
            bytes.asIntBuffer().put(p);
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(bytes.array());
            StringBuilder hash = new StringBuilder();
            for (int i = 0; i < 10; i++) hash.append(String.format("%02x", digest[i]));
            return new Decoded(p, slim, hash.toString());
        } catch (Exception e) {
            return null;
        }
    }

    /** Copies a block to (dx, dy) away, flipped left to right. */
    private static void copy(int[] p, int x, int y, int dx, int dy, int w, int h) {
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) p[(y + dy + j) * 64 + x + dx + (w - 1 - i)] = p[(y + j) * 64 + x + i];
        }
    }

    private static void opaque(int[] p, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) p[y * 64 + x] |= 0xFF000000;
        }
    }
}
