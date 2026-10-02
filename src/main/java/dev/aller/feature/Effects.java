package dev.aller.feature;

import com.mojang.blaze3d.textures.GpuTextureView;
import dev.aller.AllerClient;
import dev.aller.module.Modules;
import dev.aller.module.mods.EffectMods;
import dev.aller.module.mods.EffectMods.Quality;
import dev.aller.platform.Post;
import dev.aller.ui.Toasts;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.Arrays;

/**
 * The effect mods' work on the picture of the world, once it is drawn and before the HUD goes on:
 * depth of field, colour grading and motion blur (and bloom, rim lighting, sharpening and the sky
 * tint, whose mods are not in the catalogue at present).
 *
 * Bloom and depth of field prepare small blurred pictures of their own and the rest is mixed in one
 * pass, so a second effect costs a few texture reads rather than another trip over the screen.
 * Motion blur is a pass of its own after that. Nothing runs, and no memory is held, while every mod
 * is off.
 */
public final class Effects {
    private Effects() {}

    private static final int MAX_LEVELS = 5;

    private static Post.Pass down, up, coc, blur, composite, motion;
    private static final Post.Target scene = new Post.Target("scene", true);
    /** Where the mixing pass draws when motion blur still has to follow it. */
    private static final Post.Target work = new Post.Target("work", false);
    private static final Post.Target dofA = new Post.Target("focus", false), dofB = new Post.Target("focus blur", false);
    private static final Post.Target[] bloom = new Post.Target[MAX_LEVELS];
    private static final float[] v = new float[Post.VECTORS * 4];

    private static boolean failed, holding;
    /** Whether {@link #scene} holds this frame's world depth (taken before the hand is drawn over it). */
    private static boolean depthFresh;
    private static long lastFrame;
    // The camera a frame ago, for motion blur.
    private static Matrix4f lastCamera;
    private static Vec3 lastPos;
    private static float focus = 1f / 16f;

    private static boolean wantsDepth() {
        return Modules.DEPTH_OF_FIELD.enabled() || Modules.RIM_LIGHT.enabled() || Modules.MOTION_BLUR.enabled()
                || Modules.ATMOSPHERE.enabled() && Modules.ATMOSPHERE.skyTinted.get();
    }

    private static boolean any() {
        return Modules.BLOOM.enabled() || Modules.COLOUR_GRADING.enabled()
                || Modules.SHARPEN.enabled() || wantsDepth();
    }

    /** The world is drawn and the hand is about to be: its depth is cleared for that, so keep a copy. */
    public static void worldDepth() {
        depthFresh = false;
        if (failed || !wantsDepth()) return;
        try {
            if (Post.shaderPack()) return;
            scene.size(Post.width(), Post.height());
            Post.grabDepth(scene);
            depthFresh = true;
        } catch (RuntimeException e) {
            fail(e);
        }
    }

    /** World and hand are drawn; the HUD is not. */
    public static void worldDrawn() {
        if (failed) return;
        if (!any()) {
            release();
            return;
        }
        try {
            if (ready()) draw();
        } catch (RuntimeException e) {
            fail(e);
        }
    }

    private static boolean ready() {
        if (composite == null) {
            down = Post.pass("bloom_down", "InSampler");
            up = Post.additive("bloom_up", "InSampler");
            coc = Post.pass("dof_coc", "InSampler", "SceneDepthSampler", "HandDepthSampler");
            blur = Post.pass("dof_blur", "InSampler");
            composite = Post.pass("composite", "SceneSampler", "SceneDepthSampler", "HandDepthSampler",
                    "BloomSampler", "DofSampler");
            motion = Post.pass("motion", "InSampler", "SceneDepthSampler", "HandDepthSampler");
            for (int i = 0; i < MAX_LEVELS; i++) bloom[i] = new Post.Target("bloom " + i, false);
        }
        if (Post.usable(down, up, coc, blur, composite, motion)) return true;
        failed = true;
        Toasts.info("Effects are off", "Their shaders did not compile on this graphics driver");
        return false;
    }

