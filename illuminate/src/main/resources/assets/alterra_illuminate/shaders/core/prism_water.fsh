#version 150

#moj_import <fog.glsl>

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform vec3 SunDir;
uniform vec3 Zenith;
uniform vec3 Horizon;
uniform vec3 WaterColor;

in float vertexDistance;
in vec4 vertexColor;   // r: foam, g: how fast the water moves, a: how much of the surface is there (fades out at the rim)
in vec4 lightTint;
in vec2 texCoord0;
in vec3 worldNormal;
in vec3 viewPos;

out vec4 fragColor;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}
float fbm(vec2 p) { return 0.5 * vnoise(p) + 0.25 * vnoise(p * 2.03 + 7.1) + 0.125 * vnoise(p * 4.1 + 3.3); }

void main() {
    vec3 n = normalize(worldNormal), v = normalize(viewPos);
    float fres = pow(1.0 - clamp(dot(-v, n), 0.0, 1.0), 4.0);
    vec3 r = reflect(v, n);
    vec3 sky = mix(Horizon, Zenith, clamp(r.y * 1.4, 0.0, 1.0));
    float glint = pow(max(dot(r, SunDir), 0.0), 140.0) * smoothstep(-0.05, 0.2, SunDir.y);
    float light = clamp(0.35 + 0.65 * max(lightTint.r, lightTint.b), 0.0, 1.0);
    vec3 body = WaterColor * (0.55 + 0.45 * light) * (0.85 + 0.15 * clamp(n.y, 0.0, 1.0));
    vec3 col = mix(body, sky * (0.55 + 0.45 * light), 0.18 + 0.7 * fres) + vec3(1.0, 0.97, 0.9) * glint * 0.9;
    // foam: where the surface is steep or the water piles up; broken up by noise that is carried along with the turning water
    float streak = fbm(texCoord0 * 1.6);
    float foam = clamp(vertexColor.r * 1.4 + vertexColor.g * 0.55, 0.0, 1.0) * smoothstep(0.38, 0.66, streak);
    col = mix(col, vec3(0.93, 0.97, 1.0) * (0.6 + 0.4 * light), foam * 0.9);
    float alpha = clamp(mix(0.84, 0.97, fres) + foam * 0.2, 0.0, 1.0) * vertexColor.a;
    fragColor = linear_fog(vec4(col, alpha) * ColorModulator, vertexDistance, FogStart, FogEnd, FogColor);
}
