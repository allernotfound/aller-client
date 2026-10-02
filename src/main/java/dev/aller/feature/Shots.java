package dev.aller.feature;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import dev.aller.AllerClient;
import dev.aller.feature.store.Images;
import dev.aller.platform.Game;
import dev.aller.platform.Mc;
import dev.aller.platform.Os;
import dev.aller.platform.Tex;
import dev.aller.ui.ShotCard;
import dev.aller.ui.Toasts;
import dev.aller.ui.anim.Motion;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * The screenshots: the PNG files in the game's {@code screenshots} folder, with what Aller Client
 * knows about each (where it was taken, whether it is a favourite) kept beside them in
 * {@code aller-screenshots/index.json}. It notices a new one as Minecraft saves it, which is what
 * the preview card and the screenshots screen are built on.
 *
 * <p>Deleting moves a file to {@code aller-screenshots/trash} first, so it can be taken back for a
 * few seconds; after that it goes to the system's recycle bin from where it was.
 */
public final class Shots {
    /** Seconds a deleted screenshot can still be taken back. */
    public static final float UNDO = 6f;
    /** Where a screenshot of the title screen or a menu outside a world was taken. */
    public static final String MENUS = "Menus";
    private static final int THUMB = 480, FULL = 4096;
    /** Texels of thumbnails kept on the graphics card before the least recently shown are freed. */
    private static final long BUDGET = 16_000_000;

    public static final class Shot {
        public Path file;
        public String name;
        /** When it was taken (the file's own time), in milliseconds. */
        public long time;
        public long size;
        /** In pixels; 0 if the file could not be read. */
        public int width, height;
        /** The server's address or the world's name; empty when it is not known. */
        public String place = "";
        public boolean favourite;
        private final Pic thumb = new Pic(), full = new Pic();
        private boolean gone;
    }

    /** One size of a screenshot as a texture, as far as it has got. */
    private static final class Pic {
        Tex tex;
        boolean loading, failed;
        float used;
    }

    public record Deleted(Shot shot, Path held, long at) {
        /** How much of the time to take it back is left, 1 to 0. */
        public float left() {
            return Math.clamp(1 - (System.nanoTime() - at) / 1e9f / UNDO, 0f, 1f);
        }
    }

    private static final List<Shot> all = new ArrayList<>();
    private static final Deque<Deleted> deleted = new ArrayDeque<>();
    /** Files on their way to the recycle bin, which pass through the folder once more and must not be listed. */
    private static final Set<String> leaving = ConcurrentHashMap.newKeySet();
    private static final ThreadPoolExecutor POOL;
    private static JsonObject index;
    private static int version, frame;
    private static boolean scanning, scanned;
    private static long texels;

    static {
        // Newest first: what has just scrolled into view matters more than what was asked for a moment ago.
        POOL = new ThreadPoolExecutor(3, 3, 30, TimeUnit.SECONDS, new LinkedBlockingDeque<>() {
            @Override
            public boolean offer(Runnable task) {
                return offerFirst(task);
            }
        }, r -> {
            Thread t = new Thread(r, "Aller Client screenshots");
            t.setDaemon(true);
            return t;
        });
        POOL.allowCoreThreadTimeOut(true);
    }

    private Shots() {}

    public static Path dir() {
        return Mc.mc().gameDirectory.toPath().resolve(Screenshot.SCREENSHOT_DIR);
    }

    private static Path home() {
        return Mc.mc().gameDirectory.toPath().resolve("aller-screenshots");
    }

    /** Newest first. Not to be changed. */
    public static List<Shot> all() {
        return all;
    }

    /** Goes up whenever the list or something about a screenshot changes. */
    public static int version() {
        return version;
    }

    /** True until the folder has been read once. */
    public static boolean loading() {
        return !scanned;
    }

    // ---- taking one ------------------------------------------------------------------------------

    /** Minecraft's message about a screenshot, on its way to chat. */
    private record Report(Consumer<Component> inner) implements Consumer<Component> {
        @Override
        public void accept(Component message) {
            Path file = saved(message);
            var options = AllerClient.options();
            // The card says it instead; a failure is always passed on.
            if (file == null || !options.shotCard.get() || !options.shotQuiet.get()) inner.accept(message);
            if (file != null) Mc.mc().execute(() -> captured(file));
        }
    }

    /** Wraps the callback of a screenshot about to be taken, so the file it reports is noticed. */
    public static Consumer<Component> report(Consumer<Component> callback) {
        if (callback instanceof Report) return callback;
        dev.aller.compat.EssentialCompat.screenshots(AllerClient.options().shotCard.get());
        return new Report(callback);
    }

