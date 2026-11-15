package dev.phi.alterra.illuminate.look;

import dev.phi.aethryon.api.AethryonApi;
import dev.phi.alterra.illuminate.Illuminate;
import dev.phi.alterra.illuminate.IlluminateConfig;
import dev.phi.alterra.illuminate.maps.GlUpload;
import dev.phi.alterra.illuminate.maps.HeightMap;


import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL43C;
import org.slf4j.Logger;

import java.nio.ByteBuffer;

/**
 * The scene's look, after the world is drawn and before the HUD (a full-screen pass, with Sodium and no shader pack): haze in the distance that is warm towards the sun and
 * cool away from it, a soft glow around bright things, light shafts from the sun through gaps in trees and terrain, and a filmic colour grade (richer colour, warm light,
 * cooler shadows, a gentle curve). It only reads what is on screen, so it is cheap; shafts come only from a sun that is on screen.
 */
@EventBusSubscriber(modid = Illuminate.ID, value = Dist.CLIENT)
public final class PostFx {
    private static final float[] M16 = new float[16], M9 = new float[9], FUNNEL = new float[32];   // matrix scratch for the uniforms, render thread only
    private PostFx() {}
    private static final Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static int program = -1, cloudProgram = -1, vao, color, depth, w, h, cloudFbo, cloudTex, cw, ch2; private static boolean failed;
    private static final java.util.Map<Integer, java.util.Map<String, Integer>> LOCS = new java.util.HashMap<>();
    private static double smoothT = Double.NaN; private static long smoothAt;
    /** The time the clouds move by, in game ticks: runs with real time and is pulled gently towards the game's own time. The game's time is set again by the server every so often, and when the game lags that is a jump of several ticks:
     *  the whole sky stepped. This one never jumps (a big difference, a time change or a pause, is followed at once). */
    private static double cloudClock(net.minecraft.client.multiplayer.ClientLevel level, float pt) {
        double target = level.getGameTime() % 1_000_000L + pt; long now = System.nanoTime();
        if (Double.isNaN(smoothT) || Math.abs(target - smoothT) > 300) smoothT = target;
        else if (!Minecraft.getInstance().isPaused()) {
            double dt = Math.min(0.25, (now - smoothAt) / 1.0e9) * 20.0;   // ticks since the last frame
            smoothT += dt; smoothT += (target - smoothT) * 0.012;
        }
        smoothAt = now;
        return smoothT;
    }

    private static int u(int prog, String name) { return LOCS.computeIfAbsent(prog, k -> new java.util.HashMap<>()).computeIfAbsent(name, n -> GL20C.glGetUniformLocation(prog, n)); }
    private static int uColor, uDepth, uInvProj, uInvView, uSun, uSunScreen, uHorizon, uZenith, uParams, uClouds, uCloudInfo, uCloudColor, uWaterFog, uCamPos, uStorm, uOn, uOn2;
    /** Vanilla's flat clouds are left out while ours are drawn. */
    public static boolean drawsClouds() { return !failed && IlluminateConfig.get(IlluminateConfig.SCENE_LOOK) && IlluminateConfig.get(IlluminateConfig.CLOUDS); }

