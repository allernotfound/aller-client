package dev.aller.feature;

import dev.aller.AllerClient;
import dev.aller.module.Modules;
import dev.aller.platform.Mc;
import dev.aller.ui.Toasts;
import net.minecraft.client.Screenshot;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Instant replay: keeps the last N seconds of gameplay as JPEG frames in memory and writes them to
 * a Motion-JPEG {@code .avi} on demand. Pure Java, so there are no native encoders to ship; the
 * trade-off is larger files than H.264 and a per-frame readback cost while it is enabled.
 */
public final class Replay {
    private record Frame(long time, byte[] jpeg, int width, int height) {}

    private static final ArrayDeque<Frame> frames = new ArrayDeque<>();
    private static final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Aller replay encoder");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    /** Frames read back from the GPU but not yet encoded; bounds memory if the encoder falls behind. */
    private static final AtomicInteger inFlight = new AtomicInteger();
    private static long lastCapture;
    private static boolean saving;

    private Replay() {}

    /** Called at the end of every rendered frame. */
    public static void frameEnd() {
        var mod = Modules.REPLAY;
        if (!mod.enabled() || Mc.mc().level == null) {
            if (!frames.isEmpty() && !mod.enabled()) clear();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastCapture < 1000L / mod.fps.asInt() || inFlight.get() >= 3) return;
        lastCapture = now;

        var target = Mc.mainTarget();
        int factor = downscale(target.width, target.height, mod.height.asInt());
        float quality = mod.quality.get() / 100f;
        long keepMs = mod.seconds.asInt() * 1000L;
        inFlight.incrementAndGet();
        try {
            Screenshot.takeScreenshot(target, factor, image -> {
                int[] argb;
                int w, h;
                try (image) {
                    w = image.getWidth();
                    h = image.getHeight();
                    argb = image.getPixels();
                }
                worker.execute(() -> {
                    try {
                        byte[] jpeg = encode(argb, w, h, quality);
                        synchronized (frames) {
                            frames.add(new Frame(now, jpeg, w, h));
                            while (!frames.isEmpty() && now - frames.peek().time > keepMs) frames.poll();
                        }
                    } catch (Exception e) {
                        AllerClient.LOG.warn("Replay frame dropped", e);
                    } finally {
                        inFlight.decrementAndGet();
                    }
                });
            });
        } catch (RuntimeException e) {
            inFlight.decrementAndGet();
        }
    }

    /** Largest whole-number reduction that divides the framebuffer evenly and keeps at least the target height. */
    private static int downscale(int width, int height, int targetHeight) {
        for (int f = 6; f > 1; f--) {
            if (width % f == 0 && height % f == 0 && height / f >= targetHeight) return f;
        }
        return 1;
    }

