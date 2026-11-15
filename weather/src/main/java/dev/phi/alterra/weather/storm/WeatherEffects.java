package dev.phi.alterra.weather.storm;

import dev.phi.aethryon.api.AethryonApi;
import dev.phi.alterra.weather.AlterraWeather;
import dev.phi.alterra.weather.WeatherHooks;
import dev.phi.alterra.weather.config.WeatherConfig;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.phi.alterra.weather.storm.Storms.Kind;
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
    private static final boolean[] rainRoll = new boolean[Kind.values().length];   // for this rain: does it turn into that biome storm?
    private static boolean wasRaining;
    /** For each storm and each of 16 directions around the player: how much of the way out (to about 120 blocks) is a biome where that storm is going on. */
    private static final float[][] cover = new float[Kind.values().length][16];
    private static double wind;                                    // direction of the strongest storm's wind
    private static final Map<Kind, StormSound> SOUNDS = new EnumMap<>(Kind.class);
    /** storms turned on by hand with /alterraweather */
    public static final Map<Kind, Boolean> FORCED = new EnumMap<>(Kind.class);
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
    private static final boolean PHOTO = System.getProperty("alterra.photoset") != null;
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
        if (level == null || player == null) { Storms.FORCED_UNTIL.clear(); HeavyStorm.reset(); java.util.Arrays.fill(now, 0f); SOUNDS.values().forEach(StormSound::end); SOUNDS.clear(); return; }
        if (mc.isPaused()) return;

        long time = level.getGameTime();
        HeavyStorm.update(level);
        int rx = Storms.region(player.getX()), rz = Storms.region(player.getZ());
        var biome = level.getBiome(player.blockPosition());
        float rain = level.getRainLevel(1f), thunder = level.getThunderLevel(1f);
        long day = level.getDayTime() % 24000L;
        float dawn = day > 22000 ? (day - 22000) / 1000f : day < 2500 ? 1f - Math.max(0, day - 1000) / 1500f : 0f;
        float night = day > 13000 && day < 23000 ? Mth.clamp(Math.min(day - 13000, 23000 - day) / 1000f, 0f, 1f) : 0f;
        float sun = day < 12000 ? Mth.sin(day / 12000f * Mth.PI) : 0f;
        outdoors = ease(outdoors, level.canSeeSky(player.blockPosition().above()) ? 1f : 0f);
        gust = 0.8f + 0.2f * Mth.sin(time / 37f) * Mth.sin(time / 91f + 1.7f) + 0.1f * Mth.sin(time / 11f);

        boolean rainNow = rain > 0.03f;
        if (rainNow && !wasRaining) {   // a new rain: for each kind of storm, does this one turn into it? (plain rain is realistic where it can fall; deserts have none, so they get the storm)
            long slot = time / 600L; float share = WeatherConfig.get(WeatherConfig.STORM_SHARE).floatValue();
            for (Kind k : Kind.values()) {
                float base = switch (k) { case SANDSTORM -> 0.6f; case BLIZZARD -> 0.55f; case PETALS -> 0.35f; case FOG -> 0.4f; case ASH -> 0.5f; default -> 0f; };
                float chance = biome.value().hasPrecipitation() ? base : 0.9f;
                rainRoll[k.ordinal()] = Storms.roll(k, slot, rx, rz) < chance * share;
            }
        }
        wasRaining = rainNow;
        double strongest = 0;
        for (Kind k : Kind.values()) {
            float target = 0f;
            if (FORCED.containsKey(k) || Storms.forced(k, time)) target = 1f;
            else if (on(k) && biome.is(TAGS.get(k))) {
                float own = WeatherConfig.get(WeatherConfig.OWN_STORMS) ? Storms.level(k, time, rx, rz) : 0f;
                float vanilla = WeatherConfig.get(WeatherConfig.FOLLOW_VANILLA) && (rainRoll[k.ordinal()] || thunder > 0.6f) ? switch (k) {   // vanilla rain and thunder, as this biome's storm
                    case SANDSTORM, BLIZZARD -> Math.max(rain, thunder);
                    case ASH -> rain * 0.8f + thunder * 0.2f;
                    case FOG -> rain * 0.7f;
                    case PETALS -> Math.max(rain * 0.7f, thunder);
                    default -> 0f;
                } : 0f;
                target = Math.max(own, vanilla);
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

        if (time % 10 == 0) coverage(level, player, time);
        spawn(mc, level, player, wind);
        distantClouds(level, player);
        sounds(mc.getSoundManager());
    }

    // ---------------------------------------------------------------- particles
    private static ColorParticleOption color(ParticleType<ColorParticleOption> t, int rgb) { return ColorParticleOption.create(t, 0xFF000000 | rgb); }

    @SuppressWarnings("unchecked")
    private static ParticleType<ColorParticleOption> petal() {
        return (ParticleType<ColorParticleOption>) BuiltInRegistries.PARTICLE_TYPE.getOptional(ResourceLocation.fromNamespaceAndPath("alterra", "petal")).orElse(null);
    }

    private static final float[] RING = {24f, 48f, 80f, 120f}, WEIGHT = {0.4f, 0.3f, 0.2f, 0.1f};
    private static void coverage(net.minecraft.client.multiplayer.ClientLevel level, net.minecraft.client.player.LocalPlayer p, long time) {
        boolean distant = WeatherConfig.get(WeatherConfig.DISTANT);
        var pos = new BlockPos.MutableBlockPos();
        for (Kind k : Kind.values()) {
            for (int d = 0; d < 16; d++) {
                float c = 0f;
                if (distant && k != Kind.AURORA && k != Kind.POLLEN && on(k)) {
                    double a = d * Math.PI / 8;
                    for (int i = 0; i < RING.length; i++) {
                        int x = Mth.floor(p.getX() + Math.cos(a) * RING[i]), z = Mth.floor(p.getZ() + Math.sin(a) * RING[i]);
                        if (!level.hasChunk(x >> 4, z >> 4)) continue;
                        if (level.getBiome(pos.set(x, Mth.clamp(p.getBlockY(), level.getMinBuildHeight(), level.getMaxBuildHeight() - 1), z)).is(TAGS.get(k)))
                            c += WEIGHT[i] * Math.max(Storms.level(k, time, Storms.region(x), Storms.region(z)), FORCED.containsKey(k) || Storms.forced(k, time) ? 1f : 0f);
                    }
                }
                cover[k.ordinal()][d] = Mth.lerp(0.35f, cover[k.ordinal()][d], Math.min(1f, c));
            }
        }
    }

    /** A storm in the next biome, from here: big soft clouds of it drifting at the edge of what you can see, in the directions where the storm is. */
    private static void distantClouds(net.minecraft.client.multiplayer.ClientLevel level, net.minecraft.client.player.LocalPlayer p) {
        if (!WeatherConfig.get(WeatherConfig.DISTANT) || mc().options.particles().get() == ParticleStatus.MINIMAL) return;
        var r = level.random; double wx = Math.cos(wind), wz = Math.sin(wind);
        for (Kind k : Kind.values()) {
            int rgb = switch (k) { case SANDSTORM -> 0xD2B27A; case BLIZZARD -> 0xE6EEF6; case ASH -> 0x7E7A76; case FOG -> 0xF3F6F9; case PETALS -> 0xF4B8CC; default -> -1; };
            if (rgb < 0 || now[k.ordinal()] > 0.5f) continue;
            for (int tries = 0; tries < 2; tries++) {
                int d = r.nextInt(16); float c = cover[k.ordinal()][d];
                if (c < 0.1f || r.nextFloat() > c * 0.6f) continue;
                double a = (d + r.nextDouble() - 0.5) * Math.PI / 8, dist = 46 + r.nextDouble() * 70;
                double x = p.getX() + Math.cos(a) * dist, z = p.getZ() + Math.sin(a) * dist;
                if (!level.hasChunk(Mth.floor(x) >> 4, Mth.floor(z) >> 4)) continue;
                int ground = WeatherHooks.ground(level, Mth.floor(x), Mth.floor(z));
                var type = k == Kind.PETALS ? petal() : AlterraWeather.PUFF.get();
                dev.phi.alterra.weather.WeatherParticles.add(level, color(type, rgb), x, ground + 1 + r.nextDouble() * 14, z, wx * 0.35, 0.02, wz * 0.35);
            }
        }
    }
    private static Minecraft mc() { return Minecraft.getInstance(); }

    /** Foam where water meets land: a few spots near the player are looked at every tick; where water borders a beach or bank, a patch of foam washes up and back. */
    private static void shoreFoam(net.minecraft.client.multiplayer.ClientLevel level, net.minecraft.client.player.LocalPlayer p) {
        if (true || mc().options.particles().get() == ParticleStatus.MINIMAL) return;
        var r = level.random; var pos = new BlockPos.MutableBlockPos(); var n = new BlockPos.MutableBlockPos();
        float windy = (float) AethryonApi.wind().at(level, p.getX(), p.getZ()).length();
        int spawned = 0, max = windy > 0.6f ? 3 : 2;
        for (int i = 0; i < 10 && spawned < max; i++) {
            int x = Mth.floor(p.getX() + (r.nextDouble() - 0.5) * 56), z = Mth.floor(p.getZ() + (r.nextDouble() - 0.5) * 56);
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            int y = WeatherHooks.ground(level, x, z) - 1;
            pos.set(x, y, z);
            var fluid = level.getFluidState(pos);
            if (!fluid.is(net.minecraft.tags.FluidTags.WATER) || !fluid.isSource() && fluid.getAmount() < 5) continue;
            for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL) {   // is land next to it, about as high as the water?
                n.set(x + d.getStepX(), y, z + d.getStepZ());
                if (level.getFluidState(n).is(net.minecraft.tags.FluidTags.WATER)) continue;
                boolean land = !level.getBlockState(n).getCollisionShape(level, n).isEmpty() || !level.getBlockState(n.below()).getCollisionShape(level, n.below()).isEmpty() && level.getBlockState(n).isAir();
                if (!land) continue;
                double surface = y + fluid.getHeight(level, pos);
                dev.phi.alterra.weather.WeatherParticles.add(level, AlterraWeather.FOAM.get(), x + 0.5 + d.getStepX() * (0.3 + r.nextDouble() * 0.15) + (r.nextDouble() - 0.5) * 0.5 * Math.abs(d.getStepZ()), surface + 0.03, z + 0.5 + d.getStepZ() * (0.3 + r.nextDouble() * 0.15) + (r.nextDouble() - 0.5) * 0.5 * Math.abs(d.getStepX()), d.getStepX(), 0, d.getStepZ());
                spawned++; break;
            }
        }
    }

    private static void spawn(Minecraft mc, net.minecraft.client.multiplayer.ClientLevel level, net.minecraft.client.player.LocalPlayer p, double windDir) {
        float quality = mc.options.particles().get() == ParticleStatus.ALL ? 1f : mc.options.particles().get() == ParticleStatus.DECREASED ? 0.5f : 0.15f;
        float amount = quality * WeatherConfig.get(WeatherConfig.INTENSITY).floatValue() * gust;
        if (WeatherConfig.get(WeatherConfig.ADAPT)) amount *= Mth.clamp(mc.getFps() / 50f, 0.25f, 1f);                       // fewer when the frame rate drops
        var r = level.random;
        if (PHOTO && level.getGameTime() % 40 == 0) org.slf4j.LoggerFactory.getLogger("Alterra Weather").info("[storm] sand {} snow {} amount {} outdoors {} fps {} particles {}", s(Kind.SANDSTORM), s(Kind.BLIZZARD), amount, outdoors, mc.getFps(), mc.particleEngine.countParticles());
        double wx = Math.cos(windDir), wz = Math.sin(windDir);
        // a point upwind of the player, just above the ground so nothing spawns inside terrain
        java.util.function.BiFunction<Double, Double, double[]> upwind = (dist, spread) -> {
            double along = dist, across = (r.nextDouble() - 0.5) * 2 * spread;
            double x = p.getX() - wx * along - wz * across, z = p.getZ() - wz * along + wx * across;
            int ground = WeatherHooks.ground(level, Mth.floor(x), Mth.floor(z));
            return new double[] {x, Mth.clamp(ground, p.getY() - 24, p.getY() + 24), z};
        };
        float out = Math.max(0.25f, outdoors);

        float sand = s(Kind.SANDSTORM);
        if (sand > 0.03f) {
            int n = Math.round(sand * 40 * amount * out);
            for (int i = 0; i < n; i++) {
                var o = upwind.apply(5 + r.nextDouble() * 26, 26.0); double sp = 0.9 + r.nextDouble() * 0.6;
                dev.phi.alterra.weather.WeatherParticles.add(level, color(AlterraWeather.DUST.get(), r.nextInt(3) == 0 ? 0xC8A66E : 0xE4CB92), o[0], o[1] + r.nextDouble() * r.nextDouble() * 8, o[2], wx * sp, -0.01, wz * sp);
            }
            int g = Math.round(sand * 70 * amount * out);   // sand lifted off the ground: low streaks skimming fast with the wind, only off loose sand
            for (int i = 0; i < g; i++) {
                var o = upwind.apply(1 + r.nextDouble() * 13, 14.0); double sp = 0.45 + r.nextDouble() * 0.4;   // a strong wind's speed along the ground (faster ones only flashed past, far away)
                if (!loose(level, o, false)) continue;   // (paler and darker than the sand, so it shows against it)
                dev.phi.alterra.weather.WeatherParticles.add(level, color(AlterraWeather.DUST.get(), r.nextInt(2) == 0 ? 0xEFE0B8 : 0xB8925A), o[0], o[1] + 0.04 + r.nextDouble() * r.nextDouble() * 0.6, o[2], wx * sp, 0.004 + r.nextDouble() * 0.01, wz * sp);
            }
            int v = Math.round(sand * 16 * amount * out);   // veils of sand skimming the ground (the specks above are too small to see on their own)
            for (int i = 0; i < v; i++) {
                var o = upwind.apply(1 + r.nextDouble() * 12, 13.0); double sp = 0.3 + r.nextDouble() * 0.3;
                if (!loose(level, o, false)) continue;
                dev.phi.alterra.weather.WeatherParticles.add(level, color(AlterraWeather.DRIFT.get(), r.nextInt(3) == 0 ? 0xC9A66C : 0xE6D2A0), o[0], o[1] + 0.2 + r.nextDouble() * 0.35, o[2], wx * sp, 0.0, wz * sp);
            }
            int c = Math.round(sand * 2.2f * amount * out + (r.nextFloat() < sand * 2.2f * amount * out % 1 ? 1 : 0));
            for (int i = 0; i < c; i++) {
                var o = upwind.apply(9 + r.nextDouble() * 22, 24.0);
                dev.phi.alterra.weather.WeatherParticles.add(level, color(AlterraWeather.PUFF.get(), 0xD2B27A), o[0], o[1] + r.nextDouble() * 5, o[2], wx * 0.55, 0.0, wz * 0.55);
            }
        }
        float snow = s(Kind.BLIZZARD);
        if (snow > 0.03f) {
            int n = Math.round(snow * 55 * amount * out);
            for (int i = 0; i < n; i++) {
                var o = upwind.apply(4 + r.nextDouble() * 24, 24.0); double sp = 1.0 + r.nextDouble() * 0.7;
                dev.phi.alterra.weather.WeatherParticles.add(level, color(AlterraWeather.DUST.get(), 0xF4F8FC), o[0], o[1] + r.nextDouble() * 12, o[2], wx * sp, -0.07 - r.nextDouble() * 0.05, wz * sp);
            }
            int g = Math.round(snow * 80 * amount * out);   // snow blown off the ground: low drifting streaks, only off snow
            for (int i = 0; i < g; i++) {
                var o = upwind.apply(1 + r.nextDouble() * 13, 14.0); double sp = 0.45 + r.nextDouble() * 0.4;
                if (!loose(level, o, true)) continue;
                dev.phi.alterra.weather.WeatherParticles.add(level, color(AlterraWeather.DUST.get(), 0xF6FAFF), o[0], o[1] + 0.04 + r.nextDouble() * r.nextDouble() * 0.7, o[2], wx * sp, 0.005 + r.nextDouble() * 0.012, wz * sp);
            }
            int v = Math.round(snow * 18 * amount * out);   // veils of snow skimming the ground
            for (int i = 0; i < v; i++) {
                var o = upwind.apply(1 + r.nextDouble() * 12, 13.0); double sp = 0.3 + r.nextDouble() * 0.3;
                if (!loose(level, o, true)) continue;
                dev.phi.alterra.weather.WeatherParticles.add(level, color(AlterraWeather.DRIFT.get(), 0xF4F8FC), o[0], o[1] + 0.2 + r.nextDouble() * 0.35, o[2], wx * sp, 0.0, wz * sp);
            }
            int c = Math.round(snow * 2.4f * amount * out + (r.nextFloat() < snow * 2.4f * amount * out % 1 ? 1 : 0));
            for (int i = 0; i < c; i++) {
                var o = upwind.apply(8 + r.nextDouble() * 20, 22.0);
                dev.phi.alterra.weather.WeatherParticles.add(level, color(AlterraWeather.PUFF.get(), 0xE6EEF6), o[0], o[1] + r.nextDouble() * 9, o[2], wx * 0.7, -0.02, wz * 0.7);
            }
        }
        float fog = s(Kind.FOG);
        if (fog > 0.03f && r.nextFloat() < fog * 1.4f * amount * out) {   // low mist drifting through, slowly
            double a = r.nextDouble() * Mth.TWO_PI, d = 3 + r.nextDouble() * 22;
            double x = p.getX() + Math.cos(a) * d, z = p.getZ() + Math.sin(a) * d;
            int ground = WeatherHooks.ground(level, Mth.floor(x), Mth.floor(z));
            if (Math.abs(ground - p.getY()) < 20)
                dev.phi.alterra.weather.WeatherParticles.add(level, color(AlterraWeather.PUFF.get(), 0xF3F6F9), x, ground + r.nextDouble() * 2.5, z, wx * 0.03, 0.0, wz * 0.03);
        }
        float petals = s(Kind.PETALS); var petalType = petals > 0.03f ? petal() : null;
        if (petalType != null) {
            int n = Math.round(petals * 12 * amount * out);
            int[] pinks = {0xF4B6D2, 0xF9D0E2, 0xFFFFFF, 0xEFA3C4};
            for (int i = 0; i < n; i++) {
                var o = upwind.apply(4 + r.nextDouble() * 18, 20.0); double sp = 0.28 + r.nextDouble() * 0.3;
                dev.phi.alterra.weather.WeatherParticles.add(level, color(petalType, pinks[r.nextInt(pinks.length)]), o[0], o[1] + 1 + r.nextDouble() * 9, o[2], wx * sp, -0.01, wz * sp);
            }
        }
        float pollen = s(Kind.POLLEN);
        if (pollen > 0.03f) {
            int n = Math.round(pollen * 4 * amount * out);
            for (int i = 0; i < n; i++) {
                double a = r.nextDouble() * Mth.TWO_PI, d = 2 + r.nextDouble() * 16;
                double x = p.getX() + Math.cos(a) * d, z = p.getZ() + Math.sin(a) * d;
                int ground = WeatherHooks.ground(level, Mth.floor(x), Mth.floor(z));
                if (Math.abs(ground - p.getY()) < 14)
                    dev.phi.alterra.weather.WeatherParticles.add(level, color(AlterraWeather.MOTE.get(), r.nextInt(4) == 0 ? 0xFFF4B0 : 0xF3D95C), x, ground + 0.4 + r.nextDouble() * 4, z, wx * 0.02, 0.006, wz * 0.02);
            }
        }
        float ash = s(Kind.ASH);
        if (ash > 0.03f) {
            int n = Math.round(ash * 9 * amount * out);
            for (int i = 0; i < n; i++) {
                double x = p.getX() + (r.nextDouble() - 0.5) * 36, z = p.getZ() + (r.nextDouble() - 0.5) * 36, y = p.getY() + 3 + r.nextDouble() * 14;
                if (WeatherHooks.ground(level, Mth.floor(x), Mth.floor(z)) <= y)
                    dev.phi.alterra.weather.WeatherParticles.add(level, color(AlterraWeather.MOTE.get(), r.nextInt(5) == 0 ? 0x4C4846 : 0xA6A09A), x, y, z, wx * 0.03, -0.03 - r.nextDouble() * 0.02, wz * 0.03);
            }
            if (ash > 0.4f && r.nextFloat() < ash * 0.5f * amount * out) {   // and now and then a big drifting cloud of it
                var o = upwind.apply(12 + r.nextDouble() * 20, 24.0);
                dev.phi.alterra.weather.WeatherParticles.add(level, color(AlterraWeather.PUFF.get(), 0x7E7A76), o[0], o[1] + r.nextDouble() * 8, o[2], wx * 0.3, 0.0, wz * 0.3);
            }
        }
    }

    // ---------------------------------------------------------------- wind
    private static void sounds(SoundManager sm) {
        sound(sm, Kind.SANDSTORM, AlterraWeather.WIND_DUST.get(), 1.0f);
        sound(sm, Kind.BLIZZARD, AlterraWeather.WIND_SNOW.get(), 1.0f);
        sound(sm, Kind.FOG, AlterraWeather.FOG_HUSH.get(), 0.55f);
        sound(sm, Kind.PETALS, AlterraWeather.PETAL_RUSTLE.get(), 0.7f);
        sound(sm, Kind.ASH, AlterraWeather.ASH_RUMBLE.get(), 0.8f);
        sound(sm, Kind.POLLEN, AlterraWeather.POLLEN_HUM.get(), 0.5f);
        sound(sm, Kind.AURORA, AlterraWeather.AURORA_CHIME.get(), 0.4f);
        rainSounds(sm);
    }
    private static final Map<String, StormSound> OTHER = new java.util.HashMap<>();
    private static void rainSounds(SoundManager sm) {
        var mc = Minecraft.getInstance(); var level = mc.level; var p = mc.player;
        float rain = 0f, heavy = 0f;
        if (level != null && p != null && WeatherConfig.get(WeatherConfig.REPLACE_RAIN)) {
            var pos = p.blockPosition();
            if (level.getBiome(pos).value().getPrecipitationAt(pos) == net.minecraft.world.level.biome.Biome.Precipitation.RAIN) { rain = HeavyStorm.rainAt(level, p.getX(), p.getZ()); heavy = HeavyStorm.at(level, p.getX(), p.getZ()); }
        }
        float out = 0.25f + 0.75f * outdoors;
        loop(sm, "rain_light", AlterraWeather.RAIN_LIGHT.get(), rain * (1f - heavy) * 0.55f * out);
        loop(sm, "rain_heavy", AlterraWeather.RAIN_HEAVY.get(), (rain > 0 ? Math.max(heavy, (rain - 0.6f) * 1.2f) : 0f) * 0.85f * out);
    }
    private static void loop(SoundManager sm, String key, SoundEvent ev, float v) {
        StormSound cur = OTHER.get(key);
        if (cur != null && cur.isStopped()) { OTHER.remove(key); cur = null; }
        if (cur == null && v > 0.02f) { cur = new StormSound(ev); OTHER.put(key, cur); sm.play(cur); }
        if (cur != null) { cur.target(v); if (v < 0.005f && cur.faded()) { cur.end(); OTHER.remove(key); } }
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
        if (e.getType() != FogType.NONE) return;
        boolean terrain = e.getMode() == FogRenderer.FogMode.FOG_TERRAIN;   // the sky gets the same fog (else it stayed clear blue over a sandstorm)
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
        if (terrain) WeatherHooks.stormFog = Mth.clamp(1f - target / Math.max(1f, far), 0f, 1f);
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
        var camPos = e.getCamera().getPosition(); float heavy = Minecraft.getInstance().level == null ? 0f : HeavyStorm.at(Minecraft.getInstance().level, camPos.x, camPos.z) * o;   // a heavy storm turns the sky dark
        if (heavy > 0.01f) { float m = heavy * 0.7f; e.setRed(Mth.lerp(m, e.getRed(), 0.10f)); e.setGreen(Mth.lerp(m, e.getGreen(), 0.12f)); e.setBlue(Mth.lerp(m, e.getBlue(), 0.16f)); }
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

    private static int argb(float alpha, int rgb) { return (Mth.clamp((int) (alpha * 255), 0, 255) << 24) | (rgb & 0xFFFFFF); }

    // ---------------------------------------------------------------- aurora
    @SubscribeEvent
    public static void sky(RenderLevelStageEvent e) {
        if (broken) return;
        try { do_sky(e); } catch (Throwable t) { fail(t); }
    }
    /** Is the ground at this spot loose enough for the wind to lift: sand (and red sand) for a sandstorm, snow for a blizzard. */
    private static boolean loose(net.minecraft.client.multiplayer.ClientLevel level, double[] o, boolean snow) {
        var p = net.minecraft.core.BlockPos.containing(o[0], o[1] - 0.5, o[2]);
        var st = level.getBlockState(p); var above = level.getBlockState(p.above());
        if (snow) return above.is(net.minecraft.world.level.block.Blocks.SNOW) || st.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK) || st.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW) || st.is(net.minecraft.world.level.block.Blocks.SNOW);
        return st.is(net.minecraft.tags.BlockTags.SAND);
    }

    private static void do_sky(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        float a = Mth.lerp(pt, before[Kind.AURORA.ordinal()], now[Kind.AURORA.ordinal()]);
        if (a < 0.01f) return;
        Matrix4f m = new Matrix4f(e.getModelViewMatrix());
        float t = WeatherHooks.clock();   // smooth, and the same speed at any tick rate
        RenderSystem.enableBlend(); RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false); RenderSystem.disableCull(); RenderSystem.setShader(GameRenderer::getPositionColorShader);
        var buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int band = 0; band < 3; band++) {   // curtains all the way round the sky, with gaps where the light is thin
            float dist = 90 + band * 26, base = 26 + band * 7, height = 46 + band * 9, phase = band * 1.7f;
            int seg = 240;
            for (int i = 0; i < seg; i++) {
                float u0 = (float) i / seg, u1 = (float) (i + 1) / seg;
                float ph0 = u0 * Mth.TWO_PI, ph1 = u1 * Mth.TWO_PI;
                float r0 = dist + Mth.sin(u0 * 27f + t * 0.33f + phase) * 12 + Mth.sin(u0 * 71f - t * 0.2f) * 3, r1 = dist + Mth.sin(u1 * 27f + t * 0.33f + phase) * 12 + Mth.sin(u1 * 71f - t * 0.2f) * 3;
                float x0 = Mth.sin(ph0) * r0, z0 = -Mth.cos(ph0) * r0, x1 = Mth.sin(ph1) * r1, z1 = -Mth.cos(ph1) * r1;
                float ray0 = 0.45f + 0.55f * Mth.sin(u0 * 190f - t * 0.9f + phase) * Mth.sin(u0 * 51f + t * 0.4f), ray1 = 0.45f + 0.55f * Mth.sin(u1 * 190f - t * 0.9f + phase) * Mth.sin(u1 * 51f + t * 0.4f);
                float w0 = 0.5f + 0.5f * Mth.sin(u0 * 63f - t * 0.7f + phase), w1 = 0.5f + 0.5f * Mth.sin(u1 * 63f - t * 0.7f + phase);
                float lobe0 = Mth.clamp(0.15f + 1.1f * Mth.sin(u0 * Mth.TWO_PI * 3f + t * 0.02f + phase * 2f) * Mth.sin(u0 * Mth.TWO_PI * 2f - t * 0.013f), 0f, 1f);
                float lobe1 = Mth.clamp(0.15f + 1.1f * Mth.sin(u1 * Mth.TWO_PI * 3f + t * 0.02f + phase * 2f) * Mth.sin(u1 * Mth.TWO_PI * 2f - t * 0.013f), 0f, 1f);
                float al0 = a * Math.max(0, ray0) * (0.4f + 0.6f * w0) * lobe0 * 0.34f, al1 = a * Math.max(0, ray1) * (0.4f + 0.6f * w1) * lobe1 * 0.34f;
                float hue = 0.5f + 0.5f * Mth.sin(t * 0.05f + band);
                buf.addVertex(m, x0, base, z0).setColor(0.20f, 0.95f - 0.2f * hue, 0.45f + 0.3f * hue, al0);
                buf.addVertex(m, x1, base, z1).setColor(0.20f, 0.95f - 0.2f * hue, 0.45f + 0.3f * hue, al1);
                buf.addVertex(m, x1, base + height * (0.7f + 0.3f * ray1) * lobe1, z1).setColor(0.60f, 0.30f, 0.95f, 0f);
                buf.addVertex(m, x0, base + height * (0.7f + 0.3f * ray0) * lobe0, z0).setColor(0.60f, 0.30f, 0.95f, 0f);
            }
        }
        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.defaultBlendFunc(); RenderSystem.disableBlend();
    }
}
