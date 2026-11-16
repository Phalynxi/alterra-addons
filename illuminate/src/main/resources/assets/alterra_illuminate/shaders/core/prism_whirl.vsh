#version 150

in vec3 Position;   // relative to the camera
in vec2 UV0;        // x: round the funnel (0..1), y: down it (0 at the surface, 1 at the bottom)
in vec4 Color;      // rgb: the outward direction (packed 0..1) , a: strength

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 uv;
out vec3 rel;
out vec3 outward;
out float strength;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    uv = UV0; rel = Position; outward = Color.rgb * 2.0 - 1.0; strength = Color.a;
}