    private static void draw() {
        int w = Post.width(), h = Post.height();
        long now = System.nanoTime();
        float dt = Math.min((now - lastFrame) / 1e9f, 1f);
        lastFrame = now;
        holding = true;

        boolean depth = depthFresh;
        Matrix4f back = camera(dt);
        boolean streak = depth && back != null && Modules.MOTION_BLUR.enabled();
        boolean look = Modules.BLOOM.enabled() || Modules.COLOUR_GRADING.enabled() || Modules.SHARPEN.enabled()
                || depth && (Modules.DEPTH_OF_FIELD.enabled() || Modules.RIM_LIGHT.enabled()
                || Modules.ATMOSPHERE.enabled() && Modules.ATMOSPHERE.skyTinted.get());
        // Only depth effects are on and there is no depth to work with (a shader pack): leave the picture alone.
        if (!streak && !look) return;

        scene.size(w, h);
        Post.grabColor(scene);
        float[] world = depth ? Post.depthParams(0.05f, Post.worldFar()) : null;
        float[] hand = depth ? Post.depthParams(0.05f, 100f) : null;

        GpuTextureView glow = Modules.BLOOM.enabled() ? bloom() : null;
        GpuTextureView soft = depth && Modules.DEPTH_OF_FIELD.enabled() ? focus(w, h, dt, world, hand) : null;

        clear();
        set(0, 1f / w, 1f / h, (float) w / h, (now / 1_000_000L % 100_000L) / 1000f);
        if (depth) {
            set(1, world);
            set(2, hand);
        }
        var bloomMod = Modules.BLOOM;
        set(3, depth ? 1 : 0, depth ? 1 : 0,
                Modules.SHARPEN.enabled() ? Modules.SHARPEN.amount.get() / 100f : 0,
                glow != null ? bloomMod.strength.get() / 100f * 1.6f : 0);
        if (soft != null) {
            var dof = Modules.DEPTH_OF_FIELD;
            set(4, focus, dof.strength.get() / 100f * 8f, dof.near.get() ? 1 : 0, 1);
        }
        if (depth && Modules.RIM_LIGHT.enabled()) {
            var rim = Modules.RIM_LIGHT;
            rgb(5, rim.tint(), rim.strength.get() / 100f);
            Quality q = rim.quality.get();
            // Widths are given for a 1080p picture.
            set(6, rim.width.get() * h / 1080f, rim.range.get(), rim.from.get() == EffectMods.RimLight.From.ABOVE ? 1 : 0,
                    rim.heldItem.get() ? 1 : 0);
            v[11 * 4 + 2] = q == Quality.LOW ? 6 : q == Quality.MEDIUM ? 8 : 14;
            v[11 * 4 + 3] = q == Quality.LOW ? 0 : 1;
        }
        if (Modules.COLOUR_GRADING.enabled()) {
            var g = Modules.COLOUR_GRADING;
            set(7, g.saturation(), g.contrast(), g.brightness(), g.temperature());
            rgb(8, g.tinted.get() ? g.tint.get() : 0xFFFFFFFF, g.tinted.get() ? g.tintAmount.get() / 100f : 0);
            float clear = g.vignetteSize.get() / 100f;
            set(9, g.vignette(), clear, Math.max(0.15f, 1.15f - clear), 1);
        }
        if (depth && Modules.ATMOSPHERE.enabled() && Modules.ATMOSPHERE.skyTinted.get()) {
            rgb(10, Modules.ATMOSPHERE.skyTint.get(), Modules.ATMOSPHERE.skyAmount.get() / 100f);
        }
        v[11 * 4 + 1] = glow != null || Modules.COLOUR_GRADING.enabled() ? 1 : 0;

        GpuTextureView picture = scene.color();
        if (look) {
            GpuTextureView a = glow != null ? glow : picture, b = soft != null ? soft : picture;
            if (streak) {
                work.size(w, h);
                Post.draw(composite, work, v, picture, scene.depth(), Post.mainDepth(), a, b);
                picture = work.color();
            } else {
                Post.drawToMain(composite, v, picture, scene.depth(), Post.mainDepth(), a, b);
            }
        }
        if (streak) {
            var mod = Modules.MOTION_BLUR;
            Quality q = mod.quality.get();
            // The shutter is open for a share of 1/30 s whatever the frame rate, so the blur is as
            // long at 240 fps as at 60; the path found is one frame's, hence the ratio.
            float shutter = mod.strength.get() / 100f / 30f;
            clear();
            set(0, 1f / w, 1f / h, q == Quality.LOW ? 6 : q == Quality.MEDIUM ? 10 : 16, (float) w / h);
            set(1, Post.zeroToOne() ? 1 : 0, 0.07f, (now / 1_000_000L % 1000L) / 1000f, 1);
            set(2, hand);
            v[12] = Math.min(shutter / dt, 4f);
            back.get(v, 16);
            Post.drawToMain(motion, v, picture, scene.depth(), Post.mainDepth());
        }
        Post.endFrame();
    }

