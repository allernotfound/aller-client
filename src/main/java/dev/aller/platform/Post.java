package dev.aller.platform;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.aller.AllerClient;
import net.minecraft.client.renderer.MappableRingBuffer;
import org.joml.Matrix4f;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
//? if <26.1 {
/*import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalInt;
*///?} else {
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import net.minecraft.resources.Identifier;
import java.util.Optional;
//?}

/**
 * Full-screen passes over the world's picture, for the effect mods. Each pass is a pipeline of its
 * own drawn into a render target through the same device API vanilla's post effects use, so it runs
 * on either graphics backend and beside Sodium and Iris. What the passes do is decided in
 * {@code feature/Effects}; this is only the version-specific plumbing.
 */
public final class Post {
    private Post() {}

    /** Every pass shader reads its parameters from one block of this many vec4s. */
    public static final int VECTORS = 12;

    /** A fragment shader in {@code shaders/post} with the textures it samples. */
    public static final class Pass {
        final String name;
        final RenderPipeline pipeline;
        final String[] samplers;
        // One buffer per draw in a frame: a backend that records its commands would otherwise see
        // only the last values written.
        final List<MappableRingBuffer> slots = new ArrayList<>();
        int used;
        Boolean valid;

        Pass(String name, boolean additive, String[] samplers) {
            this.name = name;
            this.samplers = samplers;
            //? if <26.1 {
            /*RenderPipeline.Builder b = RenderPipeline.builder()
                    .withLocation(Ids.of("pipeline/post_" + name + (additive ? "_add" : "")))
                    .withVertexShader(Ids.of("post/quad"))
                    .withFragmentShader(Ids.of("post/" + name))
                    .withUniform("Fx", UniformType.UNIFORM_BUFFER)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS);
            for (String s : samplers) b.withSampler(s);
            if (additive) b.withBlend(BlendFunction.ADDITIVE);
            *///?} else {
            BindGroupLayout.Builder layout = BindGroupLayout.builder();
            for (String s : samplers) layout.withSampler(s);
            layout.withUniform("Fx", UniformType.UNIFORM_BUFFER);
            RenderPipeline.Builder b = RenderPipeline.builder()
                    .withLocation(Ids.of("pipeline/post_" + name + (additive ? "_add" : "")))
                    .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
                    .withFragmentShader(Ids.of("post/" + name))
                    .withBindGroupLayout(layout.build())
                    .withCull(false)
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES);
            if (additive) b.withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE));
            //?}
            pipeline = b.build();
        }

        /** Whether the shader compiled. Checked once, on first use, when the resources are loaded. */
        boolean valid() {
            if (valid == null) {
                try {
                    valid = RenderSystem.getDevice().precompilePipeline(pipeline).isValid();
                } catch (RuntimeException e) {
                    valid = false;
                }
                if (!valid) AllerClient.LOG.warn("Effect shader '{}' did not compile; the effect mods are off", name);
            }
            return valid;
        }

        MappableRingBuffer slot() {
            if (used == slots.size()) {
                int n = used;
                slots.add(new MappableRingBuffer(() -> "Aller Client post " + name + " " + n, 130, VECTORS * 16));
            }
            return slots.get(used++);
        }
    }

    private static final List<Pass> PASSES = new ArrayList<>();

    public static Pass pass(String name, String... samplers) {
        return register(new Pass(name, false, samplers));
    }

    /** A pass whose output is added to what the target already holds. */
    public static Pass additive(String name, String... samplers) {
        return register(new Pass(name, true, samplers));
    }

    private static Pass register(Pass p) {
        PASSES.add(p);
        return p;
    }

    public static boolean usable(Pass... passes) {
        boolean ok = true;
        for (Pass p : passes) ok &= p.valid();
        return ok;
    }

    /** Call once the frame's passes are drawn: moves every buffer used on to its next copy. */
    public static void endFrame() {
        for (Pass p : PASSES) {
            for (int i = 0; i < p.used; i++) p.slots.get(i).rotate();
            p.used = 0;
        }
    }

    /** A colour picture, with a depth one beside it if asked for, kept at whatever size is asked each frame. */
    public static final class Target {
        private final String label;
        private final boolean depth;
        private TextureTarget rt;

        public Target(String label, boolean depth) {
            this.label = label;
            this.depth = depth;
        }

        /** @return true if the picture was (re)made and so holds nothing yet */
        public boolean size(int w, int h) {
            w = Math.max(1, w);
            h = Math.max(1, h);
            if (rt != null && rt.width == w && rt.height == h) return false;
            if (rt == null) {
                //? if <26.1 {
                /*rt = new TextureTarget("Aller Client " + label, w, h, depth);
                *///?} else {
                rt = new TextureTarget("Aller Client " + label, w, h, depth, GpuFormat.RGBA8_UNORM);
                //?}
            } else {
                rt.resize(w, h);
            }
            //? if <26.1 {
            /*rt.setFilterMode(FilterMode.LINEAR);
            *///?}
            return true;
        }

        public int width() {
            return rt.width;
        }

        public int height() {
            return rt.height;
        }

        public GpuTextureView color() {
            return rt.getColorTextureView();
        }

        public GpuTextureView depth() {
            return rt.getDepthTextureView();
        }

        public void free() {
            if (rt != null) rt.destroyBuffers();
            rt = null;
        }
    }

    public static int width() {
        return Mc.mainTarget().width;
    }

    public static int height() {
        return Mc.mainTarget().height;
    }

    public static GpuTextureView mainDepth() {
        return Mc.mainTarget().getDepthTextureView();
    }

    /** Copies the game's picture into a target of the same size. */
    public static void grabColor(Target to) {
        RenderTarget main = Mc.mainTarget();
        RenderSystem.getDevice().createCommandEncoder()
                .copyTextureToTexture(main.getColorTexture(), to.rt.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);
    }

    public static void grabDepth(Target to) {
        to.rt.copyDepthFrom(Mc.mainTarget());
    }

    /** Draws a pass over the whole of a target. */
    public static void draw(Pass pass, Target out, float[] vectors, GpuTextureView... textures) {
        draw(pass, out.color(), vectors, textures);
    }

    /** Draws a pass over the game's own picture. */
    public static void drawToMain(Pass pass, float[] vectors, GpuTextureView... textures) {
        draw(pass, Mc.mainTarget().getColorTextureView(), vectors, textures);
    }

    private static void draw(Pass pass, GpuTextureView out, float[] v, GpuTextureView[] textures) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        MappableRingBuffer ring = pass.slot();
        //? if <26.1 {
        /*try (GpuBuffer.MappedView view = encoder.mapBuffer(ring.currentBuffer(), false, true)) {
        *///?} else {
        try (GpuBufferSlice.MappedView view = ring.currentBuffer().map(false, true)) {
        //?}
            Std140Builder b = Std140Builder.intoBuffer(view.data());
            for (int i = 0; i < VECTORS; i++) b.putVec4(v[i * 4], v[i * 4 + 1], v[i * 4 + 2], v[i * 4 + 3]);
        }
        //? if <26.1 {
        /*GpuBuffer quad = RenderSystem.getQuadVertexBuffer();
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer index = indices.getBuffer(6);
        try (RenderPass rp = encoder.createRenderPass(() -> "Aller Client post " + pass.name, out, OptionalInt.empty())) {
            rp.setPipeline(pass.pipeline);
            rp.setUniform("Fx", ring.currentBuffer());
            rp.setVertexBuffer(0, quad);
            rp.setIndexBuffer(index, indices.type());
            for (int i = 0; i < pass.samplers.length; i++) rp.bindSampler(pass.samplers[i], textures[i]);
            rp.drawIndexed(0, 0, 6, 1);
        }
        *///?} else {
        try (RenderPass rp = encoder.createRenderPass(() -> "Aller Client post " + pass.name, out, Optional.empty())) {
            rp.setPipeline(pass.pipeline);
            rp.setUniform("Fx", ring.currentBuffer());
            for (int i = 0; i < pass.samplers.length; i++) {
                // Depth is read texel for texel; colour is always filtered.
                boolean depth = pass.samplers[i].contains("Depth");
                rp.bindTexture(pass.samplers[i], textures[i],
                        RenderSystem.getSamplerCache().getClampToEdge(depth ? FilterMode.NEAREST : FilterMode.LINEAR));
            }
            rp.draw(3, 1, 0, 0);
        }
        //?}
    }

    /**
     * How to turn a depth value back into a distance: {@code z / (x * depth + y)}, with the value an
     * untouched pixel holds in {@code w}. 26.1 draws with the depth range reversed.
     */
    public static float[] depthParams(float near, float far) {
        //? if <26.1 {
        /*Matrix4f m = new Matrix4f().setPerspective(1f, 1f, near, far);
        return new float[] {2f, m.m22() - 1f, m.m32(), 1f};
        *///?} else {
        boolean zeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        Matrix4f m = new Matrix4f().setPerspective(1f, 1f, far, near, zeroToOne);
        return zeroToOne ? new float[] {1f, m.m22(), m.m32(), 0f} : new float[] {2f, m.m22() - 1f, m.m32(), 0f};
        //?}
    }

    /** Whether a depth value is the clip-space z itself rather than half of it plus a half. */
    public static boolean zeroToOne() {
        //? if <26.1 {
        /*return false;
        *///?} else {
        return RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        //?}
    }

    /** The projection the world is drawn with, without view bobbing. */
    public static Matrix4f projection(float fovDegrees, float near, float far) {
        float fov = (float) Math.toRadians(fovDegrees), aspect = (float) width() / height();
        //? if <26.1 {
        /*return new Matrix4f().setPerspective(fov, aspect, near, far);
        *///?} else {
        return new Matrix4f().setPerspective(fov, aspect, far, near, zeroToOne());
        //?}
    }

    /** The far plane the world was drawn with this frame. */
    public static float worldFar() {
        //? if <26.1 {
        /*return Mc.mc().gameRenderer.getDepthFar();
        *///?} else {
        return Mc.mc().gameRenderer.gameRenderState().levelRenderState.cameraRenderState.depthFar;
        //?}
    }

    private static boolean irisLooked;
    private static Object iris;
    private static Method irisInUse;

    /** True while an Iris shader pack draws the world: its depth is then not the game's. */
    public static boolean shaderPack() {
        if (!irisLooked) {
            irisLooked = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                iris = api.getMethod("getInstance").invoke(null);
                irisInUse = api.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException | LinkageError e) {
                irisInUse = null;
            }
        }
        if (irisInUse == null) return false;
        try {
            return (Boolean) irisInUse.invoke(iris);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
