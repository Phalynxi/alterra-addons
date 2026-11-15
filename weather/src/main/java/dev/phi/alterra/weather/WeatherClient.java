package dev.phi.alterra.weather;

import dev.phi.alterra.weather.disaster.DisasterClient;
import dev.phi.alterra.weather.storm.StormRain;
import dev.phi.alterra.weather.water.Foam;
import dev.phi.alterra.weather.water.Ripples;
import dev.phi.alterra.weather.water.Splashes;
import dev.phi.alterra.weather.water.SubLevels;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/** Client-only: the weather particles. */
public final class WeatherClient {
    private WeatherClient() {}

    public static void init(IEventBus bus) {
        WeatherHooks.disasters = DisasterClient::received;
        bus.addListener((net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent e) -> e.register(net.minecraft.resources.ResourceLocation.withDefaultNamespace("overworld"), new StormRain()));   // wind-blown rain
        bus.addListener((RegisterParticleProvidersEvent e) -> {   // the game only stitches our sprites; the particles themselves are Aethryon's
            for (var type : java.util.List.of(AlterraWeather.RIPPLE, AlterraWeather.CLUMP, AlterraWeather.SPRAY, AlterraWeather.FOAM, AlterraWeather.DUST, AlterraWeather.PUFF, AlterraWeather.DRIFT, AlterraWeather.PRINT, AlterraWeather.DROP, AlterraWeather.HAIL, AlterraWeather.MOTE)) {
                if (type.get() instanceof net.minecraft.core.particles.SimpleParticleType st) e.registerSpriteSet(st, sp -> { WeatherParticles.spritesOf(st, sp); return (t, l, x, y, z, dx, dy, dz) -> { WeatherParticles.add(l, t, x, y, z, dx, dy, dz); return null; }; });
                else if (type.get() instanceof net.minecraft.core.particles.ParticleType<?> pt) e.registerSpriteSet((net.minecraft.core.particles.ParticleType) pt, sp -> { WeatherParticles.spritesOf(pt, sp); return (o, l, x, y, z, dx, dy, dz) -> { WeatherParticles.add(l, o, x, y, z, dx, dy, dz); return null; }; });
            }
            e.registerSpecial(net.minecraft.core.particles.ParticleTypes.RAIN, (t, l, x, y, z, dx, dy, dz) -> { WeatherParticles.spawnRainHit(l, x, y, z); return null; });   // raindrops hitting the ground: our small water drops instead of vanilla's
        });
    }
}
