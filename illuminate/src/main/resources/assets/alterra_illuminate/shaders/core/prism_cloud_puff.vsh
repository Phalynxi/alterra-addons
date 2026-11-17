#version 150

in vec3 Position;   // the puff's centre (before the model-view matrix)
in vec4 Color;      // alpha: how solid the puff is
in vec2 UV0;        // its horizontal and vertical radius in blocks
in vec3 Normal;     // x: baked brightness, y: storm darkness, z: a number of its own (each in -1..1)

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float RMax;   // the farthest a cloud is drawn at: farther ones are pulled in along their line of sight (the same look, never cut off by the far plane)

out vec3 wpos;          // this pixel's point on the card, relative to the camera, in world axes
flat out vec3 centre;   // the puff's centre, relative to the camera, in world axes
flat out vec2 radii;
out vec4 info;          // x: baked brightness, y: storm darkness, z: seed, w: alpha
out float dist;
out float nearFade;

void main() {
    int k = gl_VertexID & 3;
    vec2 corner = vec2((k == 1 || k == 2) ? 1.0 : -1.0, (k >= 2) ? 1.0 : -1.0);
    vec4 vc = ModelViewMat * vec4(Position, 1.0);
    dist = length(vc.xyz);
    // the ground curves away: far clouds sink below the horizon (they round off instead of ending in a line)
    float drop = dist * dist / 180000.0;
    vc.xyz -= mat3(ModelViewMat) * vec3(0.0, 1.0, 0.0) * drop;
    float R = max(UV0.x, UV0.y);
    nearFade = smoothstep(0.55 * R, 1.35 * R, dist);   // a puff the camera is at or inside fades away (the in-cloud fog takes over)
    float k2 = min(1.0, RMax / max(dist, 1.0));
    vc.xyz *= k2;
    float cth = clamp(-vc.z / max(length(vc.xyz), 1.0), 0.28, 1.0);   // how far off the view's axis the puff is: near the edges of a wide view a ball is stretched into an oval, and the card must grow with it
    vec4 v = vc; v.xy += corner * R * k2 * 1.35 / cth;   // a card big enough to hold the whole puff, lumps included, from any side (a tighter one cut clouds into rectangles that turned with the view)
    gl_Position = ProjMat * v;
    mat3 inv = transpose(mat3(ModelViewMat));    // view axes back to world axes (the model-view matrix only turns and moves)
    wpos = inv * v.xyz; centre = inv * vc.xyz; radii = UV0 * k2;
    info = vec4(Normal.x * 0.5 + 0.5, Normal.y * 0.5 + 0.5, Normal.z * 0.5 + 0.5, Color.a);
}