    /** The file a "Saved screenshot as" message is about, or null for any other message. */
    private static Path saved(Component message) {
        if (!(message.getContents() instanceof TranslatableContents text) || !text.getKey().equals("screenshot.success")) return null;
        Object[] args = text.getArgs();
        if (args.length == 0 || !(args[0] instanceof Component name)) return null;
        return dir().resolve(name.getString());
    }

    private static Runnable held;
    private static int heldAt;
    private static boolean retaking;

    /**
     * A screenshot is about to be taken of the frame on screen. If the card is on it, the screenshot
     * waits for a frame drawn without it.
     *
     * @return true if it was put off: it will be taken again from {@link #frameEnd}
     */
    public static boolean hold(File workDir, String name, RenderTarget target, int factor, Consumer<Component> callback) {
        if (retaking || held != null || !ShotCard.inFrame()) return false;
        ShotCard.hide();
        heldAt = frame;
        held = () -> Screenshot.grab(workDir, name, target, factor, callback);
        return true;
    }

    public static void frameBegin() {
        frame++;
    }

    /** Counts frames drawn, for telling whether something was on screen in the last one. */
    public static int frame() {
        return frame;
    }

    public static void frameEnd() {
        if (held == null || frame <= heldAt) return;
        Runnable take = held;
        held = null;
        retaking = true;
        try {
            take.run();
        } catch (RuntimeException e) {
            AllerClient.LOG.warn("Could not take the screenshot that was put off", e);
        } finally {
            retaking = false;
        }
    }

