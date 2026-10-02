#version 150

uniform sampler2D InSampler;
uniform sampler2D SceneDepthSampler;
uniform sampler2D HandDepthSampler;

layout(std140) uniform Fx {
    vec4 U[12];
};

in vec2 texCoord;

out vec4 fragColor;

// U[1]: world depth, U[2]: hand depth (see Post.depthParams). U[3].y: hand depth usable.
// U[4]: x 1/focus distance, y aperture, z blur what is nearer than the focus.
float dist(float d, vec4 p) {
    float den = p.x * d + p.y;
    return abs(den) < 0.0000001 ? 1000000.0 : min(p.z / den, 1000000.0);
}

// Half-size copy of the picture with how far out of focus each pixel is in alpha.
void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    float hand = texture(HandDepthSampler, texCoord).r;
    float coc = 0.0;
    if (U[3].y < 0.5 || abs(hand - U[2].w) < 0.000001) {
        float v = 1.0 / max(dist(texture(SceneDepthSampler, texCoord).r, U[1]), 0.05) - U[4].x;
        if (v < 0.0 || U[4].z > 0.5) coc = clamp(abs(v) * U[4].y, 0.0, 1.0);
    }
    fragColor = vec4(c, coc);
}
