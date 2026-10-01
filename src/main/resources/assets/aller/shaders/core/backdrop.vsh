#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;   // accent colour; alpha is the overall intensity
in vec2 UV0;     // position in device pixels
in ivec2 UV1;    // time in centiseconds, split into two 15-bit halves
in ivec2 UV2;    // x: glyph cell size in device pixels
in vec3 Normal;

out vec4 accent;
out vec2 pixel;
out float time;
out float cell;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    accent = Color;
    pixel = UV0;
    time = (float(UV1.x) + float(UV1.y) * 32768.0) / 100.0;
    cell = float(UV2.x);
}
