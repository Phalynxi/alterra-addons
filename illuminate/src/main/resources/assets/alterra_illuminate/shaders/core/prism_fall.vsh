#version 150

in vec3 Position;   // relative to the camera
in vec2 UV0;        // x: along the lip (blocks, world, so streaks do not repeat per block), y: down the fall (0 at the lip, 1 at the foot)
in vec4 Color;      // a: how much water

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 uv;
out vec3 rel;
out float amount;

void main() { gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0); uv = UV0; rel = Position; amount = Color.a; }
