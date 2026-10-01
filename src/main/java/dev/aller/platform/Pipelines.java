package dev.aller.platform;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.aller.AllerClient;
import net.minecraft.client.renderer.RenderPipelines;
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

    /** Forces class initialisation so a broken pipeline definition fails at startup, not mid-frame. */
    public static void init() {
        AllerClient.LOG.debug("Pipelines ready: {} {} {}", SHAPE, TEXT, BACKDROP);
    }
}
