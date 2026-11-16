#version 150

uniform vec3 SunW;         // the sun's direction (world axes: the normals are world normals)
uniform vec3 CloudColor;
uniform vec3 Horizon;
uniform float Fade;       // 1, less in a sandstorm or blizzard: the clouds are hidden in its fog
uniform vec4 Params;       // x: daylight 0..1

in vec3 worldNormal;
in float dark;
in float dist;
in float up;

out vec4 fragColor;

void main() {
    vec3 n = normalize(worldNormal);
    float day = Params.x;
    float sunF = max(dot(n, SunW), 0.0) * smoothstep(-0.05, 0.25, SunW.y);
    float sky = 0.5 + 0.5 * n.y;
    vec3 under = vec3(0.56, 0.63, 0.76) * (0.55 + 0.45 * day);
    vec3 sunny = CloudColor * (0.95 + 0.1 * day);
    vec3 col = mix(under, sunny, smoothstep(0.15, 0.95, 0.55 * sky + 0.40 * sunF + 0.15 * up));
    col *= 1.0 - 0.62 * dark;
    float far = smoothstep(600.0, 16000.0, dist);
    col = mix(col, Horizon * (0.6 + 0.4 * day), far * 0.8);
    fragColor = vec4(col, Fade * (1.0 - smoothstep(19000.0, 25000.0, dist)));
}
