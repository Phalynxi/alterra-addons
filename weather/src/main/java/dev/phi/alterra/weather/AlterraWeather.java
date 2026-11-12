package dev.phi.alterra.weather;

import com.mojang.serialization.MapCodec;
import dev.phi.alterra.api.AlterraApi;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.function.Supplier;

/**
 * Alterra Weather: sandstorms, fog banks, petal storms, an aurora, blizzards, pollen and ashfall. Where each happens is a biome tag
 * (alterra_weather:weather/*), so datapacks can move them; every effect has a switch; effects step aside for other weather mods.
 */
@Mod(AlterraWeather.ID)
public final class AlterraWeather {
    public static final String ID = "alterra_weather";
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, ID);
    private static ParticleType<ColorParticleOption> colored() {
        return new ParticleType<>(false) {
            @Override public MapCodec<ColorParticleOption> codec() { return ColorParticleOption.codec(this); }
            @Override public StreamCodec<? super RegistryFriendlyByteBuf, ColorParticleOption> streamCodec() { return ColorParticleOption.streamCodec(this); }
        };
    }
    /** All three are tinted by the particle's colour: DUST = grit and driven snow, PUFF = the big soft clouds of a storm, MOTE = slow pollen and ash. */
    public static final Supplier<ParticleType<ColorParticleOption>> DUST = PARTICLES.register("dust", AlterraWeather::colored),
            PUFF = PARTICLES.register("puff", AlterraWeather::colored), MOTE = PARTICLES.register("mote", AlterraWeather::colored);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ID);
    public static final Supplier<SoundEvent> WIND_DUST = sound("wind_dust"), WIND_SNOW = sound("wind_snow"), WIND_CALM = sound("wind_calm");
    private static Supplier<SoundEvent> sound(String name) { return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ID, name))); }

    public AlterraWeather(IEventBus bus, ModContainer container) {
        PARTICLES.register(bus); SOUNDS.register(bus);
        container.registerConfig(ModConfig.Type.CLIENT, WeatherConfig.SPEC);
        AlterraApi.addSettingsCategory("Weather", "Biome weather effects on this computer.", () -> new ItemStack(Items.SAND), List.of(
                AlterraApi.Option.bool("Sandstorms", "Deserts: blowing dust and close, sandy fog while it rains elsewhere.", WeatherConfig.SANDSTORMS),
                AlterraApi.Option.bool("Fog banks", "Misty biomes: thick fog at dawn and in rain.", WeatherConfig.FOG_BANKS),
                AlterraApi.Option.bool("Petal storms", "Blossom woods: gusts of petals when the weather turns.", WeatherConfig.PETAL_STORMS),
                AlterraApi.Option.bool("Aurora", "Snowy biomes: an aurora on clear nights.", WeatherConfig.AURORA),
                AlterraApi.Option.bool("Blizzards", "Snowy biomes: driven snow and a white-out while it snows.", WeatherConfig.BLIZZARDS),
                AlterraApi.Option.bool("Pollen", "Flower biomes: pollen drifting by on warm, clear days.", WeatherConfig.POLLEN),
                AlterraApi.Option.bool("Ashfall", "Burnt and ember biomes: ash drifting down.", WeatherConfig.ASHFALL),
                AlterraApi.Option.number("Intensity", "How many particles the effects use.", WeatherConfig.INTENSITY, 0.1, 2.0, 0.1, "x"),
                AlterraApi.Option.bool("Step aside for other mods", "Turn off effects that a weather, fog or sky mod you have installed already does.", WeatherConfig.STEP_ASIDE)));
        if (FMLEnvironment.dist == Dist.CLIENT) WeatherClient.init(bus);
    }
}
