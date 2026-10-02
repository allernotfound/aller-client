#version 150

uniform sampler2D InSampler;

layout(std140) uniform Fx {
    vec4 U[12];
};

in vec2 texCoord;

out vec4 fragColor;

// U[0]: xy input texel, z spread. Drawn additively onto the next larger level.
void main() {
    vec2 t = U[0].xy * U[0].z;
    vec3 c = texture(InSampler, texCoord).rgb * 4.0;
    c += (texture(InSampler, texCoord + vec2(t.x, 0.0)).rgb + texture(InSampler, texCoord - vec2(t.x, 0.0)).rgb
        + texture(InSampler, texCoord + vec2(0.0, t.y)).rgb + texture(InSampler, texCoord - vec2(0.0, t.y)).rgb) * 2.0;
    c += texture(InSampler, texCoord + t).rgb + texture(InSampler, texCoord - t).rgb
       + texture(InSampler, texCoord + vec2(t.x, -t.y)).rgb + texture(InSampler, texCoord + vec2(-t.x, t.y)).rgb;
    fragColor = vec4(c / 16.0, 1.0);
}
