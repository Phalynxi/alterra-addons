#version 150

uniform vec3 SunW;        // the sun's direction (world axes)
uniform vec3 CloudColor;
uniform vec3 Horizon;
uniform float Fade;       // 1, less in a sandstorm or blizzard: the clouds are hidden in its fog
uniform vec4 Params;      // x: daylight 0..1

in vec3 wpos;
flat in vec3 centre;
flat in vec2 radii;
in vec4 info;
in float dist;
in float nearFade;

out vec4 fragColor;

float h31(vec3 p) { p = fract(p * vec3(0.1031, 0.1030, 0.0973)); p += dot(p, p.yxz + 33.33); return fract((p.x + p.y) * p.z); }
float vn3(vec3 p) {
    vec3 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(h31(i), h31(i + vec3(1, 0, 0)), f.x), mix(h31(i + vec3(0, 1, 0)), h31(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(h31(i + vec3(0, 0, 1)), h31(i + vec3(1, 0, 1)), f.x), mix(h31(i + vec3(0, 1, 1)), h31(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

void main() {
    // the puff is an ellipsoid fixed in the world (flat-ish, wider than tall): find how close this pixel's line of sight passes to its centre, in the ellipsoid's own sphere space,
    // so its outline, base and lumps stay put as you turn; only moving changes the view of it
    float s = radii.x / max(radii.y, 1e-3);
    vec3 d = normalize(wpos) * vec3(1.0, s, 1.0), c = centre * vec3(1.0, s, 1.0);
    float t = dot(d, c) / dot(d, d);
    vec3 pc = d * t - c; float r = length(pc) / radii.x;
    if (r >= 1.25 || t <= 0.0) discard;
    vec3 side = pc / max(length(pc), 1e-4), toCam = -normalize(c);
    vec3 n = normalize(side * min(r, 1.0) + toCam * sqrt(max(0.0, 1.0 - r * r)));   // the surface facing this pixel
    n = normalize(n * vec3(1.0, 1.0 / s, 1.0));
    float lod = 1.0 - smoothstep(300.0, 1000.0, dist);
    vec3 np = (pc / radii.x) * 2.6 + info.z * 37.0;   // cauliflower lumps on the rim, in the puff's own frame (they do not turn with the view)
    float rough = 0.5;
    if (dist < 2500.0) rough = vn3(np) * 0.6 + 0.2;            // one layer of lumps out to the middle distance, none far away (a few pixels there)
    if (lod > 0.0) rough += (vn3(np * 2.3 + 11.0) - 0.5) * 0.4 * lod;   // the fine second layer only close by
    float body = smoothstep(1.0, 0.30, r + (rough - 0.5) * 0.55);
    float lit = clamp(dot(n, SunW) * 0.5 + 0.5, 0.0, 1.0);
    float shade = clamp(info.x + n.y * 0.16, 0.0, 1.0);   // darker underneath, lighter on top
    float day = Params.x;
    vec3 under = vec3(0.55, 0.62, 0.74) * (0.55 + 0.45 * day);
    vec3 sunny = CloudColor * (0.95 + 0.1 * day);
    vec3 col = mix(under, sunny, smoothstep(0.12, 0.95, lit * 0.6 + shade * 0.5));
    col *= 1.0 - 0.62 * info.y;   // a storm cloud is dark
    float far = smoothstep(600.0, 16000.0, dist);
    col = mix(col, Horizon * (0.6 + 0.4 * day), far * 0.8);
    float alpha = Fade * nearFade * body * info.w * (1.0 - smoothstep(19000.0, 25000.0, dist));
    if (alpha < 0.01) discard;
    fragColor = vec4(col * alpha, alpha);   // premultiplied: drawn into the half-size cloud picture, then laid over the scene
}
