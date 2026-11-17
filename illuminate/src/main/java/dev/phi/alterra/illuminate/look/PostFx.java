package dev.phi.alterra.illuminate.look;

import dev.phi.prismlib.api.PrismApi;
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
    private static int lastFlashTime; private static long flashStart = -1;
    private static float pulse(float t, float t0, float attack, float decay) { float x = t - t0; if (x < 0f) return 0f; if (x < attack) return x / attack; return (float) Math.exp(-(x - attack) / decay); }
    /** A lightning flash as light: it starts when the game flashes the sky (which it does for only a couple of ticks), rises in a few hundredths of a second, flickers once more, and is gone within half a second. */
    private static float flashNow(net.minecraft.client.multiplayer.ClientLevel level) {
        int ft = level.getSkyFlashTime(); long now = System.nanoTime() / 1_000_000L;
        if (ft > 0 && lastFlashTime == 0) flashStart = now;
        lastFlashTime = ft;
        if (flashStart < 0) return 0f;
        float t = (now - flashStart) / 1000f;
        if (t > 0.8f) return 0f;
        return Math.min(1f, pulse(t, 0f, 0.025f, 0.07f) + 0.55f * pulse(t, 0.17f, 0.02f, 0.10f));
    }
    private PostFx() {}
    private static final Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static int program = -1, vao, color, depth, w, h; private static boolean failed;
    private static final java.util.Map<Integer, java.util.Map<String, Integer>> LOCS = new java.util.HashMap<>();
    private static double smoothT = Double.NaN; private static long smoothAt;
    /** The time the clouds move by, in game ticks: runs with real time and is pulled gently towards the game's own time. The game's time is set again by the server every so often, and when the game lags that is a jump of several ticks:
     *  the whole sky stepped. This one never jumps (a big difference, a time change or a pause, is followed at once). */
    public static double cloudClock(net.minecraft.client.multiplayer.ClientLevel level, float pt) {
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
    public static boolean drawsClouds() { return dev.phi.alterra.illuminate.clouds.SkyClouds.active(); }

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
            uniform mat4 u_InvProj, u_Proj; uniform mat3 u_InvView;
            uniform vec4 u_Sun;        // sun (or moon) direction in the world, how strong
            uniform vec4 u_SunScreen;  // where the sun is on screen, 1 when it is in front
            uniform vec3 u_Horizon, u_Zenith;
            uniform vec4 u_Params;     // daylight, rain, underwater, time (seconds)
            uniform sampler2D u_Clouds;   // vanilla's cloud map: a cell of 12 by 12 blocks per pixel
            uniform float u_CloudStyle;   // 0 vanilla+ (rounded blocks), 1 volumetric
            uniform sampler2D u_CloudGrid;   // the large-scale clouds: cover, head height, underside, character (see CloudGrid)
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
            uniform float u_Flash;   // a lightning flash right now (0 to 1): rises in a few hundredths of a second, flickers, dies away fast
            uniform vec4 u_Cells[4];   // regional storm cells: x, z relative to the camera, radius, level
            uniform float u_CellsN;
            uniform vec4 u_On;    // colour grade, glow, light shafts, haze
            uniform vec2 u_On2;   // clouds, underwater fog     // thunder, 0 to 1
            uniform sampler2DShadow u_ShadowMap;   // the world seen from the sun (or moon): depth
            uniform mat4 u_ShadowVP; uniform vec3 u_ShadowCam; uniform float u_ShadowOn;
            uniform float u_InCloud;   // how deep inside a cloud the camera is (0 to 1)
            uniform float u_Surf;      // how far the water surface is above the camera when under water (blocks)
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
            float topAtF(vec3 p) {   // the same, smoothed between the texels (heights are whole blocks: a nearest lookup made blocky shadow edges)
                vec2 c = p.xz + u_HeightInfo.xy - 0.5;
                if (u_HeightInfo.w < 0.5 || c.x < 0.0 || c.y < 0.0 || c.x >= 127.0 || c.y >= 127.0) return -1e4;
                ivec2 i = ivec2(floor(c)); vec2 f = fract(c); f = f * f * (3.0 - 2.0 * f);
                float a = texelFetch(u_Height, i, 0).r, b = texelFetch(u_Height, i + ivec2(1, 0), 0).r, cc = texelFetch(u_Height, i + ivec2(0, 1), 0).r, d = texelFetch(u_Height, i + ivec2(1, 1), 0).r;
                return mix(mix(a, b, f.x), mix(cc, d, f.x), f.y) * 65535.0 - 64.0 - u_HeightInfo.z;
            }
            float sunlit(vec3 p, float n) {   // the far part of the shadow: 1 in the sun, 0 in the shade of trees, overhangs or terrain between this point and the sun, from the height of everything around. One ray, finely stepped, and its edge is as soft as one step's rise (several offset rays made overlapping copies of every shadow)
                float hit = 0.0, rise = 0.25 + 1.2 * max(u_Sun.y, 0.25);
                for (int k = 1; k <= 14; k++) {
                    vec3 q = p + u_Sun.xyz * (float(k) * 1.0 + 0.4);
                    float over = topAtF(q) - q.y;
                    hit = max(hit, smoothstep(0.04 * float(k), 0.04 * float(k) + rise, over));
                }
                return 1.0 - hit;
            }
            vec2 shadowFromMap(vec3 wpr, vec3 nW) {   // x: 1 in the light, 0 in shadow (soft filtered edges); y: how much the map counts here (0 outside it, fading in over its border)
                vec3 p = wpr + u_ShadowCam + nW * 0.07;   // moved off the surface along its normal, so a face does not shadow itself
                vec4 cl = u_ShadowVP * vec4(p, 1.0); vec3 sc = cl.xyz / cl.w * 0.5 + 0.5;
                float edge = min(min(sc.x, 1.0 - sc.x), min(sc.y, 1.0 - sc.y));
                if (edge < 0.0 || sc.z > 1.0) return vec2(1.0, 0.0);
                float ndl = max(dot(nW, u_Sun.xyz), 0.0);
                float bias = 0.00035 + 0.0010 * (1.0 - ndl);
                const vec2 K[12] = vec2[12](vec2(-0.326, -0.406), vec2(-0.840, -0.074), vec2(-0.696, 0.457), vec2(-0.203, 0.621), vec2(0.962, -0.195), vec2(0.473, -0.480), vec2(0.519, 0.767), vec2(0.185, -0.893), vec2(0.507, 0.064), vec2(0.896, 0.412), vec2(-0.322, -0.933), vec2(-0.792, -0.598));
                float r = 2.6 / 2048.0, lit = 0.0;
                for (int i = 0; i < 12; i++) lit += texture(u_ShadowMap, vec3(sc.xy + K[i] * r, sc.z - bias));
                lit /= 12.0;
                return vec2(lit, smoothstep(0.0, 0.06, edge));
            }
            float sunScreen(vec3 vp, vec3 nrm) {   // the near part: a ray from the surface towards the sun through the depth buffer, so whatever is drawn casts a shadow (leaves, entities, ships, anything that moves), with no map to go blocky
                vec3 sv = transpose(u_InvView) * u_Sun.xyz;
                if (dot(nrm, sv) < 0.02) return 1.0;   // facing away from the sun: its own light already makes it dark
                float dd = length(vp), occ = 0.0, t = 0.04, stepL = 0.05;
                vec3 start = vp + nrm * (0.02 + 0.012 * dd);
                for (int i = 0; i < 20; i++) {   // only the first few blocks: contact shadows (what is drawn cannot tell what is off the screen, so a long reach made shadows that moved with the view; the height map owns the long shadows)
                    t += stepL; stepL *= 1.12;
                    vec3 q = start + sv * t;
                    vec4 cl = u_Proj * vec4(q, 1.0);
                    if (cl.w <= 0.01) break;
                    vec2 suv = cl.xy / cl.w * 0.5 + 0.5;
                    if (suv.x < 0.0 || suv.y < 0.0 || suv.x > 1.0 || suv.y > 1.0) break;
                    float sd = texture(u_Depth, suv).r;
                    if (sd >= 0.99999) continue;
                    vec4 sp = u_InvProj * vec4(suv * 2.0 - 1.0, sd * 2.0 - 1.0, 1.0);
                    float diff = sp.z / sp.w - q.z;   // > 0: the surface drawn there is nearer than the ray, so the ray is behind it
                    float thick = 0.8 + 0.4 * t;      // something much nearer than the ray is not what shades this point
                    occ = max(occ, smoothstep(0.03 + 0.02 * t, 0.16 + 0.06 * t, diff) * (1.0 - smoothstep(thick, thick * 2.0 + 1.0, diff)));
                }
                return 1.0 - occ;
            }
            float cellCover(vec2 rel) {   // how strongly a storm cell is over this point (as StormCell.at)
                float s = 0.0;
                for (int i = 0; i < 4; i++) {
                    if (float(i) >= u_CellsN) break;
                    vec4 c = u_Cells[i]; float d = length(rel - c.xy) / c.z;
                    float k = clamp((d - 0.3) / 0.7, 0.0, 1.0);
                    s = max(s, d >= 1.0 ? 0.0 : c.w * (1.0 - k * k * k * (k * (k * 6.0 - 15.0) + 10.0)));
                }
                return s;
            }
            float cloudShade(vec3 p) {   // the shadow of the clouds overhead along the sun (the same map as the clouds)
                if (u_CloudInfo.w < 0.5 || u_Sun.y < 0.05) return 1.0;
                vec3 at = p + u_Sun.xyz * ((u_CloudInfo.z + 20.0 - p.y) / u_Sun.y);
                vec3 q = vec3(u_CloudInfo.x * 24.0 + at.x, 0.0, u_CloudInfo.y * 24.0 + at.z);
                float cov = smoothstep(0.47, 0.62, texture(u_CloudGrid, q.xz * (1.0 / 6144.0)).r + 0.16 * u_Params.y + 0.22 * u_Storm + 0.55 * cellCover(at.xz));
                return 1.0 - 0.55 * cov;
            }
            float stormTall() { float m = 0.0; for (int i = 0; i < 4; i++) { if (float(i) >= u_CellsN) break; m = max(m, u_Cells[i].w); } return m * 300.0; }   // how far above the cloud layer a storm's tower can reach
            // a storm cell's own cloud: a tall dark tower over its heart that widens into an anvil at the top (p relative to the camera, h above the layer's base)
            float stormTower(vec3 p, float h, float detail) {
                float best = 0.0;
                for (int i = 0; i < 4; i++) {
                    if (float(i) >= u_CellsN) break;
                    vec4 c = u_Cells[i]; if (c.w < 0.12) continue;
                    float ht = 70.0 + 300.0 * c.w, rc = c.z * (0.16 + 0.10 * c.w);
                    float u = clamp(h / ht, 0.0, 1.0);
                    float wide = rc * (0.50 + 0.55 * smoothstep(0.0, 0.7, u) + 1.6 * smoothstep(0.72, 0.93, u) * (1.0 - smoothstep(0.93, 1.0, u)));   // a column that swells, then the anvil spreads out under the top
                    float d = length(p.xz - c.xy);
                    float prof = smoothstep(wide, wide * 0.55, d) * smoothstep(0.0, 14.0, h) * (1.0 - smoothstep(0.90, 1.0, u));
                    best = max(best, max(0.0, prof - (1.0 - detail) * (0.65 - 0.35 * prof)) * 1.9);
                }
                return best;
            }
            // Vanilla+: the game's blocky clouds, rounded and properly shaped. The cloud grid's 24-block cells that hold cloud are rounded blocks as tall as their cloud is thick (steps of 3 blocks).
            // A ray walks the cells exactly (no steps through the air, so blocks are solid at any distance) and where it meets one it finds the rounded block's surface and lights it by its normal.
            float sdRoundBox(vec3 p, vec3 h, float r) { vec3 d = abs(p) - h; return length(max(d, 0.0)) + min(max(d.x, max(d.y, d.z)), 0.0) - r; }
            float cellFill(vec2 cc, vec2 org, float rain) { return smoothstep(0.47, 0.62, texture(u_CloudGrid, cc * (1.0 / 6144.0)).r + 0.16 * rain + 0.22 * u_Storm + 0.55 * cellCover(cc - org)); }
            vec4 vanillaClouds(vec3 dir, float y0, float t0, float tMax, float day, float rain, float toSun) {
                vec2 org = u_CloudInfo.xy * 24.0;   // the camera in cloud space (x, z)
                vec2 stepv = vec2(dir.x >= 0.0 ? 1.0 : -1.0, dir.z >= 0.0 ? 1.0 : -1.0);
                vec2 inv = vec2(abs(dir.x) > 1e-5 ? 1.0 / dir.x : 1e5, abs(dir.z) > 1e-5 ? 1.0 / dir.z : 1e5);
                vec2 p2 = org + dir.xz * t0;
                vec2 cell = floor(p2 / 24.0);
                vec2 tDelta = abs(24.0 * inv);
                vec2 nextB = (cell + max(stepv, 0.0)) * 24.0;
                vec2 tNext = (nextB - org) * inv;
                vec3 sunCol = u_CloudColor * (0.92 + 0.10 * day), shade = mix(u_CloudColor * 0.62, vec3(dot(u_Horizon, vec3(0.3, 0.5, 0.2))) * vec3(0.9, 0.94, 1.0), 0.35);
                float storm0 = clamp(rain * 0.8 + u_Storm, 0.0, 1.0);
                float T = 1.0, t = t0; vec3 col = vec3(0.0);
                for (int i = 0; i < 72; i++) {
                    if (t > tMax) break;
                    float tExit = min(min(tNext.x, tNext.y), tMax);
                    vec2 cc = (cell + 0.5) * 24.0, rel = cc - org;
                    float cover = texture(u_CloudGrid, cc * (1.0 / 6144.0)).r + 0.16 * rain + 0.22 * u_Storm + 0.55 * cellCover(rel);
                    float c = smoothstep(0.47, 0.62, cover);
                    if (c > 0.02) {
                        float hgt = floor((8.0 + 24.0 * c) / 3.0) * 3.0;
                        // a cell next to another cell of cloud runs on into it (and is not rounded there): clouds are masses of joined blocks, rounded only on their outside
                        float eN = cellFill(cc + vec2(-24.0, 0.0), org, rain) > 0.02 ? 7.0 : 0.0, eP = cellFill(cc + vec2(24.0, 0.0), org, rain) > 0.02 ? 7.0 : 0.0;
                        float fN = cellFill(cc + vec2(0.0, -24.0), org, rain) > 0.02 ? 7.0 : 0.0, fP = cellFill(cc + vec2(0.0, 24.0), org, rain) > 0.02 ? 7.0 : 0.0;
                        vec3 ctr = vec3(rel.x + (eP - eN) * 0.5, y0 + hgt * 0.5, rel.y + (fP - fN) * 0.5), hf = vec3(12.0 + (eP + eN) * 0.5, hgt * 0.5, 12.0 + (fP + fN) * 0.5);
                        float tt = max(t - 6.0, 0.0); bool hit = false;
                        for (int k = 0; k < 10; k++) {   // along the ray to the surface of the rounded block (sphere tracing)
                            float d = sdRoundBox(dir * tt - ctr, hf - 5.0, -5.0) ;
                            d = sdRoundBox(dir * tt - ctr, hf - 5.0, 0.0) - 5.0;
                            if (d < 0.25) { hit = true; break; }
                            tt += max(d, 0.4);
                            if (tt > tExit + 14.0) break;
                        }
                        if (hit) {
                            vec3 p = dir * tt - ctr; vec2 e = vec2(0.6, 0.0);
                            vec3 n = normalize(vec3(sdRoundBox(p + e.xyy, hf - 5.0, 0.0) - sdRoundBox(p - e.xyy, hf - 5.0, 0.0), sdRoundBox(p + e.yxy, hf - 5.0, 0.0) - sdRoundBox(p - e.yxy, hf - 5.0, 0.0), sdRoundBox(p + e.yyx, hf - 5.0, 0.0) - sdRoundBox(p - e.yyx, hf - 5.0, 0.0)));
                            float up = 0.5 + 0.5 * n.y, sunF = max(dot(n, u_Sun.xyz), 0.0);
                            float storm = clamp(storm0 + cellCover(rel) * 0.9, 0.0, 1.0);
                            vec3 shadeS = shade * mix(1.0, 0.50, storm), sunS = sunCol * mix(1.0, 0.74, storm);
                            vec3 lit = mix(shadeS, sunS, smoothstep(0.25, 0.95, 0.65 * up + 0.35 * sunF)) + vec3(1.0, 0.95, 0.86) * pow(toSun, 8.0) * u_Sun.w * 0.2 * up;
                            float a = 0.88;
                            col += lit * a * T; T *= 1.0 - a;
                            if (T < 0.05) break;
                        }
                    }
                    if (tNext.x < tNext.y) { cell.x += stepv.x; t = tNext.x; tNext.x += tDelta.x; } else { cell.y += stepv.y; t = tNext.y; tNext.y += tDelta.y; }
                }
                float far = 1.0 - smoothstep(1100.0, 1600.0, t0);
                vec3 hz = u_Horizon * (0.55 + 0.45 * day);
                float haze = smoothstep(500.0, 1500.0, t0) * 0.5;
                return vec4(mix(col, hz * (1.0 - T), haze) * far, (1.0 - T) * far);
            }
            float cloud(vec3 p, float y0) {   // p: relative to the camera. Natural cumulus: where they are comes from the cloud grid (layered noise, no pattern), more of them when it rains; each region has its own character (low and flat to tall), and storm cells have towers. Fine detail only where it can be seen
                vec3 q = vec3(u_CloudInfo.x * 24.0 + p.x, p.y - y0, u_CloudInfo.y * 24.0 + p.z);   // drifts with the wind
                vec4 g = texture(u_CloudGrid, q.xz * (1.0 / 6144.0));
                float cc = cellCover(p.xz);
                float cover = g.r + 0.16 * u_Params.y + 0.22 * u_Storm + 0.55 * cc;   // as CloudMap.cover: rain brings more cloud, thunder nearly closes the sky
                float cov = smoothstep(0.47, 0.62, cover);
                float h = q.y;
                float near = 1.0 - smoothstep(500.0, 1500.0, length(p.xz));   // the cauliflower billows are worked out only for what is near: further away the clouds are smooth shapes
                float detail = mix(0.62, n3(q * 0.05) * 0.62 + n3(q * 0.14 + 7.0) * 0.38, near);
                float tower = stormTower(p, h, detail);
                if (cov < 0.01) return tower;
                float tallness = mix(0.55, 1.45, g.a);
                float top = 6.0 + 84.0 * tallness * cov * cov * (0.55 + 0.45 * g.g);   // tall domes in the middle of each cloud: about as tall as it is wide
                float base = 1.0 + 9.0 * g.b * (1.0 - 0.5 * cov);   // a ragged underside, not a flat ceiling
                float shape = smoothstep(base, base + 7.0, h) * smoothstep(top, top - 0.5 * top, h);   // flat base, rounded heads
                float cumulus = max(0.0, shape * cov - (1.0 - detail) * (0.55 - 0.25 * cov)) * 1.8;
                return max(cumulus, tower);
            }
            float uwBeam(vec2 p, float t) {   // sun beams under water: bright bands a few blocks wide that drift and sway
                vec2 q = p * 0.55 + vec2(t * 0.30, t * 0.23);
                float a = 0.5 + 0.5 * sin(q.x + 1.8 * sin(q.y * 0.8 + t * 0.5)) * sin(q.y * 1.1 + 1.5 * sin(q.x * 0.7 - t * 0.45));
                float b = 0.5 + 0.5 * sin(q.x * 2.3 - q.y * 1.7 + t * 0.9) * sin(q.y * 2.1 + q.x * 1.3 - t * 0.7);
                return pow(a, 5.0) * (0.55 + 0.45 * b);
            }
            float uwCaustic(vec2 p, float t) {   // the fine web of bright lines the waves make on what lies below them
                vec2 q = p * 1.35 + vec2(t * 0.35, t * 0.27);
                float a = 0.5 + 0.5 * sin(q.x + 2.0 * sin(q.y * 0.9 + t * 0.7)) * sin(q.y * 1.1 + 1.7 * sin(q.x * 0.8 - t * 0.6));
                vec2 r = p * 2.1 - vec2(t * 0.22, t * 0.31);
                float b = 0.5 + 0.5 * sin(r.x + 2.0 * sin(r.y * 0.9 - t * 0.6)) * sin(r.y * 1.1 + 1.7 * sin(r.x * 0.8 + t * 0.5));
                return pow(a, 6.0) * 0.75 + pow(b, 6.0) * 0.5;
            }
            vec3 aces(vec3 x) { return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0); }
            void main() {
                vec3 c = texture(u_Color, uv).rgb;
                float d = texture(u_Depth, uv).r; bool sky = d >= 0.99999;
                vec4 vp = u_InvProj * vec4(uv * 2.0 - 1.0, d * 2.0 - 1.0, 1.0); vp /= vp.w;
                vec3 dir = normalize(u_InvView * vp.xyz); float dist = length(vp.xyz);
                vec3 vnrm = normalize(cross(dFdx(vp.xyz), dFdy(vp.xyz))); if (dot(vnrm, vp.xyz) > 0.0) vnrm = -vnrm;   // the surface's normal in view space, taken here where every pixel runs it
                #ifdef CLOUD_PASS
                {   // the cloud pass draws into the sky map, not the screen: this texel's direction (azimuth round, elevation from just below the horizon to straight up, packed towards the horizon where the clouds are small)
                    float az = (uv.x * 2.0 - 1.0) * 3.14159265, el = mix(-0.10, 1.5707963, pow(uv.y, 1.7));
                    dir = vec3(sin(az) * cos(el), sin(el), cos(az) * cos(el)); dist = 1e9; sky = true;
                }
                #endif
                float day = u_Params.x, rain = u_Params.y;
                float toSun = max(dot(dir, u_Sun.xyz), 0.0);
                #ifdef CLOUD_PASS
                vec4 cloudOut = vec4(0.0);
                // clouds: vanilla's cloud map (same places, same drift) grown into puffy clouds: a flat base, rounded billowing tops that rise higher where the map is solid,
                // broken into puffs by noise, each point lit by how much cloud lies between it and the sun (dark cores, sunlit tops, a bright rim towards the sun)
                bool insideC = u_CloudInfo.z < 0.0 && u_CloudInfo.z + 92.0 > 0.0;   // the camera is in the cloud layer
                if (u_CloudInfo.w > 0.5 && u_On2.x > 0.5 && (abs(dir.y) > 0.0005 || insideC) && u_Params.z < 0.5) {
                    float y0 = u_CloudInfo.z, y1 = y0 + (u_CloudStyle < 0.5 ? 34.0 : 92.0 + stormTall());
                    float ta = abs(dir.y) > 0.0005 ? y0 / dir.y : -1e9, tb = abs(dir.y) > 0.0005 ? y1 / dir.y : 1e9, t0 = max(min(ta, tb), 0.0), t1 = max(ta, tb);
                    float tEnd = insideC ? min(t1, sky ? 1e9 : dist) : t1;   // from outside the layer: clouds everywhere, as if open sky (the full-size pass shows them only where the sky really is); from inside, they stop at what stands in
                    float tMax = min(tEnd, t0 + (u_CloudStyle < 0.5 ? 1700.0 : 6000.0));
                    if (u_CloudStyle < 0.5) { if (tMax > t0) cloudOut = vanillaClouds(dir, y0, t0, tMax, day, rain, toSun); }
                    else if (tMax > t0 && t0 < 3100.0) {
                        float jit = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));   // a small, even offset per pixel (interleaved gradient noise): no stair-step slices, and the soft blend hides it
                        vec3 sunCol = u_CloudColor * (0.9 + 0.12 * day), shade = mix(u_CloudColor * 0.62, vec3(dot(u_Horizon, vec3(0.3, 0.5, 0.2))) * vec3(0.9, 0.94, 1.0), 0.35);   // toned in their own pass: soft whites, light grey shaded undersides
                        float storm0 = clamp(rain * 0.8 + u_Storm, 0.0, 1.0);   // rain clouds go dark grey underneath, thunderheads darker still
                        int maxSteps = 40 + int(8.0 * u_Quality);
                        float T = 1.0, sunDen = -1.0, t = t0 + jit * max(3.0, t0 * 0.08); vec3 col = vec3(0.0);
                        for (int i = 0; i < 80; i++) {
                            if (i >= maxSteps || t > tMax) break;
                            float dtS = clamp(t * 0.10 + 3.0, 4.0, 300.0);   // vanilla+: a few even steps through the thin layer; volumetric: steps that grow with distance (about the same on screen)   // steps grow with distance (about the same on screen), so a cloud is as dense near as far: the density is integrated over the real length of each step
                            vec3 p = dir * t;
                            float dn = cloud(p, y0);
                            if (dn > 0.01) {
                                if ((i & 1) == 0 || sunDen < 0.0) sunDen = cloud(p + u_Sun.xyz * 12.0, y0) * 1.8;   // cloud between this point and the sun (every other step: it changes slowly)
                                float hf = smoothstep(0.0, 1.0, clamp((p.y - y0) / 34.0, 0.0, 1.0));
                                float light = smoothstep(0.18, 0.78, 0.7 * hf + 0.3 * exp(-sunDen * 0.8));   // painted: mostly by height (a light grey flat base, white heads) with a little sun shading, pressed into two soft tones
                                float sc = cellCover(p.xz), storm = clamp(storm0 + sc * 0.9, 0.0, 1.0);   // under a storm cell the cloud is dark
                                vec3 shadeS = shade * mix(1.0, 0.50, storm), sunS = sunCol * mix(1.0, 0.74, storm);
                                vec3 lit = mix(shadeS, sunS, light) + vec3(1.0, 0.95, 0.86) * pow(toSun, 8.0) * u_Sun.w * 0.25 * light;
                                float a = 1.0 - exp(-dn * 0.034 * dtS);
                                col += lit * a * T; T *= 1.0 - a;
                                if (T < 0.03) break;
                            }
                            t += dtS;
                        }
                        float far = 1.0 - smoothstep(2300.0, 3000.0, t0);   // clouds beyond the grid thin out into the haze
                        float alpha = (1.0 - T) * far;
                        vec3 hz = u_Horizon * (0.55 + 0.45 * day);
                        float haze = smoothstep(900.0, 2800.0, t0) * 0.55;   // and take on the colour of the air
                        cloudOut = vec4(mix(col, hz * (1.0 - T), haze) * far, alpha);
                    }
                }
                frag = cloudOut; return;
                #endif
                // sunlight: soft shadows of trees and overhangs, the clouds' shadows drifting over the land, and a dimmer, softer day when a cloud covers the sun
                if (!sky && u_Params.z < 0.5 && u_Sun.w > 0.02 && (u_Shadows > 0.5 || u_On2.x > 0.5)) {
                    vec3 wpr = dir * dist;
                    vec2 shM = (u_ShadowOn > 0.5 && u_Shadows > 0.5) ? shadowFromMap(wpr, normalize(u_InvView * vnrm)) : vec2(1.0, 0.0);
                    float shH = (shM.y < 0.999 && dist < 72.0 && u_Shadows > 0.5) ? sunlit(wpr, 1.0 + u_Quality * 1.5) : 1.0;
                    float sh = mix(shH, shM.x, shM.y);   // the shadow map near the camera; the height map beyond it
                    if (u_ShadowOn > 1.5) { frag = vec4(vec3(sh * 0.9 + 0.1 * shM.y), 1.0); return; }   // (test view: the shadow alone)
                    float cs = u_On2.x > 0.5 ? cloudShade(wpr) : 1.0;
                    float light = mix(1.0, min(sh, cs), 0.58 * u_Sun.w * max(day, 0.5));
                    c *= light;
                }
                // haze: the air between you and far things, warm towards the sun, the sky's own colour away from it
                if (!sky && u_Params.z < 0.5 && u_On.w > 0.5) {
                    vec3 hz = mix(u_Horizon, vec3(1.0, 0.86, 0.66) * (0.6 + 0.6 * day), pow(toSun, 14.0) * u_Sun.w * 0.5);
                    float a = (1.0 - exp(-max(dist - 48.0, 0.0) * (0.0012 + 0.004 * rain))) * (0.45 + 0.35 * rain);   // only far away
                    c = mix(c, hz, a * 0.2);
                }
                #ifndef CLOUD_PASS
                #endif
                #ifndef CLOUD_PASS
                // disasters, drawn after the clouds so nothing hides them: a tornado or dust devil is a column that flares out upwards (a few fixed samples along the ray: steady, no shimmer),
                // a whirlpool is a spiral lying on the water: a dark centre and arms of foam turning slowly
                for (int fi = 0; fi < 4; fi++) {
                    if (float(fi) >= u_FunnelN) break;
                    vec4 fa = u_Funnel[fi * 2], fb = u_Funnel[fi * 2 + 1];
                    float farFunnel = length(fa.xz); if (farFunnel > 700.0) continue;   // a funnel is not drawn far away: it fades out from 400 blocks and is gone by 700
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
                        float dens = (body * 2.6 + skirt * 1.0) * fb.y * (1.0 - smoothstep(190.0, 250.0, fp.y + u_CamPos.y)) * (1.0 - smoothstep(400.0, 700.0, farFunnel));   // a funnel ends in the cloud base (the clouds are drawn before this)
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
                    vec3 sunRefr = normalize(vec3(u_Sun.x * 0.75, max(u_Sun.y, 0.35), u_Sun.z * 0.75));   // the sun's light bent into the water
                    float sunK = clamp(u_Sun.w, 0.0, 1.0) * (0.2 + 0.8 * day);
                    if (!sky) {
                        float depthBelow = max(u_Surf - dir.y * dist, 0.0);   // how far under the surface this point is
                        vec2 sp = wp.xz + sunRefr.xz / sunRefr.y * depthBelow;   // where on the surface the light that reaches it came through
                        c *= 1.0 + 0.22 * sunK * exp(-depthBelow * 0.05) + uwCaustic(sp, u_Params.w) * 1.7 * (0.3 + 0.7 * upF) * exp(-depthBelow * 0.06) * smoothstep(0.0, 0.7, depthBelow) * exp(-dist * 0.025) * sunK;
                    }
                    float fogA = (1.0 - exp(-dist * 0.022)) * 0.85;
                    vec3 fogC = u_WaterFog;   // the biome's water colour, clear and turquoise, darker at night (UnderwaterColour: the same as the fog)
                    c = mix(c, fogC, sky ? 0.8 : fogA);
                    {   // sun shafts: march along the view through the water, adding the light of the beams that cross it (the beams are the sun's light coming down through the waves)
                        float tmax = min(sky ? 36.0 : dist, 36.0), shaft = 0.0;
                        float face = 0.30 + 0.70 * pow(max(dot(dir, sunRefr), 0.0), 2.0);   // strongest looking up towards the light
                        for (int si = 0; si < 10; si++) {
                            float ts = (float(si) + 0.5) * tmax / 10.0;
                            float dep = u_Surf - dir.y * ts;
                            if (dep < 0.0) break;
                            vec2 sq = wp.xz - dir.xz * dist + dir.xz * ts + sunRefr.xz / sunRefr.y * dep;
                            shaft += uwBeam(sq, u_Params.w) * exp(-dep * 0.05) * exp(-ts * 0.035) * smoothstep(0.0, 1.0, dep);
                        }
                        c += vec3(0.46, 0.84, 0.88) * shaft * (tmax / 10.0) * 0.14 * face * sunK;
                    }
                }
                if (u_InCloud > 0.005 && u_Params.z < 0.5) {   // inside a cloud: everything fades into mist with distance, in thicker and thinner pockets that drift past with the wind
                    float dd = sky ? 300.0 : dist;
                    vec3 wq = u_CamPos + dir * min(dd, 90.0) + vec3(u_Params.w * 1.6, 0.0, u_Params.w * 1.0);
                    float nn = 0.6 * n3(wq * 0.045) + 0.4 * n3(wq * 0.13 + 7.0);
                    float dens = 0.45 + 1.1 * nn;
                    float mist = (1.0 - exp(-dd * 0.020 * dens)) * u_InCloud;
                    vec3 inCol = mix(u_Horizon, vec3(1.0), 0.78) * (0.66 + 0.26 * day) * (1.0 - 0.62 * u_Storm) * (0.93 + 0.14 * nn);   // milky white, lit by the sky: bright but never blown out, dark grey in a storm
                    c = mix(c, inCol, min(mist, 0.97));
                }
                // glow: the bright parts of the picture, softly spread
                vec3 g = u_On.y < 0.5 ? vec3(0.0) : textureLod(u_Color, uv, 3.0).rgb * 0.4 + textureLod(u_Color, uv, 4.5).rgb * 0.35 + textureLod(u_Color, uv, 6.0).rgb * 0.25;
                c += max(g - 0.86, 0.0) * 0.16;   // only truly bright things glow, and gently
                // light shafts: march towards the sun on screen and count the sky seen along the way
                if (u_SunScreen.w > 0.5 && u_Sun.w > 0.01 && u_Params.z < 0.5 && u_On.z > 0.5) {
                    vec2 to = u_SunScreen.xy - uv; float rays = 0.0, wgt = 1.0;
                    float wsum = 0.0;
                    for (int i = 0; i < 32; i++) {
                        vec2 q = uv + to * (float(i) + 0.5) / 32.0;
                        if (q.x < 0.0 || q.y < 0.0 || q.x > 1.0 || q.y > 1.0) break;
                        rays += step(0.99999, texture(u_Depth, q).r) * wgt; wsum += wgt; wgt *= 0.955;
                    }
                    float near = 1.0 - smoothstep(0.05, 0.7, length(to * vec2(1.4, 1.0)));
                    float sh = rays / max(wsum, 1.0);
                    float beam = 4.0 * sh * (1.0 - sh);   // shafts live where the way to the sun is partly blocked (leaves, trunks, edges): open sky or full shade gives none, so looking at the sun does not flood the screen
                    c += vec3(1.0, 0.86, 0.62) * beam * near * u_Sun.w * (1.0 - rain) * 0.42 * u_SunVis;   // (weaker when a cloud covers the sun)   // weaker when a cloud covers the sun
                }
                // the sky: a deeper blue overhead, so it is not a pale wash (the sun's glow is left alone)
                if (sky && u_Params.z < 0.5) {
                    float up = clamp(dir.y, 0.0, 1.0);
                    c = mix(c, c * vec3(0.78, 0.9, 1.08), smoothstep(0.05, 0.6, up) * day * (1.0 - rain) * (1.0 - pow(toSun, 12.0)));
                }
                if (u_On.x < 0.5) { frag = vec4(clamp(c, 0.0, 1.0), 1.0); return; }
                c += u_Flash * vec3(0.50, 0.56, 0.75) * (sky ? 0.55 : 0.35 + 0.35 * exp(-dist * 0.012)) * 1.0;   // the flash lights the sky and the land, the near land most
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
        vao = GL30C.glGenVertexArrays();
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

            float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false), t = level.getTimeOfDay(pt) * (float) (Math.PI * 2), rain = Sky.rain(level);
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
            GL20C.glUniformMatrix4fv(u(p, "u_Proj"), false, proj.get(m16));
            GL20C.glUniformMatrix3fv(u(p, "u_InvView"), false, invView.get(m9));
            GL20C.glUniform4f(u(p, "u_Sun"), fsx / flen, fsy / flen, 0f, fstrength * (1f - 0.85f * frain));
            GL20C.glUniform4f(u(p, "u_SunScreen"), fssx, fssy, 0f, ffront && fssx > -0.3f && fssx < 1.3f && fssy > -0.3f && fssy < 1.3f && fsun ? 1f : 0f);
            GL20C.glUniform3f(u(p, "u_Horizon"), (float) Math.min(1, skyc.x * 0.8 + 0.16 * fday), (float) Math.min(1, skyc.y * 0.8 + 0.18 * fday), (float) Math.min(1, skyc.z * 0.8 + 0.20 * fday));
            GL20C.glUniform3f(u(p, "u_Zenith"), (float) skyc.x, (float) skyc.y, (float) skyc.z);
            GL20C.glUniform4f(u(p, "u_Params"), fday, frain, funder ? 1f : 0f, PrismApi.clock().seconds());
            GL20C.glUniform1f(u(p, "u_Flash"), flashNow(level));
            GL20C.glUniform1f(u(p, "u_Storm"), Math.max(level.getThunderLevel(pt), PrismApi.storm().heavy(level)));
            int ncells = Sky.cells(level, pt); GL20C.glUniform4fv(u(p, "u_Cells"), Sky.CELLS); GL20C.glUniform1f(u(p, "u_CellsN"), ncells);
            GL20C.glUniform1f(u(p, "u_CloudFade"), 1f - Math.min(1f, PrismApi.storm().fog() * 1.15f));
            GL20C.glUniform4f(u(p, "u_On"), on(IlluminateConfig.GRADE), on(IlluminateConfig.GLOW), on(IlluminateConfig.SHAFTS), on(IlluminateConfig.HAZE));
            GL20C.glUniform2f(u(p, "u_On2"), on(IlluminateConfig.CLOUDS), on(IlluminateConfig.UNDERWATER_FOG));
            float[] wf = UnderwaterColour.update(pt);
            GL20C.glUniform3f(u(p, "u_WaterFog"), wf[0], wf[1], wf[2]);
            GL20C.glUniform1f(u(p, "u_Surf"), Foliage.surfaceAbove);
            GL20C.glUniform1f(u(p, "u_InCloud"), dev.phi.alterra.illuminate.clouds.SkyClouds.inside());
            var cpw = cam.getPosition(); GL20C.glUniform3f(u(p, "u_CamPos"), (float) (cpw.x % 4096.0), (float) cpw.y, (float) (cpw.z % 4096.0));
            // the clouds: where vanilla would put them
            float ch = level.effects().getCloudHeight();
            if (!Float.isNaN(ch)) ch = 250f;   // Alterra's land reaches far higher than vanilla's: the clouds start around y 250 and pile up to about 310
            boolean clouds = !Float.isNaN(ch) && mc.options.getCloudsType() != net.minecraft.client.CloudStatus.OFF;
            var cp = cam.getPosition();
            double ct = cloudClock(level, pt); long cgt = (long) Math.floor(ct); float cpt = (float) (ct - cgt);   // the clouds' own smooth clock (see cloudClock)
            double cxc = (cp.x + PrismApi.clouds().driftX(cgt, cpt)) / 24.0, czc = (cp.z + PrismApi.clouds().driftZ(cgt, cpt)) / 24.0 + 0.33;
                GL20C.glUniform1f(u(p, "u_CloudEvo"), PrismApi.clouds().evolve(cgt, cpt));
            GL20C.glUniform4f(u(p, "u_CloudInfo"), (float) cxc, (float) czc, (float) (ch + 0.33 - cp.y), clouds ? 1f : 0f);
            var cc = level.getCloudColor(pt); GL20C.glUniform3f(u(p, "u_CloudColor"), (float) cc.x, (float) cc.y, (float) cc.z);
            dev.phi.alterra.illuminate.maps.CloudGrid.update(cxc * 24.0, czc * 24.0, PrismApi.clouds().evolve(cgt, cpt));   // (the same cloud-space position and evolution the shader uses)
            dev.phi.alterra.illuminate.maps.CloudGrid.bind(); GL20C.glUniform1i(u(p, "u_CloudGrid"), dev.phi.alterra.illuminate.maps.CloudGrid.UNIT);
            GL20C.glUniform1f(u(p, "u_CloudStyle"), 1f);
            GL20C.glUniform1i(u(p, "u_Clouds"), 2);
            int cloudTex = mc.getTextureManager().getTexture(net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/environment/clouds.png")).getId();
            GlUpload.bind(GL13C.GL_TEXTURE2, cloudTex);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR); GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL11C.GL_REPEAT); GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL11C.GL_REPEAT);
            };
            // the height map (leaves too) for shadows and shafts, and how much sun gets through the clouds here
            var camp = cam.getPosition();
            HeightMap.update(level, camp.x, camp.z);
            final boolean shadowOn = IlluminateConfig.get(IlluminateConfig.SHADOWS) && strength > 0.02f
                    && dev.phi.alterra.illuminate.shadow.ShadowMap.render(level, camp.x, camp.y, camp.z, sx / len, sy / len, 0f);
            float sunVis = 1f;
            if (sun && sy / len > 0.05f) {
                double tUp = (250.0 + 20.0 - camp.y) / (sy / len);
                float cov = PrismApi.clouds().cover(level, camp.x + sx / len * tUp, camp.z, pt);
                sunVis = 1f - Math.min(1f, cov * 1.15f);
            }
            final float fSunVis = sunVis;
            float quality = (float) Math.round(IlluminateConfig.get(IlluminateConfig.QUALITY));
            java.util.function.IntConsumer extra = p -> {
                GL20C.glUniform1i(u(p, "u_Height"), 4);
                GL20C.glUniform1i(u(p, "u_ShadowMap"), dev.phi.alterra.illuminate.shadow.ShadowMap.UNIT);
                GL20C.glUniformMatrix4fv(u(p, "u_ShadowVP"), false, dev.phi.alterra.illuminate.shadow.ShadowMap.matrix());
                GL20C.glUniform3f(u(p, "u_ShadowCam"), (float) (camp.x - dev.phi.alterra.illuminate.shadow.ShadowMap.originX), (float) (camp.y - dev.phi.alterra.illuminate.shadow.ShadowMap.originY), (float) (camp.z - dev.phi.alterra.illuminate.shadow.ShadowMap.originZ));
                GL20C.glUniform1f(u(p, "u_ShadowOn"), shadowOn ? (Boolean.getBoolean("alterra.shadowDebug") ? 2f : 1f) : 0f);
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
            GlUpload.bind(GL13C.GL_TEXTURE4, HeightMap.texture());
            if (shadowOn) GlUpload.bind(GL13C.GL_TEXTURE0 + dev.phi.alterra.illuminate.shadow.ShadowMap.UNIT, dev.phi.alterra.illuminate.shadow.ShadowMap.texture());
            GlStateManager._activeTexture(GL13C.GL_TEXTURE0);
            // everything else (the clouds are geometry now: clouds/SkyClouds)
            rt.bindWrite(true);
            GlStateManager._glUseProgram(program); setAll.accept(program); extra.accept(program);
            GlUpload.bind(GL13C.GL_TEXTURE1, depth);
            GlUpload.bind(GL13C.GL_TEXTURE0, color);
            GL30C.glBindVertexArray(vao);
            GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);
            GL30C.glBindVertexArray(0);
            GlUpload.bind(GL13C.GL_TEXTURE4, 0);
            if (shadowOn) GlUpload.bind(GL13C.GL_TEXTURE0 + dev.phi.alterra.illuminate.shadow.ShadowMap.UNIT, 0);
            GlUpload.bind(GL13C.GL_TEXTURE3, 0);
            GlUpload.bind(GL13C.GL_TEXTURE2, 0); GlStateManager._activeTexture(GL13C.GL_TEXTURE0);
            com.mojang.blaze3d.vertex.BufferUploader.invalidate();
            GlStateManager._glUseProgram(0);
            GlStateManager._bindTexture(0);
            RenderSystem.depthMask(true); RenderSystem.enableDepthTest();
        } catch (Throwable ex) { failed = true; LOG.warn("Alterra Weather: the scene look is off, it could not be set up", ex); }
    }
}
