#version 150

uniform sampler2D InSampler;

layout(std140) uniform Fx {
    vec4 U[12];
};

in vec2 texCoord;

out vec4 fragColor;

// U[0]: xy input texel, z largest radius in texels, w taps.
void main() {
    vec4 centre = texture(InSampler, texCoord);
    vec2 r = U[0].xy * U[0].z * centre.a;
    vec3 acc = centre.rgb;
    float total = 1.0;
    int taps = int(U[0].w);
    for (int i = 0; i < 24; i++) {
        if (i >= taps) break;
        // A golden-angle spiral covers the disc evenly with any number of taps.
        float a = float(i) * 2.3999632;
        float d = sqrt((float(i) + 0.5) / float(taps));
        vec4 s = texture(InSampler, texCoord + vec2(cos(a), sin(a)) * d * r);
        // In-focus neighbours barely count, so a sharp edge does not smear into the blur behind it.
        float w = s.a + 0.03;
        acc += s.rgb * w;
        total += w;
    }
    fragColor = vec4(acc / total, centre.a);
}
