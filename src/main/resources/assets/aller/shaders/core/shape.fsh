#version 150

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
in vec2 local;
in vec2 halfSize;
in vec2 params;
in float mode;
in float kind;

out vec4 fragColor;

float roundedBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

// Regular polygon with one vertex pointing up; r is the distance from the centre to an edge.
float polygon(vec2 p, float r, float n) {
    float sector = 6.28318531 / n;
    // Angle measured clockwise from straight up (y grows downwards), folded onto the nearest edge.
    float a = mod(atan(p.x, -p.y), sector) - sector * 0.5;
    vec2 q = length(p) * vec2(cos(a), sin(a));
    float he = r * tan(sector * 0.5);
    return length(q - vec2(r, clamp(q.y, -he, he))) * sign(q.x - r);
}

// Five-pointed star, r from the centre to a tip.
float star(vec2 p, float r) {
    const vec2 k1 = vec2(0.809016994375, -0.587785252292);
    const vec2 k2 = vec2(-k1.x, k1.y);
    p.y = -p.y;
    p.x = abs(p.x);
    p -= 2.0 * max(dot(k1, p), 0.0) * k1;
    p -= 2.0 * max(dot(k2, p), 0.0) * k2;
    p.x = abs(p.x);
    p.y -= r;
    vec2 ba = 0.45 * vec2(-k1.y, k1.x) - vec2(0.0, 1.0);
    float h = clamp(dot(p, ba) / dot(ba, ba), 0.0, r);
    return length(p - ba * h) * sign(p.y * ba.x - p.x * ba.y);
}

void main() {
    float radius = min(params.x, min(halfSize.x, halfSize.y));
    float sides = floor(kind * 8.0 + 0.5);
    float size = min(halfSize.x, halfSize.y);
    float d;
    if (sides < 0.5) {
        d = roundedBox(local, halfSize, radius);
    } else if (sides < 1.5) {
        d = star(local, size - radius) - radius;
    } else {
        d = polygon(local, (size - radius) * cos(3.14159265 / sides), sides) - radius;
    }
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
