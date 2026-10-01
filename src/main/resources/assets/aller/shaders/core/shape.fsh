#version 150

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
in vec2 local;
in vec2 halfSize;
in vec2 params;
in float mode;

out vec4 fragColor;

float roundedBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

void main() {
    float radius = min(params.x, min(halfSize.x, halfSize.y));
    float d = roundedBox(local, halfSize, radius);
    // Size of one screen pixel in shape units: gives a one-pixel anti-aliased edge at any GUI scale.
    float px = max(length(vec2(dFdx(local.x), dFdy(local.x))), 0.0001);

    float alpha;
    if (mode > 0.75) {
        // Soft shadow: a wide falloff across the blur distance, eased so it fades out gently.
        float t = clamp(0.5 - d / (2.0 * max(params.y, 0.001)), 0.0, 1.0);
        alpha = t * t * (3.0 - 2.0 * t);
    } else if (mode > 0.25) {
        // Stroke: the band between the edge and (edge - width).
        float outer = clamp(0.5 - d / px, 0.0, 1.0);
        float inner = clamp(0.5 + (d + params.y) / px, 0.0, 1.0);
        alpha = outer * inner;
    } else {
        alpha = clamp(0.5 - d / px, 0.0, 1.0);
    }

    vec4 color = vertexColor * ColorModulator;
    color.a *= alpha;
    if (color.a < 0.002) {
        discard;
    }
    fragColor = color;
}
