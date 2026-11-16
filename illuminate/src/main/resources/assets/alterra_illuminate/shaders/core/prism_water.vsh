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

vec2 cell(ivec2 c) { c = clamp(c, ivec2(0), ivec2(int(Grid.z) - 1)); return texelFetch(Heights, c, 0).rg; }
float cr(float a, float b, float c, float d, float t) { return b + 0.5 * t * (c - a + t * (2.0 * a - 5.0 * b + 4.0 * c - d + t * (3.0 * (b - c) + d - a))); }

void main() {
    vec2 g = Position.xz * 0.5;                      // in blocks from the map's corner
    vec2 f = g - 0.5; ivec2 i0 = ivec2(floor(f)); vec2 t = f - vec2(i0);
    // a smooth (Catmull-Rom) height through the water columns around; dry columns take the height of the water beside them, so the surface does not dive at the shore
    float rows[4]; float wsum = 0.0, hsum = 0.0;
    vec2 c11 = cell(i0 + ivec2(1, 1)), c12 = cell(i0 + ivec2(1, 2)), c21 = cell(i0 + ivec2(2, 1)), c22 = cell(i0 + ivec2(2, 2));
    for (int k = 0; k < 16; k++) { vec2 q = cell(i0 + ivec2(k & 3, k >> 2)); float wk = ((k & 3) == 1 || (k & 3) == 2) && ((k >> 2) == 1 || (k >> 2) == 2) ? 4.0 : 1.0; hsum += q.r * q.g * wk; wsum += q.g * wk; }
    bool none = wsum <= 0.0;   // no water anywhere near this point: it is dropped out of sight (it would otherwise stand at camera height and stretch a sheet up from the water)
    float fill = none ? 0.0 : hsum / wsum;
    // only water at about the same level takes part (a pool beside a river far below must not be bridged by the curve: that made spikes)
    vec2 own = cell(ivec2(floor(g)));
    float ref = own.g > 0.5 ? own.r : fill;
    float lo = 1e9, hi = -1e9;
    for (int y = 0; y < 4; y++) {
        float v[4];
        for (int x = 0; x < 4; x++) { vec2 q = cell(i0 + ivec2(x, y)); float hv = q.g > 0.5 ? q.r : fill; if (abs(hv - ref) > 1.5) hv = ref; v[x] = hv; if (x >= 1 && x <= 2 && y >= 1 && y <= 2) { lo = min(lo, hv); hi = max(hi, hv); } }
        rows[y] = cr(v[0], v[1], v[2], v[3], t.x);
    }
    float h = clamp(cr(rows[0], rows[1], rows[2], rows[3], t.y), lo, hi);   // no overshoot above or below the water around
    wet = mix(mix(c11.g, c21.g, t.x), mix(c12.g, c22.g, t.x), t.y);
    vec3 p = vec3(Grid.x + g.x, h + Cam.w, Grid.y + g.y);
    if (none) { p.y = Cam.w - 300.0; wet = 0.0; }
    if (WhirlInfo.w > 0.5) { vec2 wg = (p.xz + WhirlInfo.xy) * WhirlInfo.z; if (wg.x > 0.0 && wg.y > 0.0 && wg.x < 1.0 && wg.y < 1.0) p.y += textureLod(Whirl, wg, 0.0).r; }   // a whirlpool pulls the surface down
    rel = p; wpos = p.xz + Cam.xz;
    gl_Position = ProjMat * ModelViewMat * vec4(p, 1.0);
}
