#version 150

uniform sampler2D SceneDepth;   // the scene's depth without the water: how deep the water is here
uniform vec4 SunW;      // xyz: the sun or moon, w: how bright
uniform vec3 SkyH;      // the sky at the horizon
uniform vec3 SkyZ;      // the sky overhead
uniform vec3 Tint;      // the water's own colour here (the biome's)
uniform vec4 Wave;      // x: time, yz: the wind, w: how rough
uniform vec4 Screen;
uniform float Under;    // 1 when the camera is under the water    // xy: the screen size, z: near, w: far

in vec3 rel;
in vec2 wpos;
in float wet;
in float foamAmt;

out vec4 fragColor;

// ripples: a handful of travelling waves whose strength follows the wind (the ones running with it rule)
vec2 slope(vec2 p) {
    float t = Wave.x; vec2 wv = Wave.yz; float wl = length(wv), lock = smoothstep(0.03, 0.15, wl); vec2 wd = wv / max(wl, 0.03);
    vec2 g = vec2(0.0);
    vec2 k[6] = vec2[](vec2(0.41, 0.17), vec2(-0.29, 0.58), vec2(0.67, -0.33), vec2(-0.91, -0.47), vec2(1.31, 0.61), vec2(-1.57, 1.19));
    float a[6] = float[](0.50, 0.35, 0.28, 0.18, 0.12, 0.08);
    for (int i = 0; i < 6; i++) {
        float al = mix(1.0, 0.18 + 1.65 * pow(max(dot(normalize(k[i]), wd), 0.0), 2.0), lock);
        float w = length(k[i]), sp = sqrt(9.8 * w) * 0.35;
        g += a[i] * al * cos(dot(p, k[i]) * 1.3 + t * sp + float(i) * 1.7) * k[i];
    }
    return g * (0.10 + 0.10 * Wave.w);
}
float fh(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float fn(vec2 p) { vec2 i = floor(p), q = fract(p); q = q * q * (3.0 - 2.0 * q); return mix(mix(fh(i), fh(i + vec2(1, 0)), q.x), mix(fh(i + vec2(0, 1)), fh(i + vec2(1, 1)), q.x), q.y); }
float lin(float d) { float n = Screen.z, f = Screen.w; return 2.0 * n * f / (f + n - (2.0 * d - 1.0) * (f - n)); }

void main() {
    if (wet < 0.02) discard;
    vec2 g = slope(wpos);
    vec3 n = normalize(vec3(-g.x, 1.0, -g.y) + normalize(cross(dFdx(rel), dFdy(rel))) * 0.0);
    vec3 nGeo = normalize(cross(dFdx(rel), dFdy(rel))); if (nGeo.y < 0.0) nGeo = -nGeo;
    n = normalize(nGeo + vec3(-g.x, 0.0, -g.y));   // the real slope of the surface, with the ripples on it
    vec3 v = normalize(rel);
    float nv = clamp(dot(-v, n), 0.0, 1.0), fr = 0.06 + 0.94 * pow(1.0 - nv, 4.0);
    vec3 r = reflect(v, n); r.y = max(r.y, 0.02);
    vec3 sky = mix(SkyH, SkyZ, pow(clamp(r.y * 1.3, 0.0, 1.0), 0.6)) * vec3(0.85, 0.94, 1.0);
    float sd = max(dot(r, SunW.xyz), 0.0);
    vec3 glint = vec3(1.0, 0.95, 0.85) * (pow(sd, 700.0) * 3.0 + pow(sd, 70.0) * 0.3) * SunW.w;
    // how deep: from the scene's depth behind the surface
    vec2 uv = gl_FragCoord.xy / Screen.xy;
    float behind = lin(texture(SceneDepth, uv).r), here = lin(gl_FragCoord.z), thick = max(0.0, behind - here);
    float shallow = 1.0 - exp(-thick * 0.16);
    vec3 body = mix(mix(Tint, vec3(0.35, 0.80, 0.78), 0.55) * 1.05, Tint * vec3(0.34, 0.58, 0.86), shallow) * (0.55 + 0.45 * SunW.w);   // clear turquoise over the shallows, blue in the deep
    vec3 col = mix(body, sky, clamp(fr * 1.15, 0.0, 1.0)) + glint;
    if (Under > 0.5 && v.y > 0.0) {   // seen from under the water: a bright window to the sky straight up (inside about 49 degrees), a mirror of the deep water outside it
        vec3 nd = -n;
        float c = clamp(dot(-v, nd), 0.0, 1.0), sn = sqrt(1.0 - c * c) * 1.333;
        float win = 1.0 - smoothstep(0.93, 1.0, sn);
        vec3 tr = refract(v, nd, 1.333);
        vec3 skyIn = mix(SkyH, SkyZ, clamp(tr.y * 1.5, 0.0, 1.0)) * 1.2 + vec3(1.0, 0.95, 0.85) * pow(max(dot(tr, SunW.xyz), 0.0), 80.0) * 2.0 * SunW.w;
        vec3 deep = Tint * vec3(0.30, 0.55, 0.62) * (0.35 + 0.65 * SunW.w);
        col = mix(deep, skyIn, win);
        fragColor = vec4(col, 0.95 * smoothstep(0.02, 0.35, wet));
        return;
    }
    if (foamAmt > 0.01) {   // white water where a fall lands: churning, breaking up into drifting patches as it spreads
        float t2 = Wave.x;
        float pat = fn(wpos * 1.7 + vec2(t2 * 0.6, -t2 * 0.4)) * 0.55 + fn(wpos * 4.1 - vec2(t2 * 1.1, t2 * 0.7)) * 0.45;
        float w = smoothstep(1.0 - foamAmt, 1.15 - foamAmt * 0.6, pat + foamAmt * 0.35);
        col = mix(col, vec3(0.93, 0.97, 1.0) * (0.55 + 0.45 * SunW.w), clamp(w, 0.0, 0.95));
        fr = max(fr, w * 0.6);
    }
    float edge = smoothstep(0.02, 0.35, wet) * smoothstep(0.0, 0.6, thick + 0.1);   // a soft shore, no hard block edge
    float alpha = clamp(mix(0.30, 0.86, shallow) * edge + fr * 0.4 * edge, 0.0, 1.0);   // the bed shows through shallow water
    fragColor = vec4(col, alpha);
}