    private static void captured(Path file) {
        String name = file.getFileName().toString();
        Shot shot = null;
        for (Shot s : all) if (s.name.equals(name)) shot = s;
        if (shot == null) {
            shot = new Shot();
            all.add(0, shot);
        } else {
            free(shot);
        }
        shot.file = file;
        shot.name = name;
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            shot.time = attributes.lastModifiedTime().toMillis();
            shot.size = attributes.size();
        } catch (IOException e) {
            shot.time = System.currentTimeMillis();
        }
        measure(shot);
        shot.place = here();
        store(shot);
        version++;
        if (AllerClient.options().shotCard.get()) ShotCard.show(shot);
    }

    private static String here() {
        String address = Game.serverAddress();
        if (address != null) return address;
        if (!Game.inWorld()) return MENUS;
        String world = Game.worldName();
        return world == null ? "" : world;
    }

    // ---- the folder ------------------------------------------------------------------------------

    /** Reads the folder again, on a worker; the list changes when it is done. */
    public static void scan() {
        if (scanning) return;
        scanning = true;
        boolean first = !scanned;
        Set<Path> kept = new HashSet<>();
        for (Deleted d : deleted) kept.add(d.held);
        POOL.execute(() -> {
            List<Shot> found = new ArrayList<>();
            try (Stream<Path> files = Files.list(dir())) {
                for (Path file : (Iterable<Path>) files::iterator) {
                    String name = file.getFileName().toString();
                    if (!name.toLowerCase(Locale.ROOT).endsWith(".png") || leaving.contains(name)) continue;
                    try {
                        BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
                        if (!attributes.isRegularFile()) continue;
                        Shot shot = new Shot();
                        shot.file = file;
                        shot.name = name;
                        shot.time = attributes.lastModifiedTime().toMillis();
                        shot.size = attributes.size();
                        measure(shot);
                        found.add(shot);
                    } catch (IOException ignored) {
                        // gone again, or not readable: leave it out
                    }
                }
            } catch (IOException | RuntimeException e) {
                // no folder yet: no screenshots
            }
            found.sort(Comparator.comparingLong((Shot s) -> s.time).reversed());
            if (first) {
                leftovers(kept);
                tidyThumbs(found);
            }
            Mc.mc().execute(() -> merge(found));
        });
    }

    private static void merge(List<Shot> found) {
        Map<String, Shot> known = new HashMap<>();
        for (Shot s : all) known.put(s.name, s);
        JsonObject data = index();
        Set<String> names = new HashSet<>();
        List<Shot> next = new ArrayList<>(found.size());
        for (Shot s : found) {
            names.add(s.name);
            Shot old = known.remove(s.name);
            // The same file keeps its object, and with it the pictures already loaded.
            if (old != null && old.size == s.size && old.time == s.time) {
                next.add(old);
                continue;
            }
            if (old != null) discard(old);
            read(s, data);
            next.add(s);
        }
        for (Shot s : known.values()) discard(s);
        for (Deleted d : deleted) names.add(d.shot.name);
        // What is known about files that are no longer there is dropped.
        if (data.keySet().removeIf(name -> !names.contains(name))) writeIndex();
        all.clear();
        all.addAll(next);
        scanning = false;
        scanned = true;
        version++;
    }

    /** Width and height from a PNG's header, which is far quicker than decoding it. */
    private static void measure(Shot shot) {
        try (DataInputStream in = new DataInputStream(Files.newInputStream(shot.file))) {
            byte[] head = new byte[16];
            in.readFully(head);
            if (head[12] != 'I' || head[13] != 'H' || head[14] != 'D' || head[15] != 'R') return;
            int w = in.readInt(), h = in.readInt();
            if (w > 0 && h > 0 && w < 1 << 16 && h < 1 << 16) {
                shot.width = w;
                shot.height = h;
            }
        } catch (IOException ignored) {
            // it shows without its size
        }
    }

    /** Files deleted just before the game last closed are still in the holding folder: send them on. */
    private static void leftovers(Set<Path> kept) {
        Path trash = home().resolve("trash");
        if (!Files.isDirectory(trash)) return;
        try (Stream<Path> files = Files.list(trash)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                if (kept.contains(file) || !Files.isRegularFile(file)) continue;
                String name = file.getFileName().toString();
                leaving.add(name);
                recycle(file, dir().resolve(name));
                leaving.remove(name);
            }
        } catch (IOException | RuntimeException e) {
            AllerClient.LOG.debug("Could not tidy {}", trash, e);
        }
    }

    /** From the holding folder back to where it was, and from there to the recycle bin, so that is where restoring it puts it. */
    private static boolean recycle(Path held, Path home) {
        Path from = held;
        try {
            if (!Files.exists(home)) {
                Files.move(held, home);
                from = home;
            }
        } catch (IOException e) {
            // recycled from the holding folder instead
        }
        return Os.recycle(from);
    }

    // ---- what is known about each ----------------------------------------------------------------

    private static JsonObject index() {
        if (index != null) return index;
        index = new JsonObject();
        Path file = home().resolve("index.json");
        try {
            if (Files.exists(file)) {
                JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                if (root.isJsonObject()) index = root.getAsJsonObject();
            }
        } catch (IOException | RuntimeException e) {
            AllerClient.LOG.warn("Could not read {}; places and favourites start again", file, e);
        }
        return index;
    }

    private static void read(Shot shot, JsonObject data) {
        try {
            JsonElement entry = data.get(shot.name);
            if (entry == null || !entry.isJsonObject()) return;
            JsonObject o = entry.getAsJsonObject();
            if (o.has("place") && o.get("place").isJsonPrimitive()) shot.place = o.get("place").getAsString();
            if (o.has("favourite") && o.get("favourite").isJsonPrimitive()) shot.favourite = o.get("favourite").getAsBoolean();
        } catch (RuntimeException e) {
            // a bad entry is no entry
        }
    }

    private static void store(Shot shot) {
        JsonObject data = index();
        if (shot.place.isEmpty() && !shot.favourite) {
            data.remove(shot.name);
        } else {
            JsonObject o = new JsonObject();
            if (!shot.place.isEmpty()) o.addProperty("place", shot.place);
            if (shot.favourite) o.addProperty("favourite", true);
            data.add(shot.name, o);
        }
        writeIndex();
    }

    private static void writeIndex() {
        Path file = home().resolve("index.json");
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("index.json.tmp");
            Files.writeString(tmp, index().toString(), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            AllerClient.LOG.warn("Could not save {}", file, e);
        }
    }

    public static void favourite(Shot shot, boolean on) {
        if (shot.favourite == on) return;
        shot.favourite = on;
        store(shot);
        version++;
    }

    /**
     * Gives the file a new name, keeping its ending.
     *
     * @return false if it could not be done, which a toast has then explained
     */
    public static boolean rename(Shot shot, String wanted) {
        String base = wanted.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "").strip();
        int dot = shot.name.lastIndexOf('.');
        String ending = dot < 0 ? "" : shot.name.substring(dot);
        if (base.toLowerCase(Locale.ROOT).endsWith(ending.toLowerCase(Locale.ROOT))) base = base.substring(0, base.length() - ending.length()).strip();
        while (base.endsWith(".")) base = base.substring(0, base.length() - 1).strip();
        if (base.isEmpty()) return false;
        if (base.length() > 80) base = base.substring(0, 80).strip();
        String name = base + ending;
        if (name.equals(shot.name)) return true;
        Path to = shot.file.resolveSibling(name);
        if (Files.exists(to)) {
            Toasts.warn("That name is taken", name);
            return false;
        }
        try {
            Files.move(shot.file, to);
        } catch (IOException | RuntimeException e) {
            Toasts.warn("Could not rename the screenshot", "Another program may have it open.");
            return false;
        }
        index().remove(shot.name);
        shot.file = to;
        shot.name = name;
        store(shot);
        version++;
        return true;
    }

    /** Puts the picture on the clipboard; {@code done} hears on the client thread whether that worked. */
    public static void copy(Shot shot, Consumer<Boolean> done) {
        Path file = shot.file;
        POOL.execute(() -> {
            boolean ok = Os.copyImage(file);
            Mc.mc().execute(() -> done.accept(ok));
        });
    }

    // ---- deleting --------------------------------------------------------------------------------

    /** Takes a screenshot out of the folder. It can be brought back with {@link #undo} for {@link #UNDO} seconds. */
    public static boolean delete(Shot shot) {
        Path trash = home().resolve("trash"), held = trash.resolve(shot.name);
        try {
            Files.createDirectories(trash);
            if (Files.exists(held)) held = trash.resolve(System.currentTimeMillis() + "-" + shot.name);
            Files.move(shot.file, held);
        } catch (IOException | RuntimeException e) {
            Toasts.warn("Could not delete the screenshot", "Another program may have it open.");
            return false;
        }
        all.remove(shot);
        deleted.push(new Deleted(shot, held, System.nanoTime()));
        version++;
        return true;
    }

    /** The screenshot deleted last, while it can still be taken back; otherwise null. */
    public static Deleted lastDeleted() {
        return deleted.peek();
    }

    /** Puts the screenshot deleted last back. @return it, or null if there was none or it could not be done */
    public static Shot undo() {
        Deleted d = deleted.poll();
        if (d == null) return null;
        Shot shot = d.shot;
        try {
            Files.move(d.held, shot.file);
        } catch (IOException | RuntimeException e) {
            Toasts.warn("Could not bring the screenshot back", "It is still in aller-screenshots/trash.");
            return null;
        }
        int at = 0;
        while (at < all.size() && all.get(at).time > shot.time) at++;
        all.add(at, shot);
        version++;
        return shot;
    }

    /** Once a client tick: sends on what can no longer be taken back. */
    public static void tick() {
        while (!deleted.isEmpty() && deleted.peekLast().left() <= 0) {
            Deleted d = deleted.pollLast();
            Shot shot = d.shot;
            discard(shot);
            if (index().remove(shot.name) != null) writeIndex();
            leaving.add(shot.name);
            POOL.execute(() -> {
                boolean ok = recycle(d.held, shot.file);
                leaving.remove(shot.name);
                if (!ok) {
                    Mc.mc().execute(() -> {
                        Toasts.warn("Could not reach the recycle bin", "The screenshot was kept.");
                        scan();
                    });
                }
            });
        }
    }

    // ---- pictures --------------------------------------------------------------------------------

    /** A small copy for a tile or the card: null until it has loaded. Ask every frame it is on show. */
    public static Tex thumb(Shot shot) {
        return picture(shot, shot.thumb, THUMB);
    }

    /** The picture itself, for looking at it full size: null until it has loaded. */
    public static Tex full(Shot shot) {
        return picture(shot, shot.full, FULL);
    }

    public static boolean failed(Shot shot) {
        return shot.thumb.failed;
    }

    private static Tex picture(Shot shot, Pic pic, int size) {
        pic.used = Motion.time();
        if (pic.tex == null && !pic.loading && !pic.failed) {
            pic.loading = true;
            Path file = shot.file;
            String key = key(shot);
            POOL.execute(() -> load(shot, pic, file, key, size));
        }
        return pic.tex;
    }

    private static void load(Shot shot, Pic pic, Path file, String key, int size) {
        NativeImage pixels = null;
        try {
            BufferedImage image = size == THUMB ? thumbnail(file, key) : fit(decode(file, size), size);
            if (image != null) pixels = toNative(image);
        } catch (Throwable e) {
            AllerClient.LOG.debug("Could not read screenshot {}", file, e);
        }
        NativeImage result = pixels;
        Mc.mc().execute(() -> {
            pic.loading = false;
            if (result == null) {
                pic.failed = true;
                return;
            }
            if (shot.gone || pic.tex != null) {
                result.close();
                return;
            }
            pic.tex = new Tex("aller-screenshot", result);
            texels += (long) pic.tex.width * pic.tex.height;
            trim(shot);
        });
    }

    /** The small copy, from {@code aller-screenshots/thumbs} if it was made before. */
    private static BufferedImage thumbnail(Path file, String key) throws IOException {
        Path cached = home().resolve("thumbs").resolve(key + ".jpg");
        if (Files.isRegularFile(cached)) {
            try {
                BufferedImage image = decode(cached, THUMB);
                if (image != null) return image;
            } catch (IOException | RuntimeException e) {
                // made again below
            }
        }
        BufferedImage small = fit(decode(file, THUMB * 2), THUMB);
        if (small == null) return null;
        BufferedImage plain = new BufferedImage(small.getWidth(), small.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = plain.createGraphics();
        g.drawImage(small, 0, 0, null);
        g.dispose();
        try {
            Files.createDirectories(cached.getParent());
            var writers = ImageIO.getImageWritersByFormatName("jpg");
            if (writers.hasNext()) {
                ImageWriter writer = writers.next();
                Path tmp = cached.resolveSibling(key + ".tmp");
                try (FileImageOutputStream out = new FileImageOutputStream(tmp.toFile())) {
                    ImageWriteParam param = writer.getDefaultWriteParam();
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(0.86f);
                    writer.setOutput(out);
                    writer.write(null, new IIOImage(plain, null, null), param);
                } finally {
                    writer.dispose();
                }
                Files.move(tmp, cached, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            // it will be made again next time
        }
        return plain;
    }

    /** Reads a picture, skipping rows and columns while that still leaves more than {@code target} pixels a side. */
    private static BufferedImage decode(Path file, int target) throws IOException {
        try (FileImageInputStream in = new FileImageInputStream(file.toFile())) {
            var readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || (long) w * h > 80_000_000) return null;
                ImageReadParam param = reader.getDefaultReadParam();
                int step = Math.max(1, Math.max(w, h) / target);
                param.setSourceSubsampling(step, step, 0, 0);
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage fit(BufferedImage source, int size) {
        if (source == null) return null;
        int w = source.getWidth(), h = source.getHeight();
        if (Math.max(w, h) <= size) return source;
        float fit = size / (float) Math.max(w, h);
        return Images.scale(source, Math.max(1, Math.round(w * fit)), Math.max(1, Math.round(h * fit)));
    }

    private static NativeImage toNative(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        int[] argb = image.getRGB(0, 0, w, h, null, 0, w);
        NativeImage out = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) out.setPixel(x, y, argb[y * w + x] | 0xFF000000);
        }
        return out;
    }

    private static String key(Shot shot) {
        return key(shot.name, shot.size, shot.time);
    }

    private static String key(String name, long size, long time) {
        String text = name + "|" + size + "|" + time;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return Integer.toHexString(text.hashCode());
        }
    }

    /** Small copies of screenshots that are no longer there are deleted. */
    private static void tidyThumbs(List<Shot> found) {
        Path thumbs = home().resolve("thumbs");
        if (!Files.isDirectory(thumbs)) return;
        Set<String> wanted = new HashSet<>();
        for (Shot s : found) wanted.add(key(s) + ".jpg");
        try (Stream<Path> files = Files.list(thumbs)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                if (!wanted.contains(file.getFileName().toString())) Files.deleteIfExists(file);
            }
        } catch (IOException | RuntimeException e) {
            AllerClient.LOG.debug("Could not tidy {}", thumbs, e);
        }
    }

    /** Frees pictures not shown lately: thumbnails once there are too many, and all but a few of the full-size ones. */
    private static void trim(Shot keep) {
        float now = Motion.time();
        List<Shot> big = new ArrayList<>();
        for (Shot s : all) if (s.full.tex != null && s != keep && now - s.full.used > 1) big.add(s);
        big.sort(Comparator.comparingDouble(s -> s.full.used));
        for (int i = 0; i < big.size() - 2; i++) release(big.get(i).full);
        if (texels <= BUDGET) return;
        List<Shot> idle = new ArrayList<>();
        for (Shot s : all) if (s.thumb.tex != null && now - s.thumb.used > 2) idle.add(s);
        idle.sort(Comparator.comparingDouble(s -> s.thumb.used));
        for (Shot s : idle) {
            if (texels <= BUDGET * 3 / 4) break;
            release(s.thumb);
        }
    }

    private static void release(Pic pic) {
        if (pic.tex == null) return;
        texels -= (long) pic.tex.width * pic.tex.height;
        pic.tex.close();
        pic.tex = null;
    }

    /** Lets go of a screenshot's pictures so they are read again (the file changed). */
    private static void free(Shot shot) {
        release(shot.thumb);
        release(shot.full);
        shot.thumb.failed = shot.full.failed = false;
    }

    /** The screenshot is no longer in the list: nothing of it is kept. */
    private static void discard(Shot shot) {
        shot.gone = true;
        release(shot.thumb);
        release(shot.full);
    }
}
