#version 150

// 1.21.8 only: later versions draw post passes with vanilla's vertex-less screen triangle.
in vec3 Position;

out vec2 texCoord;

void main() {
    gl_Position = vec4(Position.xy * 2.0 - 1.0, 0.0, 1.0);
    texCoord = Position.xy;
}
