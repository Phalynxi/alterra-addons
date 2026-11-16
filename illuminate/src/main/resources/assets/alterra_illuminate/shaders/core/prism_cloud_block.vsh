#version 150

in vec3 Position;
in vec4 Color;     // alpha: how dark a storm has made it
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float RMax;

out vec3 worldNormal;
out float dark;
out float dist;
out float up;

void main() {
    vec4 v = ModelViewMat * vec4(Position, 1.0);
    dist = length(v.xyz);
    v.xyz *= min(1.0, RMax / max(dist, 1.0));
    gl_Position = ProjMat * v;
    worldNormal = Normal;
    dark = Color.a;
    up = Color.r;   // how high in its block: the top is lit, the underside is in shade
}
