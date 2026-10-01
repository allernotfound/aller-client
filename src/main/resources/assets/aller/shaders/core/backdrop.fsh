#version 150

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 accent;
in vec2 pixel;
in float time;
in float cell;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        sum += amp * noise(p);
        p = p * 2.03 + vec2(17.0, 9.0);
        amp *= 0.5;
    }
    return sum;
}

// 5x5 bitmap glyphs packed into the low 25 bits of an int.
float glyph(int bits, vec2 p) {
    p = floor(p * vec2(-4.0, 4.0) + 2.5);
    if (clamp(p.x, 0.0, 4.0) == p.x && clamp(p.y, 0.0, 4.0) == p.y) {
        int index = int(round(p.x) + 5.0 * round(p.y));
        if (((bits >> index) & 1) == 1) {
            return 1.0;
        }
    }
    return 0.0;
}

void main() {
    // Quantise to character cells so the whole field is evaluated once per glyph.
    vec2 cellId = floor(pixel / cell);
    vec2 inCell = fract(pixel / cell) * 2.0 - 1.0;

    // Domain-warped noise: slow drifting clouds rather than uniform static.
    vec2 p = cellId * 0.022;
    float t = time * 0.035;
    vec2 warp = vec2(fbm(p + vec2(t, -t * 0.6)), fbm(p + vec2(5.2 - t * 0.4, 1.3 + t)));
    float n = fbm(p * 1.4 + warp * 2.2 + vec2(-t * 0.5, t * 0.3));
    float lum = smoothstep(0.34, 0.78, n);

    // Brightness picks the character: sparse dots in the dark, dense glyphs in the highlights.
    int bits = 4096;                       // .
    if (lum > 0.14) bits = 65600;          // :
    if (lum > 0.28) bits = 163153;         // *
    if (lum > 0.42) bits = 15255086;       // o
    if (lum > 0.56) bits = 13121101;       // &
    if (lum > 0.70) bits = 15252014;       // 8
    if (lum > 0.82) bits = 13195790;       // @
    if (lum > 0.92) bits = 11512810;       // #
    float ink = glyph(bits, inCell);

    vec3 base = vec3(0.027, 0.024, 0.043);
    vec3 glow = accent.rgb * lum * lum * 0.16;
    vec3 chars = mix(accent.rgb * 0.55, accent.rgb * 1.15 + 0.12, lum) * ink * (0.10 + 0.90 * lum);

    fragColor = vec4(base + (glow + chars) * accent.a, 1.0) * ColorModulator;
}
