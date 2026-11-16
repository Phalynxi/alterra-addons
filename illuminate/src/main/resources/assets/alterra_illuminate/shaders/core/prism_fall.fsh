#version 150

uniform vec4 Info;   // x: time (s), y: daylight, z..w: unused
uniform vec3 Tint;   // the water's colour

in vec2 uv;
in vec3 rel;
in float amount;

out vec4 fragColor;

float h11(float p) { return fract(sin(p * 127.1) * 43758.5453); }
float n11(float p) { float i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f); return mix(h11(i), h11(i + 1.0), f); }

void main() {
    float t = Info.x;
    // streaks running down: thin and thick bands along the lip, each sliding down at its own speed, faster lower down (the water speeds up as it falls)
    float x = uv.x * 6.0;
    float speed = 1.6 + 2.2 * uv.y;
    float s1 = n11(x * 2.3 + floor((uv.y * 3.0 - t * speed) * 2.0) * 0.37);
    float s2 = n11(x * 7.1 + 11.0) * n11(uv.y * 9.0 - t * speed * 2.5 + x);
    float foam = smoothstep(0.55, 1.0, s1 * 0.7 + s2 * 0.6) + smoothstep(0.75, 1.0, uv.y) * 0.8;   // white water, and churning foam at the foot
    vec3 body = Tint * vec3(0.55, 0.80, 0.95) * (0.45 + 0.55 * Info.y);
    vec3 col = mix(body, vec3(0.92, 0.97, 1.0) * (0.5 + 0.5 * Info.y), clamp(foam, 0.0, 1.0));
    float thin = smoothstep(0.0, 0.08, uv.y) * (1.0 - smoothstep(0.92, 1.0, uv.y));
    float alpha = clamp((0.55 + 0.4 * foam) * amount, 0.0, 0.95) * mix(0.6, 1.0, thin);
    if (alpha < 0.01) discard;
    fragColor = vec4(col, alpha);
}
