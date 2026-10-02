package dev.aller.feature.store;

import com.mojang.blaze3d.platform.NativeImage;
import dev.aller.AllerClient;
import dev.aller.platform.Mc;
import dev.aller.platform.Tex;
import dev.aller.ui.anim.Motion;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Pictures from the web as textures: icons, gallery shots and whatever a description embeds. Ask
 * for one every frame it is on show with {@link #get}; it is fetched (or read from the cache in
 * {@code aller-store/cache}), decoded and scaled on a worker thread and appears when ready.
 * Textures nothing has asked for lately are freed once there are too many.
 *
 * <p>PNG, JPEG, GIF (first frame) and WebP (through TwelveMonkeys, since Modrinth serves its icons
 * and thumbnails as WebP). SVG and animated WebP are not read; such a picture simply fails.
 */
public final class Images {
    /** A picture as far as it has got. Not to be kept: ask again each frame. */
    public static final class Image {
        /** Null until it has loaded, and again after it was freed. */
        public Tex tex;
        public boolean failed;
        /** The picture's own size in its pixels, 0 until known. */
        public int width, height;

        private final String url;
        private int have, want;
        private boolean loading;
        private float used;

        private Image(String url) {
            this.url = url;
        }

        public boolean ready() {
            return tex != null;
        }
    }

    private static final int MAX_BYTES = 16 << 20, MAX_SOURCE_PIXELS = 48_000_000;
    /** Texels kept on the graphics card before the least recently shown are freed (four bytes each). */
    private static final long BUDGET = 28_000_000;
    private static final long CACHE_LIMIT = 256L << 20;

    private static final Map<String, Image> images = new HashMap<>();
    private static final ThreadPoolExecutor POOL;
    private static int version;
    private static long texels;
    private static boolean pruned;

    static {
        // Newest first: what has just scrolled into view matters more than what was asked for a moment ago.
        POOL = new ThreadPoolExecutor(6, 6, 30, TimeUnit.SECONDS, new LinkedBlockingDeque<>() {
            @Override
            public boolean offer(Runnable task) {
                return offerFirst(task);
            }
        }, r -> {
            Thread t = new Thread(r, "Aller Client pictures");
            t.setDaemon(true);
            return t;
        });
        POOL.allowCoreThreadTimeOut(true);
    }

    private Images() {}

    /** Goes up whenever a picture arrives or fails, so a laid-out page knows to measure again. */
    public static int version() {
        return version;
    }

    public static Path cacheDir() {
        return Mc.mc().gameDirectory.toPath().resolve("aller-store").resolve("cache");
    }

    /**
     * @param url   where the picture is; null gives a failed picture
     * @param pixels the longer side it will be drawn at, in screen pixels: it is scaled down to about that
     */
    public static Image get(String url, float pixels) {
        if (url == null) url = "";
        Image image = images.get(url);
        if (image == null) {
            image = new Image(url);
            image.failed = Modrinth.safe(url) == null;
            images.put(url, image);
        }
        image.used = Motion.time();
        if (image.failed) return image;
        int bucket = 64;
        while (bucket < pixels && bucket < 2048) bucket *= 2;
        // A bigger copy is only worth fetching while the one in hand was scaled down to fit.
        boolean scaled = image.have > 0 && Math.max(image.width, image.height) > image.have;
        boolean needed = image.tex == null || bucket > image.have && scaled;
        if (needed && !image.loading) {
            image.want = Math.max(bucket, image.tex == null ? 0 : image.have);
            image.loading = true;
            Image target = image;
            int size = image.want;
            POOL.execute(() -> load(target, size));
        }
        return image;
    }

    // ---- loading (worker threads) ----------------------------------------------------------------

    private record Decoded(NativeImage pixels, int width, int height) {}

    private static void load(Image image, int size) {
        Decoded decoded = null;
        try {
            byte[] bytes = fetch(image.url);
            decoded = bytes == null ? null : decode(bytes, size);
        } catch (Throwable e) {
            AllerClient.LOG.debug("Could not load picture {}", image.url, e);
        }
        Decoded result = decoded;
        Mc.mc().execute(() -> {
            image.loading = false;
            version++;
            if (result == null) {
                image.failed = image.tex == null;
                return;
            }
            if (image.tex != null) {
                texels -= (long) image.tex.width * image.tex.height;
                image.tex.close();
            }
            image.tex = new Tex("aller-store-picture", result.pixels);
            image.width = result.width;
            image.height = result.height;
            image.have = size;
            texels += (long) image.tex.width * image.tex.height;
            trim();
        });
    }

