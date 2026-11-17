package dev.phi.alterra.illuminate.look;

import dev.phi.prismlib.api.WaterEvents;
import dev.phi.prismlib.api.PrismApi;
import dev.phi.alterra.illuminate.Illuminate;
import dev.phi.alterra.illuminate.IlluminateConfig;
import dev.phi.alterra.illuminate.maps.FlowMap;
import dev.phi.alterra.illuminate.maps.GlUpload;
import dev.phi.alterra.illuminate.maps.RainMap;
import dev.phi.alterra.illuminate.maps.SceneCopy;
import dev.phi.alterra.illuminate.maps.SeaState;
import dev.phi.alterra.illuminate.maps.ShoreMap;
import dev.phi.alterra.illuminate.maps.SnowMap;


import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL20C;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Leaves and plants that sway in the wind, made in the vertex shader (so it costs the processor nothing). Sodium's block shader and vanilla's leaf shader
 * are patched when the game starts: the original files are read from Sodium and from Minecraft, a little code is added, and the result is written to
 * a built-in resource pack that is placed on top. Nothing of theirs is shipped. The sway moves with the wind (Alterra Weather's wind), and the pattern
 * repeats every 16 blocks, so it is the same in every chunk and nothing tears at the edges.
 */
@EventBusSubscriber(modid = Illuminate.ID, value = Dist.CLIENT)
public final class Foliage {
    private Foliage() {}
    private static final Logger LOG = LogUtils.getLogger();

    private static final String SWELL = """
            // Alterra Weather: swells on the water. Spatial frequencies are whole multiples of 2*pi/16 so the pattern repeats at the 1024 wrap of the camera position.
            float alterraSwell(vec2 p, vec4 wave) {
                float t = wave.x, k = 0.39269908;   // 2*pi/16
                vec2 d = length(wave.yz) > 0.001 ? normalize(wave.yz) : vec2(1.0, 0.0);
                float a = sin(dot(p, d * 1.0 * k) * 1.0 + t * 1.1);
                float b = sin(dot(p, vec2(-d.y, d.x) * 2.0 * k) + t * 1.7 + 1.0);
                float c = sin(dot(p, vec2(3.0 * k, 2.0 * k)) - t * 2.3);
                return a * 0.5 + b * 0.3 + c * 0.2;
            }
            """;

    /** Added to the shader: the uniforms, and the function that works out how far a vertex moves. */
    private static final String FUNCTION = """
            // Alterra Weather: swaying foliage
            vec2 alterraSway(vec3 local, vec4 wave, float amount) {
                float t = wave.x;
                float ph = 6.2831853 * (local.x * (2.0 / 16.0) + local.z * (3.0 / 16.0) + local.y * (1.0 / 16.0));
                float g = sin(t * 1.6 + ph) * 0.6 + sin(t * 2.9 + ph * 2.0 + 1.3) * 0.4;
                float sp = length(wave.yz);
                vec2 dir = sp > 0.001 ? wave.yz / sp : vec2(1.0, 0.0);
                float lean = 0.3 + 0.7 * min(sp, 1.0);
                return (dir * (lean * (0.55 + 0.45 * g)) + vec2(-dir.y, dir.x) * 0.25 * g) * amount * (0.5 + 0.5 * min(sp, 1.5));
            }
            """;

    // ---------------------------------------------------------------- the pack with the patched shaders
    /** Called from FoliagePackEvent (a mod-bus event). */
    static void packs(AddPackFindersEvent e) {
        if (e.getPackType() != PackType.CLIENT_RESOURCES || !IlluminateConfig.get(IlluminateConfig.SWAY)) return;
        try {
            Path dir = FMLPaths.GAMEDIR.get().resolve("alterra_weather/foliage_pack");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":34,\"description\":\"Alterra Weather: swaying foliage\"}}");
            boolean any = false;
            if (ModList.get().getModFileById("sodium") == null) any |= patchVanilla(dir);   // with Sodium vanilla's chunk shaders are not used (and Sodium warns about packs that replace them)
            if (!any) return;
            e.addRepositorySource(consumer -> {
                var info = new PackLocationInfo("alterra_weather_foliage", Component.literal("Alterra Weather: swaying foliage"), PackSource.BUILT_IN, Optional.empty());
                Pack pack = Pack.readMetaAndCreate(info, new PathPackResources.PathResourcesSupplier(dir), PackType.CLIENT_RESOURCES, new PackSelectionConfig(true, Pack.Position.TOP, false));
                if (pack != null) consumer.accept(pack);
            });
        } catch (Throwable t) { LOG.warn("Alterra Weather: swaying foliage is off, the shaders could not be prepared", t); }
    }

    private static String sodiumPatched;
    private static boolean sodiumFailed;
    /**
     * Sodium reads its shader sources from the class path, so a resource pack cannot replace them. Instead a mixin asks here first: for the block vertex
     * shader the answer is Sodium's own text with the sway code added (nothing of Sodium's is copied into this mod); for everything else, null.
     */
    public static String patchedSodium(net.minecraft.resources.ResourceLocation name) {
        if (sodiumFailed || !name.getNamespace().equals("sodium") || !(IlluminateConfig.get(IlluminateConfig.SWAY) || IlluminateConfig.get(IlluminateConfig.FOAM) || IlluminateConfig.get(IlluminateConfig.WATER_LOOK) || IlluminateConfig.get(IlluminateConfig.SMOOTH_SNOW))) return null;
        if (name.getPath().equals("blocks/block_layer_opaque.fsh"))   // the fragment shader reads what the vertex shader writes, so it is only patched when that one is
            return patchedSodium(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("sodium", "blocks/block_layer_opaque.vsh")) == null ? null : patchedFragment();
        if (!name.getPath().equals("blocks/block_layer_opaque.vsh")) return null;
        if (sodiumPatched != null) return sodiumPatched;
        try {
            String text;
            try (InputStream in = Class.forName("net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader").getClassLoader().getResourceAsStream("assets/sodium/shaders/blocks/block_layer_opaque.vsh")) {
                if (in == null) throw new IllegalStateException("shader not found");
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            String anchor = "vec3 position = _vert_position + translation;";
            if (!text.contains(anchor) || !text.contains("uniform int u_FogShape;")) throw new IllegalStateException("the shader looks different than expected (Sodium updated?)");
            sodiumPatched = text.replace("uniform int u_FogShape;", "uniform int u_FogShape;\nout vec4 v_AlterraView;       // xyz: the vertex relative to the camera, w: 1 on water, 2 on ice, that get the reflection look\nout vec2 v_AlterraWaveP;      // where on the repeating ripple pattern\nout vec3 v_AlterraPos;        // the vertex relative to the camera\nout float v_AlterraSkyL;      // sky light at the vertex (0 to 1): how much it is out in the open\nout float v_AlterraLeaf;      // 1 on leaves\nout float v_AlterraSnow;     // 1 on snow\nout float v_AlterraTop;       // 1 on the top of water (its corners are at a partial height), less on a side face (its bottom is at a whole height)\nout vec3 v_AlterraSwirl;     // the slope a whirlpool gives the water here (into the core, and round it)\nuniform vec4 u_AlterraVortex[4];   // whirlpools near the camera: x, z relative to it, radius, strength with the sign of the spin\nuniform vec4 u_AlterraVortexN;   // how many\nuniform sampler2D u_AlterraWhirl;   // the height of a whirlpool's grid water (blocks, relative to the rest surface)\nuniform vec4 u_AlterraWhirlInfo;   // camera x, z relative to the grid corner, 1 over its width, its size in cells (0: none)\nout float v_AlterraSea;       // how open the sea is here (0 sheltered, 1 open)\nuniform sampler2D u_AlterraSeaMap;\nuniform sampler2D u_AlterraFlow;   // how far each water column's surface still is from its new height (easing after a level change)\nuniform vec4 u_AlterraFlowInfo;   // camera x, z relative to that map, 1 over its size, 1 when there is anything to ease\nuniform sampler2D u_AlterraSnowMap;   // the top of the snow in each column around the camera (eighths of a block, plus 512)\nuniform vec4 u_AlterraSnowInfo;   // camera x, z relative to that map, 1 over its size, 1 when there is snow to smooth\nuniform vec4 u_AlterraSeaInfo; // camera x, z relative to the sea map, 1 over its size (0: no map), how much the weather lifts the sea\nuniform vec4 u_AlterraShips[16]; // ships and physics objects near the camera: (centre x, z, half width, half depth) and (min y, max y), relative to it\nuniform vec4 u_AlterraShipsN;    // how many\nuniform vec4 u_AlterraSky;    // sun or moon direction, glint strength (w 0: the look is off)\nout vec3 v_AlterraShore;      // position on the shore map, 1 on water\nuniform vec4 u_AlterraShore;  // camera x, z relative to the shore map, its size, on or off\nuniform vec4 u_AlterraWave;   // time, wind x, wind z, on or off\nuniform vec4 u_AlterraWave2;  // how much for leaves, for plants\nuniform vec4 u_AlterraCam;    // where the camera is (x, z wrapped at 1024), y, swell height\nuniform sampler2D u_AlterraMask; // which textures sway: red all over (leaves), green at the top only (plants)")
                    .replace("void main() {", FUNCTION + SWELL + "\nvoid main() {")
                    .replace(anchor, anchor + """

                                // what sways is decided by the picture on the face: leaf pictures sway all over, plant pictures at their top edge only (grass blocks, glass, doors do not)
                                v_AlterraShore = vec3(0.0); v_AlterraView = vec4(0.0); v_AlterraWaveP = vec2(0.0); v_AlterraPos = vec3(0.0); v_AlterraSea = 0.0; v_AlterraSkyL = 0.0; v_AlterraLeaf = 0.0; v_AlterraTop = 0.0; v_AlterraSnow = 0.0; v_AlterraSwirl = vec3(0.0);
                                {
                                    vec4 _m4 = textureLod(u_AlterraMask, _vert_tex_diffuse_coord - _vert_tex_diffuse_coord_bias * 0.0002, 0.0); vec3 _m = _m4.rgb;
                                    v_AlterraTop = step(0.02, fract(position.y + u_AlterraCam.y + 0.002));   // before anything moves it: a water top is at a partial height, the bottom edge of a side at a whole one
                                    float _amount = _m.r * u_AlterraWave2.x * 0.05;
                                    if (_vert_tex_diffuse_coord_bias.y < 0.0) _amount += _m.g * u_AlterraWave2.y * 0.14;
                                    if (_amount > 0.0) position.xz += alterraSway(_vert_position, u_AlterraWave, _amount);
                                    if (_m.b > 0.5 && u_AlterraSky.w > 0.0) { v_AlterraView = vec4(position, 1.0); v_AlterraWaveP = position.xz + u_AlterraCam.xz; }
                                    if (_m.b > 0.5 && u_AlterraFlowInfo.w > 1.5 && max(abs(position.x), abs(position.z)) < 61.0) v_AlterraView.w = 9.0;   // our own water surface draws here: the game\'s water is not drawn at all
                                    if (_m4.a > 0.5 && u_AlterraSky.w > 0.0) { v_AlterraView = vec4(position, 2.0); v_AlterraWaveP = position.xz + u_AlterraCam.xz; }
                                    if (_m4.a > 0.15 && _m4.a < 0.5 && u_AlterraSky.w > 0.0) { v_AlterraView = vec4(position, 3.0); v_AlterraWaveP = position.xz + u_AlterraCam.xz; }
                                    if (_m.b > 0.5 && u_AlterraShore.w > 0.0) v_AlterraShore = vec3(position.xz + u_AlterraShore.xy, 1.0);
                                    vec2 _sm = (_m.b > 0.5 && u_AlterraSeaInfo.z > 0.0) ? textureLod(u_AlterraSeaMap, (position.xz + u_AlterraSeaInfo.xy) * u_AlterraSeaInfo.z, 0.0).rg : vec2(0.0, 128.0 / 255.0);
                                    float _ss = _sm.r, _sg = (_sm.g * 255.0 - 128.0) / 100.0;   // how open the sea is; the storm surge from the server's water grid (blocks)
                                    v_AlterraSea = _ss;
                                    if (_m.b > 0.5 && u_AlterraCam.w > 0.0) {
                                        float _o = alterraSwell(position.xz + u_AlterraCam.xz, u_AlterraWave) * u_AlterraCam.w * (1.0 + 5.0 * _ss * (0.4 + u_AlterraSeaInfo.w)) + _sg;   // far out at sea the swell is much higher; the surge lifts it all
                                        float _sh = 0.0;
                                        for (int _i = 0; _i < 8; _i++) {   // near a ship the water stays at or a little below its normal level, so it never climbs into a hull that floats just above it
                                            if (_i >= int(u_AlterraShipsN.x)) break;
                                            vec4 _a = u_AlterraShips[_i * 2], _b = u_AlterraShips[_i * 2 + 1];
                                            if (position.y > _b.x - 1.5 && position.y < _b.y + 0.7) _sh = max(_sh, 1.0 - smoothstep(0.0, 2.5, length(max(abs(position.xz - _a.xy) - _a.zw, 0.0))));
                                        }
                                        position.y += mix(_o, min(_o, 0.0) - 0.08, _sh);
                                    }
                                    if (_m.b > 0.5 && u_AlterraFlowInfo.w > 0.5 && fract(position.y + u_AlterraCam.y) > 0.02)   // the top of the water eases to a new level instead of jumping an eighth of a block
                                        position.y += textureLod(u_AlterraFlow, (position.xz + u_AlterraFlowInfo.xy) * u_AlterraFlowInfo.z, 0.0).r;
                                    v_AlterraSnow = (_m4.a > 0.04 && _m4.a < 0.14) ? 1.0 : 0.0;
                                    if (_m4.a > 0.04 && _m4.a < 0.14 && u_AlterraSnowInfo.w > 0.5) {   // snow: the top corner of a layer follows the average of the four columns around it, so drifts and slopes are smooth (a full block's corners are whole heights and stay)
                                        float _wy = position.y + u_AlterraCam.y, _sb = floor(_wy + 0.001);
                                        if (_wy - _sb > 0.01) {
                                            ivec2 _cc = ivec2(floor(position.xz + u_AlterraSnowInfo.xy + 0.5)); float _ss2 = 0.0, _sn = 0.0;
                                            for (int _dx = -1; _dx <= 0; _dx++) for (int _dz = -1; _dz <= 0; _dz++) {
                                                ivec2 _sq = _cc + ivec2(_dx, _dz);
                                                if (_sq.x < 0 || _sq.y < 0 || _sq.x >= 128 || _sq.y >= 128) continue;
                                                float _sv = texelFetch(u_AlterraSnowMap, _sq, 0).r * 65535.0;
                                                if (_sv < 1.0) continue;
                                                _ss2 += (_sv - 512.0) / 8.0; _sn += 1.0;
                                            }
                                            if (_sn > 0.5) position.y += clamp(_ss2 / _sn, _sb + 0.02, _sb + 1.0) - _wy;
                                        }
                                    }
                                    if (_m.b > 0.5 && u_AlterraVortexN.x > 0.5) {   // whirlpools: the surface dips into the core, and the ripples turn round it
                                        for (int _vi = 0; _vi < 4; _vi++) {
                                            if (_vi >= int(u_AlterraVortexN.x)) break;
                                            vec4 _vt = u_AlterraVortex[_vi]; vec2 _vd = position.xz - _vt.xy; float _vr = max(length(_vd), 0.001);
                                            float _vf = 1.0 - smoothstep(0.0, _vt.z * 1.25, _vr), _sp = sign(_vt.w);
                                            position.y -= 0.3 * abs(_vt.w) * _vf * _vf;   // a little smoothing: the funnel itself is real water, carved by the server
                                            vec2 _vu = _vd / _vr;
                                            v_AlterraSwirl.xy += (-_vu * 0.9 + vec2(-_vu.y, _vu.x) * _sp * 0.7) * _vf * abs(_vt.w);
                                            float _va = _vf * 2.4 * _sp; vec2 _vc = _vt.xy + u_AlterraCam.xz, _vp = v_AlterraWaveP - _vc;
                                            v_AlterraWaveP = _vc + vec2(cos(_va) * _vp.x - sin(_va) * _vp.y, sin(_va) * _vp.x + cos(_va) * _vp.y);
                                        }
                                    }
                                    if (_m.b > 0.5 && v_AlterraTop > 0.5 && u_AlterraWhirlInfo.w > 0.5) {   // a whirlpool: the water's own surface is lifted and lowered by the grid water
                                        vec2 _wg = (position.xz + u_AlterraWhirlInfo.xy) * u_AlterraWhirlInfo.z;
                                        if (_wg.x > 0.0 && _wg.y > 0.0 && _wg.x < 1.0 && _wg.y < 1.0) position.y += textureLod(u_AlterraWhirl, _wg, 0.0).r;
                                    }
                                    v_AlterraPos = position; v_AlterraSkyL = _vert_tex_light_coord.y; v_AlterraLeaf = _m.r;
                                }
                            """);
            LOG.info("Alterra Weather: Sodium's block shader patched for swaying foliage");
            return sodiumPatched;
        } catch (Throwable t) { sodiumFailed = true; LOG.warn("Alterra Weather: no sway with Sodium: {}", t.toString()); return null; }
    }

    /** The slope of the ripple surface (the gradient of a few sines, the first three the same as the swell the water geometry makes, plus two fine ones), all whole multiples of 2*pi/16 so it repeats where the camera position wraps. */
    private static final String WATER_LOOK_SLOPE = """
            vec2 alterraSlope(vec2 p, vec4 sea, vec4 w2, float open) {
                // seven waves, each a whole multiple of 2*pi/1024 (so the pattern only repeats where the camera position wraps, 1024 blocks), in unrelated directions and sizes (4 to 23 blocks),
                // travelling at the speed deep water waves of that size would, and calmer patches about a hundred blocks across. The weather only lifts it a little: wind, rain and storms make the waves a bit higher and faster.
                float k0 = 0.0061359232, lift = clamp(sea.w + 0.25 * min(length(sea.yz), 1.5), 0.0, 1.0);
                // far from land (open) the waves are larger and longer; when the weather has just changed (w2.w, which dies away within a few seconds) the sea runs high and fast for a moment
                float amp = (1.0 + 0.30 * lift) * (1.0 + 1.1 * open * (0.5 + lift)) * (1.0 + 0.6 * w2.w);
                float t = sea.x;   // already integrated on the CPU with the weather's speed in it, so a change of speed never makes the waves jump
                // bend the wave fronts with a slow wide warp (so they are not parallel rows everywhere) and let each long wave swell and fade from place to place
                p += vec2(5.0 * sin(dot(p, vec2(7.0, 3.0)) * k0 + 1.0 + t * 0.02) + 3.0 * sin(dot(p, vec2(-11.0, 9.0)) * k0 + 2.0 - t * 0.03),
                          5.0 * sin(dot(p, vec2(-5.0, 8.0)) * k0 + 0.5 - t * 0.02) + 3.0 * sin(dot(p, vec2(13.0, 6.0)) * k0 + 3.0 + t * 0.03));
                float m1 = 0.55 + 0.45 * sin(dot(p, vec2(5.0, 11.0)) * k0 + t * 0.04), m2 = 0.55 + 0.45 * sin(dot(p, vec2(-9.0, 4.0)) * k0 + 2.0 - t * 0.035), m3 = 0.55 + 0.45 * sin(dot(p, vec2(8.0, -7.0)) * k0 + 4.0 + t * 0.03);
                // the wind decides which waves rule: those that run with it are strong, those across or against it are weak, so the sea runs the way the wind blows and changes as the wind turns (calm: all equal)
                vec2 wv = sea.yz; float wl = length(wv), wlock = smoothstep(0.03, 0.15, wl); vec2 wd = wv / max(wl, 0.03);
                #define ALIGN(k) mix(1.0, 0.18 + 1.65 * pow(max(dot(normalize(k), wd), 0.0), 2.0), wlock)
                vec2 g = vec2(0.0);
                g += 0.50 * ALIGN(vec2( 41.0,  17.0)) * m1 * cos(dot(p, vec2( 41.0,  17.0)) * k0 + t * 0.569)       * vec2( 41.0,  17.0);
                g += 0.35 * ALIGN(vec2(-29.0,  58.0)) * m2 * cos(dot(p, vec2(-29.0,  58.0)) * k0 + t * 0.692 + 1.7) * vec2(-29.0,  58.0);
                g += 0.28 * ALIGN(vec2( 67.0, -33.0)) * m3 * cos(dot(p, vec2( 67.0, -33.0)) * k0 + t * 0.743 + 0.6) * vec2( 67.0, -33.0);
                g *= 1.0 + 0.9 * open;   // the long waves (the first three) grow most in the open sea
                g += (1.0 - 0.35 * open) * (0.18 * ALIGN(vec2(-91.0, -47.0)) * cos(dot(p, vec2(-91.0, -47.0)) * k0 + t * 0.867 + 2.9) * vec2(-91.0, -47.0)
                    + 0.12 * ALIGN(vec2(131.0,  61.0)) * cos(dot(p, vec2(131.0,  61.0)) * k0 + t * 1.030 + 4.1) * vec2(131.0,  61.0)
                    + 0.08 * ALIGN(vec2(-157.0, 119.0)) * cos(dot(p, vec2(-157.0, 119.0)) * k0 + t * 1.204 + 5.3) * vec2(-157.0, 119.0)
                    + 0.05 * ALIGN(vec2(223.0, -97.0)) * cos(dot(p, vec2(223.0, -97.0)) * k0 + t * 1.338 + 3.3) * vec2(223.0, -97.0));
                float calm = 0.35 + 0.65 * smoothstep(-0.3, 0.7, sin(dot(p, vec2(9.0, 4.0)) * k0 + t * 0.05) * sin(dot(p, vec2(-5.0, 11.0)) * k0 + 1.3 - t * 0.03));
                return g * k0 * calm * 0.8 * amp;
            }

            // Caustics on the bottom: a web of thin, curved, bright lines that join and part (the iterated sine web used for water caustics). On its own that web repeats in a visible grid, so two layers of different
            // size (16 and 32 blocks) are drawn through a slow wide warp that differs from place to place. Every period divides the 1024 the camera position wraps at, so nothing pops.
            float alterraCausticLayer(vec2 xz, float cell, float time) {
                const float TAU = 6.28318530718;
                float s = TAU / cell;
                vec2 p = xz * s - 250.0;
                vec2 q = mod(xz, cell) * s - 250.0;
                vec2 i = p;
                float c = 1.0, inten = 0.0075, t0 = time * 0.33 + 23.0;
                for (int n = 0; n < 4; n++) {
                    float t = t0 * (1.0 - (3.5 / float(n + 1)));
                    i = p + vec2(cos(t - i.x) + sin(t + i.y), sin(t - i.y) + cos(t + i.x));
                    c += 1.0 / length(vec2(q.x / (sin(i.x + t) / inten), q.y / (cos(i.y + t) / inten)));
                }
                c /= 4.0;
                return pow(abs(1.17 - pow(c, 1.4)), 6.0) * 0.8;   // soft lines on a darker ground
            }
            float alterraCaustic(vec2 xz, float time) {
                float k0 = 0.0061359232;
                xz += vec2(mod(time * 0.11, 1024.0), mod(time * 0.07, 1024.0));   // the whole pattern drifts, so it never replays
                vec2 w = vec2(3.2 * sin(dot(xz, vec2(13.0, 7.0)) * k0 + 1.0 + time * 0.05) + 2.2 * sin(dot(xz, vec2(-19.0, 23.0)) * k0 + 2.0 - time * 0.04) + 1.2 * sin(dot(xz, vec2(41.0, -29.0)) * k0 + time * 0.07),
                              3.2 * sin(dot(xz, vec2(-11.0, 17.0)) * k0 + 0.4 - time * 0.05) + 2.2 * sin(dot(xz, vec2(23.0, 19.0)) * k0 + 3.0 + time * 0.04) + 1.2 * sin(dot(xz, vec2(-37.0, -43.0)) * k0 + 1.5 + time * 0.06));
                float a = alterraCausticLayer(xz + w, 16.0, time);
                float b = alterraCausticLayer(xz + w.yx * 0.8 + vec2(37.0, 11.0), 32.0, time * 0.8 + 5.0);
                float swell = 0.65 + 0.35 * sin(dot(xz, vec2(0.031, 0.017)) + time * 0.11) * sin(dot(xz, vec2(-0.023, 0.037)) - time * 0.09 + 1.0);
                return clamp((0.75 * a + 0.6 * b) * swell * 1.6, 0.0, 2.0);
            }

            // Rain: each block gets a drop now and then (more often the harder it rains, at its own random moment and spot), and each drop sends out one ring that grows and fades.
            // Rings are local (hashed per block), not a repeating pattern, so they never form a grid, and they fade out with distance where they would only shimmer.
            float alterraHash(vec2 p) { uvec2 q = uvec2(ivec2(p)) * uvec2(1597334673u, 3812015801u); uint n = (q.x ^ q.y) * 1597334673u; return float(n) * (1.0 / 4294967295.0); }
            vec2 alterraRain(vec2 xz, float time, float rain) {
                vec2 g = vec2(0.0), cell = floor(xz);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    vec2 c = cell + vec2(float(dx), float(dz)), cw = mod(c, 1024.0);
                    float h = alterraHash(cw), h2 = alterraHash(cw + vec2(17.0, 91.0)), h3 = alterraHash(cw + vec2(53.0, 7.0));
                    float cyc = time / 1.4 + h * 8.0, age = fract(cyc);
                    float drop = step(1.0 - 0.85 * rain, alterraHash(cw + vec2(floor(cyc) * 3.0, 29.0)));   // does this cycle have a drop at all
                    vec2 d = xz - (c + vec2(0.15 + 0.7 * h2, 0.15 + 0.7 * h3));
                    float dist = length(d), r = age * 0.85, fade = (1.0 - age) * (1.0 - age);
                    float wave = sin((dist - r) * 38.0) * exp(-pow((dist - r) / 0.10, 2.0));
                    g += d / max(dist, 0.001) * wave * fade * drop;
                }
                return g * 0.75 * smoothstep(0.0, 0.2, rain);
            }

            // Wakes and splashes: each disturbance is a ring that grows, widens and fades (only the slope of the water, no white).
            vec2 alterraWakes(vec2 rel) {
                vec2 acc = vec2(0.0);
                int n = int(u_AlterraRipN.x);
                for (int i = 0; i < 48; i++) {
                    if (i >= n) break;
                    vec4 e = u_AlterraRip[i];
                    float hc = floor(e.w * 0.25); e.w -= 4.0 * hc; float born = smoothstep(0.0, 0.14, e.z);   // a ring grows in instead of popping up   // above 4: the heading of something that is moving (a wake), as a code
                    vec2 d = rel - e.xy; float dist = length(d);
                    // one ring per disturbance, as wide and as fast as the thing that made it is big (strength above 1 means a bigger object), a single crest with a trough behind it, like a real ripple
                    float sz = 0.7 + 0.5 * clamp(e.w, 0.0, 3.0), amp = min(e.w, 1.0);
                    float r = e.z * 1.8 * sz + 0.25 * sz, w = (0.18 + e.z * 0.25) * sz, fade = exp(-e.z * 0.9 / sz) * amp * born;
                    float x = (dist - r) / w, aw = 1.0;
                    if (hc > 0.5) {   // a wake: the ring is only strong where its envelope with the others is (the two arms about 19 degrees off the track behind it) and, weaker, in the churned trail straight behind
                        float ang = (hc - 1.0) / 256.0 * 6.2831853 - 3.14159265;
                        float ph = acos(clamp(dot(d / max(dist, 0.001), vec2(cos(ang), sin(ang))), -1.0, 1.0));
                        float _wa = 1.0 / (1.0 + 0.18 * r);   // the arc narrows as it spreads, so the arms end in tapering lines, not round caps
                        aw = (1.4 * exp(-pow((ph - 1.91) / (0.40 * _wa), 2.0)) + 0.3 * exp(-pow((ph - 3.14159) / (0.35 * _wa), 2.0))) * (1.0 - smoothstep(0.4, 2.2, e.z));
                    }
                    acc += d / max(dist, 0.001) * (-1.6 * x * exp(-x * x)) * fade * aw;
                }
                return acc;
            }

            // Foam: patches that thin out into blocky flecks and fade, part of the surface itself
            float alterraFoam(vec2 rel, vec2 wp) {
                float f = 0.0; int n = int(u_AlterraFoamN.x);
                for (int i = 0; i < 40; i++) {
                    if (i >= n) break;
                    vec4 e = u_AlterraFoam[i];
                    float fade = smoothstep(0.0, 0.5, e.z) * (1.0 - smoothstep(1.6, 4.4, e.z));   // grows in, holds, then thins out for good
                    float R = e.w * (0.55 + 0.45 * fade), dist = length(rel - e.xy);
                    if (dist > R || fade <= 0.0) continue;
                    // whole pixels of foam (a quarter block each) with their own ragged edge; each one fades through a few steps as the patch thins, none pops
                    vec2 cell = floor(wp * 4.0) + float(i) * 13.0;
                    float edge = 0.5 + 0.5 * alterraHash(cell), pick = alterraHash(cell + vec2(5.0, 5.0));
                    float lvl = floor(clamp((fade - 0.7 * pick) / 0.3, 0.0, 1.0) * 4.0 + 0.5) / 4.0;
                    f = max(f, step(dist, R * edge) * lvl * step(0.45, pick));
                }
                return min(f, 1.0);
            }

            // the view depth of a value from the depth buffer
            float alterraW(float d) { return u_ProjectionMatrix[3][2] / ((d * 2.0 - 1.0) + u_ProjectionMatrix[2][2]); }

            // How upright the surface behind the water is at a screen position (0 for a wall, 1 for flat ground), from the depth around it: foam is only drawn where the water meets walls (a block, a pillar, a ship), not along beaches.
            float alterraUp(vec2 uv) {
                vec2 t = 1.0 / u_AlterraScreen.xy, k = vec2(1.0 / u_ProjectionMatrix[0][0], 1.0 / u_ProjectionMatrix[1][1]);
                float w0 = alterraW(texture(u_AlterraDepth, uv).r), w1 = alterraW(texture(u_AlterraDepth, uv + vec2(t.x, 0.0)).r), w2 = alterraW(texture(u_AlterraDepth, uv + vec2(0.0, t.y)).r);
                vec3 p0 = vec3((uv * 2.0 - 1.0) * k * w0, -w0), p1 = vec3(((uv + vec2(t.x, 0.0)) * 2.0 - 1.0) * k * w1, -w1), p2 = vec3(((uv + vec2(0.0, t.y)) * 2.0 - 1.0) * k * w2, -w2);
                vec3 n = normalize(cross(p1 - p0, p2 - p0));
                return abs(normalize(transpose(mat3(u_ModelViewMatrix)) * n).y);   // the normal turned back into world directions
            }

            // A reflection: march a ray (camera-relative world space) through the copy of the screen; rgb is what it hits, a is how sure (0 on a miss, fading out at the screen's edge).
            vec4 alterraTraceM(mat4 vp, vec3 shift, vec3 p0, vec3 r, int steps, float grow) {   // fewer, longer steps where a reflection is faint or far, so it stays cheap on integrated graphics
                float t = 0.3 + 0.25 * fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715)))), stepLen = 0.25, tPrev = 0.0;   // each pixel starts a little differently: whatever banding is left turns into fine noise
                for (int i = 0; i < steps; i++) {
                    vec3 p = p0 + r * t;
                    vec4 c = vp * vec4(p + shift, 1.0);
                    if (c.w <= 0.05) break;
                    vec2 uv = c.xy / c.w * 0.5 + 0.5;
                    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) break;
                    float diff = c.w - alterraW(texture(u_AlterraDepth, uv).r);   // positive: the ray has gone behind what is drawn there
                    if (diff > 0.0) {
                        if (diff > 0.35 + t * 0.045 + stepLen) break;   // the ray must really touch what it hits: no hit rather than a ghost copy of something behind   // passed behind something thick: no hit rather than a wrong one (a smeared hit shows as repeated copies)
                        float lo = tPrev, hi = t;
                        for (int k = 0; k < 4; k++) {   // narrow down where it crossed, so the hit does not land in steps
                            float mid = 0.5 * (lo + hi);
                            vec4 cm = vp * vec4(p0 + r * mid + shift, 1.0);
                            vec2 um = cm.xy / cm.w * 0.5 + 0.5;
                            if (cm.w - alterraW(texture(u_AlterraDepth, um).r) > 0.0) hi = mid; else lo = mid;
                        }
                        vec4 ch = vp * vec4(p0 + r * hi + shift, 1.0);
                        vec2 uh = clamp(ch.xy / ch.w * 0.5 + 0.5, 0.0, 1.0);
                        if (abs(ch.w - alterraW(texture(u_AlterraDepth, uh).r)) > 0.15 + hi * 0.012 + stepLen * 0.3) break;   // after narrowing down, the ray must really touch the surface there: crossing an edge onto something far behind made ghost copies
                        float edge = smoothstep(0.0, 0.12, min(min(uh.x, 1.0 - uh.x), min(uh.y, 1.0 - uh.y)));
                        return vec4(texture(u_AlterraScene, uh).rgb, edge);
                    }
                    tPrev = t; t += stepLen; stepLen *= grow;
                }
                return vec4(0.0);
            }
            vec4 alterraTrace(vec3 p0, vec3 r, int steps, float grow) { return alterraTraceM(u_ProjectionMatrix * u_ModelViewMatrix, vec3(0.0), p0, r, steps, grow); }
            """;
    private static String sodiumFragment;
    /** The block fragment shader with the shore foam in it: where the water's cell is near land (the shore map), its colour is pulled towards white in square steps. */
    private static String patchedFragment() {
        if (sodiumFragment != null) return sodiumFragment;
        try {
            String text;
            try (InputStream in = Class.forName("net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader").getClassLoader().getResourceAsStream("assets/sodium/shaders/blocks/block_layer_opaque.fsh")) {
                if (in == null) throw new IllegalStateException("shader not found");
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            String a1 = "flat in uint v_Material;", a2 = "color *= v_Color; // Apply per-vertex color modulator";
            if (!text.contains(a1) || !text.contains(a2)) throw new IllegalStateException("the fragment shader looks different than expected (Sodium updated?)");
            sodiumFragment = text.replace(a1, a1 + "\nin vec3 v_AlterraShore;   // xz: the block position on the shore map, z: 1 on water\nuniform sampler2D u_AlterraShoreMap;\nin vec4 v_AlterraView;\nin vec2 v_AlterraWaveP;\nuniform vec4 u_AlterraSky;      // sun or moon direction, glint strength\nuniform vec4 u_AlterraZenith;   // sky colour overhead, rain\nuniform vec4 u_AlterraHorizon;  // sky colour at the horizon\nuniform vec4 u_AlterraSea;      // time, wind x, wind z, storm (0 to 1)\nuniform vec4 u_AlterraSea2;     // rain, thunder, heavy storm, surge\nuniform vec4 u_AlterraHulls[16]; // ship hulls as oriented boxes (centre; axis x + half x; axis y + half y; axis z + half z), relative to the camera\nuniform vec4 u_AlterraHullsN;    // how many\nuniform vec4 u_AlterraFoam[40];\nuniform vec4 u_AlterraFoamN;\nuniform vec4 u_AlterraRip[48];  // rings: x, z relative to the camera, age in seconds, strength\nuniform vec4 u_AlterraRipN;     // how many\nuniform sampler2D u_AlterraScene;  // colour of everything drawn before the water\nuniform sampler2D u_AlterraDepth;  // and its depth\nuniform vec4 u_AlterraScreen;      // its size, and whether it is there\nuniform mat4 u_ProjectionMatrix;\nuniform mat4 u_ModelViewMatrix;\nuniform mat4 u_AlterraPrevVP;   // the view-projection matrix of the frame before (for metals, which look in the previous frame's copy)\nuniform vec4 u_AlterraPrev;     // camera now minus camera then, and 1 when there is a previous frame\nin vec3 v_AlterraPos;\nin float v_AlterraSkyL;\nin float v_AlterraLeaf;\nin float v_AlterraTop;\nin float v_AlterraSnow;\nuniform vec4 u_AlterraSnowInfo;\nuniform vec4 u_AlterraVortex[4];\nuniform vec4 u_AlterraVortexN;\nuniform sampler2D u_AlterraWaveGrid;\nuniform vec4 u_AlterraWaveGridInfo;\nuniform sampler2D u_AlterraWhirl;\nuniform vec4 u_AlterraWhirlInfo;\nin vec3 v_AlterraSwirl;\nuniform sampler2D u_AlterraMask;\nuniform vec4 u_AlterraWet;      // how wet things are (0 to 1, rises in rain and dries slowly), the rain\nuniform sampler2D u_AlterraRainMap;  // per block column: the height of the first thing that stops rain (leaves do not)\nuniform vec4 u_AlterraRainInfo;      // camera x, z relative to the map's corner, camera y, 1 when there is a map\nin float v_AlterraSea;\nuniform vec4 u_AlterraCam;     // camera position wrapped at 1024 (x, z)\nuniform vec4 u_AlterraUnder;   // x: 1 when the camera is under water, y: the water surface above it, relative to the camera\n")
                    .replace("void main() {", WATER_LOOK_SLOPE + "\nvoid main() {")
                    .replace(a2, a2 + """

                            float _alterraLeafPx = texture(u_AlterraMask, v_TexCoord).r;   // leaf or not, per pixel (the vertex value fades across a face where a corner samples outside the leaf texture: half the leaf got puddles)
                            float _snowPx = step(abs(texture(u_AlterraMask, v_TexCoord).a - 0.0902), 0.012);   // snow, decided per pixel from the mask (a vertex at the edge of a sprite blends with the sprite next to it in the atlas and flagged half a grass block as snow)
                            if (_snowPx > 0.5 && u_AlterraSnowInfo.w > 1.5 && abs(v_AlterraPos.x) < 48.0 && abs(v_AlterraPos.z) < 48.0) discard;   // near the camera the snow is drawn by its own surface
                            if (v_AlterraView.w > 8.5) discard;
                            if (_snowPx > 0.5 && v_AlterraView.w < 0.5 && u_AlterraUnder.x < 0.5) {   // snow's own look: matte, a fine grain, tiny glints, never glossy and never blue
                                vec2 _sp = v_AlterraPos.xz + u_AlterraCam.xz;
                                float _g1 = fract(sin(dot(floor(_sp * 16.0), vec2(12.9898, 78.233))) * 43758.5453), _g2 = fract(sin(dot(floor(_sp * 40.0), vec2(39.3468, 11.135))) * 24634.6345);
                                float _sl = dot(color.rgb, vec3(0.299, 0.587, 0.114));
                                color.rgb = mix(vec3(_sl), color.rgb, 0.45) * vec3(1.0, 0.995, 0.985);   // the texture's cold tint taken out: snow is white, a touch warm in the light
                                color.rgb *= 0.955 + 0.07 * _g1;   // a fine grain, a few pixels across
                                color.rgb *= mix(vec3(0.93, 0.96, 1.0), vec3(1.0), smoothstep(0.55, 0.9, _sl));   // only the shade is cool
                                color.rgb += vec3(1.0, 0.99, 0.96) * step(0.9975, _g2) * (0.25 + 0.75 * u_AlterraSky.w) * 0.55 * smoothstep(0.5, 0.9, _sl);   // single glints, mostly in the sun
                            }
                            // rain: what stands out in the open gets wet (darker, richer), and on flat ground puddles form in the low spots with raindrop rings and a real reflection of the sky and the world
                            if ((u_AlterraWet.x > 0.01 || u_AlterraRainInfo.w > 0.5) && v_AlterraView.w < 0.5 && color.a > mix(0.99, 0.1, step(0.5, _alterraLeafPx)) && u_AlterraUnder.x < 0.5 && u_AlterraHullsN.w > 0.5) {   // leaves far away are blended down to half see-through pixels: they still get wet
                                vec3 _fn = normalize(cross(dFdx(v_AlterraPos), dFdy(v_AlterraPos)));
                                if (dot(_fn, v_AlterraPos) > 0.0) _fn = -_fn;   // the side facing the camera
                                float _fy = fract(v_AlterraPos.y + u_AlterraRainInfo.z);
                                if (_fy < 0.004 || _fy > 0.996) _fn = vec3(0.0, v_AlterraPos.y < 0.0 ? 1.0 : -1.0, 0.0);   // a face at a whole block height is a top seen from above or an underside seen from below: sure, unlike the slope between pixels, which breaks along block edges (thin dry lines)
                                float _expo = smoothstep(0.82, 0.94, v_AlterraSkyL), _ret = 0.0, _wl = 0.0, _wallF = 0.0; bool _hasW = false;   // without a map: by the sky light
                                if (u_AlterraRainInfo.w > 0.5) {   // with it: is anything solid above the air just in front of this surface (the point is nudged out along the normal, so a trunk's side and a wall's outer face count as outdoors and a room's inner face does not)
                                    bool _isLeaf = _alterraLeafPx > 0.5;   // water runs through a canopy: a leaf is tested in its own column and is wet on every face; anything else by the air in front of the surface
                                    vec3 _q = v_AlterraPos + (_isLeaf ? -_fn : _fn) * 0.08;
                                    vec2 _rc = _q.xz + u_AlterraRainInfo.xy;
                                    if (_rc.x >= 0.0 && _rc.y >= 0.0 && _rc.x < 128.0 && _rc.y < 128.0) {
                                        vec4 _tx = texelFetch(u_AlterraRainMap, ivec2(floor(_rc)), 0);
                                        if (abs((v_AlterraPos.y + u_AlterraRainInfo.z) - (_tx.r * 512.0 - 64.0)) < 0.3) {   // on top of the column: how much rain water has gathered here (blended between columns, so puddles have soft shapes)
                                            vec2 _pw0 = v_AlterraPos.xz + u_AlterraCam.xz;
                                            vec2 _wuv = (_rc + 0.4 * vec2(sin(dot(_pw0, vec2(0.93, 0.61)) + 0.3), cos(dot(_pw0, vec2(0.71, -0.84)) + 1.1)) + 0.15 * vec2(sin(dot(_pw0, vec2(2.3, 1.7))), cos(dot(_pw0, vec2(-1.9, 2.5))))) / 128.0;   // the look-up is bent a little, so edges are not the block grid
                                            vec2 _cf = _wuv * 128.0 - 0.5; ivec2 _c0 = ivec2(floor(_cf)); vec2 _bf = fract(_cf); _bf = _bf * _bf * (3.0 - 2.0 * _bf);
                                            vec2 _acc = vec2(0.0); float _wsum = 0.0, _hs = v_AlterraPos.y + u_AlterraRainInfo.z;
                                            for (int _j = 0; _j < 2; _j++) for (int _i = 0; _i < 2; _i++) {   // smooth between the four columns round this point, but only those at the same surface height: next to a wall the taller column holds no water, and mixing it in made puddles fade out before they reached the wall
                                                vec4 _t4 = texelFetch(u_AlterraRainMap, clamp(_c0 + ivec2(_i, _j), ivec2(0), ivec2(127)), 0);
                                                float _same = 1.0 - step(0.3, abs(_hs - (_t4.r * 512.0 - 64.0)));
                                                float _wgt = mix(1.0 - _bf.x, _bf.x, float(_i)) * mix(1.0 - _bf.y, _bf.y, float(_j)) * _same;
                                                _acc += _t4.ba * _wgt; _wsum += _wgt;
                                            }
                                            vec2 _wv = _wsum > 0.001 ? _acc / _wsum : _tx.ba;
                                            float _rf = clamp(u_AlterraRainInfo.w - 1.0, 0.0, 1.0); _wl = mix(_wv.y, _wv.x, _rf) * 0.4;   // from what was shown to what is there now: puddles fill and dry smoothly between the map's updates
                                            _hasW = true;
                                            vec2 _fc = fract(_rc); ivec2 _ic = ivec2(floor(_rc)); float _h0 = _tx.r * 512.0 - 64.0;   // foam only where the puddle touches a block: a neighbouring column that stands up at least half a block above this one
                                            float _hE = texelFetch(u_AlterraRainMap, clamp(_ic + ivec2(1, 0), ivec2(0), ivec2(127)), 0).r * 512.0 - 64.0, _hW = texelFetch(u_AlterraRainMap, clamp(_ic + ivec2(-1, 0), ivec2(0), ivec2(127)), 0).r * 512.0 - 64.0;
                                            float _hS = texelFetch(u_AlterraRainMap, clamp(_ic + ivec2(0, 1), ivec2(0), ivec2(127)), 0).r * 512.0 - 64.0, _hN = texelFetch(u_AlterraRainMap, clamp(_ic + ivec2(0, -1), ivec2(0), ivec2(127)), 0).r * 512.0 - 64.0;
                                            _wallF = max(max(step(0.55, _hE - _h0) * smoothstep(0.45, 1.0, _fc.x), step(0.55, _hW - _h0) * smoothstep(0.45, 1.0, 1.0 - _fc.x)), max(step(0.55, _hS - _h0) * smoothstep(0.45, 1.0, _fc.y), step(0.55, _hN - _h0) * smoothstep(0.45, 1.0, 1.0 - _fc.y)));
                                        }
                                        _expo = smoothstep(-0.5, 0.1, (_q.y + u_AlterraRainInfo.z) - (_tx.r * 512.0 - 64.0));
                                        _ret = _tx.g * (1.0 - _expo);   // what was wet before it got covered stays wet for a while
                                    }
                                }
                                if (_alterraLeafPx > 0.5) { _expo = smoothstep(0.55, 0.9, v_AlterraSkyL); _ret = 0.0; }   // leaves out under the sky are wet all over, every face (rain runs through a canopy)
                                if (_alterraLeafPx < 0.5 && _fn.y < -0.5) { _expo = 0.0; _ret = 0.0; }   // nothing wets the underside of a log or a block (rain falls from above)
                                float _wt = max(max(u_AlterraWet.x * _expo, _ret), smoothstep(0.02, 0.07, _wl) * float(_hasW));   // standing water is wet wherever it has run to, under trees too (the shade only stops rain, not a puddle spreading)
                                _wt *= 1.0 - _snowPx;   // snow does not get wet (it neither darkens nor mirrors)
                                if (_wt > 0.01) {
                                    float _flat = smoothstep(0.85, 0.98, _fn.y) * (_alterraLeafPx > 0.5 ? 0.0 : 1.0) * (1.0 - _snowPx), _lm = dot(color.rgb, vec3(0.299, 0.587, 0.114));   // puddles and sheen only on ground that faces up, never on leaves (they are wet all over, but do not mirror)
                                    color.rgb = mix(color.rgb, mix(vec3(_lm), color.rgb, 1.12) * mix(0.80, 0.78, step(0.5, _alterraLeafPx)), _wt);   // wet things are darker and richer (leaves a little less)
                                    if (_alterraLeafPx > 0.5) {   // wet leaves shine: they mirror the sky (more at a low angle), catch the sun in a soft glint, and the lighter parts of the leaf (veins, edges) shine most
                                        vec3 _lv = normalize(v_AlterraPos), _lr = reflect(_lv, _fn);
                                        float _lfr = pow(1.0 - clamp(dot(-_lv, _fn), 0.0, 1.0), 2.5), _lt = clamp((_lm - 0.1) * 3.0, 0.0, 1.0);
                                        vec3 _lenv = mix(u_AlterraHorizon.rgb, u_AlterraZenith.rgb, clamp(_lr.y * 1.5, 0.0, 1.0));
                                        float _lgl = pow(max(dot(_lr, u_AlterraSky.xyz), 0.0), 22.0) * u_AlterraSky.w;
                                        color.rgb += (_lenv * (0.20 + 0.45 * _lfr) + vec3(1.0, 0.97, 0.9) * _lgl * 0.8) * _lt * _wt;
                                    }
                                    vec2 _pw = v_AlterraPos.xz + u_AlterraCam.xz; float _k = 0.0061359232;
                                    float _pn = 0.5 + 0.25 * cos(dot(_pw, vec2(211.0, 97.0)) * _k + 1.0) + 0.25 * cos(dot(_pw, vec2(-139.0, 181.0)) * _k + 2.0) + 0.12 * cos(dot(_pw, vec2(397.0, -251.0)) * _k + 0.3);
                                    float _pud = _hasW ? smoothstep(0.02, 0.07, _wl + (_pn - 0.5) * 0.03) * _flat : smoothstep(0.62 - 0.30 * _wt, 0.78 - 0.30 * _wt, _pn) * _flat * smoothstep(0.35, 0.9, _wt) * _wt * (1.0 - step(0.5, u_AlterraRainInfo.w));   // where the rain water has really gathered (or, without the map, low spots by a pattern)
                                    if (_pud > 0.02) {
                                        vec2 _pg = alterraRain(_pw, u_AlterraSea.x, u_AlterraWet.y) * 0.6 * smoothstep(0.06, 0.14, _wl);   // raindrop rings only on water that has really gathered: a thin film that has not filled up stays still
                                        vec3 _pnn = normalize(vec3(-_pg.x, 1.0, -_pg.y)), _pv = normalize(v_AlterraPos), _pr = reflect(_pv, _pnn);
                                        float _pf = mix(0.25, 1.0, pow(1.0 - clamp(dot(-_pv, _pnn), 0.0, 1.0), 3.0));
                                        vec3 _env = mix(u_AlterraHorizon.rgb, u_AlterraZenith.rgb, clamp(_pr.y * 1.5, 0.0, 1.0));
                                        if (u_AlterraScreen.z > 0.5 && u_AlterraPrev.w > 0.5 && u_AlterraHullsN.y > 0.5) { vec4 _pt = alterraTraceM(u_AlterraPrevVP, u_AlterraPrev.xyz, v_AlterraPos, _pr, 12, 1.25); _env = mix(_env, _pt.rgb, _pt.a); }
                                        vec3 _gl = vec3(1.0, 0.96, 0.88) * pow(max(dot(_pr, u_AlterraSky.xyz), 0.0), 200.0) * 0.8 * u_AlterraSky.w;
                                        // like shallow water, without waves: the ground seen through it goes darker and a little blue, the surface mirrors sky and world (more at a low angle), a darker rim where it meets wet ground
                                        vec3 _thru = color.rgb * mix(vec3(1.0), vec3(0.66, 0.74, 0.80), smoothstep(0.0, 0.8, _pud) * (0.4 + 0.6 * smoothstep(0.02, 0.10, _wl)));
                                        color.rgb = mix(_thru, _env * 0.9 + _gl, _pud * mix(0.16, 0.8, _pf) * (0.35 + 0.65 * smoothstep(0.02, 0.10, _wl)));   // shallow: a faint sheen, deeper: more and more of a mirror
                                        color.rgb *= 1.0 - 0.22 * _pud * (1.0 - _pud) * 4.0 * (1.0 - _pf);
                                        float _pe = _wallF * smoothstep(0.12, 0.35, _pud);   // a thin pale line of foam only where the puddle touches a block (not along its edge on open ground), broken up a little so it is not a clean line
                                        color.rgb = mix(color.rgb, vec3(0.86, 0.91, 0.95) * clamp(_lm * 1.6 + 0.2, 0.0, 1.0), _pe * (0.35 + 0.25 * sin(dot(_pw, vec2(7.3, 5.1)) + u_AlterraSea.x * 0.6)));
                                    }
                                    color.rgb += u_AlterraHorizon.rgb * (0.05 * _flat + 0.07 * step(0.5, _alterraLeafPx)) * _wt;   // a faint sheen on wet ground, a little more on wet leaves   // a faint sheen on everything wet
                                }
                            }
                            // under water: the ground, plants and walls around you get caustics (strongest near the surface, fading with depth)
                            if (u_AlterraUnder.x > 0.5 && v_AlterraView.w < 0.5 && u_AlterraScreen.w > 0.0) {
                                float _dp = u_AlterraUnder.y - v_AlterraPos.y;
                                if (_dp > 0.0) {
                                    vec3 _fnrm = normalize(cross(dFdx(v_AlterraPos), dFdy(v_AlterraPos)));
                                    float _face = 0.35 + 0.65 * abs(_fnrm.y);
                                    float _cf = alterraCaustic(v_AlterraPos.xz + u_AlterraCam.xz, u_AlterraSea.x) * exp(-_dp * 0.05) * smoothstep(0.0, 0.5, _dp) * _face * u_AlterraScreen.w * (0.15 + 0.85 * u_AlterraSky.w);   // the same soft light as on the bottom seen from above the water
                                    color.rgb *= 1.0 + min(_cf, 1.0) * 0.0 * vec3(0.8, 1.0, 1.08);   // (the scene pass draws the underwater caustics and shafts, finer)   // a faint moving light on the bottom, never a bright blotch (dark stone stays dark)   // a soft teal light, exactly as seen from above (not white, never blowing out)
                                }
                            }
                            // the water surface seen from below: past about 49 degrees from straight up it is a mirror (of the bottom, bent by the ripples), inside that a bright window to the sky
                            if (v_AlterraView.w > 0.5 && v_AlterraView.w < 1.5 && (v_AlterraView.y > 0.0 || (v_AlterraTop < 0.97 && u_AlterraUnder.y - v_AlterraView.y < 1.3)) && u_AlterraUnder.x > 0.5) {   // (a side of the water only counts as the water's edge within a block of the surface: deeper in flowing water those faces keep their own look, translucent, and no longer show as opaque blue sheets on the blocks)
                                vec2 _g = alterraSlope(v_AlterraWaveP * 2.5, u_AlterraSea, u_AlterraSea2, v_AlterraSea) * 0.16;
                                vec3 _n = normalize(vec3(_g.x, -1.0, _g.y));
                                if (v_AlterraTop < 0.97) {   // a side of the water seen from inside it (where the water ends at a step or a shore): the same window and mirror, about the face's own normal turned to the viewer
                                    vec3 _fn = normalize(cross(dFdx(v_AlterraView.xyz), dFdy(v_AlterraView.xyz)));
                                    _n = normalize((dot(_fn, v_AlterraView.xyz) > 0.0 ? -_fn : _fn) + vec3(_g.x, 0.0, _g.y) * 0.4);
                                }
                                vec3 _v = normalize(v_AlterraView.xyz);
                                float _c = clamp(dot(_v, -_n), 0.0, 1.0);
                                float _s = sqrt(1.0 - _c * _c) * 1.333;
                                vec3 _deep = mix(v_Color.rgb, vec3(0.30, 0.75, 0.80), 0.6) * vec3(0.30, 0.55, 0.60) * (0.35 + 0.65 * u_AlterraSky.w);   // clear water lit from above: the biome tint turned turquoise (the plain tint is a strong blue, which made it navy), darker only at night
                                vec3 _mirror = _deep * 0.95 * (0.3 + 0.7 * u_AlterraSky.w) + _deep * 0.25;   // a calm mirror: no bright moving pattern
                                vec3 _tr = refract(_v, _n, 1.333);
                                vec3 _window = (mix(u_AlterraHorizon.rgb, u_AlterraZenith.rgb, clamp(_tr.y * 1.5, 0.0, 1.0)) * 1.0 + 0.05) * vec3(0.78, 0.95, 1.0) + vec3(1.0, 0.96, 0.85) * pow(max(dot(_tr, u_AlterraSky.xyz), 0.0), 120.0) * 2.5 * u_AlterraSky.w;
                                _window *= mix(1.0, 0.45, smoothstep(0.25, 0.95, _s));   // the light thins out towards the rim
                                float _edge = smoothstep(0.78, 1.0, _s);   // Snell's window: the sky squeezed into a circle about 97 degrees across, its edge spread wide into the mirror beyond it
                                color.rgb = mix(_window, _mirror, _edge) + vec3(0.55, 0.8, 0.9) * (smoothstep(0.6, 0.97, _s) - _edge) * 0.2;
                                color.a = 1.0;
                            }
                            // the water look: ripple normals worked out from the position (no picture), the sky and the sun or moon mirrored by a Schlick fresnel, and a glint
                            if (v_AlterraView.w > 0.5 && v_AlterraView.w < 1.5 && ((v_AlterraTop > 0.97 && (v_AlterraView.y < 0.0 || u_AlterraUnder.x < 0.5)) || (v_AlterraTop < 0.97 && u_AlterraUnder.x < 0.5))) {   // every face of the water, the sides and bottom too, through the same look (a side face has the face's own normal as its base): a side face (where water steps down at a shore) keeps vanilla's look (the water maths assume a surface facing up, and turned side faces black)
                                vec2 _g = alterraSlope(v_AlterraWaveP * 2.5, u_AlterraSea, u_AlterraSea2, v_AlterraSea) * 0.32;   // ripples at a smaller scale: many small waves, not a few big streaks (the slope of f(2.5x) is 2.5 times steeper, hence the factor)
                                _g += v_AlterraSwirl.xy;   // the slope of a whirlpool
                                float _dist = length(v_AlterraView.xyz);
                                if (u_AlterraRipN.x > 0.5 && _dist < 40.0) _g += alterraWakes(v_AlterraView.xz) * 0.5 * (1.0 - smoothstep(18.0, 40.0, _dist));   // wakes and splashes
                                if (u_AlterraWhirlInfo.w > 0.5 && _dist < 90.0) {   // a whirlpool: the slope of its grid water
                                    vec2 _wq = (v_AlterraView.xz + u_AlterraWhirlInfo.xy) * u_AlterraWhirlInfo.z;
                                    if (_wq.x > 0.01 && _wq.y > 0.01 && _wq.x < 0.99 && _wq.y < 0.99) {
                                        float _wt = 1.0 / u_AlterraWhirlInfo.w, _wc = 1.0 / (u_AlterraWhirlInfo.z * u_AlterraWhirlInfo.w);
                                        _g += vec2(texture(u_AlterraWhirl, _wq + vec2(_wt, 0.0)).r - texture(u_AlterraWhirl, _wq - vec2(_wt, 0.0)).r, texture(u_AlterraWhirl, _wq + vec2(0.0, _wt)).r - texture(u_AlterraWhirl, _wq - vec2(0.0, _wt)).r) / (2.0 * _wc);
                                    }
                                }
                                if (u_AlterraWaveGridInfo.w > 0.5 && _dist < 40.0) {   // the grid water: ripples of rain, swimmers, boats and splashes, bouncing off the shore
                                    vec2 _gp = (v_AlterraView.xz + u_AlterraWaveGridInfo.xy) * u_AlterraWaveGridInfo.z;
                                    if (_gp.x > 0.02 && _gp.y > 0.02 && _gp.x < 0.98 && _gp.y < 0.98) _g += texture(u_AlterraWaveGrid, _gp).rg * 2.2 * (1.0 - smoothstep(20.0, 40.0, _dist));
                                }
                                if (u_AlterraSea2.x > 0.02 && _dist < 28.0) _g += alterraRain(v_AlterraWaveP, u_AlterraSea.x, u_AlterraSea2.x) * (1.0 - smoothstep(10.0, 28.0, _dist));   // raindrop rings

                                vec3 _fnw = vec3(0.0, 1.0, 0.0);
                                if (v_AlterraTop < 0.97 || v_AlterraView.y > 0.0) { _fnw = normalize(cross(dFdx(v_AlterraView.xyz), dFdy(v_AlterraView.xyz))); if (dot(_fnw, v_AlterraView.xyz) > 0.0) _fnw = -_fnw; }
                                vec3 _n = normalize(_fnw + vec3(-_g.x, 0.0, -_g.y));
                                vec3 _v = normalize(v_AlterraView.xyz);
                                float _nv = clamp(dot(-_v, _n), 0.0, 1.0);
                                float _fr = 0.06 + 0.94 * pow(1.0 - _nv, 4.0);   // a little more than real water's 2% looking straight down, so the world is still seen in it from above
                                vec3 _r = reflect(_v, _n);
                                _r.y = max(_r.y, 0.02);   // a wave facet tilted below the horizon still sees sky (as the heavy shader packs do), never the mirrored underside
                                float _sd = max(dot(_r, u_AlterraSky.xyz), 0.0);
                                vec3 _sky = mix(u_AlterraHorizon.rgb, u_AlterraZenith.rgb, pow(clamp(_r.y * 1.3, 0.0, 1.0), 0.6)) * vec3(0.8, 0.93, 1.0) * mix(1.0, 0.75, smoothstep(0.0, 0.35, _r.y));   // at the horizon the water takes on the sky's own colour   // the mirror shows the sky a little dimmer than it is, so the water is never brighter than the day
                                _sky += vec3(1.0, 0.82, 0.55) * pow(_sd, 6.0) * 0.18 * u_AlterraSky.w;
                                _sky = max(_sky, vec3(0.035, 0.055, 0.095));   // at night the mirror still shows a faint moonlit sky   // the glow of the sky around the sun, in the mirror too
                                float _gl = pow(_sd, 700.0) * 3.0 + pow(_sd, 70.0) * 0.30 + pow(_sd, 14.0) * 0.05;   // sun or moon: sharp sparkles, a wider shimmer and a faint broad path
                                float _ga = abs(u_AlterraHorizon.w); float _sg = step(0.0, u_AlterraHorizon.w) * 2.0 - 1.0; vec3 _sdir = u_AlterraSky.xyz * _sg;   // the sunset glow's strength, and where the sun really is (u_AlterraSky turns into the moon once the sun is down)
                                float _twl = smoothstep(0.04, 0.5, _ga);   // sunrise or sunset
                                float _sdt = max(dot(_r, _sdir), 0.0);
                                _gl += (pow(_sdt, 4.0) * 0.30 + pow(_sdt, 26.0) * 0.55) * _twl * step(0.0, u_AlterraHorizon.w);   // a wide glowing path of the sun across the water while it is up
                                vec2 _rh = normalize(_r.xz + vec2(1e-4)), _sh = normalize(_sdir.xz + vec2(1e-4));
                                float _gw = pow(max(dot(_rh, _sh), 0.0), 1.6) * exp(-max(_r.y, 0.0) * 3.0);   // the glow belongs to the side of the sky where the sun is, and hugs the horizon
                                vec3 _gc = vec3(0.70 + 0.30 * _ga, 0.20 + 0.70 * _ga * _ga, 0.20);
                                _sky = mix(_sky, _gc * (0.80 + 0.40 * _ga), clamp(_ga * (0.12 + 0.88 * _gw), 0.0, 0.95));   // the sky in the water turns the colour of the sunset, there, and only a little on the far side
                                vec3 _hue = v_Color.rgb;
                                bool _scene = u_AlterraScreen.z > 0.5;
                                float _lightvFoam = clamp(max(max(_hue.r, _hue.g), _hue.b) * 1.1, 0.2, 1.0);
                                vec3 _water = _hue * vec3(0.38, 0.62, 0.90) * (0.92 + 0.16 * dot(_n, u_AlterraSky.xyz));
                                float _alpha = mix(0.74, 0.97, _fr);
                                if (_scene) {   // how deep the water is here: clear and turquoise over shallows with the real bottom showing, darker and bluer with depth (red is absorbed first)
                                    vec2 _uv = gl_FragCoord.xy / u_AlterraScreen.xy;
                                    vec2 _ruv = clamp(_uv + _g.xy * 0.03, 0.002, 0.998);
                                    float _w = 1.0 / gl_FragCoord.w;
                                    float _sw = alterraW(texture(u_AlterraDepth, _ruv).r);
                                    if (_sw < _w) { _ruv = _uv; _sw = alterraW(texture(u_AlterraDepth, _ruv).r); }   // something in front of the water: no bending there
                                    float _d = max(_sw - _w, 0.0) * length(v_AlterraView.xyz) / _w;   // blocks of water the view travels through
                                    if (u_AlterraHullsN.x > 0.5 && _d < 1.8) {   // no water inside a ship's hull: where the surface lies inside the oriented box of a hull AND what is just below it is the hull's own floor (not the sea bed), the water is cut out. Water above the rim is above the box, so it still shows.
                                        for (int _h = 0; _h < 4; _h++) {
                                            if (_h >= int(u_AlterraHullsN.x)) break;
                                            vec3 _hd = v_AlterraView.xyz - u_AlterraHulls[_h * 4].xyz;
                                            vec4 _a0 = u_AlterraHulls[_h * 4 + 1], _a1 = u_AlterraHulls[_h * 4 + 2], _a2 = u_AlterraHulls[_h * 4 + 3];
                                            if (abs(dot(_hd, _a0.xyz)) < _a0.w && abs(dot(_hd, _a1.xyz)) < _a1.w && abs(dot(_hd, _a2.xyz)) < _a2.w) discard;
                                        }
                                    }
                                    vec3 _under = texture(u_AlterraScene, _ruv).rgb;
                                    float _cfv = 0.0;
                                    if (u_AlterraScreen.w > 0.0 && _sw < 900.0) {   // caustics on the bottom seen through the water: they fade slowly with depth (clear water shows them far down), and weaker by night and in rain
                                        vec3 _bottom = normalize(v_AlterraView.xyz) * (length(v_AlterraView.xyz) + _d);
                                        _cfv = alterraCaustic(_bottom.xz + (v_AlterraWaveP - v_AlterraView.xz), u_AlterraSea.x) * exp(-_d * 0.05) * smoothstep(0.0, 0.5, _d) * (1.0 - smoothstep(22.0, 60.0, length(v_AlterraView.xyz))) * u_AlterraScreen.w * (0.15 + 0.85 * u_AlterraSky.w);
                                        _under = min(_under * (1.0 + min(_cfv, 1.0) * 0.10 * vec3(0.75, 1.0, 1.1)) + vec3(0.0, 0.008, 0.012) * min(_cfv, 1.0), vec3(0.9));   // a soft teal light, not white; capped so sand never blows out
                                    }
                                    // tropical: turquoise over the shallows turning to a clear vivid blue in the deep (never a dark navy by day); the bottom shows through and loses red first
                                    float _lightv = clamp(max(max(_hue.r, _hue.g), _hue.b) * 1.1, 0.12, 1.0);
                                    float _ex = clamp(0.25 + 1.3 * dot(u_AlterraZenith.rgb, vec3(0.299, 0.587, 0.114)), 0.38, 1.0);   // never darker than moonlit water (a deep blue, not black)   // how bright the sky is: the water's own colour follows it, so a storm or the night does not leave bright turquoise under a dark sky
                                    vec3 _biome = clamp(_hue / vec3(0.247, 0.463, 0.894), vec3(0.35), vec3(1.6));
                                    float _murk = 1.0 - smoothstep(0.30, 0.42, _hue.b / max(_hue.r + _hue.g + _hue.b, 0.01));   // only murky biomes (swamps, bogs: little blue in their water) change the colour a lot; every other biome stays tropical, with just a hint of its own colour   // the biome's water colour against vanilla's own (1 = ordinary water): swamps murky, warm seas brighter
                                    vec3 _tint = mix(vec3(0.14, 0.80, 0.70), vec3(0.05, 0.50, 0.66), 1.0 - exp(-_d * 0.07)) * mix(vec3(1.0), _biome, 0.12 + 0.68 * _murk) * _lightv * _ex * 0.85;   // clear turquoise over shallows, blue in the deep, smoothly with depth
                                    _water = mix(_under * exp(-_d * vec3(0.26, 0.09, 0.06)) * mix(vec3(1.0), vec3(0.80, 1.0, 0.96), 1.0 - exp(-_d * 0.5)), _tint, (1.0 - exp(-_d * 0.09)) * 0.8);   // clear over shallows (the bottom, filtered turquoise), the water's own colour taking over with depth   // the water's own colour takes over from the bottom with depth, shallows stay clear
                                    _water = min(_water + _tint * _cfv * 0.2, vec3(0.95));   // the light pattern stays visible over deeper bottoms too, in the tinted water

                                    _alpha = 1.0;
                                }
                                vec3 _refl = _sky; float _hit = 0.0;
                                if (_scene && _fr > 0.03 && _dist < 48.0 && u_AlterraHullsN.y > 0.5 && v_AlterraTop > 0.97) {   // (tops only: on the sides of a waterfall the trace cost a fortune and shows little)   // far off a reflection is a few pixels: the sky there is enough   // the world in the mirror: found with a ray along a calmer normal (the full ripple normal scatters it into repeated, smeared copies of whatever stands there)
                                    vec3 _rs = reflect(_v, normalize(mix(_n, vec3(0.0, 1.0, 0.0), 0.6)));
                                    _rs.y = max(_rs.y, 0.02);
                                    vec4 _t = _fr > 0.15 ? alterraTrace(v_AlterraView.xyz, _rs, 14, 1.22) : alterraTrace(v_AlterraView.xyz, _rs, 8, 1.35);
                                    _refl = mix(_sky, _t.rgb, _t.a * 0.95); _hit = _t.a;
                                }   // only at the angles where it shows, which keeps it cheap
                                // light scattered inside the waves: slopes turned toward the light and thin crests glow turquoise, troughs stay deeper, which gives the surface body instead of one flat tone
                                float _lit = 0.25 + 0.75 * clamp(dot(vec3(0.0, 1.0, 0.0), u_AlterraSky.xyz) * 0.6 + 0.5, 0.0, 1.0);
                                float _crest = smoothstep(0.06, 0.40, length(_g)) * u_AlterraSky.w;   // steep facets in daylight only: a little light scattered through the thin water
                                _water += vec3(0.01, 0.07, 0.07) * _crest * _lit * (0.35 + 0.65 * _lightvFoam);
                                _water *= 0.88 + 0.22 * _nv;
                                                                color.rgb = mix(_water, _refl, max(_fr * mix(0.8, 1.0, _fr), 0.3 * _hit * (0.35 + 0.65 * _fr)));   // the world seen in the water counts for more than a bare Fresnel, so shores, trees and things standing in it are visible at most angles
                                if (u_AlterraFoamN.x > 0.5 && _dist < 60.0) color.rgb = mix(color.rgb, vec3(0.93, 0.97, 1.0) * _lightvFoam, alterraFoam(v_AlterraView.xz, v_AlterraWaveP) * 0.75);   // foam from splashes, waterfalls and wakes + vec3(1.0, 0.96, 0.88) * _gl * u_AlterraSky.w;
                                color.a = clamp(_alpha + _gl * u_AlterraSky.w * 0.4, 0.0, 1.0);
                            }
                            // metal: reflects the sky and the world (from the frame before) in its own colour, more at a slant, with a sharp glint of the sun or moon
                            if (v_AlterraView.w > 2.5 && u_AlterraHullsN.z > 0.5) {
                                vec3 _p = v_AlterraView.xyz;
                                vec3 _n = normalize(cross(dFdx(_p), dFdy(_p)));
                                vec3 _v = normalize(_p);
                                if (dot(_n, _v) > 0.0) _n = -_n;
                                vec3 _r = reflect(_v, _n);
                                float _fr = 0.24 + 0.5 * pow(1.0 - clamp(dot(-_v, _n), 0.0, 1.0), 4.0);
                                vec3 _env = mix(u_AlterraHorizon.rgb, u_AlterraZenith.rgb, clamp(_r.y * 1.5, 0.0, 1.0));
                                if (u_AlterraScreen.z > 0.5 && u_AlterraPrev.w > 0.5 && u_AlterraHullsN.y > 0.5) { vec4 _t = alterraTraceM(u_AlterraPrevVP, u_AlterraPrev.xyz, _p, _r, 12, 1.25); _env = mix(_env, _t.rgb, _t.a); }
                                vec3 _glint = vec3(1.0, 0.96, 0.88) * pow(max(dot(_r, u_AlterraSky.xyz), 0.0), 100.0) * 0.55 * u_AlterraSky.w;
                                _env *= mix(1.0, 0.5, u_AlterraSky.w);   // daylight is much brighter than what a metal surface shows of it
                                _env = _env / (1.0 + 0.6 * _env);   // soft limit, so bright sky never blows a metal out to white
                                color.rgb = mix(color.rgb, _env * (color.rgb * 1.2 + 0.04) + _glint, _fr);
                            }
                            // ice keeps its picture and gets a smooth gloss: the sky and the sun or moon mirrored, no ripples
                            if (v_AlterraView.w > 1.5 && v_AlterraView.y < 0.0 && abs(normalize(cross(dFdx(v_AlterraView.xyz), dFdy(v_AlterraView.xyz))).y) > 0.7) {   // (top faces only: the gloss mirrors straight up, which on a side showed as a bright patch that ended at the height of your eyes)
                                vec3 _v = normalize(v_AlterraView.xyz);
                                float _fr = 0.04 + 0.96 * pow(1.0 - clamp(-_v.y, 0.0, 1.0), 5.0);
                                vec3 _r = reflect(_v, vec3(0.0, 1.0, 0.0));
                                vec3 _sky = mix(u_AlterraHorizon.rgb, u_AlterraZenith.rgb, clamp(_r.y * 1.5, 0.0, 1.0));
                                float _gl = pow(max(dot(_r, u_AlterraSky.xyz), 0.0), 250.0) * 1.5 * u_AlterraSky.w;
                                if (u_AlterraScreen.z > 0.5 && _fr > 0.045 && u_AlterraHullsN.y > 0.5) { vec4 _t = alterraTrace(v_AlterraView.xyz, _r, 14, 1.2); _sky = mix(_sky, _t.rgb, _t.a); }
                                color.rgb = mix(color.rgb, _sky, _fr * 0.55) + vec3(1.0, 0.97, 0.9) * _gl;
                            }

                            // shore foam: a thin straight line along the water's edge where it meets land, strongest against the block's face and fading into the water in texel-sized layers
                            if (v_AlterraShore.z > 0.5) {
                                ivec2 _cell = ivec2(floor(v_AlterraShore.xy));
                                ivec2 _sz = textureSize(u_AlterraShoreMap, 0);
                                if (_cell.x >= 0 && _cell.y >= 0 && _cell.x < _sz.x && _cell.y < _sz.y) {
                                    int _b = int(texelFetch(u_AlterraShoreMap, _cell, 0).r * 255.0 + 0.5);
                                    vec2 _l = fract(v_AlterraShore.xy);
                                    float _d = 9.0;   // distance (in blocks) from the nearest edge or corner that is land
                                    if ((_b & 1) != 0) _d = min(_d, _l.x);
                                    if ((_b & 2) != 0) _d = min(_d, 1.0 - _l.x);
                                    if ((_b & 4) != 0) _d = min(_d, _l.y);
                                    if ((_b & 8) != 0) _d = min(_d, 1.0 - _l.y);
                                    if ((_b & 16) != 0) _d = min(_d, max(_l.x, _l.y));
                                    if ((_b & 32) != 0) _d = min(_d, max(1.0 - _l.x, _l.y));
                                    if ((_b & 64) != 0) _d = min(_d, max(_l.x, 1.0 - _l.y));
                                    if ((_b & 128) != 0) _d = min(_d, max(1.0 - _l.x, 1.0 - _l.y));
                                    float _f = _d < 0.0625 ? 1.0 : (_d < 0.125 ? 0.72 : (_d < 0.1875 ? 0.48 : (_d < 0.28 ? 0.27 : (_d < 0.375 ? 0.12 : 0.0))));
                                    color.rgb = mix(color.rgb, vec3(0.95, 0.98, 1.0), _f * 0.5);
                                    color.a = mix(color.a, 0.8, _f * 0.3);
                                }
                            }
                        """);
            if (System.getProperty("alterra.dumpShaders") != null) { try { java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("alterra.dumpShaders")), sodiumFragment); } catch (java.io.IOException ex) { LOG.warn("could not dump the shader: {}", ex.toString()); } }   // (a test switch)
            return sodiumFragment;
        } catch (Throwable t) { LOG.warn("Alterra Weather: no shore foam with Sodium: {}", t.toString()); return null; }
    }

    private static boolean patchVanilla(Path dir) throws Exception {
        String vsh = read("/assets/minecraft/shaders/core/rendertype_cutout_mipped.vsh"), json = read("/assets/minecraft/shaders/core/rendertype_cutout_mipped.json");
        if (vsh == null || json == null || !vsh.contains("vec3 pos = Position + ChunkOffset;") || !json.contains("\"uniforms\"")) { LOG.warn("Alterra Weather: vanilla's leaf shader looks different than expected, no sway without Sodium"); return false; }
        vsh = vsh.replace("void main() {", "uniform vec4 AlterraWave;   // time, wind x, wind z, how much\nuniform sampler2D AlterraMask;\n" + FUNCTION + "\nvoid main() {")
                .replace("vec3 pos = Position + ChunkOffset;", "vec3 pos = Position + ChunkOffset;\n    if (AlterraWave.w > 0.0) pos.xz += alterraSway(Position, AlterraWave, textureLod(AlterraMask, UV0, 0.0).r * AlterraWave.w * 0.05);");
        json = json.replaceFirst("(\"samplers\"\\s*:\\s*\\[[^\\]]*)\\]", "$1, { \"name\": \"AlterraMask\" } ]");
        json = json.replaceFirst("\"uniforms\"\\s*:\\s*\\[", "\"uniforms\": [\n        { \"name\": \"AlterraWave\", \"type\": \"float\", \"count\": 4, \"values\": [ 0.0, 0.0, 0.0, 0.0 ] },");
        Path d = dir.resolve("assets/minecraft/shaders/core"); Files.createDirectories(d);
        Files.writeString(d.resolve("rendertype_cutout_mipped.vsh"), vsh); Files.writeString(d.resolve("rendertype_cutout_mipped.json"), json);
        return true;
    }

    private static String read(String path) throws Exception {
        try (InputStream in = Minecraft.class.getResourceAsStream(path)) { return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8); }
    }

    // ---------------------------------------------------------------- the numbers that go to the shaders, every frame
    private static float[] params() {
        var mc = Minecraft.getInstance(); var level = mc.level; var cam = mc.gameRenderer.getMainCamera();
        if (level == null || !IlluminateConfig.get(IlluminateConfig.SWAY)) return null;
        var pos = cam.getPosition();
        var w = PrismApi.wind().at(level, pos.x, pos.z);
        float time = clock();
        float amount = IlluminateConfig.get(IlluminateConfig.SWAY_AMOUNT).floatValue();
        // calm air still moves the leaves a little; wind moves them more
        double sp = w.length(); float k = (float) (0.55 + 0.9 * Math.min(sp, 1.5));
        return new float[] {time, (float) w.x, (float) w.z, amount * k, IlluminateConfig.get(IlluminateConfig.SWAY_PLANTS) ? amount * k : 0f};
    }

    private static java.lang.reflect.Method handleMethod;
    /** The OpenGL handle of one of Sodium's programs (the method is on a superclass, so a mixin cannot shadow it). */
    public static int handleOf(Object program) {
        try {
            if (handleMethod == null) handleMethod = Class.forName("net.caffeinemc.mods.sodium.client.gl.GlObject").getMethod("handle");
            return (int) handleMethod.invoke(program);
        } catch (Throwable t) { return -1; }
    }

    // ---------------------------------------------------------------- the mask: which pictures in the block atlas sway
    private static final int MASK_UNIT = 11;   // (one of the units Minecraft keeps track of: 0 to 11)
    private static final int MASK = 2048;   // texels; the mask covers the atlas whatever its size
    private static int maskTexture; private static boolean maskBuilt;
    static void invalidateMask() { maskBuilt = false; }

    /** Marks the pictures (sprites) of leaves (red) and of upright plants (green): everything made of these blocks' models. Built once after the models are loaded. */
    private static Object probe;
    private static int mask() {
        // after a resource reload every picture is a new object (and may sit somewhere else in the atlas): notice that and make the mask again
        try {
            Object now = Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getBlockModel(net.minecraft.world.level.block.Blocks.OAK_LEAVES.defaultBlockState()).getParticleIcon(net.neoforged.neoforge.client.model.data.ModelData.EMPTY);
            if (now != probe) { probe = now; maskBuilt = false; }
        } catch (Throwable ignored) { }
        if (maskBuilt && maskTexture != 0) return maskTexture;
        try {
            var mc = Minecraft.getInstance(); var shaper = mc.getBlockRenderer().getBlockModelShaper();
            byte[] data = new byte[MASK * MASK * 4];
            var random = net.minecraft.util.RandomSource.create(7);
            // leaves: only pictures called ...leaves (not the fruit or flowers drawn on them). plants: only pictures used on upright faces and never on flat ones
            // (a clover lying on the ground would otherwise lift one edge)
            java.util.Set<net.minecraft.client.renderer.texture.TextureAtlasSprite> leaf = new java.util.HashSet<>(), upright = new java.util.HashSet<>(), flat = new java.util.HashSet<>();
            for (var block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
                boolean isFruit = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath().startsWith("hanging_");   // fruit hanging in a tree moves with the leaves above it, unchanged in shape
                boolean isLeaf = block.defaultBlockState().is(net.minecraft.tags.BlockTags.LEAVES) || isFruit;
                boolean isPlant = block instanceof net.minecraft.world.level.block.BushBlock && !(block instanceof net.minecraft.world.level.block.MushroomBlock) || block instanceof net.minecraft.world.level.block.VineBlock;
                if (!isLeaf && !isPlant) continue;
                int n = 0;
                for (var st : block.getStateDefinition().getPossibleStates()) {
                    if (n++ > 48) break;
                    var model = shaper.getBlockModel(st);
                    for (int d = -1; d < 6; d++) for (var quad : model.getQuads(st, d < 0 ? null : net.minecraft.core.Direction.from3DDataValue(d), random, net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null)) {
                        var sprite = quad.getSprite();
                        if (isLeaf) { if (isFruit || sprite.contents().name().getPath().contains("leaves")) leaf.add(sprite); }
                        else (quad.getDirection().getAxis().isHorizontal() ? upright : flat).add(sprite);
                    }
                }
            }
            java.util.Set<net.minecraft.client.renderer.texture.TextureAtlasSprite> plant = new java.util.HashSet<>(upright); plant.removeAll(flat); plant.removeAll(leaf);
            for (var sp : leaf) paint(data, sp, 0);
            for (var sp : plant) paint(data, sp, 1);
            try {   // water pictures (blue): the surface gets swells
                var atlas = mc.getModelManager().getAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);
                for (String n : new String[] {"water_still", "water_flow"}) { var wsp = atlas.getSprite(net.minecraft.resources.ResourceLocation.withDefaultNamespace("block/" + n)); LOG.info("Alterra Illuminate: water sprite {} at u {}..{} v {}..{} ({})", n, wsp.getU0(), wsp.getU1(), wsp.getV0(), wsp.getV1(), wsp.contents().name()); paint(data, wsp, 2); }
                paint(data, atlas.getSprite(net.minecraft.resources.ResourceLocation.withDefaultNamespace("block/snow")), 3, (byte) 23);
                for (String sn : new String[] {"block/grass_block_snow", "block/podzol_side_snow", "block/mycelium_side_snow"}) { var sp = atlas.getSprite(net.minecraft.resources.ResourceLocation.withDefaultNamespace(sn)); if (!sp.contents().name().getPath().equals("missingno")) paint(data, sp, 3, (byte) 23); }   // snowy ground's snow-capped sides are drawn by the snow surface too (its walls cover them)
                paint(data, atlas.getSprite(net.minecraft.resources.ResourceLocation.withDefaultNamespace("block/powder_snow")), 3, (byte) 23);   // powder snow is drawn by the snow surface too   // snow (a small alpha, between nothing and the metals' 80): the tops of its layers are smoothed
                for (String n : new String[] {"ice", "frosted_ice_0", "frosted_ice_1", "frosted_ice_2", "frosted_ice_3", "packed_ice", "blue_ice"}) paint(data, atlas.getSprite(net.minecraft.resources.ResourceLocation.withDefaultNamespace("block/" + n)), 3);   // ice (alpha): a smooth, glossy surface
            } catch (Throwable ignored) { }
            try {   // metal blocks (alpha 80, less than ice's 255): storage blocks and cut copper, so iron, gold, copper, diamond, netherite and emerald blocks, and brass, zinc, steel and the like from other mods, reflect
                var random2 = net.minecraft.util.RandomSource.create(11); int metals = 0;
                for (var block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
                    String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath();
                    boolean metal = path.endsWith("iron_block") || path.endsWith("gold_block") || path.contains("copper") && !path.contains("ore") && !path.contains("golem") && !path.contains("chest") || path.endsWith("diamond_block") || path.endsWith("netherite_block") || path.endsWith("emerald_block")
                            || path.endsWith("brass_block") || path.endsWith("zinc_block") || path.endsWith("steel_block") || path.endsWith("bronze_block") || path.endsWith("silver_block") || path.endsWith("lead_block") || path.endsWith("nickel_block") || path.endsWith("tin_block");
                    if (!metal) continue;
                    var st = block.defaultBlockState(); var model = shaper.getBlockModel(st); metals++;
                    for (int d = -1; d < 6; d++) for (var quad : model.getQuads(st, d < 0 ? null : net.minecraft.core.Direction.from3DDataValue(d), random2, net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null)) paint(data, quad.getSprite(), 3, (byte) 80);
                }
                LOG.info("Alterra Weather: {} metal blocks reflect", metals);
            } catch (Throwable ignored) { }
            { int r = 0, g = 0, b = 0, a = 0; for (int i = 0; i < data.length; i += 4) { if ((data[i] & 255) > 127) r++; if ((data[i + 1] & 255) > 127) g++; if ((data[i + 2] & 255) > 127) b++; if ((data[i + 3] & 255) > 3) a++; }
              LOG.info("Alterra Illuminate: mask {}x{}: leaf {} plant {} water {} other {} of {} texels", MASK, MASK, r, g, b, a, MASK * MASK); }
            if (Boolean.getBoolean("alterra.waterdebug")) try {   // photo runs: keep the mask as a picture, and say where a log and a leaf sit in it
                var img = new java.awt.image.BufferedImage(MASK, MASK, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < MASK; y++) for (int x = 0; x < MASK; x++) { int k = (y * MASK + x) * 4; img.setRGB(x, y, 0xFF000000 | ((data[k] & 255) << 16) | ((data[k + 1] & 255) << 8) | (data[k + 2] & 255)); }
                javax.imageio.ImageIO.write(img, "png", new java.io.File("alterra_mask_rgb.png"));
                var img2 = new java.awt.image.BufferedImage(MASK, MASK, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < MASK; y++) for (int x = 0; x < MASK; x++) { int a = data[(y * MASK + x) * 4 + 3] & 255; img2.setRGB(x, y, 0xFF000000 | (a << 16) | (a << 8) | a); }
                javax.imageio.ImageIO.write(img2, "png", new java.io.File("alterra_mask_alpha.png"));
                var at = mc.getModelManager().getAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);
                for (String n : new String[] {"block/oak_log", "block/oak_leaves", "block/grass_block_side", "block/dirt"}) { var sp = at.getSprite(net.minecraft.resources.ResourceLocation.withDefaultNamespace(n)); int cx = (int) ((sp.getU0() + sp.getU1()) * 0.5 * MASK), cy = (int) ((sp.getV0() + sp.getV1()) * 0.5 * MASK), k = (cy * MASK + cx) * 4;
                    LOG.info("Alterra Illuminate: {} at u {}..{} v {}..{}: mask there r{} g{} b{} a{}", n, sp.getU0(), sp.getU1(), sp.getV0(), sp.getV1(), data[k] & 255, data[k + 1] & 255, data[k + 2] & 255, data[k + 3] & 255); }
            } catch (Throwable ex) { LOG.warn("mask debug failed", ex); }
            java.nio.ByteBuffer buf = org.lwjgl.system.MemoryUtil.memAlloc(data.length); buf.put(data).flip();
            int previous = GL20C.glGetInteger(org.lwjgl.opengl.GL11C.GL_TEXTURE_BINDING_2D);
            if (maskTexture == 0) maskTexture = org.lwjgl.opengl.GL11C.glGenTextures();
            com.mojang.blaze3d.platform.GlStateManager._bindTexture(maskTexture);
            org.lwjgl.opengl.GL11C.glPixelStorei(org.lwjgl.opengl.GL11C.GL_UNPACK_ALIGNMENT, 1);
            org.lwjgl.opengl.GL11C.glTexImage2D(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL30C.GL_RGBA8, MASK, MASK, 0, org.lwjgl.opengl.GL11C.GL_RGBA, org.lwjgl.opengl.GL11C.GL_UNSIGNED_BYTE, buf);
            org.lwjgl.opengl.GL11C.glTexParameteri(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER, org.lwjgl.opengl.GL11C.GL_NEAREST);
            org.lwjgl.opengl.GL11C.glTexParameteri(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER, org.lwjgl.opengl.GL11C.GL_NEAREST);
            org.lwjgl.opengl.GL11C.glTexParameteri(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE);
            org.lwjgl.opengl.GL11C.glTexParameteri(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE);
            com.mojang.blaze3d.platform.GlStateManager._bindTexture(previous);
            org.lwjgl.system.MemoryUtil.memFree(buf);
            maskBuilt = true;
            LOG.info("Alterra Weather: swaying marked {} leaf and {} plant pictures", leaf.size(), plant.size());
        } catch (Throwable t) { LOG.warn("Alterra Weather: could not build the sway mask", t); maskBuilt = true; }
        return maskTexture;
    }
    private static void paint(byte[] data, net.minecraft.client.renderer.texture.TextureAtlasSprite sp, int channel) { paint(data, sp, channel, (byte) 255); }
    private static void paint(byte[] data, net.minecraft.client.renderer.texture.TextureAtlasSprite sp, int channel, byte value) {
        int x0 = Math.max(0, (int) Math.ceil(sp.getU0() * MASK)), x1 = Math.min(MASK, (int) Math.floor(sp.getU1() * MASK)), y0 = Math.max(0, (int) Math.ceil(sp.getV0() * MASK)), y1 = Math.min(MASK, (int) Math.floor(sp.getV1() * MASK));
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) data[(y * MASK + x) * 4 + channel] = value;
    }

    private static final Map<Integer, int[]> LOCATIONS = new HashMap<>();
    private static final float[] VORTEX = new float[16];
    /** Called when one of Sodium's shader programs is bound. */
    public static void upload(int program) {
        if (program < 0) return;
        try {
            int[] loc = LOCATIONS.computeIfAbsent(program, p -> new int[] {GL20C.glGetUniformLocation(p, "u_AlterraWave"), GL20C.glGetUniformLocation(p, "u_AlterraWave2"), GL20C.glGetUniformLocation(p, "u_AlterraMask"), GL20C.glGetUniformLocation(p, "u_AlterraCam"), GL20C.glGetUniformLocation(p, "u_AlterraShore"), GL20C.glGetUniformLocation(p, "u_AlterraShoreMap"), GL20C.glGetUniformLocation(p, "u_AlterraSky"), GL20C.glGetUniformLocation(p, "u_AlterraZenith"), GL20C.glGetUniformLocation(p, "u_AlterraHorizon"), GL20C.glGetUniformLocation(p, "u_AlterraScene"), GL20C.glGetUniformLocation(p, "u_AlterraDepth"), GL20C.glGetUniformLocation(p, "u_AlterraScreen"), GL20C.glGetUniformLocation(p, "u_AlterraUnder"), GL20C.glGetUniformLocation(p, "u_AlterraSea"), GL20C.glGetUniformLocation(p, "u_AlterraSea2"), GL20C.glGetUniformLocation(p, "u_AlterraSeaMap"), GL20C.glGetUniformLocation(p, "u_AlterraSeaInfo"), GL20C.glGetUniformLocation(p, "u_AlterraRip"), GL20C.glGetUniformLocation(p, "u_AlterraRipN"), GL20C.glGetUniformLocation(p, "u_AlterraPrevVP"), GL20C.glGetUniformLocation(p, "u_AlterraPrev"), GL20C.glGetUniformLocation(p, "u_AlterraShips"), GL20C.glGetUniformLocation(p, "u_AlterraShipsN"), GL20C.glGetUniformLocation(p, "u_AlterraHulls"), GL20C.glGetUniformLocation(p, "u_AlterraHullsN"), GL20C.glGetUniformLocation(p, "u_AlterraWet"), GL20C.glGetUniformLocation(p, "u_AlterraRainMap"), GL20C.glGetUniformLocation(p, "u_AlterraRainInfo"), GL20C.glGetUniformLocation(p, "u_AlterraFoam"), GL20C.glGetUniformLocation(p, "u_AlterraFoamN"), GL20C.glGetUniformLocation(p, "u_AlterraFlow"), GL20C.glGetUniformLocation(p, "u_AlterraFlowInfo"), GL20C.glGetUniformLocation(p, "u_AlterraSnowMap"), GL20C.glGetUniformLocation(p, "u_AlterraSnowInfo"), GL20C.glGetUniformLocation(p, "u_AlterraVortex"), GL20C.glGetUniformLocation(p, "u_AlterraVortexN"), GL20C.glGetUniformLocation(p, "u_AlterraWaveGrid"), GL20C.glGetUniformLocation(p, "u_AlterraWaveGridInfo"), GL20C.glGetUniformLocation(p, "u_AlterraWhirl"), GL20C.glGetUniformLocation(p, "u_AlterraWhirlInfo")});   // (indices 34 and 35, 36 and 37)
            if (loc[0] < 0) return;
            if (SceneCopy.pending) { SceneCopy.pending = false; SceneCopy.capture(); }
            if (loc[4] >= 0) {   // the shore foam: where the shore map is, and whether it is on
                var cp = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                boolean on = ShoreMap.ready && IlluminateConfig.get(IlluminateConfig.FOAM);
                GL20C.glUniform4f(loc[4], (float) (cp.x - ShoreMap.originX), (float) (cp.z - ShoreMap.originZ), ShoreMap.SIZE, on ? 1f : 0f);
            }
            if (loc[6] >= 0) skyUniforms(loc[6], loc[7], loc[8]);
            if (loc[12] >= 0) underUniform(loc[12]);
            if (loc[13] >= 0) seaUniforms(loc[13], loc[14]);
            if (loc[25] >= 0) wetUniform(loc[25]);
            if (loc[26] >= 0 && loc[27] >= 0) {   // where the rain can reach
                var mc = Minecraft.getInstance(); var cp = mc.gameRenderer.getMainCamera().getPosition();
                boolean live = wet > 0.01f || RainMap.hasRetained || RainMap.hasWater;
                if (mc.level != null && live) RainMap.update(mc.level, cp.x, cp.z, wet, dryNow);
                boolean on = RainMap.ready && live;
                GL20C.glUniform4f(loc[27], (float) (cp.x - RainMap.originX), (float) (cp.z - RainMap.originZ), (float) cp.y, on ? 1f + 0.999f * RainMap.blend(mc.level.getGameTime(), mc.getTimer().getGameTimeDeltaPartialTick(false)) : 0f);   // w: 1 plus how far between updates
                if (on) { GlUpload.bind(GL13C.GL_TEXTURE0 + RainMap.UNIT, RainMap.texture()); GL20C.glUniform1i(loc[26], RainMap.UNIT); com.mojang.blaze3d.platform.GlStateManager._activeTexture(GL13C.GL_TEXTURE0); }
            }
            if (loc[23] >= 0 && loc[24] >= 0) {   // the hulls, for cutting the water out of them
                int n = PrismApi.waterEvents().shipsHandledElsewhere() ? 0 : PrismApi.waterEvents().hulls(hullBuf, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());   // Create: Deep Seas hides the sea in its hulls itself and draws its own flooding
                GL20C.glUniform4fv(loc[23], hullBuf); GL20C.glUniform4f(loc[24], n, IlluminateConfig.get(IlluminateConfig.REFLECTIONS) ? 1f : 0f, IlluminateConfig.get(IlluminateConfig.METALS) ? 1f : 0f, IlluminateConfig.get(IlluminateConfig.WET) ? 1f : 0f);   // y, z, w: reflections, shining metal, wet surfaces on
            }
            if (loc[21] >= 0 && loc[22] >= 0) {   // ships near the camera: the water keeps down around them
                int n = PrismApi.waterEvents().shipsHandledElsewhere() ? 0 : PrismApi.waterEvents().shipBoxes(shipBuf, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());   // Create: Deep Seas keeps the sea out of its ships itself; lowering the water near them here would only leave gaps
                GL20C.glUniform4fv(loc[21], shipBuf); GL20C.glUniform4f(loc[22], n, 0, 0, 0);
            }
            if (loc[19] >= 0 && loc[20] >= 0) {   // where the camera was when the copy of the scene was taken (for the metals)
                var cp = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                GL20C.glUniformMatrix4fv(loc[19], false, SceneCopy.vp);
                GL20C.glUniform4f(loc[20], (float) (cp.x - SceneCopy.camX), (float) (cp.y - SceneCopy.camY), (float) (cp.z - SceneCopy.camZ), SceneCopy.haveVp && SceneCopy.ready ? 1f : 0f);
            }
            if (loc[17] >= 0 && loc[18] >= 0) {   // the wakes and splashes
                int n = PrismApi.waterEvents().ripples(ripBuf, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
                GL20C.glUniform4fv(loc[17], ripBuf); GL20C.glUniform4f(loc[18], n, 0, 0, 0);
            }
            if (loc[30] >= 0 && loc[31] >= 0) {   // water easing between levels
                var cp = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                boolean on = FlowMap.live && FlowMap.texture() != 0 && IlluminateConfig.get(IlluminateConfig.SMOOTH_WATER);
                GL20C.glUniform4f(loc[31], (float) (cp.x - FlowMap.originX), (float) (cp.z - FlowMap.originZ), 1f / FlowMap.SIZE, on ? 1f : 0f);
                if (on) { GlUpload.bind(GL13C.GL_TEXTURE0 + FlowMap.UNIT, FlowMap.texture()); GL20C.glUniform1i(loc[30], FlowMap.UNIT); com.mojang.blaze3d.platform.GlStateManager._activeTexture(GL13C.GL_TEXTURE0); }
            }
            if (loc[32] >= 0 && loc[33] >= 0) {   // the snow surface
                var smc = Minecraft.getInstance(); var scp = smc.gameRenderer.getMainCamera().getPosition();
                boolean son = IlluminateConfig.get(IlluminateConfig.SMOOTH_SNOW);
                if (son && smc.level != null) SnowMap.update(smc.level, scp.x, scp.z);
                son = son && SnowMap.ready && SnowMap.any && SnowMap.texture() != 0;
                GL20C.glUniform4f(loc[33], (float) (scp.x - SnowMap.originX), (float) (scp.z - SnowMap.originZ), 1f / SnowMap.SIZE, dev.phi.alterra.illuminate.snow.SnowSurface.active() ? 2f : son ? 1f : 0f);   // 2: the snow surface draws the snow near the camera, the layers' own faces are dropped
                if (son) { GlUpload.bind(GL13C.GL_TEXTURE0 + SnowMap.UNIT, SnowMap.texture()); GL20C.glUniform1i(loc[32], SnowMap.UNIT); com.mojang.blaze3d.platform.GlStateManager._activeTexture(GL13C.GL_TEXTURE0); }
            }
            if (loc[36] >= 0 && loc[37] >= 0) {   // the grid water around the camera (rain, swimmers, boats, splashes)
                var wcp = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                dev.phi.alterra.illuminate.maps.WaveMap.upload(wcp.x, wcp.z);
                float[] wi = dev.phi.alterra.illuminate.maps.WaveMap.INFO;
                GL20C.glUniform4f(loc[37], wi[0], wi[1], wi[2], dev.phi.alterra.illuminate.maps.WaveMap.live ? 1f : 0f);
                if (dev.phi.alterra.illuminate.maps.WaveMap.live) { dev.phi.alterra.illuminate.maps.WaveMap.bind(); GL20C.glUniform1i(loc[36], dev.phi.alterra.illuminate.maps.WaveMap.UNIT); }
            }
            if (loc[38] >= 0 && loc[39] >= 0) {   // a whirlpool's grid water
                var whc = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                dev.phi.alterra.illuminate.maps.WhirlMap.upload(whc.x, whc.z);
                float[] wh = dev.phi.alterra.illuminate.maps.WhirlMap.INFO;
                GL20C.glUniform4f(loc[39], wh[0], wh[1], wh[2], wh[3]);
                if (wh[3] > 0.5) { dev.phi.alterra.illuminate.maps.WhirlMap.bind(); GL20C.glUniform1i(loc[38], dev.phi.alterra.illuminate.maps.WhirlMap.UNIT); }
            }
            if (loc[34] >= 0 && loc[35] >= 0) {   // whirlpools near the camera
                var vc = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition(); int vn = 0; java.util.Arrays.fill(VORTEX, 0f);   // (the old whirlpool dip is off: whirlpools are grid water, see WhirlMap)
                GL20C.glUniform4fv(loc[34], VORTEX); GL20C.glUniform4f(loc[35], vn, 0f, 0f, 0f);
            }
            if (loc[28] >= 0 && loc[29] >= 0) {   // the foam
                int n = PrismApi.waterEvents().foam(foamBuf, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
                GL20C.glUniform4fv(loc[28], foamBuf); GL20C.glUniform4f(loc[29], n, 0, 0, 0);
            }
            if (loc[16] >= 0) {   // how open the sea is around here
                var cp = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                boolean on = SeaState.ready && SeaState.texture() != 0;
                GL20C.glUniform4f(loc[16], (float) (cp.x - SeaState.originX), (float) (cp.z - SeaState.originZ), on ? 1f / (SeaState.SIZE * SeaState.CELL) : 0f, lift() + 0.8f * surge);
                if (on && loc[15] >= 0) { GlUpload.bind(GL13C.GL_TEXTURE0 + SeaState.UNIT, SeaState.texture()); GL20C.glUniform1i(loc[15], SeaState.UNIT); com.mojang.blaze3d.platform.GlStateManager._activeTexture(GL13C.GL_TEXTURE0); }
            }
            if (loc[11] >= 0) {   // the copy of the scene, when there is one
                boolean on = SceneCopy.ready && IlluminateConfig.get(IlluminateConfig.WATER_LOOK);
                GL20C.glUniform4f(loc[11], SceneCopy.width(), SceneCopy.height(), on ? 1f : 0f, IlluminateConfig.get(IlluminateConfig.CAUSTICS) ? IlluminateConfig.get(IlluminateConfig.CAUSTIC_STRENGTH).floatValue() : 0f);
                if (on && loc[10] >= 0 && loc[9] >= 0) {
                    GlUpload.bind(GL13C.GL_TEXTURE0 + SceneCopy.DEPTH_UNIT, SceneCopy.depth()); GL20C.glUniform1i(loc[10], SceneCopy.DEPTH_UNIT);
                    GlUpload.bind(GL13C.GL_TEXTURE0 + SceneCopy.COLOR_UNIT, SceneCopy.color()); GL20C.glUniform1i(loc[9], SceneCopy.COLOR_UNIT);
                    com.mojang.blaze3d.platform.GlStateManager._activeTexture(GL13C.GL_TEXTURE0);
                }
            }
            if (loc[5] >= 0 && ShoreMap.texture() != 0) {
                GlUpload.bind(GL13C.GL_TEXTURE0 + ShoreMap.UNIT, ShoreMap.texture());
                GL20C.glUniform1i(loc[5], ShoreMap.UNIT); com.mojang.blaze3d.platform.GlStateManager._activeTexture(GL13C.GL_TEXTURE0);
            }
            float[] v = params();
            if (v == null) { GL20C.glUniform4f(loc[0], 0, 0, 0, 0); if (loc[1] >= 0) GL20C.glUniform4f(loc[1], 0, 0, 0, 0); return; }
            GL20C.glUniform4f(loc[0], v[0], v[1], v[2], 1f);
            if (loc[1] >= 0) GL20C.glUniform4f(loc[1], v[3], v[4], 0f, 0f);
            if (loc[3] >= 0) {
                var cp = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                GL20C.glUniform4f(loc[3], (float) (((cp.x % 1024) + 1024) % 1024), (float) cp.y, (float) (((cp.z % 1024) + 1024) % 1024), IlluminateConfig.get(IlluminateConfig.WATER_WAVES) && IlluminateConfig.get(IlluminateConfig.WATER_LOOK) ? 0.035f * (1f + 0.3f * lift() + 0.5f * surge) : 0f);   // the swell is a little higher in wind, rain and storms
            }
            if (loc[2] >= 0) {   // the mask goes on its own texture unit (Sodium uses 0 and 2, Deep Seas 8 and 9)
                int tex = mask();
                GlUpload.bind(GL13C.GL_TEXTURE0 + MASK_UNIT, tex);
                GL20C.glUniform1i(loc[2], MASK_UNIT); com.mojang.blaze3d.platform.GlStateManager._activeTexture(GL13C.GL_TEXTURE0);
            }
        } catch (Throwable t) { LOCATIONS.put(program, java.util.stream.IntStream.generate(() -> -1).limit(48).toArray()); LOG.warn("Alterra Illuminate: the block shader settings failed, the look is off for this shader program", t); }
    }

    /** The sky the water mirrors: the sun's or moon's direction and how strongly it glints, the colour overhead and at the horizon. Off (w = 0) when the look is switched off or the player is under water. */
    private static void skyUniforms(int sky, int zenith, int horizon) {
        var mc = Minecraft.getInstance(); var level = mc.level; var cam = mc.gameRenderer.getMainCamera();
        if (level == null || !IlluminateConfig.get(IlluminateConfig.WATER_LOOK) || cam.getEntity() == null || !level.dimensionType().hasSkyLight()) { GL20C.glUniform4f(sky, 0, 1, 0, 0); return; }
        float pt = mc.getTimer().getGameTimeDeltaPartialTick(false), t = level.getTimeOfDay(pt) * (float) (Math.PI * 2), rain = Sky.rain(level);
        float sx = -(float) Math.sin(t), sy = (float) Math.cos(t);   // the sun climbs the east and sets in the west (x)
        boolean sun = sy > -0.05f; if (!sun) { sx = -sx; sy = -sy; }   // by night the moon is opposite
        float len = (float) Math.sqrt(sx * sx + sy * sy + 0.0001f), strength = (sun ? 1f : 0.28f) * (1f - 0.85f * rain) * Math.min(1f, Math.max(0f, (sy / len + 0.05f) * 6f));
        GL20C.glUniform4f(sky, sx / len, sy / len, 0f, strength + 0.0001f);
        var sc = level.getSkyColor(cam.getPosition(), pt); float day = Math.max(0.1f, Math.min(1f, sun ? sy / len * 1.4f + 0.3f : 0.1f));
        GL20C.glUniform4f(zenith, (float) sc.x, (float) sc.y, (float) sc.z, rain);
        float[] hzc = SkyGlow.horizon(level, sc, day, pt), gl = SkyGlow.glow(level, pt); GL20C.glUniform4f(horizon, hzc[0], hzc[1], hzc[2], (sun ? 1f : -1f) * Math.max(0.0001f, gl[3] * 0.9f));   // w: the sunset glow's strength, negative when the direction in u_AlterraSky is the moon's
    }

    /** Whether the water shader is in use (Sodium's block shaders were patched): then the rain rings on water are drawn by it. */
    public static boolean shaderWater() { return sodiumPatched != null && sodiumFragment != null && IlluminateConfig.get(IlluminateConfig.WATER_LOOK); }
    static float liftNow() { return lift() + 0.8f * surge; }

    /** The height the shader gives the swell at a place with this much open sea (blocks): the same formula as in the vertex shader, so boats can ride the surface. */
    static float swellAmp(float open) { float l = lift(); return 0.035f * (1f + 0.3f * l + 0.5f * surge) * (1f + 5f * open * (0.4f + l + 0.8f * surge)); }

    /** How much the weather lifts the sea, 0 to 1: the strongest of heavy storm, thunder and rain, plus a little for wind. */
    private static float lift() {
        var mc = Minecraft.getInstance(); var level = mc.level; if (level == null) return 0f;
        float pt = mc.getTimer().getGameTimeDeltaPartialTick(false);
        var cp = mc.gameRenderer.getMainCamera().getPosition();
        float storm = Math.max(PrismApi.storm().heavy(level), Math.max(0.7f * level.getThunderLevel(pt), 0.35f * Sky.rain(level)));
        return Math.min(1f, storm + 0.25f * (float) Math.min(PrismApi.wind().at(level, cp.x, cp.z).length(), 1.5));
    }

    /** The weather the sea follows: the wind where the camera is (Alterra Weather's wind, which already follows rain and storms), rain, thunder and the heavy storms. The time is the same clock the foliage sway uses. */
    private static final float[] foamBuf = new float[WaterEvents.FOAM_MAX * 4], ripBuf = new float[WaterEvents.RIPPLES_MAX * 4], shipBuf = new float[WaterEvents.SHIP_BOXES_MAX * 8], hullBuf = new float[WaterEvents.HULLS_MAX * 16];
    private static float surge, stormRef = -1f; private static long lastNano, refNano; private static double seaTime; private static boolean changing;
    /** Works out the weather surge and returns the shared clock. */
    private static float advance(float storm, float lift) {
        long now = System.nanoTime(); float dt = lastNano == 0 ? 0f : Math.min(0.1f, (now - lastNano) / 1.0e9f); lastNano = now;
        // the weather has just started to change: one quick surge, which then dies away within about a second (not for as long as the weather keeps changing)
        if (stormRef < 0f) { stormRef = storm; refNano = now; }
        if (now - refNano >= 250_000_000L) {
            float rate = Math.abs(storm - stormRef) / ((now - refNano) / 1.0e9f); stormRef = storm; refNano = now;
            boolean moving = rate > 0.05f;
            if (moving && !changing) surge = 1f;
            changing = moving;
        }
        surge *= (float) Math.exp(-dt / 0.8f);
        return clock();
    }
    /** The one clock every animation runs on (waves, sway, boats riding the swell). */
    static float clock() { return PrismApi.clock().seconds(); }   // one clock for every animation (see there)
    private static void seaUniforms(int sea, int sea2) {
        var mc = Minecraft.getInstance(); var level = mc.level; var cam = mc.gameRenderer.getMainCamera();
        if (level == null) { GL20C.glUniform4f(sea, 0, 0, 0, 0); GL20C.glUniform4f(sea2, 0, 0, 0, 0); return; }
        float pt = mc.getTimer().getGameTimeDeltaPartialTick(false), rain = Sky.rain(level), thunder = level.getThunderLevel(pt), heavy = PrismApi.storm().heavy(level);
        var w = PrismApi.wind().at(level, cam.getPosition().x, cam.getPosition().z);
        float storm = Math.min(1f, Math.max(heavy, Math.max(0.7f * thunder, 0.35f * rain)));
        float time = advance(storm, Math.min(1f, storm + 0.25f * (float) Math.min(w.length(), 1.5)));
        GL20C.glUniform4f(sea, time, (float) w.x, (float) w.z, storm);
        GL20C.glUniform4f(sea2, rain, thunder, heavy, surge);
    }

    private static float wet, dryNow; private static long wetNano;
    /** How wet the world is: it rises while it rains where the camera is, and dries slowly afterwards (about two minutes), so puddles form during a storm and shrink after it. */
    private static void wetUniform(int loc) {
        var mc = Minecraft.getInstance(); var level = mc.level; var cam = mc.gameRenderer.getMainCamera();
        long now = System.nanoTime(); float dt = wetNano == 0 ? 0f : Math.min(0.2f, (now - wetNano) / 1.0e9f); wetNano = now;
        if (level == null) { GL20C.glUniform4f(loc, 0, 0, 0, 0); return; }
        float rain = Sky.rain(level);
        var pos = cam.getBlockPosition();
        if (level.getGameTime() % 40 == 0 || dryNow == 0f) dryNow = PrismApi.storm().drying(level, pos);
        if (level.getBiome(pos).value().getPrecipitationAt(pos) != net.minecraft.world.level.biome.Biome.Precipitation.RAIN) rain = 0f;   // it only wets the ground where it rains (not snow, not the desert)
        float serverWet = PrismApi.storm().serverWet(); boolean fromServer = !Float.isNaN(serverWet);   // the server's number if it has sent one lately, else this game's own estimate
        if (fromServer) wet += (serverWet - wet) * Math.min(1f, dt * 2f);
        else wet = Math.max(0f, Math.min(1f, wet + (rain > 0.05f ? rain * dt / 25f : -dt / 120f * dryNow)));
        GL20C.glUniform4f(loc, IlluminateConfig.get(IlluminateConfig.WATER_LOOK) ? wet : 0f, rain, 0f, 0f);
    }

    /** Whether the camera is under water, and where the surface above it is (relative to the camera): the shader then draws the surface as a mirror and window and lights the ground with caustics. */
    /** How far the water surface is above the camera when it is under water (0 otherwise): the scene pass draws the sun shafts from it. */
    public static volatile float surfaceAbove;
    private static void underUniform(int loc) {
        var mc = Minecraft.getInstance(); var level = mc.level; var cam = mc.gameRenderer.getMainCamera();
        if (level == null || cam.getFluidInCamera() != net.minecraft.world.level.material.FogType.WATER || !IlluminateConfig.get(IlluminateConfig.WATER_LOOK)) { GL20C.glUniform4f(loc, 0f, 0f, 0f, 0f); surfaceAbove = 0f; return; }
        var p = new net.minecraft.core.BlockPos.MutableBlockPos(cam.getBlockPosition().getX(), cam.getBlockPosition().getY(), cam.getBlockPosition().getZ());
        for (int i = 0; i < 80 && level.getFluidState(p.above()).is(net.minecraft.tags.FluidTags.WATER); i++) p.move(0, 1, 0);
        float surface = (float) (p.getY() + level.getFluidState(p).getHeight(level, p));
        surfaceAbove = Math.max(0.2f, surface - (float) cam.getPosition().y);
        GL20C.glUniform4f(loc, 1f, surface - (float) cam.getPosition().y, 0f, 0f);
    }

    /** Vanilla's leaf shader: set before the world is drawn. */
    @SubscribeEvent
    public static void frame(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        SceneCopy.pending = false;
        try {
            var shader = net.minecraft.client.renderer.GameRenderer.getRendertypeCutoutMippedShader();
            if (shader == null) return;
            float[] v = params();
            shader.setSampler("AlterraMask", mask());
            shader.safeGetUniform("AlterraWave").set(v == null ? 0f : v[0], v == null ? 0f : v[1], v == null ? 0f : v[2], v == null ? 0f : v[3]);
        } catch (Throwable t) { /* no sway rather than a crash */ }
    }
}
