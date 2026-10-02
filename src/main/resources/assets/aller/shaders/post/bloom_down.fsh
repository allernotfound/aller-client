#version 150

uniform sampler2D InSampler;

layout(std140) uniform Fx {
    vec4 U[12];
};

in vec2 texCoord;

out vec4 fragColor;

// U[0]: xy input texel, z threshold, w knee. U[1].x: scale of the first (bright-pass) level, 0 on the others.
void main() {
    vec2 t = U[0].xy;
    // Four bilinear taps, each between four texels: a 4x4 box for the price of four reads.
    vec3 c = texture(InSampler, texCoord + t * vec2(-1.0, -1.0)).rgb
           + texture(InSampler, texCoord + t * vec2(1.0, -1.0)).rgb
           + texture(InSampler, texCoord + t * vec2(-1.0, 1.0)).rgb
           + texture(InSampler, texCoord + t * vec2(1.0, 1.0)).rgb;
    c *= 0.25;
    if (U[1].x > 0.0) {
        float br = max(c.r, max(c.g, c.b));
        float knee = U[0].w;
        float soft = clamp(br - U[0].z + knee, 0.0, 2.0 * knee);
        soft = soft * soft / (4.0 * knee + 0.0001);
        c *= max(soft, br - U[0].z) / max(br, 0.0001) * U[1].x;
    }
    fragColor = vec4(c, 1.0);
}
