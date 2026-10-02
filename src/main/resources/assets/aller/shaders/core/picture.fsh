#version 150

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 local;
in vec2 halfSize;
in float radius;
in vec2 crop;
in float pixel;

out vec4 fragColor;

float roundedBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

void main() {
    float r = min(radius, min(halfSize.x, halfSize.y));
    // The same stepped corners as the shapes, so a picture sits flush in a pixel-art frame.
    float cell = floor(pixel * 127.0 + 0.5) / 16.0;
    float d;
    float px;
    if (cell > 0.0) {
        vec2 inward = (floor((halfSize - abs(local)) / cell) + 0.5) * cell;
        vec2 side = vec2(local.x < 0.0 ? -1.0 : 1.0, local.y < 0.0 ? -1.0 : 1.0);
        d = roundedBox((halfSize - inward) * side, halfSize, r);
        px = 0.001;
    } else {
        d = roundedBox(local, halfSize, r);
        px = max(length(vec2(dFdx(local.x), dFdy(local.x))), 0.0001);
    }

    vec2 uv = clamp(0.5 + local / halfSize * 0.5 * crop, 0.0, 1.0);
    vec4 color = texture(Sampler0, uv) * vertexColor * ColorModulator;
    color.a *= clamp(0.5 - d / px, 0.0, 1.0);
    if (color.a < 0.002) {
        discard;
    }
    fragColor = color;
}
