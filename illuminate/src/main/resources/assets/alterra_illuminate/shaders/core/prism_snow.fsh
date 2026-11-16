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

out vec4 fragColor;

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    vec3 n = normalize(worldNormal);
    float lit = max(dot(n, SunDir), 0.0) * smoothstep(-0.05, 0.25, SunDir.y);   // the sun's side is brighter, and only while the sun is up
    float shade = 0.70 + 0.22 * lit - 0.18 * (1.0 - n.y);                       // slopes away from the sun, and steep ones, are darker: the relief reads
    vec3 cool = mix(vec3(0.90, 0.95, 1.0), vec3(1.0), clamp(shade * 1.1, 0.0, 1.0));   // only the shade is cool
    vec4 color = vec4(tex.rgb * vertexColor.rgb * shade * cool, 1.0) * ColorModulator;
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