    private static byte[] encode(int[] argb, int w, int h, float quality) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, w, h, argb, 0, w);
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        ByteArrayOutputStream out = new ByteArrayOutputStream(w * h / 6);
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(stream);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    public static void clear() {
        synchronized (frames) {
            frames.clear();
        }
    }

    public static int bufferedSeconds() {
        synchronized (frames) {
            if (frames.size() < 2) return 0;
            return (int) ((frames.peekLast().time - frames.peekFirst().time) / 1000);
        }
    }

    public static long bufferedBytes() {
        long total = 0;
        synchronized (frames) {
            for (Frame f : frames) total += f.jpeg.length;
        }
        return total;
    }

    /** Writes the current buffer to disk in the background. */
    public static void save() {
        if (saving) return;
        List<Frame> snapshot;
        synchronized (frames) {
            snapshot = new ArrayList<>(frames);
        }
        // Frames from before a resize have a different size; keep the most recent consistent run.
        if (!snapshot.isEmpty()) {
            Frame last = snapshot.get(snapshot.size() - 1);
            int start = snapshot.size() - 1;
            while (start > 0 && snapshot.get(start - 1).width == last.width && snapshot.get(start - 1).height == last.height) start--;
            snapshot = snapshot.subList(start, snapshot.size());
        }
        if (snapshot.size() < 5) {
            Toasts.warn("Nothing to save yet", "The replay buffer is still filling.");
            return;
        }
        saving = true;
        List<Frame> clip = snapshot;
        Toasts.info("Saving clip", clip.size() + " frames");
        worker.execute(() -> {
            try {
                Path dir = Mc.mc().gameDirectory.toPath().resolve("aller-clips");
                Files.createDirectories(dir);
                String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss"));
                Path file = dir.resolve("clip_" + stamp + ".avi");
                writeAvi(file, clip);
                long seconds = (clip.get(clip.size() - 1).time - clip.get(0).time) / 1000;
                Mc.mc().execute(() -> Toasts.info("Clip saved (" + seconds + "s)", "aller-clips/" + file.getFileName()));
            } catch (Exception e) {
                AllerClient.LOG.error("Could not save clip", e);
                Mc.mc().execute(() -> Toasts.warn("Clip failed", String.valueOf(e.getMessage())));
            } finally {
                saving = false;
            }
        });
    }

    /** Minimal AVI 1.0 container with a single MJPG video stream and an index. */
    private static void writeAvi(Path file, List<Frame> clip) throws IOException {
        int w = clip.get(0).width, h = clip.get(0).height, n = clip.size();
        long span = Math.max(1, clip.get(n - 1).time - clip.get(0).time);
        // Use the measured average rate so playback speed matches real time even if frames were skipped.
        int microsPerFrame = (int) Math.max(1, span * 1000 / Math.max(1, n - 1));
        int maxFrame = 0;
        long moviSize = 4; // 'movi' fourcc
        for (Frame f : clip) {
            maxFrame = Math.max(maxFrame, f.jpeg.length);
            moviSize += 8 + pad(f.jpeg.length);
        }
        int hdrlSize = 4 + (8 + 56) + (8 + 4 + (8 + 56) + (8 + 40));
        int idxSize = n * 16;
        long riffSize = 4 + (8 + hdrlSize) + (8 + moviSize) + (8 + idxSize);
        if (riffSize > 0x7FFFFFF0L) throw new IOException("Clip is too large for the AVI format; lower the length or quality");

        try (RandomAccessFile out = new RandomAccessFile(file.toFile(), "rw")) {
            out.setLength(0);
            ByteBuffer b = ByteBuffer.allocate(12 + 8 + hdrlSize + 12).order(ByteOrder.LITTLE_ENDIAN);
            fourcc(b, "RIFF").putInt((int) riffSize);
            fourcc(b, "AVI ");
            fourcc(b, "LIST").putInt(hdrlSize);
            fourcc(b, "hdrl");
            // Main header.
            fourcc(b, "avih").putInt(56);
            b.putInt(microsPerFrame).putInt((int) (maxFrame * 1_000_000L / microsPerFrame)).putInt(0);
            b.putInt(0x10) // AVIF_HASINDEX
                    .putInt(n).putInt(0).putInt(1).putInt(maxFrame).putInt(w).putInt(h);
            b.putInt(0).putInt(0).putInt(0).putInt(0);
            // Stream list.
            fourcc(b, "LIST").putInt(4 + (8 + 56) + (8 + 40));
            fourcc(b, "strl");
            fourcc(b, "strh").putInt(56);
            fourcc(b, "vids");
            fourcc(b, "MJPG");
            b.putInt(0).putShort((short) 0).putShort((short) 0).putInt(0);
            b.putInt(microsPerFrame).putInt(1_000_000) // scale / rate
                    .putInt(0).putInt(n).putInt(maxFrame).putInt(-1).putInt(0);
            b.putShort((short) 0).putShort((short) 0).putShort((short) w).putShort((short) h);
            fourcc(b, "strf").putInt(40);
            b.putInt(40).putInt(w).putInt(h).putShort((short) 1).putShort((short) 24);
            fourcc(b, "MJPG");
            b.putInt(w * h * 3).putInt(0).putInt(0).putInt(0).putInt(0);
            // Frame data.
            fourcc(b, "LIST").putInt((int) moviSize);
            fourcc(b, "movi");
            out.write(b.array(), 0, b.position());

            ByteBuffer idx = ByteBuffer.allocate(8 + idxSize).order(ByteOrder.LITTLE_ENDIAN);
            fourcc(idx, "idx1").putInt(idxSize);
            int offset = 4;
            ByteBuffer head = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            for (Frame f : clip) {
                head.clear();
                fourcc(head, "00dc").putInt(f.jpeg.length);
                out.write(head.array());
                out.write(f.jpeg);
                if ((f.jpeg.length & 1) == 1) out.write(0);
                fourcc(idx, "00dc").putInt(0x10).putInt(offset).putInt(f.jpeg.length);
                offset += 8 + pad(f.jpeg.length);
            }
            out.write(idx.array());
        }
    }

    private static int pad(int size) {
        return size + (size & 1);
    }

    private static ByteBuffer fourcc(ByteBuffer b, String code) {
        for (int i = 0; i < 4; i++) b.put((byte) code.charAt(i));
        return b;
    }
}
