#version 150

uniform vec4 Info;   // x: time (s), y: daylight
uniform vec3 Tint;   // the water's colour

in vec2 uv;          // x: along the side (blocks, world), y: blocks down from the top of the fall
in vec3 rel;
in float amount;

out vec4 fragColor;

float h11(float p) { return fract(sin(p * 127.1) * 43758.5453); }
float n11(float p) { float i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f); return mix(h11(i), h11(i + 1.0), f); }

void main() {
    float t = Info.x;
    // long streaks of white water pouring down: bands across the sheet, each sliding down, stretched long (never dots), speeding up as it falls
    float x = uv.x * 5.0, y = uv.y;
    float speed = 2.0 + 0.35 * y;
    float band = n11(x * 1.7 + 3.1) * 0.6 + n11(x * 4.3 + 9.2) * 0.4;
    float flow = n11(x * 1.3 + (y * 0.45 - t * speed * 0.45)) * 0.6 + n11(x * 3.1 - 5.0 + (y * 0.9 - t * speed * 0.9)) * 0.4;
    float white = smoothstep(0.45, 0.85, band * 0.5 + flow * 0.6);
    vec3 body = mix(Tint, vec3(0.45, 0.80, 0.85), 0.4) * (0.55 + 0.45 * Info.y);
    vec3 col = mix(body, vec3(0.90, 0.96, 1.0) * (0.55 + 0.45 * Info.y), white * 0.8);
    vec3 v = normalize(rel);
    float alpha = clamp(0.55 + 0.35 * white, 0.0, 0.92) * amount;
    fragColor = vec4(col, alpha);
}