    /**
     * How the camera got here from where it was a frame ago: a matrix from this frame's clip space
     * to that one's. Null if there is no usable frame before this one.
     */
    private static Matrix4f camera(float dt) {
        if (!Modules.MOTION_BLUR.enabled()) {
            lastCamera = null;
            return null;
        }
        var cam = View.camera();
        Vec3 pos = cam.position();
        // Positions are kept relative to the camera, so the matrix holds its turn only and the move goes in between.
        Matrix4f now = Post.projection(View.fov, 0.05f, Post.worldFar())
                .mul(new Matrix4f().rotation(new Quaternionf(cam.rotation()).conjugate()));
        Matrix4f before = lastCamera;
        Vec3 from = lastPos;
        lastCamera = now;
        lastPos = pos;
        // Not across a pause, a teleport or a respawn.
        if (before == null || dt <= 0 || dt > 0.25f || pos.distanceToSqr(from) > 64) return null;
        return new Matrix4f(before)
                .translate((float) (pos.x - from.x), (float) (pos.y - from.y), (float) (pos.z - from.z))
                .mul(new Matrix4f(now).invert());
    }

    private static int levels(Quality q) {
        return q == Quality.LOW ? 3 : q == Quality.MEDIUM ? 4 : 5;
    }

    /** Bright parts of the picture, shrunk level by level and added back together on the way up. */
    private static GpuTextureView bloom() {
        var mod = Modules.BLOOM;
        Quality q = mod.quality.get();
        int n = levels(q);
        int w = scene.width() / (q == Quality.LOW ? 4 : 2), h = scene.height() / (q == Quality.LOW ? 4 : 2);
        float threshold = mod.threshold.get() / 100f;

        Post.Target in = scene;
        int made = 0;
        for (int i = 0; i < n && w >= 4 && h >= 4; i++, w /= 2, h /= 2) {
            bloom[i].size(w, h);
            clear();
            set(0, 1f / in.width(), 1f / in.height(), threshold, 0.25f);
            // Every level is stored at 1/n, so what they add up to is their average and fits in eight bits.
            v[4] = i == 0 ? 1f / n : 0;
            Post.draw(down, bloom[i], v, in.color());
            in = bloom[i];
            made++;
        }
        for (int i = made - 2; i >= 0; i--) {
            clear();
            set(0, 1f / bloom[i + 1].width(), 1f / bloom[i + 1].height(), mod.spread.get() / 100f, 0);
            Post.draw(up, bloom[i], v, bloom[i + 1].color());
        }
        return made == 0 ? null : bloom[0].color();
    }

    /** A half-size blurred picture for depth of field, each pixel blurred by how far out of focus it is. */
    private static GpuTextureView focus(int w, int h, float dt, float[] world, float[] hand) {
        var mod = Modules.DEPTH_OF_FIELD;
        // Eased in dioptres (1 / distance), the way an eye or a lens refocuses: quick up close, gentle far away.
        float target = 1f / Math.max(mod.target(), 0.5f);
        focus += (target - focus) * (1f - (float) Math.exp(-dt * mod.speed.get() * 1.5f));

        dofA.size(w / 2, h / 2);
        dofB.size(w / 2, h / 2);
        clear();
        set(1, world);
        set(2, hand);
        set(3, 1, 1, 0, 0);
        set(4, focus, mod.strength.get() / 100f * 8f, mod.near.get() ? 1 : 0, 1);
        Post.draw(coc, dofA, v, scene.color(), scene.depth(), Post.mainDepth());

        Quality q = mod.quality.get();
        clear();
        // The radius is in half-size texels and follows the picture's height.
        set(0, 1f / dofA.width(), 1f / dofA.height(), h / 1080f * (q == Quality.LOW ? 5f : 7f),
                q == Quality.LOW ? 8 : q == Quality.MEDIUM ? 14 : 22);
        Post.draw(blur, dofB, v, dofA.color());
        return dofB.color();
    }

    private static void clear() {
        Arrays.fill(v, 0f);
    }

    private static void set(int i, float x, float y, float z, float w) {
        v[i * 4] = x;
        v[i * 4 + 1] = y;
        v[i * 4 + 2] = z;
        v[i * 4 + 3] = w;
    }

    private static void set(int i, float[] p) {
        set(i, p[0], p[1], p[2], p[3]);
    }

    private static void rgb(int i, int argb, float a) {
        set(i, ((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f, a);
    }

    /** Gives the pictures back once every effect is off. */
    private static void release() {
        if (!holding) return;
        holding = false;
        lastCamera = null;
        scene.free();
        work.free();
        dofA.free();
        dofB.free();
        for (Post.Target t : bloom) if (t != null) t.free();
    }

    private static void fail(RuntimeException e) {
        failed = true;
        AllerClient.LOG.error("Effects failed and are off until the game restarts", e);
        Toasts.info("Effects are off", "Something went wrong drawing them. See the log");
    }
}
