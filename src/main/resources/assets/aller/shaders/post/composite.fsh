#version 150

uniform sampler2D SceneSampler;
uniform sampler2D SceneDepthSampler;
uniform sampler2D HandDepthSampler;
uniform sampler2D BloomSampler;
uniform sampler2D DofSampler;

layout(std140) uniform Fx {
    vec4 U[12];
};

in vec2 texCoord;

out vec4 fragColor;

// One pass applies every enabled effect, so each costs its own texture reads and nothing else.
// U[0]  xy texel, z aspect, w time
// U[1]  world depth, U[2] hand depth (see Post.depthParams)
// U[3]  x depth usable, y hand depth usable, z sharpen, w bloom strength
// U[4]  x 1/focus distance, y aperture, z blur nearer than focus, w depth of field on
// U[5]  rgb rim colour, a strength
// U[6]  x rim width in pixels, y range in blocks, z lit from above, w light the held item
// U[7]  saturation, contrast, brightness, temperature
// U[8]  rgb tint, a amount
// U[9]  x vignette strength, y where it starts, z softness, w grading on
// U[10] rgb sky tint, a amount
// U[11] y dither, z rim taps, w rim checks each gap twice

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

float dist(float d, vec4 p) {
    float den = p.x * d + p.y;
    return abs(den) < 0.0000001 ? 1000000.0 : min(p.z / den, 1000000.0);
}

/** Distance to what is drawn at a pixel; hand is set to 1 where the first-person hand covers it. */
float distAt(vec2 uv, out float hand) {
    hand = 0.0;
    if (U[3].y > 0.5) {
        float h = texture(HandDepthSampler, uv).r;
        if (abs(h - U[2].w) > 0.000001) {
            hand = 1.0;
            return dist(h, U[2]);
        }
    }
    return dist(texture(SceneDepthSampler, uv).r, U[1]);
}

/** 1 where what is drawn at p is much further away than dc. A hand pixel only asks whether p is still hand. */
float beyond(vec2 p, float dc, float hand) {
    float dn;
    if (hand > 0.5) {
        float h = texture(HandDepthSampler, p).r;
        dn = abs(h - U[2].w) > 0.000001 ? dist(h, U[2]) : 1000000.0;
    } else {
        dn = dist(texture(SceneDepthSampler, p).r, U[1]);
    }
    return smoothstep(0.08, 0.35, (dn - dc) / max(dc, 0.05));
}

/**
 * Light on the inside of an outline: the share of a disc round the pixel that lies beyond the edge,
 * which is half at the edge itself and falls away smoothly behind it.
 */
float rim(vec2 uv, float dc, float hand) {
    if (hand > 0.5 && U[6].w < 0.5) return 0.0;
    float fade = hand > 0.5 ? 1.0 : 1.0 - smoothstep(U[6].y * 0.5, U[6].y, dc);
    if (fade <= 0.0) return 0.0;
    // Narrower with distance, as a light of one size in the world would be.
    float width = U[6].x * (hand > 0.5 ? 1.2 : clamp(4.0 / dc, 0.35, 1.2));
    vec2 px = U[0].xy * width;
    int taps = int(U[11].z);
    float acc = 0.0;
    float total = 0.0;
    for (int i = 0; i < 16; i++) {
        if (i >= taps) break;
        float a = float(i) * 2.3999632;
        float r = sqrt((float(i) + 0.5) / float(taps));
        vec2 dir = vec2(cos(a), sin(a));
        float w = U[6].z > 0.5 ? clamp(dir.y * 0.75 + 0.5, 0.0, 1.0) : 1.0;
        float e = beyond(uv + dir * px * r, dc, hand);
        // The gap must be wider than a pixel or two, or distant leaves and grass turn to glitter.
        if (U[11].w > 0.5 && hand < 0.5 && e > 0.0) e = min(e, beyond(uv + dir * px * (r + 0.7), dc, hand));
        acc += w * e;
        total += w;
    }
    return fade * pow(clamp(acc / max(total, 0.001) * 2.0, 0.0, 1.0), 0.7);
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec2 uv = texCoord;
    vec3 col = texture(SceneSampler, uv).rgb;

    if (U[3].z > 0.0) {
        // Contrast-adaptive sharpening: less where the neighbourhood is already near black or white.
        vec2 t = U[0].xy;
        vec3 n = texture(SceneSampler, uv + vec2(0.0, t.y)).rgb;
        vec3 s = texture(SceneSampler, uv - vec2(0.0, t.y)).rgb;
        vec3 e = texture(SceneSampler, uv + vec2(t.x, 0.0)).rgb;
        vec3 w = texture(SceneSampler, uv - vec2(t.x, 0.0)).rgb;
        float lo = min(luma(col), min(min(luma(n), luma(s)), min(luma(e), luma(w))));
        float hi = max(luma(col), max(max(luma(n), luma(s)), max(luma(e), luma(w))));
        float amp = sqrt(clamp(min(lo, 1.0 - hi) / max(hi, 0.0001), 0.0, 1.0));
        float k = -amp * mix(0.08, 0.2, U[3].z);
        // Kept within what the neighbourhood already holds: blocky textures would otherwise get a bright
        // line along every texel edge.
        col = clamp((col + (n + s + e + w) * k) / (1.0 + 4.0 * k),
                min(col, min(min(n, s), min(e, w))), max(col, max(max(n, s), max(e, w))));
    }

    if (U[3].x > 0.5) {
        float hand;
        float dc = distAt(uv, hand);

        if (U[10].a > 0.0 && hand < 0.5 && abs(texture(SceneDepthSampler, uv).r - U[1].w) < 0.000001) {
            col = mix(col, clamp(U[10].rgb * luma(col) * 1.6, 0.0, 1.0), U[10].a);
        }

        if (U[4].w > 0.5 && hand < 0.5) {
            float v = 1.0 / max(dc, 0.05) - U[4].x;
            float coc = (v < 0.0 || U[4].z > 0.5) ? clamp(abs(v) * U[4].y, 0.0, 1.0) : 0.0;
            col = mix(col, texture(DofSampler, uv).rgb, smoothstep(0.0, 0.5, coc));
        }

        if (U[5].a > 0.0) {
            vec3 light = U[5].rgb * rim(uv, dc, hand) * U[5].a;
            col = 1.0 - (1.0 - col) * (1.0 - light);
        }
    }

    if (U[3].w > 0.0) col += texture(BloomSampler, uv).rgb * U[3].w;

    if (U[9].w > 0.5) {
        col *= U[7].z;
        col = (col - 0.5) * U[7].y + 0.5;
        col = mix(vec3(luma(col)), col, U[7].x);
        col.r *= 1.0 + U[7].w * 0.15;
        col.b *= 1.0 - U[7].w * 0.15;
        vec3 tint = U[8].rgb / max(max(U[8].r, max(U[8].g, U[8].b)), 0.01);
        col = mix(col, col * tint, U[8].a);
        vec2 q = (uv - 0.5) * vec2(mix(1.0, U[0].z, 0.5), 1.0);
        col *= 1.0 - U[9].x * smoothstep(U[9].y, U[9].y + U[9].z, length(q) * 1.4142);
        col = max(col, 0.0);
    }

    // A little noise hides the banding eight-bit bloom and vignette gradients would otherwise show.
    col += (hash(gl_FragCoord.xy + fract(U[0].w) * 61.0) - 0.5) * U[11].y / 255.0;
    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
