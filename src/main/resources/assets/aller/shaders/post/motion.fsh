#version 150

uniform sampler2D InSampler;
uniform sampler2D SceneDepthSampler;
uniform sampler2D HandDepthSampler;

layout(std140) uniform Fx {
    vec4 U[12];
};

in vec2 texCoord;

out vec4 fragColor;

// Camera motion blur. Each pixel is put back where it was on screen a frame ago (from its depth and
// how the camera moved and turned), and the picture is averaged along that path for as long as the
// shutter is open. Nothing is mixed in from earlier frames, so there is no ghosting.
// U[0]    xy texel, z taps, w aspect
// U[1]    x depth runs zero to one, y longest blur as a share of the height, z time, w hand depth usable
// U[2].w  what an untouched hand depth pixel holds
// U[3].x  shutter time over frame time
// U[4..7] clip space now -> clip space a frame ago

bool handAt(vec2 p) {
    return U[1].w > 0.5 && abs(texture(HandDepthSampler, p).r - U[2].w) > 0.000001;
}

void main() {
    vec3 col = texture(InSampler, texCoord).rgb;
    fragColor = vec4(col, 1.0);
    // The hand moves with the camera: it stays sharp and is kept out of the blur behind it.
    if (handAt(texCoord)) return;

    float d = texture(SceneDepthSampler, texCoord).r;
    vec4 before = mat4(U[4], U[5], U[6], U[7]) * vec4(texCoord * 2.0 - 1.0, U[1].x > 0.5 ? d : d * 2.0 - 1.0, 1.0);
    if (before.w <= 0.00001) return;
    vec2 path = (texCoord - (before.xy / before.w * 0.5 + 0.5)) * U[3].x;

    float len = length(path * vec2(U[0].w, 1.0));
    if (len < U[0].y) return;
    if (len > U[1].y) path *= U[1].y / len;

    // Each pixel starts its samples at a different point along the path, which turns the steps
    // between samples into fine noise instead of visible copies of the picture.
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715)) + U[1].z));
    int taps = int(U[0].z);
    float total = 1.0;
    for (int i = 0; i < 24; i++) {
        if (i >= taps) break;
        vec2 p = texCoord + path * ((float(i) + jitter) / float(taps) - 0.5);
        if (p.x < 0.0 || p.x > 1.0 || p.y < 0.0 || p.y > 1.0 || handAt(p)) continue;
        col += texture(InSampler, p).rgb;
        total += 1.0;
    }
    fragColor = vec4(col / total, 1.0);
}