    private static byte[] fetch(String url) throws IOException, InterruptedException {
        Path dir = cacheDir();
        if (!pruned) {
            pruned = true;
            prune(dir);
        }
        Path cached = dir.resolve(name(url));
        if (Files.isRegularFile(cached)) {
            try {
                byte[] bytes = Files.readAllBytes(cached);
                Files.setLastModifiedTime(cached, FileTime.fromMillis(System.currentTimeMillis()));
                if (bytes.length > 0) return bytes;
            } catch (IOException ignored) {
                // fetch it again
            }
        }
        URI uri = URI.create(url);
        if (uri.getHost() == null || local(uri.getHost())) return null;
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(25)).header("User-Agent", Modrinth.AGENT)
                .header("Accept", "image/webp,image/png,image/jpeg,image/gif,*/*;q=0.5").GET().build();
        HttpResponse<InputStream> response = Modrinth.HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        byte[] bytes;
        try (InputStream in = response.body()) {
            if (response.statusCode() != 200) return null;
            bytes = in.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length == 0 || bytes.length > MAX_BYTES) return null;
        try {
            Files.createDirectories(dir);
            Files.write(cached, bytes);
        } catch (IOException ignored) {
            // it will be fetched again next time
        }
        return bytes;
    }

    /** A page's pictures can point anywhere; this machine and its network are not somewhere to go looking. */
    private static boolean local(String host) {
        try {
            for (InetAddress a : InetAddress.getAllByName(host)) {
                if (a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress() || a.isAnyLocalAddress() || a.isMulticastAddress()) return true;
            }
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    private static String name(String url) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return Integer.toHexString(url.hashCode());
        }
    }

    private static Decoded decode(byte[] bytes, int size) throws IOException {
        BufferedImage source = read(bytes);
        if (source == null) return null;
        int w = source.getWidth(), h = source.getHeight();
        if (w <= 0 || h <= 0) return null;
        float fit = Math.min(1f, size / (float) Math.max(w, h));
        int tw = Math.max(1, Math.round(w * fit)), th = Math.max(1, Math.round(h * fit));
        BufferedImage scaled = scale(source, tw, th);
        int[] argb = scaled.getRGB(0, 0, tw, th, null, 0, tw);
        NativeImage out = new NativeImage(tw, th, false);
        for (int y = 0; y < th; y++) {
            for (int x = 0; x < tw; x++) out.setPixel(x, y, argb[y * tw + x]);
        }
        return new Decoded(out, w, h);
    }

    private static BufferedImage read(byte[] bytes) throws IOException {
        boolean webp = bytes.length > 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
        if (!webp) {
            try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(in);
                if (!readers.hasNext()) return null;
                return read(readers.next(), in);
            }
        }
        // Made directly rather than found through ImageIO's registry, which does not look in a mod's class loader.
        ImageReader reader = new com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi().createReaderInstance(null);
        try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            return read(reader, in);
        }
    }

    private static BufferedImage read(ImageReader reader, ImageInputStream in) throws IOException {
        try {
            reader.setInput(in, true, true);
            if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_SOURCE_PIXELS) return null;
            return reader.read(0);
        } finally {
            reader.dispose();
        }
    }

    /** Halves the picture until it is near the size wanted, then goes the rest of the way: sharper than one big step. */
    private static BufferedImage scale(BufferedImage source, int tw, int th) {
        BufferedImage current = source;
        int w = source.getWidth(), h = source.getHeight();
        do {
            w = Math.max(tw, w > tw * 2 ? w / 2 : tw);
            h = Math.max(th, h > th * 2 ? h / 2 : th);
            BufferedImage next = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = next.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(current, 0, 0, w, h, null);
            g.dispose();
            current = next;
        } while (w != tw || h != th);
        return current;
    }

    /** Keeps the cache folder from growing without end: the files touched longest ago go first. */
    private static void prune(Path dir) {
        if (!Files.isDirectory(dir)) return;
        record Entry(Path file, long size, long touched) {}
        List<Entry> entries = new ArrayList<>();
        long total = 0;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                if (!Files.isRegularFile(file)) continue;
                long size = Files.size(file);
                entries.add(new Entry(file, size, Files.getLastModifiedTime(file).toMillis()));
                total += size;
            }
            entries.sort(Comparator.comparingLong(Entry::touched));
            for (Entry e : entries) {
                if (total <= CACHE_LIMIT * 3 / 4) break;
                Files.deleteIfExists(e.file);
                total -= e.size;
            }
        } catch (IOException | RuntimeException e) {
            AllerClient.LOG.debug("Could not tidy {}", dir, e);
        }
    }

    // ---- freeing (render thread) -----------------------------------------------------------------

    private static void trim() {
        if (texels <= BUDGET) return;
        float now = Motion.time();
        List<Image> idle = new ArrayList<>();
        for (Image image : images.values()) if (image.tex != null && now - image.used > 3) idle.add(image);
        idle.sort(Comparator.comparingDouble(i -> i.used));
        for (Image image : idle) {
            if (texels <= BUDGET * 3 / 4) break;
            texels -= (long) image.tex.width * image.tex.height;
            image.tex.close();
            image.tex = null;
            image.have = 0;
        }
    }
}