    private static final String VERT = """
            #version 150
            out vec2 uv;
            void main() { vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2); uv = p; gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0); }
            """;
    private static final String FRAG = """
            #version 150
            in vec2 uv;
            out vec4 frag;
            uniform sampler2D u_Color, u_Depth;
            uniform mat4 u_InvProj; uniform mat3 u_InvView;
            uniform vec4 u_Sun;        // sun (or moon) direction in the world, how strong
            uniform vec4 u_SunScreen;  // where the sun is on screen, 1 when it is in front
            uniform vec3 u_Horizon, u_Zenith;
            uniform vec4 u_Params;     // daylight, rain, underwater, time (seconds)
            uniform sampler2D u_Clouds;   // vanilla's cloud map: a cell of 12 by 12 blocks per pixel
            uniform vec4 u_CloudInfo;    // the camera in cloud cells (x, z), the cloud bottom relative to the camera, 1 when there are clouds
            uniform vec3 u_CloudColor;
            uniform sampler2D u_CloudTex;
            uniform float u_CloudFade;   // 1, less in a storm's fog (the clouds are hidden in a sandstorm or blizzard)
            uniform float u_CloudEvo;
            uniform sampler2D u_Height;   // the top of everything around (leaves too), a block per texel
            uniform vec4 u_HeightInfo;    // camera x, z relative to that map, camera y, 1 when there is a map
            uniform float u_SunVis;       // how much of the sun gets through the clouds where you are (0 to 1)
            uniform float u_Quality;      // 0 low, 1 medium, 2 high
            uniform vec4 u_Funnel[8];     // tornadoes and the like, two each: the foot relative to the camera + radius; height, strength, type (0 tornado, 1 dust devil, 2 whirlpool), radius at the top
            uniform float u_FunnelN;      // how many
            uniform float u_Shadows;      // 1: sun shadows and shafts through leaves on   // how far the clouds have changed (they form and fade)   // the clouds' own pass (premultiplied colour, cover)
            uniform float u_Storm;
            uniform vec4 u_On;    // colour grade, glow, light shafts, haze
            uniform vec2 u_On2;   // clouds, underwater fog     // thunder, 0 to 1
            uniform vec3 u_WaterFog;   // the colour of the water around a diver (UnderwaterColour)
            uniform vec3 u_CamPos;     // the camera, wrapped (for the caustic pattern)
            float h3i(ivec3 i) {   // the same integer hash as CloudMap (Java), so both see the same clouds
                uint n = uint(i.x) * 73856093u ^ uint(i.y) * 19349663u ^ uint(i.z) * 83492791u;
                n = (n ^ (n >> 13u)) * 1274126177u; n ^= n >> 16u;
                return float(n & 0xFFFFFFu) / 16777216.0;
            }
            float n3(vec3 p) {
                ivec3 i = ivec3(floor(p)); vec3 f = fract(p); f = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);   // a smoother blend between cells (no creases)
                return mix(mix(mix(h3i(i), h3i(i + ivec3(1, 0, 0)), f.x), mix(h3i(i + ivec3(0, 1, 0)), h3i(i + ivec3(1, 1, 0)), f.x), f.y),
                           mix(mix(h3i(i + ivec3(0, 0, 1)), h3i(i + ivec3(1, 0, 1)), f.x), mix(h3i(i + ivec3(0, 1, 1)), h3i(i + ivec3(1, 1, 1)), f.x), f.y), f.z);
            }
            float fbm2(vec2 x) { float v = 0.0, w = 0.5; for (int i = 0; i < 3; i++) { v += n3(vec3(x, 0.37 * float(i) + u_CloudEvo)) * w; x = x * 2.03 + 17.1; w *= 0.5; } return v; }   // as CloudMap.fbm: clouds form and fade over time
            float topAt(vec3 p) {   // the top of what stands in that column, relative to the camera (very low when unknown)
                vec2 c = p.xz + u_HeightInfo.xy;
                if (u_HeightInfo.w < 0.5 || c.x < 0.0 || c.y < 0.0 || c.x >= 128.0 || c.y >= 128.0) return -1e4;
                return texelFetch(u_Height, ivec2(floor(c)), 0).r * 65535.0 - 64.0 - u_HeightInfo.z;
            }
            float sunlit(vec3 p, float n) {   // 1 in the sun, 0 in the shade of trees, overhangs or terrain between this point and the sun; several rays spread a little: soft edges
                float lit = 0.0;
                for (int r = 0; r < 4; r++) {
                    if (float(r) >= n) break;
                    vec3 off = vec3(cos(float(r) * 2.4), 0.0, sin(float(r) * 2.4)) * 0.45 * float(r > 0);
                    float hit = 0.0;
                    for (int k = 1; k <= 6; k++) { vec3 q = p + off + u_Sun.xyz * (float(k) * 2.2); if (q.y < topAt(q) - 0.05) { hit = 1.0; break; } }
                    lit += 1.0 - hit;
                }
                return lit / max(n, 1.0);
            }
            float cloudShade(vec3 p) {   // the shadow of the clouds overhead along the sun (the same map as the clouds)
                if (u_CloudInfo.w < 0.5 || u_Sun.y < 0.05) return 1.0;
                vec3 at = p + u_Sun.xyz * ((u_CloudInfo.z + 20.0 - p.y) / u_Sun.y);
                vec3 q = vec3(u_CloudInfo.x * 24.0 + at.x, 0.0, u_CloudInfo.y * 24.0 + at.z);
                float cov = smoothstep(0.47, 0.62, fbm2(q.xz * 0.0065) + 0.16 * u_Params.y + 0.22 * u_Storm);
                return 1.0 - 0.55 * cov;
            }
            float cloud(vec3 p, float y0) {   // p: relative to the camera. Natural cumulus: where they are comes from layered noise (no grid), more of them when it rains
                vec3 q = vec3(u_CloudInfo.x * 24.0 + p.x, p.y - y0, u_CloudInfo.y * 24.0 + p.z);   // drifts with the wind
                float cover = fbm2(q.xz * 0.0065) + 0.16 * u_Params.y + 0.22 * u_Storm;   // as CloudMap.cover: rain brings more cloud, thunder nearly closes the sky
                float cov = smoothstep(0.47, 0.62, cover);
                if (cov < 0.01) return 0.0;
                float h = q.y;
                float top = 6.0 + 84.0 * cov * cov * (0.55 + 0.45 * fbm2(q.xz * 0.02 + 5.0));   // tall domes in the middle of each cloud: about as tall as it is wide
                float base = 1.0 + 9.0 * n3(vec3(q.xz * 0.025, 9.1)) * (1.0 - 0.5 * cov);   // a ragged underside, not a flat ceiling
                float shape = smoothstep(base, base + 7.0, h) * smoothstep(top, top - 0.5 * top, h);   // flat base, rounded heads
                float detail = n3(q * 0.05) * 0.62 + n3(q * 0.14 + 7.0) * 0.38;   // cauliflower billows, eroded at the edges
                return max(0.0, shape * cov - (1.0 - detail) * (0.55 - 0.25 * cov)) * 1.8;
            }
            vec3 aces(vec3 x) { return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0); }
            void main() {
                vec3 c = texture(u_Color, uv).rgb;
                float d = texture(u_Depth, uv).r; bool sky = d >= 0.99999;
                vec4 vp = u_InvProj * vec4(uv * 2.0 - 1.0, d * 2.0 - 1.0, 1.0); vp /= vp.w;
                vec3 dir = normalize(u_InvView * vp.xyz); float dist = length(vp.xyz);
                float day = u_Params.x, rain = u_Params.y;
                float toSun = max(dot(dir, u_Sun.xyz), 0.0);
                #ifdef CLOUD_PASS
                vec4 cloudOut = vec4(0.0);
                // clouds: vanilla's cloud map (same places, same drift) grown into puffy clouds: a flat base, rounded billowing tops that rise higher where the map is solid,
                // broken into puffs by noise, each point lit by how much cloud lies between it and the sun (dark cores, sunlit tops, a bright rim towards the sun)
                bool insideC = u_CloudInfo.z < 0.0 && u_CloudInfo.z + 92.0 > 0.0;   // the camera is in the cloud layer
                if (u_CloudInfo.w > 0.5 && u_On2.x > 0.5 && (abs(dir.y) > 0.0005 || insideC) && u_Params.z < 0.5) {
                    float y0 = u_CloudInfo.z, y1 = y0 + 92.0;
                    float ta = abs(dir.y) > 0.0005 ? y0 / dir.y : -1e9, tb = abs(dir.y) > 0.0005 ? y1 / dir.y : 1e9, t0 = max(min(ta, tb), 0.0), t1 = max(ta, tb);
                    float tEnd = insideC ? min(t1, sky ? 1e9 : dist) : t1;   // from outside the layer: clouds everywhere, as if open sky (the full-size pass shows them only where the sky really is); from inside, they stop at what stands in front (no clouds painted over leaves)   // clouds everywhere, as if open sky: the full-size pass shows them only where the sky really is (else holes in leaves had no cloud: pale sky through every gap)
                    if (tEnd > t0 && t0 < 2600.0) {
                        float steps = 8.0 + 4.0 * u_Quality, len = min(tEnd - t0, 220.0), stepL = len / steps, a = 0.0, sunDen = -1.0; vec3 col = vec3(0.0);
                        float jit = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));   // a small, even offset per pixel (interleaved gradient noise): no stair-step slices, and the soft blend hides it
                        vec3 sunCol = u_CloudColor * (0.9 + 0.12 * day), shade = mix(u_CloudColor * 0.62, vec3(dot(u_Horizon, vec3(0.3, 0.5, 0.2))) * vec3(0.9, 0.94, 1.0), 0.35);   // toned in their own pass: soft whites, light grey shaded sides with a faint cool tint (the plain horizon colour made them lavender)
                        float storm = clamp(rain * 0.8 + u_Storm, 0.0, 1.0);   // rain clouds go dark grey underneath, thunderheads darker still
                        shade *= mix(1.0, 0.62, storm); sunCol *= mix(1.0, 0.82, storm);
                        for (int i = 0; i < 16; i++) {
                            if (float(i) >= steps) break;
                            float su = (float(i) + jit) / steps, sl = len * 1.6 * pow(su, 0.6) / steps;   // steps grow with distance: fine detail close up
                            vec3 p = dir * (t0 + len * pow(su, 1.6));
                            float dn = cloud(p, y0);
                            if (dn < 0.01) continue;
                            if ((i & 1) == 0 || sunDen < 0.0) sunDen = cloud(p + u_Sun.xyz * 12.0, y0) * 1.8;   // cloud between this point and the sun (every other step: it changes slowly)
                            float hf = smoothstep(0.0, 1.0, clamp((p.y - y0) / 34.0, 0.0, 1.0));
                            float light = smoothstep(0.18, 0.78, 0.7 * hf + 0.3 * exp(-sunDen * 0.8));   // painted: mostly by height (a light grey flat base, white heads) with a little sun shading, pressed into two soft tones
                            vec3 lit = mix(shade, sunCol, light) + vec3(1.0, 0.95, 0.86) * pow(toSun, 8.0) * u_Sun.w * 0.25 * light;
                            float k = (1.0 - a) * (1.0 - exp(-dn * sl * 0.6));   // dense: a clean outline instead of see-through streaks
                            col += lit * k; a += k;
                            if (a > 0.97) break;
                        }
                        float far = 1.0 - smoothstep(1400.0, 2600.0, t0);
                        cloudOut = vec4(col / max(a, 0.001) * a * far, a * far);
                    }
                }
                frag = cloudOut; return;
                #endif
                // sunlight: soft shadows of trees and overhangs, the clouds' shadows drifting over the land, and a dimmer, softer day when a cloud covers the sun
                if (!sky && u_Params.z < 0.5 && u_Sun.w > 0.02 && (u_Shadows > 0.5 || u_On2.x > 0.5)) {
                    vec3 wpr = dir * dist;
                    float sh = (dist < 72.0 && u_Shadows > 0.5) ? sunlit(wpr, 1.0 + u_Quality * 1.5) : 1.0;
                    float cs = u_On2.x > 0.5 ? cloudShade(wpr) : 1.0;
                    float light = mix(1.0, min(sh, cs), 0.45 * u_Sun.w * day);
                    c *= light;
                }
                // haze: the air between you and far things, warm towards the sun, the sky's own colour away from it
                if (!sky && u_Params.z < 0.5 && u_On.w > 0.5) {
                    vec3 hz = mix(u_Horizon, vec3(1.0, 0.86, 0.66) * (0.6 + 0.6 * day), pow(toSun, 14.0) * u_Sun.w * 0.5);
                    float a = (1.0 - exp(-max(dist - 48.0, 0.0) * (0.0012 + 0.004 * rain))) * (0.45 + 0.35 * rain);   // only far away
                    c = mix(c, hz, a * 0.2);
                }
                #ifndef CLOUD_PASS
                float cloudFrom = 1e9;   // how far away the clouds begin along this ray (looking up from below, or down or sideways from inside or above the layer)
                if (abs(dir.y) > 0.0005) { float ca = u_CloudInfo.z / dir.y, cb = (u_CloudInfo.z + 92.0) / dir.y; cloudFrom = max(min(ca, cb), 0.0); }
                else if (u_CloudInfo.z < 0.0 && u_CloudInfo.z + 92.0 > 0.0) cloudFrom = 0.0;
                { vec2 px = 1.0 / vec2(textureSize(u_CloudTex, 0)); vec4 cl = (texture(u_CloudTex, uv) * 4.0 + (texture(u_CloudTex, uv + vec2(px.x, 0.0)) + texture(u_CloudTex, uv - vec2(px.x, 0.0)) + texture(u_CloudTex, uv + vec2(0.0, px.y)) + texture(u_CloudTex, uv - vec2(0.0, px.y))) * 2.0
                            + texture(u_CloudTex, uv + px) + texture(u_CloudTex, uv - px) + texture(u_CloudTex, uv + vec2(px.x, -px.y)) + texture(u_CloudTex, uv + vec2(-px.x, px.y))) / 16.0;   // a 3x3 tent: the per-pixel offsets of the half-size pass blend away (no grain)
                  if (sky || dist > cloudFrom) c = c * (1.0 - cl.a * u_CloudFade) + cl.rgb * u_CloudFade;   // the clouds, drawn at half size in their own pass and blended in softly, only where nothing nearer stands in front (no halo on leaves)
                }
                #endif
                #ifndef CLOUD_PASS
                if (u_CloudInfo.w > 0.5 && u_On2.x > 0.5 && u_Params.z < 0.5 && u_CloudInfo.z < 0.0 && u_CloudInfo.z + 92.0 > 0.0) {   // inside a cloud: it closes in with distance (denser cloud, thicker fog), grey-white and darker in rain
                    float reach = min(dist, 160.0);
                    float dens = 0.5 * (cloud(vec3(0.0), u_CloudInfo.z) + cloud(dir * reach * 0.5, u_CloudInfo.z));
                    float inFog = clamp(1.0 - exp(-dens * reach * 0.05), 0.0, 0.96);
                    vec3 fogCol = mix(u_CloudColor * 0.84, vec3(0.42), clamp(rain * 0.8 + u_Storm, 0.0, 1.0) * 0.6) * (0.5 + 0.5 * day);
                    c = mix(c, fogCol, inFog);
                }
                #endif
                #ifndef CLOUD_PASS
                // disasters, drawn after the clouds so nothing hides them: a tornado or dust devil is a column that flares out upwards (a few fixed samples along the ray: steady, no shimmer),
                // a whirlpool is a spiral lying on the water: a dark centre and arms of foam turning slowly
                for (int fi = 0; fi < 4; fi++) {
                    if (float(fi) >= u_FunnelN) break;
                    vec4 fa = u_Funnel[fi * 2], fb = u_Funnel[fi * 2 + 1];
                    float ftime = u_Params.w;
                    if (fb.z > 1.5) {
                        if (dir.y > -0.002 || fa.y > 0.0) continue;   // seen from below the surface or from level with it: nothing
                        float tp = fa.y / dir.y;
                        if (tp <= 0.0 || (!sky && dist < tp - 0.6)) continue;   // behind something that stands in front
                        vec2 rel = dir.xz * tp - fa.xz; float rr = length(rel), R2 = fb.w * 1.25 + 1.0;
                        if (rr > R2) continue;
                        float edge = smoothstep(R2, R2 * 0.55, rr), core = smoothstep(R2 * 0.38, 0.0, rr);
                        float wsp = fb.x < 0.0 ? -1.0 : 1.0; float ang = atan(rel.y, rel.x) * wsp + log(rr + 1.0) * 2.8 - ftime * 0.9;
                        float arms = smoothstep(0.35, 0.95, 0.5 + 0.5 * sin(ang * 3.0));
                        float foam = arms * edge * (1.0 - core * 0.8) * fb.y * (1.0 - smoothstep(60.0, 220.0, tp));
                        c = mix(c, c * vec3(0.30, 0.50, 0.66), core * 0.85 * fb.y * edge);   // the hole: deep, dark water
                        c = mix(c, vec3(0.80, 0.90, 0.95) * (0.55 + 0.45 * day), foam * 0.55);   // foam, soft and see-through
                        continue;
                    }
                    float R = fb.w * 1.25 + 2.0;
                    vec2 dxz = dir.xz; float qa = dot(dxz, dxz);
                    float qb = dot(dxz, fa.xz), qc = dot(fa.xz, fa.xz) - R * R, disc = qb * qb - qa * qc;
                    if (qa < 1e-6 || disc < 0.0) continue;
                    float sq = sqrt(disc), ta = (qb - sq) / qa, tb = (qb + sq) / qa;
                    float ty0 = 0.0, ty1 = 1e9;
                    if (abs(dir.y) > 1e-4) { float y0 = fa.y / dir.y, y1 = (fa.y + fb.x) / dir.y; ty0 = min(y0, y1); ty1 = max(y0, y1); }
                    else if (fa.y > 0.0 || fa.y + fb.x < 0.0) continue;
                    float t0 = max(max(ta, ty0), 0.0), t1 = min(min(tb, ty1), sky ? 1e9 : dist);
                    if (t1 <= t0) continue;
                    float fstep = (t1 - t0) / 6.0, fal = 0.0; vec3 fcol = vec3(0.0);
                    float spin = fb.z < 0.5 ? 2.6 : 4.0;
                    vec3 dark = fb.z < 0.5 ? vec3(0.14, 0.135, 0.13) : vec3(0.60, 0.50, 0.34);
                    vec3 lightc = fb.z < 0.5 ? vec3(0.42, 0.39, 0.36) : vec3(0.90, 0.78, 0.56);
                    vec3 skirtc = fb.z < 0.5 ? vec3(0.40, 0.35, 0.29) : vec3(0.85, 0.72, 0.48);
                    for (int fk = 0; fk < 6; fk++) {
                        float ft = mix(t0, t1, (float(fk) + 0.5) / 6.0);
                        vec3 fp = dir * ft; float h = fp.y - fa.y, hn = clamp(h / max(fb.x, 1.0), 0.0, 1.0);
                        float fr = fa.w + (fb.w - fa.w) * pow(hn, 1.4);
                        vec2 sway = vec2(sin(h * 0.07 + ftime * 0.9), cos(h * 0.05 + ftime * 0.7)) * fr * 0.28 * hn;
                        vec2 rel = fp.xz - fa.xz - sway; float fd = length(rel);
                        float ang = atan(rel.y, rel.x) + h * 0.045 - ftime * spin;
                        float ns = 0.5 + 0.5 * sin(ang * 3.0 + h * 0.21) * sin(ang * 5.0 - h * 0.13 + ftime * 0.7);
                        float skirt = exp(-h / (fb.z < 1.5 ? 5.0 : 3.0)) * smoothstep(fr * 2.4, fr * 0.7, fd);
                        float body = smoothstep(fr, fr * 0.45, fd) * (0.5 + 0.5 * ns) * (1.0 - smoothstep(0.9, 1.0, hn));
                        float dens = (body * 2.6 + skirt * 1.0) * fb.y;
                        if (dens < 0.01) continue;
                        float fk2 = (1.0 - fal) * (1.0 - exp(-dens * fstep * 0.30));
                        float side = clamp(0.5 + 0.5 * dot(normalize(vec3(rel.x, 0.4, rel.y)), u_Sun.xyz), 0.0, 1.0);
                        vec3 here = mix(mix(dark, lightc, side * (0.35 + 0.65 * day)), skirtc * (0.45 + 0.55 * day), clamp(skirt / max(dens, 0.01), 0.0, 1.0) * 0.7) * (0.45 + 0.55 * day);
                        fcol += here * fk2; fal += fk2;
                        if (fal > 0.97) break;
                    }
                    float ffar = 1.0 - smoothstep(600.0, 1100.0, t0);
                    c = c * (1.0 - fal * ffar) + fcol * ffar;
                }
                #endif
                // under water: light rippling over everything that faces up (strongest close by), and the water's own colour closing in with distance
                if (u_Params.z > 0.5 && u_On2.y > 0.5) {
                    vec3 wp = u_CamPos + dir * dist;
                    vec3 nrm = normalize(cross(dFdx(vp.xyz), dFdy(vp.xyz)));
                    float upF = clamp(abs((u_InvView * nrm).y), 0.0, 1.0);
                    if (!sky) {
                        vec2 cz = wp.xz * 0.35 + vec2(u_Params.w * 0.4, u_Params.w * 0.31);
                        float cst = pow(0.5 + 0.5 * sin(cz.x + 2.0 * sin(cz.y * 0.9 + u_Params.w * 0.7)) * sin(cz.y * 1.1 + 1.7 * sin(cz.x * 0.8 - u_Params.w * 0.6)), 3.0);
                        c *= 1.0 + cst * 0.45 * (0.3 + 0.7 * upF) * exp(-dist * 0.06) * (0.25 + 0.75 * day);
                    }
                    float fogA = (1.0 - exp(-dist * 0.022)) * 0.85;
                    vec3 fogC = u_WaterFog;   // the biome's water colour, clear and turquoise, darker at night (UnderwaterColour: the same as the fog)
                    c = mix(c, fogC, sky ? 0.8 : fogA);
                }
                // glow: the bright parts of the picture, softly spread
                vec3 g = u_On.y < 0.5 ? vec3(0.0) : textureLod(u_Color, uv, 3.0).rgb * 0.4 + textureLod(u_Color, uv, 4.5).rgb * 0.35 + textureLod(u_Color, uv, 6.0).rgb * 0.25;
                c += max(g - 0.86, 0.0) * 0.16;   // only truly bright things glow, and gently
                // light shafts: march towards the sun on screen and count the sky seen along the way
                if (u_SunScreen.w > 0.5 && u_Sun.w > 0.01 && u_Params.z < 0.5 && u_On.z > 0.5) {
                    vec2 to = u_SunScreen.xy - uv; float rays = 0.0, wgt = 1.0;
                    for (int i = 0; i < 20; i++) {
                        vec2 q = uv + to * (float(i) + 0.5) / 20.0;
                        if (q.x < 0.0 || q.y < 0.0 || q.x > 1.0 || q.y > 1.0) break;
                        rays += step(0.99999, texture(u_Depth, q).r) * wgt; wgt *= 0.93;
                    }
                    float near = 1.0 - smoothstep(0.0, 0.55, length(to * vec2(1.6, 1.0)));
                    c += vec3(1.0, 0.88, 0.68) * rays / 20.0 * near * u_Sun.w * (1.0 - rain) * 0.2 * u_SunVis;   // weaker when a cloud covers the sun
                }
                // the sky: a deeper blue overhead, so it is not a pale wash (the sun's glow is left alone)
                if (sky && u_Params.z < 0.5) {
                    float up = clamp(dir.y, 0.0, 1.0);
                    c = mix(c, c * vec3(0.78, 0.9, 1.08), smoothstep(0.05, 0.6, up) * day * (1.0 - rain) * (1.0 - pow(toSun, 12.0)));
                }
                if (u_On.x < 0.5) { frag = vec4(clamp(c, 0.0, 1.0), 1.0); return; }
                // grade: neutral brightness (no lift of the midtones), a gentle contrast curve, colour almost as it is, warm light and slightly cool shadows, a faint vignette
                c = clamp(c, 0.0, 1.0);
                c = mix(c, c * c * (3.0 - 2.0 * c), 0.22);
                float l = dot(c, vec3(0.299, 0.587, 0.114));
                c = mix(vec3(l), c, 1.03);
                c *= mix(vec3(0.985, 0.995, 1.015), vec3(1.015, 1.0, 0.985), smoothstep(0.2, 0.8, l));
                vec2 v = uv - 0.5; c *= 1.0 - 0.16 * dot(v, v);
                frag = vec4(clamp(c, 0.0, 1.0), 1.0);
            }
            """;

