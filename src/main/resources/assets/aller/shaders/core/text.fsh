#version 150

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    // The atlas stores distance to the glyph edge (0.5 = on the edge). Dividing by how fast that
    // distance changes per screen pixel yields exactly one pixel of anti-aliasing at any text size.
    float dist = texture(Sampler0, texCoord0).a;
    float width = max(fwidth(dist), 0.0001);
    float alpha = clamp((dist - 0.5) / width + 0.5, 0.0, 1.0);

    vec4 color = vertexColor * ColorModulator;
    color.a *= alpha;
    if (color.a < 0.002) {
        discard;
    }
    fragColor = color;
}
