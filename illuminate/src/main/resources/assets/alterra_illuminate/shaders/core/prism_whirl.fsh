#version 150

uniform vec4 Info;   // x: time (s), y: spin (+1 / -1), z: daylight, w: 1 when the camera is under water

in vec2 uv;
in vec3 rel;
in vec3 outward;
in float strength;

out vec4 fragColor;

void main() {
    float t = Info.x, spin = Info.y;
    // spiral streaks winding down the funnel and turning with it, faster lower down (the core spins fastest where it is narrowest)
    float a = uv.x * 7.0 + uv.y * 5.0 * spin - t * spin * (0.9 + 1.6 * uv.y);
    float streak = 0.5 + 0.5 * sin(a * 6.2831853), fine = 0.5 + 0.5 * sin(a * 6.2831853 * 3.0 + uv.y * 9.0);
    float lines = smoothstep(0.55, 0.95, streak) * 0.7 + smoothstep(0.7, 1.0, fine) * 0.3;
    vec3 v = normalize(rel);
    float edge = 1.0 - abs(dot(normalize(outward), v));   // seen edge-on the wall is thicker (brighter, more opaque)
    vec3 deep = vec3(0.10, 0.32, 0.45), foam = vec3(0.86, 0.95, 1.0);
    vec3 col = mix(deep, foam, clamp(0.35 * edge + 0.65 * lines, 0.0, 1.0)) * (0.45 + 0.55 * Info.z);
    float fade = (1.0 - smoothstep(0.82, 1.0, uv.y)) * smoothstep(0.0, 0.06, uv.y);   // it melts into the surface at the top and thins out at the bottom
    float alpha = (0.28 + 0.42 * edge + 0.30 * lines) * fade * strength;
    if (alpha < 0.01) discard;
    fragColor = vec4(col, alpha);
}
