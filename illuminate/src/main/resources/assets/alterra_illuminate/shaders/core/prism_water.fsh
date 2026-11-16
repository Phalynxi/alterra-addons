#version 150

uniform sampler2D SceneDepth;   // the scene's depth without the water: how deep the water is here
uniform vec4 SunW;      // xyz: the sun or moon, w: how bright
uniform vec3 SkyH;      // the sky at the horizon
uniform vec3 SkyZ;      // the sky overhead
uniform vec3 Tint;      // the water's own colour here (the biome's)
uniform vec4 Wave;      // x: time, yz: the wind, w: how rough
uniform vec4 Screen;    // xy: the screen size, z: near, w: far

in vec3 rel;
in vec2 wpos;
in float wet;

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
    float edge = smoothstep(0.02, 0.35, wet) * smoothstep(0.0, 0.6, thick + 0.1);   // a soft shore, no hard block edge
    float alpha = clamp(mix(0.30, 0.86, shallow) * edge + fr * 0.4 * edge, 0.0, 1.0);   // the bed shows through shallow water
    fragColor = vec4(col, alpha);
}
