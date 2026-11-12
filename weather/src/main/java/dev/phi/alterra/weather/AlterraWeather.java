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

/** Alterra Weather. Which biomes get which storm is set by the biome tags alterra_weather:weather/*. */
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
        AlterraApi.addSettingsCategory("Weather", "Weather effects. Only on your computer.", () -> new ItemStack(Items.SAND), List.of(
                AlterraApi.Option.bool("Sandstorms", "Dust and sandy fog in deserts.", WeatherConfig.SANDSTORMS),
                AlterraApi.Option.bool("Fog banks", "Thick fog in misty biomes.", WeatherConfig.FOG_BANKS),
                AlterraApi.Option.bool("Petal storms", "Petals blowing around in blossom woods.", WeatherConfig.PETAL_STORMS),
                AlterraApi.Option.bool("Aurora", "Northern lights over snowy biomes at night.", WeatherConfig.AURORA),
                AlterraApi.Option.bool("Blizzards", "Driven snow and bad visibility in snowy biomes.", WeatherConfig.BLIZZARDS),
                AlterraApi.Option.bool("Pollen", "Pollen floating around flower biomes on sunny days.", WeatherConfig.POLLEN),
                AlterraApi.Option.bool("Ashfall", "Ash falling in burnt biomes.", WeatherConfig.ASHFALL),
                AlterraApi.Option.number("Intensity", "How many particles. Lower it if the game runs badly.", WeatherConfig.INTENSITY, 0.1, 2.0, 0.1, "x"),
                AlterraApi.Option.bool("Step aside for other mods", "Turn off effects that another weather or fog mod already does.", WeatherConfig.STEP_ASIDE)));
        if (FMLEnvironment.dist == Dist.CLIENT) WeatherClient.init(bus);
    }
}
