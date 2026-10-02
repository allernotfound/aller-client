#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// A picture in a rounded box. Packed like the shapes, with the part of the picture to show in
// place of the shape's mode.
in vec3 Position;
in vec4 Color;
in vec2 UV0;     // position relative to the box centre, in GUI pixels
in ivec2 UV1;    // half size * 4
in ivec2 UV2;    // corner radius * 4
in vec3 Normal;  // x, y: how much of the picture's width and height is shown (the rest is cropped evenly);
                 // z: pixel-art cell size * 16 / 127 (0 for smooth corners)

out vec4 vertexColor;
out vec2 local;
out vec2 halfSize;
out float radius;
out vec2 crop;
out float pixel;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    local = UV0;
    halfSize = vec2(UV1) / 4.0;
    radius = float(UV2.x) / 4.0;
    crop = Normal.xy;
    pixel = Normal.z;
}
