#version 150

in vec3 Position;   // x, z: the grid point (half blocks from the grid's corner); y unused

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform sampler2D Heights;   // per block column: r = surface height above Cam.w, g = 1 where there is water
uniform vec4 Grid;           // xy: the world block at the map's corner relative to the camera, z: the map's size in blocks
uniform vec4 Cam;
uniform sampler2D Whirl;   // a whirlpool's grid water (its surface height), if there is one near
uniform vec4 WhirlInfo;   // xy: camera relative to that grid's corner, z: 1 over its width, w: > 0.5 when there is one            // xyz: the camera's world position wrapped (for patterns), w: the base height the map's heights are measured from, minus the camera height

out vec3 rel;      // the point relative to the camera
out vec2 wpos;     // world x, z (wrapped) for the ripple pattern
out float wet;     // how much of this point is water (soft at the shore)
out float foamAmt; // white water where a fall lands

vec4 cell4(ivec2 c) { c = clamp(c, ivec2(0), ivec2(int(Grid.z) - 1)); return texelFetch(Heights, c, 0); }   // r: height, g: 1 on water, b: foam, a: the height carried out over dry ground (-9999: none near)
float cr(float a, float b, float c, float d, float t) { return b + 0.5 * t * (c - a + t * (2.0 * a - 5.0 * b + 4.0 * c - d + t * (3.0 * (b - c) + d - a))); }
float hOf(vec4 q) { return q.g > 0.5 ? q.r : q.a; }

void main() {
    vec2 g = Position.xz * 0.5;                      // in blocks from the map's corner
    vec2 f = g - 0.5; ivec2 i0 = ivec2(floor(f)); vec2 t = f - vec2(i0);
    vec4 own = cell4(ivec2(floor(g)));
    bool none = own.a < -9000.0;                     // no water anywhere near: out of sight (the carried height covers the few blocks round every pool, so this is never next to water)
    float ref = none ? 0.0 : hOf(own);
    float rows[4]; float lo = 1e9, hi = -1e9;
    for (int y = 0; y < 4; y++) {
        float v[4];
        for (int x = 0; x < 4; x++) { vec4 q = cell4(i0 + ivec2(x, y)); float hv = q.a < -9000.0 ? ref : hOf(q); if (abs(hv - ref) > 3.2) hv = ref; v[x] = hv; if (x >= 1 && x <= 2 && y >= 1 && y <= 2) { lo = min(lo, hv); hi = max(hi, hv); } }   // water at about the same level only (a pool beside a river far below is not bridged)
        rows[y] = cr(v[0], v[1], v[2], v[3], t.x);
    }
    float h = clamp(cr(rows[0], rows[1], rows[2], rows[3], t.y), lo, hi);   // never above or below the four nearest heights: no spikes
    vec4 c11 = cell4(i0 + ivec2(1, 1)), c21 = cell4(i0 + ivec2(2, 1)), c12 = cell4(i0 + ivec2(1, 2)), c22 = cell4(i0 + ivec2(2, 2));
    wet = mix(mix(c11.g, c21.g, t.x), mix(c12.g, c22.g, t.x), t.y);
    foamAmt = mix(mix(c11.b, c21.b, t.x), mix(c12.b, c22.b, t.x), t.y);
    vec3 p = vec3(Grid.x + g.x, h + Cam.w, Grid.y + g.y);
    if (none) { wet = 0.0; foamAmt = 0.0; }
    if (WhirlInfo.w > 0.5) { vec2 wg = (p.xz + WhirlInfo.xy) * WhirlInfo.z; if (wg.x > 0.0 && wg.y > 0.0 && wg.x < 1.0 && wg.y < 1.0) p.y += textureLod(Whirl, wg, 0.0).r; }   // a whirlpool pulls the surface down
    rel = p; wpos = p.xz + Cam.xz;
    gl_Position = ProjMat * ModelViewMat * vec4(p, 1.0);
}
