package dev.phi.alterra.weather;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.phi.alterra.weather.Storms.Kind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.joml.Matrix4f;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Client side of the weather. Each storm kind has a strength from 0 to 1 (its timer, only inside the biomes of its tag, eased so nothing pops).
 * It drives the particles, fog, screen haze, aurora and wind sound.
 */
@EventBusSubscriber(modid = AlterraWeather.ID, value = Dist.CLIENT)
public final class WeatherEffects {
    private WeatherEffects() {}

    private static final Map<Kind, TagKey<Biome>> TAGS = new EnumMap<>(Kind.class);
    static { for (Kind k : Kind.values()) TAGS.put(k, TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(AlterraWeather.ID, "weather/" + k.tag))); }

    // strengths now and one tick ago (for smooth rendering)
    private static final float[] now = new float[Kind.values().length], before = new float[Kind.values().length];
    private static float outdoors, outdoorsBefore, gust = 1f;
    private static double wind;                                    // direction of the strongest storm's wind
    private static final Map<Kind, StormSound> SOUNDS = new EnumMap<>(Kind.class);
    /** storms turned on by hand with /alterraweather */
    static final Map<Kind, Boolean> FORCED = new EnumMap<>(Kind.class);
    /** if an effect throws, they all turn off until restart instead of crashing */
    private static volatile boolean broken;
    private static void fail(Throwable t) {
        if (broken) return;
        broken = true; java.util.Arrays.fill(now, 0f); java.util.Arrays.fill(before, 0f);
        SOUNDS.values().forEach(StormSound::end); SOUNDS.clear();
        org.slf4j.LoggerFactory.getLogger("Alterra Weather").error("A weather effect failed; the weather effects are switched off until the game restarts", t);
    }

    // ---------------------------------------------------------------- compat
    private static Boolean allClash, fogClash;
    private static boolean loaded(String... ids) { for (String id : ids) if (ModList.get().isLoaded(id)) return true; return false; }
    /** is it switched on, and not left to another mod */
    private static boolean allowed(boolean switchedOn, boolean fog) {
        if (!switchedOn) return false;
        if (!WeatherConfig.get(WeatherConfig.STEP_ASIDE)) return true;
        if (allClash == null) {
            allClash = loaded("weather2", "pmweather", "weatherstorms", "tropicraft_weather");              // whole weather systems
            fogClash = loaded("distanthorizons", "fogoverrides", "betterfog", "voxy", "fogtweaks");            // they take over the fog distance
        }
        return !allClash && !(fog && fogClash);
    }
    private static boolean on(Kind k) {
        return switch (k) {
            case SANDSTORM -> allowed(WeatherConfig.get(WeatherConfig.SANDSTORMS), true);
            case BLIZZARD -> allowed(WeatherConfig.get(WeatherConfig.BLIZZARDS), true);
            case FOG -> allowed(WeatherConfig.get(WeatherConfig.FOG_BANKS), true);
            case PETALS -> allowed(WeatherConfig.get(WeatherConfig.PETAL_STORMS), false);
            case POLLEN -> allowed(WeatherConfig.get(WeatherConfig.POLLEN), false);
            case ASH -> allowed(WeatherConfig.get(WeatherConfig.ASHFALL), true);
            case AURORA -> allowed(WeatherConfig.get(WeatherConfig.AURORA), false);
        };
    }
    private static float s(Kind k) { return now[k.ordinal()]; }
    private static float ease(float cur, float target) { return cur + Mth.clamp(target - cur, -0.012f, 0.012f); }

    // ---------------------------------------------------------------- every tick
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        if (broken) return;
        try { tick(); } catch (Throwable t) { fail(t); }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        System.arraycopy(now, 0, before, 0, now.length); outdoorsBefore = outdoors;
        var level = mc.level; var player = mc.player;
        if (level == null || player == null) { java.util.Arrays.fill(now, 0f); SOUNDS.values().forEach(StormSound::end); SOUNDS.clear(); return; }
        if (mc.isPaused()) return;

        long time = level.getGameTime();
        int rx = Storms.region(player.getX()), rz = Storms.region(player.getZ());
        var biome = level.getBiome(player.blockPosition());
        float rain = level.getRainLevel(1f), thunder = level.getThunderLevel(1f);
        long day = level.getDayTime() % 24000L;
        float dawn = day > 22000 ? (day - 22000) / 1000f : day < 2500 ? 1f - Math.max(0, day - 1000) / 1500f : 0f;
        float night = day > 13000 && day < 23000 ? Mth.clamp(Math.min(day - 13000, 23000 - day) / 1000f, 0f, 1f) : 0f;
        float sun = day < 12000 ? Mth.sin(day / 12000f * Mth.PI) : 0f;
        outdoors = ease(outdoors, level.canSeeSky(player.blockPosition().above()) ? 1f : 0f);
        gust = 0.8f + 0.2f * Mth.sin(time / 37f) * Mth.sin(time / 91f + 1.7f) + 0.1f * Mth.sin(time / 11f);

        double strongest = 0;
        for (Kind k : Kind.values()) {
            float target = 0f;
            if (FORCED.containsKey(k)) target = 1f;
            else if (on(k) && biome.is(TAGS.get(k))) {
                target = Storms.level(k, time, rx, rz);
                if (!FORCED.containsKey(k)) switch (k) {
                    case FOG -> target = Math.max(target, dawn * 0.55f);
                    case POLLEN -> target *= sun * (1f - rain);
                    case AURORA -> target *= night * (1f - rain);
                    case PETALS -> target = Math.max(target, thunder * 0.6f);
                    default -> {}
                }
            }
            now[k.ordinal()] = ease(now[k.ordinal()], target);
            if (k != Kind.AURORA && k != Kind.POLLEN && now[k.ordinal()] > strongest) { strongest = now[k.ordinal()]; wind = Storms.wind(k, time, rx, rz); }
        }

        spawn(mc, level, player, wind);
        sounds(mc.getSoundManager());
    }

    // ---------------------------------------------------------------- particles
    private static ColorParticleOption color(ParticleType<ColorParticleOption> t, int rgb) { return ColorParticleOption.create(t, 0xFF000000 | rgb); }

    @SuppressWarnings("unchecked")
    private static ParticleType<ColorParticleOption> petal() {
        return (ParticleType<ColorParticleOption>) BuiltInRegistries.PARTICLE_TYPE.getOptional(ResourceLocation.fromNamespaceAndPath("alterra", "petal")).orElse(null);
    }

    private static void spawn(Minecraft mc, net.minecraft.client.multiplayer.ClientLevel level, net.minecraft.client.player.LocalPlayer p, double windDir) {
        float quality = mc.options.particles().get() == ParticleStatus.ALL ? 1f : mc.options.particles().get() == ParticleStatus.DECREASED ? 0.5f : 0.15f;
        float amount = quality * WeatherConfig.get(WeatherConfig.INTENSITY).floatValue() * gust;
        var r = level.random;
        double wx = Math.cos(windDir), wz = Math.sin(windDir);
        // a point upwind of the player, just above the ground so nothing spawns inside terrain
        java.util.function.BiFunction<Double, Double, double[]> upwind = (dist, spread) -> {
            double along = dist, across = (r.nextDouble() - 0.5) * 2 * spread;
            double x = p.getX() - wx * along - wz * across, z = p.getZ() - wz * along + wx * across;
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            return new double[] {x, Mth.clamp(ground, p.getY() - 24, p.getY() + 24), z};
        };
        float out = Math.max(0.25f, outdoors);

        float sand = s(Kind.SANDSTORM);
        if (sand > 0.03f) {
            int n = Math.round(sand * 40 * amount * out);
            for (int i = 0; i < n; i++) {
                var o = upwind.apply(5 + r.nextDouble() * 26, 26.0); double sp = 0.9 + r.nextDouble() * 0.6;
                level.addParticle(color(AlterraWeather.DUST.get(), r.nextInt(3) == 0 ? 0xC8A66E : 0xE4CB92), o[0], o[1] + r.nextDouble() * r.nextDouble() * 8, o[2], wx * sp, -0.01, wz * sp);
            }
            int c = Math.round(sand * 2.2f * amount * out + (r.nextFloat() < sand * 2.2f * amount * out % 1 ? 1 : 0));
            for (int i = 0; i < c; i++) {
                var o = upwind.apply(9 + r.nextDouble() * 22, 24.0);
                level.addParticle(color(AlterraWeather.PUFF.get(), 0xD2B27A), o[0], o[1] + r.nextDouble() * 5, o[2], wx * 0.55, 0.0, wz * 0.55);
            }
        }
        float snow = s(Kind.BLIZZARD);
        if (snow > 0.03f) {
            int n = Math.round(snow * 55 * amount * out);
            for (int i = 0; i < n; i++) {
                var o = upwind.apply(4 + r.nextDouble() * 24, 24.0); double sp = 1.0 + r.nextDouble() * 0.7;
                level.addParticle(color(AlterraWeather.DUST.get(), 0xF4F8FC), o[0], o[1] + r.nextDouble() * 12, o[2], wx * sp, -0.07 - r.nextDouble() * 0.05, wz * sp);
            }
            int c = Math.round(snow * 2.4f * amount * out + (r.nextFloat() < snow * 2.4f * amount * out % 1 ? 1 : 0));
            for (int i = 0; i < c; i++) {
                var o = upwind.apply(8 + r.nextDouble() * 20, 22.0);
                level.addParticle(color(AlterraWeather.PUFF.get(), 0xE6EEF6), o[0], o[1] + r.nextDouble() * 9, o[2], wx * 0.7, -0.02, wz * 0.7);
            }
        }
        float fog = s(Kind.FOG);
        if (fog > 0.03f && r.nextFloat() < fog * 1.4f * amount * out) {   // low mist drifting through, slowly
            double a = r.nextDouble() * Mth.TWO_PI, d = 3 + r.nextDouble() * 22;
            double x = p.getX() + Math.cos(a) * d, z = p.getZ() + Math.sin(a) * d;
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            if (Math.abs(ground - p.getY()) < 20)
                level.addParticle(color(AlterraWeather.PUFF.get(), 0xF3F6F9), x, ground + r.nextDouble() * 2.5, z, wx * 0.03, 0.0, wz * 0.03);
        }
        float petals = s(Kind.PETALS); var petalType = petals > 0.03f ? petal() : null;
        if (petalType != null) {
            int n = Math.round(petals * 12 * amount * out);
            int[] pinks = {0xF4B6D2, 0xF9D0E2, 0xFFFFFF, 0xEFA3C4};
            for (int i = 0; i < n; i++) {
                var o = upwind.apply(4 + r.nextDouble() * 18, 20.0); double sp = 0.28 + r.nextDouble() * 0.3;
                level.addParticle(color(petalType, pinks[r.nextInt(pinks.length)]), o[0], o[1] + 1 + r.nextDouble() * 9, o[2], wx * sp, -0.01, wz * sp);
            }
        }
        float pollen = s(Kind.POLLEN);
        if (pollen > 0.03f) {
            int n = Math.round(pollen * 4 * amount * out);
            for (int i = 0; i < n; i++) {
                double a = r.nextDouble() * Mth.TWO_PI, d = 2 + r.nextDouble() * 16;
                double x = p.getX() + Math.cos(a) * d, z = p.getZ() + Math.sin(a) * d;
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
                if (Math.abs(ground - p.getY()) < 14)
                    level.addParticle(color(AlterraWeather.MOTE.get(), r.nextInt(4) == 0 ? 0xFFF4B0 : 0xF3D95C), x, ground + 0.4 + r.nextDouble() * 4, z, wx * 0.02, 0.006, wz * 0.02);
            }
        }
        float ash = s(Kind.ASH);
        if (ash > 0.03f) {
            int n = Math.round(ash * 9 * amount * out);
            for (int i = 0; i < n; i++) {
                double x = p.getX() + (r.nextDouble() - 0.5) * 36, z = p.getZ() + (r.nextDouble() - 0.5) * 36, y = p.getY() + 3 + r.nextDouble() * 14;
                if (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z)) <= y)
                    level.addParticle(color(AlterraWeather.MOTE.get(), r.nextInt(5) == 0 ? 0x4C4846 : 0xA6A09A), x, y, z, wx * 0.03, -0.03 - r.nextDouble() * 0.02, wz * 0.03);
            }
            if (ash > 0.4f && r.nextFloat() < ash * 0.5f * amount * out) {   // and now and then a big drifting cloud of it
                var o = upwind.apply(12 + r.nextDouble() * 20, 24.0);
                level.addParticle(color(AlterraWeather.PUFF.get(), 0x7E7A76), o[0], o[1] + r.nextDouble() * 8, o[2], wx * 0.3, 0.0, wz * 0.3);
            }
        }
    }

    // ---------------------------------------------------------------- wind
    private static void sounds(SoundManager sm) {
        sound(sm, Kind.SANDSTORM, AlterraWeather.WIND_DUST.get(), 1.0f);
        sound(sm, Kind.BLIZZARD, AlterraWeather.WIND_SNOW.get(), 1.0f);
        sound(sm, Kind.FOG, AlterraWeather.WIND_CALM.get(), 0.30f);
        sound(sm, Kind.PETALS, AlterraWeather.WIND_CALM.get(), 0.65f);
        sound(sm, Kind.ASH, AlterraWeather.WIND_CALM.get(), 0.45f);
    }
    private static void sound(SoundManager sm, Kind k, SoundEvent ev, float max) {
        float v = s(k) * max * (0.3f + 0.7f * outdoors);
        StormSound cur = SOUNDS.get(k);
        if (cur != null && cur.isStopped()) { SOUNDS.remove(k); cur = null; }
        if (cur == null && v > 0.02f) { cur = new StormSound(ev); SOUNDS.put(k, cur); sm.play(cur); }
        if (cur != null) { cur.target(v); if (v < 0.005f && cur.faded()) { cur.end(); SOUNDS.remove(k); } }
    }

    // ---------------------------------------------------------------- fog
    @SubscribeEvent
    public static void fogDistance(ViewportEvent.RenderFog e) {
        if (broken) return;
        try { do_fogDistance(e); } catch (Throwable t) { fail(t); }
    }
    private static void do_fogDistance(ViewportEvent.RenderFog e) {
        if (e.getType() != FogType.NONE || e.getMode() != FogRenderer.FogMode.FOG_TERRAIN) return;
        float pt = (float) e.getPartialTick(), o = Mth.lerp(pt, outdoorsBefore, outdoors);
        float far = e.getFarPlaneDistance(), target = far;
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        for (Kind k : Kind.values()) {
            float a = Mth.lerp(pt, before[k.ordinal()], now[k.ordinal()]) * o;
            if (a < 0.01f) continue;
            float dense = switch (k) {   // fog distance at full strength
                case SANDSTORM -> 15f * (0.85f + 0.3f * gust);
                case BLIZZARD -> 12f * (0.85f + 0.3f * gust);
                case FOG -> {   // banks: thicker and thinner patches, slowly rolling past
                    long gt = Minecraft.getInstance().level.getGameTime();
                    float patch = 0.5f + 0.5f * Mth.sin((float) (cam.x * 0.012 + gt * 0.0015)) * Mth.sin((float) (cam.z * 0.015 - gt * 0.001));
                    yield 24f * (0.65f + 0.45f * (1f - patch));
                }
                case ASH -> 56f;
                default -> far;
            };
            if (dense < far && (k == Kind.SANDSTORM || k == Kind.BLIZZARD || k == Kind.FOG || k == Kind.ASH)) target = Math.min(target, Mth.lerp(a, far, dense));
        }
        if (target >= far - 0.01f) return;
        e.setFarPlaneDistance(target);
        e.setNearPlaneDistance(Math.min(e.getNearPlaneDistance(), target * 0.04f));
        e.setFogShape(com.mojang.blaze3d.shaders.FogShape.SPHERE);
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void fogColour(ViewportEvent.ComputeFogColor e) {
        if (broken) return;
        try { do_fogColour(e); } catch (Throwable t) { fail(t); }
    }
    private static void do_fogColour(ViewportEvent.ComputeFogColor e) {
        float pt = (float) e.getPartialTick(), o = Mth.lerp(pt, outdoorsBefore, outdoors);
        for (Kind k : Kind.values()) {
            float a = Mth.lerp(pt, before[k.ordinal()], now[k.ordinal()]) * o;
            if (a < 0.01f) continue;
            float[] c = switch (k) { case SANDSTORM -> new float[] {0.80f, 0.64f, 0.42f}; case BLIZZARD -> new float[] {0.88f, 0.92f, 0.96f};
                case FOG -> new float[] {0.86f, 0.89f, 0.92f}; case ASH -> new float[] {0.42f, 0.40f, 0.39f}; default -> null; };
            if (c == null) continue;
            float m = Math.min(1f, a * 1.2f);
            e.setRed(Mth.lerp(m, e.getRed(), c[0])); e.setGreen(Mth.lerp(m, e.getGreen(), c[1])); e.setBlue(Mth.lerp(m, e.getBlue(), c[2]));
        }
    }

    // ---------------------------------------------------------------- screen haze: a tint over everything, heavier at the edges
    @SubscribeEvent
    public static void haze(RenderGuiLayerEvent.Pre e) {
        if (broken) return;
        try { do_haze(e); } catch (Throwable t) { fail(t); }
    }
    private static void do_haze(RenderGuiLayerEvent.Pre e) {
        if (!e.getName().equals(VanillaGuiLayers.CAMERA_OVERLAYS)) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false), o = Mth.lerp(pt, outdoorsBefore, outdoors);
        GuiGraphics g = e.getGuiGraphics(); int w = g.guiWidth(), h = g.guiHeight();
        for (Kind k : new Kind[] {Kind.SANDSTORM, Kind.BLIZZARD, Kind.ASH, Kind.FOG}) {
            float a = Mth.lerp(pt, before[k.ordinal()], now[k.ordinal()]) * o;
            if (a < 0.02f) continue;
            int rgb; float flat, edge;
            switch (k) {
                case SANDSTORM -> { rgb = 0xC49A58; flat = 0.26f; edge = 0.22f; }
                case BLIZZARD -> { rgb = 0xE9F1F9; flat = 0.30f; edge = 0.26f; }
                case ASH -> { rgb = 0x5E5A57; flat = 0.14f; edge = 0.16f; }
                default -> { rgb = 0xE6ECF0; flat = 0.07f; edge = 0.10f; }
            }
            a *= (k == Kind.SANDSTORM || k == Kind.BLIZZARD) ? (0.85f + 0.15f * gust) : 1f;
            g.fill(0, 0, w, h, argb(a * flat, rgb));
            int band = Math.max(20, w / 7);
            for (int i = 0; i < band; i += 2) {   // darker towards the sides, in steps
                int al = argb(a * edge * (1f - (float) i / band) * (1f - (float) i / band), rgb);
                g.fill(i, 0, i + 2, h, al); g.fill(w - i - 2, 0, w - i, h, al);
            }
            g.fillGradient(0, 0, w, h / 5, argb(a * edge, rgb), argb(0f, rgb)); g.fillGradient(0, h - h / 5, w, h, argb(0f, rgb), argb(a * edge, rgb));
        }
    }
    private static int argb(float alpha, int rgb) { return (Mth.clamp((int) (alpha * 255), 0, 255) << 24) | (rgb & 0xFFFFFF); }

    // ---------------------------------------------------------------- aurora
    @SubscribeEvent
    public static void sky(RenderLevelStageEvent e) {
        if (broken) return;
        try { do_sky(e); } catch (Throwable t) { fail(t); }
    }
    private static void do_sky(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        float a = Mth.lerp(pt, before[Kind.AURORA.ordinal()], now[Kind.AURORA.ordinal()]);
        if (a < 0.01f) return;
        Matrix4f m = new Matrix4f(e.getModelViewMatrix());
        float t = (Minecraft.getInstance().level.getGameTime() + pt) / 20f;
        RenderSystem.enableBlend(); RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false); RenderSystem.disableCull(); RenderSystem.setShader(GameRenderer::getPositionColorShader);
        var buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int band = 0; band < 3; band++) {
            float dist = 90 + band * 26, base = 26 + band * 7, height = 46 + band * 9, phase = band * 1.7f;
            int seg = 96;
            for (int i = 0; i < seg; i++) {
                float u0 = (float) i / seg, u1 = (float) (i + 1) / seg;
                float x0 = (u0 - 0.5f) * 280, x1 = (u1 - 0.5f) * 280;
                float z0 = -dist + Mth.sin(u0 * 9f + t * 0.33f + phase) * 15 + Mth.sin(u0 * 23f - t * 0.2f) * 4, z1 = -dist + Mth.sin(u1 * 9f + t * 0.33f + phase) * 15 + Mth.sin(u1 * 23f - t * 0.2f) * 4;
                float ray0 = 0.45f + 0.55f * Mth.sin(u0 * 64f - t * 0.9f + phase) * Mth.sin(u0 * 17f + t * 0.4f), ray1 = 0.45f + 0.55f * Mth.sin(u1 * 64f - t * 0.9f + phase) * Mth.sin(u1 * 17f + t * 0.4f);
                float w0 = 0.5f + 0.5f * Mth.sin(u0 * 21f - t * 0.7f + phase), w1 = 0.5f + 0.5f * Mth.sin(u1 * 21f - t * 0.7f + phase);
                float edge0 = Mth.sin(u0 * Mth.PI), edge1 = Mth.sin(u1 * Mth.PI);
                float al0 = a * Math.max(0, ray0) * (0.4f + 0.6f * w0) * edge0 * 0.34f, al1 = a * Math.max(0, ray1) * (0.4f + 0.6f * w1) * edge1 * 0.34f;
                float hue = 0.5f + 0.5f * Mth.sin(t * 0.05f + band);   // slowly drifting between green and teal at the foot
                buf.addVertex(m, x0, base, z0).setColor(0.20f, 0.95f - 0.2f * hue, 0.45f + 0.3f * hue, al0);
                buf.addVertex(m, x1, base, z1).setColor(0.20f, 0.95f - 0.2f * hue, 0.45f + 0.3f * hue, al1);
                buf.addVertex(m, x1, base + height * (0.7f + 0.3f * ray1), z1).setColor(0.60f, 0.30f, 0.95f, 0f);
                buf.addVertex(m, x0, base + height * (0.7f + 0.3f * ray0), z0).setColor(0.60f, 0.30f, 0.95f, 0f);
            }
        }
        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.defaultBlendFunc(); RenderSystem.disableBlend();
    }
}
