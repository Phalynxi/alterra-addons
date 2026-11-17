#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform vec3 SunDir;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 worldNormal;
in vec3 viewV;

out vec4 fragColor;

float h21(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h21(i), h21(i + vec2(1.0, 0.0)), f.x), mix(h21(i + vec2(0.0, 1.0)), h21(i + vec2(1.0, 1.0)), f.x), f.y);
}

void main() {
    vec2 wp = texCoord0 * 4.0;   // the world position (x, z) in blocks
    // wind-drifted undulation: very gentle swells a few blocks across, so a flat field is never a flat plane
    float s = 0.32, n0 = vnoise(wp * s), nx = vnoise((wp + vec2(0.4, 0.0)) * s), nz = vnoise((wp + vec2(0.0, 0.4)) * s);
    float fine = 0.5 * vnoise(wp * 1.3), fx = 0.5 * vnoise((wp + vec2(0.3, 0.0)) * 1.3), fz = 0.5 * vnoise((wp + vec2(0.0, 0.3)) * 1.3);
    float farK = smoothstep(20.0, 80.0, vertexDistance);   // far away the steps are small: their sides shade like the tops, so a hillside reads as one soft slope
    vec3 n = normalize(mix(worldNormal, vec3(0.0, 1.0, 0.0), 0.25 + 0.55 * farK) + vec3((n0 - nx) * 0.95 + (fine - fx) * 0.40, 0.0, (n0 - nz) * 0.95 + (fine - fz) * 0.40));
    // (snow is a soft, matte blanket: facets are blended towards up above, so slopes do not flash light and dark)
    // how bright the sky is (the fog takes the sky's colour): a storm or the night leaves the snow dull and blue-grey instead of a glowing white
    float skyLuma = dot(FogColor.rgb, vec3(0.299, 0.587, 0.114)), skyK = smoothstep(0.04, 0.62, skyLuma);
    float lit = max(dot(n, SunDir), 0.0) * smoothstep(-0.05, 0.25, SunDir.y) * smoothstep(0.38, 0.62, skyLuma);
    float skyUp = 0.5 + 0.5 * n.y;
    // snow takes its colour from the light: warm white in the sun, blue in the shade (it is lit by the sky), never grey
    vec3 ambient = mix(vec3(0.72, 0.80, 0.95), vec3(0.89, 0.94, 1.0), skyUp);
    ambient = mix(vec3(dot(ambient, vec3(0.333))), ambient, 0.45 + 0.55 * skyK);   // dim light is greyer, not a deep blue
    vec3 irradiance = ambient * mix(0.36, 1.0, skyK) * (0.56 + 0.08 * mix(max(worldNormal.y, 0.0), 1.0, farK)) + vec3(1.0, 0.965, 0.89) * lit * 0.26;   // tops are a soft white (never blown out), slopes and walls a step darker, not a harsh contrast   // walls and edges shade a little apart from the tops, so terraces read from afar
    float grain = texture(Sampler0, wp * 0.18).r;   // a soft, broad grain
    vec3 albedo = vec3(0.985, 0.99, 1.0) * (0.95 + 0.10 * grain);
    // glitter: tiny crystals that catch the sun, flickering as you move
    float h = h21(floor(wp * 90.0)), flick = fract(h * 41.0 + dot(normalize(viewV), vec3(3.1, 2.3, 1.7)) * 4.0);
    float sparkle = step(0.9985, h) * smoothstep(0.5, 1.0, flick) * (0.25 + 0.75 * lit) * (1.0 - smoothstep(6.0, 24.0, vertexDistance));   // fine, rare, only near
    vec3 col = albedo * irradiance * vertexColor.rgb + vec3(1.0, 0.99, 0.95) * sparkle * 0.6 * max(vertexColor.g, 0.2);
    fragColor = linear_fog(vec4(col, 1.0) * ColorModulator, vertexDistance, FogStart, FogEnd, FogColor);
}