    private static float on(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue v) { return IlluminateConfig.get(v) ? 1f : 0f; }

    private static int shader(int type, String src) {
        int s = GL20C.glCreateShader(type); GL20C.glShaderSource(s, src); GL20C.glCompileShader(s);
        if (GL20C.glGetShaderi(s, GL20C.GL_COMPILE_STATUS) == 0) throw new IllegalStateException(GL20C.glGetShaderInfoLog(s));
        return s;
    }

    private static int link(String frag) {
        int v = shader(GL20C.GL_VERTEX_SHADER, VERT), f = shader(GL20C.GL_FRAGMENT_SHADER, frag);
        int p = GL20C.glCreateProgram(); GL20C.glAttachShader(p, v); GL20C.glAttachShader(p, f); GL20C.glLinkProgram(p);
        if (GL20C.glGetProgrami(p, GL20C.GL_LINK_STATUS) == 0) throw new IllegalStateException(GL20C.glGetProgramInfoLog(p));
        return p;
    }
    private static void init() {
        program = link(FRAG);
        cloudProgram = link(FRAG.replace("#version 150", "#version 150\n#define CLOUD_PASS"));
        vao = GL30C.glGenVertexArrays();
    }

    private static void cloudTarget(int nw, int nh) {   // half the screen in each direction: a quarter of the work, and the blend softens it
        int tw = Math.max(1, nw / 2), th = Math.max(1, nh / 2);
        if (tw == cw && th == ch2 && cloudTex != 0) return;
        if (cloudTex == 0) { cloudTex = GL11C.glGenTextures(); cloudFbo = GL30C.glGenFramebuffers(); }
        cw = tw; ch2 = th;
        GlStateManager._bindTexture(cloudTex);
        GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA8, cw, ch2, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR); GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL13C.GL_CLAMP_TO_EDGE); GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL13C.GL_CLAMP_TO_EDGE);
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, cloudFbo);
        GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0, GL11C.GL_TEXTURE_2D, cloudTex, 0);
        GlStateManager._bindTexture(0);
    }

    private static void textures(int nw, int nh) {
        if (nw == w && nh == h && color != 0) return;
        if (color != 0) { GL11C.glDeleteTextures(color); GL11C.glDeleteTextures(depth); }
        w = nw; h = nh;
        color = GL11C.glGenTextures(); GlStateManager._bindTexture(color);
        int levels = 1 + (int) Math.floor(Math.log(Math.max(w, h)) / Math.log(2));
        for (int i = 0; i < Math.min(levels, 8); i++) GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, i, GL11C.GL_RGBA8, Math.max(1, w >> i), Math.max(1, h >> i), 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL30C.GL_TEXTURE_MAX_LEVEL, Math.min(levels, 8) - 1);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR_MIPMAP_LINEAR);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL13C.GL_CLAMP_TO_EDGE); GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL13C.GL_CLAMP_TO_EDGE);
        depth = GL11C.glGenTextures(); GlStateManager._bindTexture(depth);
        GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_DEPTH_COMPONENT, w, h, 0, GL11C.GL_DEPTH_COMPONENT, GL11C.GL_FLOAT, (ByteBuffer) null);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST); GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
        GlStateManager._bindTexture(0);
    }

    @SubscribeEvent
    public static void draw(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || failed || !IlluminateConfig.get(IlluminateConfig.SCENE_LOOK)) return;
        var mc = Minecraft.getInstance(); var level = mc.level;
        if (level == null || !level.dimensionType().hasSkyLight()) return;
        try {
            if (program < 0) init();
            var rt = mc.getMainRenderTarget();
            textures(rt.width, rt.height);
            GL43C.glCopyImageSubData(rt.getColorTextureId(), GL11C.GL_TEXTURE_2D, 0, 0, 0, 0, color, GL11C.GL_TEXTURE_2D, 0, 0, 0, 0, w, h, 1);
            GL43C.glCopyImageSubData(rt.getDepthTextureId(), GL11C.GL_TEXTURE_2D, 0, 0, 0, 0, depth, GL11C.GL_TEXTURE_2D, 0, 0, 0, 0, w, h, 1);
            GlStateManager._bindTexture(color); GL30C.glGenerateMipmap(GL11C.GL_TEXTURE_2D);

            float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false), t = level.getTimeOfDay(pt) * (float) (Math.PI * 2), rain = level.getRainLevel(pt);
            float sx = -(float) Math.sin(t), sy = (float) Math.cos(t); boolean sun = sy > -0.05f; if (!sun) { sx = -sx; sy = -sy; }
            float len = (float) Math.sqrt(sx * sx + sy * sy), strength = (sun ? 1f : 0.2f) * Math.min(1f, Math.max(0f, (sy / len + 0.05f) * 6f));
            Matrix4f proj = new Matrix4f(e.getProjectionMatrix()), view = new Matrix4f(e.getModelViewMatrix());
            Matrix4f invProj = new Matrix4f(proj).invert();
            org.joml.Matrix3f invView = new org.joml.Matrix3f(view).invert();
            org.joml.Vector4f s = new Matrix4f(proj).mul(view).transform(new org.joml.Vector4f(sx / len * 100f, sy / len * 100f, 0f, 1f));
            boolean front = s.w > 0.01f; float ssx = front ? s.x / s.w * 0.5f + 0.5f : 0f, ssy = front ? s.y / s.w * 0.5f + 0.5f : 0f;
            var cam = e.getCamera();
            var skyc = level.getSkyColor(cam.getPosition(), pt); float day = Math.max(0.1f, Math.min(1f, sun ? sy / len * 1.4f + 0.3f : 0.1f));
            boolean under = cam.getFluidInCamera() != net.minecraft.world.level.material.FogType.NONE;

            RenderSystem.disableDepthTest(); RenderSystem.depthMask(false); RenderSystem.disableBlend();
            final float fsx = sx, fsy = sy, flen = len, fstrength = strength, frain = rain, fssx = ssx, fssy = ssy, fday = day; final boolean ffront = front, fsun = sun, funder = under;
            java.util.function.IntConsumer setAll = p -> {
                float sx_ = fsx, sy_ = fsy;
            GL20C.glUniform1i(u(p, "u_Color"), 0); GL20C.glUniform1i(u(p, "u_Depth"), 1);
            float[] m16 = M16, m9 = M9;
            GL20C.glUniformMatrix4fv(u(p, "u_InvProj"), false, invProj.get(m16));
            GL20C.glUniformMatrix3fv(u(p, "u_InvView"), false, invView.get(m9));
            GL20C.glUniform4f(u(p, "u_Sun"), fsx / flen, fsy / flen, 0f, fstrength * (1f - 0.85f * frain));
            GL20C.glUniform4f(u(p, "u_SunScreen"), fssx, fssy, 0f, ffront && fssx > -0.3f && fssx < 1.3f && fssy > -0.3f && fssy < 1.3f && fsun ? 1f : 0f);
            GL20C.glUniform3f(u(p, "u_Horizon"), (float) Math.min(1, skyc.x * 0.8 + 0.16 * fday), (float) Math.min(1, skyc.y * 0.8 + 0.18 * fday), (float) Math.min(1, skyc.z * 0.8 + 0.20 * fday));
            GL20C.glUniform3f(u(p, "u_Zenith"), (float) skyc.x, (float) skyc.y, (float) skyc.z);
            GL20C.glUniform4f(u(p, "u_Params"), fday, frain, funder ? 1f : 0f, AethryonApi.clock().seconds());
            GL20C.glUniform1f(u(p, "u_Storm"), level.getThunderLevel(pt));
            GL20C.glUniform1f(u(p, "u_CloudFade"), 1f - Math.min(1f, AethryonApi.storm().fog() * 1.15f));
            GL20C.glUniform4f(u(p, "u_On"), on(IlluminateConfig.GRADE), on(IlluminateConfig.GLOW), on(IlluminateConfig.SHAFTS), on(IlluminateConfig.HAZE));
            GL20C.glUniform2f(u(p, "u_On2"), on(IlluminateConfig.CLOUDS), on(IlluminateConfig.UNDERWATER_FOG));
            float[] wf = UnderwaterColour.update(pt);
            GL20C.glUniform3f(u(p, "u_WaterFog"), wf[0], wf[1], wf[2]);
            var cpw = cam.getPosition(); GL20C.glUniform3f(u(p, "u_CamPos"), (float) (cpw.x % 4096.0), (float) cpw.y, (float) (cpw.z % 4096.0));
            // the clouds: where vanilla would put them
            float ch = level.effects().getCloudHeight();
            if (!Float.isNaN(ch)) ch = 250f;   // Alterra's land reaches far higher than vanilla's: the clouds start around y 250 and pile up to about 310
            boolean clouds = !Float.isNaN(ch) && mc.options.getCloudsType() != net.minecraft.client.CloudStatus.OFF;
            var cp = cam.getPosition();
            double ct = cloudClock(level, pt); long cgt = (long) Math.floor(ct); float cpt = (float) (ct - cgt);   // the clouds' own smooth clock (see cloudClock)
            double cxc = (cp.x + AethryonApi.clouds().driftX(cgt, cpt)) / 24.0, czc = (cp.z + AethryonApi.clouds().driftZ(cgt, cpt)) / 24.0 + 0.33;
                GL20C.glUniform1f(u(p, "u_CloudEvo"), AethryonApi.clouds().evolve(cgt, cpt));
            GL20C.glUniform4f(u(p, "u_CloudInfo"), (float) cxc, (float) czc, (float) (ch + 0.33 - cp.y), clouds ? 1f : 0f);
            var cc = level.getCloudColor(pt); GL20C.glUniform3f(u(p, "u_CloudColor"), (float) cc.x, (float) cc.y, (float) cc.z);
            GL20C.glUniform1i(u(p, "u_Clouds"), 2);
            int cloudTex = mc.getTextureManager().getTexture(net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/environment/clouds.png")).getId();
            GlUpload.bind(GL13C.GL_TEXTURE2, cloudTex);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR); GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL11C.GL_REPEAT); GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL11C.GL_REPEAT);
            };
            // the height map (leaves too) for shadows and shafts, and how much sun gets through the clouds here
            var camp = cam.getPosition();
            HeightMap.update(level, camp.x, camp.z);
            float sunVis = 1f;
            if (sun && sy / len > 0.05f) {
                double tUp = (250.0 + 20.0 - camp.y) / (sy / len);
                float cov = AethryonApi.clouds().cover(level, camp.x + sx / len * tUp, camp.z, pt);
                sunVis = 1f - Math.min(1f, cov * 1.15f);
            }
            final float fSunVis = sunVis;
            float quality = (float) Math.round(IlluminateConfig.get(IlluminateConfig.QUALITY));
            java.util.function.IntConsumer extra = p -> {
                GL20C.glUniform1i(u(p, "u_Height"), 4);
                GL20C.glUniform4f(u(p, "u_HeightInfo"), (float) (camp.x - HeightMap.originX), (float) (camp.z - HeightMap.originZ), (float) camp.y, HeightMap.ready ? 1f : 0f);
                GL20C.glUniform1f(u(p, "u_SunVis"), fSunVis);
                GL20C.glUniform1f(u(p, "u_Quality"), quality);
                var fd = new FunnelData(); float[] fn = fd.a; int fnc = fd.n;
                float[] fu = FUNNEL; java.util.Arrays.fill(fu, 0f);
                for (int i = 0; i < fnc; i++) {
                    fu[i * 8] = (float) (fn[i * 8] - camp.x); fu[i * 8 + 1] = (float) (fn[i * 8 + 1] - camp.y); fu[i * 8 + 2] = (float) (fn[i * 8 + 2] - camp.z); fu[i * 8 + 3] = fn[i * 8 + 3];
                    fu[i * 8 + 4] = fn[i * 8 + 4]; fu[i * 8 + 5] = fn[i * 8 + 5]; fu[i * 8 + 6] = fn[i * 8 + 6]; fu[i * 8 + 7] = fn[i * 8 + 7];
                }
                GL20C.glUniform4fv(u(p, "u_Funnel"), fu); GL20C.glUniform1f(u(p, "u_FunnelN"), fnc);
                GL20C.glUniform1f(u(p, "u_Shadows"), IlluminateConfig.get(IlluminateConfig.SHADOWS) ? 1f : 0f);
            };
            GlUpload.bind(GL13C.GL_TEXTURE4, HeightMap.texture()); GlStateManager._activeTexture(GL13C.GL_TEXTURE0);
            // 1. the clouds, into their own half-size picture
            boolean cloudsOn = IlluminateConfig.get(IlluminateConfig.CLOUDS) || IlluminateConfig.get(IlluminateConfig.SHADOWS);   // the half-size pass draws the clouds and the shafts under trees
            cloudTarget(w, h);
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, cloudFbo);
            GlStateManager._viewport(0, 0, cw, ch2);
            GL11C.glClearColor(0f, 0f, 0f, 0f); GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);
            GlUpload.bind(GL13C.GL_TEXTURE1, depth);
            GlUpload.bind(GL13C.GL_TEXTURE0, color);
            if (cloudsOn) {
                GlStateManager._glUseProgram(cloudProgram); setAll.accept(cloudProgram); extra.accept(cloudProgram);
                GL30C.glBindVertexArray(vao); GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3); GL30C.glBindVertexArray(0);
            }
            // 2. everything else, with the clouds blended in
            rt.bindWrite(true);
            GlStateManager._glUseProgram(program); setAll.accept(program); extra.accept(program);
            GL20C.glUniform1i(u(program, "u_CloudTex"), 3);
            GlUpload.bind(GL13C.GL_TEXTURE3, cloudTex);
            GlUpload.bind(GL13C.GL_TEXTURE1, depth);
            GlUpload.bind(GL13C.GL_TEXTURE0, color);
            GL30C.glBindVertexArray(vao);
            GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);
            GL30C.glBindVertexArray(0);
            GlUpload.bind(GL13C.GL_TEXTURE4, 0);
            GlUpload.bind(GL13C.GL_TEXTURE3, 0);
            GlUpload.bind(GL13C.GL_TEXTURE2, 0); GlStateManager._activeTexture(GL13C.GL_TEXTURE0);
            com.mojang.blaze3d.vertex.BufferUploader.invalidate();
            GlStateManager._glUseProgram(0);
            GlStateManager._bindTexture(0);
            RenderSystem.depthMask(true); RenderSystem.enableDepthTest();
        } catch (Throwable ex) { failed = true; LOG.warn("Alterra Weather: the scene look is off, it could not be set up", ex); }
    }
}
