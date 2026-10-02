#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// Every Aller primitive packs its parameters into the stock entity vertex format, so no custom
// uniforms are needed and the same pipeline works on any graphics backend.
in vec3 Position;
in vec4 Color;
in vec2 UV0;     // position relative to the shape centre, in GUI pixels
in ivec2 UV1;    // half size * 4
in ivec2 UV2;    // (corner radius, stroke width or shadow blur) * 4
in vec3 Normal;  // x: 0 fill, 0.5 stroke, 1 shadow; y: outline kind / 8 (0 box, 1 star, 3+ polygon sides);
                 // z: pixel-art cell size * 16 / 127 (0 for a smooth shape)

out vec4 vertexColor;
out vec2 local;
out vec2 halfSize;
out vec2 params;
out float mode;
out float kind;
out float pixel;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    local = UV0;
    halfSize = vec2(UV1) / 4.0;
    params = vec2(UV2) / 4.0;
    mode = Normal.x;
    kind = Normal.y;
    pixel = Normal.z;
}
