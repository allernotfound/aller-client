package dev.aller.platform;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.aller.AllerClient;
import net.minecraft.client.renderer.RenderPipelines;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
//? if <26.1 {
/*import com.mojang.blaze3d.vertex.VertexFormat;
*///?}

/**
 * The three shader pipelines behind every Aller pixel. They extend vanilla's own GUI snippets and
 * only swap the shaders and vertex layout, so blending, depth and uniform bindings stay whatever the
 * running Minecraft version (and graphics backend) expects.
 */
public final class Pipelines {
    private Pipelines() {}

    /** Rounded rectangles, strokes and shadows, evaluated analytically per pixel. */
    public static final RenderPipeline SHAPE = build(RenderPipelines.GUI_SNIPPET, "shape");
    /** Signed-distance-field text. */
    public static final RenderPipeline TEXT = build(RenderPipelines.GUI_TEXTURED_SNIPPET, "text");
    /** The animated ASCII noise field behind the main menu. */
    public static final RenderPipeline BACKDROP = build(RenderPipelines.GUI_SNIPPET, "backdrop");

    private static RenderPipeline build(RenderPipeline.Snippet base, String name) {
        RenderPipeline.Builder b = RenderPipeline.builder(base)
                .withLocation(Ids.of("pipeline/" + name))
                .withVertexShader(Ids.of("core/" + name))
                .withFragmentShader(Ids.of("core/" + name));
        //? if <26.1 {
        /*b.withVertexFormat(DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS);
        *///?} else {
        b.withVertexBinding(0, DefaultVertexFormat.ENTITY);
        //?}
        return b.build();
    }

    private static boolean preloaded;

    /** Whether Aller's shaders are usable yet. False only very early in startup, or if they failed to compile. */
    public static boolean ready() {
        return preloaded || !Mc.loadingOverlay();
    }

    /**
     * Compiles the pipelines straight from the mod jar, before the first resource reload, so the
     * startup splash can already draw with them. Vanilla does the same for its own GUI shaders.
     */
    public static void preload() {
        try {
            GpuDevice device = RenderSystem.getDevice();
            boolean ok = true;
            for (RenderPipeline p : new RenderPipeline[] {SHAPE, TEXT, BACKDROP}) {
                ok &= device.precompilePipeline(p, Pipelines::source).isValid();
            }
            preloaded = ok;
            if (!ok) AllerClient.LOG.warn("Aller Client shaders did not compile at startup; the splash will be plain");
        } catch (RuntimeException e) {
            AllerClient.LOG.warn("Could not preload Aller Client shaders", e);
        }
    }

    //? if <26.1 {
    /*private static String source(net.minecraft.resources.ResourceLocation id, ShaderType type) {
    *///?} else {
    private static String source(net.minecraft.resources.Identifier id, ShaderType type) {
    //?}
        var file = type.idConverter().idToFile(id);
        try (InputStream in = AllerClient.class.getResourceAsStream("/assets/" + file.getNamespace() + "/" + file.getPath())) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /** Forces class initialisation so a broken pipeline definition fails at startup, not mid-frame. */
    public static void init() {
        AllerClient.LOG.debug("Pipelines ready: {} {} {}", SHAPE, TEXT, BACKDROP);
    }
}
