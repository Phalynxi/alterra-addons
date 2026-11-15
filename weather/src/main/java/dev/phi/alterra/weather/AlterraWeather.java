package dev.phi.alterra.weather;

import dev.phi.alterra.weather.config.DisasterConfig;
import dev.phi.alterra.weather.config.HeavyConfig;
import dev.phi.alterra.weather.config.WeatherConfig;
import dev.phi.alterra.weather.net.BlastSync;
import dev.phi.alterra.weather.net.DisasterSync;
import dev.phi.alterra.weather.net.SeaSync;
import dev.phi.alterra.weather.net.WaterSync;
import dev.phi.alterra.weather.net.WetSync;
import dev.phi.alterra.weather.storm.Blast;
import dev.phi.alterra.weather.storm.ForcedStorm;
import dev.phi.alterra.weather.storm.HeavyStorm;
import dev.phi.alterra.weather.storm.WetSubscribe;
import dev.phi.alterra.weather.storm.Wetness;
import dev.phi.alterra.weather.water.SeaSurge;
import dev.phi.alterra.weather.water.WaterBlocks;

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
            PUFF = PARTICLES.register("puff", AlterraWeather::colored), MOTE = PARTICLES.register("mote", AlterraWeather::colored),
            PRINT = PARTICLES.register("print", AlterraWeather::colored),
            DRIFT = PARTICLES.register("drift", AlterraWeather::colored);   // a low veil of sand or snow skimming the ground in a storm
    public static final Supplier<net.minecraft.core.particles.SimpleParticleType> RIPPLE = PARTICLES.register("ripple", () -> new net.minecraft.core.particles.SimpleParticleType(false)),
            FOAM = PARTICLES.register("foam", () -> new net.minecraft.core.particles.SimpleParticleType(false)),
            SPRAY = PARTICLES.register("spray", () -> new net.minecraft.core.particles.SimpleParticleType(false)),
            CLUMP = PARTICLES.register("clump", () -> new net.minecraft.core.particles.SimpleParticleType(false)),
            HAIL = PARTICLES.register("hail", () -> new net.minecraft.core.particles.SimpleParticleType(false)),
            DROP = PARTICLES.register("drop", () -> new net.minecraft.core.particles.SimpleParticleType(false));
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ID);
    public static final Supplier<SoundEvent> WIND_DUST = sound("wind_dust"), WIND_SNOW = sound("wind_snow"), ASH_RUMBLE = sound("ash_rumble"), FOG_HUSH = sound("fog_hush"),
            PETAL_RUSTLE = sound("petal_rustle"), POLLEN_HUM = sound("pollen_hum"), AURORA_CHIME = sound("aurora_chime"),
            RAIN_LIGHT = sound("rain_light"), RAIN_HEAVY = sound("rain_heavy");
    private static Supplier<SoundEvent> sound(String name) { return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ID, name))); }

    public AlterraWeather(IEventBus bus, ModContainer container) {
        PARTICLES.register(bus); SOUNDS.register(bus); WaterBlocks.register(bus);
        bus.addListener((net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e) -> e.registrar("1").optional()
                .playToClient(ForcedStorm.TYPE, ForcedStorm.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> ForcedStorm.apply(payload)))
                .playToServer(WetSubscribe.TYPE, WetSubscribe.CODEC, (p, ctx) -> ctx.enqueueWork(() -> Wetness.subscribe((net.minecraft.server.level.ServerPlayer) ctx.player(), p.on())))
                .playToClient(WetSync.TYPE, WetSync.CODEC, (p, ctx) -> ctx.enqueueWork(() -> Wetness.received(p.wet())))
                .playToClient(BlastSync.TYPE, BlastSync.CODEC, (p, ctx) -> ctx.enqueueWork(() -> Blast.received(p)))
                .playToClient(SeaSync.TYPE, SeaSync.CODEC, (p, ctx) -> ctx.enqueueWork(() -> SeaSurge.received(p)))
                .playToClient(WaterSync.TYPE, WaterSync.CODEC, (p, ctx) -> ctx.enqueueWork(() -> WeatherHooks.waterAmounts.accept(p)))
                .playToClient(DisasterSync.TYPE, DisasterSync.CODEC, (p, ctx) -> ctx.enqueueWork(() -> WeatherHooks.disasters.accept(p))));
        AlterraApi.setWindProvider((level, x, z) -> Wind.at(level.getGameTime(), x, z, level.getRainLevel(1f), level.getThunderLevel(1f), HeavyStorm.level(level)));   // other addons can ask for the wind
        container.registerConfig(ModConfig.Type.CLIENT, WeatherConfig.SPEC);
        container.registerConfig(ModConfig.Type.COMMON, HeavyConfig.SPEC);
        container.registerConfig(ModConfig.Type.COMMON, DisasterConfig.SPEC, "alterra_weather-disasters.toml");
        AlterraApi.addSettingsCategory("Weather", "Weather effects. Only on your computer.", () -> new ItemStack(Items.SAND), List.of(
                AlterraApi.Option.bool("Sandstorms", "Dust and sandy fog in deserts.", WeatherConfig.SANDSTORMS),
                AlterraApi.Option.bool("Fog banks", "Thick fog in misty biomes.", WeatherConfig.FOG_BANKS),
                AlterraApi.Option.bool("Petal storms", "Petals blowing around in blossom woods.", WeatherConfig.PETAL_STORMS),
                AlterraApi.Option.bool("Aurora", "Northern lights over snowy biomes at night.", WeatherConfig.AURORA),
                AlterraApi.Option.bool("Blizzards", "Driven snow and bad visibility in snowy biomes.", WeatherConfig.BLIZZARDS),
                AlterraApi.Option.bool("Pollen", "Pollen floating around flower biomes on sunny days.", WeatherConfig.POLLEN),
                AlterraApi.Option.bool("Ashfall", "Ash falling in burnt biomes.", WeatherConfig.ASHFALL),
                AlterraApi.Option.bool("Follow vanilla weather", "Rain and thunder bring each biome's own storm: sandstorm in deserts, blizzard in snow, petals, ash, mist.", WeatherConfig.FOLLOW_VANILLA),
                AlterraApi.Option.number("Storm share", "How often rain turns into the biome storm instead of plain rain or snow.", WeatherConfig.STORM_SHARE, 0.0, 2.0, 0.1, "x"),
                AlterraApi.Option.bool("Rain ripples", "Raindrops that land on water make rings.", WeatherConfig.RIPPLES),
                AlterraApi.Option.bool("Wind-blown rain", "Rain and snow fall slanted by the wind and are drawn by Alterra, not vanilla.", WeatherConfig.REPLACE_RAIN),
                AlterraApi.Option.number("Rain slant", "How far the wind pushes the rain sideways.", WeatherConfig.RAIN_SLANT, 0.0, 1.5, 0.1, "x"),
                AlterraApi.Option.bool("Heavy thunderstorms", "Some thunderstorms are heavy: a gale, pouring rain, a dark sky.", HeavyConfig.HEAVY),
                AlterraApi.Option.number("Heavy storm share", "How many thunderstorms are heavy ones.", HeavyConfig.SHARE, 0.0, 1.0, 0.1, "x"),
                AlterraApi.Option.bool("Lightning", "Heavy thunderstorms strike lightning around you.", HeavyConfig.LIGHTNING),
                AlterraApi.Option.number("Lightning rate", "How often it strikes.", HeavyConfig.LIGHTNING_RATE, 0.1, 4.0, 0.1, "x"),
                AlterraApi.Option.number("Real strikes", "What share of the strikes are real (fire, damage). The rest are for show.", HeavyConfig.REAL_BOLTS, 0.0, 1.0, 0.05, "x"),
                AlterraApi.Option.bool("Footprints", "Prints and puffs where you and other things walk on snow, sand and mud.", WeatherConfig.FOOTPRINTS),
                AlterraApi.Option.bool("Shore splashes", "Waves break on the shore and throw spray inland that wets the land.", WeatherConfig.SHORE_SPLASH),
                AlterraApi.Option.bool("Snow tracks", "Walking on layers of snow presses them down: real tracks.", HeavyConfig.SNOW_TRACKS),
                AlterraApi.Option.bool("Snow settles", "Snow on slopes creeps downhill by itself (the start of avalanches).", HeavyConfig.SNOW_SETTLES),
                AlterraApi.Option.bool("Wind moves sand and snow", "Sandstorms carry sand into dunes and blizzards carry snow into drifts, for real (blocks move).", HeavyConfig.WIND_MOVES_LOOSE),
                AlterraApi.Option.bool("Avalanches", "Snow on steep slopes slides down in heavy snowfall, when something explodes, lightning strikes or you run across it.", HeavyConfig.AVALANCHES),
                AlterraApi.Option.bool("Distant storms", "See a storm in the next biome on the horizon before you get there.", WeatherConfig.DISTANT),
                AlterraApi.Option.bool("Storms on their own", "Storms also come without rain, on their own timer.", WeatherConfig.OWN_STORMS),
                AlterraApi.Option.bool("Adapt to frame rate", "Use fewer particles when the game slows down.", WeatherConfig.ADAPT),
                AlterraApi.Option.number("Intensity", "How many particles. Lower it if the game runs badly.", WeatherConfig.INTENSITY, 0.1, 2.0, 0.1, "x"),
                AlterraApi.Option.bool("Step aside for other mods", "Turn off effects that another weather or fog mod already does.", WeatherConfig.STEP_ASIDE)));
        AlterraApi.addSettingsCategory("Disasters", "Tornadoes, hail, whirlpools and dust devils. They need Alterra Illuminate, which draws them, and happen on the server.", () -> new ItemStack(Items.LIGHTNING_ROD), List.of(
                AlterraApi.Option.bool("Tornadoes", "Now and then in heavy thunderstorms: a funnel that travels with the wind and throws things around.", DisasterConfig.TORNADOES),
                AlterraApi.Option.bool("Tornadoes tear up blocks", "Tornadoes lift soft blocks (leaves, plants, glass, snow, carpet, wool). Off: they only move free things.", DisasterConfig.TORNADO_BLOCKS),
                AlterraApi.Option.bool("Hail", "Some heavy thunderstorms bring hail that hurts.", DisasterConfig.HAIL),
                AlterraApi.Option.bool("Hail breaks glass", "Hail can break glass that is open to the sky.", DisasterConfig.HAIL_GLASS),
                AlterraApi.Option.bool("Whirlpools", "Whirlpools in the open sea that pull in boats and swimmers.", DisasterConfig.VORTEXES),
                AlterraApi.Option.bool("Dust devils", "Small harmless whirls of sand in the desert on hot days.", DisasterConfig.DUST_DEVILS),
                AlterraApi.Option.bool("Pull things in", "Disasters push and pull players, mobs, items and boats. Off: only for show.", DisasterConfig.PULL_THINGS),
                AlterraApi.Option.number("How often", "How often disasters start. 1 is normal.", DisasterConfig.RATE, 0.0, 3.0, 0.1, "x")));
        if (FMLEnvironment.dist == Dist.CLIENT) WeatherClient.init(bus);
    }
}
